package com.agilitycctracker;

import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.api.Uploader;
import com.agilitycctracker.collect.DeathTracker;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.dd.DdCallDetector;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.dd.SelfDdSampler;
import com.agilitycctracker.history.SessionHistory;
import com.agilitycctracker.loadout.SelfLoadoutCheck;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.DdConfig;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.scan.FlagAccess;
import com.agilitycctracker.kit.KitCheck;
import com.agilitycctracker.ui.GearFlagOverlay;
import com.agilitycctracker.ui.KitBankOverlay;
import com.agilitycctracker.ui.MassPromptOverlay;
import com.agilitycctracker.ui.PanelSnapshot;
import com.agilitycctracker.ui.TrackerPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

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
	description = "Opt-in attendance, laps, missing-gear checks, kits and DD calls for the Agility CC friends chat's Wilderness Agility Course masses",
	tags = {"agility", "wilderness", "friends chat", "fc", "mass", "tracker", "dd"}
)
public class AgilityCCTrackerPlugin extends Plugin
{
	private static final int WILDERNESS_AGILITY_COURSE_REGION = 11837;

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
	private MassParticipation massParticipation;

	@Inject
	private TrackerPanel trackerPanel;

	@Inject
	private GearFlagOverlay gearFlagOverlay;

	@Inject
	private KitCheck kitCheck;

	@Inject
	private KitBankOverlay kitBankOverlay;

	@Inject
	private MassPromptOverlay massPromptOverlay;

	@Inject
	private SessionHistory sessionHistory;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

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

		lapTracker.setLapTimeListener(sessionHistory::onLapTime);
		massParticipation.setCallbacks(
			() -> clientThread.invoke(this::onMassActivated),
			() -> clientThread.invoke(this::clearCollected));

		trackerPanel.init();
		overlayManager.add(gearFlagOverlay);
		overlayManager.add(kitBankOverlay);
		overlayManager.add(massPromptOverlay);
		mouseManager.registerMouseListener(massPromptOverlay);
		if (config.showPanel())
		{
			addPanel();
		}

		// §5.6: "poll immediately on startup" - without this, statusHolder
		// stays null (roster/observer features silently do nothing) until
		// the first 60s scheduled poll, which this spec line exists
		// specifically to avoid.
		pollStatusImmediately();
	}

	@Override
	protected void shutDown()
	{
		log.info("Wildy Agility FC Tracker stopped");
		overlayManager.remove(gearFlagOverlay);
		overlayManager.remove(kitBankOverlay);
		overlayManager.remove(massPromptOverlay);
		mouseManager.unregisterMouseListener(massPromptOverlay);
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

	/** The player just became an attendee (or changed role): start from a clean slate and push a fresh picture. */
	private void onMassActivated()
	{
		clearCollected();
		if (massParticipation.isRunner())
		{
			selfLoadoutCheck.refresh();
		}
	}

	/** Drops everything gathered so far, without touching the cached /status. */
	private void clearCollected()
	{
		sessionHistory.flush();
		selfStateCollector.reset();
		lapTracker.reset();
		deathTracker.reset();
		selfLoadoutCheck.reset();
		fcMemberScanner.reset();
		ddTracker.reset();
		kitCheck.reset();
	}

	private void resetState()
	{
		sessionHistory.flush();
		selfStateCollector.reset();
		lapTracker.reset();
		deathTracker.reset();
		selfLoadoutCheck.reset();
		fcMemberScanner.reset();
		ddTracker.reset();
		kitCheck.reset();
		statusHolder.set(null);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			resetState();
			if (state == GameState.LOGIN_SCREEN)
			{
				// Logged out: the answer is restored from the server on the next login.
				massParticipation.setLocalRsn(null);
				massParticipation.reset();
			}
		}
		else if (state == GameState.LOGGED_IN)
		{
			// resetState() (above) may have just cleared statusHolder to
			// null moments before this fires - poll again here rather than
			// only relying on startUp()'s one-shot poll, since the exact
			// ordering between startUp() and the client's own initial
			// LOGIN_SCREEN transition isn't something this plugin controls.
			pollStatusImmediately();
		}
	}

	private void pollStatusImmediately()
	{
		executor.submit(uploader::pollStatus);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long hash = client.getAccountHash();
		Player local = client.getLocalPlayer();
		massParticipation.setLocalRsn(local != null && local.getName() != null ? Text.toJagexName(local.getName()) : null);

		if (hash != lastAccountHash)
		{
			resetState();
			massParticipation.reset();
			lastAccountHash = hash;
		}

		if (unsupportedWorld())
		{
			return;
		}

		if (!massParticipation.isAttending())
		{
			// In the course during a mass = attended, whatever the popup said.
			StatusResponse status = statusHolder.get();
			boolean inCourse = local != null && local.getWorldLocation().getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;
			massParticipation.onLocation(status, inCourse, inTrackedFc(status));
			kitCheck.reset();
			return;
		}

		boolean inCourse = local != null && local.getWorldLocation().getRegionID() == WILDERNESS_AGILITY_COURSE_REGION;
		// Local-only "are you ready?" check against the chosen kit, until they reach the course.
		kitCheck.onTick(inCourse);

		StatusResponse active = statusHolder.get();
		if (active != null && active.getActiveEvent() != null)
		{
			sessionHistory.onTick(massParticipation.getLocalRsn(), active.getActiveEvent().getId(),
				active.getActiveEvent().getName(), massParticipation.isRunner(), inCourse,
				lapTracker.getSessionLapCount(), lapTracker.getSessionTicketsGained());
		}

		fcMemberScanner.onTick();
		if (massParticipation.isRunner())
		{
			selfDdSampler.onTick();
		}

		StatusResponse status = statusHolder.get();
		DdConfig dd = status != null ? status.getDd() : null;
		if (dd != null)
		{
			ddTracker.autoCloseIfExpired(System.currentTimeMillis(), dd.getMaxMinutes());
		}
	}

	/** In the friends chat the server tracks (client thread). */
	private boolean inTrackedFc(StatusResponse status)
	{
		FriendsChatManager fc = client.getFriendsChatManager();
		return fc != null && status != null
			&& FlagAccess.isConfiguredFc(Text.toJagexName(fc.getOwner()), Text.toJagexName(status.getFcOwner()));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (unsupportedWorld() || !massParticipation.isAttending())
		{
			return;
		}
		selfStateCollector.onItemContainerChanged(event);
		if (event.getContainerId() == InventoryID.WORN && massParticipation.isRunner())
		{
			selfLoadoutCheck.refresh();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Laps and DD calls are runner-only; antis are attendance time only.
		if (unsupportedWorld() || !massParticipation.isRunner())
		{
			return;
		}
		lapTracker.onChatMessage(event);
		ddCallDetector.onChatMessage(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (unsupportedWorld() || !massParticipation.isAttending())
		{
			return;
		}
		deathTracker.onActorDeath(event);
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		if (event.isJoined())
		{
			FriendsChatManager fc = client.getFriendsChatManager();
			if (fc != null)
			{
				log.info("Joined friends chat - name='{}' owner='{}' myRank={} memberCount={}",
					fc.getName(), fc.getOwner(), fc.getMyRank(), fc.getMembers().length);
			}
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

		if ("role".equals(event.getKey()))
		{
			// Antis are attendance-time only: drop anything runner-specific already gathered.
			selfLoadoutCheck.reset();
			selfStateCollector.reset();
			massParticipation.onRoleChanged(statusHolder.get());
		}
		else if (massParticipation.isRunner())
		{
			selfLoadoutCheck.refresh();
		}

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
		if (!unsupportedWorld() && massParticipation.isAttending())
		{
			FriendsChatManager fc = client.getFriendsChatManager();
			int rank = inTrackedFc(statusHolder.get()) && fc.getMyRank() != null ? fc.getMyRank().getValue() : -1;
			selfStateCollector.onTick(rank);
		}
		// This method runs on the client thread, so read here (not inside
		// TrackerPanel.refresh(), which runs on the EDT via invokeLater).
		Player local = client.getLocalPlayer();
		PanelSnapshot snapshot = local != null && local.getName() != null
			? new PanelSnapshot(local.getName(), client.getSkillExperience(Skill.AGILITY), inTrackedFc(statusHolder.get()))
			: PanelSnapshot.LOGGED_OUT;
		SwingUtilities.invokeLater(() -> trackerPanel.refresh(snapshot));
	}

	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void onUploadTick()
	{
		uploader.upload();
	}

	// 10s so the "mass started" prompt appears promptly after the dashboard starts one.
	@Schedule(period = 10, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void onStatusTick()
	{
		uploader.pollStatus();
		// selfLoadoutCheck.refresh() reads the local player's worn-item
		// container, which RuneLite requires happen on the client thread -
		// this @Schedule method is asynchronous (needed for pollStatus()'s
		// blocking HTTP call), so hop back for just this call.
		if (massParticipation.isRunner())
		{
			clientThread.invoke(selfLoadoutCheck::refresh);
		}
	}
}
