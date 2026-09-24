package com.agilitycctracker.loadout;

import java.util.List;
import lombok.Value;

@Value
public class Loadout
{
	long id;
	String name;
	LoadoutContext context;
	List<Requirement> requirements;

	/** Small shared helper - used by SelfLoadoutCheck, FcMemberScanner and SelfDdSampler alike. */
	public static Loadout find(List<Loadout> loadouts, LoadoutContext context)
	{
		if (loadouts == null)
		{
			return null;
		}
		for (Loadout loadout : loadouts)
		{
			if (loadout.getContext() == context)
			{
				return loadout;
			}
		}
		return null;
	}
}
