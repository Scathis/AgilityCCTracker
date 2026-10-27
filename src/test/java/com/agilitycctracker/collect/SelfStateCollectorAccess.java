package com.agilitycctracker.collect;

/** Test-only bridge to SelfStateCollector's package-private pure helpers. */
public final class SelfStateCollectorAccess
{
	private SelfStateCollectorAccess()
	{
	}

	public static boolean isAroundCourse(int region)
	{
		return SelfStateCollector.isAroundCourse(region);
	}
}
