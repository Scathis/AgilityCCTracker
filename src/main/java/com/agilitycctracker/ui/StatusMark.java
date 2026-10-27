package com.agilitycctracker.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** A small painted tick (green disc) or cross (red disc) - the RuneScape font has no such glyphs. */
public class StatusMark extends JComponent
{
	private static final int SIZE = 12;

	private boolean ok;

	public StatusMark()
	{
		setOpaque(false);
		Dimension size = new Dimension(SIZE, SIZE);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
	}

	public void setOk(boolean ok)
	{
		if (ok != this.ok)
		{
			this.ok = ok;
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int s = Math.min(getWidth(), getHeight()) - 1;
		g.setColor(ok ? Theme.GOOD : Theme.BAD);
		g.fillOval(0, 0, s, s);
		g.setColor(Theme.BACKGROUND);
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (ok)
		{
			g.drawPolyline(new int[] {3, 5, 9}, new int[] {6, 8, 4}, 3);
		}
		else
		{
			g.drawLine(4, 4, s - 4, s - 4);
			g.drawLine(s - 4, 4, 4, s - 4);
		}
		g.dispose();
	}
}
