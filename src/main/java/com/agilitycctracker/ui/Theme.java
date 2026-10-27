package com.agilitycctracker.ui;

import java.awt.Color;
import java.awt.Font;
import net.runelite.client.ui.FontManager;

/**
 * The dashboard's palette (server/app/web/static/css/osrs-theme.css: gold
 * text on dark wood brown) and RuneLite's bundled RuneScape fonts - the same
 * fonts the website vendors - so the sidebar and in-game prompt match it.
 */
public final class Theme
{
	public static final Color BACKGROUND = new Color(0x241c12);
	public static final Color CARD = new Color(0x3a2f1f);
	public static final Color CARD_BORDER = new Color(0x6b4a24);
	public static final Color RAISED = new Color(0x4a3d28);
	public static final Color RAISED_HOVER = new Color(0x5a4a30);
	public static final Color LINE = new Color(0x5a4a30);
	public static final Color SHADOW = new Color(0x1f1a12);

	public static final Color GOLD = new Color(0xe3b341);
	public static final Color GOLD_BRIGHT = new Color(0xffb833);
	public static final Color GOLD_PALE = new Color(0xfff3d6);
	public static final Color TEXT = new Color(0xd8c6a0);
	public static final Color MUTED = new Color(0xa8936b);

	public static final Color GOOD = new Color(0x7fc14a);
	public static final Color BAD = new Color(0xd9573f);

	private Theme()
	{
	}

	public static Font title()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}
}
