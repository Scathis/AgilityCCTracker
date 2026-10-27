package com.agilitycctracker.history;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The player's own past masses (laps, time on course, role), kept locally in
 * their RuneLite profile for this account - never sent anywhere. Fed once a
 * game tick while attending; saved every so often and whenever the mass ends
 * or the player logs out.
 */
@Slf4j
@Singleton
public class SessionHistory
{
	static final String KEY = "sessionHistory";
	static final String BEST_LAP_KEY = "bestLapMs";
	/** The course's own fastest possible lap is ~40s; anything quicker is a timing glitch. */
	static final long MIN_REAL_LAP_MS = 30_000;
	static final int MAX_SESSIONS = 30;
	private static final long TICK_MS = 600;
	private static final long SAVE_EVERY_MS = 30_000;
	private static final Type LIST_TYPE = new TypeToken<List<MassSession>>()
	{
	}.getType();

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	/** Newest first. */
	private final List<MassSession> sessions = new ArrayList<>();
	private String loadedFor;
	private long bestLapMs;
	private int lastLapCount;
	private int lastTickets;
	private boolean dirty;
	private long lastSavedAt;

	/**
	 * Called every game tick (client thread) while the player is attending the
	 * active mass. {@code lapCount}/{@code tickets} are LapTracker's running
	 * totals, which reset on world hop - only the increase since the previous
	 * tick is added.
	 */
	public synchronized void onTick(String rsn, long eventId, String eventName, boolean runner,
		boolean inCourse, int lapCount, int tickets)
	{
		ensureLoaded(rsn);
		MassSession current = sessions.isEmpty() ? null : sessions.get(0);
		if (current == null || current.getEventId() != eventId)
		{
			current = new MassSession(eventId, eventName, System.currentTimeMillis(), 0, 0, 0, false, false, 0);
			sessions.add(0, current);
			while (sessions.size() > MAX_SESSIONS)
			{
				sessions.remove(sessions.size() - 1);
			}
			lastLapCount = lapCount;
			lastTickets = tickets;
			dirty = true;
		}
		if (runner)
		{
			current.setRunner(true);
		}
		else
		{
			current.setAnti(true);
		}
		if (inCourse)
		{
			current.setCourseMs(current.getCourseMs() + TICK_MS);
			dirty = true;
		}
		int lapDelta = increase(lastLapCount, lapCount);
		int ticketDelta = increase(lastTickets, tickets);
		if (lapDelta > 0 || ticketDelta > 0)
		{
			current.setLaps(current.getLaps() + lapDelta);
			current.setTickets(current.getTickets() + ticketDelta);
			dirty = true;
		}
		lastLapCount = lapCount;
		lastTickets = tickets;

		long now = System.currentTimeMillis();
		if (dirty && now - lastSavedAt >= SAVE_EVERY_MS)
		{
			save();
		}
	}

	/** A running total that dropped (reset on hop/role change) counts from zero again. */
	static int increase(int previous, int current)
	{
		return current >= previous ? current - previous : current;
	}

	/** Mass ended, logout or account switch: write out anything unsaved. */
	public synchronized void flush()
	{
		if (dirty)
		{
			save();
		}
	}

	/**
	 * A timed lap (LapTracker's listener, client thread) - updates this
	 * mass's fastest and the all-time best. Laps outside a mass session
	 * aren't counted.
	 */
	public synchronized void onLapTime(long ms)
	{
		if (ms < MIN_REAL_LAP_MS || sessions.isEmpty() || loadedFor == null)
		{
			return;
		}
		MassSession current = sessions.get(0);
		if (current.getFastestLapMs() == 0 || ms < current.getFastestLapMs())
		{
			current.setFastestLapMs(ms);
			dirty = true;
		}
		if (bestLapMs == 0 || ms < bestLapMs)
		{
			bestLapMs = ms;
			configManager.setRSProfileConfiguration("agilitycctracker", BEST_LAP_KEY, ms);
		}
	}

	/** All-time fastest lap for this account in ms, 0 when none yet. */
	public synchronized long getBestLapMs(String rsn)
	{
		ensureLoaded(rsn);
		return bestLapMs;
	}

	/** Copy for the sidebar (EDT), newest first. */
	public synchronized List<MassSession> getSessions(String rsn)
	{
		ensureLoaded(rsn);
		List<MassSession> copy = new ArrayList<>();
		for (MassSession s : sessions)
		{
			copy.add(new MassSession(s.getEventId(), s.getName(), s.getStartedAt(), s.getLaps(), s.getTickets(),
				s.getCourseMs(), s.isRunner(), s.isAnti(), s.getFastestLapMs()));
		}
		return copy;
	}

	private void ensureLoaded(String rsn)
	{
		if (rsn == null || rsn.equalsIgnoreCase(loadedFor))
		{
			return;
		}
		if (dirty)
		{
			save();
		}
		sessions.clear();
		loadedFor = rsn;
		dirty = false;
		Long best = configManager.getRSProfileConfiguration("agilitycctracker", BEST_LAP_KEY, Long.class);
		bestLapMs = best != null ? best : 0;
		String json = configManager.getRSProfileConfiguration("agilitycctracker", KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			List<MassSession> stored = gson.fromJson(json, LIST_TYPE);
			if (stored != null)
			{
				sessions.addAll(stored);
			}
		}
		catch (JsonParseException e)
		{
			log.debug("Ignoring unreadable session history", e);
		}
	}

	private void save()
	{
		if (loadedFor == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration("agilitycctracker", KEY,
			gson.toJson(sessions.isEmpty() ? Collections.emptyList() : sessions, LIST_TYPE));
		dirty = false;
		lastSavedAt = System.currentTimeMillis();
	}
}
