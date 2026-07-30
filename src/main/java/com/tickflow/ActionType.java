package com.tickflow;

/**
 * Semantic action categories shown on the tick timeline.
 * Display priority when several actions share a tick:
 * {@code Attack > Consumable/Item > Prayer > Move > Other > Empty}.
 */
public enum ActionType
{
	ATTACK(0, "Attack"),
	CONSUMABLE(1, "Item"),
	PRAYER(2, "Pray"),
	MOVE(3, "Move"),
	OTHER(4, "Other"),
	EMPTY(5, "Empty");

	private final int priority;
	private final String shortLabel;

	ActionType(int priority, String shortLabel)
	{
		this.priority = priority;
		this.shortLabel = shortLabel;
	}

	/**
	 * Lower values win when choosing a dominant action.
	 */
	public int getPriority()
	{
		return priority;
	}

	public String getShortLabel()
	{
		return shortLabel;
	}

	public boolean isEmpty()
	{
		return this == EMPTY;
	}

	public boolean beats(ActionType other)
	{
		return this.priority < other.priority;
	}
}
