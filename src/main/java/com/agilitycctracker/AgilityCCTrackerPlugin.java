package com.agilitycctracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Wildy Agility FC Tracker",
	description = "Tracks attendance, laps, loadout compliance and DD calls for a Wilderness Agility friends chat, syncing to a self-hosted server",
	tags = {"agility", "wilderness", "friends chat", "fc", "mass", "tracker", "dd"}
)
public class AgilityCCTrackerPlugin extends Plugin
{
	@Inject
	private AgilityCCTrackerConfig config;

	@Provides
	AgilityCCTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AgilityCCTrackerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Wildy Agility FC Tracker started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Wildy Agility FC Tracker stopped");
	}
}
