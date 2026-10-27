package com.agilitycctracker.ui;

import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.scan.FlaggedPlayer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
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
		for (FlaggedPlayer flagged : fcMemberScanner.getFlagged())
		{
			OverlayUtil.renderActorOverlay(graphics, flagged.getPlayer(), flagged.getLabel(), FLAG_COLOUR);
		}
		return null;
	}
}
