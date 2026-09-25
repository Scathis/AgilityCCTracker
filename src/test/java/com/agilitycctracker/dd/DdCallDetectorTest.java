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
	// Widened default (BUILD_LOG.md, post-Phase-7 in-game testing) - ranks
	// habitually say things like "break DD go run" mid-sentence, so this
	// tolerates surrounding text instead of requiring an exact match.
	private static final String BREAK_REGEX_WILDCARD = "^(?=.*\\b(?:break|brk)\\b)(?=.*\\bdd\\b).*$";

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
	public void breakRegexWildcardMatchesMidSentencePhrasing()
	{
		assertTrue(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "break DD go run"));
		assertTrue(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "break dd"));
		assertTrue(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "brk dd please"));
		assertTrue(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "DD BREAK everyone run"));
	}

	@Test
	public void breakRegexWildcardStillRequiresBothWords()
	{
		// Bare "break" (no "dd") deliberately still doesn't match - avoids
		// treating unrelated chat like "taking a quick break" as a DD break.
		assertFalse(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "taking a quick break"));
		assertFalse(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "brk"));
		assertFalse(DdCallDetector.matches(BREAK_REGEX_WILDCARD, "dd"));
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
