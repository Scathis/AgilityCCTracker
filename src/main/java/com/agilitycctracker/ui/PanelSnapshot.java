package com.agilitycctracker.ui;

import lombok.Value;

/**
 * Client state the sidebar needs, read on the client thread (RuneLite
 * requires client.* calls there) and handed to the panel's EDT refresh.
 */
@Value
public class PanelSnapshot
{
	public static final PanelSnapshot LOGGED_OUT = new PanelSnapshot(null, -1, false);

	/** Display name, or null when logged out. */
	String name;
	/** Agility xp, or -1 when logged out. */
	int agilityXp;
	/** In the friends chat the server tracks. */
	boolean inTrackedFc;
}
