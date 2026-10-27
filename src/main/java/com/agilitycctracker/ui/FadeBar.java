package com.agilitycctracker.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** A short vertical gold line fading out at both ends - the "|" counterpart of {@link OrnamentDivider}. */
public class FadeBar extends JComponent
{
	private static final int WIDTH = 13;
	private static final int HEIGHT = 16;

	public FadeBar()
	{
		setOpaque(false);
		Dimension size = new Dimension(WIDTH, HEIGHT);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		int x = getWidth() / 2;
		int h = getHeight();
		int mid = h / 2;
		Color clear = new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 0);
		g.setPaint(new GradientPaint(0, 0, clear, 0, mid, Theme.GOLD_BRIGHT));
		g.fillRect(x, 0, 1, mid);
		g.setPaint(new GradientPaint(0, mid, Theme.GOLD_BRIGHT, 0, h, clear));
		g.fillRect(x, mid, 1, h - mid);
		g.dispose();
	}
}
