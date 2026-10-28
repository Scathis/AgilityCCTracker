package com.agilitycctracker.model;

import lombok.Value;

@Value
public class ActiveEvent
{
	long id;
	String name;
	long startedAt;
	/** Epoch ms (server clock) of the latest mass bank call during this mass, or null. */
	Long massBankAt;
}
