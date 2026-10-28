package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.mass.MassNotices;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
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
	private static final int PAD = 10;
	private static final int LINE = 16;
	private static final int BUTTON_W = 80;
	private static final int BUTTON_H = 24;

	private final MassNotices notices;
	private final AgilityCCTrackerConfig config;
	// The message wraps, so the box (and where OK sits) depends on its length.
	private final Rectangle ok = new Rectangle((WIDTH - BUTTON_W) / 2, 0, BUTTON_W, BUTTON_H);
	private final Rectangle close = new Rectangle(WIDTH - PAD - PromptPainter.CLOSE + 2, 7, PromptPainter.CLOSE, PromptPainter.CLOSE);
	private volatile boolean hoverOk;
	private volatile boolean hoverClose;
	// "Pop-up size" as last drawn, so clicks map back to the full-size layout.
	private volatile double scale = 1;

	@Inject
	MassNoticeOverlay(MassNotices notices, AgilityCCTrackerConfig config)
	{
		this.notices = notices;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		MassNotices.Notice notice = visibleNotice();
		if (notice == null)
		{
			return null;
		}
		double s = PromptPainter.scale(config.popupScale());
		scale = s;
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			PromptPainter.applyScale(g2, s);
			return PromptPainter.scaled(WIDTH, draw(g2, notice), s);
		}
		finally
		{
			g2.dispose();
		}
	}

	/** Draws the notice at full size; returns its height. */
	private int draw(Graphics2D g, MassNotices.Notice notice)
	{
		g.setFont(Theme.body());
		List<String> lines = wrap(notice.getMessage(), g.getFontMetrics(), WIDTH - PAD * 2);
		int height = 40 + (lines.size() - 1) * LINE + 12 + BUTTON_H + PAD;
		ok.y = height - PAD - BUTTON_H;

		PromptPainter.box(g, WIDTH, height);
		g.setFont(Theme.title());
		PromptPainter.centred(g, notice.getTitle(), WIDTH, 22, Theme.GOLD_BRIGHT);
		g.setFont(Theme.body());
		for (int i = 0; i < lines.size(); i++)
		{
			PromptPainter.centred(g, lines.get(i), WIDTH, 40 + i * LINE, Theme.TEXT);
		}
		PromptPainter.button(g, ok, "OK", true, hoverOk);
		PromptPainter.closeCross(g, close, hoverClose);
		return height;
	}

	/** Greedy word wrap to fit {@code maxWidth}. */
	static List<String> wrap(String text, FontMetrics metrics, int maxWidth)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && metrics.stringWidth(candidate) > maxWidth)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}

	/** The notice, unless there's none or pop-ups are switched off (the chat line still goes out). */
	private MassNotices.Notice visibleNotice()
	{
		return config.showPopups() ? notices.getNotice() : null;
	}

	private Point local(Point canvasPoint)
	{
		// Checked here too: getBounds() keeps the last drawn box after it stops being drawn.
		if (visibleNotice() == null)
		{
			return null;
		}
		Rectangle bounds = getBounds();
		if (bounds == null || bounds.isEmpty() || !bounds.contains(canvasPoint))
		{
			return null;
		}
		return PromptPainter.unscale(canvasPoint.x - bounds.x, canvasPoint.y - bounds.y, scale);
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
