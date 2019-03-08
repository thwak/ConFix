package com.github.thwak.confix.tree.compiler;

import java.util.Comparator;

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;

public class NameComparator implements Comparator<Name> {

	public int compare(Name n0, Name n1) {
		IVariableBinding binding0 = (IVariableBinding)n0.resolveBinding();
		IVariableBinding binding1 = (IVariableBinding)n1.resolveBinding();		
		int id0 = binding0 != null ? binding0.getVariableId() : -1;
		int id1 = binding1 != null ? binding1.getVariableId() : -1;
		
		String key0 = n0.getFullyQualifiedName() + "|" + id0 + "|" + n0.hashCode();
		String key1 = n1.getFullyQualifiedName() + "|" + id1 + "|" + n1.hashCode();
		
		return key0.compareTo(key1);
	}

	
	
}
