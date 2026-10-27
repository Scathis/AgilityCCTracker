package com.agilitycctracker.mass;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.MassRole;
import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.Participant;
import com.agilitycctracker.model.StatusResponse;
import java.awt.GridLayout;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/**
 * Per-mass opt-in. Nothing is collected, scanned or uploaded until the player
 * says they're attending the currently active mass: when the dashboard starts
 * one, a popup asks "Mass started - attending?" (Yes/No plus Runner/Anti).
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

	/** Short text for the sidebar panel. */
	public String describe(StatusResponse status)
	{
		if (status == null || status.getActiveEvent() == null)
		{
			return "no mass running";
		}
		if (attending)
		{
			return "attending as " + config.role();
		}
		if (answeredEventId == status.getActiveEvent().getId())
		{
			return "not attending";
		}
		return lastError != null ? lastError : "waiting for your answer";
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

	/** Manual "attend this mass" (sidebar button) - re-opens the prompt for the active mass. */
	public void promptNow(StatusResponse status)
	{
		if (status == null || status.getActiveEvent() == null)
		{
			return;
		}
		promptedEventId = status.getActiveEvent().getId();
		showPrompt(status.getActiveEvent());
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
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=ff0000>Mass started: " + event.getName() + " - answer the Wildy Agility FC Tracker popup (or use its sidebar panel).</col>", null));

		SwingUtilities.invokeLater(() ->
		{
			JComboBox<MassRole> roleBox = new JComboBox<>(MassRole.values());
			roleBox.setSelectedItem(config.role());
			JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
			panel.add(new JLabel("Mass started: " + event.getName()));
			panel.add(new JLabel("Are you attending?"));
			panel.add(new JLabel("I am a:"));
			panel.add(roleBox);

			JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
			JDialog dialog = pane.createDialog("Wildy Agility FC Tracker");
			dialog.setAlwaysOnTop(true);
			dialog.setVisible(true);
			dialog.dispose();

			Object value = pane.getValue();
			if (!(value instanceof Integer))
			{
				return; // dismissed without answering: nothing is sent, the sidebar button can still be used
			}
			boolean yes = (Integer) value == JOptionPane.YES_OPTION;
			MassRole role = (MassRole) roleBox.getSelectedItem();
			if (role == null)
			{
				role = MassRole.RUNNER;
			}
			if (role != config.role())
			{
				configManager.setConfiguration("agilitycctracker", "role", role);
			}
			final MassRole chosen = role;
			executor.submit(() -> sendAnswer(event.getId(), yes, chosen));
		});
	}

	private boolean sendAnswer(long eventId, boolean yes, MassRole role)
	{
		String rsn = localRsn;
		if (rsn == null)
		{
			lastError = "log in to answer";
			return false;
		}
		boolean ok = apiClient.postRespond(rsn, eventId, yes, role);
		if (!ok)
		{
			lastError = "could not reach the server - try the button again";
			log.debug("respond failed");
			return false;
		}
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
					"<col=ff0000>You're in the course during " + event.getName() + ", so you've been marked as attending ("
						+ role + "). Change your role in the Wildy Agility FC Tracker settings.</col>", null));
			}
		});
	}
}
