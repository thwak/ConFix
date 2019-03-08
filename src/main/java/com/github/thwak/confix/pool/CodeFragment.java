package com.github.thwak.confix.pool;

import java.io.Serializable;

import com.github.thwak.confix.patch.Score;
import com.github.thwak.confix.tree.Node;

public class CodeFragment implements Score, Serializable {
	private static final long serialVersionUID = 3574509785618050344L;
	private Node n;
	private String hash;
	private TokenVector v;
	private double score;

	public CodeFragment(Node n, String hash) {
		this.n = n;
		this.hash = hash;
		this.v = new TokenVector(n);
		score = -1.0d;
	}

	public Node getNode() {
		return n;
	}

	public String getHash() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof CodeFragment) {
			CodeFragment cf = (CodeFragment)obj;
			return hash.equals(cf.hash);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return hash.hashCode();
	}

	public double score(TokenVector vec) {
		score = v.dist(vec);
		return score;
	}

	@Override
	public double getScore() {
		return score;
	}
}
