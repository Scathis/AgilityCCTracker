package com.agilitycctracker.loadout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Exercises LoadoutEvaluator's three per-slot states (required-any-item,
 * required-from-allow-list, optional-but-restricted-if-worn) and the
 * RING/AMMO visibility restriction from §5.10. The plugin only ever gets
 * real Loadout data from the server's /status response - these fixtures are
 * test-only, not baked into the plugin itself.
 */
public class LoadoutEvaluatorTest
{
	private static final Set<Slot> ALL_SLOTS = EnumSet.allOf(Slot.class);
	private static final int SHORTBOW_ID = 841;
	private static final int LONGBOW_ID = 839;
	private static final int CROSSBOW_ID = 837;

	private final LoadoutEvaluator evaluator = new LoadoutEvaluator();

	private static Loadout loadoutOf(Requirement... requirements)
	{
		return new Loadout(1, "Test", LoadoutContext.RUNNING, Arrays.asList(requirements));
	}

	private static Map<Slot, WornItem> worn(Slot slot, int itemId)
	{
		Map<Slot, WornItem> worn = new EnumMap<>(Slot.class);
		worn.put(slot, new WornItem(itemId, "item"));
		return worn;
	}

	@Test
	public void requiredAnyItemFailsWhenSlotEmpty()
	{
		Loadout loadout = loadoutOf(new Requirement(Slot.HEAD, true, Collections.emptyList()));
		List<String> failed = evaluator.evaluate(loadout, Collections.emptyMap(), ALL_SLOTS);
		assertEquals(Collections.singletonList("HEAD"), failed);
	}

	@Test
	public void requiredAnyItemPassesWithAnyItemWorn()
	{
		Loadout loadout = loadoutOf(new Requirement(Slot.HEAD, true, Collections.emptyList()));
		List<String> failed = evaluator.evaluate(loadout, worn(Slot.HEAD, 12345), ALL_SLOTS);
		assertTrue(failed.isEmpty());
	}

	@Test
	public void requiredWithAllowListPassesOnAllowedItem()
	{
		Loadout loadout = loadoutOf(
			new Requirement(Slot.WEAPON, true, Arrays.asList(SHORTBOW_ID, LONGBOW_ID)));
		assertTrue(evaluator.evaluate(loadout, worn(Slot.WEAPON, SHORTBOW_ID), ALL_SLOTS).isEmpty());
	}

	@Test
	public void requiredWithAllowListFailsOnDisallowedItem()
	{
		Loadout loadout = loadoutOf(
			new Requirement(Slot.WEAPON, true, Arrays.asList(SHORTBOW_ID, LONGBOW_ID)));
		List<String> failed = evaluator.evaluate(loadout, worn(Slot.WEAPON, CROSSBOW_ID), ALL_SLOTS);
		assertEquals(Collections.singletonList("WEAPON"), failed);
	}

	@Test
	public void requiredWithAllowListFailsWhenEmpty()
	{
		Loadout loadout = loadoutOf(
			new Requirement(Slot.WEAPON, true, Collections.singletonList(SHORTBOW_ID)));
		List<String> failed = evaluator.evaluate(loadout, Collections.emptyMap(), ALL_SLOTS);
		assertEquals(Collections.singletonList("WEAPON"), failed);
	}

	@Test
	public void optionalWithAllowListPassesWhenEmpty()
	{
		// required=false: not wearing anything there is fine.
		Loadout loadout = loadoutOf(
			new Requirement(Slot.CAPE, false, Collections.singletonList(999)));
		assertTrue(evaluator.evaluate(loadout, Collections.emptyMap(), ALL_SLOTS).isEmpty());
	}

	@Test
	public void optionalWithAllowListStillFailsOnDisallowedItemIfWorn()
	{
		// required=false, but if they DO wear something there, it must match.
		Loadout loadout = loadoutOf(
			new Requirement(Slot.CAPE, false, Collections.singletonList(999)));
		List<String> failed = evaluator.evaluate(loadout, worn(Slot.CAPE, 1), ALL_SLOTS);
		assertEquals(Collections.singletonList("CAPE"), failed);
	}

	@Test
	public void unrestrictedSlotNeverFails()
	{
		// No Requirement for a slot at all = never checked, regardless of worn().
		Loadout loadout = loadoutOf(new Requirement(Slot.HEAD, true, Collections.emptyList()));
		assertTrue(evaluator.evaluate(loadout, worn(Slot.LEGS, 1), ALL_SLOTS).contains("HEAD"));
		assertTrue(evaluator.evaluate(loadout, worn(Slot.HEAD, 1), ALL_SLOTS).isEmpty());
	}

	@Test
	public void ringAndAmmoRequirementsSkippedWhenNotVisible()
	{
		Loadout loadout = loadoutOf(
			new Requirement(Slot.RING, true, Collections.emptyList()),
			new Requirement(Slot.AMMO, true, Collections.emptyList()));
		Set<Slot> visibleToObservers = EnumSet.complementOf(EnumSet.of(Slot.RING, Slot.AMMO));

		// Nothing is worn at all, including no ring/ammo - if these requirements
		// were evaluated they would fail, but restricted visibility must skip them.
		List<String> failed = evaluator.evaluate(loadout, Collections.emptyMap(), visibleToObservers);
		assertTrue("RING/AMMO requirements must be skipped, not failed, when not visible: " + failed, failed.isEmpty());
	}

	@Test
	public void multipleFailuresReportedInOrder()
	{
		Loadout loadout = loadoutOf(
			new Requirement(Slot.WEAPON, true, Collections.emptyList()),
			new Requirement(Slot.HEAD, true, Collections.emptyList()));
		List<String> failed = evaluator.evaluate(loadout, Collections.emptyMap(), ALL_SLOTS);
		assertEquals(Arrays.asList("WEAPON", "HEAD"), failed);
	}
}
