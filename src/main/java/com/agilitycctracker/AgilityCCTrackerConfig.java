package com.agilitycctracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("agilitycctracker")
public interface AgilityCCTrackerConfig extends Config
{
	@ConfigSection(
		name = "Mass",
		description = "Your role in the mass",
		position = 0
	)
	String massSection = "mass";

	@ConfigSection(
		name = "Sharing",
		description = "What your own plugin reports about you",
		position = 1
	)
	String sharingSection = "sharing";

	@ConfigSection(
		name = "FC observer",
		description = "Reporting loadout gaps / DD presence of other FC members in view",
		position = 2
	)
	String observerSection = "observer";

	@ConfigSection(
		name = "Display",
		description = "Overlay, panel and chat display options",
		position = 3
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "role",
		name = "My role",
		description = "Runner or Anti. Only runners are gear-checked and tracked for laps and DD; antis are attendance time only.",
		section = massSection,
		position = 0
	)
	default MassRole role()
	{
		return MassRole.RUNNER;
	}

	@ConfigItem(
		keyName = "shareStats",
		name = "Share stats",
		description = "Upload your own skills and combat level",
		section = sharingSection,
		position = 0
	)
	default boolean shareStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareGear",
		name = "Share gear",
		description = "Upload your own worn items",
		section = sharingSection,
		position = 1
	)
	default boolean shareGear()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareRoster",
		name = "Share roster",
		description = "Upload the FC roster while an event is active",
		section = sharingSection,
		position = 2
	)
	default boolean shareRoster()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fcObserver",
		name = "FC observer",
		description = "Report loadout gaps and DD presence of FC members in view. Also needs the server's own observer switch enabled.",
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
		name = "Loadout chat warnings",
		description = "Show a chat warning when your own loadout has gaps",
		section = displaySection,
		position = 1
	)
	default boolean loadoutWarnings()
	{
		return true;
	}
}
