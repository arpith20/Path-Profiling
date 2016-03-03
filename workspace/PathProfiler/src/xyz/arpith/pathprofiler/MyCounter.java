package xyz.arpith.pathprofiler;

import java.util.HashMap;

public class MyCounter {
	private static HashMap<Integer, Integer> count;

	public static synchronized void increase(Integer i, Integer c) {
		Integer val = count.get(i);
		count.put(i, val + c);

	}

	public static synchronized void initialize(Integer i, Integer c) {
		if (count == null)
			count = new HashMap<Integer, Integer>();
		count.put(i, c);
	}

	public static synchronized void report() {
		System.out.println("Done Analysis");
		// Iterator it = count.entrySet().iterator();
		// while (it.hasNext()) {
		// Map.Entry pair = (Map.Entry) it.next();
		// Integer i = (Integer) pair.getKey();
		// Integer c = (Integer) pair.getValue();
		// System.out.println("Path Sum:" + i + " --> " + c);
		// }
	}
}