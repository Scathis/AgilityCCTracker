package com.agilitycctracker.model;

import com.agilitycctracker.MassRole;
import lombok.Value;

/** POST /api/v1/event/respond body. */
@Value
public class RespondRequest
{
	String rsn;
	long eventId;
	boolean attending;
	MassRole role;
}
