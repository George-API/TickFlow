package com.tickflow;

/**
 * Attack-cycle tracker lifecycle.
 */
public enum AttackCyclePhase
{
	IDLE,
	ACQUIRING,
	TRACKING_LOW_CONFIDENCE,
	TRACKING_HIGH_CONFIDENCE,
	INVALIDATED
}
