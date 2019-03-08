package com.github.thwak.confix.pool;

import java.io.Serializable;

import com.github.thwak.confix.tree.TreeUtils;

public class Context  implements Serializable {
	private static final long serialVersionUID = -1783212689497010361L;
	public String hashString;
	public String hash;
	public int hashCode;

	public Context(String hashString){
		this(hashString, TreeUtils.computeSHA256Hash(hashString));
	}

	public Context(String hashString, String hash){
		this.hashString = hashString;
		this.hash = hash;
		this.hashCode = hash.hashCode();
	}

	public Context(){
		this("");
	}

	@Override
	public boolean equals(Object obj){
		if(obj instanceof Context){
			return this.hash.equals(((Context)obj).hash);
		}
		return false;
	}

	@Override
	public int hashCode(){
		return this.hashCode;
	}

	@Override
	public String toString(){
		return hashString;
	}
}
