package com.agilitycctracker.api;

/**
 * The tracker server this plugin talks to. Deliberately compiled in rather
 * than user-configurable: players install the plugin and go, with nothing to
 * set up (and nothing to mistype or point somewhere else).
 *
 * The token is a shared, effectively public key - this source is public - so
 * it only identifies "a plugin for this FC"; the server's real protection is
 * that nothing is accepted from a player who hasn't opted in to the active
 * mass.
 *
 * Developers can point a locally-run client at a test server with
 * {@code -Dagilitycc.apiUrl=... -Dagilitycc.apiToken=...}; there is no
 * in-game setting for it.
 */
public final class ServerConfig
{
	private static final String BASE_URL = "https://agilitycc.grestinator.com";
	private static final String TOKEN = "auMTyjm1_hbtvO85tG9ghAiy7s-srjNaP4RaYSkRLb4";

	private ServerConfig()
	{
	}

	public static String baseUrl()
	{
		return System.getProperty("agilitycc.apiUrl", BASE_URL);
	}

	public static String token()
	{
		return System.getProperty("agilitycc.apiToken", TOKEN);
	}
}
