package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.MassRole;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.ActiveEvent;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
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
	private static final int CLOSE = PromptPainter.CLOSE;

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
	private final AgilityCCTrackerConfig config;

	private final Rectangle[] buttons = new Rectangle[3];
	private final Rectangle close;
	private volatile Target hover;
	// "Pop-up size" as last drawn, so clicks map back to the full-size layout.
	private volatile double scale = 1;

	@Inject
	MassPromptOverlay(MassParticipation massParticipation, AgilityCCTrackerConfig config)
	{
		this.massParticipation = massParticipation;
		this.config = config;
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
		double s = PromptPainter.scale(config.popupScale());
		scale = s;
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			PromptPainter.applyScale(g2, s);
			draw(g2);
		}
		finally
		{
			g2.dispose();
		}
		return PromptPainter.scaled(WIDTH, HEIGHT, s);
	}

	/** Draws the prompt at full size. */
	private void draw(Graphics2D g)
	{
		PromptPainter.box(g, WIDTH, HEIGHT);
		g.setFont(Theme.title());
		PromptPainter.centred(g, "Agility mass started", WIDTH, 22, Theme.GOLD_BRIGHT);
		g.setFont(Theme.body());
		PromptPainter.centred(g, "Are you attending?", WIDTH, 40, Theme.TEXT);

		Target current = hover;
		for (int i = 0; i < 3; i++)
		{
			Target target = Target.values()[i];
			PromptPainter.button(g, buttons[i], target.label, i < 2, current == target);
		}
		PromptPainter.closeCross(g, close, current == Target.CLOSE);
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
		Point local = PromptPainter.unscale(canvasPoint.x - bounds.x, canvasPoint.y - bounds.y, scale);
		for (int i = 0; i < 3; i++)
		{
			if (buttons[i].contains(local))
			{
				return Target.values()[i];
			}
		}
		return PromptPainter.closeHitArea(close).contains(local) ? Target.CLOSE : null;
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
