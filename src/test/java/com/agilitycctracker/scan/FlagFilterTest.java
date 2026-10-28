package com.agilitycctracker.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agilitycctracker.loadout.GearRules;
import com.agilitycctracker.loadout.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FlagFilterTest
{
	private static final GearRules RULES = new GearRules(
		Arrays.asList(Slot.BOOTS, Slot.HEAD, Slot.AMULET, Slot.RING), true);

	@Test
	public void optionsFollowTheRulesInSlotOrderAndSkipInvisibleSlots()
	{
		// RING can't be seen on other players, so it's never a filter option.
		assertEquals(Arrays.asList("HEAD", "AMULET", "WEAPON", "SHIELD", "BOOTS"), FlagFilter.options(RULES));
		assertEquals(Arrays.asList("HEAD"), FlagFilter.options(new GearRules(Arrays.asList(Slot.HEAD), false)));
		assertTrue(FlagFilter.options(null).isEmpty());
	}

	@Test
	public void savedPicksRoundTripAndDropRemovedRules()
	{
		Set<String> picked = FlagFilter.parse(" amulet, WEAPON,,");
		assertEquals("AMULET,WEAPON", FlagFilter.format(picked));
		List<String> noBowRule = Arrays.asList("HEAD", "AMULET");
		assertEquals(Collections.singleton("AMULET"), FlagFilter.effective(picked, noBowRule));
		assertTrue(FlagFilter.effective(FlagFilter.parse("WEAPON"), noBowRule).isEmpty());
	}

	@Test
	public void nothingPickedShowsEveryoneMissingSomething()
	{
		Set<String> none = Collections.emptySet();
		assertTrue(FlagFilter.matches(Arrays.asList("BOOTS"), none));
		assertFalse(FlagFilter.matches(Collections.emptyList(), none));
	}

	@Test
	public void pickedTypesShowRunnersMissingAnyOfThem()
	{
		Set<String> amuletOrBow = FlagFilter.parse("AMULET,WEAPON");
		assertTrue(FlagFilter.matches(Arrays.asList("AMULET"), amuletOrBow));
		assertTrue(FlagFilter.matches(Arrays.asList("HEAD", "WEAPON"), amuletOrBow));
		assertFalse(FlagFilter.matches(Arrays.asList("HEAD", "BOOTS"), amuletOrBow));
	}

	@Test
	public void chipLabels()
	{
		assertEquals("Bow", FlagFilter.label("WEAPON"));
		assertEquals("Helm", FlagFilter.label("HEAD"));
		assertEquals("Amulet", FlagFilter.label("AMULET"));
	}
}
