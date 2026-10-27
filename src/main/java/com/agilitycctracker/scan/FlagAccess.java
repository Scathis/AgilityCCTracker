package com.agilitycctracker.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Who gets to see other players' gear gaps in-game, and how a gap list reads.
 * Pure functions so the rules are unit-testable without a client.
 *
 * The information itself (what a player is wearing) is public in-game; this
 * only decides who is handed the ready-made list and over-head flags. The
 * FC owner controls FC ranks, so requiring a minimum rank keeps the
 * convenience away from a stranger who merely installed the plugin.
 */
public final class FlagAccess
{
	private static final int MAX_LABELS = 3;

	private FlagAccess()
	{
	}

	/** The viewer must be in the FC this server tracks, at or above the group's configured rank. */
	public static boolean canViewFlags(boolean inConfiguredFc, int myRank, int minRank)
	{
		return inConfiguredFc && myRank >= minRank;
	}

	/** Whether the friends chat the viewer is in is the one the server tracks (names compared as Jagex names). */
	public static boolean isConfiguredFc(String viewerFcOwner, String serverFcOwner)
	{
		return viewerFcOwner != null && serverFcOwner != null
			&& normalise(viewerFcOwner).equals(normalise(serverFcOwner));
	}

	/** "WEAPON, BOOTS" -> "No bow, boots"; capped so a naked player doesn't produce a wall of text. */
	public static String describe(List<String> failedKeys)
	{
		List<String> parts = new ArrayList<>();
		for (String key : failedKeys)
		{
			parts.add(friendly(key));
		}
		if (parts.isEmpty())
		{
			return "";
		}
		String joined = String.join(", ", parts.subList(0, Math.min(parts.size(), MAX_LABELS)));
		int extra = parts.size() - MAX_LABELS;
		return "No " + joined + (extra > 0 ? " +" + extra : "");
	}

	private static String friendly(String key)
	{
		switch (key.toUpperCase(Locale.ROOT))
		{
			case "HEAD":
				return "helm";
			case "BOOTS":
				return "boots";
			case "HANDS":
				return "gloves";
			case "TORSO":
				return "body";
			case "LEGS":
				return "legs";
			case "AMULET":
				return "amulet";
			case "WEAPON":
				return "bow";
			case "SHIELD":
				return "shield";
			default:
				return key.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
		}
	}

	private static String normalise(String name)
	{
		return name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
