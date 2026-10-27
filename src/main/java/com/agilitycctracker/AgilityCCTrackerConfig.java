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
		description = "Reporting which required gear slots other FC members in view have empty, and their DD presence",
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
		description = "Runner or Anti. Only runners get missing-gear checks and are tracked for laps and DD; antis are attendance time only.",
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
		keyName = "shareRoster",
		name = "Share roster",
		description = "Upload the FC roster while an event is active",
		section = sharingSection,
		position = 0
	)
	default boolean shareRoster()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fcObserver",
		name = "FC observer",
		description = "Report which required gear slots FC members in view have empty (never what they are wearing), and their DD presence. Also needs the server's own observer switch enabled.",
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
		name = "Gear chat warnings",
		description = "Show a chat warning when your own gear has gaps (empty required slot, no bow/crossbow, crossbow without a shield)",
		section = displaySection,
		position = 1
	)
	default boolean loadoutWarnings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGearFlags",
		name = "Show gear flags",
		description = "Show a short label over runners with missing gear. Only shown to friends chat members at or above the rank the organisers set; ignored for everyone else.",
		section = displaySection,
		position = 2
	)
	default boolean showGearFlags()
	{
		return true;
	}
}
