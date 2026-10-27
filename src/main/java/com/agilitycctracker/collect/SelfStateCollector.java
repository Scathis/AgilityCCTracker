package com.agilitycctracker.collect;

import com.agilitycctracker.model.SelfState;
import com.agilitycctracker.state.DataState;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks the little the server needs about the local player - their own
 * missing-gear slots, as a change-tracked {@link SelfState} - plus local
 * state: whether they're at the mass (for the attendance heartbeat), their
 * FC rank, the course area and their ticket count. No skills, gear, world or
 * location are ever uploaded.
 * Only class besides {@code LapTracker}/{@code DeathTracker} allowed to read
 * local-player-only data; never reads other players (see
 * {@code scan/FcMemberScanner}, added in Phase 3, for the one exception that
 * reads *other* players).
 */
@Singleton
public class SelfStateCollector
{
	private static final int WILDERNESS_AGILITY_COURSE_REGION = 11837;
	static final int WILDY_TICKET_ITEM_ID = ItemID.WILDY_AGILITY_TOKEN;

	@Inject
	private Client client;

	private final DataState<SelfState> dataState = new DataState<>();

	private final AtomicInteger wildyTicketCount = new AtomicInteger();
	private final AtomicBoolean onCourse = new AtomicBoolean();
	private final AtomicBoolean atMass = new AtomicBoolean();
	private final AtomicInteger fcRank = new AtomicInteger(-1);
	private volatile List<String> loadoutFailed;

	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// Worn items are deliberately never collected or uploaded: the server
		// only ever learns which required slots are empty (loadoutFailed).
		if (event.getContainerId() == InventoryID.INV)
		{
			updateTicketCount();
			refresh();
		}
	}

	private void updateTicketCount()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		int tickets = 0;
		if (inv != null)
		{
			for (Item item : inv.getItems())
			{
				if (item != null && item.getId() == WILDY_TICKET_ITEM_ID)
				{
					tickets += item.getQuantity();
				}
			}
		}
		wildyTicketCount.set(tickets);
	}

	/** Read by LapTracker to compute tickets_gained as a delta between laps. */
	public int getWildyTicketCount()
	{
		return wildyTicketCount.get();
	}

	/**
	 * Called from the 1s schedule tick (client thread). {@code rankInTrackedFc}
	 * is the player's rank in the tracked friends chat, or -1 when they're not
	 * in it.
	 */
	public void onTick(int rankInTrackedFc)
	{
		Player local = client.getLocalPlayer();
		int region = local != null ? local.getWorldLocation().getRegionID() : -1;
		onCourse.set(region == WILDERNESS_AGILITY_COURSE_REGION);
		atMass.set(isAroundCourse(region));
		fcRank.set(rankInTrackedFc);
		refresh();
	}

	/**
	 * The course region or any region touching it. Wider than the course
	 * itself so antis working the area around it count as present; this only
	 * decides whether the player's own plugin says "I'm here" - it never
	 * sends where.
	 */
	static boolean isAroundCourse(int region)
	{
		if (region < 0)
		{
			return false;
		}
		int dx = (region >> 8) - (WILDERNESS_AGILITY_COURSE_REGION >> 8);
		int dy = (region & 0xFF) - (WILDERNESS_AGILITY_COURSE_REGION & 0xFF);
		return Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
	}

	/** Whether the player is at the mass, for the attendance heartbeat. */
	public boolean isAtMass()
	{
		return atMass.get();
	}

	/** The player's rank in the tracked friends chat, -1 when not in it. */
	public int getFcRank()
	{
		return fcRank.get();
	}

	/** Set by Phase 3's SelfLoadoutCheck once it exists; null until then. */
	public void setLoadoutFailed(List<String> failed)
	{
		this.loadoutFailed = failed;
		refresh();
	}

	/** For TrackerPanel's "in course area" indicator - thread-safe to read from the EDT. */
	public boolean isOnCourse()
	{
		return onCourse.get();
	}

	private SelfState buildSnapshot()
	{
		return SelfState.builder()
			.loadoutFailed(loadoutFailed)
			.build();
	}

	private void refresh()
	{
		dataState.update(buildSnapshot());
	}

	public SelfState consume()
	{
		return dataState.consume();
	}

	public void restore(SelfState state)
	{
		dataState.restore(state);
	}

	public void markSent(SelfState state)
	{
		dataState.markSent(state);
	}

	public void reset()
	{
		wildyTicketCount.set(0);
		atMass.set(false);
		fcRank.set(-1);
		onCourse.set(false);
		loadoutFailed = null;
		dataState.reset();
	}
}
