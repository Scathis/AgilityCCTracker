package com.agilitycctracker.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Shared drawing for the in-game boxes (the attending prompt and the mass notices), so they look alike. */
final class PromptPainter
{
	static final int CLOSE = 10;

	private PromptPainter()
	{
	}

	/** The "Pop-up size" setting as a factor, clamped to 50-100%. */
	static double scale(int percent)
	{
		return Math.max(50, Math.min(100, percent)) / 100.0;
	}

	/**
	 * Starts drawing a box laid out at full size but shown at {@code scale}.
	 * Smooths text when shrunk, since the RuneScape fonts are pixel fonts.
	 */
	static void applyScale(Graphics2D g, double scale)
	{
		if (scale < 1)
		{
			g.scale(scale, scale);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		}
	}

	/** The on-screen size of a {@code width} x {@code height} box drawn at {@code scale}. */
	static Dimension scaled(int width, int height, double scale)
	{
		return new Dimension((int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
	}

	/** A point relative to the box on screen, mapped back to its full-size layout. */
	static Point unscale(int x, int y, double scale)
	{
		return new Point((int) (x / scale), (int) (y / scale));
	}

	/** Dark box with a slow gold pulse on the border - noticed without being loud. */
	static void box(Graphics2D g, int width, int height)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double pulse = (Math.sin(System.currentTimeMillis() / 400.0) + 1) / 2;
		g.setColor(new Color(Theme.BACKGROUND.getRed(), Theme.BACKGROUND.getGreen(), Theme.BACKGROUND.getBlue(), 238));
		g.fillRoundRect(0, 0, width, height, 6, 6);
		g.setColor(Theme.SHADOW);
		g.setStroke(new BasicStroke(3f));
		g.drawRoundRect(1, 1, width - 3, height - 3, 6, 6);
		Color edge = Theme.GOLD_BRIGHT;
		g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), (int) (110 + 145 * pulse)));
		g.setStroke(new BasicStroke(1.2f));
		g.drawRoundRect(2, 2, width - 5, height - 5, 5, 5);
	}

	static void centred(Graphics2D g, String text, int width, int baseline, Color color)
	{
		FontMetrics metrics = g.getFontMetrics();
		int x = (width - metrics.stringWidth(text)) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	static void button(Graphics2D g, Rectangle r, String label, boolean primary, boolean hovered)
	{
		Color fill = primary
			? (hovered ? Theme.GOLD_PALE : Theme.GOLD_BRIGHT)
			: (hovered ? Theme.RAISED_HOVER : Theme.RAISED);
		g.setColor(fill);
		g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
		g.setStroke(new BasicStroke(1f));
		g.setColor(hovered && !primary ? Theme.GOLD : Theme.CARD_BORDER);
		g.drawRoundRect(r.x, r.y, r.width, r.height, 4, 4);

		g.setFont(Theme.body());
		FontMetrics metrics = g.getFontMetrics();
		int x = r.x + (r.width - metrics.stringWidth(label)) / 2;
		int y = r.y + (r.height - metrics.getHeight()) / 2 + metrics.getAscent();
		g.setColor(primary ? Theme.BACKGROUND : Theme.GOLD_BRIGHT);
		g.drawString(label, x, y);
	}

	/** The × in the top-right corner, at {@code r}. */
	static void closeCross(Graphics2D g, Rectangle r, boolean hovered)
	{
		g.setColor(hovered ? Theme.GOLD_BRIGHT : Theme.MUTED);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
		g.drawLine(r.x, r.y, r.x + CLOSE - 2, r.y + CLOSE - 2);
		g.drawLine(r.x + CLOSE - 2, r.y, r.x, r.y + CLOSE - 2);
	}

	/** Generous hit area around the ×. */
	static Rectangle closeHitArea(Rectangle r)
	{
		return new Rectangle(r.x - 4, r.y - 4, CLOSE + 8, CLOSE + 8);
	}
}
