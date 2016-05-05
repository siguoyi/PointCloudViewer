package edu.buptant.timestatistic;

public class TimeStatistic {
	private static final String TAG = TimeStatistic.class.getSimpleName();
	
	public static long parseStartTime;
	public static long parseCompleteTime;
	public static long loadStartTime;
	public static long loadCompleteTime;
	public static long getParseStartTime() {
		return parseStartTime;
	}
	public static void setParseStartTime(long parseStartTime) {
		TimeStatistic.parseStartTime = parseStartTime;
	}
	public static long getParseCompleteTime() {
		return parseCompleteTime;
	}
	public static void setParseCompleteTime(long parseCompleteTime) {
		TimeStatistic.parseCompleteTime = parseCompleteTime;
	}
	public static long getLoadStartTime() {
		return loadStartTime;
	}
	public static void setLoadStartTime(long loadStartTime) {
		TimeStatistic.loadStartTime = loadStartTime;
	}
	public static long getLoadCompleteTime() {
		return loadCompleteTime;
	}
	public static void setLoadCompleteTime(long loadCompleteTime) {
		TimeStatistic.loadCompleteTime = loadCompleteTime;
	}
	
	
}
