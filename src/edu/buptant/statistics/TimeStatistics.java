package edu.buptant.statistics;

public class TimeStatistics {
	private static final String TAG = TimeStatistics.class.getSimpleName();
	
	public static long parseStartTime;
	public static long parseCompleteTime;

	public static long getParseStartTime() {
		return parseStartTime;
	}
	public static void setParseStartTime(long parseStartTime) {
		TimeStatistics.parseStartTime = parseStartTime;
	}
	public static long getParseCompleteTime() {
		return parseCompleteTime;
	}
	public static void setParseCompleteTime(long parseCompleteTime) {
		TimeStatistics.parseCompleteTime = parseCompleteTime;
	}
	
}
