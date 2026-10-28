package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.scan.FlaggedPlayer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

/**
 * Marks runners with missing gear, so antis can see who needs to fix up
 * without leaving the game: a small icon left of their name and/or a short
 * amber label above it (each switchable in Settings). Amber, not red, so it
 * doesn't read as a PKer's or skulled player's name. Purely a renderer: the
 * list of who to draw comes from {@link FcMemberScanner#getFlagged()}, which
 * is empty unless the local player is in the tracked friends chat at or above
 * the rank the organisers set, so this draws nothing for anyone else.
 */
@Singleton
public class GearFlagOverlay extends Overlay
{
	private static final Color TEXT_COLOUR = Theme.GOLD_BRIGHT;
	// Where names go: Player Indicators draws them at logical height + 40,
	// same as OverlayUtil.renderActorOverlay.
	private static final int NAME_Z_OFFSET = 40;
	private static final int LINE_GAP = 2;
	private static final int ICON_GAP = 3;

	private final FcMemberScanner fcMemberScanner;
	private final AgilityCCTrackerConfig config;
	private final ChatIconManager chatIconManager;
	private final BufferedImage icon = ImageUtil.loadImageResource(GearFlagOverlay.class, "gear_flag.png");

	@Inject
	private GearFlagOverlay(FcMemberScanner fcMemberScanner, AgilityCCTrackerConfig config, ChatIconManager chatIconManager)
	{
		this.fcMemberScanner = fcMemberScanner;
		this.config = config;
		this.chatIconManager = chatIconManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean showIcon = config.gearFlagIcon();
		boolean showText = config.gearFlagText();
		FontMetrics metrics = graphics.getFontMetrics();
		int lift = metrics.getHeight() + LINE_GAP;
		int textHeight = metrics.getHeight() - metrics.getMaxDescent();
		for (FlaggedPlayer flagged : fcMemberScanner.getFlagged())
		{
			Player player = flagged.getPlayer();
			int z = player.getLogicalHeight() + NAME_Z_OFFSET;
			if (showIcon && player.getName() != null)
			{
				Point name = player.getCanvasTextLocation(graphics, player.getName(), z);
				if (name != null)
				{
					int right = name.getX() - rankIconAllowance(flagged.getRank()) - ICON_GAP;
					int top = name.getY() - textHeight / 2 - icon.getHeight() / 2;
					OverlayUtil.renderImageLocation(graphics, new Point(right - icon.getWidth(), top), icon);
				}
			}
			if (showText)
			{
				String label = flagged.getLabel();
				Point at = player.getCanvasTextLocation(graphics, label, z);
				if (at != null)
				{
					// One line above the name, never on top of it.
					OverlayUtil.renderTextLocation(graphics, new Point(at.getX(), at.getY() - lift), label, TEXT_COLOUR);
				}
			}
		}
		return null;
	}

	/**
	 * Player Indicators (with FC ranks on) centres a rank icon on the name's
	 * left edge and shifts the name right by half its width; start our icon
	 * left of that so the two don't overlap. Unranked members get no rank icon.
	 */
	private int rankIconAllowance(FriendsChatRank rank)
	{
		if (rank == null || rank == FriendsChatRank.UNRANKED)
		{
			return 0;
		}
		BufferedImage rankImage = chatIconManager.getRankImage(rank);
		return rankImage == null ? 0 : rankImage.getWidth() / 2 + 1;
	}
}
