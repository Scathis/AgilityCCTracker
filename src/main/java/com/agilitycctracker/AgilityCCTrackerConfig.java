package com.agilitycctracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AgilityCCTrackerConfig.GROUP)
public interface AgilityCCTrackerConfig extends Config
{
	String GROUP = "wildyagilitymass";

	@ConfigSection(
		name = "Mass",
		description = "Your role in the Agility CC mass",
		position = 0
	)
	String massSection = "mass";

	@ConfigSection(
		name = "FC observer",
		description = "Reporting on other FC members in the course",
		position = 1
	)
	String observerSection = "observer";

	@ConfigSection(
		name = "Display",
		description = "Panel, chat and overlay options - nothing here changes what is sent",
		position = 2
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "role",
		name = "My role",
		description = "Runner (laps, DD, gear checks) or Anti (attendance only)",
		section = massSection,
		position = 0
	)
	default MassRole role()
	{
		return MassRole.RUNNER;
	}

	@ConfigItem(
		keyName = "selectedKitId",
		name = "",
		description = "Kit chosen in the sidebar panel's dropdown",
		hidden = true
	)
	default long selectedKitId()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "fcObserver",
		name = "FC observer",
		description = "Report FC members in the course: name, rank, empty slots, DD time",
		section = observerSection,
		position = 0
	)
	default boolean fcObserver()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPanel",
		name = "Show sidebar panel",
		description = "Show the tracker's sidebar panel",
		section = displaySection,
		position = 0
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "loadoutWarnings",
		name = "Pre-mass check messages",
		description = "Chat warnings about missing gear or kit items",
		section = displaySection,
		position = 1
	)
	default boolean loadoutWarnings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gearFlagIcon",
		name = "Gear flag icon",
		description = "Icon left of runners' names when gear is missing (FC ranks set by organisers)",
		section = displaySection,
		position = 2
	)
	default boolean gearFlagIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gearFlagText",
		name = "Gear flag text",
		description = "What's missing, shown above runners' names (FC ranks set by organisers)",
		section = displaySection,
		position = 3
	)
	default boolean gearFlagText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "flagFilter",
		name = "",
		description = "Gap types picked in the sidebar's Runners in view filter (empty = all)",
		hidden = true
	)
	default String flagFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showAgilityGoal",
		name = "Show agility goal",
		description = "Agility goal card in the sidebar: laps to go and what your tickets are worth",
		section = displaySection,
		position = 6
	)
	default boolean showAgilityGoal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "goalCollapsed",
		name = "",
		description = "Agility goal card collapsed to its one-line summary (toggled from the sidebar)",
		hidden = true
	)
	default boolean goalCollapsed()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showPopups",
		name = "Show pop-ups",
		description = "Show the in-game mass boxes (chat messages still show)",
		section = displaySection,
		position = 4
	)
	default boolean showPopups()
	{
		return true;
	}

	@Range(min = 50, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "popupScale",
		name = "Pop-up size",
		description = "Size of the in-game mass boxes",
		section = displaySection,
		position = 5
	)
	default int popupScale()
	{
		return 100;
	}
}
