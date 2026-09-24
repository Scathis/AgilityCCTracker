package com.agilitycctracker.model;

import lombok.Value;

/**
 * GET /api/v1/status. {@code activeEvent} is null when no event is running.
 * Loadout/DD config fields are added in Phase 3 once LoadoutEvaluator/DdTracker
 * exist to consume them.
 */
@Value
public class StatusResponse
{
	String fcOwner;
	String fcName;
	long serverTime;
	boolean observerEnabled;
	ActiveEvent activeEvent;
}
