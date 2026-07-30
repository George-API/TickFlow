package com.tickflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class TickFlowIconsTest
{
	@Test
	public void crispFitPreservesAspectAndDoesNotUpscale()
	{
		BufferedImage source = new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB);
		BufferedImage fitted = TickFlowIcons.crispFit(source, 24);
		assertEquals(24, fitted.getWidth());
		assertEquals(16, fitted.getHeight());
	}

	@Test
	public void crispFitKeepsNativeWhenAlreadySmall()
	{
		BufferedImage source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		BufferedImage fitted = TickFlowIcons.crispFit(source, 24);
		assertEquals(16, fitted.getWidth());
		assertEquals(16, fitted.getHeight());
	}

	@Test
	public void crispFitSmallBadgeStaysSquareWhenSourceIsSquare()
	{
		BufferedImage source = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		BufferedImage fitted = TickFlowIcons.crispFit(source, 12);
		assertEquals(12, fitted.getWidth());
		assertEquals(12, fitted.getHeight());
		assertTrue(fitted.getWidth() == fitted.getHeight());
	}
}
