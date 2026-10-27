package com.agilitycctracker.scan;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.MassRole;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.loadout.ItemNameResolver;
import com.agilitycctracker.loadout.GearRuleEvaluator;
import com.agilitycctracker.loadout.GearRules;
import com.agilitycctracker.loadout.Slot;
import com.agilitycctracker.loadout.WornItem;
import com.agilitycctracker.model.DdTile;
import com.agilitycctracker.model.LoadoutCheckDto;
import com.agilitycctracker.model.Participant;
import com.agilitycctracker.model.StatusResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.kit.KitType;
import net.runelite.client.util.Text;

/**
 * The ONLY class in this plugin that reads other players. Runs on GameTick,
 * strictly limited to friends-chat members already on this FC's roster, and
 * emits nothing but requirement-id gaps and booleans - never coordinates,
 * item ids or names of anyone but the local player. See BUILD_PLAN.md
 * §1.1-1.2 and §5.8. Every worn/location value read here is discarded at the
 * end of the loop body; nothing about another player is ever stored in a
 * field.
 */
@Singleton
public class FcMemberScanner
{
	private static final int WILDERNESS_AGILITY_COURSE_REGION = 11837;
	private static final int MAX_PLAYERS_PER_SCAN = 200;
	private static final int MAX_QUEUE = 500;

	private static final Set<Slot> VISIBLE_SLOTS;
	static
	{
		EnumSet<Slot> visible = EnumSet.noneOf(Slot.class);
		for (Slot slot : Slot.values())
		{
			if (slot.toKitType() != null)
			{
				visible.add(slot);
			}
		}
		VISIBLE_SLOTS = visible;
	}

	@Inject
	private Client client;

	@Inject
	private AgilityCCTrackerConfig config;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private ItemNameResolver nameResolver;

	@Inject
	private GearRuleEvaluator evaluator;

	@Inject
	private DdTracker ddTracker;

	private final Map<String, List<String>> lastLoadoutResult = new HashMap<>();
	private final Deque<LoadoutCheckDto> queue = new ArrayDeque<>();
	// Rebuilt fresh every tick this scans at all (see the two early returns
	// below, which also clear it) - a live snapshot of "who did I just see
	// in the course area", read by Uploader every cycle for the
	// course_sightings heartbeat (BUILD_LOG.md: attendance auto-timeout).
	// Deliberately not queue/drain semantics like loadout checks - this is
	// a live heartbeat, not a durable event that needs restore-on-failure.
	private final Map<String, Integer> currentlyInCourse = new ConcurrentHashMap<>();

	// Whether the local player may be shown other players' gear gaps (sidebar
	// list + over-head flags): in the tracked FC, at or above the server's
	// configured rank. Refreshed every scanning tick; false whenever not scanning.
	private volatile boolean viewerMaySeeFlags;
	private volatile List<FlaggedPlayer> flagged = Collections.emptyList();

	private void clearLive()
	{
		currentlyInCourse.clear();
		viewerMaySeeFlags = false;
		flagged = Collections.emptyList();
	}

	public void onTick()
	{
		StatusResponse status = statusHolder.get();
		if (!config.fcObserver() || status == null || !status.isObserverEnabled() || status.getActiveEvent() == null)
		{
			clearLive();
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getWorldLocation().getRegionID() != WILDERNESS_AGILITY_COURSE_REGION)
		{
			clearLive();
			return;
		}

		// Only ever scan the friends chat this server tracks - not whichever
		// chat the player happens to be sitting in.
		FriendsChatManager fc = client.getFriendsChatManager();
		if (fc == null || !FlagAccess.isConfiguredFc(Text.toJagexName(fc.getOwner()), Text.toJagexName(status.getFcOwner())))
		{
			clearLive();
			return;
		}

		currentlyInCourse.clear();
		boolean maySeeFlags = fc.getMyRank() != null
			&& FlagAccess.canViewFlags(true, fc.getMyRank().getValue(), status.getFlagViewerMinRank());
		viewerMaySeeFlags = maySeeFlags;
		List<FlaggedPlayer> flaggedNow = new ArrayList<>();

		GearRules rules = status.getGearRules();
		List<DdTile> tiles = status.getDd() != null ? status.getDd().getTiles() : Collections.emptyList();
		long now = System.currentTimeMillis();

		// Plugin users who answered "no" or are antis are never gear-checked or
		// DD-tracked. Anyone not listed (no plugin, e.g. mobile) is a runner.
		Set<String> excluded = new HashSet<>();
		if (status.getParticipants() != null)
		{
			for (Participant participant : status.getParticipants())
			{
				if (!participant.isAttending() || participant.getRole() == MassRole.ANTI)
				{
					excluded.add(participant.getRsn().toLowerCase());
				}
			}
		}

		int scanned = 0;
		for (Player p : client.getTopLevelWorldView().players())
		{
			if (scanned >= MAX_PLAYERS_PER_SCAN)
			{
				break;
			}
			if (p == local || p.getName() == null || !p.isFriendsChatMember())
			{
				continue;
			}
			String rsn = Text.toJagexName(p.getName());
			if (excluded.contains(rsn.toLowerCase()))
			{
				continue;
			}
			scanned++;

			WorldPoint location = p.getWorldLocation();
			boolean onDdTile = isOnAnyTile(location, tiles);
			boolean inCourseArea = location.getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;
			FriendsChatMember member = fc.findByName(rsn);
			int rank = member != null && member.getRank() != null ? member.getRank().getValue() : -1;
			if (inCourseArea)
			{
				currentlyInCourse.put(rsn, rank);
			}
			Map<Slot, WornItem> worn = readVisibleGear(p);

			if (rules != null)
			{
				List<String> failed = evaluator.evaluate(rules, worn, VISIBLE_SLOTS);
				queueIfChanged(rsn, failed);
				if (maySeeFlags && config.showGearFlags() && inCourseArea && !failed.isEmpty())
				{
					flaggedNow.add(new FlaggedPlayer(p, FlagAccess.describe(failed)));
				}
			}

			// Owner request: ranked members are usually busy with other FC
			// duties during a DD call rather than actually participating -
			// excluded from DD presence entirely (see the matching check
			// and its longer comment in SelfDdSampler.onTick()).
			boolean isRanked = rank >= 0;
			if (!isRanked)
			{
				// DD gear (bow) check dropped 2026-10-27 - presence/timing only.
				ddTracker.onObservation(rsn, onDdTile, inCourseArea, now);
			}

			// worn/location/rsn all go out of scope here - never stored past this iteration.
		}
		flagged = flaggedNow;
	}

	/** Runners to draw a flag over (empty unless the viewer is entitled to see them). Replaced every tick. */
	public List<FlaggedPlayer> getFlagged()
	{
		return flagged;
	}

	private Map<Slot, WornItem> readVisibleGear(Player p)
	{
		Map<Slot, WornItem> worn = new EnumMap<>(Slot.class);
		PlayerComposition composition = p.getPlayerComposition();
		if (composition == null)
		{
			return worn;
		}
		int[] equipmentIds = composition.getEquipmentIds();
		for (Slot slot : VISIBLE_SLOTS)
		{
			KitType kitType = slot.toKitType();
			int ordinal = kitType.ordinal();
			if (ordinal >= equipmentIds.length)
			{
				continue;
			}
			int raw = equipmentIds[ordinal];
			if (raw >= PlayerComposition.ITEM_OFFSET)
			{
				int itemId = raw - PlayerComposition.ITEM_OFFSET;
				worn.put(slot, new WornItem(itemId, nameResolver.resolve(itemId)));
			}
			// raw in [256,2048): a kit/body shape shows, no wearable item in that slot.
			// raw < 256 or -1: empty. Both cases: leave the slot absent from `worn`.
		}
		return worn;
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

	private void queueIfChanged(String rsn, List<String> failed)
	{
		synchronized (lastLoadoutResult)
		{
			List<String> last = lastLoadoutResult.get(rsn);
			if (failed.equals(last))
			{
				return;
			}
			lastLoadoutResult.put(rsn, failed);
		}
		synchronized (queue)
		{
			queue.addLast(new LoadoutCheckDto(rsn, failed, System.currentTimeMillis()));
			while (queue.size() > MAX_QUEUE)
			{
				queue.pollFirst();
			}
		}
	}

	/** For TrackerPanel's "FC members in view" list - labels (rsn + failed requirement ids) only. */
	public Map<String, List<String>> getLastResults()
	{
		// Viewers below the FC-rank threshold get no list at all; their scans still feed the dashboard.
		if (!viewerMaySeeFlags)
		{
			return Collections.emptyMap();
		}
		synchronized (lastLoadoutResult)
		{
			return new HashMap<>(lastLoadoutResult);
		}
	}

	/** For Uploader's course_sightings heartbeat - FC members seen in the course area this tick, with their FC rank. */
	public Map<String, Integer> getCurrentlyInCourse()
	{
		return new HashMap<>(currentlyInCourse);
	}

	public List<LoadoutCheckDto> drain()
	{
		synchronized (queue)
		{
			if (queue.isEmpty())
			{
				return Collections.emptyList();
			}
			List<LoadoutCheckDto> drained = new ArrayList<>(queue);
			queue.clear();
			return drained;
		}
	}

	public void restore(List<LoadoutCheckDto> checks)
	{
		synchronized (queue)
		{
			List<LoadoutCheckDto> combined = new ArrayList<>(checks);
			combined.addAll(queue);
			queue.clear();
			queue.addAll(combined);
			while (queue.size() > MAX_QUEUE)
			{
				queue.pollLast();
			}
		}
	}

	public void reset()
	{
		synchronized (lastLoadoutResult)
		{
			lastLoadoutResult.clear();
		}
		synchronized (queue)
		{
			queue.clear();
		}
		clearLive();
	}
}
