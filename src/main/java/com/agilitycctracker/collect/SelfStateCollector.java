package com.agilitycctracker.collect;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.model.SelfState;
import com.agilitycctracker.state.DataState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks the local player's own state (skills, wilderness level, etc.)
 * and exposes it as a change-tracked {@link SelfState} via a {@link DataState}.
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

	@Inject
	private WildyLevelReader wildyLevelReader;

	@Inject
	private AgilityCCTrackerConfig config;

	private final DataState<SelfState> dataState = new DataState<>();

	private final AtomicReference<Map<String, Integer>> skills = new AtomicReference<>();
	private final AtomicInteger wildyTicketCount = new AtomicInteger();
	private final AtomicInteger world = new AtomicInteger();
	private final AtomicReference<Integer> wildyLevel = new AtomicReference<>();
	private final AtomicBoolean onCourse = new AtomicBoolean();
	private final AtomicBoolean skulled = new AtomicBoolean();
	private volatile List<String> loadoutFailed;

	public void onStatChanged()
	{
		// Skill.values() no longer includes OVERALL at all (it's a deprecated
		// constant literally assigned null, kept only for source
		// compatibility) - nothing to skip here any more.
		Map<String, Integer> xp = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			xp.put(skill.name().toLowerCase(), client.getSkillExperience(skill));
		}
		skills.set(Collections.unmodifiableMap(xp));
		refresh();
	}

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

	/** Called from the 1s schedule tick. */
	public void onTick()
	{
		world.set(client.getWorld());
		Player local = client.getLocalPlayer();
		skulled.set(local != null && local.getSkullIcon() != -1);
		wildyLevel.set(wildyLevelReader.detect());
		onCourse.set(local != null && local.getWorldLocation().getRegionID() == WILDERNESS_AGILITY_COURSE_REGION);
		refresh();
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
		SelfState.SelfStateBuilder builder = SelfState.builder()
			.world(world.get())
			.onCourse(onCourse.get())
			.skulled(skulled.get())
			.wildyLevel(wildyLevel.get())
			.loadoutFailed(loadoutFailed);

		Player local = client.getLocalPlayer();
		builder.combatLevel(local != null ? local.getCombatLevel() : null);

		if (config.shareStats())
		{
			builder.skills(skills.get());
		}
		return builder.build();
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
		skills.set(null);
		wildyTicketCount.set(0);
		world.set(0);
		wildyLevel.set(null);
		onCourse.set(false);
		skulled.set(false);
		loadoutFailed = null;
		dataState.reset();
	}
}
