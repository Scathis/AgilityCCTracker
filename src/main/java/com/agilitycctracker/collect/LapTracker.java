package com.agilitycctracker.collect;

import com.agilitycctracker.model.LapEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

/**
 * Detects Wilderness Agility lap completions from the raw game-message chat
 * line and queues one {@link LapEvent} per lap for the next upload.
 *
 * <p>Only the chat-message signal is implemented (BUILD_PLAN.md §5.4's
 * secondary tile-based signal is deliberately deferred — it's explicitly a
 * fallback "for lap timing if the chat message is disabled", and nothing in
 * this project disables that message).
 */
@Singleton
public class LapTracker
{
	private static final Pattern LAP_PATTERN = Pattern.compile(
		"Your Wilderness Agility lap count is: (?:<col=[0-9a-f]{6}>)?([0-9,]+)(?:</col>)?\\.?");

	private static final int MAX_QUEUE = 500;

	@Inject
	private SelfStateCollector selfStateCollector;

	private final Deque<LapEvent> queue = new ArrayDeque<>();
	private Integer lastLapCount;
	private Long lastLapCompletedAt;
	private int lastTicketCount = -1;
	private int sessionLapCount;
	private int sessionTicketsGained;

	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		Integer lapCount = parseLapCount(event.getMessage());
		if (lapCount == null)
		{
			return;
		}
		onLap(lapCount, System.currentTimeMillis());
	}

	private synchronized void onLap(int lapCount, long completedAt)
	{
		if (lastLapCount != null && lapCount == lastLapCount)
		{
			// Duplicate re-fire of the same message (e.g. a client redraw); ignore.
			return;
		}

		Long lapTimeMs = lastLapCompletedAt == null ? null : completedAt - lastLapCompletedAt;

		int currentTickets = selfStateCollector.getWildyTicketCount();
		Integer ticketsGained = lastTicketCount < 0 ? null : Math.max(0, currentTickets - lastTicketCount);

		queue.addLast(new LapEvent(lapCount, completedAt, lapTimeMs, ticketsGained));
		while (queue.size() > MAX_QUEUE)
		{
			queue.pollFirst();
		}

		lastLapCount = lapCount;
		lastLapCompletedAt = completedAt;
		lastTicketCount = currentTickets;
		sessionLapCount++;
		sessionTicketsGained += ticketsGained != null ? ticketsGained : 0;
	}

	/** Session-scoped (not event-scoped) totals, for TrackerPanel display. */
	public synchronized int getSessionLapCount()
	{
		return sessionLapCount;
	}

	public synchronized int getSessionTicketsGained()
	{
		return sessionTicketsGained;
	}

	/** Removes and returns every queued lap, oldest first. */
	public synchronized List<LapEvent> drain()
	{
		if (queue.isEmpty())
		{
			return Collections.emptyList();
		}
		List<LapEvent> drained = new ArrayList<>(queue);
		queue.clear();
		return drained;
	}

	/** Puts previously-drained events back at the front after a failed upload. */
	public synchronized void restore(List<LapEvent> events)
	{
		List<LapEvent> combined = new ArrayList<>(events);
		combined.addAll(queue);
		queue.clear();
		queue.addAll(combined);
		while (queue.size() > MAX_QUEUE)
		{
			queue.pollLast();
		}
	}

	public synchronized void reset()
	{
		queue.clear();
		lastLapCount = null;
		lastLapCompletedAt = null;
		lastTicketCount = -1;
		sessionLapCount = 0;
		sessionTicketsGained = 0;
	}

	/** Pure and package-visible for unit testing without mocking RuneLite's ChatMessage. */
	static Integer parseLapCount(String rawMessage)
	{
		if (rawMessage == null)
		{
			return null;
		}
		Matcher matcher = LAP_PATTERN.matcher(rawMessage);
		if (!matcher.find())
		{
			return null;
		}
		String digits = matcher.group(1).replace(",", "");
		try
		{
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
