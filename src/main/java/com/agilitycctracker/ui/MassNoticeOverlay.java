package com.agilitycctracker.ui;

import com.agilitycctracker.mass.MassNotices;
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
 * Draws the current {@link MassNotices} alert ("Agility mass over", "Mass bank
 * starting") in the game view, in the same style as the attending prompt, with
 * an OK button and a × - both dismiss it. Clicks on it never reach the game.
 */
@Singleton
public class MassNoticeOverlay extends Overlay implements MouseListener
{
	private static final int WIDTH = 240;
	private static final int HEIGHT = 86;
	private static final int PAD = 10;
	private static final int BUTTON_W = 80;
	private static final int BUTTON_H = 24;

	private final MassNotices notices;
	private final Rectangle ok = new Rectangle((WIDTH - BUTTON_W) / 2, HEIGHT - PAD - BUTTON_H, BUTTON_W, BUTTON_H);
	private final Rectangle close = new Rectangle(WIDTH - PAD - PromptPainter.CLOSE + 2, 7, PromptPainter.CLOSE, PromptPainter.CLOSE);
	private volatile boolean hoverOk;
	private volatile boolean hoverClose;

	@Inject
	MassNoticeOverlay(MassNotices notices)
	{
		this.notices = notices;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		MassNotices.Notice notice = notices.getNotice();
		if (notice == null)
		{
			return null;
		}
		PromptPainter.box(g, WIDTH, HEIGHT);
		g.setFont(Theme.title());
		PromptPainter.centred(g, notice.getTitle(), WIDTH, 22, Theme.GOLD_BRIGHT);
		g.setFont(Theme.body());
		PromptPainter.centred(g, notice.getMessage(), WIDTH, 40, Theme.TEXT);
		PromptPainter.button(g, ok, "OK", true, hoverOk);
		PromptPainter.closeCross(g, close, hoverClose);
		return new Dimension(WIDTH, HEIGHT);
	}

	private Point local(Point canvasPoint)
	{
		if (notices.getNotice() == null)
		{
			return null;
		}
		Rectangle bounds = getBounds();
		if (bounds == null || bounds.isEmpty() || !bounds.contains(canvasPoint))
		{
			return null;
		}
		return new Point(canvasPoint.x - bounds.x, canvasPoint.y - bounds.y);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		Point p = SwingUtilities.isLeftMouseButton(e) && !e.isAltDown() ? local(e.getPoint()) : null;
		if (p == null)
		{
			return e; // alt+drag is RuneLite's own "move overlay"
		}
		if (ok.contains(p) || PromptPainter.closeHitArea(close).contains(p))
		{
			notices.dismiss();
			hoverOk = false;
			hoverClose = false;
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
		Point p = local(e.getPoint());
		hoverOk = p != null && ok.contains(p);
		hoverClose = p != null && PromptPainter.closeHitArea(close).contains(p);
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
		hoverOk = false;
		hoverClose = false;
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	private MouseEvent consumeIfInside(MouseEvent e)
	{
		if (SwingUtilities.isLeftMouseButton(e) && !e.isAltDown() && local(e.getPoint()) != null)
		{
			e.consume();
		}
		return e;
	}
}
