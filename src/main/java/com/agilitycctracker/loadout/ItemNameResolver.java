package com.agilitycctracker.loadout;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

/**
 * Client-thread-only id -&gt; normalised-name cache. Normalisation strips
 * trailing bracketed tags repeatedly (e.g. "(i)", "(cr)") so ornament/charge
 * variants of the same base item still match the same loadout requirement.
 */
@Singleton
public class ItemNameResolver
{
	private static final Pattern TRAILING_TAG = Pattern.compile("\\s*\\([^)]*\\)\\s*$");

	@Inject
	private ItemManager itemManager;

	private final ConcurrentMap<Integer, String> cache = new ConcurrentHashMap<>();

	/** Client-thread only - reads item composition data. */
	public String resolve(int itemId)
	{
		return cache.computeIfAbsent(itemId,
			id -> normalize(itemManager.getItemComposition(itemManager.canonicalize(id)).getName()));
	}

	/** Pure and package-visible for unit testing. */
	static String normalize(String name)
	{
		if (name == null)
		{
			return null;
		}
		String result = name.toLowerCase().trim();
		while (true)
		{
			String stripped = TRAILING_TAG.matcher(result).replaceAll("").trim();
			if (stripped.equals(result))
			{
				return result;
			}
			result = stripped;
		}
	}
}
