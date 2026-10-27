package com.agilitycctracker.api;

import com.agilitycctracker.model.StatusResponse;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Singleton;

/**
 * Latest known /status response, refreshed by Uploader's 60s poll and read
 * by every component that needs to know the active event / FC owner / server
 * feature switches (RosterTracker now; GearRuleEvaluator/DdCallDetector in
 * Phase 3).
 */
@Singleton
public class StatusHolder
{
	private final AtomicReference<StatusResponse> current = new AtomicReference<>();

	public StatusResponse get()
	{
		return current.get();
	}

	public void set(StatusResponse status)
	{
		current.set(status);
	}
}
