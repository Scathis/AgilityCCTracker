package com.agilitycctracker.goal;

import com.agilitycctracker.AgilityCCTrackerConfig;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;

/**
 * Wilderness agility tickets for the Agility goal card, and the goal itself.
 * Banked tickets are counted whenever the bank's contents load (opening a
 * bank) and remembered per account in RuneLite's profile config, so the card
 * still knows them with the bank shut. Local only - nothing here is sent.
 */
@Singleton
public class TicketCount
{
	public static final int TICKET_ITEM_ID = ItemID.WILDY_AGILITY_TOKEN;
	static final String BANK_KEY = "bankTickets";
	static final String GOAL_KEY = "agilityGoal";

	@Inject
	private ConfigManager configManager;

	/** Client thread, from the plugin's ItemContainerChanged subscriber. */
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		int tickets = count(event.getItemContainer());
		if (!Objects.equals(getBankTickets(), tickets))
		{
			configManager.setRSProfileConfiguration(AgilityCCTrackerConfig.GROUP, BANK_KEY, tickets);
		}
	}

	/** Tickets in the bank when it was last opened on this account, or null if it hasn't been opened yet. */
	public Integer getBankTickets()
	{
		return configManager.getRSProfileConfiguration(AgilityCCTrackerConfig.GROUP, BANK_KEY, Integer.class);
	}

	public String getGoal()
	{
		return AgilityGoal.parseGoal(configManager.getRSProfileConfiguration(AgilityCCTrackerConfig.GROUP, GOAL_KEY));
	}

	public void setGoal(String goal)
	{
		configManager.setRSProfileConfiguration(AgilityCCTrackerConfig.GROUP, GOAL_KEY, AgilityGoal.parseGoal(goal));
	}

	public static int count(ItemContainer container)
	{
		int tickets = 0;
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() == TICKET_ITEM_ID)
				{
					tickets += item.getQuantity();
				}
			}
		}
		return tickets;
	}
}
