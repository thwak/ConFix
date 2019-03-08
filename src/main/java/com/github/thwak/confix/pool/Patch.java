package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.thwak.confix.pool.PatchPool.ContextChange;

public class Patch implements Serializable {

	private static final long serialVersionUID = -5579469928737763643L;
	public String bugId;
	public Map<String, List<ContextChange>> changes;

	public Patch(String bugId) {
		this.bugId = bugId;
		changes = new HashMap<>();
	}

	public void add(String fileName, ContextChange cc) {
		if(!changes.containsKey(fileName))
			changes.put(fileName, new ArrayList<ContextChange>());
		changes.get(fileName).add(cc);
	}
}
