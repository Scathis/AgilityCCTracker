package com.agilitycctracker.loadout;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class GearRuleEvaluatorTest
{
	private static final Set<Slot> ALL = EnumSet.allOf(Slot.class);
	private static final GearRules RULES = new GearRules(
		Arrays.asList(Slot.HEAD, Slot.AMULET, Slot.TORSO, Slot.LEGS, Slot.BOOTS), true);

	private final GearRuleEvaluator evaluator = new GearRuleEvaluator();

	private static Map<Slot, WornItem> fullKit(String weapon)
	{
		Map<Slot, WornItem> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.HEAD, new WornItem(1, "coif"));
		worn.put(Slot.AMULET, new WornItem(2, "amulet of power"));
		worn.put(Slot.TORSO, new WornItem(3, "leather body"));
		worn.put(Slot.LEGS, new WornItem(4, "leather chaps"));
		worn.put(Slot.BOOTS, new WornItem(5, "leather boots"));
		if (weapon != null)
		{
			worn.put(Slot.WEAPON, new WornItem(6, weapon));
		}
		return worn;
	}

	@Test
	public void fullKitWithABowPassesWithAnEmptyShield()
	{
		assertEquals(Collections.emptyList(), evaluator.evaluate(RULES, fullKit("magic shortbow"), ALL));
	}

	@Test
	public void eachEmptyRequiredSlotIsFlagged()
	{
		Map<Slot, WornItem> worn = fullKit("maple shortbow");
		worn.remove(Slot.HEAD);
		worn.remove(Slot.BOOTS);
		assertEquals(Arrays.asList("HEAD", "BOOTS"), evaluator.evaluate(RULES, worn, ALL));
	}

	@Test
	public void noWeaponOrANonBowWeaponIsFlagged()
	{
		assertEquals(Collections.singletonList("WEAPON"), evaluator.evaluate(RULES, fullKit(null), ALL));
		assertEquals(Collections.singletonList("WEAPON"), evaluator.evaluate(RULES, fullKit("rune scimitar"), ALL));
		assertEquals(Collections.singletonList("WEAPON"), evaluator.evaluate(RULES, fullKit("toxic blowpipe"), ALL));
	}

	@Test
	public void crossbowNeedsAShield()
	{
		assertEquals(Collections.singletonList("SHIELD"), evaluator.evaluate(RULES, fullKit("rune crossbow"), ALL));
		Map<Slot, WornItem> worn = fullKit("rune crossbow");
		worn.put(Slot.SHIELD, new WornItem(7, "wooden shield"));
		assertEquals(Collections.emptyList(), evaluator.evaluate(RULES, worn, ALL));
	}

	@Test
	public void namedBowsCount()
	{
		for (String bow : Arrays.asList("dark bow", "twisted bow", "craw's bow", "magic longbow", "armadyl crossbow"))
		{
			List<String> failed = evaluator.evaluate(RULES, fullKit(bow), ALL);
			assertEquals(bow, bow.endsWith("crossbow") ? Collections.singletonList("SHIELD") : Collections.emptyList(), failed);
		}
	}

	@Test
	public void slotsTheViewerCannotSeeAreNeverReported()
	{
		Map<Slot, WornItem> worn = fullKit("rune crossbow");
		Set<Slot> noShield = EnumSet.allOf(Slot.class);
		noShield.remove(Slot.SHIELD);
		assertEquals(Collections.emptyList(), evaluator.evaluate(RULES, worn, noShield));
	}

	@Test
	public void unknownWeaponNameIsNotFlagged()
	{
		assertEquals(Collections.emptyList(), evaluator.evaluate(RULES, withUnnamedWeapon(), ALL));
	}

	private static Map<Slot, WornItem> withUnnamedWeapon()
	{
		Map<Slot, WornItem> worn = fullKit(null);
		worn.put(Slot.WEAPON, new WornItem(6, null));
		return worn;
	}

	@Test
	public void rangedRuleCanBeTurnedOff()
	{
		GearRules slotsOnly = new GearRules(Collections.singletonList(Slot.HEAD), false);
		assertEquals(Collections.emptyList(), evaluator.evaluate(slotsOnly, fullKit("rune scimitar"), ALL));
	}
}
