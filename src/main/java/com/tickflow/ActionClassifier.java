package com.tickflow;

import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.MenuAction;

/**
 * Deterministic, conservative conversion of raw observations into semantic actions.
 * Prefer opcode / enum checks over broad string matching. String matching is isolated here.
 */
public final class ActionClassifier
{
	private static final Set<String> ATTACK_OPTIONS = Set.of(
		"attack",
		"cast",
		"hit"
	);

	private static final Set<String> ITEM_OPTIONS = Set.of(
		"eat",
		"drink",
		"use",
		"empty",
		"break",
		"bury",
		"scatter"
	);

	private static final Set<String> PRAYER_HINTS = Set.of(
		"activate",
		"deactivate",
		"prayer"
	);

	/**
	 * Classify a menu option click. Returns null when the event is not relevant.
	 */
	@Nullable
	public TickAction classifyMenu(
		MenuAction menuAction,
		@Nullable String option,
		@Nullable String target,
		int order)
	{
		if (menuAction == null || menuAction == MenuAction.CANCEL || isRuneLiteMenu(menuAction))
		{
			return null;
		}

		String normalizedOption = normalize(option);
		String normalizedTarget = normalize(target);

		if (menuAction == MenuAction.WALK)
		{
			return new TickAction(ActionType.MOVE, ActionSource.MENU, "Move", 80, order, "walk");
		}

		if (isNpcAttack(menuAction, normalizedOption))
		{
			int confidence = ATTACK_OPTIONS.contains(normalizedOption) ? 85 : 70;
			return new TickAction(ActionType.ATTACK, ActionSource.MENU, "Attack", confidence, order, option);
		}

		if (isPlayerAttack(menuAction, normalizedOption))
		{
			return new TickAction(ActionType.ATTACK, ActionSource.MENU, "Attack", 75, order, option);
		}

		if (isItemUse(menuAction, normalizedOption))
		{
			String label = inferItemLabel(normalizedOption);
			return new TickAction(ActionType.CONSUMABLE, ActionSource.MENU, label, 70, order, option);
		}

		if (looksLikePrayer(normalizedOption, normalizedTarget))
		{
			net.runelite.api.Prayer prayer = PrayerSprites.matchFromText(normalizedTarget);
			if (prayer == null)
			{
				prayer = PrayerSprites.matchFromText(normalizedOption);
			}
			if (prayer != null)
			{
				return new TickAction(
					ActionType.PRAYER,
					ActionSource.MENU,
					PrayerSprites.shortLabel(prayer),
					80,
					order,
					option,
					PrayerSprites.spriteId(prayer));
			}
			// Unknown prayer yet — neutral orb icon, never a specific prayer sprite.
			return new TickAction(
				ActionType.PRAYER,
				ActionSource.MENU,
				"Pray",
				60,
				order,
				option,
				PrayerSprites.GENERIC_SPRITE);
		}

		if (menuAction == MenuAction.CC_OP
			|| menuAction == MenuAction.CC_OP_LOW_PRIORITY
			|| menuAction == MenuAction.WIDGET_FIRST_OPTION
			|| menuAction == MenuAction.WIDGET_TYPE_1)
		{
			if (ITEM_OPTIONS.contains(normalizedOption))
			{
				return new TickAction(ActionType.CONSUMABLE, ActionSource.MENU, inferItemLabel(normalizedOption), 65, order, option);
			}
			return new TickAction(ActionType.OTHER, ActionSource.MENU, shortLabel(option), 40, order, option);
		}

		return null;
	}

	public TickAction movementFromPositionChange(int order)
	{
		return new TickAction(ActionType.MOVE, ActionSource.POSITION, "Move", 90, order, "tile-change");
	}

	public TickAction prayerFromStateChange(int changedCount, int order)
	{
		String meta = "changed=" + changedCount;
		return new TickAction(
			ActionType.PRAYER,
			ActionSource.PRAYER_STATE,
			"Pray",
			95,
			order,
			meta,
			PrayerSprites.GENERIC_SPRITE);
	}

	public TickAction prayerFromStateChange(net.runelite.api.Prayer prayer, boolean activated, int order)
	{
		String label = PrayerSprites.shortLabel(prayer);
		String meta = (activated ? "on:" : "off:") + (prayer == null ? "?" : prayer.name());
		return new TickAction(
			ActionType.PRAYER,
			ActionSource.PRAYER_STATE,
			label,
			95,
			order,
			meta,
			PrayerSprites.spriteId(prayer));
	}

	public TickAction attackFromAnimation(int order)
	{
		return new TickAction(ActionType.ATTACK, ActionSource.ANIMATION, "Attack", 55, order, "animation");
	}

	public TickAction other(String label, ActionSource source, int confidence, int order)
	{
		return new TickAction(ActionType.OTHER, source, label, confidence, order, null);
	}

	static String normalize(@Nullable String value)
	{
		if (value == null)
		{
			return "";
		}
		String stripped = value.replaceAll("<[^>]*>", " ");
		return stripped.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static boolean isRuneLiteMenu(MenuAction menuAction)
	{
		switch (menuAction)
		{
			case RUNELITE:
			case RUNELITE_OVERLAY:
			case RUNELITE_OVERLAY_CONFIG:
			case RUNELITE_HIGH_PRIORITY:
			case RUNELITE_LOW_PRIORITY:
			case RUNELITE_INFOBOX:
			case RUNELITE_PLAYER:
			case RUNELITE_WIDGET:
				return true;
			default:
				return false;
		}
	}

	private static boolean isNpcAttack(MenuAction menuAction, String option)
	{
		if (!ATTACK_OPTIONS.contains(option))
		{
			return false;
		}
		switch (menuAction)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case WIDGET_TARGET_ON_NPC:
				return true;
			default:
				return false;
		}
	}

	private static boolean isPlayerAttack(MenuAction menuAction, String option)
	{
		switch (menuAction)
		{
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
			case WIDGET_TARGET_ON_PLAYER:
				return ATTACK_OPTIONS.contains(option);
			default:
				return false;
		}
	}

	private static boolean isItemUse(MenuAction menuAction, String option)
	{
		if (ITEM_OPTIONS.contains(option))
		{
			return true;
		}
		// Legacy item menu opcodes still appear in some clients/paths.
		return menuAction == MenuAction.ITEM_USE
			|| menuAction == MenuAction.ITEM_FIRST_OPTION
			|| menuAction == MenuAction.ITEM_SECOND_OPTION
			|| menuAction == MenuAction.ITEM_THIRD_OPTION
			|| menuAction == MenuAction.ITEM_FOURTH_OPTION
			|| menuAction == MenuAction.ITEM_FIFTH_OPTION;
	}

	private static boolean looksLikePrayer(String option, String target)
	{
		if (PRAYER_HINTS.contains(option))
		{
			return true;
		}
		return target.contains("prayer") && (option.contains("activate") || option.contains("deactivate") || option.isEmpty());
	}

	private static String inferItemLabel(String option)
	{
		if ("eat".equals(option) || "drink".equals(option))
		{
			return "Item";
		}
		return "Item";
	}

	private static String shortLabel(@Nullable String option)
	{
		if (option == null || option.isEmpty())
		{
			return ActionType.OTHER.getShortLabel();
		}
		String cleaned = option.replaceAll("<[^>]*>", "").trim();
		if (cleaned.length() > 8)
		{
			return cleaned.substring(0, 8);
		}
		return cleaned;
	}
}
