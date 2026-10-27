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
 * SECONDARY is a raised brown box, LINK is text only.
 */
public class ThemedButton extends JButton
{
	public enum Style
	{
		PRIMARY,
		SECONDARY,
		LINK
	}

	private final Style style;

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
		setFont(style == Style.LINK ? Theme.small() : Theme.body());
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics metrics = getFontMetrics(getFont());
		int w = metrics.stringWidth(getText()) + (style == Style.LINK ? 4 : 16);
		int h = metrics.getHeight() + (style == Style.LINK ? 2 : 8);
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
