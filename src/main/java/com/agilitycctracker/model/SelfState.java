package com.agilitycctracker.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the local player's own plugin reports about themselves: their
 * world (the server's FC roster diff needs it) and which of their required
 * gear slots are empty. Nothing else - no skills, gear or location.
 */
@Value
@Builder
public class SelfState
{
	Integer world;
	List<String> loadoutFailed;
}
