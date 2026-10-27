package com.agilitycctracker.dd;

import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.model.DdTile;
import com.agilitycctracker.model.StatusResponse;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

/**
 * Feeds the local player's own position + own worn items into {@link DdTracker}
 * each game tick. Deliberately separate from {@code FcMemberScanner} - self's
 * own gear needs full 11-slot visibility (unlike the restricted view of other
 * players), and keeping self-reading out of the one "reads others" class
 * matches BUILD_PLAN.md §1.2's review-friendly class boundary.
 */
@Singleton
public class SelfDdSampler
{
	private static final int WILDERNESS_AGILITY_COURSE_REGION = 11837;

	@Inject
	private Client client;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private DdTracker ddTracker;

	public void onTick()
	{
		if (!ddTracker.isWindowOpen())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		// Owner request: ranked members are usually busy with other FC
		// duties during a DD call (calling it, watching chat, managing the
		// queue) rather than actually participating - excluded from DD
		// presence entirely, self included. This is a separate, hardcoded
		// rule from the group's configurable dd.min_rank (who's *allowed to
		// call* DD) - not the same concept, even though both currently
		// happen to use the same threshold.
		FriendsChatManager fc = client.getFriendsChatManager();
		if (fc != null && fc.getMyRank() != null && fc.getMyRank().getValue() >= 0)
		{
			return;
		}

		StatusResponse status = statusHolder.get();
		List<DdTile> tiles = status != null && status.getDd() != null ? status.getDd().getTiles() : Collections.emptyList();

		WorldPoint location = local.getWorldLocation();
		boolean onDdTile = isOnAnyTile(location, tiles);
		boolean inCourseArea = location.getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;

		// The DD gear (bow) check was dropped 2026-10-27 - presence/timing only.
		ddTracker.onObservation(Text.toJagexName(local.getName()), onDdTile, inCourseArea, Collections.emptyList(), System.currentTimeMillis());
	}

	private boolean isOnAnyTile(WorldPoint location, List<DdTile> tiles)
	{
		for (DdTile tile : tiles)
		{
			if (location.getX() == tile.getX() && location.getY() == tile.getY() && location.getPlane() == tile.getPlane())
			{
				return true;
			}
		}
		return false;
	}
}
