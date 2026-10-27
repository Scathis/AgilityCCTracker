package com.agilitycctracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Experience;
import org.junit.Test;

public class AgilityRingTest
{
	@Test
	public void fillsByLevelUpTo99()
	{
		assertEquals(0, AgilityRing.levelFraction(0), 1e-9);
		assertEquals(0.5, AgilityRing.levelFraction(Experience.getXpForLevel(50)), 1e-9);
		assertEquals(1, AgilityRing.levelFraction(AgilityRing.XP_99), 1e-9);
		// Halfway through a level sits between that level and the next.
		int mid = (Experience.getXpForLevel(70) + Experience.getXpForLevel(71)) / 2;
		double f = AgilityRing.levelFraction(mid);
		assertTrue(f > 69.0 / 98 && f < 70.0 / 98);
	}

	@Test
	public void overchargesFrom99To200m()
	{
		assertEquals(0, AgilityRing.overcharge(AgilityRing.XP_99 - 1), 1e-9);
		assertEquals(0, AgilityRing.overcharge(AgilityRing.XP_99), 1e-9);
		int half = AgilityRing.XP_99 + (Experience.MAX_SKILL_XP - AgilityRing.XP_99) / 2;
		assertEquals(0.5, AgilityRing.overcharge(half), 1e-6);
		assertEquals(1, AgilityRing.overcharge(Experience.MAX_SKILL_XP), 1e-9);
	}

	@Test
	public void shortXp()
	{
		assertEquals("950", TrackerPanel.shortXp(950));
		assertEquals("43k", TrackerPanel.shortXp(43_210));
		assertEquals("13.0M", TrackerPanel.shortXp(13_034_431));
	}
}
