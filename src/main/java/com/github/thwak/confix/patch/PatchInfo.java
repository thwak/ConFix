package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.thwak.confix.pool.Change;

public class PatchInfo {

	public TargetLocation loc;
	public Change change;
	public String className;
	public Set<String> cMethods;
	public List<RepairAction> repairs;

	public PatchInfo(String targetClass, Change change, TargetLocation loc) {
		this.className = targetClass;
		this.loc = loc;
		this.change = change;
		this.repairs = new ArrayList<>();
		this.cMethods = new HashSet<>();
	}

	public String getConcretize() {
		StringBuffer sb = new StringBuffer();
		for(String str : cMethods) {
			sb.append(",");
			sb.append(str);
		}
		return sb.substring(1);
	}
}
