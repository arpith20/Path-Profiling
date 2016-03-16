package xyz.arpith.pathprofiler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
import soot.UnitBox;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.util.Chain;
import soot.util.cfgcmd.AltClassLoader;
import soot.util.cfgcmd.CFGGraphType;
import soot.util.cfgcmd.CFGIntermediateRep;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;

/**
 * A Efficient Java Path Profiler
 * 
 * Author: Arpith K
 * 
 * OS: Ubuntu 16.04 LTS (Beta 1)
 * 
 * IDE: Eclipse Neon Milestone 4 (4.6.0M5)
 * 
 * Java build 1.8.0_74-b02
 *
 * 
 */

// TODO: Handle backedge's instrumentation
// TODO: handle system.exit(0) cases
public class PathProfiler extends BodyTransformer {

	Unit ENTRY; // Entry node of the graph
	Unit EXIT; // Exit node of the graph
	boolean spanningDummyBackedge; // makes the backedge a part of spanning tree
	boolean useFailSafe = false; // use failsafe instrumentation technique.
									// see failSafePlaceInstruments()

	/*
	 * This allows the user to input a path sum for which the this returns the
	 * path taken by the program
	 */
	boolean regeneratePath = false;

	boolean placeInst = false; // Specify whether you want to modify the class
								// files. Use with caution.

	static boolean printToFile = false; // commenting such obvious things is an
	// overkill :D

	// Stores metadata (NodeData) for each unit in CFG
	HashMap<Unit, NodeData> nodeDataHash = new HashMap<Unit, NodeData>();

	// List of all edges that form a spanning tree
	List<MyEdge> spanningTreeEdges = new ArrayList<MyEdge>();

	// List of all edges not in spanning tree
	List<MyEdge> chordEdges = new ArrayList<MyEdge>();

	// Stores increment values for each edge
	HashMap<MyEdge, Integer> inc = new HashMap<MyEdge, Integer>();

	// Stores instrumentation data in a form that is easily human readable
	HashMap<MyEdge, String> instrument = new HashMap<MyEdge, String>();

	// Stores instrumentation data in a form that is used by this implementation
	HashMap<MyEdge, String> instrument_encoded = new HashMap<MyEdge, String>();

	/*
	 * Code from CFG Viewer. CFG ciewer generates a dot file in sootOutpot
	 * directory which can be used to graphically view the control flow graph of
	 * the given method
	 */
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
	private Map methodsToPrint;

	// This is required for instrumentation. Look at MyCounter.java
	SootClass counterClass = null;
	SootMethod increaseCounter, reportCounter, initializeCounter, setCountCounter;

	// This graph is responsible for converting the given CFG to DAG
	public class DAG {
		List<MyEdge> edges; // Stores all edges in DAG
		List<Unit> visited; // Stores visited units
		List<MyEdge> backedges; // Stores only backedges

		/*
		 * Stores artificial edges (Edges that were artificially from entry ->
		 * w; v->exit; here v->w is a backedge)
		 */
		HashMap<MyEdge, MyEdge> artificial;
		List<MyEdge> artificial_list; // stores same data as above, except the
										// actual backedge
		List<MyEdge> original;// Stores original edges in CFG

		/*
		 * This stores edges that is responsible for converting the given CFG
		 * with multiple exits to a DAG with a unique EXIT node
		 */
		List<MyEdge> singleexit;// This stores edges that is responsible for
								// converting the

		// Initialize above data members of DAG class
		DAG() {
			edges = new ArrayList<MyEdge>();
			visited = new ArrayList<Unit>();
			backedges = new ArrayList<MyEdge>();
			artificial = new HashMap<MyEdge, MyEdge>();
			artificial_list = new ArrayList<MyEdge>();
			original = new ArrayList<MyEdge>();
			singleexit = new ArrayList<MyEdge>();
		}

		/*
		 * Returns the predecessor of a unit u
		 */
		public List<Unit> getPredsOf(Unit u) {
			List<Unit> pred = new ArrayList<Unit>();
			for (MyEdge e : edges) {
				if (e.tgt == u)
					pred.add(e.src);
			}
			return pred;
		}

		/*
		 * Returns the successor of a unit u
		 */
		public List<Unit> getSuccsOf(Unit u) {
			List<Unit> succ = new ArrayList<Unit>();
			for (MyEdge e : edges) {
				if (e.src == u)
					succ.add(e.tgt);
			}
			return succ;
		}

		/*
		 * Checks whether adding an edge from src to tgt forms a cycle True ->
		 * Forms a cycle False -> Does not create a cycle
		 */
		public boolean formsCycle(Unit src, Unit tgt, BriefUnitGraph cfg) {
			Queue<Unit> queue = new LinkedList();
			queue.add(tgt);
			while (!queue.isEmpty()) {
				Unit u = queue.remove();
				List<Unit> succs = getSuccsOf(u);
				for (Unit succ : succs) {
					if (src == succ)
						return true;
					queue.add(succ);
				}
			}
			return false;
		}

		/*
		 * Converts CFG to DAG
		 */
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
					if (formsCycle(unit, succ, cfg)) {
						// '*' helps me identify that the given method contains
						// a
						// backedge
						System.out.println("*");
						// System.out.println(unit + " " + succ);

						/*
						 * To use alternate method, comment the following lines
						 * and uncomment the other
						 */
						if (retriveEdge(unit, succ, this) == null) {
							MyEdge back = new MyEdge(unit, succ);
							backedges.add(back);
							// instrument.put(back, "r=0; count[r]++; ");
							// instrument_encoded.put(back, "count:r:0");
							// instrument_encoded.put(back, "ini:0");

							if (retriveEdge(ENTRY, succ, this) == null) {
								MyEdge ent = new MyEdge(ENTRY, succ);
								edges.add(ent);
								artificial.put(ent, back);
								artificial_list.add(ent);
							}

							if (retriveEdge(unit, EXIT, this) == null) {
								MyEdge ext = new MyEdge(unit, EXIT);
								edges.add(ext);
								artificial.put(ext, back);
								artificial_list.add(ext);
							}
						}

						// legacy code. NOT used
						useFailSafe = true;

						/*
						 * legacy code. Kept it; just in case
						 */
						// // handle backedge
						// Queue<Unit> queue = new LinkedList();
						// MyEdge e = null;
						// queue.add(succ);
						// while (!queue.isEmpty()) {
						// Unit u = queue.remove();
						// List<Unit> alt_succs = cfg.getSuccsOf(u);
						// for (Unit alt_succ : alt_succs) {
						// queue.add(alt_succ);
						// if (!formsCycle(unit, alt_succ, cfg)) {
						// if (retriveEdge(unit, alt_succ, this) == null) {
						// e = new MyEdge(unit, alt_succ);
						// edges.add(e);
						// backedges.add(e);
						//
						// queue.clear();
						// break;
						// } else {
						// queue.clear();
						// break;
						// }
						// }
						// }
						// }
						// if (e == null)
						// System.out.println("Error!!");
					} else {
						if (retriveEdge(unit, succ, this) == null) {
							MyEdge e = new MyEdge(unit, succ);
							edges.add(e);
							original.add(e);
						}
					}
				}
			}
		}

	}

	/*
	 * A class to represent each edge
	 */
	public class MyEdge {
		Unit src; // Source vertex of a directed edge
		Unit tgt;// Sink/target of a directed edge

		// Constructor to initialize the above data members
		public MyEdge(Unit u1, Unit u2) {
			src = u1;
			tgt = u2;
		}

		/*
		 * Returns true if the MyEdge object calling the function is equal to e2
		 */
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

		/*
		 * Returns true if the MyEdge object calling the function is present in
		 * list l
		 */
		public boolean isContainedIn(List<MyEdge> l) {
			for (MyEdge e : l) {
				if (this.equals(e))
					return true;
			}
			return false;
		}
	}

	/*
	 * Every node has a metadata associated with it. I call this metadata,
	 * NodeData
	 */
	public class NodeData {
		public int nodeNumber; // a unique number assigned to wach node
		public int numPaths; // NumPaths (v) as defined in paper; Figure 5
		public HashMap<Unit, Integer> edgeVal; // Contains data pertaining to
												// the outgoing edges of a node
		// Val (e) as defined in paper; Figure 5

		/*
		 * Stores the spanninng tree node. Edge: this->succSpanningNode
		 */
		public List<Unit> succSpanningNode;

		// Constructor to initialize the members
		NodeData(int val) {
			nodeNumber = val;
			numPaths = 0;
			edgeVal = new HashMap<Unit, Integer>();
			succSpanningNode = new ArrayList<Unit>();
		}

		// returns a unique number for the given node
		public int getNodeNumber() {
			return nodeNumber;
		}

		// use this to modify edgeVal
		public void updateEdgeVal(Unit unit, Integer i) {
			edgeVal.put(unit, i);
		}

		// returns edgeval from a given node
		public int getEdgeVal(Unit unit) {
			return edgeVal.get(unit);
		}
	}

	/*
	 * !!!This function is NOT used!!! This function was initially used to test
	 * the correctness of the results obtained. The CF that the following
	 * finction generates is same as the one given in the Ball Larus paper
	 */
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
		// initialize various mapping required for CFGViewer
		initialize(options);

		synchronized (this) {
			/*
			 * Initialize the counter class. The counter class 'MyCounter' is
			 * responsible for keeping track of path sums
			 */
			if (counterClass == null) {
				counterClass = Scene.v().loadClassAndSupport("xyz.arpith.pathprofiler.MyCounter");
				increaseCounter = counterClass.getMethod("void increase(java.lang.String)");
				initializeCounter = counterClass.getMethod("void initialize(java.lang.String)");
				setCountCounter = counterClass.getMethod("void setCount(java.lang.String)");
				reportCounter = counterClass.getMethod("void report()");
			}

			// method currently under observation
			SootMethod meth = b.getMethod();

			/*
			 * Instrumentation to display path sums and path counts when the
			 * execution ends
			 */
			String signature = meth.getSubSignature();

			boolean check = signature.equals("void main(java.lang.String[])");
			if (check) {
				Chain units = b.getUnits();
				Iterator stmtIt = units.snapshotIterator();

				while (stmtIt.hasNext()) {
					Stmt stmt = (Stmt) stmtIt.next();

					// check if the instruction is a return with/without value
					if ((stmt instanceof ReturnStmt) || (stmt instanceof ReturnVoidStmt)) {

						InvokeExpr reportExpr = Jimple.v().newStaticInvokeExpr(reportCounter.makeRef());
						Stmt reportStmt = Jimple.v().newInvokeStmt(reportExpr);
						units.insertBefore(reportStmt, stmt);
					}
				}
			}

			/*
			 * Do not instrument or analyze void <init>()
			 */
			check = signature.contains("void <init");
			if (check)
				return;

			/*
			 * This analyzes the methods given as command line argument. If this
			 * is not given by the user, than analyze the whole class
			 */

			if ((methodsToPrint == null)
					|| (meth.getDeclaringClass().getName() == methodsToPrint.get(meth.getName()))) {
				Body body = ir.getBody((JimpleBody) b);

				// Prints the IR of the method under observation
				// System.out.println("This is the IR: \n" + body.toString());
				BriefUnitGraph cfg = new BriefUnitGraph(b);

				// initializations
				ENTRY = cfg.getHeads().get(0);
				EXIT = cfg.getTails().get(0);

				/*
				 * The following ignores methods with multiple end points as it
				 * is not considered in the paper.
				 * 
				 * However, my code supports multiple EXITS with the addition of
				 * failSafePlaceInstruments function.
				 * 
				 * Just comment the following code if you wish to analyze such
				 * methods.
				 */
				if (cfg.getTails().size() > 1)
					return;

				// display signature of method being analyzed
				System.out.println();
				System.out.println("====================================");
				System.out.println("Signature: " + meth.getSignature());
				System.out.println("------------------------------------");

				// add a dummy backedge from EXIT to ENTRY
				spanningDummyBackedge = true;

				// build DAG
				DAG dag = new DAG();
				dag.buildDAG(cfg);

				// for initial stage testing
				// fabricatedata(cfg);

				// iterate through all nodes and initialize them correctly
				int i = 0;
				for (Unit unit : dag.visited) {
					NodeData node = new NodeData(i++);

					Iterator<Unit> succ_iterator = dag.getSuccsOf(unit).iterator();
					while (succ_iterator.hasNext()) {
						// initialize edgeValue count to 0
						node.updateEdgeVal(succ_iterator.next(), 0);
					}
					nodeDataHash.put(unit, node);
				}

				// assign values to edges in DAG (Algo in Figure 5 in paper)
				assignVals(dag);

				// name says it all :)
				buildSpanningTree(dag);

				// buile chord edges
				buildChordEdges(dag);

				// determine increments from each chord edges
				// (Edges not in spanning tree)
				determineIncrements(dag);

				/*
				 * The paper assumes the existence of only one EXIT vertex. The
				 * algorithm proposed in the paper fails if there are multiple
				 * EXIT nodes. Real world programs might contain multiple EXIT
				 * nodes. In such case use a custom failsafe algorithm.
				 * 
				 * Also note that these functions only determine which edges
				 * need to be instrumented with what. It does not place the
				 * instruments in class. The task is done by
				 * placeInstrumentsToClass
				 * 
				 */
				if (cfg.getTails().size() != 1)
					failSafePlaceInstruments(cfg, dag);
				else
					placeInstruments(cfg, dag);

				/*
				 * This is responsible for instrumenting the class files
				 */
				if (placeInst)
					placeInstrumentsToClass(body, cfg);

				/*
				 * Various display options. Use as required
				 */
				// displayChordEdges();
				// displaySpanningTree();
				// displayEdges(dag);
				//
				// displayNodeDataHash(dag);

				/*
				 * Saves the CFG to sootOutput file
				 */
				print_cfg(b);

				/*
				 * Given a path sum, which the user has to input,
				 * regeneratePathFunc returns that path taken the the program
				 * (in the function under observation).
				 */
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				if (regeneratePath) {
					System.out.print("Enter count");
					try {
						String s = br.readLine();
						Integer path_value = Integer.parseInt(s);
						regeneratePathFunc(path_value, dag);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				System.out.println("Exiting internalTransform");

				// Clear data for use by other threads
				nodeDataHash.clear();
				spanningTreeEdges.clear();
				chordEdges.clear();
				inc.clear();
				instrument.clear();
				instrument_encoded.clear();
			}
		}
	}

	/*
	 * Given a path sum, which the user has to input, regeneratePathFunc returns
	 * that path taken the the program (in the function under observation).
	 */
	public void regeneratePathFunc(Integer pathval, DAG dag) {
		Integer val = pathval;
		Queue<Unit> queue = new LinkedList();

		System.out.println("regenerating path");

		List<Unit> allnodes = new ArrayList<Unit>();
		allnodes.addAll(dag.visited);

		queue.add(ENTRY);
		while (!queue.isEmpty()) {
			Unit cur = queue.remove();
			NodeData nd = nodeDataHash.get(cur);
			Integer max = Integer.MIN_VALUE;
			Unit next = null;
			for (Unit u : dag.getSuccsOf(cur)) {
				Integer edgeval = nd.edgeVal.get(u);
				if (edgeval > max) {
					if (edgeval <= val) {
						max = edgeval;
						next = u;
					}
				}
			}
			if (next != null) {
				queue.add(next);

				MyEdge e = retriveEdge(cur, next, dag);
				System.out.println("RegereratePath: " + e.src + " --> " + e.tgt);

				val = val - max;
				next = null;
			}
		}

	}

	/*
	 * Displays the DAD edges to Console
	 */
	public void displayEdges(DAG dag) {
		System.out.println("&&&&&&&&&&&&&DAG Edges&&&&&&&&&&&&&&&&&");

		for (MyEdge e : dag.edges) {
			System.out.println("Edge: " + e.src + " -> " + e.tgt);
		}

		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&");
	}

	/*
	 * This is responsible for instrumenting the class files
	 */
	public void placeInstrumentsToClass(Body body, BriefUnitGraph cfg) {

		Iterator it = instrument_encoded.entrySet().iterator();
		while (it.hasNext()) {
			// pair: (Edge, Instrumentation(encoded format))
			Map.Entry pair = (Map.Entry) it.next();

			// extract edge
			MyEdge edge = (MyEdge) pair.getKey();

			// extract instrumentation
			String val = (String) pair.getValue();

			// add method information to the instrumentation
			// This information is then used by the counter
			val = val + "#" + body.getMethod().getSignature();

			String[] tokens = val.split(":");

			// Format: backedge#ini:0#count:r:x#signature
			if (tokens[0].contains("backedge")) {
				String[] tokens_be = val.split("#");

				String val_ini = null, val_count = null;

				if (tokens_be[1].contains("ini")) {
					val_ini = tokens_be[1] + "#" + body.getMethod().getSignature();
				} else if (tokens_be[2].contains("ini")) {
					val_ini = tokens_be[2] + "#" + body.getMethod().getSignature();
				}

				if (tokens_be[1].contains("count")) {
					val_count = tokens_be[1] + "#" + body.getMethod().getSignature();
				} else if (tokens_be[2].contains("count")) {
					val_count = tokens_be[2] + "#" + body.getMethod().getSignature();
				}

				if (val_ini == null && val_count == null)
					throw new Error("How did I get here!? Backedge contains no instrumentable data");
				else if (val_ini != null && val_count == null) {
					// set only r=0
					List<Unit> succ_toProcess = new ArrayList<Unit>();
					succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

					boolean set = false;

					System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

					List<UnitBox> u_boxes = edge.src.getUnitBoxes();
					for (UnitBox u_box : u_boxes) {
						succ_toProcess.remove(u_box.getUnit());
						if (u_box.getUnit() == edge.tgt) {

							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
									StringConstant.v(val_ini));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, EXIT);

							body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

							u_box.setUnit(incStmt);

							set = true;
						}
					}

					if (set == false) {
						if (succ_toProcess.get(0) == edge.tgt) {
							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
									StringConstant.v(val_ini));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, edge.src);

							succ_toProcess.clear();
						} else {
							throw new Error("Something is wrong with the logic of placing instrumentation");
						}
					}
					succ_toProcess.clear();
				} else if (val_ini == null && val_count != null) {
					// set only count[r+x]++
					List<Unit> succ_toProcess = new ArrayList<Unit>();
					succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

					boolean set = false;

					System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

					List<UnitBox> u_boxes = edge.src.getUnitBoxes();
					for (UnitBox u_box : u_boxes) {
						succ_toProcess.remove(u_box.getUnit());
						if (u_box.getUnit() == edge.tgt) {

							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
									StringConstant.v(val_count));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, EXIT);

							body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

							u_box.setUnit(incStmt);

							set = true;
						}
					}

					if (set == false) {
						if (succ_toProcess.get(0) == edge.tgt) {
							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
									StringConstant.v(val_count));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, edge.src);

							succ_toProcess.clear();
						} else {
							throw new Error("Something is wrong with the logic of placing instrumentation");
						}
					}
					succ_toProcess.clear();
				} else if (val_ini != null && val_count != null) {
					// set r=0; count[r+x]++
					List<Unit> succ_toProcess = new ArrayList<Unit>();
					succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

					boolean set = false;

					System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

					List<UnitBox> u_boxes = edge.src.getUnitBoxes();
					for (UnitBox u_box : u_boxes) {
						succ_toProcess.remove(u_box.getUnit());
						if (u_box.getUnit() == edge.tgt) {

							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
									StringConstant.v(val_ini));
							Stmt incStmt0 = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt0, EXIT);

							incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
									StringConstant.v(val_count));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, incStmt0);

							body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

							u_box.setUnit(incStmt0);

							set = true;
						}
					}

					if (set == false) {
						if (succ_toProcess.get(0) == edge.tgt) {
							InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
									StringConstant.v(val_ini));
							Stmt incStmt0 = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt0, edge.src);

							incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
									StringConstant.v(val_count));
							Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
							body.getUnits().insertAfter(incStmt, incStmt0);

							succ_toProcess.clear();
						} else {
							throw new Error("Something is wrong with the logic of placing instrumentation");
						}
					}
					succ_toProcess.clear();
				}
			}

			if (tokens[0].equals("ini")) {
				List<Unit> succ_toProcess = new ArrayList<Unit>();
				succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

				boolean set = false;

				System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

				List<UnitBox> u_boxes = edge.src.getUnitBoxes();
				for (UnitBox u_box : u_boxes) {
					succ_toProcess.remove(u_box.getUnit());
					if (u_box.getUnit() == edge.tgt) {

						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, EXIT);

						body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

						u_box.setUnit(incStmt);

						set = true;
					}
				}

				if (set == false) {
					if (succ_toProcess.get(0) == edge.tgt) {
						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(initializeCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, edge.src);

						succ_toProcess.clear();
					} else {
						throw new Error("Something is wrong with the logic of placing instrumentation");
					}
				}
				succ_toProcess.clear();
			} else if (tokens[0].equals("inc")) {
				List<Unit> succ_toProcess = new ArrayList<Unit>();
				succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

				boolean set = false;

				System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

				List<UnitBox> u_boxes = edge.src.getUnitBoxes();
				for (UnitBox u_box : u_boxes) {
					succ_toProcess.remove(u_box.getUnit());
					if (u_box.getUnit() == edge.tgt) {

						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(increaseCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, EXIT);

						body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

						u_box.setUnit(incStmt);

						succ_toProcess.clear();

						set = true;
					}
				}

				if (set == false) {
					if (succ_toProcess.get(0) == edge.tgt) {
						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(increaseCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, edge.src);

						succ_toProcess.clear();
					} else {
						throw new Error("Something is wrong with the logic of placing instrumentation");
					}
				}
				succ_toProcess.clear();
			} else if (tokens[0].equals("count")) {
				List<Unit> succ_toProcess = new ArrayList<Unit>();
				succ_toProcess.addAll(cfg.getSuccsOf(edge.src));

				boolean set = false;

				System.out.println("Instrument_toClass:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);

				List<UnitBox> u_boxes = edge.src.getUnitBoxes();
				for (UnitBox u_box : u_boxes) {
					succ_toProcess.remove(u_box.getUnit());
					if (u_box.getUnit() == edge.tgt) {

						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, EXIT);

						body.getUnits().insertAfter(Jimple.v().newGotoStmt(edge.tgt), incStmt);

						u_box.setUnit(incStmt);

						succ_toProcess.clear();

						set = true;
					}
				}

				if (set == false) {
					if (succ_toProcess.get(0) == edge.tgt) {
						InvokeExpr incExpr = Jimple.v().newStaticInvokeExpr(setCountCounter.makeRef(),
								StringConstant.v(val));
						Stmt incStmt = Jimple.v().newInvokeStmt(incExpr);
						body.getUnits().insertAfter(incStmt, edge.src);

						succ_toProcess.clear();
					} else {
						throw new Error("Something is wrong with the logic of placing instrumentation");
					}
				}
				succ_toProcess.clear();
			}
		}
	}

	/*
	 * The paper assumes the existence of only one EXIT vertex. The algorithm
	 * proposed in the paper fails if there are multiple EXIT nodes. Real world
	 * programs might contain multiple EXIT nodes. In such case I use a custom
	 * failsafe algorithm to place instruments
	 * 
	 * Also note that this function only determine which edges need to be
	 * instrumented with what. It does not place the instruments in class. The
	 * task is done by placeInstrumentsToClass
	 * 
	 */
	public void failSafePlaceInstruments(BriefUnitGraph cfg, DAG dag) {
		List<MyEdge> remaining = new ArrayList<MyEdge>();
		remaining.addAll(dag.edges);
		for (Unit tgt : cfg.getTails()) {
			for (Unit src : cfg.getPredsOf(tgt)) {
				MyEdge e = retriveEdge(src, tgt, dag);
				Integer val = inc.get(e);
				if (val != null) {
					instrument.put(e, "count[r+" + val + "]++");
					instrument_encoded.put(e, "count:r:" + val);
				} else {
					instrument.put(e, "count[r]++");
					instrument_encoded.put(e, "count:r:" + 0);
				}
				remaining.remove(e);
			}
		}
		for (MyEdge e : remaining) {
			Integer val = inc.get(e);
			if (val != null) {
				instrument.put(e, "r=r+" + val);
				instrument_encoded.put(e, "inc:" + val);
			}
		}

		Iterator it = instrument.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			MyEdge edge = (MyEdge) pair.getKey();
			String val = (String) pair.getValue();
			System.out.println("Instrument_fs:" + edge.src + "  -- >  " + edge.tgt + " *** " + val);
		}
	}

	/*
	 * Determined what instrumentation needs to be placed.
	 * 
	 * Also note that this function only determine which edges need to be
	 * instrumented with what. It does not place the instruments in class. The
	 * task is done by placeInstrumentsToClass
	 */
	public void placeInstruments(BriefUnitGraph cfg, DAG dag) {

		// register initialization code
		Queue<Unit> WS = new LinkedList<Unit>();
		WS.add(ENTRY);

		while (!WS.isEmpty()) {
			Unit v = WS.remove();
			List<Unit> succ = dag.getSuccsOf(v);
			// if (v == EXIT) {
			// succ.add(ENTRY);
			// }
			for (Unit w : succ) {
				MyEdge e = retriveEdge(v, w, dag);
				if (e.isContainedIn(chordEdges)) {
					String value = "r=" + inc.get(e);
					instrument.put(e, value);
					instrument_encoded.put(e, "ini:" + inc.get(e));
				} // else if (w == ENTRY || cfg.getPredsOf(w).size() == 1) {
				else if (dag.getPredsOf(w).size() == 1) {
					WS.add(w);
				} else {
					instrument.put(e, "r=0");
					instrument_encoded.put(e, "ini:" + 0);
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
				MyEdge e = retriveEdge(v, w, dag);
				if (e.isContainedIn(chordEdges)) {
					Integer inc_e = inc.get(e);
					if (instrument.get(e) != null && instrument.get(e).equals("r=" + inc_e)) {
						instrument.put(e, "count[" + inc_e + "]++");
						instrument_encoded.put(e, "count:x:" + inc.get(e));
					} else {
						instrument.put(e, "count[r+" + inc_e + "]++");
						instrument_encoded.put(e, "count:r:" + inc.get(e));
					}
				} // else if (v == EXIT || cfg.getSuccsOf(v).size() == 1) {
				else if (dag.getSuccsOf(v).size() == 1) {
					WS.add(v);
				} else {
					instrument.put(e, "count[r]++");
					instrument_encoded.put(e, "count:r:0");
				}
			}
		}

		// register increment code
		for (MyEdge c : chordEdges) {
			if (instrument.get(c) == null) {
				instrument.put(c, "r=r+" + inc.get(c));
				instrument_encoded.put(c, "inc:" + inc.get(c));
			}
		}

		Iterator it = instrument.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			MyEdge edge = (MyEdge) pair.getKey();
			String val = (String) pair.getValue();
			if (edge.isContainedIn(dag.artificial_list)) {
				MyEdge backedge = dag.artificial.get(edge);
				String instrumentation = instrument_encoded.get(edge);
				instrument_encoded.remove(edge);

				// new_instrumentation is the instrumentation to be placed in
				// backedge
				// Format: backedge#ini:0#count:r:x
				String new_instrumentation = instrument_encoded.get(backedge);
				if (new_instrumentation == null) {
					new_instrumentation = "backedge";
				}
				new_instrumentation += "#" + instrumentation;
				instrument_encoded.put(backedge, new_instrumentation);

			}
			System.out.println("Instrument:" + edge.src + " -- > " + edge.tgt + " *** " + val);
		}
	}

	/*
	 * Given a source and target nodes, it returns a unique edge with this nodes
	 * as it's source and target
	 */
	public MyEdge retriveEdge(Unit src, Unit tgt, DAG dag) {
		for (MyEdge e : dag.edges) {
			if (e.src == src && e.tgt == tgt)
				return e;
		}
		return null;
	}

	/*
	 * Determines edges that are not in spanning tree
	 */
	public void buildChordEdges(DAG cfg) {

		for (Unit unit : cfg.visited) {

			List<Unit> succs = cfg.getSuccsOf(unit);
			for (Unit succ : succs) {
				MyEdge chord = retriveEdge(unit, succ, cfg);
				if (!chord.isContainedIn(spanningTreeEdges)) {
					if (!chord.isContainedIn(chordEdges)) {
						chordEdges.add(chord);
					}
				}
			}
		}
	}

	/*
	 * Determines increments from each chord edge
	 */
	public void determineIncrements(DAG cfg) {
		// initialize int inc variable
		for (MyEdge e : chordEdges) {
			inc.put(e, 0);
		}

		DFS(0, ENTRY, null);

		for (MyEdge e : chordEdges) {
			Integer i = inc.get(e) + Events(e);
			inc.put(e, i);
			// System.out.println("Increment: " + e.src + "&&&&" + e.tgt +
			// "&&&&&&" + i);
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

	/*
	 * Builds Spanning tree
	 */
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
			spanningTreeEdges.add(retriveEdge(max_unit, max_unitSucc, cfg));
			nodeDataHash.get(max_unit).succSpanningNode.add(max_unitSucc);
			// System.out.println("*" + max_unit + "**********" + max_unitSucc +
			// "-" + max);
		}
	}

	/*
	 * assign values to edges in DAG (Algo in Figure 5)
	 */
	public void assignVals(DAG cfg) {
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

	/*
	 * Display node metadata
	 */
	public void displayNodeDataHash(DAG cfg) {
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

	/*
	 * display all DAG edges in graph
	 */
	public void displayChordEdges() {
		System.out.println("&&&&&&&&&&&&&Chord Edges&&&&&&&&&&&&&&&&&");

		for (MyEdge e : chordEdges) {
			System.out.println("ChordEdge: " + e.src + " -> " + e.tgt);
		}

		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&");
	}

	/*
	 * Display Spanning tree
	 */
	public void displaySpanningTree() {
		System.out.println("&&&&&&&&&&&&&Spanning Tree&&&&&&&&&&&&&&&&&");

		for (MyEdge e : spanningTreeEdges) {
			System.out.println("SpanningEdge: " + e.src + " -> " + e.tgt);
		}

		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&");
	}

	public static void main(String[] args) throws FileNotFoundException {

		if (printToFile) {
			PrintStream printStream = new PrintStream(new FileOutputStream("output.txt"));
			System.setOut(printStream);
		}
		PathProfiler profiler = new PathProfiler();
		Transform printTransform = new Transform(phaseFullname, profiler);

		// for CFGViewer
		printTransform.setDeclaredOptions("enabled " + altClassPathOptionName + ' ' + graphTypeOptionName + ' '
				+ irOptionName + ' ' + multipageOptionName + ' ' + briefLabelOptionName + ' ');
		printTransform.setDefaultOptions("enabled " + altClassPathOptionName + ": " + graphTypeOptionName + ':'
				+ defaultGraph + ' ' + irOptionName + ':' + defaultIR + ' ' + multipageOptionName + ":false " + ' '
				+ briefLabelOptionName + ":false ");
		PackManager.v().getPack("jtp").add(printTransform);

		// Get arguments from user
		args = profiler.parse_options(args);

		// A random print statement
		System.out.println("in main");

		// if the user does not enter any arguments, print the usage
		if (args.length == 0) {
			usage();
		} else {

			/*
			 * Required for functioning of MyCounter.java
			 */
			Scene.v().addBasicClass("xyz.arpith.pathprofiler.MyCounter");
			Scene.v().addBasicClass("java.util.HashMap");
			Scene.v().addBasicClass("java.util.HashMap$Node");
			Scene.v().addBasicClass("java.util.function.Function");
			Scene.v().addBasicClass("java.util.function.BiFunction");
			Scene.v().addBasicClass("java.util.function.BiConsumer");
			Scene.v().addBasicClass("java.util.HashMap$TreeNode");
			Scene.v().addBasicClass("java.lang.invoke.SerializedLambda");
			Scene.v().addBasicClass("java.util.function.Predicate");
			Scene.v().addBasicClass("java.util.stream.Stream");
			Scene.v().addBasicClass("java.util.Iterator");
			Scene.v().addBasicClass("java.util.function.Consumer");
			Scene.v().addBasicClass("java.io.OutputStreamWriter");
			Scene.v().addBasicClass("java.io.BufferedWriter");
			Scene.v().addBasicClass("java.io.FileNotFoundException");
			Scene.v().addBasicClass("java.util.Formatter");

			// Start analysis
			soot.Main.main(args);
		}
	}

	private static void usage() {
		G.v().out.println("Input arguments. ToDo: write usage here");
	}

	/*
	 * Parse the command line arguments
	 */
	private String[] parse_options(String[] args) {
		List sootArgs = new ArrayList(args.length);

		for (int i = 0, n = args.length; i < n; i++) {

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
		String[] sootArgsArray = new String[sootArgs.size()];
		return (String[]) sootArgs.toArray(sootArgsArray);
	}

	/*
	 * All initializations pertaining to CFG Viewer is done here
	 */
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

	/*
	 * Prints the CFG to do file. This file is placed in sootOutput
	 */
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