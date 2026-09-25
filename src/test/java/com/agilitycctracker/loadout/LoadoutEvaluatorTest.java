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
 * Exercises LoadoutEvaluator against the default Running/DD loadouts from
 * BUILD_PLAN.md §7 (hardcoded here as test fixtures - the plugin itself only
 * ever gets Loadout data from the server's /status response, never bakes it
 * in), covering every scenario listed in §5.10.
 */
public class LoadoutEvaluatorTest
{
	private static final Set<Slot> ALL_SLOTS = EnumSet.allOf(Slot.class);

	private final LoadoutEvaluator evaluator = new LoadoutEvaluator();

	private static Loadout runningLoadout()
	{
		return new Loadout(1, "Running", LoadoutContext.RUNNING, Arrays.asList(
			new Requirement("run-weapon", "No weapon in hand", Slot.WEAPON, Check.FILLED, Collections.emptyList(), null),
			new Requirement("run-bow", "Weapon isn't a bow", Slot.WEAPON, Check.NAME_ENDS_WITH, Collections.singletonList("bow"), null),
			new Requirement("run-helm", "Missing helm", Slot.HEAD, Check.FILLED, Collections.emptyList(), null),
			new Requirement("run-legs", "Missing legs", Slot.LEGS, Check.FILLED, Collections.emptyList(), null),
			new Requirement("run-boots", "Missing boots", Slot.BOOTS, Check.FILLED, Collections.emptyList(), null),
			new Requirement("run-neck", "Missing necklace", Slot.AMULET, Check.FILLED, Collections.emptyList(), null),
			new Requirement("run-xbow-shield", "Crossbow without shield", Slot.SHIELD, Check.FILLED, Collections.emptyList(),
				new Condition(Slot.WEAPON, Check.NAME_ENDS_WITH, Collections.singletonList("crossbow")))
		));
	}

	private static Loadout ddLoadout()
	{
		return new Loadout(2, "DD", LoadoutContext.DD, Collections.singletonList(
			new Requirement("dd-bow", "Not holding a bow at DD", Slot.WEAPON, Check.NAME_ENDS_WITH, Collections.singletonList("bow"), null)
		));
	}

	/** A full, valid outfit with the given weapon/shield swapped in, everything else present. */
	private static Map<Slot, WornItem> fullOutfit(String weaponName, boolean hasShield)
	{
		Map<Slot, WornItem> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.HEAD, new WornItem(1, "helm"));
		worn.put(Slot.AMULET, new WornItem(2, "necklace"));
		worn.put(Slot.WEAPON, new WornItem(3, weaponName));
		worn.put(Slot.LEGS, new WornItem(4, "legs"));
		worn.put(Slot.BOOTS, new WornItem(5, "boots"));
		if (hasShield)
		{
			worn.put(Slot.SHIELD, new WornItem(6, "shield"));
		}
		return worn;
	}

	@Test
	public void shortbowNoShieldPasses()
	{
		List<String> failed = evaluator.evaluate(runningLoadout(), fullOutfit("magic shortbow", false), ALL_SLOTS);
		assertTrue("expected no failures, got " + failed, failed.isEmpty());
	}

	@Test
	public void runeCrossbowNoShieldFailsXbowShieldOnly()
	{
		List<String> failed = evaluator.evaluate(runningLoadout(), fullOutfit("rune crossbow", false), ALL_SLOTS);
		assertEquals(Collections.singletonList("run-xbow-shield"), failed);
	}

	@Test
	public void crossbowWithShieldPasses()
	{
		List<String> failed = evaluator.evaluate(runningLoadout(), fullOutfit("rune crossbow", true), ALL_SLOTS);
		assertTrue("expected no failures, got " + failed, failed.isEmpty());
	}

	@Test
	public void blowpipeFailsRunBowOnly()
	{
		List<String> failed = evaluator.evaluate(runningLoadout(), fullOutfit("toxic blowpipe", false), ALL_SLOTS);
		assertEquals(Collections.singletonList("run-bow"), failed);
	}

	@Test
	public void emptyHandFailsWeaponAndBow()
	{
		Map<Slot, WornItem> worn = fullOutfit("magic shortbow", false);
		worn.remove(Slot.WEAPON);
		List<String> failed = evaluator.evaluate(runningLoadout(), worn, ALL_SLOTS);
		assertEquals(Arrays.asList("run-weapon", "run-bow"), failed);
	}

	@Test
	public void missingHelmFails()
	{
		Map<Slot, WornItem> worn = fullOutfit("magic shortbow", false);
		worn.remove(Slot.HEAD);
		assertEquals(Collections.singletonList("run-helm"), evaluator.evaluate(runningLoadout(), worn, ALL_SLOTS));
	}

	@Test
	public void missingLegsFails()
	{
		Map<Slot, WornItem> worn = fullOutfit("magic shortbow", false);
		worn.remove(Slot.LEGS);
		assertEquals(Collections.singletonList("run-legs"), evaluator.evaluate(runningLoadout(), worn, ALL_SLOTS));
	}

	@Test
	public void missingBootsFails()
	{
		Map<Slot, WornItem> worn = fullOutfit("magic shortbow", false);
		worn.remove(Slot.BOOTS);
		assertEquals(Collections.singletonList("run-boots"), evaluator.evaluate(runningLoadout(), worn, ALL_SLOTS));
	}

	@Test
	public void missingNecklaceFails()
	{
		Map<Slot, WornItem> worn = fullOutfit("magic shortbow", false);
		worn.remove(Slot.AMULET);
		assertEquals(Collections.singletonList("run-neck"), evaluator.evaluate(runningLoadout(), worn, ALL_SLOTS));
	}

	@Test
	public void ringAndAmmoRequirementsSkippedWhenNotVisible()
	{
		Loadout loadoutWithRingReq = new Loadout(3, "Test", LoadoutContext.RUNNING, Arrays.asList(
			new Requirement("has-ring", "Missing ring", Slot.RING, Check.FILLED, Collections.emptyList(), null),
			new Requirement("has-ammo", "Missing ammo", Slot.AMMO, Check.FILLED, Collections.emptyList(), null)
		));
		Set<Slot> visibleToObservers = EnumSet.complementOf(EnumSet.of(Slot.RING, Slot.AMMO));

		// Nothing is worn at all, including no ring/ammo - if these requirements
		// were evaluated they would fail, but restricted visibility must skip them.
		List<String> failed = evaluator.evaluate(loadoutWithRingReq, Collections.emptyMap(), visibleToObservers);
		assertTrue("RING/AMMO requirements must be skipped, not failed, when not visible: " + failed, failed.isEmpty());
	}

	@Test
	public void ddLoadoutFailsOnNonBowWeapon()
	{
		assertEquals(Collections.singletonList("dd-bow"),
			evaluator.evaluate(ddLoadout(), fullOutfit("dragon claws", false), ALL_SLOTS));
	}

	@Test
	public void ddLoadoutPassesOnBow()
	{
		assertTrue(evaluator.evaluate(ddLoadout(), fullOutfit("magic shortbow", false), ALL_SLOTS).isEmpty());
	}
}
