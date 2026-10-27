package com.agilitycctracker.model;

import com.agilitycctracker.loadout.GearRules;
import java.util.List;
import lombok.Value;

/**
 * GET /api/v1/status. {@code activeEvent} is null when no event is running.
 */
@Value
public class StatusResponse
{
	String fcOwner;
	String fcName;
	long serverTime;
	boolean observerEnabled;
	/** Lowest FC rank allowed to see other players' gear gaps in-game (see FlagAccess). */
	int flagViewerMinRank;
	ActiveEvent activeEvent;
	/** Plugin users who answered the active mass's prompt; null from an older server. Anyone not listed is a non-plugin FC member, treated as a runner. */
	List<Participant> participants;
	/** Runner gear rules (required slots, bow/crossbow rule). */
	GearRules gearRules;
	/** Recommended kits for the local pre-course check; null from an older server. */
	List<Kit> kits;
	DdConfig dd;
}
