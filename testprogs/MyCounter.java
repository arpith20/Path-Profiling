import java.util.HashMap;

public class MyCounter {
	private static HashMap<String, HashMap<Integer, Integer>> count;
	private static HashMap<String, Integer> r;

	public static synchronized void increase(String method, Integer val) {
		Integer v = r.get(method);
		v = v + val;
		r.put(method, v);
	}

	public static synchronized void initialize(String method, Integer val) {
		if (r == null) {
			r = new HashMap<String, Integer>();
		}
		r.put(method, val);
	}

	public static synchronized void setCount(String method, Integer add_val) {
		if (count == null) {
			count = new HashMap<String, HashMap<Integer, Integer>>();
		}
		Integer val = r.get(method);
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
