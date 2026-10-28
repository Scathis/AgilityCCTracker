package com.agilitycctracker.goal;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar's Agility goal maths, mirroring the OSRS Hub Agility Tracker:
 * how many more Wilderness Agility laps (one ticket each) reach a goal level
 * if every ticket is handed in at the end, and what handing in the tickets
 * stored now is worth. Pure functions so it's unit-testable without a client.
 *
 * Ticket XP follows the OSRS Wiki: the rate depends on how many are handed in
 * at once and applies to the whole batch - 1-10 tickets 200 xp each, 11-50
 * 210, 51-100 220, 101+ 230.
 */
public final class AgilityGoal
{
	public static final double LAP_XP = 571.4;
	public static final int LAP_SECONDS = 45;
	public static final int MIN_LEVEL = 52;
	public static final int MAX_XP = 200_000_000;
	public static final String MAX_GOAL = "200m";
	public static final String DEFAULT_GOAL = "99";

	private static final int[] XP_TABLE = new int[100];

	static
	{
		int points = 0;
		for (int level = 1; level < 100; level++)
		{
			XP_TABLE[level] = points / 4;
			points += (int) Math.floor(level + 300 * Math.pow(2, level / 7.0));
		}
	}

	private AgilityGoal()
	{
	}

	/** Standard OSRS xp for a level (1-99). */
	public static int xpForLevel(int level)
	{
		return XP_TABLE[Math.max(1, Math.min(99, level))];
	}

	public static int levelForXp(long xp)
	{
		int level = 1;
		while (level < 99 && xp >= XP_TABLE[level + 1])
		{
			level++;
		}
		return level;
	}

	public static int ticketRate(int tickets)
	{
		return tickets <= 10 ? 200 : tickets <= 50 ? 210 : tickets <= 100 ? 220 : 230;
	}

	/** XP for handing in {@code tickets} in one go. */
	public static long ticketXp(int tickets)
	{
		return tickets <= 0 ? 0 : (long) tickets * ticketRate(tickets);
	}

	/** Goals offered: level 52 (the course's requirement) to 99, then 200m XP. */
	public static List<String> goals()
	{
		List<String> goals = new ArrayList<>();
		for (int level = MIN_LEVEL; level <= 99; level++)
		{
			goals.add(String.valueOf(level));
		}
		goals.add(MAX_GOAL);
		return goals;
	}

	/** A saved goal, or the default when it's missing or not one of {@link #goals()}. */
	public static String parseGoal(String saved)
	{
		return saved != null && goals().contains(saved) ? saved : DEFAULT_GOAL;
	}

	public static int targetXp(String goal)
	{
		return MAX_GOAL.equals(goal) ? MAX_XP : xpForLevel(Integer.parseInt(goal));
	}

	public static String goalLabel(String goal)
	{
		return MAX_GOAL.equals(goal) ? "200m XP" : "Level " + goal;
	}

	/**
	 * Fewest more laps (each earning one ticket) so that their XP plus handing
	 * in every ticket - stored and new - at the end reaches {@code targetXp}.
	 */
	public static int lapsToGoal(long xp, int tickets, long targetXp)
	{
		if (projected(xp, tickets, 0) >= targetXp)
		{
			return 0;
		}
		// Each lap is worth at least LAP_XP + 200, so this many always reaches it.
		int high = (int) Math.ceil((targetXp - xp) / (LAP_XP + 200)) + 1;
		int low = 0;
		while (low < high)
		{
			int mid = (low + high) >>> 1;
			if (projected(xp, tickets, mid) >= targetXp)
			{
				high = mid;
			}
			else
			{
				low = mid + 1;
			}
		}
		return low;
	}

	private static double projected(long xp, int tickets, int laps)
	{
		return xp + laps * LAP_XP + ticketXp(tickets + laps);
	}
}
