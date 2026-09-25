package com.agilitycctracker.model;

import lombok.Value;

@Value
public class ActiveEvent
{
	long id;
	String name;
	long startedAt;
}
