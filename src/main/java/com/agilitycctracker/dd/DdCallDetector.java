package com.agilitycctracker.dd;

import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.model.DdConfig;
import com.agilitycctracker.model.StatusResponse;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

/**
 * Watches friends-chat messages for a ranked member calling/breaking "DD"
 * (BUILD_PLAN.md §5.9). Only detection lives here - the actual per-tick
 * tracking is {@link DdTracker}, which this just opens/closes a window on.
 */
@Slf4j
@Singleton
public class DdCallDetector
{
	@Inject
	private Client client;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private DdTracker ddTracker;

	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.FRIENDSCHAT)
		{
			return;
		}
		StatusResponse status = statusHolder.get();
		if (status == null || status.getActiveEvent() == null || status.getDd() == null || status.getFcOwner() == null)
		{
			return;
		}
		FriendsChatManager fc = client.getFriendsChatManager();
		if (fc == null || fc.getOwner() == null
			|| !Text.toJagexName(fc.getOwner()).equalsIgnoreCase(Text.toJagexName(status.getFcOwner())))
		{
			return;
		}

		String sender = Text.toJagexName(Text.removeTags(event.getName()));
		FriendsChatMember member = fc.findByName(sender);
		if (member == null || member.getRank() == null || !isRanked(member.getRank().getValue(), status.getDd().getMinRank()))
		{
			return;
		}

		DdConfig dd = status.getDd();
		String message = Text.removeTags(event.getMessage());
		long now = System.currentTimeMillis();

		if (matches(dd.getCallRegex(), message))
		{
			ddTracker.openWindow(now, dd.getArriveGraceS() * 1000L, dd.getLeaveGraceS() * 1000L);
		}
		else if (matches(dd.getBreakRegex(), message))
		{
			ddTracker.closeWindow(now);
		}
	}

	/** Pure and package-visible for unit testing. */
	static boolean isRanked(int memberRankValue, int minRank)
	{
		return memberRankValue >= minRank;
	}

	/** Pure and package-visible for unit testing. */
	static boolean matches(String regex, String message)
	{
		if (regex == null || message == null)
		{
			return false;
		}
		try
		{
			return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message).matches();
		}
		catch (PatternSyntaxException e)
		{
			return false;
		}
	}
}
