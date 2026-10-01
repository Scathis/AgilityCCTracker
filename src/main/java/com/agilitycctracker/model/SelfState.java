package com.agilitycctracker.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the local player's own plugin instance may report about
 * themselves. Fields are all boxed/nullable so partial updates (only a
 * changed subset, or fields disabled by a sharing toggle) can omit sections
 * cleanly when serialised.
 */
@Value
@Builder
public class SelfState
{
	Integer world;
	Integer combatLevel;
	Map<String, Integer> skills;
	List<EquipmentItem> equipment;
	Integer wildyLevel;
	Boolean onCourse;
	Boolean skulled;
	List<String> loadoutFailed;
}
