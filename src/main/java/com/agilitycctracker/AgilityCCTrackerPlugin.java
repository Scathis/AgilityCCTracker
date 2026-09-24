package com.agilitycctracker;

import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.api.Uploader;
import com.agilitycctracker.collect.DeathTracker;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.RosterTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.google.inject.Provides;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

/**
 * Ties everything together: holds the {@code @Subscribe}/{@code @Schedule}
 * methods RuneLite actually calls (only the Plugin instance itself is
 * auto-registered with the event bus), and delegates to the plain component
 * classes that hold the real logic. Skips all collection/upload on
 * unsupported world types, and resets every component's state on logout /
 * world-hop / account change.
 */
@Slf4j
@PluginDescriptor(
	name = "Wildy Agility FC Tracker",
	description = "Tracks attendance, laps, loadout compliance and DD calls for a Wilderness Agility friends chat, syncing to a self-hosted server",
	tags = {"agility", "wilderness", "friends chat", "fc", "mass", "tracker", "dd"}
)
public class AgilityCCTrackerPlugin extends Plugin
{
	private static final Set<WorldType> UNSUPPORTED_WORLD_TYPES = EnumSet.of(
		WorldType.SEASONAL,
		WorldType.DEADMAN,
		WorldType.BETA_WORLD,
		WorldType.TOURNAMENT_WORLD,
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.NOSAVE_MODE,
		WorldType.FRESH_START_WORLD
	);

	@Inject
	private Client client;

	@Inject
	private AgilityCCTrackerConfig config;

	@Inject
	private SelfStateCollector selfStateCollector;

	@Inject
	private LapTracker lapTracker;

	@Inject
	private DeathTracker deathTracker;

	@Inject
	private RosterTracker rosterTracker;

	@Inject
	private ApiClient apiClient;

	@Inject
	private Uploader uploader;

	@Inject
	private StatusHolder statusHolder;

	private long lastAccountHash = -1;

	@Provides
	AgilityCCTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AgilityCCTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Wildy Agility FC Tracker started");
		lastAccountHash = client.getAccountHash();
	}

	@Override
	protected void shutDown()
	{
		log.info("Wildy Agility FC Tracker stopped");
	}

	private boolean unsupportedWorld()
	{
		Set<WorldType> types = client.getWorldType();
		for (WorldType type : UNSUPPORTED_WORLD_TYPES)
		{
			if (types.contains(type))
			{
				return true;
			}
		}
		return false;
	}

	private void resetState()
	{
		selfStateCollector.reset();
		lapTracker.reset();
		deathTracker.reset();
		rosterTracker.reset();
		statusHolder.set(null);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			resetState();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long hash = client.getAccountHash();
		if (hash != lastAccountHash)
		{
			resetState();
			lastAccountHash = hash;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (unsupportedWorld())
		{
			return;
		}
		selfStateCollector.onStatChanged();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (unsupportedWorld())
		{
			return;
		}
		selfStateCollector.onItemContainerChanged(event);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (unsupportedWorld())
		{
			return;
		}
		lapTracker.onChatMessage(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (unsupportedWorld())
		{
			return;
		}
		deathTracker.onActorDeath(event);
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
	{
		rosterTracker.onMemberChanged();
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
	{
		rosterTracker.onMemberChanged();
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		if (event.isJoined())
		{
			rosterTracker.onFriendsChatJoined();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"agilitycctracker".equals(event.getGroup()))
		{
			return;
		}
		apiClient.onConfigChanged();
		uploader.pollStatus();
	}

	@Schedule(period = 1, unit = ChronoUnit.SECONDS)
	public void onSecondTick()
	{
		if (unsupportedWorld())
		{
			return;
		}
		selfStateCollector.onTick();
		rosterTracker.onTick();
	}

	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void onUploadTick()
	{
		uploader.upload();
	}

	@Schedule(period = 60, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void onStatusTick()
	{
		uploader.pollStatus();
	}
}
