package com.github.thwak.confix.patch;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.github.thwak.confix.coverage.CoverageInfo;
import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.CodePool;

public class StrategyFactory {

	public static Map<String, CodePool> codePools = new HashMap<>();
	public static Map<String, CodePool> pkgCodePools = new HashMap<>();

	public static PatchStrategy getPatchStrategy(String key, CoverageManager coverage, ChangePool pool, Random r,
			String flMetric, String cStrategyKey, String sourceDir, String[] compileClassPathEntries) {
		PatchStrategy strategy = null;
		key = key.toLowerCase();
		switch(key) {
		case "flfreq":
			strategy = new FLFreqPatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey,
					sourceDir, compileClassPathEntries);
			break;
		case "tested-first":
			strategy = new TestedFirstPatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey,
					sourceDir, compileClassPathEntries);
			break;
		case "noctx":
			strategy = new NoContextPatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey,
					sourceDir, compileClassPathEntries);
			break;
		default:
			strategy = new PatchStrategy(coverage, pool, pool.getIdentifier(), r, flMetric, cStrategyKey, sourceDir,
					compileClassPathEntries);
		}
		return strategy;
	}

	public static ConcretizationStrategy getConcretizationStrategy(String key, CoverageManager coverage, String className, String srcDir, Random r) {
		ConcretizationStrategy strategy = null;
		CoverageInfo info = coverage.get(className);
		String packageName = getPackageName(className);
		key = key.toLowerCase();
		switch(key) {
		case "tcvfl":
			strategy = new TCVFLStrategy(info, r);
			break;
		case "hash-match":
			if(codePools.containsKey(className)) {
				strategy = new HashMatchStrategy(srcDir, codePools.get(className), pkgCodePools.get(packageName), r);
			} else {
				strategy = new HashMatchStrategy(srcDir, r);
			}
			break;
		case "neighbor":
			strategy = new NeighborFirstStrategy(r);
		case "tc":
		default:
			strategy = new ConcretizationStrategy(r);	//Default Type-compatible strategy
		}
		return strategy;
	}

	private static String getPackageName(String className) {
		if(className == null)
			return "";
		int index = className.lastIndexOf('.');
		return index < 0 ? "" : className.substring(0, index);
	}

}
