package com.agilitycctracker.history;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One mass the player attended, as remembered locally (see {@link SessionHistory}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MassSession
{
	private long eventId;
	private String name;
	/** When the player first counted as attending, epoch ms. */
	private long startedAt;
	private int laps;
	private int tickets;
	/** Time spent inside the Wilderness Agility Course area while attending. */
	private long courseMs;
	private boolean runner;
	private boolean anti;

	/** "Runner", "Anti" or "Runner + Anti" for someone who swapped mid-mass. */
	public String roles()
	{
		if (runner && anti)
		{
			return "Runner + Anti";
		}
		return anti ? "Anti" : "Runner";
	}
}
