package com.agilitycctracker.goal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgilityGoalTest
{
	@Test
	public void xpTableMatchesTheGame()
	{
		assertEquals(0, AgilityGoal.xpForLevel(1));
		assertEquals(83, AgilityGoal.xpForLevel(2));
		assertEquals(123_660, AgilityGoal.xpForLevel(52));
		assertEquals(13_034_431, AgilityGoal.xpForLevel(99));
		assertEquals(98, AgilityGoal.levelForXp(13_034_430));
		assertEquals(99, AgilityGoal.levelForXp(200_000_000));
		assertEquals(52, AgilityGoal.levelForXp(123_660));
	}

	@Test
	public void ticketRateAppliesToTheWholeBatch()
	{
		// OSRS Wiki: 1-10 at 200, 11-50 at 210, 51-100 at 220, 101+ at 230 - each.
		assertEquals(0, AgilityGoal.ticketXp(0));
		assertEquals(2_000, AgilityGoal.ticketXp(10));
		assertEquals(2_310, AgilityGoal.ticketXp(11));
		assertEquals(10_500, AgilityGoal.ticketXp(50));
		assertEquals(11_220, AgilityGoal.ticketXp(51));
		assertEquals(22_000, AgilityGoal.ticketXp(100));
		assertEquals(23_230, AgilityGoal.ticketXp(101));
	}

	@Test
	public void lapsToGoalIsTheFewestThatReachIt()
	{
		int[][] cases = {{123_660, 0}, {5_000_000, 0}, {5_000_000, 400}, {12_900_000, 150}, {0, 0}};
		for (int[] c : cases)
		{
			long target = AgilityGoal.xpForLevel(99);
			int laps = AgilityGoal.lapsToGoal(c[0], c[1], target);
			assertTrue(projected(c[0], c[1], laps) >= target);
			assertTrue(laps == 0 || projected(c[0], c[1], laps - 1) < target);
		}
	}

	@Test
	public void storedTicketsAloneCanReachIt()
	{
		long xp = AgilityGoal.xpForLevel(99) - 23_230;
		assertEquals(0, AgilityGoal.lapsToGoal(xp, 101, AgilityGoal.xpForLevel(99)));
		assertEquals(0, AgilityGoal.lapsToGoal(AgilityGoal.MAX_XP, 0, AgilityGoal.MAX_XP));
	}

	@Test
	public void goalsRunFrom52To99Then200m()
	{
		assertEquals("52", AgilityGoal.goals().get(0));
		assertEquals("200m", AgilityGoal.goals().get(AgilityGoal.goals().size() - 1));
		assertEquals("99", AgilityGoal.parseGoal(null));
		assertEquals("99", AgilityGoal.parseGoal("51"));
		assertEquals("70", AgilityGoal.parseGoal("70"));
		assertEquals(200_000_000, AgilityGoal.targetXp("200m"));
		assertEquals("Level 70", AgilityGoal.goalLabel("70"));
	}

	private static double projected(long xp, int tickets, int laps)
	{
		return xp + laps * AgilityGoal.LAP_XP + AgilityGoal.ticketXp(tickets + laps);
	}
}
