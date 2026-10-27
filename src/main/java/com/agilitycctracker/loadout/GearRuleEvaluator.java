package com.agilitycctracker.loadout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Pure evaluation of {@link GearRules} against worn items - no RuneLite client
 * imports, so it's directly unit-testable. Returns ONLY the names of failed
 * slots (e.g. "BOOTS", "WEAPON"), never item data - that's what keeps the
 * gaps-only design reviewable. Slots not in {@code visibleSlots} (ring/ammo
 * on other players) are never reported.
 */
@Singleton
public class GearRuleEvaluator
{
	public List<String> evaluate(GearRules rules, Map<Slot, WornItem> worn, Set<Slot> visibleSlots)
	{
		Set<Slot> failed = EnumSet.noneOf(Slot.class);
		if (rules == null)
		{
			return new ArrayList<>();
		}
		if (rules.getRequiredSlots() != null)
		{
			for (Slot slot : rules.getRequiredSlots())
			{
				if (slot != null && visibleSlots.contains(slot) && worn.get(slot) == null)
				{
					failed.add(slot);
				}
			}
		}
		if (rules.isRangedWeapon() && visibleSlots.contains(Slot.WEAPON))
		{
			WornItem weapon = worn.get(Slot.WEAPON);
			if (weapon == null)
			{
				failed.add(Slot.WEAPON);
			}
			else if (weapon.getNormalizedName() != null)
			{
				// Names come from ItemNameResolver: lower-case, trailing "(i)"/"(u)" etc stripped.
				String name = weapon.getNormalizedName();
				boolean crossbow = name.endsWith("crossbow");
				boolean bow = !crossbow && name.endsWith("bow");
				if (!bow && !crossbow)
				{
					failed.add(Slot.WEAPON);
				}
				else if (crossbow && visibleSlots.contains(Slot.SHIELD) && worn.get(Slot.SHIELD) == null)
				{
					failed.add(Slot.SHIELD);
				}
			}
		}
		List<String> names = new ArrayList<>();
		for (Slot slot : failed)
		{
			names.add(slot.name());
		}
		return names;
	}
}
