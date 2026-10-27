package com.agilitycctracker.scan;

import lombok.Value;
import net.runelite.api.Player;

/**
 * A runner the viewer is entitled to see a gear flag for: the live Player (so
 * the overlay can anchor text above their head) plus the short label. Built
 * fresh by {@link FcMemberScanner} every tick and never stored beyond the
 * next tick; the overlay only ever draws it, nothing is read from the Player
 * here and nothing is uploaded from this object.
 */
@Value
public class FlaggedPlayer
{
	Player player;
	String label;
}
