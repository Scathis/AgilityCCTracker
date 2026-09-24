package com.agilitycctracker.dd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests the pure, package-visible matches()/isRanked() static helpers
 * directly - no Client/ChatMessage mocking needed. Regexes match the
 * defaults from BUILD_PLAN.md §5.6.
 */
public class DdCallDetectorTest
{
	private static final String CALL_REGEX = "^\\s*dd\\s*!*$";
	private static final String BREAK_REGEX = "^\\s*(break|brk)\\s*(dd)?\\s*!*$";

	@Test
	public void callRegexMatchesPlainAndExcited()
	{
		assertTrue(DdCallDetector.matches(CALL_REGEX, "DD"));
		assertTrue(DdCallDetector.matches(CALL_REGEX, "dd!!"));
		assertTrue(DdCallDetector.matches(CALL_REGEX, "  dd  "));
	}

	@Test
	public void breakRegexMatchesEveryPhrasing()
	{
		assertTrue(DdCallDetector.matches(BREAK_REGEX, "break dd"));
		assertTrue(DdCallDetector.matches(BREAK_REGEX, "brk"));
		assertTrue(DdCallDetector.matches(BREAK_REGEX, "BREAK DD"));
	}

	@Test
	public void callRegexDoesNotMatchBreakPhrases()
	{
		assertFalse(DdCallDetector.matches(CALL_REGEX, "break dd"));
		assertFalse(DdCallDetector.matches(CALL_REGEX, "brk"));
	}

	@Test
	public void rankGateRespectsMinRank()
	{
		assertTrue(DdCallDetector.isRanked(3, 3));
		assertTrue(DdCallDetector.isRanked(5, 3));
		assertFalse(DdCallDetector.isRanked(0, 3));
		assertFalse(DdCallDetector.isRanked(-1, 3));
	}

	@Test
	public void nullOrEmptyRegexNeverMatches()
	{
		assertFalse(DdCallDetector.matches(null, "dd"));
		assertFalse(DdCallDetector.matches(CALL_REGEX, null));
	}
}
