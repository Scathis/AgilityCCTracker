package com.agilitycctracker;

/**
 * Which side of the mass the player is on. Only runners are gear-checked;
 * antis are attendance-time only (no laps, no DD, no loadout checks).
 */
public enum MassRole
{
	RUNNER("Runner"),
	ANTI("Anti");

	private final String label;

	MassRole(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
