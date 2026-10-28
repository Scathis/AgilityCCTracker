package com.agilitycctracker.api;

import com.agilitycctracker.collect.DeathTracker;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.mass.MassNotices;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.DdSection;
import com.agilitycctracker.model.DeathEvent;
import com.agilitycctracker.model.IngestBatch;
import com.agilitycctracker.model.LapEvent;
import com.agilitycctracker.model.LoadoutCheckDto;
import com.agilitycctracker.model.CourseSighting;
import com.agilitycctracker.model.SelfState;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds and sends one {@link IngestBatch} per upload tick, and separately
 * polls {@code /status} on its own slower cadence (BUILD_PLAN.md §5.6).
 * Both entry points ({@link #upload()}, {@link #pollStatus()}) are called
 * from the plugin's {@code @Schedule} methods, which already run off the
 * client thread — nothing here calls {@code client.*} at all (the local
 * player's name comes from MassParticipation, which the plugin refreshes
 * each game tick).
 */
@Slf4j
@Singleton
public class Uploader
{
	@Inject
	private MassParticipation massParticipation;

	@Inject
	private MassNotices massNotices;

	@Inject
	private ApiClient apiClient;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private SelfStateCollector selfStateCollector;

	@Inject
	private LapTracker lapTracker;

	@Inject
	private DeathTracker deathTracker;

	@Inject
	private FcMemberScanner fcMemberScanner;

	@Inject
	private DdTracker ddTracker;

	private Long lastDdSentAt;

	public void pollStatus()
	{
		// The status shows this player's own answer plus, for ranked viewers,
		// everyone else's - so it's fetched *for* a specific name, and that
		// same name is what the answer is matched against (never a name that
		// changed while the request was in flight).
		String rsn = massParticipation.getLocalRsn();
		StatusResponse status = apiClient.getStatus(rsn);
		if (status != null)
		{
			statusHolder.set(status);
			if (rsn != null)
			{
				// Before MassParticipation, which forgets the answer when the mass ends.
				massNotices.onStatus(status, massParticipation.isAttending());
				massParticipation.onStatus(status, rsn);
			}
		}
	}

	public void upload()
	{
		// Nothing leaves the client until the player has said they're
		// attending the active mass (see MassParticipation).
		if (!massParticipation.isAttending())
		{
			return;
		}

		String rsn = massParticipation.getLocalRsn();
		if (rsn == null)
		{
			return;
		}

		SelfState self = selfStateCollector.consume();
		List<LapEvent> laps = lapTracker.drain();
		List<DeathEvent> deaths = deathTracker.drain();
		List<LoadoutCheckDto> loadoutChecks = fcMemberScanner.drain();

		// Deliberately not part of the "nothing changed, skip the POST"
		// check below - this is a live heartbeat (BUILD_LOG.md's attendance
		// auto-timeout), not a durable event, so it's fine if a skipped
		// upload occasionally drops one, but it must never itself be
		// suppressed just because nothing else happened to change.
		Map<String, Integer> seen = new HashMap<>(fcMemberScanner.getCurrentlyInCourse());
		if (selfStateCollector.isAtMass())
		{
			seen.put(rsn, selfStateCollector.getFcRank());
		}
		List<CourseSighting> courseSightings = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : seen.entrySet())
		{
			courseSightings.add(new CourseSighting(entry.getKey(), entry.getValue()));
		}

		boolean ddClosed = ddTracker.getBrokenAt() != null;
		boolean ddDue = false;
		DdSection dd = null;
		if (ddTracker.isWindowOpen() || ddClosed)
		{
			long now = System.currentTimeMillis();
			if (ddClosed || lastDdSentAt == null || now - lastDdSentAt >= 10_000)
			{
				dd = new DdSection(ddTracker.getCalledAt(), ddTracker.getBrokenAt(), ddTracker.summaries());
				ddDue = true;
			}
		}

		if (self == null && laps.isEmpty() && deaths.isEmpty()
			&& loadoutChecks.isEmpty() && dd == null && courseSightings.isEmpty())
		{
			// Nothing but rsn would be sent - skip the POST entirely.
			return;
		}

		IngestBatch.IngestBatchBuilder builder = IngestBatch.builder()
			.rsn(rsn);
		if (self != null)
		{
			builder.self(self);
		}
		if (!laps.isEmpty())
		{
			builder.laps(laps);
		}
		if (!deaths.isEmpty())
		{
			builder.deaths(deaths);
		}
		if (!courseSightings.isEmpty())
		{
			builder.courseSightings(courseSightings);
		}
		if (!loadoutChecks.isEmpty())
		{
			builder.loadoutChecks(loadoutChecks);
		}
		if (dd != null)
		{
			builder.dd(dd);
		}

		boolean ok = apiClient.postIngest(builder.build());
		if (ok)
		{
			if (self != null)
			{
				selfStateCollector.markSent(self);
			}
			if (ddDue)
			{
				lastDdSentAt = System.currentTimeMillis();
				if (ddClosed)
				{
					ddTracker.acknowledgeClosed();
					lastDdSentAt = null;
				}
			}
		}
		else
		{
			// Nothing is silently lost on a failed upload - put it all back.
			// (dd isn't restored - DdTracker's live state is untouched by a
			// failed send, so the same window/samples are simply resent next tick.)
			if (self != null)
			{
				selfStateCollector.restore(self);
			}
			if (!laps.isEmpty())
			{
				lapTracker.restore(laps);
			}
			if (!deaths.isEmpty())
			{
				deathTracker.restore(deaths);
			}
			if (!loadoutChecks.isEmpty())
			{
				fcMemberScanner.restore(loadoutChecks);
			}
		}
	}
}
