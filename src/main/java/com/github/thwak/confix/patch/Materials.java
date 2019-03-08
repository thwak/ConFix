package com.github.thwak.confix.patch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import com.github.thwak.confix.pool.MVTManager;
import com.github.thwak.confix.pool.Method;
import com.github.thwak.confix.pool.Variable;
import com.github.thwak.confix.pool.VariableType;

public class Materials {
	public Map<String, Map<VariableType, Set<VarEntry>>> localVariables;
	public Map<VariableType, Set<Variable>> variables;
	public Map<VariableType, Set<Variable>> fields;
	public Map<String, Set<Method>> methods;
	public Set<VariableType> types;
	public Set<VariableType> genericTypes;
	public Set<String> varNames;
	public Map<VariableType, Set<String>> importsForTypes;
	public Map<Method, Set<String>> importsForMethods;
	public Map<String, Integer> strings;
	public int maxStrFreq = -1;

	private class VarEntry {
		public Variable v;
		public String key;
		public int line;
		public int[] index;

		public VarEntry(Variable v, String key, int line) {
			this.v = v;
			this.key = key == null ? v.name : key;
			this.line = line;
			this.index = resolveIndex(key);
		}

		@Override
		public int hashCode() {
			return this.key.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if(obj instanceof VarEntry) {
				return this.key.equals(((VarEntry)obj).key);
			}
			return false;
		}

		public boolean checkScope(int[] blkIndex, int line) {
			if(index.length == 0)
				return true;
			if(index.length > blkIndex.length) {
				return false;
			} else {
				for(int i=0; i<index.length; i++) {
					if(index[i] != blkIndex[i])
						return false;
				}
				return this.line < line;
			}
		}
	}

	public Materials(){
		localVariables = new HashMap<>();
		variables = new HashMap<>();
		fields = new HashMap<>();
		methods = new HashMap<>();
		types = new HashSet<>();
		genericTypes = new HashSet<>();
		varNames = new HashSet<>();
		importsForTypes = new HashMap<>();
		importsForMethods = new HashMap<>();
		strings = new HashMap<>();
	}

	public Method addMethod(IMethodBinding mb){
		Method m = new Method(mb);
		String absSignature = m.getAbstractSignature();
		if(!methods.containsKey(absSignature))
			methods.put(absSignature, new HashSet<Method>());
		methods.get(absSignature).add(m);
		if(mb != null){
			Set<String> importNames = new HashSet<>();
			ITypeBinding declType = mb.getDeclaringClass();
			if(declType != null)
				importNames.addAll(MVTManager.collectImportNames(declType));
			ITypeBinding returnType = mb.getReturnType();
			if(returnType != null)
				importNames.addAll(MVTManager.collectImportNames(returnType));
			ITypeBinding[] params = mb.getParameterTypes();
			for(ITypeBinding param : params){
				if(param != null)
					importNames.addAll(MVTManager.collectImportNames(param));
			}
			if(importNames.size() > 0)
				importsForMethods.put(m, importNames);
		}
		return m;
	}

	public void addVariable(VariableType type, Variable v){
		if(!variables.containsKey(type))
			variables.put(type, new HashSet<Variable>());
		variables.get(type).add(v);
		varNames.add(v.name);
	}

	public void addLocalVariable(VariableType type, Variable v, String varKey, int line){
		int idx = varKey.indexOf('#');
		String methodKey = varKey.substring(0, idx);
		if(!localVariables.containsKey(methodKey))
			localVariables.put(methodKey, new HashMap<VariableType, Set<VarEntry>>());
		Map<VariableType, Set<VarEntry>> map = localVariables.get(methodKey);
		if(!map.containsKey(type))
			map.put(type, new HashSet<VarEntry>());
		VarEntry locVar = new VarEntry(v, varKey.substring(idx), line);
		map.get(type).add(locVar);
		varNames.add(v.name);
	}

	public void addField(VariableType type, Variable v){
		if(!fields.containsKey(type))
			fields.put(type, new HashSet<Variable>());
		fields.get(type).add(v);
	}

	public void addType(VariableType type){
		if(type.isGeneric)
			genericTypes.add(type);
		else
			types.add(type);
	}

	public void addType(ITypeBinding tb){
		VariableType type = new VariableType(tb);
		addType(type);
	}

	public void addImports(VariableType type, ITypeBinding tb) {
		Set<String> importNames = MVTManager.collectImportNames(tb);
		if(importNames.size() > 0)
			importsForTypes.put(type, importNames);
	}

	public Set<Variable> getLocalVariables(TargetLocation loc, VariableType type) {
		return getLocalVariables(loc.methodKey, type, loc.blockIndex, loc.node.startLine);
	}

	public Set<Variable> getLocalVariables(String methodKey, VariableType type, int[] blkIndex, int line) {
		Map<VariableType, Set<VarEntry>> map = localVariables.get(methodKey);
		Set<Variable> vars = new HashSet<>();
		if(map != null && map.containsKey(type)) {
			Set<VarEntry> entries = map.get(type);
			for(VarEntry e : entries) {
				if(e.checkScope(blkIndex, line))
					vars.add(e.v);
			}
		}
		return vars;
	}

	public static int[] resolveIndex(String key) {
		int[] index = null;
		int idx = key.indexOf('#');
		if(idx < 0)
			return new int[0];
		String str = key.substring(idx);
		str = str.substring(0, str.lastIndexOf('#')+1);
		String[] blkIndex = str.split("\\#");
		if(blkIndex.length == 0 || blkIndex.length == 1
				&& blkIndex[0].equals("")) {
			index = new int[0];
		} else {
			index = new int[blkIndex.length-1];
			for(int i=1; i<blkIndex.length; i++) {
				index[i-1] = Integer.parseInt(blkIndex[i]);
			}
		}
		return index;
	}

	public static boolean isLocal(IVariableBinding vb) {
		if(vb != null) {
			return vb.getKey().indexOf('#') >= 0;
		}
		return false;
	}

	public void addString(String str) {
		if(!strings.containsKey(str))
			strings.put(str, 0);
		int freq = strings.get(str)+1;
		strings.put(str, freq);
		if(freq > maxStrFreq)
			maxStrFreq = freq;
	}
}
