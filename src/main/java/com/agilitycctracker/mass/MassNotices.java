package com.agilitycctracker.mass;

import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.StatusResponse;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Short in-game alerts drawn like the attending prompt
 * ({@code ui.MassNoticeOverlay}), each dismissable:
 * <ul>
 * <li><b>Mass over</b> - when staff stop the mass, to players who were
 * attending and are in the course at that moment. Hides itself after
 * {@link #MASS_OVER_SHOW_MS}.</li>
 * <li><b>Mass bank</b> - when staff call one from the dashboard (only possible
 * during a mass), to runners attending it who are in the course during the
 * next {@link #MASS_BANK_WINDOW_MS}; it disappears when that minute is up.</li>
 * </ul>
 * Status arrives on a background thread; location on the client thread.
 */
@Singleton
public class MassNotices
{
	public static final long MASS_BANK_WINDOW_MS = 60_000;
	public static final long MASS_OVER_SHOW_MS = 120_000;
	private static final long NONE = -1;

	@Value
	public static class Notice
	{
		String title;
		String message;
		/** Local clock, ms. */
		long expiresAt;
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private volatile Notice notice;
	private volatile boolean inCourse;
	private volatile long lastEventId = NONE;

	/** A mass bank call that can still be shown: its id (server ms) and when its window ends (local ms). */
	private volatile long pendingBankAt = NONE;
	private volatile long pendingBankUntil;
	private volatile long shownBankAt = NONE;

	/** Pure: did the mass the player was in just end (stopped, or replaced by a new one)? */
	static boolean massJustEnded(long lastEventId, ActiveEvent now)
	{
		return lastEventId != NONE && (now == null || now.getId() != lastEventId);
	}

	/**
	 * Pure: how much of a mass bank call's alert window is left, in ms (0 or
	 * less = over). Uses the server's own clock for both ends, so a player's
	 * wrong PC clock doesn't matter.
	 */
	static long bankWindowLeft(Long massBankAt, long serverTime)
	{
		if (massBankAt == null)
		{
			return 0;
		}
		return MASS_BANK_WINDOW_MS - Math.max(0, serverTime - massBankAt);
	}

	/**
	 * Every fresh /status (background thread). {@code wasAttending} is the
	 * player's state before MassParticipation processed this status.
	 */
	public void onStatus(StatusResponse status, boolean wasAttending)
	{
		ActiveEvent event = status.getActiveEvent();
		if (massJustEnded(lastEventId, event) && wasAttending && inCourse)
		{
			show("Agility mass over", "Thanks for running with Agility CC!", MASS_OVER_SHOW_MS);
		}
		lastEventId = event != null ? event.getId() : NONE;

		Long bankAt = event != null ? event.getMassBankAt() : null;
		long left = bankWindowLeft(bankAt, status.getServerTime());
		if (bankAt != null && left > 0 && bankAt != shownBankAt)
		{
			pendingBankAt = bankAt;
			pendingBankUntil = System.currentTimeMillis() + left;
		}
		else if (event == null)
		{
			pendingBankAt = NONE;
		}
	}

	/** Every game tick (client thread): shows a pending mass bank alert once the player qualifies. */
	public void onTick(boolean inCourse, boolean runner)
	{
		this.inCourse = inCourse;
		long bankAt = pendingBankAt;
		if (bankAt == NONE)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= pendingBankUntil)
		{
			pendingBankAt = NONE;
			return;
		}
		if (inCourse && runner)
		{
			shownBankAt = bankAt;
			pendingBankAt = NONE;
			show("Mass bank starting",
				"Make your way to the mage bank to secure your loot. 40+ laps is recommended",
				pendingBankUntil - now);
		}
	}

	/** The notice to draw, or null. */
	public Notice getNotice()
	{
		Notice current = notice;
		if (current != null && System.currentTimeMillis() >= current.getExpiresAt())
		{
			notice = null;
			return null;
		}
		return current;
	}

	public void dismiss()
	{
		notice = null;
	}

	/** Logout / account switch. */
	public void reset()
	{
		notice = null;
		lastEventId = NONE;
		pendingBankAt = NONE;
	}

	private void show(String title, String message, long forMs)
	{
		notice = new Notice(title, message, System.currentTimeMillis() + forMs);
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=ff0000>" + title + " - " + message + "</col>", null));
	}
}
