package com.tickflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;

/**
 * Maps each {@link Prayer} to its official on-state prayer book sprite.
 */
final class PrayerSprites
{
	/** Neutral prayer-orb sprite used when the specific prayer is not yet known. */
	static final int GENERIC_SPRITE = SpriteID.OrbIcon.PRAYER;

	private static final Map<Prayer, Integer> SPRITES = new EnumMap<>(Prayer.class);
	private static final List<NameMatch> NAME_MATCHES = new ArrayList<>();

	static
	{
		SPRITES.put(Prayer.THICK_SKIN, SpriteID.Prayeron.THICK_SKIN);
		SPRITES.put(Prayer.BURST_OF_STRENGTH, SpriteID.Prayeron.BURST_OF_STRENGTH);
		SPRITES.put(Prayer.CLARITY_OF_THOUGHT, SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		SPRITES.put(Prayer.SHARP_EYE, SpriteID.Prayeron.SHARP_EYE);
		SPRITES.put(Prayer.MYSTIC_WILL, SpriteID.Prayeron.MYSTIC_WILL);
		SPRITES.put(Prayer.ROCK_SKIN, SpriteID.Prayeron.ROCK_SKIN);
		SPRITES.put(Prayer.SUPERHUMAN_STRENGTH, SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		SPRITES.put(Prayer.IMPROVED_REFLEXES, SpriteID.Prayeron.IMPROVED_REFLEXES);
		SPRITES.put(Prayer.RAPID_RESTORE, SpriteID.Prayeron.RAPID_RESTORE);
		SPRITES.put(Prayer.RAPID_HEAL, SpriteID.Prayeron.RAPID_HEAL);
		SPRITES.put(Prayer.PROTECT_ITEM, SpriteID.Prayeron.PROTECT_ITEM);
		SPRITES.put(Prayer.HAWK_EYE, SpriteID.Prayeron.HAWK_EYE);
		SPRITES.put(Prayer.MYSTIC_LORE, SpriteID.Prayeron.MYSTIC_LORE);
		SPRITES.put(Prayer.STEEL_SKIN, SpriteID.Prayeron.STEEL_SKIN);
		SPRITES.put(Prayer.ULTIMATE_STRENGTH, SpriteID.Prayeron.ULTIMATE_STRENGTH);
		SPRITES.put(Prayer.INCREDIBLE_REFLEXES, SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		SPRITES.put(Prayer.PROTECT_FROM_MAGIC, SpriteID.Prayeron.PROTECT_FROM_MAGIC);
		SPRITES.put(Prayer.PROTECT_FROM_MISSILES, SpriteID.Prayeron.PROTECT_FROM_MISSILES);
		SPRITES.put(Prayer.PROTECT_FROM_MELEE, SpriteID.Prayeron.PROTECT_FROM_MELEE);
		SPRITES.put(Prayer.EAGLE_EYE, SpriteID.Prayeron.EAGLE_EYE);
		SPRITES.put(Prayer.MYSTIC_MIGHT, SpriteID.Prayeron.MYSTIC_MIGHT);
		SPRITES.put(Prayer.RETRIBUTION, SpriteID.Prayeron.RETRIBUTION);
		SPRITES.put(Prayer.REDEMPTION, SpriteID.Prayeron.REDEMPTION);
		SPRITES.put(Prayer.SMITE, SpriteID.Prayeron.SMITE);
		SPRITES.put(Prayer.CHIVALRY, SpriteID.Prayeron.CHIVALRY);
		SPRITES.put(Prayer.DEADEYE, SpriteID.Prayeron.DEADEYE);
		SPRITES.put(Prayer.MYSTIC_VIGOUR, SpriteID.Prayeron.MYSTIC_VIGOUR);
		SPRITES.put(Prayer.PIETY, SpriteID.Prayeron.PIETY);
		SPRITES.put(Prayer.PRESERVE, SpriteID.Prayeron.PRESERVE);
		SPRITES.put(Prayer.RIGOUR, SpriteID.Prayeron.RIGOUR);
		SPRITES.put(Prayer.AUGURY, SpriteID.Prayeron.AUGURY);

		SPRITES.put(Prayer.RP_REJUVENATION, SpriteID.IconPrayerZaros01_30x30.REJUVENATION);
		SPRITES.put(Prayer.RP_ANCIENT_STRENGTH, SpriteID.IconPrayerZaros01_30x30.ANCIENT_STRENGTH);
		SPRITES.put(Prayer.RP_ANCIENT_SIGHT, SpriteID.IconPrayerZaros01_30x30.ANCIENT_SIGHT);
		SPRITES.put(Prayer.RP_ANCIENT_WILL, SpriteID.IconPrayerZaros01_30x30.ANCIENT_WILL);
		SPRITES.put(Prayer.RP_PROTECT_ITEM, SpriteID.IconPrayerZaros01_30x30.PROTECT_ITEM);
		SPRITES.put(Prayer.RP_RUINOUS_GRACE, SpriteID.IconPrayerZaros01_30x30.RUINOUS_GRACE);
		SPRITES.put(Prayer.RP_DAMPEN_MAGIC, SpriteID.IconPrayerZaros01_30x30.DAMPEN_MAGIC);
		SPRITES.put(Prayer.RP_DAMPEN_RANGED, SpriteID.IconPrayerZaros01_30x30.DAMPEN_RANGED);
		SPRITES.put(Prayer.RP_DAMPEN_MELEE, SpriteID.IconPrayerZaros01_30x30.DAMPEN_MELEE);
		SPRITES.put(Prayer.RP_TRINITAS, SpriteID.IconPrayerZaros01_30x30.TRINITAS);
		SPRITES.put(Prayer.RP_BERSERKER, SpriteID.IconPrayerZaros01_30x30.BERSERKER);
		SPRITES.put(Prayer.RP_PURGE, SpriteID.IconPrayerZaros01_30x30.PURGE);
		SPRITES.put(Prayer.RP_METABOLISE, SpriteID.IconPrayerZaros01_30x30.METABOLISE);
		SPRITES.put(Prayer.RP_REBUKE, SpriteID.IconPrayerZaros01_30x30.REBUKE);
		SPRITES.put(Prayer.RP_VINDICATION, SpriteID.IconPrayerZaros01_30x30.VINDICATION);
		SPRITES.put(Prayer.RP_DECIMATE, SpriteID.IconPrayerZaros01_30x30.DECIMATE);
		SPRITES.put(Prayer.RP_ANNIHILATE, SpriteID.IconPrayerZaros01_30x30.ANNIHILATE);
		SPRITES.put(Prayer.RP_VAPORISE, SpriteID.IconPrayerZaros01_30x30.VAPORISE);
		SPRITES.put(Prayer.RP_FUMUS_VOW, SpriteID.IconPrayerZaros01_30x30.FUMUS_VOW);
		SPRITES.put(Prayer.RP_UMBRA_VOW, SpriteID.IconPrayerZaros01_30x30.UMBRAS_VOW);
		SPRITES.put(Prayer.RP_CRUORS_VOW, SpriteID.IconPrayerZaros01_30x30.CRUORS_VOW);
		SPRITES.put(Prayer.RP_GLACIES_VOW, SpriteID.IconPrayerZaros01_30x30.GLACIES_VOW);
		SPRITES.put(Prayer.RP_WRATH, SpriteID.IconPrayerZaros01_30x30.WRATH);
		SPRITES.put(Prayer.RP_INTENSIFY, SpriteID.IconPrayerZaros01_30x30.INTENSIFY);

		for (Prayer prayer : Prayer.values())
		{
			String pretty = prettyName(prayer.name()).toLowerCase(Locale.ROOT);
			NAME_MATCHES.add(new NameMatch(pretty, prayer));
			String underscored = pretty.replace(' ', '_');
			if (!underscored.equals(pretty))
			{
				NAME_MATCHES.add(new NameMatch(underscored, prayer));
			}
		}
		// Longer names first so "protect from melee" wins over "protect item".
		NAME_MATCHES.sort(Comparator.comparingInt((NameMatch m) -> m.name.length()).reversed());
	}

	private PrayerSprites()
	{
	}

	static int spriteId(@Nullable Prayer prayer)
	{
		if (prayer == null)
		{
			return GENERIC_SPRITE;
		}
		Integer id = SPRITES.get(prayer);
		return id != null ? id : GENERIC_SPRITE;
	}

	/**
	 * Resolve a prayer from menu option/target text (e.g. "Activate Piety").
	 */
	@Nullable
	static Prayer matchFromText(@Nullable String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}
		String haystack = text.toLowerCase(Locale.ROOT);
		for (NameMatch match : NAME_MATCHES)
		{
			if (haystack.contains(match.name))
			{
				return match.prayer;
			}
		}
		return null;
	}

	/**
	 * Compact overlay label. Icons already identify the prayer, so long names
	 * (e.g. Protect From Melee) collapse to a short stem that fits a tick slot.
	 */
	static String shortLabel(@Nullable Prayer prayer)
	{
		if (prayer == null)
		{
			return ActionType.PRAYER.getShortLabel();
		}

		switch (prayer)
		{
			case PROTECT_FROM_MAGIC:
			case PROTECT_FROM_MISSILES:
			case PROTECT_FROM_MELEE:
			case PROTECT_ITEM:
			case RP_PROTECT_ITEM:
				return "Protect";
			case RP_DAMPEN_MAGIC:
			case RP_DAMPEN_RANGED:
			case RP_DAMPEN_MELEE:
				return "Dampen";
			case BURST_OF_STRENGTH:
				return "Burst";
			case CLARITY_OF_THOUGHT:
				return "Clarity";
			case SUPERHUMAN_STRENGTH:
			case ULTIMATE_STRENGTH:
				return "Strength";
			case IMPROVED_REFLEXES:
			case INCREDIBLE_REFLEXES:
				return "Reflexes";
			case RAPID_RESTORE:
				return "Restore";
			case RAPID_HEAL:
				return "Heal";
			case THICK_SKIN:
				return "Thick";
			case ROCK_SKIN:
				return "Rock";
			case STEEL_SKIN:
				return "Steel";
			case SHARP_EYE:
				return "Sharp";
			case HAWK_EYE:
				return "Hawk";
			case EAGLE_EYE:
				return "Eagle";
			case DEADEYE:
				return "Deadeye";
			case MYSTIC_WILL:
				return "Will";
			case MYSTIC_LORE:
				return "Lore";
			case MYSTIC_MIGHT:
				return "Might";
			case MYSTIC_VIGOUR:
				return "Vigour";
			case RETRIBUTION:
				return "Retrib";
			case REDEMPTION:
				return "Redeem";
			case RP_ANCIENT_STRENGTH:
			case RP_ANCIENT_SIGHT:
			case RP_ANCIENT_WILL:
				return "Ancient";
			case RP_RUINOUS_GRACE:
				return "Grace";
			case RP_FUMUS_VOW:
			case RP_UMBRA_VOW:
			case RP_CRUORS_VOW:
			case RP_GLACIES_VOW:
				return "Vow";
			case RP_REJUVENATION:
				return "Rejuv";
			case RP_METABOLISE:
				return "Metab";
			case RP_VINDICATION:
				return "Vindic";
			case RP_ANNIHILATE:
				return "Annihil";
			default:
				return firstWordLabel(prettyName(prayer.name()));
		}
	}

	/** Prefer a single short word when the pretty name would overflow a slot. */
	private static String firstWordLabel(String pretty)
	{
		if (pretty.length() <= 8)
		{
			return pretty;
		}
		int space = pretty.indexOf(' ');
		if (space > 0 && space <= 8)
		{
			return pretty.substring(0, space);
		}
		return pretty.substring(0, 8);
	}

	/**
	 * Prefer an activated prayer for the icon; otherwise the first deactivated one.
	 */
	@Nullable
	static Prayer pickChangedPrayer(long previousMask, long currentMask)
	{
		long delta = previousMask ^ currentMask;
		if (delta == 0)
		{
			return null;
		}

		Prayer[] prayers = Prayer.values();
		int limit = Math.min(prayers.length, 63);

		Prayer activated = null;
		Prayer deactivated = null;
		for (int i = 0; i < limit; i++)
		{
			if ((delta & (1L << i)) == 0)
			{
				continue;
			}
			boolean nowOn = (currentMask & (1L << i)) != 0;
			if (nowOn && activated == null)
			{
				activated = prayers[i];
			}
			else if (!nowOn && deactivated == null)
			{
				deactivated = prayers[i];
			}
		}
		return activated != null ? activated : deactivated;
	}

	private static String prettyName(String enumName)
	{
		String cleaned = enumName.startsWith("RP_") ? enumName.substring(3) : enumName;
		String[] parts = cleaned.toLowerCase().split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts)
		{
			if (part.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1)
			{
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}

	private static final class NameMatch
	{
		private final String name;
		private final Prayer prayer;

		private NameMatch(String name, Prayer prayer)
		{
			this.name = name;
			this.prayer = prayer;
		}
	}
}
