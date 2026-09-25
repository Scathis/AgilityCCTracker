package com.agilitycctracker.loadout;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests the pure, package-visible normalize() method directly - no
 * ItemManager/client mocking needed. Cases from BUILD_PLAN.md §5.10.
 */
public class ItemNameResolverTest
{
	@Test
	public void stripsOrnamentKitTag()
	{
		assertEquals("magic shortbow", ItemNameResolver.normalize("Magic shortbow (i)"));
	}

	@Test
	public void leavesPlainNamesUnchanged()
	{
		assertEquals("rune crossbow", ItemNameResolver.normalize("Rune crossbow"));
		assertEquals("toxic blowpipe", ItemNameResolver.normalize("Toxic blowpipe"));
	}

	@Test
	public void stripsChargeTag()
	{
		assertEquals("dragon crossbow", ItemNameResolver.normalize("Dragon crossbow (cr)"));
	}

	@Test
	public void stripsCosmeticTag()
	{
		assertEquals("bow of faerdhinen", ItemNameResolver.normalize("Bow of faerdhinen (c)"));
	}

	@Test
	public void handlesNull()
	{
		assertEquals(null, ItemNameResolver.normalize(null));
	}
}
