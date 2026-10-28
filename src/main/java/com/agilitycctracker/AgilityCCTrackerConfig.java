package com.agilitycctracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("agilitycctracker")
public interface AgilityCCTrackerConfig extends Config
{
	@ConfigSection(
		name = "Mass",
		description = "Your role in the Agility CC mass",
		position = 0
	)
	String massSection = "mass";

	@ConfigSection(
		name = "FC observer",
		description = "What your plugin reports about other Agility CC members it can see in the course",
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
		description = "Runner or Anti. Runners are tracked for laps and DD and get missing-gear checks; antis are attendance time only. Changing it mid-mass starts a separate record for the new role.",
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
		description = "While you're attending and standing in the course, report Agility CC members you can see there: their name and FC rank (this is how players without the plugin get attendance), which required gear slots they have empty (never what they're wearing), and their time on the DD tiles during a DD call. Antis and people not attending are skipped. Also needs the organisers' observer switch on.",
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
		description = "Red chat lines about your own gear (an empty required slot, no bow/crossbow, a crossbow without a shield) and your kit. Shown once when you join a mass or log in during one, and when you open a bank if something's still missing.",
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

	@Range(min = 50, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "popupScale",
		name = "Pop-up size",
		description = "Size of the in-game boxes (the \"Agility mass started\" prompt and the mass over / mass bank alerts). Lower it on a small screen; text gets slightly softer below about 75%.",
		section = displaySection,
		position = 3
	)
	default int popupScale()
	{
		return 100;
	}
}
