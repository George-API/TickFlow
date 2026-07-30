package com.tickflow;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CombatStyleTest
{
	@Test
	public void mapsStyleNamesToFamilies()
	{
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Accurate"));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Controlled"));
		assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackStyleName("Ranging"));
		assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackStyleName("Longrange"));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackStyleName("Casting"));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackStyleName("Defensive casting"));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName(null));
	}
}
