package com.agilitycctracker.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class FlagAccessTest
{
	@Test
	public void rankAtOrAboveThresholdMaySee()
	{
		assertTrue(FlagAccess.canViewFlags(true, 0, 0));
		assertTrue(FlagAccess.canViewFlags(true, 3, 1));
	}

	@Test
	public void rankBelowThresholdMayNotSee()
	{
		// -1 is "unranked": a guest in the chat.
		assertFalse(FlagAccess.canViewFlags(true, -1, 0));
		assertFalse(FlagAccess.canViewFlags(true, 1, 2));
	}

	@Test
	public void thresholdOfMinusOneLetsAnyoneInTheChatSee()
	{
		assertTrue(FlagAccess.canViewFlags(true, -1, -1));
	}

	@Test
	public void nobodyOutsideTheTrackedChatSees()
	{
		assertFalse(FlagAccess.canViewFlags(false, 7, -1));
	}

	@Test
	public void configuredChatMatchIgnoresCaseAndNonBreakingSpaces()
	{
		assertTrue(FlagAccess.isConfiguredFc("Agility Cc", "agility cc"));
		assertFalse(FlagAccess.isConfiguredFc("Someone Else", "Agility Cc"));
		assertFalse(FlagAccess.isConfiguredFc(null, "Agility Cc"));
		assertFalse(FlagAccess.isConfiguredFc("Agility Cc", null));
	}

	@Test
	public void describeUsesFriendlySlotNamesAndCapsTheList()
	{
		assertEquals("No helm", FlagAccess.describe(Collections.singletonList("HEAD")));
		assertEquals("No helm, boots", FlagAccess.describe(Arrays.asList("HEAD", "FEET")));
		assertEquals("No helm, boots, gloves +2",
			FlagAccess.describe(Arrays.asList("HEAD", "FEET", "HANDS", "WEAPON", "SHIELD")));
		assertEquals("", FlagAccess.describe(Collections.emptyList()));
	}
}
