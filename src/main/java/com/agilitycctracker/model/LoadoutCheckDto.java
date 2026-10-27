package com.agilitycctracker.model;

import java.util.List;
import lombok.Value;

@Value
public class LoadoutCheckDto
{
	String rsn;
	List<String> failed;
	long ts;
}
