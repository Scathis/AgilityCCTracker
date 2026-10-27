package com.agilitycctracker.model;

import lombok.Value;

@Value
public class DdPresenceDto
{
	String rsn;
	Long arrivedAfterMs;
	long msOnTile;
	boolean leftBeforeBreak;
	boolean seenRunning;
	long observedMs;
}
