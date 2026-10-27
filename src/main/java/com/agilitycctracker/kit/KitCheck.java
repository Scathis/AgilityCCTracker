package com.agilitycctracker.kit;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.loadout.Slot;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.Kit;
import com.agilitycctracker.model.StatusResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Local-only pre-course check: while a runner is attending a mass but not yet
 * in the course, compares their worn gear and inventory with the selected
 * kit and lists what's missing (sidebar + one chat line), and tells the bank
 * overlay which items to outline. Nothing here is ever uploaded.
 *
 * Item variants count as the same item (RuneLite's ItemVariationMapping), so
 * any dose of a potion, charged/uncharged versions etc. all satisfy the kit;
 * inventory quantities count potions, not doses.
 */
@Singleton
public class KitCheck
{
	@Value
	public static class Missing
	{
		/** Equipment slot, or null for an inventory item. */
		Slot slot;
		int itemId;
		int qty;
	}

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private MassParticipation massParticipation;

	@Inject
	private AgilityCCTrackerConfig config;

	private volatile Kit activeKit;
	private volatile List<String> missingLines = Collections.emptyList();
	private volatile Set<Integer> missingBaseIds = Collections.emptySet();
	private long announcedForEvent = -1;

	/** The selected kit if it still exists, else the FC's default, else the first one. */
	public static Kit selectKit(List<Kit> kits, long selectedId)
	{
		if (kits == null || kits.isEmpty())
		{
			return null;
		}
		Kit fallback = kits.get(0);
		for (Kit kit : kits)
		{
			if (kit.getId() == selectedId)
			{
				return kit;
			}
			if (kit.isDefault())
			{
				fallback = kit;
			}
		}
		return fallback;
	}

	/**
	 * Pure comparison. {@code wornBase}: slot -> base item id worn there;
	 * {@code carriedByBase}: base item id -> quantity carried; {@code base}
	 * maps any item id to its base variant.
	 */
	public static List<Missing> computeMissing(Kit kit, Map<Slot, Integer> wornBase, Map<Integer, Integer> carriedByBase, IntUnaryOperator base)
	{
		List<Missing> missing = new ArrayList<>();
		if (kit.getEquipment() != null)
		{
			for (Kit.Equipment equipment : kit.getEquipment())
			{
				Integer worn = wornBase.get(equipment.getSlot());
				if (worn == null || worn != base.applyAsInt(equipment.getItemId()))
				{
					missing.add(new Missing(equipment.getSlot(), equipment.getItemId(), 1));
				}
			}
		}
		if (kit.getInventory() != null)
		{
			// The same item can be listed twice in a kit; add the counts up first.
			Map<Integer, Integer> needByBase = new HashMap<>();
			Map<Integer, Integer> exampleId = new HashMap<>();
			for (Kit.InventoryItem item : kit.getInventory())
			{
				int b = base.applyAsInt(item.getItemId());
				needByBase.merge(b, Math.max(1, item.getQty()), Integer::sum);
				exampleId.putIfAbsent(b, item.getItemId());
			}
			for (Map.Entry<Integer, Integer> need : needByBase.entrySet())
			{
				int have = carriedByBase.getOrDefault(need.getKey(), 0);
				if (have < need.getValue())
				{
					missing.add(new Missing(null, exampleId.get(need.getKey()), need.getValue() - have));
				}
			}
		}
		return missing;
	}

	/** Called every game tick (client thread) while attending. */
	public void onTick(boolean inCourse)
	{
		StatusResponse status = statusHolder.get();
		boolean shouldRun = massParticipation.isRunner() && !inCourse && status != null && status.getActiveEvent() != null;
		Kit kit = shouldRun ? selectKit(status.getKits(), config.selectedKitId()) : null;
		if (kit == null)
		{
			clear();
			return;
		}

		List<Missing> missing = computeMissing(kit, readWorn(), readCarried(), this::base);
		List<String> lines = new ArrayList<>();
		Set<Integer> bases = new HashSet<>();
		for (Missing m : missing)
		{
			String name = itemManager.getItemComposition(m.getItemId()).getName();
			lines.add(m.getSlot() != null ? "Wear: " + name : "Bring: " + name + (m.getQty() > 1 ? " x" + String.format("%,d", m.getQty()) : ""));
			bases.add(base(m.getItemId()));
		}
		activeKit = kit;
		missingLines = lines;
		missingBaseIds = bases;

		long eventId = status.getActiveEvent().getId();
		if (announcedForEvent != eventId)
		{
			announcedForEvent = eventId;
			String text = missing.isEmpty()
				? "Pre-mass check (" + kit.getName() + "): you're all set."
				: "Pre-mass check (" + kit.getName() + "): " + missing.size() + " thing(s) missing - see the tracker sidebar; they're outlined in your bank.";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff9040>" + text + "</col>", null);
		}
	}

	public boolean isActive()
	{
		return activeKit != null;
	}

	public String getKitName()
	{
		Kit kit = activeKit;
		return kit != null ? kit.getName() : null;
	}

	public List<String> getMissingLines()
	{
		return missingLines;
	}

	/** For the bank overlay (client thread). */
	public boolean isMissing(int itemId)
	{
		Set<Integer> bases = missingBaseIds;
		return !bases.isEmpty() && bases.contains(base(itemId));
	}

	private void clear()
	{
		activeKit = null;
		missingLines = Collections.emptyList();
		missingBaseIds = Collections.emptySet();
	}

	public void reset()
	{
		clear();
		announcedForEvent = -1;
	}

	private int base(int itemId)
	{
		return ItemVariationMapping.map(itemManager.canonicalize(itemId));
	}

	private Map<Slot, Integer> readWorn()
	{
		Map<Slot, Integer> worn = new EnumMap<>(Slot.class);
		ItemContainer container = client.getItemContainer(InventoryID.WORN);
		if (container == null)
		{
			return worn;
		}
		Item[] items = container.getItems();
		for (Slot slot : Slot.values())
		{
			int idx = slot.toEquipmentSlotIdx();
			if (idx < items.length && items[idx] != null && items[idx].getId() > 0)
			{
				worn.put(slot, base(items[idx].getId()));
			}
		}
		return worn;
	}

	private Map<Integer, Integer> readCarried()
	{
		Map<Integer, Integer> carried = new HashMap<>();
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container == null)
		{
			return carried;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0)
			{
				carried.merge(base(item.getId()), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}
		return carried;
	}
}
