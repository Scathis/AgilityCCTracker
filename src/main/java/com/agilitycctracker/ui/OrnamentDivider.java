package com.agilitycctracker.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** A thin gold line fading out to both edges, with a small diamond in the middle. */
public class OrnamentDivider extends JComponent
{
	private static final int HEIGHT = 14;
	private static final int DIAMOND = 4;

	public OrnamentDivider()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int cy = getHeight() / 2;
		int cx = w / 2;
		Color clear = new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 0);
		int gap = DIAMOND + 4;

		g.setPaint(new GradientPaint(0, 0, clear, cx - gap, 0, Theme.GOLD));
		g.fillRect(0, cy, cx - gap, 1);
		g.setPaint(new GradientPaint(cx + gap, 0, Theme.GOLD, w, 0, clear));
		g.fillRect(cx + gap, cy, w - cx - gap, 1);

		Polygon diamond = new Polygon(
			new int[] {cx, cx + DIAMOND, cx, cx - DIAMOND},
			new int[] {cy - DIAMOND, cy, cy + DIAMOND, cy},
			4);
		g.setColor(Theme.GOLD_BRIGHT);
		g.fill(diamond);
		g.dispose();
	}
}
