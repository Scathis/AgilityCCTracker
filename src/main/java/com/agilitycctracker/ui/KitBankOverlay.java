package com.agilitycctracker.ui;

import com.agilitycctracker.kit.KitCheck;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines, in the bank, the items the selected kit says you're still missing (pre-course check only). */
@Singleton
public class KitBankOverlay extends WidgetItemOverlay
{
	private static final Color OUTLINE = new Color(0xff, 0x90, 0x40);

	private final KitCheck kitCheck;

	@Inject
	private KitBankOverlay(KitCheck kitCheck)
	{
		this.kitCheck = kitCheck;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!kitCheck.isMissing(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(OUTLINE);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
	}
}
