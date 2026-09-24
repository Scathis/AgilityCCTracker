package com.agilitycctracker.model;

import com.agilitycctracker.loadout.Loadout;
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
	ActiveEvent activeEvent;
	List<Loadout> loadouts;
	DdConfig dd;
}
