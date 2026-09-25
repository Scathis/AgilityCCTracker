package com.agilitycctracker.model;

import lombok.Value;

@Value
public class LapEvent
{
	int lapCount;
	long completedAt;
	Long lapTimeMs;
	Integer ticketsGained;
}
