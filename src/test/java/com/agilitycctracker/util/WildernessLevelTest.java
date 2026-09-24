package com.agilitycctracker.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WildernessLevelTest
{
	@Test
	public void parsesWidgetText()
	{
		assertEquals(Integer.valueOf(1), WildernessLevel.fromWidgetText("Level: 1"));
		assertEquals(Integer.valueOf(52), WildernessLevel.fromWidgetText("Level: 52"));
	}

	@Test
	public void rejectsUnrelatedOrMalformedWidgetText()
	{
		assertNull(WildernessLevel.fromWidgetText(""));
		assertNull(WildernessLevel.fromWidgetText("Wilderness"));
		assertNull(WildernessLevel.fromWidgetText("Level: "));
		assertNull(WildernessLevel.fromWidgetText(null));
	}

	@Test
	public void coordinateFallbackMatchesKnownLevels()
	{
		// y=3520 is the wilderness ditch (level 1); every +8y is +1 level.
		assertEquals(Integer.valueOf(1), WildernessLevel.fromCoordinate(3520));
		assertEquals(Integer.valueOf(1), WildernessLevel.fromCoordinate(3527));
		assertEquals(Integer.valueOf(2), WildernessLevel.fromCoordinate(3528));
		assertEquals(Integer.valueOf(56), WildernessLevel.fromCoordinate(3960));
	}

	@Test
	public void coordinateFallbackIsNullOutsideWilderness()
	{
		assertNull(WildernessLevel.fromCoordinate(3519));
		assertNull(WildernessLevel.fromCoordinate(0));
	}
}
