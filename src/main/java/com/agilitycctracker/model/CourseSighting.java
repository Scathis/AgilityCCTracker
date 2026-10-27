package com.agilitycctracker.model;

import lombok.Value;

/**
 * One friends chat member seen at the mass this upload cycle - the player
 * themselves or someone observed at the course. Their FC rank is the only
 * other thing sent: the server uses it to decide who sees gear flags.
 */
@Value
public class CourseSighting
{
	String name;
	int rank;
}
