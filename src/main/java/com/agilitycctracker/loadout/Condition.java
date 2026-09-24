package com.agilitycctracker.loadout;

import java.util.List;
import lombok.Value;

@Value
public class Condition
{
	Slot slot;
	Check check;
	List<String> args;
}
