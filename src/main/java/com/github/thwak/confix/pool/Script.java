package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import script.model.EditOp;

public class Script implements Serializable {
	private static final long serialVersionUID = 5193028666977419501L;
	public Map<Change, List<EditOp>> changes = new HashMap<>();

	public void add(Change c, EditOp op) {
		if(!changes.containsKey(c))
			changes.put(c, new ArrayList<EditOp>());
		changes.get(c).add(op);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for(Change c : changes.keySet()) {
			sb.append(c);
			sb.append("\n");
		}
		return sb.toString();
	}
}
