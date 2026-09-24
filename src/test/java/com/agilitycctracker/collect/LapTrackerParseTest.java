package com.agilitycctracker.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Tests LapTracker.parseLapCount directly. In the same package so it can see
 * the package-private static method without mocking RuneLite's ChatMessage.
 */
public class LapTrackerParseTest
{
	@Test
	public void parsesPlainMessage()
	{
		assertEquals(Integer.valueOf(1), LapTracker.parseLapCount("Your Wilderness Agility lap count is: 1."));
	}

	@Test
	public void parsesColouredMessage()
	{
		assertEquals(Integer.valueOf(42),
			LapTracker.parseLapCount("Your Wilderness Agility lap count is: <col=ff9040>42</col>."));
	}

	@Test
	public void parsesCommaSeparatedThousands()
	{
		assertEquals(Integer.valueOf(1234),
			LapTracker.parseLapCount("Your Wilderness Agility lap count is: <col=ff9040>1,234</col>."));
	}

	@Test
	public void toleratesMissingTrailingPeriod()
	{
		assertEquals(Integer.valueOf(5), LapTracker.parseLapCount("Your Wilderness Agility lap count is: 5"));
	}

	@Test
	public void ignoresUnrelatedMessages()
	{
		assertNull(LapTracker.parseLapCount("You have completed 5 laps of the Barbarian Outpost course."));
		assertNull(LapTracker.parseLapCount(null));
	}
}
