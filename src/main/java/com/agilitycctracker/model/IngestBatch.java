package com.agilitycctracker.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * One POST /api/v1/ingest body. Every section besides {@code rsn} is
 * optional and omitted (left null) when empty/unchanged by {@code Uploader}.
 */
@Value
@Builder
public class IngestBatch
{
	String rsn;
	SelfState self;
	List<LapEvent> laps;
	List<DeathEvent> deaths;
	List<LoadoutCheckDto> loadoutChecks;
	DdSection dd;
	/** Sent every upload cycle regardless of change, unlike every other
	 * field here - a live heartbeat of who is at the mass right now (it's
	 * what attendance is built from), not a durable event. */
	List<CourseSighting> courseSightings;
}
