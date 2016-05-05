package edu.buptant.statistics;

public class CpuStatistics {
	private static final String TAG = CpuStatistics.class.getSimpleName();
	
	public static long parse_totalCpuTime1;
	public static long parse_processCpuTime1;
	public static long parse_totalCpuTime2;
	public static long parse_processCpuTime2;
	public static long getParse_totalCpuTime1() {
		return parse_totalCpuTime1;
	}
	public static void setParse_totalCpuTime1(long parse_totalCpuTime1) {
		CpuStatistics.parse_totalCpuTime1 = parse_totalCpuTime1;
	}
	public static long getParse_processCpuTime1() {
		return parse_processCpuTime1;
	}
	public static void setParse_processCpuTime1(long parse_processCpuTime1) {
		CpuStatistics.parse_processCpuTime1 = parse_processCpuTime1;
	}
	public static long getParse_totalCpuTime2() {
		return parse_totalCpuTime2;
	}
	public static void setParse_totalCpuTime2(long parse_totalCpuTime2) {
		CpuStatistics.parse_totalCpuTime2 = parse_totalCpuTime2;
	}
	public static long getParse_processCpuTime2() {
		return parse_processCpuTime2;
	}
	public static void setParse_processCpuTime2(long parse_processCpuTime2) {
		CpuStatistics.parse_processCpuTime2 = parse_processCpuTime2;
	}
	
	
}
