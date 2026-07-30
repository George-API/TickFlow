package com.tickflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Pure attack-cycle state machine.
 * <p>
 * Infers next-attack readiness from weapon metadata and/or repeated credible
 * attack intervals. Prefer unknown over invented accuracy.
 */
public final class AttackCycleTracker
{
	public static final int MIN_SPEED_TICKS = 2;
	public static final int MAX_SPEED_TICKS = 10;
	public static final int DEFAULT_FEEDBACK_TTL = 6;
	public static final int INACTIVITY_TIMEOUT_TICKS = 25;
	public static final int INTERVALS_FOR_HIGH_CONFIDENCE = 3;
	public static final int INTERVALS_FOR_LOW_CONFIDENCE = 2;

	private AttackCyclePhase phase = AttackCyclePhase.IDLE;
	private CycleConfidence confidence = CycleConfidence.NONE;
	private long lastCredibleAttackTick = -1;
	private int candidateAttackSpeedTicks = -1;
	private long nextReadyTick = -1;
	private int lastCycleDeltaTicks = 0;
	@Nullable
	private CycleFeedback lastFeedback;
	@Nullable
	private String equipmentFingerprint;
	@Nullable
	private String targetIdentity;
	private final Deque<Integer> recentIntervals = new ArrayDeque<>();
	private int feedbackTtl = DEFAULT_FEEDBACK_TTL;
	private boolean weaponSpeedKnown;
	private long lastActivityTick = -1;

	public void setFeedbackTtl(int feedbackTtl)
	{
		this.feedbackTtl = Math.max(1, feedbackTtl);
	}

	public AttackCyclePhase getPhase()
	{
		return phase;
	}

	public CycleConfidence getConfidence()
	{
		return confidence;
	}

	public long getLastCredibleAttackTick()
	{
		return lastCredibleAttackTick;
	}

	public int getCandidateAttackSpeedTicks()
	{
		return candidateAttackSpeedTicks;
	}

	public long getNextReadyTick()
	{
		return nextReadyTick;
	}

	public int getLastCycleDeltaTicks()
	{
		return lastCycleDeltaTicks;
	}

	@Nullable
	public CycleFeedback getLastFeedback()
	{
		return lastFeedback;
	}

	public boolean isWeaponSpeedKnown()
	{
		return weaponSpeedKnown;
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

	/**
	 * Apply equipped-weapon attack speed from ItemManager metadata when available.
	 *
	 * @param aspeed attack interval in ticks from {@code ItemEquipmentStats#getAspeed()}, or &lt;=0 if unknown
	 */
	public void setWeaponSpeedFromMetadata(int aspeed)
	{
		if (aspeed < MIN_SPEED_TICKS || aspeed > MAX_SPEED_TICKS)
		{
			weaponSpeedKnown = false;
			return;
		}
		weaponSpeedKnown = true;
		candidateAttackSpeedTicks = aspeed;
		if (lastCredibleAttackTick >= 0)
		{
			nextReadyTick = lastCredibleAttackTick + candidateAttackSpeedTicks;
			promoteConfidenceFromWeapon();
		}
		else if (phase == AttackCyclePhase.IDLE || phase == AttackCyclePhase.INVALIDATED)
		{
			phase = AttackCyclePhase.ACQUIRING;
		}
	}

	public void noteEquipmentFingerprint(@Nullable String fingerprint)
	{
		if (fingerprint == null)
		{
			return;
		}
		if (equipmentFingerprint != null && !equipmentFingerprint.equals(fingerprint))
		{
			reset("equipment change");
			equipmentFingerprint = fingerprint;
			return;
		}
		equipmentFingerprint = fingerprint;
	}

	public void noteTarget(@Nullable String target)
	{
		if (Objects.equals(targetIdentity, target))
		{
			return;
		}
		if (targetIdentity != null && target != null && !targetIdentity.equals(target))
		{
			downgrade("target change");
		}
		else if (targetIdentity != null && target == null)
		{
			downgrade("target lost");
		}
		targetIdentity = target;
	}

	/**
	 * Record a credible attack observation.
	 *
	 * @return feedback produced by this attack, if any
	 */
	@Nullable
	public CycleFeedback onCredibleAttack(long tickIndex)
	{
		lastActivityTick = tickIndex;

		if (lastCredibleAttackTick >= 0 && candidateAttackSpeedTicks > 0 && confidence.allowsFeedback())
		{
			int delta = (int) (tickIndex - nextReadyTick);
			if (delta < 0)
			{
				// Impossible early attack relative to estimate — reacquire rather than praise.
				reacquireAfterMismatch(tickIndex);
				lastCredibleAttackTick = tickIndex;
				recomputeNextReady();
				lastFeedback = null;
				lastCycleDeltaTicks = delta;
				return null;
			}

			lastCycleDeltaTicks = delta;
			lastFeedback = CycleFeedback.maybeCreate(delta, tickIndex, feedbackTtl);
		}
		else
		{
			lastFeedback = null;
			lastCycleDeltaTicks = 0;
		}

		if (lastCredibleAttackTick >= 0)
		{
			int interval = (int) (tickIndex - lastCredibleAttackTick);
			if (interval >= MIN_SPEED_TICKS && interval <= MAX_SPEED_TICKS)
			{
				recentIntervals.addLast(interval);
				while (recentIntervals.size() > 5)
				{
					recentIntervals.removeFirst();
				}
				maybeInferSpeedFromIntervals();
			}
		}

		lastCredibleAttackTick = tickIndex;
		if (candidateAttackSpeedTicks > 0)
		{
			recomputeNextReady();
			if (weaponSpeedKnown)
			{
				promoteConfidenceFromWeapon();
			}
			else if (phase == AttackCyclePhase.IDLE || phase == AttackCyclePhase.INVALIDATED)
			{
				phase = AttackCyclePhase.ACQUIRING;
			}
		}
		else
		{
			phase = AttackCyclePhase.ACQUIRING;
			confidence = CycleConfidence.NONE;
			nextReadyTick = -1;
		}

		return lastFeedback;
	}

	/**
	 * @return {@code true} if the tracker was cleared due to inactivity
	 */
	public boolean onTick(long tickIndex)
	{
		if (lastFeedback != null && lastFeedback.isExpired(tickIndex))
		{
			lastFeedback = null;
		}

		if (lastActivityTick >= 0 && tickIndex - lastActivityTick >= INACTIVITY_TIMEOUT_TICKS)
		{
			reset("inactivity");
			return true;
		}
		return false;
	}

	public boolean isReadyAt(long tickIndex)
	{
		return confidence.allowsReadiness()
			&& nextReadyTick >= 0
			&& tickIndex >= nextReadyTick
			&& lastCredibleAttackTick >= 0;
	}

	public int ticksUntilReady(long tickIndex)
	{
		if (!confidence.allowsReadiness() || nextReadyTick < 0)
		{
			return -1;
		}
		long delta = nextReadyTick - tickIndex;
		return delta < 0 ? 0 : (int) delta;
	}

	public void reset(String reason)
	{
		phase = AttackCyclePhase.INVALIDATED;
		confidence = CycleConfidence.NONE;
		lastCredibleAttackTick = -1;
		nextReadyTick = -1;
		lastCycleDeltaTicks = 0;
		lastFeedback = null;
		recentIntervals.clear();
		targetIdentity = null;
		lastActivityTick = -1;
		// Keep equipment fingerprint and weapon metadata so the next attack can reacquire quickly.
		if (!weaponSpeedKnown)
		{
			candidateAttackSpeedTicks = -1;
		}
		phase = AttackCyclePhase.IDLE;
	}

	public void clearWeaponMetadata()
	{
		weaponSpeedKnown = false;
		candidateAttackSpeedTicks = -1;
		nextReadyTick = -1;
		confidence = CycleConfidence.NONE;
		if (phase == AttackCyclePhase.TRACKING_HIGH_CONFIDENCE || phase == AttackCyclePhase.TRACKING_LOW_CONFIDENCE)
		{
			phase = AttackCyclePhase.ACQUIRING;
		}
	}

	public String debugSummary()
	{
		return String.format(Locale.US,
			"phase=%s conf=%s speed=%d ready=%d lastAtk=%d delta=%d weaponKnown=%s intervals=%s",
			phase, confidence, candidateAttackSpeedTicks, nextReadyTick, lastCredibleAttackTick,
			lastCycleDeltaTicks, weaponSpeedKnown, recentIntervals);
	}

	private void recomputeNextReady()
	{
		if (lastCredibleAttackTick >= 0 && candidateAttackSpeedTicks > 0)
		{
			nextReadyTick = lastCredibleAttackTick + candidateAttackSpeedTicks;
		}
		else
		{
			nextReadyTick = -1;
		}
	}

	private void promoteConfidenceFromWeapon()
	{
		if (!weaponSpeedKnown || candidateAttackSpeedTicks <= 0 || lastCredibleAttackTick < 0)
		{
			return;
		}
		confidence = CycleConfidence.HIGH;
		phase = AttackCyclePhase.TRACKING_HIGH_CONFIDENCE;
	}

	private void maybeInferSpeedFromIntervals()
	{
		if (weaponSpeedKnown)
		{
			return;
		}
		Integer consensus = consensusInterval(recentIntervals);
		if (consensus == null)
		{
			return;
		}
		candidateAttackSpeedTicks = consensus;
		recomputeNextReady();
		int agreeing = countAgreeing(recentIntervals, consensus);
		if (agreeing >= INTERVALS_FOR_HIGH_CONFIDENCE)
		{
			confidence = CycleConfidence.HIGH;
			phase = AttackCyclePhase.TRACKING_HIGH_CONFIDENCE;
		}
		else if (agreeing >= INTERVALS_FOR_LOW_CONFIDENCE)
		{
			confidence = CycleConfidence.LOW;
			phase = AttackCyclePhase.TRACKING_LOW_CONFIDENCE;
		}
	}

	@Nullable
	static Integer consensusInterval(Deque<Integer> intervals)
	{
		if (intervals.isEmpty())
		{
			return null;
		}
		int bestValue = -1;
		int bestCount = 0;
		List<Integer> values = new ArrayList<>(intervals);
		for (int candidate : values)
		{
			int count = 0;
			for (int other : values)
			{
				if (other == candidate)
				{
					count++;
				}
			}
			if (count > bestCount)
			{
				bestCount = count;
				bestValue = candidate;
			}
		}
		if (bestCount < INTERVALS_FOR_LOW_CONFIDENCE)
		{
			return null;
		}
		return bestValue;
	}

	private static int countAgreeing(Deque<Integer> intervals, int value)
	{
		int count = 0;
		for (int interval : intervals)
		{
			if (interval == value)
			{
				count++;
			}
		}
		return count;
	}

	private void downgrade(String reason)
	{
		if (confidence == CycleConfidence.HIGH)
		{
			confidence = CycleConfidence.LOW;
			phase = AttackCyclePhase.TRACKING_LOW_CONFIDENCE;
		}
		else
		{
			confidence = CycleConfidence.NONE;
			phase = AttackCyclePhase.ACQUIRING;
			lastFeedback = null;
		}
		recentIntervals.clear();
	}

	private void reacquireAfterMismatch(long tickIndex)
	{
		confidence = CycleConfidence.NONE;
		phase = AttackCyclePhase.ACQUIRING;
		recentIntervals.clear();
		weaponSpeedKnown = false;
		// Keep last interval as a weak hint only after next attack.
		candidateAttackSpeedTicks = -1;
		nextReadyTick = -1;
		lastActivityTick = tickIndex;
	}
}
