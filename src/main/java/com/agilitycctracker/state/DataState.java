package com.agilitycctracker.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest value of a field that changes on the client thread and is
 * consumed for upload from the async uploader thread. Only ever holds a
 * pending value when it differs from the last value actually sent.
 */
public class DataState<T>
{
	private final AtomicReference<T> pending = new AtomicReference<>();
	private T lastSent;

	public synchronized void update(T v)
	{
		if (!Objects.equals(v, lastSent))
		{
			pending.set(v);
		}
	}

	public T consume()
	{
		return pending.getAndSet(null);
	}

	public synchronized void markSent(T v)
	{
		lastSent = v;
	}

	/**
	 * Puts a value back after a failed upload, but only if nothing newer has
	 * been queued in the meantime.
	 */
	public void restore(T v)
	{
		pending.compareAndSet(null, v);
	}

	/**
	 * Clears both the pending and last-sent value. Not part of the original
	 * GIM-style holder this was modelled on, but needed here: the plugin
	 * spec (BUILD_PLAN.md §5.2) requires resetting all collector state on
	 * logout/world-hop and on account-hash change, and there is no other way
	 * to forget a stale lastSent across a relogin.
	 */
	public synchronized void reset()
	{
		pending.set(null);
		lastSent = null;
	}
}
