package com.agilitycctracker.ui;

import com.agilitycctracker.kit.KitCheck;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines, in the bank, the items the selected kit says you're still missing
 * (pre-course check only). Traces the item sprite's own shape (RuneLite's
 * cached item outline) rather than boxing the whole slot.
 */
@Singleton
public class KitBankOverlay extends WidgetItemOverlay
{
	private static final Color OUTLINE = Theme.GOLD_BRIGHT;

	private final KitCheck kitCheck;
	private final ItemManager itemManager;

	@Inject
	private KitBankOverlay(KitCheck kitCheck, ItemManager itemManager)
	{
		this.kitCheck = kitCheck;
		this.itemManager = itemManager;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!kitCheck.isMissing(itemId))
		{
			return;
		}
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OUTLINE);
		if (outline == null)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(outline, bounds.x, bounds.y, null);
	}
}
