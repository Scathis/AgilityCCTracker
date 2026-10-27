package com.agilitycctracker.model;

import com.agilitycctracker.loadout.Slot;
import java.util.List;
import lombok.Value;

/**
 * A recommended runner setup built on the dashboard's Kits page: what to
 * wear and what to carry. Used only for the local pre-course check and bank
 * highlighting - nothing about the player's own inventory is ever sent back.
 */
@Value
public class Kit
{
	long id;
	String name;
	boolean isDefault;
	List<Equipment> equipment;
	List<InventoryItem> inventory;

	@Value
	public static class Equipment
	{
		Slot slot;
		int itemId;
	}

	@Value
	public static class InventoryItem
	{
		int itemId;
		int qty;
	}
}
