package com.agilitycctracker.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Pure evaluation of a {@link Loadout} against a set of worn items - no
 * RuneLite client imports at all (BUILD_PLAN.md §5.7), so it's directly
 * unit-testable. Returns ONLY the names of failed slots, never any item
 * data - that's what keeps the gaps-only design reviewable.
 *
 * Per-slot semantics (one {@link Requirement} per slot, at most):
 * - required=true, no allow-list: slot must have something worn, any item.
 * - required=true, allow-list set: slot must have one of those specific items.
 * - required=false, allow-list set: an empty slot passes; if something IS
 *   worn there, it must still be one of the allowed items.
 * - no Requirement for a slot at all: unrestricted, never checked.
 */
@Singleton
public class LoadoutEvaluator
{
	public List<String> evaluate(Loadout loadout, Map<Slot, WornItem> worn, Set<Slot> visibleSlots)
	{
		List<String> failed = new ArrayList<>();
		for (Requirement req : loadout.getRequirements())
		{
			if (!visibleSlots.contains(req.getSlot()))
			{
				continue;
			}
			WornItem item = worn.get(req.getSlot());
			boolean hasAllowList = req.getAllowedItemIds() != null && !req.getAllowedItemIds().isEmpty();

			if (item == null)
			{
				if (req.isRequired())
				{
					failed.add(req.getSlot().name());
				}
				continue;
			}
			if (hasAllowList && !req.getAllowedItemIds().contains(item.getId()))
			{
				failed.add(req.getSlot().name());
			}
		}
		return failed;
	}
}
