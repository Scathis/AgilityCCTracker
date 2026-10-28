package com.agilitycctracker.ui;

import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.scan.FlaggedPlayer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Short red label over runners with missing gear, so antis can see who needs
 * to fix up without leaving the game. Purely a renderer: the list of who to
 * draw comes from {@link FcMemberScanner#getFlagged()}, which is empty unless
 * the local player is in the tracked friends chat at or above the rank the
 * organisers set, so this draws nothing for anyone else.
 */
@Singleton
public class GearFlagOverlay extends Overlay
{
	private static final Color FLAG_COLOUR = new Color(0xff, 0x40, 0x40);
	private static final int NAME_Z_OFFSET = 40;
	private static final int LINE_GAP = 2;

	private final FcMemberScanner fcMemberScanner;

	@Inject
	private GearFlagOverlay(FcMemberScanner fcMemberScanner)
	{
		this.fcMemberScanner = fcMemberScanner;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// One line above where names go (Player Indicators draws them at
		// logical height + 40, same as OverlayUtil.renderActorOverlay), so the
		// label never sits on top of the name.
		int lift = graphics.getFontMetrics().getHeight() + LINE_GAP;
		for (FlaggedPlayer flagged : fcMemberScanner.getFlagged())
		{
			Player player = flagged.getPlayer();
			String label = flagged.getLabel();
			Point at = player.getCanvasTextLocation(graphics, label, player.getLogicalHeight() + NAME_Z_OFFSET);
			if (at != null)
			{
				OverlayUtil.renderTextLocation(graphics, new Point(at.getX(), at.getY() - lift), label, FLAG_COLOUR);
			}
		}
		return null;
	}
}
