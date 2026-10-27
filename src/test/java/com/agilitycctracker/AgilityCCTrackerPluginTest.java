package com.agilitycctracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AgilityCCTrackerPluginTest
{
	// loadBuiltin takes Class<? extends Plugin>... - the generic varargs array is what javac warns about.
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AgilityCCTrackerPlugin.class);
		RuneLite.main(args);
	}
}
