package com.agilitycctracker.model;

import java.util.List;
import lombok.Value;

@Value
public class DdConfig
{
	List<DdTile> tiles;
	String callRegex;
	String breakRegex;
	int minRank;
	int arriveGraceS;
	int leaveGraceS;
	int maxMinutes;
	boolean crossbowCountsAsBowAtDd;
}
