package xyz.arpith.pathprofiler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MyCounter {
	private static HashMap<String, HashMap<Integer, Integer>> count;
	private static HashMap<String, Integer> r;

	/*
	 * Increment the value of r for a given method.
	 * 
	 * All data is present in input, which is formatted as 'inc:val#method'
	 */
	public static synchronized void increase(String input) {
		String method = input.split("#")[1];
		String data = input.split("#")[0];

		Integer val = Integer.parseInt(data.split(":")[1]);

		Integer v = r.get(method);
		if (v == null)
			v = 0;
		v = v + val;
		r.put(method, v);

	}

	/*
	 * Initialize the value of r for a given method.
	 * 
	 * All data is present in input, which is formatted as 'ini:val#method'
	 */
	public static synchronized void initialize(String input) {
		String method = input.split("#")[1];
		String data = input.split("#")[0];

		Integer val = Integer.parseInt(data.split(":")[1]);

		if (r == null) {
			r = new HashMap<String, Integer>();
		}

		r.put(method, val);
	}

	/*
	 * Increment value of count.
	 * 
	 * All data is present in input, which is formatted as:
	 * 
	 * 'count:[x or r]:val#method'
	 * 
	 * x-> count[val]++
	 * 
	 * r-> count[r+val]++
	 */
	public static synchronized void setCount(String input) {
		String method = input.split("#")[1];
		String data = input.split("#")[0];

		Integer add_val = Integer.parseInt(data.split(":")[2]);

		if (count == null) {
			count = new HashMap<String, HashMap<Integer, Integer>>();
		}

		Integer val;
		if (data.split(":")[1].equals("r")) {
			val = r.get(method);
			if (val == null)
				val = 0;
		} else
			val = 0;
		val = val + add_val;

		HashMap<Integer, Integer> count_vals = count.get(method);
		if (count_vals == null) {
			count_vals = new HashMap<Integer, Integer>();
			count_vals.put(val, 1);
			count.put(method, count_vals);
		} else {
			Integer count_val = count_vals.get(val);
			if (count_val == null) {
				count_vals.put(val, 1);
			} else {
				count_vals.put(val, count_val + 1);
			}
		}
	}

	// prints the output at the end of execution
	public static synchronized void report() {
		for (Map.Entry<String, HashMap<Integer, Integer>> entry : count.entrySet()) {
			String method = entry.getKey();
			System.out.println("****" + method);
			for (Map.Entry<Integer, Integer> valEntry : entry.getValue().entrySet()) {
				Integer name = valEntry.getKey();
				Integer student = valEntry.getValue();
				System.out.println("    PathSum:" + name + " --> Count: " + student);
			}
		}
	}

	// prints the output at the end of execution when System.exit is called
	public static synchronized void report_sys_exit(String ret_val) {
		System.out.println("Done Analysis");
		for (Map.Entry<String, HashMap<Integer, Integer>> entry : count.entrySet()) {
			String method = entry.getKey();
			System.out.println("****" + method);
			for (Map.Entry<Integer, Integer> valEntry : entry.getValue().entrySet()) {
				Integer name = valEntry.getKey();
				Integer student = valEntry.getValue();
				System.out.println("    PathSum:" + name + " --> Count: " + student);
			}
		}
		System.exit(Integer.parseInt(ret_val));
	}
}
