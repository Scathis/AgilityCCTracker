package com.agilitycctracker.loadout;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FlagAccess;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/**
 * Evaluates the FC's runner gear rules (from /status) against the local player's own
 * gear, on all 11 slots - unlike other players, self has full visibility of
 * their own equipment (BUILD_PLAN.md §5.7).
 */
@Singleton
public class SelfLoadoutCheck
{
	private static final Set<Slot> ALL_SLOTS = EnumSet.allOf(Slot.class);

	@Inject
	private Client client;

	@Inject
	private ItemNameResolver nameResolver;

	@Inject
	private GearRuleEvaluator evaluator;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private SelfStateCollector selfStateCollector;

	@Inject
	private AgilityCCTrackerConfig config;

	private List<String> lastFailed;

	/** Call on WORN change and whenever /status refreshes. */
	public void refresh()
	{
		StatusResponse status = statusHolder.get();
		GearRules rules = status != null ? status.getGearRules() : null;
		if (rules == null)
		{
			return;
		}

		Map<Slot, WornItem> worn = readOwnGear();
		List<String> failed = evaluator.evaluate(rules, worn, ALL_SLOTS);
		if (failed.equals(lastFailed))
		{
			return;
		}
		lastFailed = failed;
		selfStateCollector.setLoadoutFailed(failed);

		if (config.loadoutWarnings() && !failed.isEmpty())
		{
			warnInChat(failed);
		}
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

	private void warnInChat(List<String> failedSlots)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>FC gear check: " + FlagAccess.describe(failedSlots) + "</col>", null);
	}

	/** For TrackerPanel display - never empty-vs-null ambiguous. */
	public List<String> getLastFailed()
	{
		return lastFailed != null ? lastFailed : java.util.Collections.emptyList();
	}

	public void reset()
	{
		lastFailed = null;
	}
}
