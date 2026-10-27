package com.agilitycctracker.mass;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.MassRole;
import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.Participant;
import com.agilitycctracker.model.StatusResponse;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/**
 * Per-mass opt-in. Nothing is collected, scanned or uploaded until the player
 * says they're attending the currently active mass: when the dashboard starts
 * one, an in-game prompt ({@code ui.MassPromptOverlay}) offers Runner / Anti /
 * Not today, and the sidebar panel has the same choices.
 * The answer is recorded server-side, so logging back in mid-mass restores it
 * silently instead of asking again. Ending the mass, switching account or
 * logging out clears it. Being in the course during a mass counts as
 * attending regardless of the answer - see {@link #onLocation}.
 */
@Slf4j
@Singleton
public class MassParticipation
{
	private static final long NONE = -1;
	private static final long AUTO_ATTEND_RETRY_MS = 30_000;

	/** What {@link #decide} wants done with a fresh /status. */
	public enum Action
	{
		NONE,
		/** No active mass any more (or a different one started): drop the old answer. */
		RESET,
		/** The server already has an "attending" answer from this player. */
		RESTORE_ATTENDING,
		/** The server already has a "declined" answer from this player. */
		RESTORE_DECLINED,
		/** Active mass, no answer yet: ask. */
		PROMPT
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AgilityCCTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ApiClient apiClient;

	@Inject
	private ScheduledExecutorService executor;

	private volatile String localRsn;
	private volatile long answeredEventId = NONE;
	private volatile boolean attending;
	private volatile long promptedEventId = NONE;
	private volatile String lastError;
	private volatile long autoAttemptEventId = NONE;
	private volatile long autoAttemptAt;
	/** The mass the in-game prompt is currently asking about, or null when it's hidden. */
	private volatile ActiveEvent pendingPrompt;
	private volatile boolean sending;

	private volatile Runnable onActivated = () ->
	{
	};
	private volatile Runnable onDeactivated = () ->
	{
	};

	/**
	 * Pure decision step, kept static so the state machine is unit-testable
	 * without Swing, Guice or a client.
	 */
	public static Action decide(StatusResponse status, String myRsn, long answeredEventId, boolean attending, long promptedEventId)
	{
		if (status == null)
		{
			return Action.NONE;
		}
		ActiveEvent event = status.getActiveEvent();
		if (event == null)
		{
			return (attending || answeredEventId != NONE) ? Action.RESET : Action.NONE;
		}
		if (answeredEventId != NONE && answeredEventId != event.getId())
		{
			return Action.RESET;
		}
		if (myRsn == null)
		{
			return Action.NONE;
		}
		if (status.getParticipants() != null)
		{
			for (Participant participant : status.getParticipants())
			{
				if (myRsn.equalsIgnoreCase(participant.getRsn()))
				{
					if (participant.isAttending())
					{
						return (attending && answeredEventId == event.getId()) ? Action.NONE : Action.RESTORE_ATTENDING;
					}
					return (!attending && answeredEventId == event.getId()) ? Action.NONE : Action.RESTORE_DECLINED;
				}
			}
		}
		if (answeredEventId == event.getId() || promptedEventId == event.getId())
		{
			return Action.NONE;
		}
		return Action.PROMPT;
	}

	/** Called by the plugin once a tick (client thread) with the local player's normalised name, or null when logged out. */
	public void setLocalRsn(String rsn)
	{
		localRsn = rsn;
	}

	public String getLocalRsn()
	{
		return localRsn;
	}

	public void setCallbacks(Runnable onActivated, Runnable onDeactivated)
	{
		this.onActivated = onActivated;
		this.onDeactivated = onDeactivated;
	}

	public boolean isAttending()
	{
		return attending;
	}

	/** Attending as a runner - the only state in which laps, DD and gear checks apply to this player. */
	public boolean isRunner()
	{
		return attending && config.role() == MassRole.RUNNER;
	}

	/** True once the player answered "not today" for the active mass. */
	public boolean hasDeclined(StatusResponse status)
	{
		return !attending && status != null && status.getActiveEvent() != null
			&& answeredEventId == status.getActiveEvent().getId();
	}

	/** Why the last answer didn't go through, or null. */
	public String getLastError()
	{
		return lastError;
	}

	/** An answer is on its way to the server. */
	public boolean isSending()
	{
		return sending;
	}

	/** The mass the in-game prompt should be showing, or null. */
	public ActiveEvent getPendingPrompt()
	{
		return pendingPrompt;
	}

	/** Hide the in-game prompt without answering - the sidebar panel can still answer. */
	public void dismissPrompt()
	{
		pendingPrompt = null;
	}

	/**
	 * Answer for the given mass, from the in-game prompt or the sidebar panel
	 * (any thread). Saves the role to config when it changed, so the player's
	 * choice sticks for the next mass too.
	 */
	public void answer(long eventId, boolean yes, MassRole role)
	{
		if (sending)
		{
			return;
		}
		final MassRole chosen = role != null ? role : config.role();
		if (yes && chosen != config.role())
		{
			configManager.setConfiguration("agilitycctracker", "role", chosen);
		}
		pendingPrompt = null;
		sending = true;
		executor.submit(() ->
		{
			try
			{
				sendAnswer(eventId, yes, chosen);
			}
			finally
			{
				sending = false;
			}
		});
	}

	/**
	 * Called with every fresh /status (any thread). {@code fetchedFor} is the name the status
	 * was requested for; if the player has since logged out or switched account the
	 * response describes someone else and is ignored.
	 */
	public void onStatus(StatusResponse status, String fetchedFor)
	{
		if (fetchedFor == null || !fetchedFor.equalsIgnoreCase(localRsn))
		{
			return;
		}
		Action action = decide(status, fetchedFor, answeredEventId, attending, promptedEventId);
		if (action == Action.RESET)
		{
			reset();
			action = decide(status, fetchedFor, answeredEventId, attending, promptedEventId);
		}
		switch (action)
		{
			case RESTORE_ATTENDING:
			{
				MassRole serverRole = roleOnServer(status, fetchedFor);
				activate(status.getActiveEvent().getId());
				if (serverRole != config.role())
				{
					// Config is the source of truth for the role; tell the server if it differs.
					sendAnswer(status.getActiveEvent().getId(), true, config.role());
				}
				break;
			}
			case RESTORE_DECLINED:
				reset();
				answeredEventId = status.getActiveEvent().getId();
				promptedEventId = answeredEventId;
				break;
			case PROMPT:
				promptedEventId = status.getActiveEvent().getId();
				showPrompt(status.getActiveEvent());
				break;
			default:
				break;
		}
	}

	private MassRole roleOnServer(StatusResponse status, String rsn)
	{
		for (Participant participant : status.getParticipants())
		{
			if (rsn.equalsIgnoreCase(participant.getRsn()))
			{
				return participant.getRole();
			}
		}
		return null;
	}

	/** The player changed their Runner/Anti setting mid-mass. */
	public void onRoleChanged(StatusResponse status)
	{
		if (!attending || status == null || status.getActiveEvent() == null)
		{
			return;
		}
		long eventId = status.getActiveEvent().getId();
		MassRole role = config.role();
		executor.submit(() -> sendAnswer(eventId, true, role));
		onActivated.run();
	}

	/** Forget everything: logout, account switch, or the mass ended. */
	public void reset()
	{
		boolean wasAttending = attending;
		attending = false;
		answeredEventId = NONE;
		promptedEventId = NONE;
		lastError = null;
		autoAttemptEventId = NONE;
		pendingPrompt = null;
		if (wasAttending)
		{
			onDeactivated.run();
		}
	}

	private void activate(long eventId)
	{
		answeredEventId = eventId;
		promptedEventId = eventId;
		lastError = null;
		if (!attending)
		{
			attending = true;
			onActivated.run();
		}
	}

	private void showPrompt(ActiveEvent event)
	{
		pendingPrompt = event;
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=ff9040>Agility mass started - pick Runner or Anti on the prompt at the top of your screen.</col>", null));
	}

	private boolean sendAnswer(long eventId, boolean yes, MassRole role)
	{
		String rsn = localRsn;
		if (rsn == null)
		{
			lastError = "Log in to answer";
			return false;
		}
		boolean ok = apiClient.postRespond(rsn, eventId, yes, role);
		if (!ok)
		{
			lastError = "Couldn't reach the server - try again";
			log.debug("respond failed");
			return false;
		}
		pendingPrompt = null;
		if (yes)
		{
			activate(eventId);
		}
		else
		{
			boolean was = attending;
			answeredEventId = eventId;
			promptedEventId = eventId;
			attending = false;
			lastError = null;
			if (was)
			{
				onDeactivated.run();
			}
		}
		return true;
	}

	/**
	 * Owner rule: being in the course during a mass means you attended, whatever
	 * the popup said (forgot to answer, closed it, clicked No by mistake). Pure so
	 * the throttle is testable: retries a failed attempt at most every
	 * {@link #AUTO_ATTEND_RETRY_MS}, and never while already attending.
	 */
	public static boolean shouldAutoAttend(boolean attending, boolean inCourse, boolean inTrackedFc,
		long eventId, long lastAttemptEventId, long lastAttemptAt, long now)
	{
		if (attending || !inCourse || !inTrackedFc)
		{
			return false;
		}
		return lastAttemptEventId != eventId || now - lastAttemptAt >= AUTO_ATTEND_RETRY_MS;
	}

	/**
	 * Called every game tick (client thread) while not attending. Marks the player
	 * attending with their configured role once they're in the course during the
	 * active mass, while in the friends chat the server tracks.
	 */
	public void onLocation(StatusResponse status, boolean inCourse, boolean inTrackedFc)
	{
		if (status == null || status.getActiveEvent() == null || localRsn == null)
		{
			return;
		}
		ActiveEvent event = status.getActiveEvent();
		long now = System.currentTimeMillis();
		if (!shouldAutoAttend(attending, inCourse, inTrackedFc, event.getId(), autoAttemptEventId, autoAttemptAt, now))
		{
			return;
		}
		autoAttemptEventId = event.getId();
		autoAttemptAt = now;
		MassRole role = config.role();
		executor.submit(() ->
		{
			if (sendAnswer(event.getId(), true, role))
			{
				clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=ff9040>You're in the course during the mass, so you've been marked as attending ("
						+ role + "). You can switch role in the tracker's sidebar panel.</col>", null));
			}
		});
	}
}
