package com.tickflow;

/**
 * Confidence band for inferred attack readiness and cycle feedback.
 */
public enum CycleConfidence
{
	NONE,
	LOW,
	HIGH;

	public boolean allowsFeedback()
	{
		return this == HIGH;
	}

	public boolean allowsReadiness()
	{
		return this == HIGH || this == LOW;
	}
}
