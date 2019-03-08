package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.CoveredLine;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.ContextIdentifier;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;
import com.github.thwak.confix.util.IndexMap;

public class PatchStrategy {

	protected Random r;
	protected CoverageManager manager;
	protected ChangePool pool;
	protected ContextIdentifier collector;
	protected List<LocEntry> locations;
	protected IndexMap<CoveredLine> coveredLines;
	protected Map<Integer, List<LocEntry>> lineLocMap;
	protected int currLocIndex = 0;
	protected int currLineIndex = -1;
	protected String cStrategyKey;
	protected String flMetric;
	protected Map<String, Patcher> patcherMap;
	protected String sourceDir;
	protected String[] compileClassPathEntries;
	protected int fixLocCount = 0;
	protected StringBuffer sbLoc = new StringBuffer("LocKind$$Loc$$Class#Line:Freq:Score");

	protected PatchStrategy() {
		super();
	}

	public PatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector) {
		this(manager, pool, collector, new Random());
	}

	public PatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r) {
		this.r = r;
		this.manager = manager;
		this.pool = pool;
		this.collector = collector;
		this.locations = new ArrayList<>();
		coveredLines = new IndexMap<>();
		lineLocMap = new HashMap<>();
		patcherMap = new HashMap<>();
	}

	public PatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
			String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
		this.r = r;
		this.manager = manager;
		this.pool = pool;
		this.collector = collector;
		this.locations = new ArrayList<>();
		coveredLines = new IndexMap<>();
		lineLocMap = new HashMap<>();
		patcherMap = new HashMap<>();
		this.flMetric = flMetric;
		this.cStrategyKey = cStrategyKey;
		this.sourceDir = sourceDir;
		this.compileClassPathEntries = compileClassPathEntries;
		prioritizeCoveredLines();
	}

	protected void prioritizeCoveredLines() {
		List<CoveredLine> lines = this.manager.computeScore(flMetric);
		for(CoveredLine cl : lines) {
			if(Double.compare(cl.score, 0.0000d) > 0) {
				coveredLines.add(cl);
			}
		}
	}

	public String getLineInfo() {
		StringBuffer sb = new StringBuffer();
		for(CoveredLine cl : coveredLines.values()) {
			sb.append(cl.toString());
			sb.append("\n");
		}
		return sb.toString();
	}

	public int getCurrentLineIndex() {
		return currLineIndex;
	}

	public CoveredLine getCurrentLine() {
		return currLineIndex < coveredLines.size() ? coveredLines.get(currLineIndex) : null;
	}

	public int getCurrentLocIndex() {
		return currLocIndex;
	}

	public LocEntry getCurrentLoc() {
		return currLocIndex < locations.size() ? locations.get(currLocIndex) : null;
	}

	public String getCurrentLocInfo() {
		LocEntry e = getCurrentLoc();
		if(e == null)
			return "";
		StringBuffer sb = new StringBuffer();
		sb.append(e.loc.getTypeName());
		sb.append("$$");
		sb.append(e.loc.node.label);
		sb.append("$$");
		sb.append(e.loc.className);
		sb.append("#");
		sb.append(e.loc.node.startLine);
		sb.append(":");
		sb.append(e.freq);
		sb.append(":");
		sb.append(String.format("%1.4f", e.score));
		return sb.toString();
	}

	public int getFixLocCount() {
		return fixLocCount;
	}

	public int getFixLocCount(String className, int line) {
		List<LocEntry> locations = getLocations(className, line);
		return locations != null ? locations.size() : 0;
	}

	protected List<LocEntry> getLocations(String className, int line) {
		CoveredLine cl = new CoveredLine(className, line);
		int lineIdx = coveredLines.getIndex(cl);
		List<LocEntry> locations = lineLocMap.get(lineIdx);
		return locations;
	}

	public TargetLocation getFixLoc(String className, int line, int index) {
		List<LocEntry> locations = getLocations(className, line);
		return locations != null && index < locations.size() ? locations.get(index).loc : null;
	}

	public List<TargetLocation> getFixLocs(String className, int line) {
		List<LocEntry> locations = getLocations(className, line);
		List<TargetLocation> locs = new ArrayList<>();
		for(LocEntry e : locations)
			locs.add(e.loc);
		return locs;
	}

	public ContextIdentifier collector() {
		return collector;
	}

	public boolean inRange(String className, Node n){
		return coveredLines.contains(new CoveredLine(className, n.startLine));
	}

	public boolean isTarget(TargetLocation loc){
		return pool.changeIterator(loc.context).hasNext();
	}

	public void updateLocations(String className, Node root, FixLocationIdentifier identifier) {
		List<TargetLocation> fixLocs = new ArrayList<>();
		identifier.findLocations(className, root, fixLocs);
		fixLocCount += fixLocs.size();
		for(TargetLocation loc : fixLocs) {
			CoveredLine cl = new CoveredLine(className, loc.node.startLine);
			int index = coveredLines.getIndex(cl);
			if(!lineLocMap.containsKey(index))
				lineLocMap.put(index, new ArrayList<LocEntry>());
			lineLocMap.get(index).add(new LocEntry(loc, coveredLines.get(index).score));
		}
	}

	public void nextLoc() {
		currLocIndex++;
	}

	public TargetLocation nextLocation(){
		if(currLocIndex < locations.size()){
			LocEntry e = locations.get(currLocIndex);
			return e.loc;
		} else {
			if(++currLineIndex < coveredLines.size()) {
				locations.clear();
				CoveredLine cl = coveredLines.get(currLineIndex);
				if(!patcherMap.containsKey(cl.className)) {
					System.out.println("Loading Class - "+cl.className);
					String source = PatchUtils.loadSource(sourceDir, cl.className);
					ConcretizationStrategy cStrategy = StrategyFactory.getConcretizationStrategy(cStrategyKey, manager, cl.className, sourceDir, r);
					Patcher patcher = new Patcher(cl.className, source, compileClassPathEntries, new String[] { sourceDir }, this, cStrategy);
					patcherMap.put(cl.className, patcher);
				}
				if(lineLocMap.containsKey(currLineIndex)) {
					locations.addAll(lineLocMap.get(currLineIndex));
				}
				currLocIndex = 0;
				return selectLocation();
			}
			return null;
		}
	}

	public TargetLocation selectLocation(){
		if(currLocIndex < locations.size()){
			LocEntry e = locations.get(currLocIndex);
			if(e.changeIds == null) {
				e.changeIds = findCandidateChanges(e.loc);
				if(e.changeIds.size() > 0)
					appendLoc(e);
			}
			if(e.changeIds.size() == 0){
				currLocIndex++;
				return selectLocation();
			}
			return e.loc;
		} else {
			if(++currLineIndex < coveredLines.size()) {
				locations.clear();
				CoveredLine cl = coveredLines.get(currLineIndex);
				if(!patcherMap.containsKey(cl.className)) {
					System.out.println("Loading Class - "+cl.className);
					String source = PatchUtils.loadSource(sourceDir, cl.className);
					ConcretizationStrategy cStrategy = StrategyFactory.getConcretizationStrategy(cStrategyKey, manager, cl.className, sourceDir, r);
					Patcher patcher = new Patcher(cl.className, source, compileClassPathEntries, new String[] { sourceDir }, this, cStrategy);
					patcherMap.put(cl.className, patcher);
				}
				if(lineLocMap.containsKey(currLineIndex)) {
					locations.addAll(lineLocMap.get(currLineIndex));
				}
				currLocIndex = 0;
				return selectLocation();
			}
			return null;
		}
	}

	public List<Integer> findCandidateChanges(TargetLocation loc){
		return findCandidateChanges(loc, false);
	}

	public List<Integer> findCandidateChanges(TargetLocation loc, boolean checkOnly){
		List<Integer> candidates = new ArrayList<>();
		Iterator<Integer> it = pool.changeIterator(loc.context);
		while(it.hasNext()){
			int id = it.next();
			Change c = pool.getChange(id);
			if(loc.kind != TargetLocation.DEFAULT
					&& checkDescriptor(loc, c)
					&& loc.isCompatible(c)){
				candidates.add(id);
			}else if(!c.type.equals(Change.INSERT) && loc.kind == TargetLocation.DEFAULT){
				if(c.node.hashString == null){
					c.node.hashString = TreeUtils.getTypeHash(c.node);
				}
				if(loc.node.hashString == null){
					loc.node.hashString = TreeUtils.getTypeHash(loc.node);
				}
				switch(c.type){
				case Change.UPDATE:
					if(c.node.hashString.equals(loc.node.hashString)){
						if(c.node.isStatement){
							candidates.add(id);
						}else if(c.node.kind == loc.node.kind){
							if(c.node.normalized){
								candidates.add(id);
							}else if(loc.isCompatible(c) && c.node.value.equals(loc.node.value)){
								candidates.add(id);
							}
						}
					}
					break;
				case Change.REPLACE:
					if(c.node.isStatement || !c.node.isStatement && loc.isCompatible(c)){
						if(c.node.hashString.equals(loc.node.hashString)) {
							if(valueMatched(c.node, loc.node))
								candidates.add(id);
						}
					}
					break;
				case Change.DELETE:
				case Change.MOVE:
					if(c.node.hashString.equals(loc.node.hashString)
							&& c.node.kind == loc.node.kind)
						candidates.add(id);
					break;
				}
			}
			if(checkOnly && candidates.size() > 0)
				return candidates;
		}
		return candidates;
	}

	protected boolean checkDescriptor(TargetLocation loc, Change c) {
		return c.type.equals(Change.INSERT);
	}

	protected boolean valueMatched(Node c, Node loc) {
		List<Node> cNodes = TreeUtils.traverse(c);
		List<Node> locNodes = TreeUtils.traverse(loc);
		if(cNodes.size() == locNodes.size()) {
			for(int i=0; i<cNodes.size(); i++) {
				c = cNodes.get(i);
				loc = locNodes.get(i);
				if(!c.normalized && c.value != null && !c.value.equals(loc.value))
					return false;
			}
			return true;
		}
		return false;
	}

	public Change selectChange(){
		if(currLocIndex < locations.size()) {
			LocEntry e = locations.get(currLocIndex);
			Change c = e.changeIds != null && e.changeIds.size() > 0 ? pool.getChange(e.changeIds.remove(0)) : null;
			return c;
		}
		return null;
	}

	public String getCurrentLocKey() {
		return currLineIndex+":"+currLocIndex;
	}

	public String getCurrentClass() {
		return currLineIndex >= 0 && currLineIndex < coveredLines.size() ? coveredLines.get(currLineIndex).className : "";
	}

	public Patcher patcher() {
		return patcherMap.get(getCurrentClass());
	}

	public Patcher patcher(String className) {
		if(!patcherMap.containsKey(className)) {
			String source = PatchUtils.loadSource(sourceDir, className);
			ConcretizationStrategy cStrategy = StrategyFactory.getConcretizationStrategy(cStrategyKey, manager, className, sourceDir, r);
			Patcher patcher = new Patcher(className, source, compileClassPathEntries, new String[] { sourceDir }, this, cStrategy);
			patcherMap.put(className, patcher);
		}
		return patcherMap.get(className);
	}

	protected void appendLoc(LocEntry e) {
		sbLoc.append("\n");
		sbLoc.append(e.loc.getTypeName());
		sbLoc.append("$$");
		sbLoc.append(e.loc.node.label);
		sbLoc.append("$$");
		sbLoc.append(e.loc.className);
		sbLoc.append("#");
		sbLoc.append(e.loc.node.startLine);
		sbLoc.append(":");
		sbLoc.append(e.freq);
		sbLoc.append(":");
		sbLoc.append(String.format("%1.4f", e.score));
	}

	public String getLocInfo() {
		return sbLoc.toString();
	}

	protected static class LocEntry implements Comparable<LocEntry> {
		public TargetLocation loc;
		public List<Integer> changeIds;
		public int freq;
		public double score;

		public LocEntry(TargetLocation loc) {
			this(loc, 0, 0.0d);
		}

		public LocEntry(TargetLocation loc, int freq) {
			this(loc, freq, 0.0d);
		}

		public LocEntry(TargetLocation loc, double score) {
			this(loc, 0, score);
		}

		public LocEntry(TargetLocation loc, int freq, double score) {
			this.loc = loc;
			this.freq = freq;
			this.score = score;
			this.changeIds = null;
		}

		@Override
		public int compareTo(LocEntry e) {
			return Integer.compare(e.freq, this.freq);
		}
	}

	public void finishUpdate() {
		//Do nothing for the baseline.
	}
}
