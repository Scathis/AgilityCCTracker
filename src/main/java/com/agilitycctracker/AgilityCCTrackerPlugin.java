package com.agilitycctracker;

import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.api.Uploader;
import com.agilitycctracker.collect.DeathTracker;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.RosterTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.dd.DdCallDetector;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.dd.SelfDdSampler;
import com.agilitycctracker.loadout.SelfLoadoutCheck;
import com.agilitycctracker.model.DdConfig;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.ui.TrackerPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
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
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

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

	@Inject
	private SelfLoadoutCheck selfLoadoutCheck;

	@Inject
	private FcMemberScanner fcMemberScanner;

	@Inject
	private DdCallDetector ddCallDetector;

	@Inject
	private DdTracker ddTracker;

	@Inject
	private SelfDdSampler selfDdSampler;

	@Inject
	private TrackerPanel trackerPanel;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navigationButton;
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

		trackerPanel.init();
		if (config.showPanel())
		{
			addPanel();
		}
	}

	@Override
	protected void shutDown()
	{
		log.info("Wildy Agility FC Tracker stopped");
		removePanel();
	}

	private void addPanel()
	{
		if (navigationButton != null)
		{
			return;
		}
		BufferedImage icon = loadIcon();
		navigationButton = NavigationButton.builder()
			.tooltip("Wildy Agility FC Tracker")
			.icon(icon)
			.panel(trackerPanel)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	private void removePanel()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
	}

	private BufferedImage loadIcon()
	{
		try
		{
			return ImageIO.read(getClass().getResourceAsStream("panel_icon.png"));
		}
		catch (IOException e)
		{
			log.warn("Failed to load panel icon", e);
			return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		}
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
		selfLoadoutCheck.reset();
		fcMemberScanner.reset();
		ddTracker.reset();
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

		if (unsupportedWorld())
		{
			return;
		}

		fcMemberScanner.onTick();
		selfDdSampler.onTick();

		StatusResponse status = statusHolder.get();
		DdConfig dd = status != null ? status.getDd() : null;
		if (dd != null)
		{
			ddTracker.autoCloseIfExpired(System.currentTimeMillis(), dd.getMaxMinutes());
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
		if (event.getContainerId() == InventoryID.WORN)
		{
			selfLoadoutCheck.refresh();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (unsupportedWorld())
		{
			return;
		}
		lapTracker.onChatMessage(event);
		ddCallDetector.onChatMessage(event);
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
		selfLoadoutCheck.refresh();

		if ("showPanel".equals(event.getKey()))
		{
			if (config.showPanel())
			{
				addPanel();
			}
			else
			{
				removePanel();
			}
		}
	}

	@Schedule(period = 1, unit = ChronoUnit.SECONDS)
	public void onSecondTick()
	{
		if (!unsupportedWorld())
		{
			selfStateCollector.onTick();
			rosterTracker.onTick();
		}
		SwingUtilities.invokeLater(trackerPanel::refresh);
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
		selfLoadoutCheck.refresh();
	}
}
