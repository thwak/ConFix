package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.CoveredLine;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.Context;
import com.github.thwak.confix.pool.ContextIdentifier;
import com.github.thwak.confix.tree.CodeVisitor;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.tree.TreeUtils;

public class FLFreqPatchStrategy extends PatchStrategy {

	protected FLFreqPatchStrategy() {
		super();
	}

	public FLFreqPatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
			String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
		super(manager, pool, collector, r, flMetric, cStrategyKey, sourceDir, compileClassPathEntries);
	}

	@Override
	protected void prioritizeCoveredLines() {
		List<CoveredLine> lines = this.manager.computeScore(flMetric);
		Set<String> classes = new HashSet<>();
		//Filter zero score lines.
		Iterator<CoveredLine> it = lines.iterator();
		while(it.hasNext()) {
			CoveredLine cl = it.next();
			if(Double.compare(cl.score, 0.0000d) <= 0) {
				it.remove();
			}
		}
		for(CoveredLine cl : lines) {
			if(!classes.contains(cl.className)) {
				classes.add(cl.className);
				updateFrequency(cl.className, lines);
			}
		}
		Collections.sort(lines, new PatchUtils.CoveredLineComparator());
		for(CoveredLine cl : lines)
			if(cl.freq > 0)
				coveredLines.add(cl);
	}

	protected void updateFrequency(String className, List<CoveredLine> lines) {
		String source = PatchUtils.loadSource(sourceDir, className);
		Parser parser = new Parser(compileClassPathEntries, new String[] { sourceDir });
		Map<Integer, Integer> freqMap = new HashMap<>();
		for(CoveredLine cl : lines) {
			if(cl.className.equals(className))
				freqMap.put(cl.line, 0);
		}
		CompilationUnit cu = parser.parse(source);
		Node root = new Node("root");
		CodeVisitor visitor = new CodeVisitor(root);
		cu.accept(visitor);
		List<Node> nodes = TreeUtils.traverse(root);
		for(Node n : nodes) {
			if(freqMap.containsKey(n.startLine)) {
				Context c = collector().getContext(n);
				int freq = pool.getFrequency(c);
				c = collector().getLeftContext(n);
				freq += pool.getFrequency(c);
				c = collector().getRightContext(n);
				freq += pool.getFrequency(c);
				if(n.children.size() == 0
						&& n.type != ASTNode.SIMPLE_NAME
						&& n.type != ASTNode.SIMPLE_TYPE
						&& n.type != ASTNode.QUALIFIED_NAME
						&& n.type != ASTNode.QUALIFIED_TYPE) {
					List<StructuralPropertyDescriptor> spdList = null;
					if(n.astNode != null) {
						spdList = n.astNode.structuralPropertiesForType();
					} else {
						AST ast = AST.newAST(AST.JLS8);
						ASTNode astNode = ast.createInstance(n.type);
						spdList = astNode.structuralPropertiesForType();
					}
					for(StructuralPropertyDescriptor spd : spdList) {
						if(!spd.isSimpleProperty()) {
							c = collector().getUnderContext(n, spd);
							freq += pool.getFrequency(c);
						}
					}
				}
				freqMap.put(n.startLine, freqMap.get(n.startLine)+freq);
			}
		}
		for(CoveredLine cl : lines) {
			if(cl.className.equals(className) && freqMap.containsKey(cl.line)) {
				cl.freq = freqMap.get(cl.line);
			}
		}
	}

	@Override
	public void updateLocations(String className, Node root, FixLocationIdentifier identifier) {
		List<TargetLocation> locs = new ArrayList<>();
		identifier.findLocations(className, root, locs);
		fixLocCount += locs.size();
		Set<Integer> processed = new HashSet<>();
		for(TargetLocation loc : locs) {
			CoveredLine cl = new CoveredLine(className, loc.node.startLine);
			int index = coveredLines.getIndex(cl);
			processed.add(index);
			int freq = pool.getChangeCount(loc.context);
			if(!lineLocMap.containsKey(index)) {
				lineLocMap.put(index, new ArrayList<LocEntry>());
			}
			lineLocMap.get(index).add(new LocEntry(loc, freq, coveredLines.get(index).score));
		}
		for(Integer index : processed) {
			Collections.sort(lineLocMap.get(index), new Comparator<LocEntry>() {
				@Override
				public int compare(LocEntry o1, LocEntry o2) {
					return Integer.compare(o1.freq, o2.freq);
				}
			});
		}
	}

	@Override
	public void finishUpdate() {

	}
}