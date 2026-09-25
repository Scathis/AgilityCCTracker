package com.agilitycctracker.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wilderness level detection, shared by SelfStateCollector (self.wildy_level)
 * and DeathTracker (deaths[].wildy_level). Mirrors core RuneLite's own
 * CombatLevelPlugin: prefer the PvP icon widget's text, fall back to the
 * surface-coordinate formula when the widget isn't present.
 */
public final class WildernessLevel
{
	private static final Pattern WIDGET_PATTERN = Pattern.compile("^Level: (\\d+)$");

	private WildernessLevel()
	{
	}

	public static Integer fromWidgetText(String text)
	{
		if (text == null)
		{
			return null;
		}
		Matcher matcher = WIDGET_PATTERN.matcher(text.trim());
		if (!matcher.matches())
		{
			return null;
		}
		try
		{
			return Integer.parseInt(matcher.group(1));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Fallback for surface coordinates (y &gt;= 3520) when the widget isn't
	 * available. Returns null outside that range rather than a bogus level.
	 */
	public static Integer fromCoordinate(int y)
	{
		if (y < 3520)
		{
			return null;
		}
		return (y - 3520) / 8 + 1;
	}
}
