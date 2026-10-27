package com.agilitycctracker.history;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionHistoryTest
{
	@Test
	public void countsOnlyTheIncrease()
	{
		assertEquals(0, SessionHistory.increase(4, 4));
		assertEquals(1, SessionHistory.increase(4, 5));
	}

	@Test
	public void totalThatResetCountsFromZero()
	{
		// LapTracker resets on world hop: 7 -> 0 adds nothing, 7 -> 1 adds the one new lap.
		assertEquals(0, SessionHistory.increase(7, 0));
		assertEquals(1, SessionHistory.increase(7, 1));
	}

	@Test
	public void rolesLabel()
	{
		MassSession session = new MassSession();
		session.setRunner(true);
		assertEquals("Runner", session.roles());
		session.setAnti(true);
		assertEquals("Runner + Anti", session.roles());
	}

	@Test
	public void aroundCourseIsTheCourseRegionAndItsNeighbours()
	{
		int course = 11837;
		assertEquals(true, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(course));
		assertEquals(true, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(course + 1));
		assertEquals(true, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(course - 256));
		assertEquals(false, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(course + 2));
		assertEquals(false, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(course + 512));
		assertEquals(false, com.agilitycctracker.collect.SelfStateCollectorAccess.isAroundCourse(-1));
	}
}
