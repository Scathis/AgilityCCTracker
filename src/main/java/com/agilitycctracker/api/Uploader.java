package com.agilitycctracker.api;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.collect.DeathTracker;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.RosterTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.model.DdSection;
import com.agilitycctracker.model.DeathEvent;
import com.agilitycctracker.model.IngestBatch;
import com.agilitycctracker.model.LapEvent;
import com.agilitycctracker.model.LoadoutCheckDto;
import com.agilitycctracker.model.RosterSnapshot;
import com.agilitycctracker.model.SelfState;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Builds and sends one {@link IngestBatch} per upload tick, and separately
 * polls {@code /status} on its own slower cadence (BUILD_PLAN.md §5.6).
 * Both entry points ({@link #upload()}, {@link #pollStatus()}) are called
 * from the plugin's {@code @Schedule} methods, which already run off the
 * client thread — nothing here calls {@code client.*} except cheap reads of
 * already-current fields ({@code getLocalPlayer()}, {@code getAccountHash()}),
 * matching how RuneLite documents those two as safe from any thread.
 */
@Slf4j
@Singleton
public class Uploader
{
	@Inject
	private Client client;

	@Inject
	private AgilityCCTrackerConfig config;

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
	private RosterTracker rosterTracker;

	@Inject
	private FcMemberScanner fcMemberScanner;

	@Inject
	private DdTracker ddTracker;

	private Long lastDdSentAt;

	private boolean hasApiUrl()
	{
		String url = config.apiUrl();
		return url != null && !url.trim().isEmpty();
	}

	public void pollStatus()
	{
		if (!hasApiUrl())
		{
			return;
		}
		StatusResponse status = apiClient.getStatus();
		if (status != null)
		{
			statusHolder.set(status);
		}
	}

	public void upload()
	{
		if (!hasApiUrl())
		{
			return;
		}

		Player local = client.getLocalPlayer();
		String rsn = local != null ? local.getName() : null;
		if (rsn == null)
		{
			return;
		}

		SelfState self = selfStateCollector.consume();
		List<LapEvent> laps = lapTracker.drain();
		List<DeathEvent> deaths = deathTracker.drain();
		RosterSnapshot roster = rosterTracker.consume();
		List<LoadoutCheckDto> loadoutChecks = fcMemberScanner.drain();

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

		if (self == null && laps.isEmpty() && deaths.isEmpty() && roster == null && loadoutChecks.isEmpty() && dd == null)
		{
			// Nothing but rsn would be sent - skip the POST entirely.
			return;
		}

		IngestBatch.IngestBatchBuilder builder = IngestBatch.builder()
			.rsn(rsn)
			.accountHash(sha256Hex(String.valueOf(client.getAccountHash())));
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
		if (roster != null)
		{
			builder.roster(roster);
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
			if (roster != null)
			{
				rosterTracker.restore(roster);
			}
			if (!loadoutChecks.isEmpty())
			{
				fcMemberScanner.restore(loadoutChecks);
			}
		}
	}

	private static String sha256Hex(String input)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			log.warn("SHA-256 unavailable, omitting account_hash", e);
			return null;
		}
	}
}
