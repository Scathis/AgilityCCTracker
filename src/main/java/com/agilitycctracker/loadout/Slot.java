package com.agilitycctracker.loadout;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.kit.KitType;

/**
 * The 11 equipment slots this project's loadout requirements can target.
 * Deliberately narrower than RuneLite's own equipment enums (no ARMS/HAIR/JAW
 * - nothing here ever needs to check those) and named to match the server's
 * JSON contract (BUILD_PLAN.md §5.6/§5.7) exactly, e.g. "TORSO" not "BODY".
 */
public enum Slot
{
	HEAD, CAPE, AMULET, WEAPON, TORSO, SHIELD, LEGS, HANDS, BOOTS, RING, AMMO;

	/** Index into the local player's own 14-slot WORN item container. */
	public int toEquipmentSlotIdx()
	{
		switch (this)
		{
			case HEAD:
				return EquipmentInventorySlot.HEAD.getSlotIdx();
			case CAPE:
				return EquipmentInventorySlot.CAPE.getSlotIdx();
			case AMULET:
				return EquipmentInventorySlot.AMULET.getSlotIdx();
			case WEAPON:
				return EquipmentInventorySlot.WEAPON.getSlotIdx();
			case TORSO:
				return EquipmentInventorySlot.BODY.getSlotIdx();
			case SHIELD:
				return EquipmentInventorySlot.SHIELD.getSlotIdx();
			case LEGS:
				return EquipmentInventorySlot.LEGS.getSlotIdx();
			case HANDS:
				return EquipmentInventorySlot.GLOVES.getSlotIdx();
			case BOOTS:
				return EquipmentInventorySlot.BOOTS.getSlotIdx();
			case RING:
				return EquipmentInventorySlot.RING.getSlotIdx();
			case AMMO:
				return EquipmentInventorySlot.AMMO.getSlotIdx();
			default:
				throw new IllegalStateException(name());
		}
	}

	/**
	 * Ordinal into {@code PlayerComposition.getEquipmentIds()} for OTHER
	 * players. Null for RING/AMMO - RuneLite's KitType has no entry for
	 * either, meaning neither is ever visible on anyone but yourself.
	 */
	public KitType toKitType()
	{
		switch (this)
		{
			case HEAD:
				return KitType.HEAD;
			case CAPE:
				return KitType.CAPE;
			case AMULET:
				return KitType.AMULET;
			case WEAPON:
				return KitType.WEAPON;
			case TORSO:
				return KitType.TORSO;
			case SHIELD:
				return KitType.SHIELD;
			case LEGS:
				return KitType.LEGS;
			case HANDS:
				return KitType.HANDS;
			case BOOTS:
				return KitType.BOOTS;
			case RING:
			case AMMO:
				return null;
			default:
				throw new IllegalStateException(name());
		}
	}
}
