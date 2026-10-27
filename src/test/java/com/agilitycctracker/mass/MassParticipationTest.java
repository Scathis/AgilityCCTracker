package com.agilitycctracker.mass;

import static org.junit.Assert.assertEquals;

import com.agilitycctracker.MassRole;
import com.agilitycctracker.mass.MassParticipation.Action;
import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.Participant;
import com.agilitycctracker.model.StatusResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MassParticipationTest
{
	private static final long NONE = -1;
	private static final String ME = "Some Player";

	private static StatusResponse status(Long eventId, List<Participant> participants)
	{
		ActiveEvent event = eventId != null ? new ActiveEvent(eventId, "Mass", 0) : null;
		return new StatusResponse("Owner", "FC", 0, true, 0, event, participants, Collections.emptyList(), null);
	}

	private static Participant participant(String rsn, String status, MassRole role)
	{
		return new Participant(rsn, status, role);
	}

	@Test
	public void noStatusDoesNothing()
	{
		assertEquals(Action.NONE, MassParticipation.decide(null, ME, NONE, false, NONE));
	}

	@Test
	public void noActiveMassAndNothingHeldDoesNothing()
	{
		assertEquals(Action.NONE, MassParticipation.decide(status(null, null), ME, NONE, false, NONE));
	}

	@Test
	public void massEndingClearsAnAttendingPlayer()
	{
		assertEquals(Action.RESET, MassParticipation.decide(status(null, null), ME, 5, true, 5));
	}

	@Test
	public void aDifferentMassStartingClearsTheOldAnswer()
	{
		assertEquals(Action.RESET, MassParticipation.decide(status(6L, null), ME, 5, true, 5));
	}

	@Test
	public void newMassWithNoAnswerPromptsOnce()
	{
		StatusResponse s = status(5L, Collections.emptyList());
		assertEquals(Action.PROMPT, MassParticipation.decide(s, ME, NONE, false, NONE));
		assertEquals(Action.NONE, MassParticipation.decide(s, ME, NONE, false, 5));
	}

	@Test
	public void nullParticipantsFromAnOlderServerStillPrompts()
	{
		assertEquals(Action.PROMPT, MassParticipation.decide(status(5L, null), ME, NONE, false, NONE));
	}

	@Test
	public void notLoggedInNeverPrompts()
	{
		assertEquals(Action.NONE, MassParticipation.decide(status(5L, Collections.emptyList()), null, NONE, false, NONE));
	}

	@Test
	public void serverRememberedAttendingIsRestoredWithoutPrompting()
	{
		StatusResponse s = status(5L, Arrays.asList(participant("some player", "ATTENDING", MassRole.ANTI)));
		assertEquals(Action.RESTORE_ATTENDING, MassParticipation.decide(s, ME, NONE, false, NONE));
		assertEquals(Action.NONE, MassParticipation.decide(s, ME, 5, true, 5));
	}

	@Test
	public void serverRememberedDeclineIsRestoredWithoutPrompting()
	{
		StatusResponse s = status(5L, Arrays.asList(participant(ME, "DECLINED", MassRole.RUNNER)));
		assertEquals(Action.RESTORE_DECLINED, MassParticipation.decide(s, ME, NONE, false, NONE));
		assertEquals(Action.NONE, MassParticipation.decide(s, ME, 5, false, 5));
	}

	@Test
	public void otherPlayersAnswersDoNotCountAsMine()
	{
		StatusResponse s = status(5L, Arrays.asList(participant("Someone Else", "ATTENDING", MassRole.RUNNER)));
		assertEquals(Action.PROMPT, MassParticipation.decide(s, ME, NONE, false, NONE));
	}

	@Test
	public void ourOwnAnswerNotYetListedDoesNotPromptAgain()
	{
		// The poll can race our own respond POST: answered locally, server list not caught up yet.
		assertEquals(Action.NONE, MassParticipation.decide(status(5L, Collections.emptyList()), ME, 5, true, 5));
	}

	@Test
	public void inTheCourseDuringAMassAutoAttendsEvenAfterSayingNo()
	{
		// lastAttemptEventId NONE: never tried for this mass (covers No, closed popup, no answer yet).
		assertEquals(true, MassParticipation.shouldAutoAttend(false, true, true, 5, NONE, 0, 1_000));
	}

	@Test
	public void autoAttendNeedsTheCourseAndTheTrackedChat()
	{
		assertEquals(false, MassParticipation.shouldAutoAttend(false, false, true, 5, NONE, 0, 1_000));
		assertEquals(false, MassParticipation.shouldAutoAttend(false, true, false, 5, NONE, 0, 1_000));
	}

	@Test
	public void alreadyAttendingNeverReSends()
	{
		assertEquals(false, MassParticipation.shouldAutoAttend(true, true, true, 5, NONE, 0, 1_000));
	}

	@Test
	public void aFailedAutoAttendRetriesAfterThirtySecondsNotEveryTick()
	{
		assertEquals(false, MassParticipation.shouldAutoAttend(false, true, true, 5, 5, 1_000, 1_600));
		assertEquals(true, MassParticipation.shouldAutoAttend(false, true, true, 5, 5, 1_000, 31_000));
		// A new mass is tried straight away.
		assertEquals(true, MassParticipation.shouldAutoAttend(false, true, true, 6, 5, 1_000, 1_600));
	}
}
