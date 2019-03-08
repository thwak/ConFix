package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.github.thwak.confix.patch.PatchUtils;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

public class CodePool implements Serializable {

	private static final long serialVersionUID = -1383083913198678546L;

	protected Map<String, Map<CodeFragment, Integer>> fragments;

	public CodePool() {
		fragments = new HashMap<>();
	}

	public void addClass(String className, Node root) {
		TreeUtils.computeTypeHash(root);
		Map<String, List<Node>> map = new HashMap<>();
		List<Node> nodes = TreeUtils.traverse(root);
		for(Node n : nodes.subList(1, nodes.size())) {
			//Ignore leaves.
			if(n.children.size() > 0) {
				if(!map.containsKey(n.hashString))
					map.put(n.hashString, new ArrayList<Node>());
				map.get(n.hashString).add(n);
			}
		}
		TreeUtils.computeShortHashString(root);
		for(String typeHash : map.keySet()) {
			Map<CodeFragment, Integer> freqMap = fragments.containsKey(typeHash) ? fragments.get(typeHash) : new HashMap<CodeFragment, Integer>();
			fragments.put(typeHash, freqMap);
			nodes = map.get(typeHash);
			for(Node n : nodes) {
				CodeFragment cf = new CodeFragment(n, n.hashString);
				int freq = 1;
				if(freqMap.containsKey(cf))
					freq = freqMap.get(cf)+1;
				freqMap.put(cf, freq);
			}
		}
	}

	public Map<CodeFragment, Integer> getCode(Node n) {
		String shortHash = TreeUtils.getTypeHash(n);
		return fragments.get(shortHash);
	}

	public int getCount(Node n) {
		String typeHash = TreeUtils.getTypeHash(n);
		if(!fragments.containsKey(typeHash))
			return 0;
		Map<CodeFragment, Integer> cfMap = fragments.get(typeHash);
		return cfMap.size();
	}

	public int size() {
		return fragments.size();
	}

	public List<CodeFragment> getCodeFragments(Node n, TokenVector v, Random r) {
		String typeHash = TreeUtils.getTypeHash(n);
		List<CodeFragment> list = new ArrayList<>();
		if(!fragments.containsKey(typeHash))
			return list;
		Map<CodeFragment, Integer> cfMap = fragments.get(typeHash);
		list.addAll(cfMap.keySet());
		for(CodeFragment cf : list) {
			cf.score(v);
		}
		//Sorted based on distance(score asc), frequency(desc).
		Collections.sort(list, new PatchUtils.ScoreFreqComparator<>(cfMap, false, true));
		return list;
	}
}
