package com.github.thwak.confix.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;

public class IndexMap <E> implements Serializable {
	private static final long serialVersionUID = -2591819678834618917L;
	private HashMap<E, Integer> set;
	private TreeMap<Integer, E> index;

	public IndexMap() {
		set = new HashMap<>();
		index = new TreeMap<>();
	}

	public IndexMap(Collection<E> c) {
		this();
		addAll(c);
	}

	public int add(E e) {
		if(set.containsKey(e))
			return set.get(e);
		else {
			int idx = index.size() == 0 ? 0 : index.lastKey()+1;
			index.put(idx, e);
			set.put(e, idx);
			return idx;
		}
	}

	public void addAll(IndexMap<E> s) {
		for(E e : s.elements()) {
			add(e);
		}
	}

	public void addAll(Collection<E> c) {
		for(E e : c)
			add(e);
	}

	public E put(int index, E e) {
		E old = this.index.put(index, e);
		set.put(e, index);
		if(old != null)
			set.remove(old);
		return old;
	}

	public Set<E> elements() {
		return set.keySet();
	}

	public Collection<E> values() {
		return index.values();
	}

	public Set<Integer> indexSet() {
		return index.keySet();
	}

	public E get(Integer index) {
		return this.index.get(index);
	}

	public int getIndex(E e) {
		return this.set.containsKey(e) ? this.set.get(e) : -1;
	}

	public boolean contains(E e) {
		return this.set.containsKey(e);
	}

	public boolean hasIndex(int index) {
		return this.index.containsKey(index);
	}

	public int size() {
		return this.set.size();
	}

	public void clear() {
		set.clear();
		index.clear();
	}
}
