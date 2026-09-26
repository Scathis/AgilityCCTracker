package com.agilitycctracker.scan;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.loadout.ItemNameResolver;
import com.agilitycctracker.loadout.Loadout;
import com.agilitycctracker.loadout.LoadoutContext;
import com.agilitycctracker.loadout.LoadoutEvaluator;
import com.agilitycctracker.loadout.Slot;
import com.agilitycctracker.loadout.WornItem;
import com.agilitycctracker.model.DdTile;
import com.agilitycctracker.model.LoadoutCheckDto;
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
	private LoadoutEvaluator evaluator;

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
	private final Set<String> currentlyInCourse = ConcurrentHashMap.newKeySet();

	public void onTick()
	{
		StatusResponse status = statusHolder.get();
		if (!config.fcObserver() || status == null || !status.isObserverEnabled() || status.getActiveEvent() == null)
		{
			currentlyInCourse.clear();
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getWorldLocation().getRegionID() != WILDERNESS_AGILITY_COURSE_REGION)
		{
			currentlyInCourse.clear();
			return;
		}

		currentlyInCourse.clear();

		Loadout runningLoadout = Loadout.find(status.getLoadouts(), LoadoutContext.RUNNING);
		Loadout ddLoadout = Loadout.find(status.getLoadouts(), LoadoutContext.DD);
		List<DdTile> tiles = status.getDd() != null ? status.getDd().getTiles() : Collections.emptyList();
		FriendsChatManager fc = client.getFriendsChatManager();
		long now = System.currentTimeMillis();

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
			scanned++;

			String rsn = Text.toJagexName(p.getName());
			WorldPoint location = p.getWorldLocation();
			boolean onDdTile = isOnAnyTile(location, tiles);
			boolean inCourseArea = location.getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;
			if (inCourseArea)
			{
				currentlyInCourse.add(rsn);
			}
			Map<Slot, WornItem> worn = readVisibleGear(p);

			if (runningLoadout != null)
			{
				List<String> failed = evaluator.evaluate(runningLoadout, worn, VISIBLE_SLOTS);
				queueIfChanged(rsn, runningLoadout.getId(), failed);
			}

			// Owner request: ranked members are usually busy with other FC
			// duties during a DD call rather than actually participating -
			// excluded from DD presence entirely (see the matching check
			// and its longer comment in SelfDdSampler.onTick()).
			FriendsChatMember member = fc != null ? fc.findByName(rsn) : null;
			boolean isRanked = member != null && member.getRank() != null && member.getRank().getValue() >= 0;
			if (!isRanked)
			{
				List<String> ddFailed = (onDdTile && ddLoadout != null)
					? evaluator.evaluate(ddLoadout, worn, VISIBLE_SLOTS)
					: Collections.emptyList();
				ddTracker.onObservation(rsn, onDdTile, inCourseArea, ddFailed, now);
			}

			// worn/location/rsn all go out of scope here - never stored past this iteration.
		}
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

	private void queueIfChanged(String rsn, long loadoutId, List<String> failed)
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
			queue.addLast(new LoadoutCheckDto(rsn, loadoutId, failed, System.currentTimeMillis()));
			while (queue.size() > MAX_QUEUE)
			{
				queue.pollFirst();
			}
		}
	}

	/** For TrackerPanel's "FC members in view" list - labels (rsn + failed requirement ids) only. */
	public Map<String, List<String>> getLastResults()
	{
		synchronized (lastLoadoutResult)
		{
			return new HashMap<>(lastLoadoutResult);
		}
	}

	/** For Uploader's course_sightings heartbeat - RSNs of FC members seen in the course area as of the most recent tick. */
	public Set<String> getCurrentlyInCourse()
	{
		return new HashSet<>(currentlyInCourse);
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
	}
}
