package xyz.arpith.pathprofiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.jboss.util.graph.Edge;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.PackManager;
import soot.PhaseOptions;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.JimpleBody;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.util.cfgcmd.AltClassLoader;
import soot.util.cfgcmd.CFGGraphType;
import soot.util.cfgcmd.CFGIntermediateRep;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;

/**
 * 
 *
 * 
 */
public class PathProfiler extends BodyTransformer {

	private static final String packToJoin = "jtp";
	private static final String phaseSubname = "printcfg";
	private static final String phaseFullname = packToJoin + '.' + phaseSubname;
	private static final String altClassPathOptionName = "alt-class-path";
	private static final String graphTypeOptionName = "graph-type";
	private static final String defaultGraph = "BriefUnitGraph";
	private static final String irOptionName = "ir";
	private static final String defaultIR = "jimple";
	private static final String multipageOptionName = "multipages";
	private static final String briefLabelOptionName = "brief";

	private CFGGraphType graphtype;
	private CFGIntermediateRep ir;
	private CFGToDotGraph drawer;
	private Map methodsToPrint; // If the user specifies particular
	// methods to print, this is a map
	// from method name to the class
	// name declaring the method.

	Unit ENTRY;
	Unit EXIT;
	boolean spanningDummyBackedge;
	HashMap<Unit, NodeData> nodeDataHash = new HashMap<Unit, NodeData>();
	List<MyEdge> spanningTreeEdges = new ArrayList<MyEdge>();
	List<MyEdge> chordEdges = new ArrayList<MyEdge>();
	List<MyEdge> allEdges = new ArrayList<MyEdge>();
	HashMap<MyEdge, Integer> inc = new HashMap<MyEdge, Integer>();
	HashMap<MyEdge, String> instrument = new HashMap<MyEdge, String>();

	SootClass counterClass = null;
	SootMethod increaseCounter, reportCounter;

	public class DAG {
		List<MyEdge> edges;
		List<Unit> visited;
		List<MyEdge> backedges;
		List<MyEdge> artificial;
		List<MyEdge> original;
		List<MyEdge> singleexit;

		DAG() {
			edges = new ArrayList<MyEdge>();
			visited = new ArrayList<Unit>();
			backedges = new ArrayList<MyEdge>();
			artificial = new ArrayList<MyEdge>();
			original = new ArrayList<MyEdge>();
			singleexit = new ArrayList<MyEdge>();
		}

		public List<Unit> getPredsOf(Unit u) {
			List<Unit> pred = new ArrayList<Unit>();
			for (MyEdge e : edges) {
				if (e.tgt == u)
					pred.add(e.src);
			}
			return pred;
		}

		public List<Unit> getSuccsOf(Unit u) {
			List<Unit> succ = new ArrayList<Unit>();
			for (MyEdge e : edges) {
				if (e.src == u)
					succ.add(e.tgt);
			}
			return succ;
		}

		public void buildDAG(BriefUnitGraph cfg) {

			// ensure single exit
			if (cfg.getTails().size() > 1) {
				for (int i = 1; i < cfg.getTails().size(); i++) {
					MyEdge e = new MyEdge(cfg.getTails().get(i), EXIT);
					edges.add(e);
					singleexit.add(e);
				}
			}
			Iterator<Unit> cfg_iterator = cfg.iterator();
			while (cfg_iterator.hasNext()) {
				Unit unit = cfg_iterator.next();
				visited.add(unit);

				for (Unit succ : cfg.getSuccsOf(unit)) {
					if (visited.contains(succ)) {
						backedges.add(new MyEdge(unit, succ));

						MyEdge ent = new MyEdge(ENTRY, succ);
						edges.add(ent);
						artificial.add(ent);

						MyEdge ext = new MyEdge(unit, EXIT);
						edges.add(ext);
						artificial.add(ext);
					} else {
						MyEdge e = new MyEdge(unit, succ);
						edges.add(e);
						original.add(e);
					}
				}
			}
		}

	}

	public class MyEdge {
		Unit src;
		Unit tgt;

		public MyEdge(Unit u1, Unit u2) {
			src = u1;
			tgt = u2;
		}

		public boolean equals(MyEdge e2) {
			if (this == null && e2 == null)
				return true;
			if (this == null || e2 == null)
				return false;
			if (this.src == e2.src) {
				if (this.tgt == e2.tgt)
					return true;
			}
			return false;
		}

		public boolean isContainedIn(List<MyEdge> l) {
			for (MyEdge e : l) {
				if (this.equals(e))
					return true;
			}
			return false;
		}
	}

	public class NodeData {
		public int nodeNumber; // a unique number assigned to wach node
		public int numPaths; // NumPaths (v) as defined in paper; Figure 5
		public HashMap<Unit, Integer> edgeVal; // Contains data pertaining to
												// the outgoing edges of a node
		// Val (e) as defined in paper; Figure 5

		public List<Unit> succSpanningNode;

		NodeData(int val) {
			nodeNumber = val;
			numPaths = 0;
			edgeVal = new HashMap<Unit, Integer>();
			succSpanningNode = new ArrayList<Unit>();
		}

		public int getNodeNumber() {
			return nodeNumber;
		}

		// use this to modify edgeVal
		public void updateEdgeVal(Unit unit, Integer i) {
			edgeVal.put(unit, i);
		}

		public int getEdgeVal(Unit unit) {
			return edgeVal.get(unit);
		}
	}

	public void fabricatedata(BriefUnitGraph cfg) {
		Iterator<Unit> cfg_iterator = cfg.iterator();

		Unit u1 = cfg_iterator.next();
		Unit u2 = cfg_iterator.next();
		Unit u3 = cfg_iterator.next();
		Unit u4 = cfg_iterator.next();
		Unit u5 = cfg_iterator.next();
		Unit u6 = cfg_iterator.next();

		MyEdge e1 = new MyEdge(u1, u2);
		MyEdge e2 = new MyEdge(u1, u3);
		MyEdge e3 = new MyEdge(u2, u3);
		MyEdge e4 = new MyEdge(u2, u4);
		MyEdge e5 = new MyEdge(u3, u4);
		MyEdge e6 = new MyEdge(u4, u5);
		MyEdge e7 = new MyEdge(u4, u6);
		MyEdge e8 = new MyEdge(u5, u6);
		MyEdge e9 = new MyEdge(u6, u1);

		spanningTreeEdges.add(e1);
		spanningTreeEdges.add(e5);
		spanningTreeEdges.add(e7);
		spanningTreeEdges.add(e8);
		spanningTreeEdges.add(e9);

		chordEdges.add(e2);
		chordEdges.add(e3);
		chordEdges.add(e4);
		chordEdges.add(e6);

		NodeData nd = new NodeData(1);
		nd.edgeVal.put(u2, 2);
		nd.edgeVal.put(u3, 0);
		nodeDataHash.put(u1, nd);

		nd = new NodeData(2);
		nd.edgeVal.put(u3, 0);
		nd.edgeVal.put(u4, 2);
		nodeDataHash.put(u2, nd);

		nd = new NodeData(3);
		nd.edgeVal.put(u4, 0);
		nodeDataHash.put(u3, nd);

		nd = new NodeData(4);
		nd.edgeVal.put(u5, 1);
		nd.edgeVal.put(u6, 0);
		nodeDataHash.put(u4, nd);

		nd = new NodeData(5);
		nd.edgeVal.put(u6, 0);
		nodeDataHash.put(u5, nd);

		nd = new NodeData(6);
		nd.edgeVal.put(u1, 0);
		nodeDataHash.put(u6, nd);

		System.out.println(u1.toString());
		System.out.println(u2.toString());
		System.out.println(u3.toString());
		System.out.println(u4.toString());
		System.out.println(u5.toString());
		System.out.println(u6.toString());

		// determineIncrements(cfg);
	}

	protected void internalTransform(Body b, String phaseName, Map options) {
		System.out.println(Scene.v().getSootClassPath());
		initialize(options);

		synchronized (this) {
			if (counterClass == null) {
				counterClass = Scene.v().loadClassAndSupport("xyz.arpith.pathprofiler.MyCounter");
				increaseCounter = counterClass.getMethod("void increase(int)");
				reportCounter = counterClass.getMethod("void report()");
			}
		}
		SootMethod meth = b.getMethod();

		if ((methodsToPrint == null) || (meth.getDeclaringClass().getName() == methodsToPrint.get(meth.getName()))) {
			Body body = ir.getBody((JimpleBody) b);
			// System.out.println("This is the IR: \n" + body.toString());
			BriefUnitGraph cfg = new BriefUnitGraph(b);

			ENTRY = cfg.getHeads().get(0);
			EXIT = cfg.getTails().get(0);
			spanningDummyBackedge = true;

			DAG dag = new DAG();
			dag.buildDAG(cfg);
			// for initial stage testing
			// fabricatedata(cfg);

			// iterate through all statements and initialize them correctly

			int i = 0;
			for (Unit unit : dag.visited) {
				NodeData node = new NodeData(i++);

				Iterator<Unit> succ_iterator = dag.getSuccsOf(unit).iterator();
				while (succ_iterator.hasNext()) {
					node.updateEdgeVal(succ_iterator.next(), 0); // initialize
					// edgeValue
					// count to
					// 0
				}
				nodeDataHash.put(unit, node);
			}

			// assign values to edges in DAG (Algo in Figure 5)
			figure5(dag);

			buildSpanningTree(dag);
			buildChordEdges(dag);

			determineIncrements(dag);

			if (cfg.getTails().size() == 1)
				placeInstruments(cfg, dag);
			else
				failSafePlaceInstruments(cfg, dag);

			// display
			// displayChordEdges();
			displaySpanningTree();
			// displayNodeDataHash(cfg);

			print_cfg(b);
			System.out.println("Exiting internalTransform");
		}
	}

	public void failSafePlaceInstruments(BriefUnitGraph cfg, DAG dag) {

	}

	public void placeInstruments(BriefUnitGraph cfg, DAG dag) {

		// initialize all edges
		allEdges.addAll(dag.original);

		// register initialization code
		Queue<Unit> WS = new LinkedList<Unit>();
		WS.add(ENTRY);

		while (!WS.isEmpty()) {
			Unit v = WS.remove();
			List<Unit> succ = cfg.getSuccsOf(v);
			// if (v == EXIT) {
			// succ.add(ENTRY);
			// }
			for (Unit w : succ) {
				MyEdge e = retriveEdge(v, w, dag);
				if (e.isContainedIn(chordEdges)) {
					String value = "r=" + inc.get(e);
					instrument.put(e, value);
				} // else if (w == ENTRY || cfg.getPredsOf(w).size() == 1) {
				else if (cfg.getPredsOf(w).size() == 1) {
					WS.add(w);
				} else {
					instrument.put(e, "r=0");
				}
			}
		}

		// memory increment code
		WS.add(EXIT);
		while (!WS.isEmpty()) {
			Unit w = WS.remove();
			List<Unit> pred = dag.getPredsOf(w);
			// if (w == ENTRY) {
			// pred.add(EXIT);
			// }
			for (Unit v : pred) {
				System.out.println(v + "^^^" + w);
				MyEdge e = retriveEdge(v, w, dag);
				if (e.isContainedIn(chordEdges)) {
					Integer inc_e = inc.get(e);
					if (instrument.get(e) != null && instrument.get(e).equals("r=" + inc_e)) {
						instrument.put(e, "count[" + inc_e + "]++");
					} else {
						instrument.put(e, "count[r+" + inc_e + "]++");
					}
				} // else if (v == EXIT || cfg.getSuccsOf(v).size() == 1) {
				else if (cfg.getSuccsOf(v).size() == 1) {
					WS.add(v);
				} else {
					instrument.put(e, "count[r]++");
				}
			}
		}

		Iterator it = instrument.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			MyEdge edge = (MyEdge) pair.getKey();
			String val = (String) pair.getValue();
			System.out.println("Instrument:" + edge.src + "************" + edge.tgt + "&&" + val);
		}
	}

	public MyEdge retriveEdge(Unit src, Unit tgt, DAG dag) {
		for (MyEdge e : dag.edges) {
			if (e.src == src && e.tgt == tgt)
				return e;
		}
		return null;
	}

	public void buildChordEdges(DAG cfg) {

		for (Unit unit : cfg.visited) {

			List<Unit> succs = cfg.getSuccsOf(unit);
			for (Unit succ : succs) {
				MyEdge chord = new MyEdge(unit, succ);
				if (!chord.isContainedIn(spanningTreeEdges)) {
					if (!chord.isContainedIn(chordEdges)) {
						chordEdges.add(chord);
					}
				}
			}
		}

		if (!spanningDummyBackedge) {
			// add dummy backedge
			NodeData nd = nodeDataHash.get(EXIT);
			nd.edgeVal.put(ENTRY, 0);
			chordEdges.add(new MyEdge(EXIT, ENTRY));
		}
	}

	public void determineIncrements(DAG cfg) {
		// initialize int inc variable
		for (MyEdge e : chordEdges) {
			inc.put(e, 0);
		}

		DFS(0, ENTRY, null);

		for (MyEdge e : chordEdges) {
			Integer i = inc.get(e) + Events(e);
			inc.put(e, i);
			System.out.println("Increment: " + e.src + "&&&&" + e.tgt + "&&&&&&" + i);
		}

		// if increments are there in the extra edges from exit(1) to EXIT
		for (MyEdge e : cfg.singleexit) {
			System.out.println(e.src + " " + e.tgt);
			List<Unit> pred = cfg.getPredsOf(e.src);
			System.out.println(pred.toString());
			for (Unit u : pred) {
				System.out.println(u.toString());
				Integer val = 0;
				if (inc.get(e) != null)
					val = inc.get(e) + inc.get(retriveEdge(u, e.src, cfg));
				inc.put(retriveEdge(u, e.src, cfg), val);
			}
		}
	}

	public void DFS(Integer events, Unit v, MyEdge e) {
		for (MyEdge f : spanningTreeEdges) {
			if (!(f.equals(e)) && (v == f.tgt)) {
				DFS(((Dir(e, f) * events) + Events(f)), f.src, f);
			}
		}
		for (MyEdge f : spanningTreeEdges) {
			if ((!(f.equals(e))) && (v == f.src)) {
				DFS(((Dir(e, f) * events) + Events(f)), f.tgt, f);
			}
		}
		for (MyEdge f : chordEdges) {
			if (v == f.src || v == f.tgt) {
				Integer value;
				value = inc.get(f) + (Dir(e, f) * events);
				inc.put(f, value);
			}
		}
	}

	public Integer Events(MyEdge e) {
		NodeData nd = nodeDataHash.get(e.src);
		return nd.edgeVal.get(e.tgt);
	}

	public Integer Dir(MyEdge e, MyEdge f) {
		if (e == null)
			return 1;
		else if ((e.src == f.tgt) || (e.tgt == f.src)) {
			return 1;
		} else {
			return -1;
		}
	}

	public void buildSpanningTree(DAG cfg) {
		DisjointSets disjointSet = new DisjointSets();

		// initialize
		for (Unit unit : cfg.visited) {
			disjointSet.create_set(unit);
		}

		if (spanningDummyBackedge) {
			disjointSet.union(ENTRY, EXIT);
			nodeDataHash.get(EXIT).succSpanningNode.add(ENTRY);
			spanningTreeEdges.add(new MyEdge(EXIT, ENTRY));
			nodeDataHash.get(EXIT).edgeVal.put(ENTRY, 0);
		}

		// add edges which ensure single exit to spanning tree
		for (MyEdge e : cfg.singleexit) {
			disjointSet.union(e.src, EXIT);
			nodeDataHash.get(e.src).succSpanningNode.add(EXIT);
			spanningTreeEdges.add(e);
			nodeDataHash.get(e.src).edgeVal.put(EXIT, 0);
		}

		int max;
		Unit ret = null;
		Unit max_unit = null, max_unitSucc = null;

		while (disjointSet.getNumberofDisjointSets() != 1) {
			max = Integer.MIN_VALUE;

			for (Unit unit : cfg.visited) {

				NodeData nd = nodeDataHash.get(unit);
				Iterator it = nd.edgeVal.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry pair = (Map.Entry) it.next();
					Unit cur_unit = (Unit) pair.getKey();
					int cur_val = (Integer) pair.getValue();
					// System.out.println(unit+"******************"+cur_unit+"&&"+cur_val);
					// System.out.print("&&&" + cur_val + max);
					if (disjointSet.find_set(unit) != disjointSet.find_set(cur_unit)) {
						// System.out.println("&&&" + cur_val + max);
						if (cur_val > max) {
							max = cur_val;
							max_unit = unit;
							max_unitSucc = cur_unit;
						}
					}
				}
			}
			disjointSet.union(max_unit, max_unitSucc);
			spanningTreeEdges.add(new MyEdge(max_unit, max_unitSucc));
			nodeDataHash.get(max_unit).succSpanningNode.add(max_unitSucc);
			// System.out.println("*" + max_unit + "**********" + max_unitSucc +
			// "-" + max);
		}
	}

	public void figure5(DAG cfg) {
		Queue<Unit> toProcess = new LinkedList<Unit>();
		// initialize queue
		toProcess.add(EXIT);
		while (!toProcess.isEmpty()) {
			Unit v = toProcess.remove();
			NodeData nd = nodeDataHash.get(v);
			if (cfg.getSuccsOf(v).isEmpty()) {
				nd.numPaths = 1;
			} else {
				nd.numPaths = 0;
				List<Unit> list_u = cfg.getSuccsOf(v);
				for (Unit w : list_u) {
					// Val (e) = numPaths(v)
					nd.updateEdgeVal(w, (nd.numPaths));
					nd.numPaths = nd.numPaths + nodeDataHash.get(w).numPaths;
				}
			}
			nodeDataHash.put(v, nd);
			toProcess.addAll(cfg.getPredsOf(v));
		}
	}

	public void displayNodeDataHash(BriefUnitGraph cfg) {
		System.out.println("**************Nodedatahash****************");
		Iterator it = nodeDataHash.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();

			Unit node = (Unit) pair.getKey();
			NodeData nodeData = (NodeData) pair.getValue();
			System.out.print(nodeData.getNodeNumber() + "*");
			System.out.println(" " + node);
			List<Unit> succNodes = cfg.getSuccsOf((Unit) pair.getKey());
			for (Unit succNode : succNodes) {
				NodeData nd = nodeDataHash.get(node);
				System.out.print("    " + succNode + "-" + nd.getEdgeVal(succNode) + ";" + nd.numPaths);
			}
			System.out.println();
			System.out.println("  " + nodeData.succSpanningNode);
		}
	}

	public void displayChordEdges() {
		System.out.println("&&&&&&&&&&&&&Chord Edges&&&&&&&&&&&&&&&&&");

		for (MyEdge e : chordEdges) {
			System.out.println("ChordEdge: " + e.src + " -> " + e.tgt);
		}

		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&");
	}

	public void displaySpanningTree() {
		System.out.println("&&&&&&&&&&&&&Spanning Tree&&&&&&&&&&&&&&&&&");

		for (MyEdge e : spanningTreeEdges) {
			System.out.println("SpanningEdge: " + e.src + " -> " + e.tgt);
		}

		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&");
	}

	public static void main(String[] args) {
		PathProfiler viewer = new PathProfiler();
		Transform printTransform = new Transform(phaseFullname, viewer);
		printTransform.setDeclaredOptions("enabled " + altClassPathOptionName + ' ' + graphTypeOptionName + ' '
				+ irOptionName + ' ' + multipageOptionName + ' ' + briefLabelOptionName + ' ');
		printTransform.setDefaultOptions("enabled " + altClassPathOptionName + ": " + graphTypeOptionName + ':'
				+ defaultGraph + ' ' + irOptionName + ':' + defaultIR + ' ' + multipageOptionName + ":false " + ' '
				+ briefLabelOptionName + ":false ");
		PackManager.v().getPack("jtp").add(printTransform);
		args = viewer.parse_options(args);
		System.out.println("in main");

		if (args.length == 0) {
			usage();
		} else {
			Scene.v().addBasicClass("xyz.arpith.pathprofiler.MyCounter");
			soot.Main.main(args);
		}
	}

	private static void usage() {
		G.v().out.println("Usage:\n"
				+ "   java soot.util.CFGViewer [soot options] [CFGViewer options] [class[:method]]...\n\n"
				+ "   CFGViewer options:\n" + "      (When specifying the value for an '=' option, you only\n"
				+ "       need to type enough characters to specify the choice\n"
				+ "       unambiguously, and case is ignored.)\n" + "\n" + "       --alt-classpath PATH :\n"
				+ "                specifies the classpath from which to load classes\n"
				+ "                that implement graph types whose names begin with 'Alt'.\n" + "       --graph={"
				+ CFGGraphType.help(0, 70, "                ".length()) + "} :\n"
				+ "                show the specified type of graph.\n" + "                Defaults to " + defaultGraph
				+ ".\n" + "       --ir={" + CFGIntermediateRep.help(0, 70, "                ".length()) + "} :\n"
				+ "                create the CFG from the specified intermediate\n"
				+ "                representation. Defaults to " + defaultIR + ".\n" + "       --brief :\n"
				+ "                label nodes with the unit or block index,\n"
				+ "                instead of the text of their statements.\n" + "       --multipages :\n"
				+ "                produce dot file output for multiple 8.5x11\" pages.\n"
				+ "                By default, a single page is produced.\n" + "       --help :\n"
				+ "                print this message.\n" + "\n"
				+ "   Particularly relevant soot options (see \"soot --help\" for details):\n"
				+ "       --soot-class-path PATH\n" + "       --show-exception-dests\n"
				+ "       --throw-analysis {pedantic|unit}\n" + "       --omit-excepting-unit-edges\n"
				+ "       --trim-cfgs\n");
	}

	/**
	 * Parse the command line arguments specific to CFGViewer, and convert them
	 * into phase options for jtp.printcfg.
	 *
	 * @return an array of arguments to pass on to Soot.Main.main().
	 */
	private String[] parse_options(String[] args) {
		List sootArgs = new ArrayList(args.length);

		for (int i = 0, n = args.length; i < n; i++) {
			if (args[i].equals("--alt-classpath") || args[i].equals("--alt-class-path")) {
				sootArgs.add("-p");
				sootArgs.add(phaseFullname);
				sootArgs.add(altClassPathOptionName + ':' + args[++i]);
			} else if (args[i].startsWith("--graph=")) {
				sootArgs.add("-p");
				sootArgs.add(phaseFullname);
				sootArgs.add(graphTypeOptionName + ':' + args[i].substring("--graph=".length()));
			} else if (args[i].startsWith("--ir=")) {
				sootArgs.add("-p");
				sootArgs.add(phaseFullname);
				sootArgs.add(irOptionName + ':' + args[i].substring("--ir=".length()));
			} else if (args[i].equals("--brief")) {
				sootArgs.add("-p");
				sootArgs.add(phaseFullname);
				sootArgs.add(briefLabelOptionName + ":true");
			} else if (args[i].equals("--multipages")) {
				sootArgs.add("-p");
				sootArgs.add(phaseFullname);
				sootArgs.add(multipageOptionName + ":true");
			} else if (args[i].equals("--help")) {
				return new String[0]; // This is a cheesy method to inveigle
				// our caller into printing the help
				// and exiting.
			} else if (args[i].equals("--soot-class-path") || args[i].equals("-soot-class-path")
					|| args[i].equals("--soot-classpath") || args[i].equals("-soot-classpath")) {
				// Pass classpaths without treating ":" as a method specifier.
				sootArgs.add(args[i]);
				sootArgs.add(args[++i]);
			} else if (args[i].equals("-p") || args[i].equals("--phase-option") || args[i].equals("-phase-option")) {
				// Pass phase options without treating ":" as a method
				// specifier.
				sootArgs.add(args[i]);
				sootArgs.add(args[++i]);
				sootArgs.add(args[++i]);
			} else {
				int smpos = args[i].indexOf('#');
				if (smpos == -1) {
					sootArgs.add(args[i]);
				} else {
					String clsname = args[i].substring(0, smpos);
					sootArgs.add(clsname);
					String methname = args[i].substring(smpos + 1);
					if (methodsToPrint == null) {
						methodsToPrint = new HashMap();
					}
					methodsToPrint.put(methname, clsname);
				}
			}
		}
		String[] sootArgsArray = new String[sootArgs.size()];
		return (String[]) sootArgs.toArray(sootArgsArray);
	}

	private void initialize(Map options) {
		if (drawer == null) {
			drawer = new CFGToDotGraph();
			drawer.setBriefLabels(PhaseOptions.getBoolean(options, briefLabelOptionName));
			drawer.setOnePage(!PhaseOptions.getBoolean(options, multipageOptionName));
			drawer.setUnexceptionalControlFlowAttr("color", "black");
			drawer.setExceptionalControlFlowAttr("color", "red");
			drawer.setExceptionEdgeAttr("color", "lightgray");
			drawer.setShowExceptions(Options.v().show_exception_dests());
			ir = CFGIntermediateRep.getIR(PhaseOptions.getString(options, irOptionName));
			graphtype = CFGGraphType.getGraphType(PhaseOptions.getString(options, graphTypeOptionName));

			AltClassLoader.v().setAltClassPath(PhaseOptions.getString(options, altClassPathOptionName));
			AltClassLoader.v()
					.setAltClasses(new String[] { "soot.toolkits.graph.ArrayRefBlockGraph", "soot.toolkits.graph.Block",
							"soot.toolkits.graph.Block$AllMapTo", "soot.toolkits.graph.BlockGraph",
							"soot.toolkits.graph.BriefBlockGraph", "soot.toolkits.graph.BriefUnitGraph",
							"soot.toolkits.graph.CompleteBlockGraph", "soot.toolkits.graph.CompleteUnitGraph",
							"soot.toolkits.graph.TrapUnitGraph", "soot.toolkits.graph.UnitGraph",
							"soot.toolkits.graph.ZonedBlockGraph", });
		}
	}

	protected void print_cfg(Body body) {
		DirectedGraph graph = graphtype.buildGraph(body);
		DotGraph canvas = graphtype.drawGraph(drawer, graph, body);
		String methodname = body.getMethod().getSubSignature();
		String filename = soot.SourceLocator.v().getOutputDir();
		if (filename.length() > 0) {
			filename = filename + java.io.File.separator;
		}
		filename = filename + methodname.replace(java.io.File.separatorChar, '.') + DotGraph.DOT_EXTENSION;

		G.v().out.println("Generate dot file in " + filename);
		canvas.plot(filename);

	}
}
