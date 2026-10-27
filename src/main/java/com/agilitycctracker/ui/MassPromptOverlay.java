package com.agilitycctracker.ui;

import com.agilitycctracker.MassRole;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.ActiveEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The "Agility mass started" prompt, drawn inside the game view instead of
 * a separate window so it never steals focus or covers the client. Three
 * buttons answer it (Runner / Anti / Not today) and the cross hides it
 * without answering - the sidebar panel can still answer after that. Clicks
 * on it are consumed so they never reach the game underneath.
 */
@Singleton
public class MassPromptOverlay extends Overlay implements MouseListener
{
	private static final int WIDTH = 240;
	private static final int HEIGHT = 86;
	private static final int PAD = 10;
	private static final int BUTTON_H = 24;
	private static final int BUTTON_GAP = 6;
	private static final int CLOSE = 10;

	private enum Target
	{
		RUNNER("Runner"),
		ANTI("Anti"),
		NO("Not today"),
		CLOSE("");

		final String label;

		Target(String label)
		{
			this.label = label;
		}
	}

	private final MassParticipation massParticipation;

	private final Rectangle[] buttons = new Rectangle[3];
	private final Rectangle close;
	private volatile Target hover;

	@Inject
	MassPromptOverlay(MassParticipation massParticipation)
	{
		this.massParticipation = massParticipation;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		setMovable(true);

		int buttonW = (WIDTH - PAD * 2 - BUTTON_GAP * 2) / 3;
		int y = HEIGHT - PAD - BUTTON_H;
		for (int i = 0; i < 3; i++)
		{
			buttons[i] = new Rectangle(PAD + i * (buttonW + BUTTON_GAP), y, buttonW, BUTTON_H);
		}
		close = new Rectangle(WIDTH - PAD - CLOSE + 2, 7, CLOSE, CLOSE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (massParticipation.getPendingPrompt() == null)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Box with a slow gold pulse on the border, so it's noticed without being loud.
		double pulse = (Math.sin(System.currentTimeMillis() / 400.0) + 1) / 2;
		g.setColor(new Color(Theme.BACKGROUND.getRed(), Theme.BACKGROUND.getGreen(), Theme.BACKGROUND.getBlue(), 238));
		g.fillRoundRect(0, 0, WIDTH, HEIGHT, 6, 6);
		g.setColor(Theme.SHADOW);
		g.setStroke(new BasicStroke(3f));
		g.drawRoundRect(1, 1, WIDTH - 3, HEIGHT - 3, 6, 6);
		Color edge = Theme.GOLD_BRIGHT;
		g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), (int) (110 + 145 * pulse)));
		g.setStroke(new BasicStroke(1.2f));
		g.drawRoundRect(2, 2, WIDTH - 5, HEIGHT - 5, 5, 5);

		g.setFont(Theme.title());
		centred(g, "Agility mass started", 22, Theme.GOLD_BRIGHT);
		g.setFont(Theme.body());
		centred(g, "Are you attending?", 40, Theme.TEXT);

		Target current = hover;
		for (int i = 0; i < 3; i++)
		{
			Target target = Target.values()[i];
			drawButton(g, buttons[i], target.label, i < 2, current == target);
		}

		g.setColor(current == Target.CLOSE ? Theme.GOLD_BRIGHT : Theme.MUTED);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
		g.drawLine(close.x, close.y, close.x + CLOSE - 2, close.y + CLOSE - 2);
		g.drawLine(close.x + CLOSE - 2, close.y, close.x, close.y + CLOSE - 2);

		return new Dimension(WIDTH, HEIGHT);
	}

	private static void centred(Graphics2D g, String text, int baseline, Color color)
	{
		FontMetrics metrics = g.getFontMetrics();
		int x = (WIDTH - metrics.stringWidth(text)) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	private static void drawButton(Graphics2D g, Rectangle r, String label, boolean primary, boolean hovered)
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

	/** Which part of the prompt is under the mouse, or null when it's hidden or the mouse is elsewhere. */
	private Target hit(Point canvasPoint)
	{
		if (massParticipation.getPendingPrompt() == null)
		{
			return null;
		}
		Rectangle bounds = getBounds();
		if (bounds == null || bounds.isEmpty() || !bounds.contains(canvasPoint))
		{
			return null;
		}
		Point local = new Point(canvasPoint.x - bounds.x, canvasPoint.y - bounds.y);
		for (int i = 0; i < 3; i++)
		{
			if (buttons[i].contains(local))
			{
				return Target.values()[i];
			}
		}
		Rectangle closeHit = new Rectangle(close.x - 4, close.y - 4, CLOSE + 8, CLOSE + 8);
		return closeHit.contains(local) ? Target.CLOSE : null;
	}

	private boolean insidePrompt(Point canvasPoint)
	{
		Rectangle bounds = getBounds();
		return massParticipation.getPendingPrompt() != null
			&& bounds != null && !bounds.isEmpty() && bounds.contains(canvasPoint);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (!SwingUtilities.isLeftMouseButton(e) || e.isAltDown() || !insidePrompt(e.getPoint()))
		{
			return e; // alt+drag is RuneLite's own "move overlay"
		}
		Target target = hit(e.getPoint());
		ActiveEvent event = massParticipation.getPendingPrompt();
		if (target != null && event != null)
		{
			switch (target)
			{
				case RUNNER:
					massParticipation.answer(event.getId(), true, MassRole.RUNNER);
					break;
				case ANTI:
					massParticipation.answer(event.getId(), true, MassRole.ANTI);
					break;
				case NO:
					massParticipation.answer(event.getId(), false, null);
					break;
				default:
					massParticipation.dismissPrompt();
					break;
			}
			hover = null;
		}
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return consumeIfInside(e);
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return consumeIfInside(e);
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		hover = hit(e.getPoint());
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		hover = null;
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	private MouseEvent consumeIfInside(MouseEvent e)
	{
		if (SwingUtilities.isLeftMouseButton(e) && !e.isAltDown() && insidePrompt(e.getPoint()))
		{
			e.consume();
		}
		return e;
	}
}
