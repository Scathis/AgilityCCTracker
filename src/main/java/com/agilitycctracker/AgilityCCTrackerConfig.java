package com.agilitycctracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("agilitycctracker")
public interface AgilityCCTrackerConfig extends Config
{
	@ConfigSection(
		name = "Connection",
		description = "Self-hosted server connection settings",
		position = 0
	)
	String connectionSection = "connection";

	@ConfigItem(
		keyName = "apiUrl",
		name = "API URL",
		description = "Base URL of the self-hosted tracker server, e.g. https://agilitycc.grestinator.com. Plugin is idle when blank.",
		section = connectionSection,
		position = 0
	)
	default String apiUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "apiToken",
		name = "API token",
		description = "FC group token from the dashboard",
		section = connectionSection,
		position = 1,
		secret = true
	)
	default String apiToken()
	{
		return "";
	}
}
