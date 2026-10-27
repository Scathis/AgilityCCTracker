package com.agilitycctracker.dd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agilitycctracker.model.DdPresenceDto;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Scripted timelines against DdTracker's real clock, using its explicit
 * `now` parameter throughout instead of System.currentTimeMillis() - no
 * RuneLite client needed. Scenarios per BUILD_PLAN.md §5.10.
 */
public class DdTrackerTest
{
	private static final long ARRIVE_GRACE_MS = 15_000;
	private static final long LEAVE_GRACE_MS = 5_000;
	private static final long TICK_MS = DdTracker.TICK_MS;

	private final DdTracker tracker = new DdTracker();

	private DdPresenceDto find(List<DdPresenceDto> summaries, String rsn)
	{
		return summaries.stream().filter(s -> s.getRsn().equals(rsn)).findFirst().orElse(null);
	}

	@Test
	public void onTimeArrivalRecordsZeroDelay()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.onObservation("Alice", true, true, TICK_MS);

		DdPresenceDto sample = find(tracker.summaries(), "Alice");
		assertEquals(Long.valueOf(TICK_MS), sample.getArrivedAfterMs());
		assertEquals(TICK_MS, sample.getMsOnTile());
		assertFalse(sample.isLeftBeforeBreak());
		assertFalse(sample.isSeenRunning());
	}

	@Test
	public void lateArrivalRecordsRealDelay()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		long lateArrival = 20_000; // after the 15s arrive grace
		tracker.onObservation("Bob", true, true, lateArrival);

		DdPresenceDto sample = find(tracker.summaries(), "Bob");
		assertEquals(Long.valueOf(lateArrival), sample.getArrivedAfterMs());
	}

	@Test
	public void leftBeforeBreakFlagsAfterExceedingLeaveGrace()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.onObservation("Carl", true, true, 1_000);
		// Off-tile continuously from 1_000 onward.
		tracker.onObservation("Carl", false, true, 2_000);
		assertFalse("not yet past the 5s leave grace", find(tracker.summaries(), "Carl").isLeftBeforeBreak());

		tracker.onObservation("Carl", false, true, 7_001); // 6001ms off, > 5000ms grace
		assertTrue("should have left before break by now", find(tracker.summaries(), "Carl").isLeftBeforeBreak());
	}

	@Test
	public void seenRunningFlagsWhenNeverOnTileAfterGrace()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.onObservation("Dan", false, true, 5_000);
		assertFalse("still inside arrive grace", find(tracker.summaries(), "Dan").isSeenRunning());

		tracker.onObservation("Dan", false, true, 16_000); // past 15s arrive grace
		assertTrue("kept running past the grace period without ever showing on a tile",
			find(tracker.summaries(), "Dan").isSeenRunning());
	}

	@Test
	public void windowAutoClosesAfterMaxMinutes()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.autoCloseIfExpired(5 * 60_000L, 10);
		assertTrue("10 minutes hasn't elapsed yet", tracker.isWindowOpen());

		tracker.autoCloseIfExpired(10 * 60_000L, 10);
		assertFalse("window should auto-close at exactly max_minutes", tracker.isWindowOpen());
		assertEquals(Long.valueOf(10 * 60_000L), tracker.getBrokenAt());
	}

	@Test
	public void openWindowIsIgnoredWhileOneIsAlreadyOpen()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.onObservation("Frank", true, true, 1_000);
		tracker.openWindow(50_000, ARRIVE_GRACE_MS, LEAVE_GRACE_MS); // should be a no-op

		assertEquals(Long.valueOf(0), tracker.getCalledAt());
		assertTrue("samples from the still-open window must survive", find(tracker.summaries(), "Frank") != null);
	}

	@Test
	public void observationsAreIgnoredWhenNoWindowIsOpen()
	{
		tracker.onObservation("Grace", true, true, 1_000);
		assertTrue(tracker.summaries().isEmpty());
	}

	@Test
	public void acknowledgeClosedResetsForANewCall()
	{
		tracker.openWindow(0, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		tracker.onObservation("Hank", true, true, 1_000);
		tracker.closeWindow(2_000);
		tracker.acknowledgeClosed();

		assertFalse(tracker.isWindowOpen());
		assertTrue(tracker.summaries().isEmpty());

		tracker.openWindow(10_000, ARRIVE_GRACE_MS, LEAVE_GRACE_MS);
		assertEquals(Long.valueOf(10_000), tracker.getCalledAt());
	}
}
