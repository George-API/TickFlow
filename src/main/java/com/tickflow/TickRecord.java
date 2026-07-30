package com.tickflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Immutable snapshot of one finalized (or open) game tick for UI/debug.
 */
public final class TickRecord
{
	private final long localTickIndex;
	private final TickAction primaryAction;
	private final List<TickAction> secondaryActions;
	@Nullable
	private final String observedPlayerLocation;
	@Nullable
	private final String observedInteractionTarget;
	private final boolean attackReady;
	@Nullable
	private final CycleFeedback cycleFeedback;
	private final boolean open;

	private TickRecord(
		long localTickIndex,
		TickAction primaryAction,
		List<TickAction> secondaryActions,
		@Nullable String observedPlayerLocation,
		@Nullable String observedInteractionTarget,
		boolean attackReady,
		@Nullable CycleFeedback cycleFeedback,
		boolean open)
	{
		this.localTickIndex = localTickIndex;
		this.primaryAction = primaryAction == null
			? new TickAction(ActionType.EMPTY, ActionSource.GAME_STATE, ActionType.EMPTY.getShortLabel(), 100, 0)
			: primaryAction;
		this.secondaryActions = Collections.unmodifiableList(new ArrayList<>(secondaryActions));
		this.observedPlayerLocation = observedPlayerLocation;
		this.observedInteractionTarget = observedInteractionTarget;
		this.attackReady = attackReady;
		this.cycleFeedback = cycleFeedback;
		this.open = open;
	}

	public static TickRecord empty(long tickIndex)
	{
		return new TickRecord(
			tickIndex,
			new TickAction(ActionType.EMPTY, ActionSource.GAME_STATE, ActionType.EMPTY.getShortLabel(), 100, 0),
			Collections.emptyList(),
			null,
			null,
			false,
			null,
			false);
	}

	public static Builder builder(long localTickIndex)
	{
		return new Builder(localTickIndex);
	}

	public long getLocalTickIndex()
	{
		return localTickIndex;
	}

	public TickAction getPrimaryAction()
	{
		return primaryAction;
	}

	public List<TickAction> getSecondaryActions()
	{
		return secondaryActions;
	}

	@Nullable
	public String getObservedPlayerLocation()
	{
		return observedPlayerLocation;
	}

	@Nullable
	public String getObservedInteractionTarget()
	{
		return observedInteractionTarget;
	}

	public boolean isAttackReady()
	{
		return attackReady;
	}

	@Nullable
	public CycleFeedback getCycleFeedback()
	{
		return cycleFeedback;
	}

	public boolean isOpen()
	{
		return open;
	}

	public static final class Builder
	{
		private final long localTickIndex;
		private TickAction primaryAction;
		private final List<TickAction> secondaryActions = new ArrayList<>();
		@Nullable
		private String observedPlayerLocation;
		@Nullable
		private String observedInteractionTarget;
		private boolean attackReady;
		@Nullable
		private CycleFeedback cycleFeedback;
		private boolean open;

		private Builder(long localTickIndex)
		{
			this.localTickIndex = localTickIndex;
		}

		public Builder primaryAction(TickAction primaryAction)
		{
			this.primaryAction = primaryAction;
			return this;
		}

		public Builder secondaryActions(List<TickAction> secondaryActions)
		{
			this.secondaryActions.clear();
			if (secondaryActions != null)
			{
				this.secondaryActions.addAll(secondaryActions);
			}
			return this;
		}

		public Builder observedPlayerLocation(@Nullable String observedPlayerLocation)
		{
			this.observedPlayerLocation = observedPlayerLocation;
			return this;
		}

		public Builder observedInteractionTarget(@Nullable String observedInteractionTarget)
		{
			this.observedInteractionTarget = observedInteractionTarget;
			return this;
		}

		public Builder attackReady(boolean attackReady)
		{
			this.attackReady = attackReady;
			return this;
		}

		public Builder cycleFeedback(@Nullable CycleFeedback cycleFeedback)
		{
			this.cycleFeedback = cycleFeedback;
			return this;
		}

		public Builder open(boolean open)
		{
			this.open = open;
			return this;
		}

		public TickRecord build()
		{
			return new TickRecord(
				localTickIndex,
				primaryAction,
				secondaryActions,
				observedPlayerLocation,
				observedInteractionTarget,
				attackReady,
				cycleFeedback,
				open);
		}
	}
}
