package com.tickflow;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Broad combat skill family used for attack icons.
 */
public enum CombatStyle
{
	MELEE,
	RANGED,
	MAGIC;

	/**
	 * Map an OSRS attack-style name (from weapon-style structs) to a skill family.
	 */
	static CombatStyle fromAttackStyleName(@Nullable String name)
	{
		if (name == null || name.isEmpty())
		{
			return MELEE;
		}
		switch (name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'))
		{
			case "RANGING":
			case "LONGRANGE":
				return RANGED;
			case "CASTING":
			case "DEFENSIVE_CASTING":
				return MAGIC;
			default:
				return MELEE;
		}
	}
}
