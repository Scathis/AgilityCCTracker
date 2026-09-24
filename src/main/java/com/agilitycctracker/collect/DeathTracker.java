package com.agilitycctracker.collect;

import com.agilitycctracker.model.DeathEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.VarbitID;

/**
 * Queues a {@link DeathEvent} whenever the local player dies while in the
 * wilderness. Deliberately records no killer/attacker information, per
 * BUILD_PLAN.md §5.4.
 */
@Singleton
public class DeathTracker
{
	private static final int MAX_QUEUE = 500;

	@Inject
	private Client client;

	@Inject
	private WildyLevelReader wildyLevelReader;

	private final Deque<DeathEvent> queue = new ArrayDeque<>();

	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (local == null || actor != local)
		{
			return;
		}
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 1)
		{
			return;
		}

		Integer wildyLevel = wildyLevelReader.detect();
		synchronized (this)
		{
			queue.addLast(new DeathEvent(System.currentTimeMillis(), client.getWorld(), wildyLevel != null ? wildyLevel : 0));
			while (queue.size() > MAX_QUEUE)
			{
				queue.pollFirst();
			}
		}
	}

	public synchronized List<DeathEvent> drain()
	{
		if (queue.isEmpty())
		{
			return Collections.emptyList();
		}
		List<DeathEvent> drained = new ArrayList<>(queue);
		queue.clear();
		return drained;
	}

	public synchronized void restore(List<DeathEvent> events)
	{
		List<DeathEvent> combined = new ArrayList<>(events);
		combined.addAll(queue);
		queue.clear();
		queue.addAll(combined);
		while (queue.size() > MAX_QUEUE)
		{
			queue.pollLast();
		}
	}

	public synchronized void reset()
	{
		queue.clear();
	}
}
