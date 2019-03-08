package com.github.thwak.confix.coverage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

public class CoverageInfo implements Serializable {
	private static final long serialVersionUID = -5729805301059083915L;
	private String className;
	private TreeMap<Integer, Integer> negCoverage;
	private TreeMap<Integer, Integer> posCoverage;
	private int lineCount;

	public CoverageInfo(String className){
		this.className = className;
		lineCount = 0;
		negCoverage = new TreeMap<>();
		posCoverage = new TreeMap<>();
	}

	public int getLineCount() {
		return lineCount;
	}

	public void setLineCount(int lineCount) {
		this.lineCount = lineCount;
	}

	public void setClassName(String className){
		this.className = className;
	}

	public String getClassName() {
		return className;
	}

	public void addPosCoverage(int line){
		if(!posCoverage.containsKey(line)) {
			posCoverage.put(line, 1);
		} else {
			posCoverage.put(line, posCoverage.get(line)+1);
		}
	}

	public void addNegCoverage(int line){
		if(!negCoverage.containsKey(line)) {
			negCoverage.put(line, 1);
		} else {
			negCoverage.put(line, negCoverage.get(line)+1);
		}
	}

	public void addPosCoverage(int startLine, int endLine){
		for(int line=startLine; line<=endLine; line++)
			addPosCoverage(line);
	}

	public void addNegCoverage(int startLine, int endLine){
		for(int line=startLine; line<=endLine; line++)
			addNegCoverage(line);
	}

	public TreeSet<Integer> getPosCoverage(){
		return new TreeSet<>(posCoverage.navigableKeySet());
	}

	public TreeSet<Integer> getNegCoverage(){
		return new TreeSet<>(negCoverage.navigableKeySet());
	}

	public TreeSet<Integer> getAllLines() {
		TreeSet<Integer> lines = new TreeSet<>(negCoverage.keySet());
		lines.addAll(posCoverage.keySet());
		return lines;
	}

	public int getNegCovCount() {
		return Collections.max(negCoverage.values());
	}

	public int getPosCovCount() {
		return Collections.max(posCoverage.values());
	}

	public boolean isCovered(int line){
		return posCoverage.containsKey(line) || negCoverage.containsKey(line);
	}

	public boolean isNegCovered(int line){
		return negCoverage.containsKey(line);
	}

	public boolean isCovered() {
		return negCoverage.size() > 0 || posCoverage.size() > 0;
	}

	public boolean isNegCovered() {
		return negCoverage.size() > 0;
	}

	public boolean isPosCovered(int line){
		return posCoverage.containsKey(line);
	}

	public boolean isPosCovered() {
		return posCoverage.size() > 0;
	}

	public boolean isNegOnlyCovered(int line){
		return negCoverage.containsKey(line) && !posCoverage.containsKey(line);
	}

	public TreeSet<Integer> getNegOnlyCovered(){
		TreeSet<Integer> lines = new TreeSet<>(negCoverage.navigableKeySet());
		lines.removeAll(posCoverage.keySet());
		return lines;
	}

	public List<Integer> getOrderedSuspiciousLines(){
		List<Integer> suspicousLines = new ArrayList<>();
		List<Integer> lines = new ArrayList<>();
		for(Integer line : negCoverage.keySet()){
			if(isPosCovered(line))
				lines.add(line);
			else
				suspicousLines.add(line);
		}
		suspicousLines.addAll(lines);
		return suspicousLines;
	}

	public List<Integer> getRandomOrderNegOnlyCoveredLines(Random r){
		List<Integer> lines = new ArrayList<>(getNegOnlyCovered());
		List<Integer> orderedLines = new ArrayList<>();
		while(lines.size() > 0){
			orderedLines.add(lines.remove(r.nextInt(lines.size())));
		}
		return orderedLines;
	}

	public List<Integer> getRandomOrderSuspiciousLines(Random r){
		List<Integer> orderedLines = new ArrayList<>(getRandomOrderNegOnlyCoveredLines(r));
		HashSet<Integer> intersection = new HashSet<>(negCoverage.keySet());
		intersection.retainAll(posCoverage.keySet());
		List<Integer> lines = new ArrayList<>(intersection);
		while(lines.size() > 0){
			orderedLines.add(lines.remove(r.nextInt(lines.size())));
		}
		return orderedLines;
	}

	public int getNegCovCount(int line) {
		return negCoverage.containsKey(line) ? negCoverage.get(line) : 0;
	}

	public int getPosCovCount(int line) {
		return posCoverage.containsKey(line) ? posCoverage.get(line) : 0;
	}
}
