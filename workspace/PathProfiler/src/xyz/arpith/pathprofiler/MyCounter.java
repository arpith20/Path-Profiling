package xyz.arpith.pathprofiler;

public class MyCounter {
	private static int c = 0;

	public static synchronized void increase(int howmany) {
		c += howmany;
	}

	public static synchronized void report() {
		System.err.println("counter : " + c);
	}
}
