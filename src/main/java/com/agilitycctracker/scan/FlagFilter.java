package com.agilitycctracker.scan;

import com.agilitycctracker.loadout.GearRules;
import com.agilitycctracker.loadout.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The sidebar's "Runners in view" filter, which the over-head flags follow
 * too: pick gap types (Amulet, Bow, ...) to show only runners missing one of
 * them; pick none to show everyone missing anything. Pure functions so the
 * rules are unit-testable without a client. Saved as a comma-separated list
 * of slot keys in the hidden {@code flagFilter} config item.
 */
public final class FlagFilter
{
	private FlagFilter()
	{
	}

	/** Gap types the current rules can produce, in slot order (only slots visible on other players). */
	public static List<String> options(GearRules rules)
	{
		List<String> options = new ArrayList<>();
		if (rules == null)
		{
			return options;
		}
		for (Slot slot : Slot.values())
		{
			boolean required = rules.getRequiredSlots() != null && rules.getRequiredSlots().contains(slot)
				&& slot.toKitType() != null;
			boolean ranged = rules.isRangedWeapon() && (slot == Slot.WEAPON || slot == Slot.SHIELD);
			if (required || ranged)
			{
				options.add(slot.name());
			}
		}
		return options;
	}

	public static Set<String> parse(String saved)
	{
		Set<String> keys = new LinkedHashSet<>();
		if (saved == null)
		{
			return keys;
		}
		for (String part : saved.split(","))
		{
			String key = part.trim().toUpperCase(Locale.ROOT);
			if (!key.isEmpty())
			{
				keys.add(key);
			}
		}
		return keys;
	}

	public static String format(Set<String> keys)
	{
		return String.join(",", keys);
	}

	/** The saved picks the current rules still offer; picks for a rule that's since been removed are ignored. */
	public static Set<String> effective(Set<String> picked, List<String> options)
	{
		Set<String> kept = new LinkedHashSet<>(picked);
		kept.retainAll(options);
		return kept.isEmpty() ? Collections.emptySet() : kept;
	}

	/** A runner shows when they're missing something, and - if any types are picked - one of those. */
	public static boolean matches(List<String> failed, Set<String> picked)
	{
		if (failed == null || failed.isEmpty())
		{
			return false;
		}
		if (picked.isEmpty())
		{
			return true;
		}
		for (String key : failed)
		{
			if (picked.contains(key))
			{
				return true;
			}
		}
		return false;
	}

	/** Button text for a gap type: "Bow", "Helm", "Amulet". */
	public static String label(String key)
	{
		String word = FlagAccess.friendly(key);
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
