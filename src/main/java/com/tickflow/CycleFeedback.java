package com.tickflow;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Brief neutral summary of a completed attack cycle.
 * Expires after a bounded number of ticks.
 */
public final class CycleFeedback
{
	private final String summary;
	private final int deltaTicks;
	private final long createdOnTick;
	private final int ttlTicks;

	public CycleFeedback(String summary, int deltaTicks, long createdOnTick, int ttlTicks)
	{
		this.summary = Objects.requireNonNull(summary, "summary");
		this.deltaTicks = deltaTicks;
		this.createdOnTick = createdOnTick;
		this.ttlTicks = Math.max(1, ttlTicks);
	}

	public String getSummary()
	{
		return summary;
	}

	public int getDeltaTicks()
	{
		return deltaTicks;
	}

	public long getCreatedOnTick()
	{
		return createdOnTick;
	}

	public boolean isExpired(long currentTick)
	{
		return currentTick - createdOnTick >= ttlTicks;
	}

	public static CycleFeedback onTime(long tick, int ttl)
	{
		return new CycleFeedback("On inferred ready tick", 0, tick, ttl);
	}

	public static CycleFeedback late(int delta, long tick, int ttl)
	{
		String noun = delta == 1 ? "tick" : "ticks";
		return new CycleFeedback("Attack occurred " + delta + " " + noun + " after ready", delta, tick, ttl);
	}

	@Nullable
	public static CycleFeedback maybeCreate(int delta, long tick, int ttl)
	{
		if (delta < 0)
		{
			return null;
		}
		if (delta == 0)
		{
			return onTime(tick, ttl);
		}
		return late(delta, tick, ttl);
	}
}
