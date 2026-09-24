package com.agilitycctracker.loadout;

import java.util.List;
import lombok.Value;

@Value
public class Requirement
{
	String id;
	String label;
	Slot slot;
	Check check;
	List<String> args;
	Condition when;
}
