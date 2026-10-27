package com.agilitycctracker.loadout;

import java.util.List;
import lombok.Value;

/**
 * The FC's runner gear rules, from /status (set on the dashboard's Kits page).
 * Gaps only - nothing here names a specific item:
 * - every slot in {@code requiredSlots} must have something worn;
 * - if {@code rangedWeapon}, the weapon must be a bow or crossbow, and a
 *   crossbow (one-handed) also needs something in the shield slot. A bow is
 *   two-handed, so its empty shield slot is fine.
 */
@Value
public class GearRules
{
	List<Slot> requiredSlots;
	boolean rangedWeapon;
}
