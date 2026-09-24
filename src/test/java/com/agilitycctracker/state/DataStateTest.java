package com.agilitycctracker.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DataStateTest
{
	@Test
	public void updateQueuesWhenDifferentFromLastSent()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		assertEquals("a", state.consume());
	}

	@Test
	public void updateIsNoOpWhenSameAsLastSent()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		state.markSent(state.consume());
		state.update("a");
		assertNull("re-sending the same value should not re-queue it", state.consume());
	}

	@Test
	public void consumeClearsThePendingValue()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		assertEquals("a", state.consume());
		assertNull("a second consume with nothing new queued should be null", state.consume());
	}

	@Test
	public void restoreOnlyAppliesWhenNothingNewerIsPending()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		String consumed = state.consume();
		state.restore(consumed);
		assertEquals("a failed upload should be put back", "a", state.consume());
	}

	@Test
	public void restoreDoesNotOverwriteANewerPendingValue()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		String consumed = state.consume();
		state.update("b");
		state.restore(consumed);
		assertEquals("newer value must win over a late restore", "b", state.consume());
	}

	@Test
	public void updateAfterAValueChangesBackQueuesAgain()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		state.markSent(state.consume());
		state.update("b");
		state.markSent(state.consume());
		state.update("a");
		assertEquals("a", state.consume());
	}

	@Test
	public void resetClearsBothPendingAndLastSent()
	{
		DataState<String> state = new DataState<>();
		state.update("a");
		state.markSent(state.consume());
		state.reset();
		state.update("a");
		assertEquals("after reset, even the same value should queue again", "a", state.consume());
	}
}
