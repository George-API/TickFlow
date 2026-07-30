package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AttackCycleTrackerTest
{
	private AttackCycleTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new AttackCycleTracker();
	}

	@Test
	public void acquiresFourTickCadenceFromMetadata()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.onCredibleAttack(10);

		assertEquals(4, tracker.getCandidateAttackSpeedTicks());
		assertEquals(14, tracker.getNextReadyTick());
		assertEquals(CycleConfidence.HIGH, tracker.getConfidence());
		assertTrue(tracker.isReadyAt(14));
		assertFalse(tracker.isReadyAt(13));
	}

	@Test
	public void calculatesCorrectNextReadyTick()
	{
		tracker.setWeaponSpeedFromMetadata(5);
		tracker.onCredibleAttack(100);
		assertEquals(105, tracker.getNextReadyTick());
		assertEquals(3, tracker.ticksUntilReady(102));
		assertEquals(0, tracker.ticksUntilReady(105));
	}

	@Test
	public void reportsZeroDeltaForOnTimeAttack()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.onCredibleAttack(0);
		CycleFeedback feedback = tracker.onCredibleAttack(4);

		assertNotNull(feedback);
		assertEquals(0, feedback.getDeltaTicks());
		assertTrue(feedback.getSummary().toLowerCase().contains("ready"));
	}

	@Test
	public void reportsPlusOneForLateAttack()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.onCredibleAttack(0);
		CycleFeedback feedback = tracker.onCredibleAttack(5);

		assertNotNull(feedback);
		assertEquals(1, feedback.getDeltaTicks());
		assertTrue(feedback.getSummary().contains("1"));
	}

	@Test
	public void rejectsImpossibleEarlyAttackAndReacquires()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.onCredibleAttack(0);
		CycleFeedback feedback = tracker.onCredibleAttack(2);

		assertNull(feedback);
		assertEquals(CycleConfidence.NONE, tracker.getConfidence());
		assertEquals(AttackCyclePhase.ACQUIRING, tracker.getPhase());
	}

	@Test
	public void resetsOnEquipmentChange()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.noteEquipmentFingerprint("1:2:3");
		tracker.onCredibleAttack(10);
		assertEquals(CycleConfidence.HIGH, tracker.getConfidence());

		tracker.noteEquipmentFingerprint("9:2:3");
		assertEquals(CycleConfidence.NONE, tracker.getConfidence());
		assertEquals(-1, tracker.getLastCredibleAttackTick());
	}

	@Test
	public void downgradesOnTargetChange()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.noteTarget("goblin#1");
		tracker.onCredibleAttack(10);
		assertEquals(CycleConfidence.HIGH, tracker.getConfidence());

		tracker.noteTarget("cow#2");
		assertEquals(CycleConfidence.LOW, tracker.getConfidence());
	}

	@Test
	public void resetsOnInactivity()
	{
		tracker.setWeaponSpeedFromMetadata(4);
		tracker.onCredibleAttack(10);
		tracker.onTick(10 + AttackCycleTracker.INACTIVITY_TIMEOUT_TICKS);
		assertEquals(CycleConfidence.NONE, tracker.getConfidence());
		assertEquals(AttackCyclePhase.IDLE, tracker.getPhase());
	}

	@Test
	public void producesNoFeedbackAtLowConfidence()
	{
		// Observed cadence without weapon metadata needs multiple agreeing intervals
		// for high confidence. A single interval should not produce feedback yet.
		tracker.onCredibleAttack(0);
		CycleFeedback first = tracker.onCredibleAttack(4);
		assertNull(first);

		CycleFeedback second = tracker.onCredibleAttack(8);
		// Still low/none until enough agreeing intervals elevate confidence for feedback.
		// After two intervals of 4, consensus may reach LOW which does not allow feedback.
		assertNull(second);

		assertFalse(tracker.getConfidence().allowsFeedback());
	}

	@Test
	public void acquiresHighConfidenceFromRepeatedIntervals()
	{
		tracker.onCredibleAttack(0);
		tracker.onCredibleAttack(4);
		tracker.onCredibleAttack(8);
		tracker.onCredibleAttack(12);

		assertEquals(4, tracker.getCandidateAttackSpeedTicks());
		assertEquals(CycleConfidence.HIGH, tracker.getConfidence());

		CycleFeedback feedback = tracker.onCredibleAttack(16);
		assertNotNull(feedback);
		assertEquals(0, feedback.getDeltaTicks());
	}

	@Test
	public void unknownSpeedHidesReadiness()
	{
		tracker.onCredibleAttack(0);
		assertFalse(tracker.getConfidence().allowsReadiness());
		assertEquals(-1, tracker.ticksUntilReady(1));
		assertFalse(tracker.isReadyAt(4));
	}
}
