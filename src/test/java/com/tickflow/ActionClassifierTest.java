package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.MenuAction;
import org.junit.Before;
import org.junit.Test;

public class ActionClassifierTest
{
	private ActionClassifier classifier;

	@Before
	public void setUp()
	{
		classifier = new ActionClassifier();
	}

	@Test
	public void classifiesWalkAsMove()
	{
		TickAction action = classifier.classifyMenu(MenuAction.WALK, "Walk here", "", 0);
		assertNotNull(action);
		assertEquals(ActionType.MOVE, action.getType());
	}

	@Test
	public void classifiesNpcAttackOption()
	{
		TickAction action = classifier.classifyMenu(MenuAction.NPC_FIRST_OPTION, "Attack", "Goblin", 0);
		assertNotNull(action);
		assertEquals(ActionType.ATTACK, action.getType());
	}

	@Test
	public void classifiesEatAsItem()
	{
		TickAction action = classifier.classifyMenu(MenuAction.CC_OP, "Eat", "Shark", 0);
		assertNotNull(action);
		assertEquals(ActionType.CONSUMABLE, action.getType());
	}

	@Test
	public void ignoresCancelAndRuneLiteMenus()
	{
		assertNull(classifier.classifyMenu(MenuAction.CANCEL, "Cancel", "", 0));
		assertNull(classifier.classifyMenu(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "TickFlow", 0));
	}

	@Test
	public void normalizesTagsInOptionText()
	{
		assertEquals("attack", ActionClassifier.normalize("<col=ff9040>Attack</col>"));
	}

	@Test
	public void prayerStateChangeHasHighConfidence()
	{
		TickAction action = classifier.prayerFromStateChange(2, 1);
		assertEquals(ActionType.PRAYER, action.getType());
		assertEquals(95, action.getConfidence());
	}

	@Test
	public void prayerStateChangeUsesSpecificSprite()
	{
		TickAction action = classifier.prayerFromStateChange(net.runelite.api.Prayer.PROTECT_FROM_MELEE, true, 0);
		assertEquals(ActionType.PRAYER, action.getType());
		assertTrue(action.hasSpriteId());
		assertEquals("Protect", action.getLabel());
	}

	@Test
	public void menuPrayerResolvesNamedSpriteImmediately()
	{
		TickAction action = classifier.classifyMenu(
			MenuAction.CC_OP,
			"Activate",
			"Piety",
			0);
		assertNotNull(action);
		assertEquals(ActionType.PRAYER, action.getType());
		assertEquals("Piety", action.getLabel());
		assertEquals(net.runelite.api.gameval.SpriteID.Prayeron.PIETY, action.getSpriteId());
	}

	@Test
	public void unknownMenuPrayerUsesNeutralOrb()
	{
		TickAction action = classifier.classifyMenu(
			MenuAction.CC_OP,
			"Activate",
			"Some Unknown Prayer Buff",
			0);
		assertNotNull(action);
		assertEquals(ActionType.PRAYER, action.getType());
		assertEquals(PrayerSprites.GENERIC_SPRITE, action.getSpriteId());
	}
}
