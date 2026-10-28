package com.agilitycctracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;

public class PromptPainterTest
{
	@Test
	public void popupScaleIsClampedTo50To100Percent()
	{
		assertEquals(1.0, PromptPainter.scale(100), 1e-9);
		assertEquals(0.75, PromptPainter.scale(75), 1e-9);
		assertEquals(0.5, PromptPainter.scale(10), 1e-9);
		assertEquals(1.0, PromptPainter.scale(150), 1e-9);
	}

	@Test
	public void scaledSizeRoundsUpSoNothingIsClipped()
	{
		assertEquals(new Dimension(240, 86), PromptPainter.scaled(240, 86, 1));
		assertEquals(new Dimension(120, 43), PromptPainter.scaled(240, 86, 0.5));
		assertEquals(new Dimension(180, 65), PromptPainter.scaled(240, 86, 0.75));
	}

	@Test
	public void clicksOnAShrunkButtonLandOnTheFullSizeButton()
	{
		Rectangle button = new Rectangle(10, 52, 68, 24);
		double scale = 0.6;
		// Every on-screen pixel of the drawn button maps back inside it.
		for (int x = (int) Math.ceil(button.x * scale); x < (int) ((button.x + button.width) * scale); x++)
		{
			for (int y = (int) Math.ceil(button.y * scale); y < (int) ((button.y + button.height) * scale); y++)
			{
				assertTrue(x + "," + y, button.contains(PromptPainter.unscale(x, y, scale)));
			}
		}
		Point justLeft = PromptPainter.unscale((int) (button.x * scale) - 2, button.y, scale);
		assertFalse(button.contains(justLeft));
	}
}
