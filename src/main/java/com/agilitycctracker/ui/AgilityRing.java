package com.agilitycctracker.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;
import net.runelite.api.Experience;

/**
 * The player's Agility level as a ring. Up to 99 the ring fills by level
 * (smoothly within each level); past 99 it "overcharges": a brighter second
 * lap runs round the ring while the fill grows inward from the ring towards
 * the centre, glowing harder as it goes, until the whole disc is solid at
 * 200M xp.
 */
public class AgilityRing extends JComponent
{
	static final int XP_99 = Experience.getXpForLevel(Experience.MAX_REAL_LEVEL);
	private static final int SIZE = 64;
	private static final float RING = 7f;
	private static final float PAD = 4f;

	private int xp = -1;

	public AgilityRing()
	{
		setOpaque(false);
		Dimension size = new Dimension(SIZE, SIZE);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
	}

	/** -1 = logged out (empty ring, no number). */
	public void setXp(int xp)
	{
		if (xp == this.xp)
		{
			return;
		}
		this.xp = xp;
		setToolTipText(tooltip(xp));
		repaint();
	}

	/** 0..1 of the way from level 1 to 99, by level, interpolated within the current level. */
	static double levelFraction(int xp)
	{
		if (xp <= 0)
		{
			return 0;
		}
		if (xp >= XP_99)
		{
			return 1;
		}
		int level = Experience.getLevelForXp(xp);
		int from = Experience.getXpForLevel(level);
		int to = Experience.getXpForLevel(level + 1);
		double within = (xp - from) / (double) (to - from);
		return (level - 1 + within) / (Experience.MAX_REAL_LEVEL - 1);
	}

	/** 0..1 of the way from 99 (13,034,431 xp) to 200M xp; 0 below 99. */
	static double overcharge(int xp)
	{
		if (xp <= XP_99)
		{
			return 0;
		}
		return Math.min(1, (xp - XP_99) / (double) (Experience.MAX_SKILL_XP - XP_99));
	}

	private static String tooltip(int xp)
	{
		if (xp < 0)
		{
			return null;
		}
		int level = Experience.getLevelForXp(xp);
		String base = String.format("Agility %d - %,d xp", Math.min(level, Experience.MAX_REAL_LEVEL), xp);
		if (xp < XP_99)
		{
			return base + String.format(" - %.0f%% of the way to 99", levelFraction(xp) * 100);
		}
		return base + String.format(" - %.1f%% overcharged towards 200M", overcharge(xp) * 100);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		float size = Math.min(getWidth(), getHeight());
		float cx = getWidth() / 2f;
		float cy = getHeight() / 2f;
		float ringRadius = size / 2f - PAD - RING / 2f;
		float innerRadius = ringRadius - RING / 2f;

		double fraction = levelFraction(Math.max(xp, 0));
		double over = overcharge(xp);

		// Glow behind everything once overcharging, stronger as it climbs.
		if (over > 0)
		{
			for (int i = 3; i >= 1; i--)
			{
				int alpha = (int) Math.round((25 + 70 * over) / i);
				g.setColor(alpha(Theme.GOLD_BRIGHT, alpha));
				g.setStroke(new BasicStroke(RING + i * 2.5f));
				g.draw(circle(cx, cy, ringRadius));
			}
		}

		// Centre disc and track.
		g.setColor(Theme.BACKGROUND);
		g.fill(circle(cx, cy, innerRadius));
		g.setColor(Theme.RAISED);
		g.setStroke(new BasicStroke(RING));
		g.draw(circle(cx, cy, ringRadius));

		// Overcharge: fill from the ring's inner edge in towards the centre.
		if (over > 0)
		{
			float hole = (float) (innerRadius * (1 - over));
			Area fill = new Area(circle(cx, cy, innerRadius));
			if (hole > 0.5f)
			{
				fill.subtract(new Area(circle(cx, cy, hole)));
			}
			Color deep = blend(new Color(0x8a5a10), Theme.GOLD_BRIGHT, over);
			Color hot = blend(Theme.GOLD, Theme.GOLD_PALE, over);
			g.setPaint(new RadialGradientPaint(cx, cy, innerRadius,
				new float[] {0f, 1f},
				new Color[] {alpha(hot, (int) (150 + 105 * over)), alpha(deep, (int) (170 + 85 * over))}));
			g.fill(fill);
		}

		// Main ring: progress to 99, then fully lit.
		g.setStroke(new BasicStroke(RING, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (fraction >= 1)
		{
			g.setColor(Theme.GOLD_BRIGHT);
			g.draw(circle(cx, cy, ringRadius));
		}
		else if (fraction > 0)
		{
			g.setColor(Theme.GOLD);
			g.draw(arc(cx, cy, ringRadius, fraction));
		}

		// Second lap while overcharging, in near-white gold.
		if (over > 0)
		{
			g.setStroke(new BasicStroke(RING * 0.45f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(Theme.GOLD_PALE);
			g.draw(arc(cx, cy, ringRadius, Math.max(over, 0.01)));
		}

		drawLevel(g, cx, cy, over);
		g.dispose();
	}

	private void drawLevel(Graphics2D g, float cx, float cy, double over)
	{
		String text = xp < 0 ? "-" : String.valueOf(Math.min(Experience.getLevelForXp(xp), Experience.MAX_REAL_LEVEL));
		g.setFont(Theme.title().deriveFont(22f));
		FontMetrics metrics = g.getFontMetrics();
		int x = Math.round(cx - metrics.stringWidth(text) / 2f);
		int y = Math.round(cy - (metrics.getAscent() + metrics.getDescent()) / 2f + metrics.getAscent()) - 1;

		// Dark outline keeps the number readable on top of a bright fill.
		g.setColor(Theme.SHADOW);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					g.drawString(text, x + dx, y + dy);
				}
			}
		}
		g.setColor(over > 0 ? blend(Theme.GOLD_BRIGHT, Color.WHITE, over) : Theme.GOLD_BRIGHT);
		g.drawString(text, x, y);
	}

	private static Ellipse2D circle(float cx, float cy, float r)
	{
		return new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2);
	}

	/** Clockwise from 12 o'clock. */
	private static Arc2D arc(float cx, float cy, float r, double fraction)
	{
		return new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 90f, (float) (-360 * fraction), Arc2D.OPEN);
	}

	private static Color alpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
	}

	private static Color blend(Color a, Color b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
