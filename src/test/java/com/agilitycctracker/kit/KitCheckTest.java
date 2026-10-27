package com.agilitycctracker.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.agilitycctracker.loadout.Slot;
import com.agilitycctracker.model.Kit;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class KitCheckTest
{
	// Pretend ids 100-103 are the four doses of one potion (base 100).
	private static final IntUnaryOperator BASE = id -> id >= 100 && id <= 103 ? 100 : id;

	private static Kit kit(long id, boolean isDefault)
	{
		return new Kit(id, "Kit " + id, isDefault,
			Arrays.asList(new Kit.Equipment(Slot.WEAPON, 9185), new Kit.Equipment(Slot.SHIELD, 1540)),
			Arrays.asList(new Kit.InventoryItem(103, 2), new Kit.InventoryItem(379, 5)));
	}

	@Test
	public void selectKitPrefersTheChoiceThenTheDefaultThenTheFirst()
	{
		List<Kit> kits = Arrays.asList(kit(1, false), kit(2, true), kit(3, false));
		assertEquals(3, KitCheck.selectKit(kits, 3).getId());
		assertEquals(2, KitCheck.selectKit(kits, 99).getId());
		assertEquals(1, KitCheck.selectKit(Arrays.asList(kit(1, false), kit(3, false)), 0).getId());
		assertNull(KitCheck.selectKit(Collections.emptyList(), 1));
		assertNull(KitCheck.selectKit(null, 1));
	}

	@Test
	public void everythingPresentMeansNothingMissing()
	{
		Map<Slot, Integer> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.WEAPON, 9185);
		worn.put(Slot.SHIELD, 1540);
		Map<Integer, Integer> carried = new HashMap<>();
		carried.put(100, 2); // two potions, any dose
		carried.put(379, 7);
		assertEquals(Collections.emptyList(), KitCheck.computeMissing(kit(1, true), worn, carried, BASE));
	}

	@Test
	public void missingGearAndShortInventoryAreListed()
	{
		Map<Slot, Integer> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.WEAPON, 841); // wrong weapon
		Map<Integer, Integer> carried = new HashMap<>();
		carried.put(100, 1);
		List<KitCheck.Missing> missing = KitCheck.computeMissing(kit(1, true), worn, carried, BASE);
		assertEquals(4, missing.size());
		assertEquals(new KitCheck.Missing(Slot.WEAPON, 9185, 1), missing.get(0));
		assertEquals(new KitCheck.Missing(Slot.SHIELD, 1540, 1), missing.get(1));
		// One more potion and all 5 of the other item.
		assertEquals(true, missing.contains(new KitCheck.Missing(null, 103, 1)));
		assertEquals(true, missing.contains(new KitCheck.Missing(null, 379, 5)));
	}

	@Test
	public void theSameItemListedTwiceIsAddedUp()
	{
		Kit doubled = new Kit(1, "K", true, Collections.emptyList(),
			Arrays.asList(new Kit.InventoryItem(101, 1), new Kit.InventoryItem(102, 1)));
		Map<Integer, Integer> carried = new HashMap<>();
		carried.put(100, 1);
		assertEquals(Collections.singletonList(new KitCheck.Missing(null, 101, 1)),
			KitCheck.computeMissing(doubled, new EnumMap<>(Slot.class), carried, BASE));
	}
}
