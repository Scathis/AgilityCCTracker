package com.agilitycctracker.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Pure evaluation of a {@link Loadout} against a set of worn items - no
 * RuneLite client imports at all (BUILD_PLAN.md §5.7), so it's directly
 * unit-testable. Returns ONLY the ids of failed requirements, never any item
 * data - that's what keeps the gaps-only design reviewable.
 */
@Singleton
public class LoadoutEvaluator
{
	public List<String> evaluate(Loadout loadout, Map<Slot, WornItem> worn, Set<Slot> visibleSlots)
	{
		List<String> failed = new ArrayList<>();
		for (Requirement req : loadout.getRequirements())
		{
			if (!isVisible(req.getSlot(), visibleSlots))
			{
				continue;
			}
			if (req.getWhen() != null)
			{
				Condition when = req.getWhen();
				if (!isVisible(when.getSlot(), visibleSlots))
				{
					continue;
				}
				if (!passes(when.getCheck(), worn.get(when.getSlot()), when.getArgs()))
				{
					// Condition not satisfied - the requirement itself is skipped (passes).
					continue;
				}
			}
			if (!passes(req.getCheck(), worn.get(req.getSlot()), req.getArgs()))
			{
				failed.add(req.getId());
			}
		}
		return failed;
	}

	private boolean isVisible(Slot slot, Set<Slot> visibleSlots)
	{
		return slot == null || visibleSlots.contains(slot);
	}

	private boolean passes(Check check, WornItem item, List<String> args)
	{
		switch (check)
		{
			case FILLED:
				return item != null;
			case EMPTY:
				return item == null;
			case NAME_ENDS_WITH:
				return item != null && endsWithAny(item.getNormalizedName(), args);
			case NAME_NOT_ENDS_WITH:
				return item == null || !endsWithAny(item.getNormalizedName(), args);
			case ITEM_IN:
				return item != null && containsId(item.getId(), args);
			case ITEM_NOT_IN:
				return item == null || !containsId(item.getId(), args);
			default:
				throw new IllegalArgumentException("Unknown check: " + check);
		}
	}

	private boolean endsWithAny(String normalizedName, List<String> suffixes)
	{
		if (normalizedName == null)
		{
			return false;
		}
		for (String suffix : suffixes)
		{
			if (normalizedName.endsWith(suffix.toLowerCase().trim()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsId(int id, List<String> ids)
	{
		for (String s : ids)
		{
			try
			{
				if (Integer.parseInt(s.trim()) == id)
				{
					return true;
				}
			}
			catch (NumberFormatException ignored)
			{
				// Not a numeric id - doesn't match, keep checking the rest.
			}
		}
		return false;
	}
}
