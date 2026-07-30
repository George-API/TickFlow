package com.tickflow;

import java.awt.Color;

/**
 * Shared layout constants so circular and square timelines stay visually consistent.
 */
final class TickFlowLayout
{
	/**
	 * Current-tick cell scale vs neighboring cells (+15%, then another +10%).
	 */
	static final float NOW_CELL_SCALE = 1.15f * 1.10f;

	/** Shared cell extent (circle diameter / square cell width) so both panels match width. */
	static final int BASE_CELL = 52;

	/** Shared gap between cells. */
	static final int BASE_GAP = 5;

	/** Soft amber at tick start — resets make the next tick easy to spot. */
	static final Color PROGRESS_START = new Color(210, 175, 85);

	/** Mint green at tick end. */
	static final Color PROGRESS_END = new Color(0, 220, 165);

	private static final int PROGRESS_STEPS = 64;
	private static final Color[] PROGRESS_LUT = buildProgressLut();

	private TickFlowLayout()
	{
	}

	static int nowSize(int base)
	{
		return Math.max(base + 1, Math.round(base * NOW_CELL_SCALE));
	}

	/** Yellow → green through the current tick (0 = start, 1 = end). Uses a fixed LUT (no per-frame alloc). */
	static Color progressColor(double progress)
	{
		int idx = (int) Math.round(clamp01(progress) * (PROGRESS_STEPS - 1));
		return PROGRESS_LUT[idx];
	}

	private static Color[] buildProgressLut()
	{
		Color[] lut = new Color[PROGRESS_STEPS];
		for (int i = 0; i < PROGRESS_STEPS; i++)
		{
			float t = i / (float) (PROGRESS_STEPS - 1);
			// Ease slightly so the amber hang is readable early in the tick.
			t = t * t * (3f - 2f * t);
			lut[i] = lerp(PROGRESS_START, PROGRESS_END, t);
		}
		return lut;
	}

	static Color lerp(Color a, Color b, float t)
	{
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		int alpha = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
		return new Color(r, g, bl, alpha);
	}

	static double clamp01(double value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 1)
		{
			return 1;
		}
		return value;
	}
}
