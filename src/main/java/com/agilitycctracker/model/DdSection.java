package com.agilitycctracker.model;

import java.util.List;
import lombok.Value;

@Value
public class DdSection
{
	Long calledAt;
	Long brokenAt;
	List<DdPresenceDto> presence;
}
