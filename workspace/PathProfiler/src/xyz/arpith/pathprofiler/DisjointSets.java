package xyz.arpith.pathprofiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Unit;

public class DisjointSets {
	private List<Map<Unit, Set<Unit>>> disjointSet;

	public DisjointSets() {
		disjointSet = new ArrayList<Map<Unit, Set<Unit>>>();
	}

	public void create_set(Unit element) {
		Map<Unit, Set<Unit>> map = new HashMap<Unit, Set<Unit>>();
		Set<Unit> set = new HashSet<Unit>();
		set.add(element);
		map.put(element, set);
		disjointSet.add(map);
	}

	public void union(Unit first, Unit second) {
		Unit first_rep = find_set(first);
		Unit second_rep = find_set(second);

		Set<Unit> first_set = null;
		Set<Unit> second_set = null;

		for (int index = 0; index < disjointSet.size(); index++) {
			Map<Unit, Set<Unit>> map = disjointSet.get(index);
			if (map.containsKey(first_rep)) {
				first_set = map.get(first_rep);
			} else if (map.containsKey(second_rep)) {
				second_set = map.get(second_rep);
			}
		}

		if (first_set != null && second_set != null)
			first_set.addAll(second_set);

		for (int index = 0; index < disjointSet.size(); index++) {
			Map<Unit, Set<Unit>> map = disjointSet.get(index);
			if (map.containsKey(first_rep)) {
				map.put(first_rep, first_set);
			} else if (map.containsKey(second_rep)) {
				map.remove(second_rep);
				disjointSet.remove(index);
			}
		}

		return;
	}

	public Unit find_set(Unit element) {
		for (int index = 0; index < disjointSet.size(); index++) {
			Map<Unit, Set<Unit>> map = disjointSet.get(index);
			Set<Unit> keySet = map.keySet();
			for (Unit key : keySet) {
				Set<Unit> set = map.get(key);
				if (set.contains(element)) {
					return key;
				}
			}
		}
		return (null);
	}

	public int getNumberofDisjointSets() {
		return disjointSet.size();
	}
}