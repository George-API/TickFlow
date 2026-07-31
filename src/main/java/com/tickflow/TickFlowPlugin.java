package com.tickflow;

import com.google.inject.Provides;
import java.util.EnumSet;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * TickFlow — passive visualization of recent player actions and inferred attack rhythm.
 * <p>
 * Event mapping:
 * <ul>
 *   <li>{@link GameTick} — advance local tick index, sample position/prayer/equipment, finalize timeline</li>
 *   <li>{@link MenuOptionClicked} — classify attack/move/item/prayer attempts into the open tick</li>
 *   <li>{@link AnimationChanged} — low-confidence attack corroboration for local player</li>
 *   <li>{@link GameStateChanged} / {@link ActorDeath} / equipment changes — reset stale combat timing</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "TickFlow",
	description = "Rolling game-tick timeline of recent actions and inferred attack rhythm.",
	tags = {"tick", "timing", "pvm", "combat", "training", "rhythm"}
)
public class TickFlowPlugin extends Plugin
{
	private static final EnumSet<GameState> RESET_STATES = EnumSet.of(
		GameState.LOGIN_SCREEN,
		GameState.LOGIN_SCREEN_AUTHENTICATOR,
		GameState.LOGGING_IN,
		GameState.HOPPING,
		GameState.CONNECTION_LOST,
		GameState.LOADING
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TickFlowConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TickFlowOverlay overlay;

	@Inject
	private TickFlowCircleOverlay circleOverlay;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TickFlowIcons icons;

	@Inject
	private TickMetronome metronome;

	@Inject
	private ConfigManager configManager;

	private final TickFlowState state = new TickFlowState();
	private final ActionClassifier classifier = state.getClassifier();
	private static final Prayer[] PRAYERS = Prayer.values();

	private volatile long lastGameTickMs;
	@Nullable
	private String cachedEquipFp;
	private int cachedWeaponId = -1;
	private int cachedWeaponAspeed = -1;
	private int cachedShieldId = -1;
	private int cachedAmmoId = -1;
	private volatile CombatStyle combatStyle = CombatStyle.MELEE;

	@Override
	protected void startUp()
	{
		state.reset("startup");
		state.setTimelineLength(config.timelineLength());
		state.setCaptureMenuDebug(config.debugMode());
		lastGameTickMs = 0L;
		cachedEquipFp = null;
		cachedWeaponId = -1;
		cachedWeaponAspeed = -1;
		cachedShieldId = -1;
		cachedAmmoId = -1;
		combatStyle = CombatStyle.MELEE;
		icons.ensureLoaded();
		// Plugin list toggles startUp on the EDT — never touch the client there.
		clientThread.invoke(this::refreshClientCachesIfLoggedIn);
		metronome.setVolumePercent(config.tickSoundVolume());
		if (config.tickSound())
		{
			metronome.start();
		}
		overlayManager.add(overlay);
		overlayManager.add(circleOverlay);
		syncTimelineOverlays();
		log.debug("TickFlow started");
	}

	private void refreshClientCachesIfLoggedIn()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			refreshEquipmentCache();
			refreshCombatStyle();
		}
	}

	@Override
	protected void shutDown()
	{
		overlay.unregisterInput();
		circleOverlay.unregisterInput();
		overlayManager.remove(overlay);
		overlayManager.remove(circleOverlay);
		metronome.stop();
		state.reset("shutdown");
		lastGameTickMs = 0L;
		icons.clear();
		log.debug("TickFlow stopped");
	}

	@Provides
	TickFlowConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickFlowConfig.class);
	}

	TickFlowState.Snapshot getSnapshot()
	{
		return state.snapshot();
	}

	TickFlowIcons getIcons()
	{
		return icons;
	}

	/**
	 * Progress through the current game tick in {@code [0, 1]}.
	 * Used by the overlay pulse; safe to call from the render path.
	 */
	double getTickProgress()
	{
		if (lastGameTickMs <= 0L)
		{
			return 0;
		}
		long elapsed = System.currentTimeMillis() - lastGameTickMs;
		if (elapsed <= 0)
		{
			return 0;
		}
		if (elapsed >= Constants.GAME_TICK_LENGTH)
		{
			return 1;
		}
		return elapsed / (double) Constants.GAME_TICK_LENGTH;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TickFlowConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		state.setTimelineLength(config.timelineLength());
		state.setCaptureMenuDebug(config.debugMode());
		if ("timelineLength".equals(event.getKey()))
		{
			state.getAttackCycleTracker().setFeedbackTtl(AttackCycleTracker.DEFAULT_FEEDBACK_TTL);
		}
		if ("tickSound".equals(event.getKey()) || "tickSoundVolume".equals(event.getKey()))
		{
			metronome.setVolumePercent(config.tickSoundVolume());
			if (config.tickSound())
			{
				metronome.start();
			}
			else
			{
				metronome.stop();
			}
		}
		if ("timelineStyle".equals(event.getKey())
			|| "enabledOverlay".equals(event.getKey()))
		{
			syncTimelineOverlays();
		}
	}

	private void syncTimelineOverlays()
	{
		overlay.unregisterInput();
		circleOverlay.unregisterInput();
		if (!config.enabledOverlay())
		{
			return;
		}
		if (config.timelineStyle() == TimelineStyle.SQUARE)
		{
			overlay.registerInput();
		}
		else
		{
			circleOverlay.registerInput();
		}
	}

	void toggleTickSoundMute()
	{
		if (!config.tickSound())
		{
			configManager.setConfiguration(TickFlowConfig.GROUP, "tickSound", true);
			configManager.setConfiguration(TickFlowConfig.GROUP, "tickSoundMuted", false);
			metronome.setVolumePercent(config.tickSoundVolume());
			metronome.start();
			return;
		}
		configManager.setConfiguration(TickFlowConfig.GROUP, "tickSoundMuted", !config.tickSoundMuted());
	}

	/** Cycle metronome volume through three quiet presets (overlay control). */
	void cycleTickSoundVolume()
	{
		if (!config.tickSound())
		{
			return;
		}
		int v = config.tickSoundVolume();
		int next;
		if (v < 50)
		{
			next = 60;
		}
		else if (v < 70)
		{
			next = 80;
		}
		else
		{
			next = 40;
		}
		configManager.setConfiguration(TickFlowConfig.GROUP, "tickSoundVolume", next);
		metronome.setVolumePercent(next);
	}

	boolean isTickSoundEnabled()
	{
		return config.tickSound();
	}

	int getTickSoundVolume()
	{
		return config.tickSoundVolume();
	}

	void toggleTimelineStyle()
	{
		boolean circularNow = config.timelineStyle() == TimelineStyle.CIRCULAR;
		Overlay active = circularNow ? circleOverlay : overlay;
		Overlay nextOverlay = circularNow ? overlay : circleOverlay;

		// Keep the dragged window position when swapping styles — only the timeline row changes.
		java.awt.Point loc = active.getPreferredLocation();
		if (loc == null)
		{
			java.awt.Rectangle bounds = active.getBounds();
			if (bounds != null && bounds.width > 0 && bounds.height > 0)
			{
				loc = new java.awt.Point(bounds.x, bounds.y);
			}
		}
		if (loc != null)
		{
			nextOverlay.setPreferredLocation(new java.awt.Point(loc.x, loc.y));
		}

		configManager.setConfiguration(
			TickFlowConfig.GROUP,
			"timelineStyle",
			circularNow ? TimelineStyle.SQUARE : TimelineStyle.CIRCULAR);
	}

	boolean isTickSoundAudible()
	{
		return config.tickSound() && !config.tickSoundMuted();
	}

	CombatStyle getCombatStyle()
	{
		return combatStyle;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.COM_MODE
			|| event.getVarbitId() == VarbitID.COMBAT_WEAPON_CATEGORY
			|| event.getVarbitId() == VarbitID.AUTOCAST_DEFMODE)
		{
			refreshCombatStyle();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (RESET_STATES.contains(gameState) || gameState == GameState.LOGIN_SCREEN)
		{
			state.reset("game-state:" + gameState);
			lastGameTickMs = 0L;
			cachedEquipFp = null;
			cachedWeaponId = -1;
			cachedWeaponAspeed = -1;
			cachedShieldId = -1;
			cachedAmmoId = -1;
			combatStyle = CombatStyle.MELEE;
		}
		else if (gameState == GameState.LOGGED_IN)
		{
			refreshClientCachesIfLoggedIn();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		lastGameTickMs = System.currentTimeMillis();

		if (isTickSoundAudible())
		{
			metronome.play();
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			state.reset("no-local-player");
			return;
		}

		String location = formatWorldPoint(local.getWorldLocation());
		String target = resolveTargetIdentity(local);
		long prayerMask = samplePrayerMask();
		int animationId = local.getAnimation();
		refreshEquipmentCache();
		state.beginNextTick(location, target, prayerMask, animationId, cachedEquipFp, cachedWeaponAspeed);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		MenuAction menuAction = event.getMenuAction();
		String option = event.getMenuOption();
		String target = event.getMenuTarget();
		if (config.debugMode())
		{
			state.recordMenuDebug(option, target);
		}

		TickAction action = classifier.classifyMenu(menuAction, option, target, 0);
		if (action != null)
		{
			state.recordAction(action);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (local == null || actor != local)
		{
			return;
		}
		int animation = local.getAnimation();
		if (animation <= 0 || local.getInteracting() == null)
		{
			return;
		}
		// At most one low-confidence animation corroboration per tick.
		state.recordAnimationAttack(classifier.attackFromAnimation(0));
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player local = client.getLocalPlayer();
		if (local != null && event.getActor() == local)
		{
			state.reset("local-player-death");
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.WORN)
		{
			return;
		}
		refreshEquipmentCache();
		refreshCombatStyle();
		state.getAttackCycleTracker().noteEquipmentFingerprint(cachedEquipFp);
		if (cachedWeaponAspeed > 0)
		{
			state.getAttackCycleTracker().setWeaponSpeedFromMetadata(cachedWeaponAspeed);
		}
		else
		{
			state.getAttackCycleTracker().clearWeaponMetadata();
		}
	}

	private void refreshEquipmentCache()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			cachedEquipFp = null;
			cachedWeaponId = -1;
			cachedWeaponAspeed = -1;
			cachedShieldId = -1;
			cachedAmmoId = -1;
			return;
		}
		Item[] items = equipment.getItems();
		int weaponId = itemId(items, EquipmentInventorySlot.WEAPON);
		int shieldId = itemId(items, EquipmentInventorySlot.SHIELD);
		int ammoId = itemId(items, EquipmentInventorySlot.AMMO);
		if (weaponId == cachedWeaponId
			&& shieldId == cachedShieldId
			&& ammoId == cachedAmmoId
			&& cachedEquipFp != null)
		{
			return;
		}
		cachedShieldId = shieldId;
		cachedAmmoId = ammoId;
		cachedEquipFp = weaponId + ":" + shieldId + ":" + ammoId;
		if (weaponId != cachedWeaponId)
		{
			cachedWeaponId = weaponId;
			cachedWeaponAspeed = resolveWeaponAspeed(weaponId);
		}
	}

	private void refreshCombatStyle()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			combatStyle = CombatStyle.MELEE;
			return;
		}
		combatStyle = CombatStyleResolver.resolve(client);
	}

	private long samplePrayerMask()
	{
		long mask = 0L;
		int limit = Math.min(PRAYERS.length, 63);
		for (int i = 0; i < limit; i++)
		{
			if (client.isPrayerActive(PRAYERS[i]))
			{
				mask |= 1L << i;
			}
		}
		return mask;
	}

	@Nullable
	private String resolveTargetIdentity(Player local)
	{
		Actor interacting = local.getInteracting();
		if (interacting == null)
		{
			return null;
		}
		String name = interacting.getName();
		if (name == null)
		{
			return "actor:" + System.identityHashCode(interacting);
		}
		return name + "#" + System.identityHashCode(interacting);
	}

	private int resolveWeaponAspeed(int weaponId)
	{
		if (weaponId <= 0)
		{
			return -1;
		}
		ItemStats stats = itemManager.getItemStats(weaponId);
		if (stats == null)
		{
			return -1;
		}
		ItemEquipmentStats equipmentStats = stats.getEquipment();
		if (equipmentStats == null)
		{
			return -1;
		}
		return equipmentStats.getAspeed();
	}

	private static int itemId(Item[] items, EquipmentInventorySlot slot)
	{
		int idx = slot.getSlotIdx();
		if (items == null || idx < 0 || idx >= items.length || items[idx] == null)
		{
			return -1;
		}
		return items[idx].getId();
	}

	@Nullable
	private static String formatWorldPoint(@Nullable WorldPoint point)
	{
		if (point == null)
		{
			return null;
		}
		return point.getX() + "," + point.getY() + "," + point.getPlane();
	}
}
