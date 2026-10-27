package com.agilitycctracker.dd;

import com.agilitycctracker.model.DdPresenceDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Pure logic (fed by SelfDdSampler/FcMemberScanner each tick, with an
 * explicit {@code now} parameter throughout so it's directly unit-testable
 * with a fake clock, no RuneLite client needed). Tracks who's on the DD tiles
 * while a call window is open, per BUILD_PLAN.md §5.9. Only ever exposes
 * booleans/durations via {@link #summaries()} - never coordinates or item ids.
 */
@Singleton
public class DdTracker
{
	static final long TICK_MS = 600;

	private Long calledAt;
	private Long brokenAt;
	private long arriveGraceMs;
	private long leaveGraceMs;

	private final Map<String, Sample> samples = new HashMap<>();

	private static final class Sample
	{
		Long firstOnTileAt;
		long msOnTile;
		Long lastOnTileAt;
		Long offSince;
		boolean leftBeforeBreak;
		boolean seenRunning;
		long observedMs;
	}

	/** A ranked member called "DD". Ignored if a window is already open. */
	public synchronized void openWindow(long now, long arriveGraceMs, long leaveGraceMs)
	{
		if (isWindowOpen())
		{
			return;
		}
		calledAt = now;
		brokenAt = null;
		this.arriveGraceMs = arriveGraceMs;
		this.leaveGraceMs = leaveGraceMs;
		samples.clear();
	}

	/** A ranked member called "break dd". No-op if no window is open. */
	public synchronized void closeWindow(long now)
	{
		if (!isWindowOpen())
		{
			return;
		}
		brokenAt = now;
	}

	public synchronized boolean isWindowOpen()
	{
		return calledAt != null && brokenAt == null;
	}

	/** Auto-closes a window that's run longer than the configured max_minutes. */
	public synchronized void autoCloseIfExpired(long now, int maxMinutes)
	{
		if (isWindowOpen() && now - calledAt >= maxMinutes * 60_000L)
		{
			brokenAt = now;
		}
	}

	/** One sample for one RSN (self or an observed FC member) this tick. No-op if no window is open. */
	public synchronized void onObservation(String rsn, boolean onDdTile, boolean inCourseArea, long now)
	{
		if (!isWindowOpen())
		{
			return;
		}
		Sample sample = samples.computeIfAbsent(rsn, r -> new Sample());
		sample.observedMs += TICK_MS;

		if (onDdTile)
		{
			if (sample.firstOnTileAt == null)
			{
				sample.firstOnTileAt = now;
			}
			sample.msOnTile += TICK_MS;
			sample.lastOnTileAt = now;
			sample.offSince = null;
			return;
		}

		if (sample.offSince == null)
		{
			sample.offSince = now;
		}
		if (sample.lastOnTileAt != null && now - sample.offSince > leaveGraceMs)
		{
			sample.leftBeforeBreak = true;
		}
		if (inCourseArea && now - calledAt > arriveGraceMs)
		{
			sample.seenRunning = true;
		}
	}

	public synchronized List<DdPresenceDto> summaries()
	{
		List<DdPresenceDto> result = new ArrayList<>();
		for (Map.Entry<String, Sample> entry : samples.entrySet())
		{
			Sample s = entry.getValue();
			Long arrivedAfterMs = s.firstOnTileAt != null ? s.firstOnTileAt - calledAt : null;
			result.add(new DdPresenceDto(
				entry.getKey(),
				arrivedAfterMs,
				s.msOnTile,
				s.leftBeforeBreak,
				s.seenRunning,
				s.observedMs));
		}
		return result;
	}

	/** For TrackerPanel display - a single RSN's current sample, or null if unobserved this window. */
	public synchronized DdPresenceDto getSample(String rsn)
	{
		Sample s = samples.get(rsn);
		if (s == null)
		{
			return null;
		}
		Long arrivedAfterMs = s.firstOnTileAt != null ? s.firstOnTileAt - calledAt : null;
		return new DdPresenceDto(rsn, arrivedAfterMs, s.msOnTile, s.leftBeforeBreak, s.seenRunning, s.observedMs);
	}

	public synchronized Long getCalledAt()
	{
		return calledAt;
	}

	public synchronized Long getBrokenAt()
	{
		return brokenAt;
	}

	/** Called by Uploader once a closed window's final summary has been sent successfully. */
	public synchronized void acknowledgeClosed()
	{
		if (brokenAt != null)
		{
			calledAt = null;
			brokenAt = null;
			samples.clear();
		}
	}

	public synchronized void reset()
	{
		calledAt = null;
		brokenAt = null;
		samples.clear();
	}
}
