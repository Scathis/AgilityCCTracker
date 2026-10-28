package com.agilitycctracker.mass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agilitycctracker.model.ActiveEvent;
import org.junit.Test;

public class MassNoticesTest
{
	private static ActiveEvent event(long id)
	{
		return new ActiveEvent(id, "Mass", 0, null);
	}

	@Test
	public void massEndsWhenItStopsOrIsReplaced()
	{
		assertTrue(MassNotices.massJustEnded(5, null));
		assertTrue(MassNotices.massJustEnded(5, event(6)));
		assertFalse(MassNotices.massJustEnded(5, event(5)));
		assertFalse("no mass was being watched", MassNotices.massJustEnded(-1, null));
	}

	@Test
	public void bankWindowIsAMinuteOnTheServerClock()
	{
		long called = 1_000_000;
		assertEquals(0, MassNotices.bankWindowLeft(null, called));
		assertEquals(60_000, MassNotices.bankWindowLeft(called, called));
		assertEquals(15_000, MassNotices.bankWindowLeft(called, called + 45_000));
		assertTrue(MassNotices.bankWindowLeft(called, called + 61_000) <= 0);
		// A status from just before the call (server clocks can tick unevenly) still gets the full minute.
		assertEquals(60_000, MassNotices.bankWindowLeft(called, called - 500));
	}
}
