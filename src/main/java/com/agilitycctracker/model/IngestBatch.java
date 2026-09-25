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
	String accountHash;
	SelfState self;
	List<LapEvent> laps;
	List<DeathEvent> deaths;
	RosterSnapshot roster;
	List<LoadoutCheckDto> loadoutChecks;
	DdSection dd;
}
