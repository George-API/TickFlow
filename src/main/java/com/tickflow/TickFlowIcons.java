package com.tickflow;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;

/**
 * Loads and caches official OSRS UI sprites for timeline actions.
 * Icons are fitted with nearest-neighbor scaling (never SCALE_SMOOTH) so pixel art stays sharp.
 */
@Singleton
public class TickFlowIcons
{
	/** Max edge for primary timeline icons. Native size kept when already smaller. */
	static final int ICON_MAX = 24;
	/** Max edge for secondary badge icons. */
	static final int ICON_SMALL = 12;

	private final SpriteManager spriteManager;
	private final Map<ActionType, BufferedImage> typeIcons = new EnumMap<>(ActionType.class);
	private final Map<ActionType, BufferedImage> typeIconsSmall = new EnumMap<>(ActionType.class);
	private final Map<CombatStyle, BufferedImage> combatIcons = new EnumMap<>(CombatStyle.class);
	private final Map<CombatStyle, BufferedImage> combatIconsSmall = new EnumMap<>(CombatStyle.class);
	private final Map<Integer, BufferedImage> spriteIcons = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> spriteIconsSmall = new ConcurrentHashMap<>();
	private final java.util.Set<Integer> pendingSprites = ConcurrentHashMap.newKeySet();
	private boolean requested;

	@Inject
	private TickFlowIcons(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	public void ensureLoaded()
	{
		if (requested)
		{
			return;
		}
		requested = true;

		requestCombat(CombatStyle.MELEE, SpriteID.Staticons.ATTACK);
		requestCombat(CombatStyle.RANGED, SpriteID.Staticons.RANGED);
		requestCombat(CombatStyle.MAGIC, SpriteID.Staticons.MAGIC);
		requestType(ActionType.ATTACK, SpriteID.Staticons.ATTACK);
		requestType(ActionType.MOVE, SpriteID.OrbIcon.RUN);
		requestType(ActionType.PRAYER, SpriteID.OrbIcon.PRAYER);
		requestType(ActionType.CONSUMABLE, SpriteID.Staticons.HITPOINTS);
		requestType(ActionType.OTHER, SpriteID.SideIcons.INVENTORY);
	}

	public void clear()
	{
		typeIcons.clear();
		typeIconsSmall.clear();
		combatIcons.clear();
		combatIconsSmall.clear();
		spriteIcons.clear();
		spriteIconsSmall.clear();
		pendingSprites.clear();
		requested = false;
	}

	@Nullable
	public BufferedImage get(ActionType type)
	{
		if (type == null || type == ActionType.EMPTY)
		{
			return null;
		}
		ensureLoaded();
		return typeIcons.get(type);
	}

	@Nullable
	public BufferedImage getSmall(ActionType type)
	{
		if (type == null || type == ActionType.EMPTY)
		{
			return null;
		}
		ensureLoaded();
		BufferedImage small = typeIconsSmall.get(type);
		return small != null ? small : typeIcons.get(type);
	}

	@Nullable
	public BufferedImage getAttack(@Nullable CombatStyle style)
	{
		ensureLoaded();
		CombatStyle key = style == null ? CombatStyle.MELEE : style;
		BufferedImage icon = combatIcons.get(key);
		return icon != null ? icon : typeIcons.get(ActionType.ATTACK);
	}

	@Nullable
	public BufferedImage getAttackSmall(@Nullable CombatStyle style)
	{
		ensureLoaded();
		CombatStyle key = style == null ? CombatStyle.MELEE : style;
		BufferedImage icon = combatIconsSmall.get(key);
		return icon != null ? icon : getAttack(key);
	}

	@Nullable
	public BufferedImage get(@Nullable TickAction action)
	{
		if (action == null)
		{
			return null;
		}
		if (action.hasSpriteId())
		{
			return getSprite(action.getSpriteId());
		}
		return get(action.getType());
	}

	@Nullable
	public BufferedImage getSprite(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}
		ensureLoaded();
		BufferedImage cached = spriteIcons.get(spriteId);
		if (cached != null)
		{
			return cached;
		}
		requestSprite(spriteId);
		return spriteIcons.get(spriteId);
	}

	@Nullable
	public BufferedImage getSpriteSmall(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}
		ensureLoaded();
		BufferedImage small = spriteIconsSmall.get(spriteId);
		if (small != null)
		{
			return small;
		}
		BufferedImage full = getSprite(spriteId);
		if (full == null)
		{
			return null;
		}
		BufferedImage fitted = crispFit(full, ICON_SMALL);
		spriteIconsSmall.put(spriteId, fitted);
		return fitted;
	}

	private void requestCombat(CombatStyle style, int spriteId)
	{
		spriteManager.getSpriteAsync(spriteId, 0, image ->
		{
			if (image == null)
			{
				return;
			}
			BufferedImage full = crispFit(image, ICON_MAX);
			BufferedImage small = crispFit(image, ICON_SMALL);
			combatIcons.put(style, full);
			combatIconsSmall.put(style, small);
			spriteIcons.put(spriteId, full);
			spriteIconsSmall.put(spriteId, small);
			if (style == CombatStyle.MELEE)
			{
				typeIcons.put(ActionType.ATTACK, full);
				typeIconsSmall.put(ActionType.ATTACK, small);
			}
		});
	}

	private void requestType(ActionType type, int spriteId)
	{
		if (type == ActionType.ATTACK)
		{
			// Loaded via requestCombat(MELEE) so melee/ranged/magic stay in sync.
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, image ->
		{
			if (image == null)
			{
				return;
			}
			BufferedImage full = crispFit(image, ICON_MAX);
			BufferedImage small = crispFit(image, ICON_SMALL);
			typeIcons.put(type, full);
			typeIconsSmall.put(type, small);
			spriteIcons.put(spriteId, full);
			spriteIconsSmall.put(spriteId, small);
		});
	}

	private void requestSprite(int spriteId)
	{
		if (spriteIcons.containsKey(spriteId) || !pendingSprites.add(spriteId))
		{
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, image ->
		{
			pendingSprites.remove(spriteId);
			if (image == null)
			{
				return;
			}
			BufferedImage full = crispFit(image, ICON_MAX);
			BufferedImage small = crispFit(image, ICON_SMALL);
			spriteIcons.put(spriteId, full);
			spriteIconsSmall.put(spriteId, small);
		});
	}

	/**
	 * Fit inside {@code max}×{@code max} preserving aspect ratio.
	 * Uses nearest-neighbor only — never bilinear/smooth — so OSRS pixel art stays sharp.
	 * Does not upscale; returns an ARGB copy when already small enough.
	 */
	static BufferedImage crispFit(BufferedImage source, int max)
	{
		int sw = source.getWidth();
		int sh = source.getHeight();
		if (sw <= 0 || sh <= 0)
		{
			return source;
		}

		int dw;
		int dh;
		if (sw <= max && sh <= max)
		{
			dw = sw;
			dh = sh;
		}
		else if (sw >= sh)
		{
			dw = max;
			dh = Math.max(1, (int) Math.round(sh * (max / (double) sw)));
		}
		else
		{
			dh = max;
			dw = Math.max(1, (int) Math.round(sw * (max / (double) sh)));
		}

		if (dw == sw && dh == sh && source.getType() == BufferedImage.TYPE_INT_ARGB)
		{
			return source;
		}

		BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
			if (dw == sw && dh == sh)
			{
				g.drawImage(source, 0, 0, null);
			}
			else
			{
				g.drawImage(source, 0, 0, dw, dh, null);
			}
		}
		finally
		{
			g.dispose();
		}
		return out;
	}
}
