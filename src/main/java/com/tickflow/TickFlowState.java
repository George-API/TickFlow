package com.tickflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Bounded session model for the rolling tick timeline and combat timing.
 * Client-thread confined. Overlay must only read {@link Snapshot} values.
 *
 * <p><b>Tick assignment convention:</b> menu/input observations are attached to the
 * currently open tick as they arrive. On each {@code GameTick}, the open tick is
 * finalized into history (after reconciling position/prayer/equipment deltas), then
 * a new open tick begins. Future slots never invent player actions — only an inferred
 * ready marker may appear ahead.
 */
public final class TickFlowState
{
	public static final int MAX_HISTORY = 16;
	public static final int MIN_TIMELINE = 5;
	public static final int MAX_TIMELINE = 8;

	private final AttackCycleTracker attackCycleTracker = new AttackCycleTracker();
	private final ActionClassifier classifier = new ActionClassifier();

	private long localTickIndex = -1;
	private final Deque<TickRecord> history = new ArrayDeque<>();
	private final List<TickAction> currentActions = new ArrayList<>();
	private int actionSequence;
	private boolean tickOpen;

	@Nullable
	private String lastPlayerLocation;
	@Nullable
	private String currentPlayerLocation;
	@Nullable
	private String lastTargetIdentity;
	@Nullable
	private String currentTargetIdentity;
	@Nullable
	private String equipmentFingerprint;
	private long lastPrayerMask;
	private long currentPrayerMask;
	private int lastAnimationId = -1;
	private int currentAnimationId = -1;
	private int timelineLength = 5;
	@Nullable
	private CycleFeedback activeFeedback;
	@Nullable
	private String lastResetReason;
	private boolean inCombat;
	private final List<String> debugBufferedMenus = new ArrayList<>();
	private boolean captureMenuDebug;
	private long lastAnimationAttackTick = -1;
	/** Published only from the client thread; overlays must only read this reference. */
	@Nullable
	private volatile Snapshot published;
	/** When &gt; 0, mutations defer publish until the batch ends (avoids rebuild spam in beginNextTick). */
	private int publishBatchDepth;
	private boolean publishQueued;

	private static final TickAction EMPTY_ACTION =
		new TickAction(ActionType.EMPTY, ActionSource.GAME_STATE, ActionType.EMPTY.getShortLabel(), 100, 0);

	public AttackCycleTracker getAttackCycleTracker()
	{
		return attackCycleTracker;
	}

	public ActionClassifier getClassifier()
	{
		return classifier;
	}

	public void setTimelineLength(int timelineLength)
	{
		this.timelineLength = clampTimeline(timelineLength);
		trimHistory();
		markSnapshotDirty();
	}

	public void setCaptureMenuDebug(boolean captureMenuDebug)
	{
		this.captureMenuDebug = captureMenuDebug;
	}

	public long getLocalTickIndex()
	{
		return localTickIndex;
	}

	@Nullable
	public String getLastResetReason()
	{
		return lastResetReason;
	}

	public boolean isInCombat()
	{
		return inCombat;
	}

	public void setInCombat(boolean inCombat)
	{
		this.inCombat = inCombat;
	}

	/**
	 * Advance on GameTick: finalize the previous open tick, then open a new one.
	 */
	public void beginNextTick(
		@Nullable String playerLocation,
		@Nullable String targetIdentity,
		long prayerMask,
		int animationId,
		@Nullable String equipmentFingerprint,
		int weaponAspeed)
	{
		beginPublishBatch();
		try
		{
			if (tickOpen)
			{
				finalizeCurrentTick();
			}

			localTickIndex++;
			tickOpen = true;
			currentActions.clear();
			actionSequence = 0;
			debugBufferedMenus.clear();

			String previousLocation = currentPlayerLocation;
			long previousPrayerMask = currentPrayerMask;

			lastPlayerLocation = previousLocation;
			currentPlayerLocation = playerLocation;
			lastTargetIdentity = currentTargetIdentity;
			currentTargetIdentity = targetIdentity;
			lastPrayerMask = previousPrayerMask;
			currentPrayerMask = prayerMask;
			lastAnimationId = currentAnimationId;
			currentAnimationId = animationId;

			attackCycleTracker.noteEquipmentFingerprint(equipmentFingerprint);
			this.equipmentFingerprint = equipmentFingerprint;
			attackCycleTracker.setWeaponSpeedFromMetadata(weaponAspeed);
			attackCycleTracker.noteTarget(targetIdentity);
			if (attackCycleTracker.onTick(localTickIndex))
			{
				// Inactivity timeout — allow auto-hide to take effect again.
				inCombat = false;
			}

			if (activeFeedback != null && activeFeedback.isExpired(localTickIndex))
			{
				activeFeedback = null;
			}

			// Position / prayer deltas observed at the boundary belong to the new tick.
			if (previousLocation != null
				&& playerLocation != null
				&& !previousLocation.equals(playerLocation))
			{
				recordAction(classifier.movementFromPositionChange(nextOrder()));
			}

			long prayerDelta = previousPrayerMask ^ prayerMask;
			if (localTickIndex > 0 && prayerDelta != 0)
			{
				net.runelite.api.Prayer changed = PrayerSprites.pickChangedPrayer(previousPrayerMask, prayerMask);
				boolean activated = changed != null
					&& (prayerMask & (1L << changed.ordinal())) != 0;
				if (changed != null)
				{
					recordAction(classifier.prayerFromStateChange(changed, activated, nextOrder()));
				}
				else
				{
					int changedCount = Long.bitCount(prayerDelta);
					recordAction(classifier.prayerFromStateChange(changedCount, nextOrder()));
				}
			}
			markSnapshotDirty();
		}
		finally
		{
			endPublishBatch();
		}
	}

	public void recordAction(TickAction action)
	{
		if (!tickOpen || action == null)
		{
			return;
		}
		if (action.getType() == ActionType.PRAYER && action.hasSpriteId()
			&& action.getSpriteId() != PrayerSprites.GENERIC_SPRITE)
		{
			// Replace same-tick generic prayer placeholders so the icon doesn't flash a wrong default.
			for (int i = 0; i < currentActions.size(); i++)
			{
				TickAction existing = currentActions.get(i);
				if (existing.getType() == ActionType.PRAYER
					&& (!existing.hasSpriteId() || existing.getSpriteId() == PrayerSprites.GENERIC_SPRITE))
				{
					currentActions.set(i, action);
					markSnapshotDirty();
					return;
				}
			}
		}
		currentActions.add(action);
		if (action.getType() == ActionType.ATTACK && action.getConfidence() >= 70)
		{
			CycleFeedback feedback = attackCycleTracker.onCredibleAttack(localTickIndex);
			if (feedback != null)
			{
				activeFeedback = feedback;
			}
			inCombat = true;
		}
		markSnapshotDirty();
	}

	public void recordSecondaryAction(TickAction action)
	{
		recordAction(action);
	}

	/**
	 * Records at most one low-confidence animation attack per local tick.
	 */
	public boolean recordAnimationAttack(TickAction action)
	{
		if (!tickOpen || action == null)
		{
			return false;
		}
		if (lastAnimationAttackTick == localTickIndex)
		{
			return false;
		}
		lastAnimationAttackTick = localTickIndex;
		recordAction(action);
		return true;
	}

	public void recordMenuDebug(@Nullable String option, @Nullable String target)
	{
		if (!captureMenuDebug || debugBufferedMenus.size() >= 8)
		{
			return;
		}
		debugBufferedMenus.add(String.valueOf(option) + " -> " + target);
		markSnapshotDirty();
	}

	public void finalizeCurrentTick()
	{
		if (!tickOpen)
		{
			return;
		}
		TickRecord record = buildRecord(false);
		history.addLast(record);
		trimHistory();
		tickOpen = false;
		currentActions.clear();
		markSnapshotDirty();
	}

	public void reset(String reason)
	{
		lastResetReason = reason;
		history.clear();
		currentActions.clear();
		actionSequence = 0;
		tickOpen = false;
		localTickIndex = -1;
		lastPlayerLocation = null;
		currentPlayerLocation = null;
		lastTargetIdentity = null;
		currentTargetIdentity = null;
		lastPrayerMask = 0;
		currentPrayerMask = 0;
		lastAnimationId = -1;
		currentAnimationId = -1;
		activeFeedback = null;
		inCombat = false;
		debugBufferedMenus.clear();
		lastAnimationAttackTick = -1;
		attackCycleTracker.reset(reason);
		publishSnapshot();
	}

	/**
	 * Returns the last snapshot published on the client thread. Never builds on the caller thread.
	 */
	@Nullable
	public Snapshot snapshot()
	{
		return published;
	}

	private void markSnapshotDirty()
	{
		if (publishBatchDepth > 0)
		{
			publishQueued = true;
			return;
		}
		publishSnapshot();
	}

	private void beginPublishBatch()
	{
		publishBatchDepth++;
	}

	private void endPublishBatch()
	{
		publishBatchDepth = Math.max(0, publishBatchDepth - 1);
		if (publishBatchDepth == 0 && publishQueued)
		{
			publishQueued = false;
			publishSnapshot();
		}
	}

	private void publishSnapshot()
	{
		published = buildSnapshot();
		publishQueued = false;
	}

	private Snapshot buildSnapshot()
	{
		List<TickRecord> past = history.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(history));
		TickRecord current = tickOpen ? buildRecord(true) : null;
		int untilReady = attackCycleTracker.ticksUntilReady(Math.max(localTickIndex, 0));
		boolean readyNow = attackCycleTracker.isReadyAt(Math.max(localTickIndex, 0));
		CycleFeedback feedback = activeFeedback;
		if (feedback == null)
		{
			feedback = attackCycleTracker.getLastFeedback();
			if (feedback != null && feedback.isExpired(localTickIndex))
			{
				feedback = null;
			}
		}

		List<String> menus = debugBufferedMenus.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(debugBufferedMenus));

		String trackerDebug = captureMenuDebug ? attackCycleTracker.debugSummary() : "";

		return new Snapshot(
			localTickIndex,
			past,
			current,
			timelineLength,
			untilReady,
			readyNow,
			attackCycleTracker.getConfidence(),
			attackCycleTracker.getCandidateAttackSpeedTicks(),
			attackCycleTracker.getPhase(),
			feedback,
			equipmentFingerprint,
			currentTargetIdentity,
			currentPlayerLocation,
			currentAnimationId,
			menus,
			lastResetReason,
			inCombat,
			trackerDebug);
	}

	private TickRecord buildRecord(boolean open)
	{
		TickAction primary = selectPrimary(currentActions);
		List<TickAction> secondary;
		if (currentActions.size() <= 1)
		{
			secondary = Collections.emptyList();
		}
		else
		{
			secondary = new ArrayList<>(currentActions.size() - 1);
			for (TickAction action : currentActions)
			{
				if (action != primary)
				{
					secondary.add(action);
				}
			}
		}

		boolean ready = attackCycleTracker.isReadyAt(localTickIndex);

		return TickRecord.builder(localTickIndex)
			.primaryAction(primary)
			.secondaryActions(secondary)
			.observedPlayerLocation(currentPlayerLocation)
			.observedInteractionTarget(currentTargetIdentity)
			.attackReady(ready)
			.cycleFeedback(null)
			.open(open)
			.build();
	}

	static TickAction selectPrimary(List<TickAction> actions)
	{
		if (actions == null || actions.isEmpty())
		{
			return EMPTY_ACTION;
		}
		TickAction best = actions.get(0);
		for (int i = 1; i < actions.size(); i++)
		{
			TickAction candidate = actions.get(i);
			if (candidate.isStrongerThan(best))
			{
				best = candidate;
			}
		}
		return best;
	}

	private int nextOrder()
	{
		return actionSequence++;
	}

	private void trimHistory()
	{
		int keep = Math.max(timelineLength, MAX_TIMELINE);
		keep = Math.min(keep, MAX_HISTORY);
		while (history.size() > keep)
		{
			history.removeFirst();
		}
	}

	static int clampTimeline(int value)
	{
		if (value < MIN_TIMELINE)
		{
			return MIN_TIMELINE;
		}
		if (value > MAX_TIMELINE)
		{
			return MAX_TIMELINE;
		}
		return value;
	}

	/**
	 * Immutable view consumed by the overlay.
	 */
	public static final class Snapshot
	{
		private final long localTickIndex;
		private final List<TickRecord> history;
		@Nullable
		private final TickRecord currentTick;
		private final int timelineLength;
		private final int ticksUntilReady;
		private final boolean attackReadyNow;
		private final CycleConfidence confidence;
		private final int candidateSpeed;
		private final AttackCyclePhase phase;
		@Nullable
		private final CycleFeedback cycleFeedback;
		@Nullable
		private final String equipmentFingerprint;
		@Nullable
		private final String targetIdentity;
		@Nullable
		private final String playerLocation;
		private final int animationId;
		private final List<String> bufferedMenus;
		@Nullable
		private final String lastResetReason;
		private final boolean inCombat;
		private final String trackerDebug;

		Snapshot(
			long localTickIndex,
			List<TickRecord> history,
			@Nullable TickRecord currentTick,
			int timelineLength,
			int ticksUntilReady,
			boolean attackReadyNow,
			CycleConfidence confidence,
			int candidateSpeed,
			AttackCyclePhase phase,
			@Nullable CycleFeedback cycleFeedback,
			@Nullable String equipmentFingerprint,
			@Nullable String targetIdentity,
			@Nullable String playerLocation,
			int animationId,
			List<String> bufferedMenus,
			@Nullable String lastResetReason,
			boolean inCombat,
			String trackerDebug)
		{
			this.localTickIndex = localTickIndex;
			this.history = history;
			this.currentTick = currentTick;
			this.timelineLength = timelineLength;
			this.ticksUntilReady = ticksUntilReady;
			this.attackReadyNow = attackReadyNow;
			this.confidence = confidence;
			this.candidateSpeed = candidateSpeed;
			this.phase = phase;
			this.cycleFeedback = cycleFeedback;
			this.equipmentFingerprint = equipmentFingerprint;
			this.targetIdentity = targetIdentity;
			this.playerLocation = playerLocation;
			this.animationId = animationId;
			this.bufferedMenus = bufferedMenus;
			this.lastResetReason = lastResetReason;
			this.inCombat = inCombat;
			this.trackerDebug = trackerDebug;
		}

		public long getLocalTickIndex()
		{
			return localTickIndex;
		}

		public List<TickRecord> getHistory()
		{
			return history;
		}

		@Nullable
		public TickRecord getCurrentTick()
		{
			return currentTick;
		}

		public int getTimelineLength()
		{
			return timelineLength;
		}

		public int getTicksUntilReady()
		{
			return ticksUntilReady;
		}

		public boolean isAttackReadyNow()
		{
			return attackReadyNow;
		}

		public CycleConfidence getConfidence()
		{
			return confidence;
		}

		public int getCandidateSpeed()
		{
			return candidateSpeed;
		}

		public AttackCyclePhase getPhase()
		{
			return phase;
		}

		@Nullable
		public CycleFeedback getCycleFeedback()
		{
			return cycleFeedback;
		}

		@Nullable
		public String getEquipmentFingerprint()
		{
			return equipmentFingerprint;
		}

		@Nullable
		public String getTargetIdentity()
		{
			return targetIdentity;
		}

		@Nullable
		public String getPlayerLocation()
		{
			return playerLocation;
		}

		public int getAnimationId()
		{
			return animationId;
		}

		public List<String> getBufferedMenus()
		{
			return bufferedMenus;
		}

		@Nullable
		public String getLastResetReason()
		{
			return lastResetReason;
		}

		public boolean isInCombat()
		{
			return inCombat;
		}

		public String getTrackerDebug()
		{
			return trackerDebug;
		}
	}
}
