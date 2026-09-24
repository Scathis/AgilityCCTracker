package com.agilitycctracker.api;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.model.IngestBatch;
import com.agilitycctracker.model.StatusResponse;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Thin HTTP layer over the tracker server's plugin API. Never constructs its
 * own OkHttpClient/Gson (RuneLite Hub rule) — both are injected, and a
 * snake_case-naming copy of the injected Gson is derived via
 * {@code gson.newBuilder()} to match the server's JSON field convention
 * without needing a {@code @SerializedName} on every model field.
 */
@Slf4j
@Singleton
public class ApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String PLUGIN_VERSION = "0.1.0";

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson baseGson;

	@Inject
	private AgilityCCTrackerConfig config;

	private Gson gson;
	private final AtomicInteger skipRemaining = new AtomicInteger();
	private volatile boolean tokenInvalid;
	private volatile long rateLimitedUntil;

	private Gson gson()
	{
		if (gson == null)
		{
			gson = baseGson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
		}
		return gson;
	}

	/** Call when apiUrl/apiToken change, to clear the "invalid token" latch and any backoff. */
	public void onConfigChanged()
	{
		tokenInvalid = false;
		rateLimitedUntil = 0;
		skipRemaining.set(0);
	}

	public boolean isTokenInvalid()
	{
		return tokenInvalid;
	}

	public StatusResponse getStatus()
	{
		if (!canAttempt())
		{
			return null;
		}
		String base = baseUrl();
		if (base == null)
		{
			return null;
		}
		Request request = requestBuilder(base, "/api/v1/status").get().build();
		try (Response response = okHttpClient.newCall(request).execute())
		{
			if (!checkCommonErrors(response) || response.body() == null)
			{
				return null;
			}
			return gson().fromJson(response.body().string(), StatusResponse.class);
		}
		catch (IOException e)
		{
			log.debug("status request failed", e);
			triggerBackoff();
			return null;
		}
	}

	public boolean postIngest(IngestBatch batch)
	{
		if (!canAttempt())
		{
			return false;
		}
		String base = baseUrl();
		if (base == null)
		{
			return false;
		}
		RequestBody body = RequestBody.create(JSON, gson().toJson(batch));
		Request request = requestBuilder(base, "/api/v1/ingest").post(body).build();
		try (Response response = okHttpClient.newCall(request).execute())
		{
			return checkCommonErrors(response) && response.isSuccessful();
		}
		catch (IOException e)
		{
			log.debug("ingest request failed", e);
			triggerBackoff();
			return false;
		}
	}

	private boolean canAttempt()
	{
		if (tokenInvalid)
		{
			return false;
		}
		if (System.currentTimeMillis() < rateLimitedUntil)
		{
			return false;
		}
		int remaining = skipRemaining.get();
		if (remaining > 0)
		{
			skipRemaining.decrementAndGet();
			return false;
		}
		return true;
	}

	/** Network error / 5xx: skip the next 10 attempts before trying again. */
	private void triggerBackoff()
	{
		skipRemaining.set(10);
	}

	/** Applies the 401/403/429/5xx handling described in BUILD_PLAN.md §5.5; returns false if the caller has no usable response. */
	private boolean checkCommonErrors(Response response)
	{
		int code = response.code();
		if (code == 401 || code == 403)
		{
			tokenInvalid = true;
			return false;
		}
		if (code == 429)
		{
			rateLimitedUntil = System.currentTimeMillis() + 60_000;
			return false;
		}
		if (code >= 500)
		{
			triggerBackoff();
			return false;
		}
		return response.isSuccessful();
	}

	private String baseUrl()
	{
		String url = config.apiUrl();
		if (url == null)
		{
			return null;
		}
		url = url.trim();
		if (url.isEmpty())
		{
			return null;
		}
		while (url.endsWith("/"))
		{
			url = url.substring(0, url.length() - 1);
		}
		boolean isLocal = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1");
		if (!url.startsWith("https://") && !isLocal)
		{
			log.warn("Refusing non-HTTPS apiUrl: {}", url);
			return null;
		}
		return url;
	}

	private Request.Builder requestBuilder(String base, String path)
	{
		return new Request.Builder()
			.url(base + path)
			.header("Authorization", "Bearer " + config.apiToken())
			.header("User-Agent", "AgilityCCTracker/" + PLUGIN_VERSION + " RuneLite/" + RuneLiteProperties.getVersion())
			.header("Accept", "application/json")
			.header("Content-Type", "application/json");
	}
}
