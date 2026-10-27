package com.agilitycctracker.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the local player's own plugin reports about themselves: which
 * of their required gear slots are empty. Nothing else - no skills, gear,
 * world or location.
 */
@Value
@Builder
public class SelfState
{
	List<String> loadoutFailed;
}
