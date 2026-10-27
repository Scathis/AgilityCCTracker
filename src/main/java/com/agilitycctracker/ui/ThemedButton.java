package com.agilitycctracker.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * Flat button in the dashboard palette, painted by hand so it looks the same
 * whatever look-and-feel the client is running. PRIMARY is filled gold,
 * SECONDARY is a raised brown box, PILL is a small gold-edged chip (for
 * inline actions like switching role), TAB is a tab-strip label underlined
 * while selected, LINK is text only.
 */
public class ThemedButton extends JButton
{
	public enum Style
	{
		PRIMARY,
		SECONDARY,
		PILL,
		TAB,
		LINK
	}

	private final Style style;
	private boolean tabSelected;

	public ThemedButton(String text, Style style)
	{
		super(text);
		this.style = style;
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFont(style == Style.LINK || style == Style.PILL ? Theme.small() : Theme.body());
	}

	/** TAB only: draw as the active tab. */
	public void setTabSelected(boolean selected)
	{
		if (selected != tabSelected)
		{
			tabSelected = selected;
			repaint();
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics metrics = getFontMetrics(getFont());
		int w = metrics.stringWidth(getText()) + (style == Style.LINK ? 4 : style == Style.PILL ? 22 : 16);
		int h = metrics.getHeight() + (style == Style.LINK ? 2 : style == Style.PILL ? 6 : 8);
		return new Dimension(w, h);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean hover = getModel().isRollover() && isEnabled();
		boolean pressed = getModel().isPressed();
		int w = getWidth();
		int h = getHeight();

		java.awt.Color text;
		switch (style)
		{
			case PRIMARY:
				g.setColor(pressed ? Theme.GOLD : hover ? Theme.GOLD_PALE : Theme.GOLD_BRIGHT);
				g.fillRoundRect(0, 0, w - 1, h - 1, 4, 4);
				g.setColor(Theme.CARD_BORDER);
				g.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
				text = Theme.BACKGROUND;
				break;
			case SECONDARY:
				g.setColor(pressed ? Theme.CARD : hover ? Theme.RAISED_HOVER : Theme.RAISED);
				g.fillRoundRect(0, 0, w - 1, h - 1, 4, 4);
				g.setColor(hover ? Theme.GOLD : Theme.CARD_BORDER);
				g.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
				text = Theme.GOLD_BRIGHT;
				break;
			case PILL:
				g.setColor(hover ? Theme.GOLD_BRIGHT : Theme.RAISED);
				g.fillRoundRect(0, 0, w - 1, h - 1, h, h);
				g.setColor(Theme.GOLD);
				g.drawRoundRect(0, 0, w - 1, h - 1, h, h);
				text = hover ? Theme.BACKGROUND : Theme.GOLD_BRIGHT;
				break;
			case TAB:
				if (tabSelected)
				{
					g.setColor(Theme.GOLD_BRIGHT);
					g.fillRect(0, h - 2, w, 2);
				}
				else if (hover)
				{
					g.setColor(Theme.LINE);
					g.fillRect(0, h - 2, w, 2);
				}
				text = tabSelected ? Theme.GOLD_BRIGHT : hover ? Theme.TEXT : Theme.MUTED;
				break;
			default:
				text = hover ? Theme.GOLD_BRIGHT : Theme.MUTED;
				break;
		}
		if (!isEnabled())
		{
			text = Theme.MUTED;
		}

		g.setFont(getFont());
		FontMetrics metrics = g.getFontMetrics();
		int x = (w - metrics.stringWidth(getText())) / 2;
		int y = (h - metrics.getHeight()) / 2 + metrics.getAscent();
		g.setColor(text);
		g.drawString(getText(), x, y);
		g.dispose();
	}
}
