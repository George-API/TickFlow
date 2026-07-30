package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class TickFlowStateTest
{
	private TickFlowState state;

	@Before
	public void setUp()
	{
		state = new TickFlowState();
		state.setTimelineLength(5);
	}

	@Test
	public void rollingHistoryStaysBounded()
	{
		for (int i = 0; i < 30; i++)
		{
			state.beginNextTick("1,1,0", null, 0L, -1, "1:0:0", 4);
			state.recordAction(new TickAction(ActionType.MOVE, ActionSource.POSITION, "Move", 90, 0));
		}

		TickFlowState.Snapshot snap = state.snapshot();
		assertTrue(snap.getHistory().size() <= TickFlowState.MAX_HISTORY);
		assertTrue(snap.getHistory().size() <= Math.max(5, TickFlowState.MAX_TIMELINE));
	}

	@Test
	public void primaryActionPriorityIsDeterministic()
	{
		TickAction move = new TickAction(ActionType.MOVE, ActionSource.MENU, "Move", 90, 0);
		TickAction pray = new TickAction(ActionType.PRAYER, ActionSource.PRAYER_STATE, "Pray", 95, 1);
		TickAction attack = new TickAction(ActionType.ATTACK, ActionSource.MENU, "Attack", 85, 2);
		TickAction item = new TickAction(ActionType.CONSUMABLE, ActionSource.MENU, "Item", 70, 3);

		TickAction primary = TickFlowState.selectPrimary(Arrays.asList(move, pray, attack, item));
		assertEquals(ActionType.ATTACK, primary.getType());

		TickAction withoutAttack = TickFlowState.selectPrimary(Arrays.asList(move, pray, item));
		assertEquals(ActionType.CONSUMABLE, withoutAttack.getType());
	}

	@Test
	public void secondaryActionsArePreserved()
	{
		state.beginNextTick("1,1,0", "npc#1", 0L, -1, "1:0:0", 4);
		state.recordAction(new TickAction(ActionType.ATTACK, ActionSource.MENU, "Attack", 85, 0));
		state.recordAction(new TickAction(ActionType.MOVE, ActionSource.MENU, "Move", 80, 1));
		state.finalizeCurrentTick();

		List<TickRecord> history = state.snapshot().getHistory();
		assertEquals(1, history.size());
		TickRecord record = history.get(0);
		assertEquals(ActionType.ATTACK, record.getPrimaryAction().getType());
		assertEquals(1, record.getSecondaryActions().size());
		assertEquals(ActionType.MOVE, record.getSecondaryActions().get(0).getType());
	}

	@Test
	public void emptyTicksAreRepresentedNeutrally()
	{
		state.beginNextTick("1,1,0", null, 0L, -1, "1:0:0", -1);
		state.finalizeCurrentTick();

		TickRecord record = state.snapshot().getHistory().get(0);
		assertEquals(ActionType.EMPTY, record.getPrimaryAction().getType());
		assertEquals("Empty", record.getPrimaryAction().getLabel());
		assertTrue(record.getSecondaryActions().isEmpty());
	}

	@Test
	public void resetClearsStaleCombatTiming()
	{
		state.beginNextTick("1,1,0", "npc#1", 0L, -1, "1:0:0", 4);
		state.recordAction(new TickAction(ActionType.ATTACK, ActionSource.MENU, "Attack", 85, 0));
		state.beginNextTick("1,1,0", "npc#1", 0L, -1, "1:0:0", 4);
		assertTrue(state.getLocalTickIndex() >= 0);
		assertNotNull(state.snapshot().getCurrentTick());

		state.reset("test");
		assertEquals(-1, state.getLocalTickIndex());
		assertEquals(0, state.snapshot().getHistory().size());
		assertNull(state.snapshot().getCurrentTick());
		assertEquals(CycleConfidence.NONE, state.getAttackCycleTracker().getConfidence());
		assertEquals("test", state.getLastResetReason());
		assertFalse(state.isInCombat());
	}

	@Test
	public void movementFromTileChangeIsRecorded()
	{
		state.beginNextTick("10,10,0", null, 0L, -1, "1:0:0", 4);
		state.beginNextTick("11,10,0", null, 0L, -1, "1:0:0", 4);

		TickRecord current = state.snapshot().getCurrentTick();
		assertNotNull(current);
		assertEquals(ActionType.MOVE, current.getPrimaryAction().getType());
	}

	@Test
	public void prayerMaskChangeIsRecorded()
	{
		state.beginNextTick("10,10,0", null, 0L, -1, "1:0:0", 4);
		state.beginNextTick("10,10,0", null, 1L, -1, "1:0:0", 4);

		TickRecord current = state.snapshot().getCurrentTick();
		assertNotNull(current);
		assertEquals(ActionType.PRAYER, current.getPrimaryAction().getType());
	}

	@Test
	public void timelineLengthIsClamped()
	{
		assertEquals(5, TickFlowState.clampTimeline(1));
		assertEquals(8, TickFlowState.clampTimeline(99));
		assertEquals(6, TickFlowState.clampTimeline(6));
	}
}
