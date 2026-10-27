package com.agilitycctracker.model;

import com.agilitycctracker.MassRole;
import lombok.Value;

/** One plugin user's answer to the active mass's "attending?" prompt, as listed by /status. */
@Value
public class Participant
{
	String rsn;
	/** "ATTENDING" or "DECLINED". */
	String status;
	MassRole role;

	public boolean isAttending()
	{
		return "ATTENDING".equals(status);
	}
}
