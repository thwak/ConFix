package com.github.thwak.confix.coverage;

import com.github.thwak.confix.patch.Score;

public class CoveredLine implements Score {
	public String className;
	public int line;
	public double score;
	public int freq;

	public CoveredLine(String entry) {
		int idx = entry.indexOf('#');
		className = entry.substring(0, idx);
		int index = className.indexOf('$');
		if(index >= 0)
			className = className.substring(0, index);
		line = Integer.parseInt(entry.substring(idx+1).trim());
		score = -1d;
		freq = 0;
	}

	public CoveredLine(String className, int line) {
		super();
		int index = className.indexOf('$');
		if(index >= 0)
			className = className.substring(0, index);
		this.className = className;
		this.line = line;
		score = -1d;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof CoveredLine ) {
			CoveredLine cl = (CoveredLine)obj;
			return cl.line == this.line && this.className.equals(cl.className);
		}
		return false;
	}

	@Override
	public int hashCode() {
		String hash = className + "#" + line;
		return hash.hashCode();
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(className);
		sb.append("#");
		sb.append(line);
		sb.append(":");
		sb.append(String.format("%1.4f", score));
		sb.append(":");
		sb.append(freq);
		return sb.toString();
	}

	@Override
	public double getScore() {
		return score;
	}
}
