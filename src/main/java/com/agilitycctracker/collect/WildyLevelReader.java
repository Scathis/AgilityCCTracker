package com.agilitycctracker.collect;

import com.agilitycctracker.util.WildernessLevel;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Shared wilderness-level detection so SelfStateCollector and DeathTracker
 * don't each re-implement the widget/coordinate fallback logic.
 */
@Singleton
public class WildyLevelReader
{
	@Inject
	private Client client;

	/** Null when not in the wilderness (per the varbit) or the level can't be determined. */
	public Integer detect()
	{
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 1)
		{
			return null;
		}

		Widget widget = client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL);
		if (widget != null)
		{
			Integer fromWidget = WildernessLevel.fromWidgetText(widget.getText());
			if (fromWidget != null)
			{
				return fromWidget;
			}
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		return WildernessLevel.fromCoordinate(local.getWorldLocation().getY());
	}
}
