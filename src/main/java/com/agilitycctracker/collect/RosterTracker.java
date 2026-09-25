package com.agilitycctracker.collect;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.model.RosterMember;
import com.agilitycctracker.model.RosterSnapshot;
import com.agilitycctracker.model.StatusResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.client.util.Text;

/**
 * Snapshots the friends-chat roster while an event is active on this FC's
 * server. Driven entirely by {@link #onTick()} rather than its own scheduled
 * executor, so the 5s debounce / 60s keep-alive logic is pure and testable
 * with a fake clock instead of depending on Guice's ScheduledExecutorService
 * binding being available at plugin scope.
 */
@Singleton
public class RosterTracker
{
	private static final long DEBOUNCE_MS = 5_000;
	private static final long FULL_RESEND_MS = 60_000;

	@Inject
	private Client client;

	@Inject
	private AgilityCCTrackerConfig config;

	@Inject
	private StatusHolder statusHolder;

	private final AtomicReference<RosterSnapshot> pending = new AtomicReference<>();
	private volatile Long pendingChangeAt;
	private long lastSentAt;

	public void onFriendsChatJoined()
	{
		snapshotNow(System.currentTimeMillis());
	}

	public void onMemberChanged()
	{
		pendingChangeAt = System.currentTimeMillis();
	}

	/** Call once per tick (the plugin's 1s schedule). Drives both the debounce and the keep-alive resend. */
	public void onTick()
	{
		long now = System.currentTimeMillis();
		Long changeAt = pendingChangeAt;
		if (changeAt != null && now - changeAt >= DEBOUNCE_MS)
		{
			pendingChangeAt = null;
			snapshotNow(now);
			return;
		}
		if (isActive() && now - lastSentAt >= FULL_RESEND_MS)
		{
			snapshotNow(now);
		}
	}

	private void snapshotNow(long now)
	{
		if (!isActive())
		{
			return;
		}
		FriendsChatManager fc = client.getFriendsChatManager();
		if (fc == null)
		{
			return;
		}
		List<RosterMember> members = new ArrayList<>();
		for (FriendsChatMember member : fc.getMembers())
		{
			members.add(new RosterMember(Text.toJagexName(member.getName()), member.getRank().getValue(), member.getWorld()));
		}
		pending.set(new RosterSnapshot(now, members));
		lastSentAt = now;
	}

	private boolean isActive()
	{
		if (!config.shareRoster())
		{
			return false;
		}
		StatusResponse status = statusHolder.get();
		if (status == null || status.getActiveEvent() == null || status.getFcOwner() == null)
		{
			return false;
		}
		FriendsChatManager fc = client.getFriendsChatManager();
		if (fc == null || fc.getOwner() == null)
		{
			return false;
		}
		return Text.toJagexName(fc.getOwner()).equalsIgnoreCase(Text.toJagexName(status.getFcOwner()));
	}

	public RosterSnapshot consume()
	{
		return pending.getAndSet(null);
	}

	public void restore(RosterSnapshot snapshot)
	{
		pending.compareAndSet(null, snapshot);
	}

	public void reset()
	{
		pending.set(null);
		pendingChangeAt = null;
		lastSentAt = 0;
	}
}
