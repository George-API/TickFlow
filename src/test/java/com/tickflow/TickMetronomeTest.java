package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TickMetronomeTest
{
	@Test
	public void buildsValidWavHeaderAndPcm()
	{
		byte[] wav = TickMetronome.buildSoftTickWav();
		assertTrue(wav.length > 44);
		assertEquals('R', wav[0]);
		assertEquals('I', wav[1]);
		assertEquals('F', wav[2]);
		assertEquals('F', wav[3]);
		assertEquals('W', wav[8]);
		assertEquals('A', wav[9]);
		assertEquals('V', wav[10]);
		assertEquals('E', wav[11]);
	}
}
