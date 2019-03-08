package com.github.thwak.confix.coverage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CoverageManager implements Serializable {
	private static final long serialVersionUID = 6389926425102795341L;
	public static final String M_JACCARD = "jaccard";
	public static final String M_TARANTULA = "tarantula";
	public static final String M_OCHIAI = "ochiai";
	public static final String M_NEG_ONLY = "neg_only";
	private int totalTests;
	private int failedTests;
	private int passedTests;
	private Map<String, CoverageInfo> info;

	public CoverageManager() {
		info = new HashMap<>();
		totalTests = 0;
		failedTests = 0;
		passedTests = 0;
	}

	public int getTotalTests() {
		return totalTests;
	}

	public void setTotalTests(int totalTests) {
		this.totalTests = totalTests;
	}

	public int getFailedTests() {
		return failedTests;
	}

	public void setFailedTests(int failedTests) {
		this.failedTests = failedTests;
	}

	public int getPassedTests() {
		return passedTests;
	}

	public void setPassedTests(int passedTests) {
		this.passedTests = passedTests;
	}

	public CoverageInfo get(String className) {
		return info.get(className);
	}

	public void add(String className, CoverageInfo covInfo) {
		info.put(className, covInfo);
	}

	public boolean contains(String className) {
		return info.containsKey(className);
	}

	public TreeSet<String> getNegCoveredClasses() {
		TreeSet<String> classes = new TreeSet<>();
		for(String className : info.keySet()) {
			if(info.get(className).isNegCovered())
				classes.add(className);
		}
		return classes;
	}

	/**
	 * Compute suspiciousness of covered lines on classes covered by failed tests.
	 *
	 * @param metric FL formula to compute suspiciousness.
	 * @return a list of covered lines sorted in descending order of suspiciousness.
	 */
	public List<CoveredLine> computeScore(String metric) {
		return computeScore(metric, info.keySet(), false);
	}

	public List<CoveredLine> computeScore(String metric, Set<String> classes, boolean filterZeros) {
		List<CoveredLine> list = new ArrayList<>();
		for(String className : classes) {
			CoverageInfo cov = info.get(className);
			if(cov != null && cov.isNegCovered()) {
				TreeSet<Integer> lines = cov.getAllLines();
				for(Integer line : lines) {
					CoveredLine cl = new CoveredLine(className, line);
					double negCovTests = cov.getNegCovCount(line);
					double posCovTests = cov.getPosCovCount(line);
					double negNonCovTests = failedTests - negCovTests;
					double posNonCovTests = passedTests - posCovTests;
					switch(metric) {
					case M_JACCARD:
						cl.score = jaccard(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
						break;
					case M_TARANTULA:
						cl.score = tarantula(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
						break;
					case M_OCHIAI:
						cl.score = ochiai(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
						break;
					case M_NEG_ONLY:
					default:
						cl.score = negOnly((int)negCovTests, (int)posCovTests);
					}
					if(!filterZeros || Double.compare(cl.score, 0.0d) > 0)
						list.add(cl);
				}
			}
		}
		//Sort in descending order of scores.
		Collections.sort(list, new Comparator<CoveredLine>() {
			@Override
			public int compare(CoveredLine l1, CoveredLine l2) {
				return Double.compare(l2.score, l1.score);
			}
		});
		return list;
	}

	public List<CoveredLine> computeScore(String metric, String className, boolean filterZeros) {
		List<CoveredLine> list = new ArrayList<>();
		CoverageInfo cov = info.get(className);
		if(cov.isNegCovered()) {
			TreeSet<Integer> lines = cov.getAllLines();
			for(Integer line : lines) {
				CoveredLine cl = new CoveredLine(className, line);
				double negCovTests = cov.getNegCovCount(line);
				double posCovTests = cov.getPosCovCount(line);
				double negNonCovTests = failedTests - negCovTests;
				double posNonCovTests = passedTests - posCovTests;
				switch(metric) {
				case M_JACCARD:
					cl.score = jaccard(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
					break;
				case M_TARANTULA:
					cl.score = tarantula(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
					break;
				case M_OCHIAI:
					cl.score = ochiai(negCovTests, posCovTests, negNonCovTests, posNonCovTests);
					break;
				case M_NEG_ONLY:
				default:
					cl.score = negOnly((int)negCovTests, (int)posCovTests);
				}
				if(!filterZeros || Double.compare(cl.score, 0.0d) > 0)
					list.add(cl);
			}
		}
		//Sort in descending order of scores.
		Collections.sort(list, new Comparator<CoveredLine>() {
			@Override
			public int compare(CoveredLine l1, CoveredLine l2) {
				return Double.compare(l2.score, l1.score);
			}
		});
		return list;
	}

	public double negOnly(int a11, int a10) {
		double coeff = 0.0d;
		if(a11 > 0 && a10 == 0){
			coeff = 1.0d;
		}else if(a11 > 0 || a10 > 0){
			coeff = 0.1d;
		}else{
			coeff = 0.0d;
		}

		return coeff;
	}

	public double jaccard(double a11, double a10, double a01, double a00) {
		double coeff = 0.0;
		if(a11 + a01 + a10 > 0.0){
			coeff = a11/(a11 + a01 + a10);
		}
		return coeff;
	}

	public double tarantula(double a11, double a10, double a01, double a00) {
		double coeff = 0.0;
		double denominator = ((a11/(a11+a01))+(a10/(a10+a00)));
		if(denominator != 0){
			coeff = (a11/a11+a01)/denominator;
		}
		return coeff;
	}

	public double ochiai(double a11, double a10, double a01, double a00) {
		double coeff = 0.0;
		if(a11>0.0){
			coeff = a11/Math.sqrt((a11+a01)*(a11+a10));
		}
		return coeff;
	}
}
