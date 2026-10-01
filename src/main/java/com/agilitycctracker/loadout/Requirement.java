package com.agilitycctracker.loadout;

import java.util.List;
import lombok.Value;

/**
 * One slot's rule within a {@link Loadout}: either required (something must
 * be worn there) or not, and optionally restricted to a specific allow-list
 * of item ids. No allow-list means "anything in this slot passes" - the
 * slot's own name (e.g. "WEAPON") is the stable key used in failed-
 * requirement-key lists, since one slot = at most one requirement now.
 */
@Value
public class Requirement
{
	Slot slot;
	boolean required;
	List<Integer> allowedItemIds;
}
