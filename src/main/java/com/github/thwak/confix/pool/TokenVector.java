package com.github.thwak.confix.pool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

public class TokenVector {

	public static final String SPLIT_REGEX = "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])";
	public Map<String, Integer> tokenMap;

	public TokenVector(List<Node> nodes) {
		tokenMap = new HashMap<>();
		for(Node n : nodes)
			addAllTokens(n);
	}

	public TokenVector(Node node) {
		tokenMap = new HashMap<>();
		addAllTokens(node);
	}

	public TokenVector(String str) {
		tokenMap = new HashMap<>();
		addTokens(str);
	}

	private void addAllTokens(Node node) {
		for(Node n : TreeUtils.traverse(node)) {
			if(n.value == null)
				continue;
			switch(n.type) {
			case ASTNode.SIMPLE_NAME:
			case ASTNode.SIMPLE_TYPE:
				addTokens(n.value);
				break;
			case ASTNode.QUALIFIED_NAME:
			case ASTNode.QUALIFIED_TYPE:
				String[] tokens = n.value.split("\\.");
				for(String t : tokens)
					addTokens(t);
			}
		}
	}

	private void addTokens(String str) {
		String[] tokens = str.split(SPLIT_REGEX);
		for(String t : tokens) {
			if(t.indexOf('.') >= 0) {
				for(String s : t.split("\\."))
					if(s.length() > 0)
						addToken(s);
			} else {
				addToken(t);
			}
		}
	}

	private void addToken(String token) {
		int freq = tokenMap.containsKey(token) ? tokenMap.get(token)+1 : 1;
		tokenMap.put(token, freq);
	}

	public double dist(TokenVector v) {
		Set<String> keys = new HashSet<>(tokenMap.keySet());
		keys.addAll(v.tokenMap.keySet());
		double dist = 0.0d;
		int x1 = 0;
		int x2 = 0;
		for(String key : keys) {
			x1 = tokenMap.containsKey(key) ? tokenMap.get(key) : 0;
			x2 = v.tokenMap.containsKey(key) ? v.tokenMap.get(key) : 0;
			dist += (x1-x2)*(x1-x2);
		}
		return Math.sqrt(dist);
	}

	public int commonTokenSize(TokenVector v) {
		Set<String> keys = new HashSet<>(tokenMap.keySet());
		keys.retainAll(v.tokenMap.keySet());
		return keys.size();
	}

	public int size() {
		return tokenMap.size();
	}
}
