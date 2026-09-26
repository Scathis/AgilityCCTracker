package com.agilitycctracker.dd;

import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.loadout.ItemNameResolver;
import com.agilitycctracker.loadout.Loadout;
import com.agilitycctracker.loadout.LoadoutContext;
import com.agilitycctracker.loadout.LoadoutEvaluator;
import com.agilitycctracker.loadout.Slot;
import com.agilitycctracker.loadout.WornItem;
import com.agilitycctracker.model.DdTile;
import com.agilitycctracker.model.StatusResponse;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
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
	private static final Set<Slot> ALL_SLOTS = EnumSet.allOf(Slot.class);

	@Inject
	private Client client;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private ItemNameResolver nameResolver;

	@Inject
	private LoadoutEvaluator evaluator;

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
		Loadout ddLoadout = status != null ? Loadout.find(status.getLoadouts(), LoadoutContext.DD) : null;

		WorldPoint location = local.getWorldLocation();
		boolean onDdTile = isOnAnyTile(location, tiles);
		boolean inCourseArea = location.getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;

		List<String> ddFailed = Collections.emptyList();
		if (onDdTile && ddLoadout != null)
		{
			ddFailed = evaluator.evaluate(ddLoadout, readOwnGear(), ALL_SLOTS);
		}

		ddTracker.onObservation(Text.toJagexName(local.getName()), onDdTile, inCourseArea, ddFailed, System.currentTimeMillis());
	}

	private Map<Slot, WornItem> readOwnGear()
	{
		Map<Slot, WornItem> worn = new EnumMap<>(Slot.class);
		ItemContainer container = client.getItemContainer(InventoryID.WORN);
		if (container == null)
		{
			return worn;
		}
		Item[] items = container.getItems();
		for (Slot slot : ALL_SLOTS)
		{
			int idx = slot.toEquipmentSlotIdx();
			if (idx < items.length && items[idx] != null && items[idx].getId() > 0)
			{
				int id = items[idx].getId();
				worn.put(slot, new WornItem(id, nameResolver.resolve(id)));
			}
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
}
