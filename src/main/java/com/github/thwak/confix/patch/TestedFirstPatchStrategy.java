package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.CoveredLine;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.ContextIdentifier;
import com.github.thwak.confix.pool.TokenVector;
import com.github.thwak.confix.util.IOUtils;
import com.github.thwak.confix.util.IndexMap;

public class TestedFirstPatchStrategy extends FLFreqPatchStrategy {

	protected Map<String, Integer> testClasses;

	protected TestedFirstPatchStrategy() {
		super();
	}

	public TestedFirstPatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
			String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
		this(manager, pool, collector, r, flMetric, cStrategyKey, sourceDir, compileClassPathEntries, new HashMap<String, Integer>());
	}

	public TestedFirstPatchStrategy(CoverageManager manager, ChangePool pool, ContextIdentifier collector, Random r,
			String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries, Map<String, Integer> testClasses) {
		super();
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
		this.testClasses = testClasses;
		if(testClasses.size() == 0) {
			loadTests();
		}
		prioritizeCoveredLines();
	}

	private void loadTests() {
		String s = IOUtils.readFile("tests.trigger");
		String[] tests = s.split("\n");
		for(String test : tests) {
			if(!test.startsWith("#")) {
				String testClass = test.split("::")[0];
				if(!testClasses.containsKey(testClass))
					testClasses.put(testClass, 1);
				else
					testClasses.put(testClass, testClasses.get(testClass)+1);
			}
		}
	}

	@Override
	protected void prioritizeCoveredLines() {
		Set<String> targetClasses = manager.getNegCoveredClasses();
		List<String> classes = findSuspiciousClasses(testClasses, targetClasses);
		targetClasses.removeAll(classes);
		for(String className : classes) {
			List<CoveredLine> lines = manager.computeScore(flMetric, className, true);
			updateFrequency(className, lines);
			Collections.sort(lines, new PatchUtils.CoveredLineComparator());
			for(CoveredLine cl : lines)
				if(cl.freq > 0)
					coveredLines.add(cl);
		}
		List<CoveredLine> lines = manager.computeScore(flMetric, targetClasses, true);
		for(String className : targetClasses) {
			updateFrequency(className, lines);
		}
		Collections.sort(lines, new PatchUtils.CoveredLineComparator());
		for(CoveredLine cl : lines)
			if(cl.freq > 0)
				coveredLines.add(cl);
	}

	private List<String> findSuspiciousClasses(Map<String, Integer> testClasses, Set<String> targetClasses) {
		Map<String, TokenVector> targets = getTokenVectors(targetClasses);
		Map<String, TokenVector> tests = getTokenVectors(testClasses.keySet());
		final Map<String, Integer> scoreMap = new HashMap<>();
		List<String> suspicious = new ArrayList<>();
		for(String test : testClasses.keySet()) {
			TokenVector v1 = tests.get(test);
			int max = 0;
			Set<String> matched = new HashSet<>();
			for(String target : targetClasses) {
				TokenVector v2 = targets.get(target);
				int common = v1.commonTokenSize(v2);
				if(v2.size() == common) {
					if(common > max) {
						max = common;
						matched.clear();
						matched.add(target);
					} else if(common == max){
						matched.add(target);
					}
				}
			}
			for(String target : matched) {
				if(!scoreMap.containsKey(target))
					scoreMap.put(target, 0);
				scoreMap.put(target, scoreMap.get(target)+testClasses.get(test));
			}
		}
		suspicious.addAll(scoreMap.keySet());
		Collections.sort(suspicious, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return Integer.compare(scoreMap.get(o2), scoreMap.get(o1));
			}
		});
		return suspicious;
	}

	private Map<String, TokenVector> getTokenVectors(Set<String> classNames) {
		Map<String, TokenVector> map = new HashMap<>();
		for(String qName : classNames) {
			map.put(qName, new TokenVector(qName));
		}
		return map;
	}
}
