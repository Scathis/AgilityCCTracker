package com.agilitycctracker.model;

import java.util.List;
import lombok.Value;

@Value
public class RosterSnapshot
{
	long ts;
	List<RosterMember> members;
}
