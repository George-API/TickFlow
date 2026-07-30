package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class PrayerSpritesTest
{
	@Test
	public void mapsProtectFromMeleeSprite()
	{
		assertEquals(SpriteID.Prayeron.PROTECT_FROM_MELEE, PrayerSprites.spriteId(Prayer.PROTECT_FROM_MELEE));
	}

	@Test
	public void prefersActivatedPrayer()
	{
		long previous = 0L;
		long current = 1L << Prayer.PROTECT_FROM_MELEE.ordinal();
		Prayer picked = PrayerSprites.pickChangedPrayer(previous, current);
		assertEquals(Prayer.PROTECT_FROM_MELEE, picked);
	}

	@Test
	public void fallsBackToDeactivatedPrayer()
	{
		long previous = 1L << Prayer.PIETY.ordinal();
		long current = 0L;
		Prayer picked = PrayerSprites.pickChangedPrayer(previous, current);
		assertEquals(Prayer.PIETY, picked);
	}

	@Test
	public void noChangeReturnsNull()
	{
		assertNull(PrayerSprites.pickChangedPrayer(3L, 3L));
	}

	@Test
	public void shortLabelFitsSlot()
	{
		assertEquals("Protect", PrayerSprites.shortLabel(Prayer.PROTECT_FROM_MELEE));
		assertEquals("Protect", PrayerSprites.shortLabel(Prayer.PROTECT_FROM_MAGIC));
		assertEquals("Dampen", PrayerSprites.shortLabel(Prayer.RP_DAMPEN_MELEE));
		assertEquals("Piety", PrayerSprites.shortLabel(Prayer.PIETY));
		assertEquals("Ancient", PrayerSprites.shortLabel(Prayer.RP_ANCIENT_STRENGTH));
		assertNotNull(PrayerSprites.shortLabel(null));
	}

	@Test
	public void unknownPrayerUsesNeutralOrbSprite()
	{
		assertEquals(SpriteID.OrbIcon.PRAYER, PrayerSprites.spriteId(null));
		assertEquals(SpriteID.OrbIcon.PRAYER, PrayerSprites.GENERIC_SPRITE);
	}

	@Test
	public void matchFromTextResolvesPrayerNames()
	{
		assertEquals(Prayer.PIETY, PrayerSprites.matchFromText("activate piety"));
		assertEquals(Prayer.PROTECT_FROM_MELEE, PrayerSprites.matchFromText("protect from melee"));
		assertEquals(Prayer.PROTECT_ITEM, PrayerSprites.matchFromText("protect item"));
		assertNull(PrayerSprites.matchFromText("shark"));
	}
}
