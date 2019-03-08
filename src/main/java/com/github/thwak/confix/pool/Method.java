package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class Method implements Serializable {
	private static final long serialVersionUID = -6117956338142112297L;
	public static final String DELIM = "::";
	public String name;
	public VariableType returnType;
	public List<VariableType> parameters;
	public VariableType declaringClass;
	private int hash = 0;
	private String signature;
	private String abstractSignature;

	public Method(){
		name = "";
		returnType = new VariableType("");
		parameters = new ArrayList<>();
		declaringClass = new VariableType("");
	}

	public Method(String name, VariableType declaringClass, VariableType returnType){
		this.name = name;
		this.declaringClass = declaringClass;
		this.returnType = returnType;
		parameters = new ArrayList<>();
	}

	public Method(IMethodBinding mb){
		if (mb != null) {
			name = mb.getName();
			ITypeBinding returnType = mb.getReturnType();
			this.returnType = returnType == null ? new VariableType("") : new VariableType(returnType);
			ITypeBinding declClass = mb.getDeclaringClass();
			declaringClass = declClass == null ? new VariableType("") : new VariableType(declClass);
			parameters = new ArrayList<>();
			ITypeBinding[] parameters = mb.getParameterTypes();
			for (ITypeBinding param : parameters) {
				if (param != null)
					this.parameters.add(new VariableType(param));
				else
					this.parameters.add(new VariableType(""));
			}
		} else {
			name = "";
			returnType = new VariableType("");
			parameters = new ArrayList<>();
			declaringClass = new VariableType("");
		}
	}

	@Override
	public int hashCode() {
		if(signature == null){
			getSignature();
			hash = (name + DELIM + signature).hashCode();
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Method){
			Method m = (Method)obj;
			return getSignature().equals(m.getSignature()) && name.equals(m.name);
		}
		return false;
	}

	public String getTypeAssignedSignature(Map<VariableType, VariableType> typeMap){
		StringBuffer sb = new StringBuffer();
		appendType(typeMap, declaringClass, sb);
		sb.append(DELIM);
		appendType(typeMap, returnType, sb);
		sb.append(DELIM);
		for(VariableType param : parameters){
			appendType(typeMap, param, sb);
			sb.append(",");
		}
		return sb.toString();
	}

	private void appendType(Map<VariableType, VariableType> typeMap, VariableType type, StringBuffer sb) {
		if(type.isJSL){
			sb.append(type.name);
		}else{
			if(typeMap.containsKey(type)){
				sb.append(typeMap.get(type).name);
			}else{
				if(!typeMap.containsKey(type))
					typeMap.put(type, new VariableType(MVTManager.TYPE_PREFIX+typeMap.size(), false));
				sb.append(typeMap.get(type.name));
			}
		}
	}

	public String getAbstractSignature(){
		if(abstractSignature == null){
			Map<String, String> typeMap = new HashMap<>();
			StringBuffer sb = new StringBuffer();
			appendTypeName(typeMap, declaringClass, sb);
			sb.append(DELIM);
			appendTypeName(typeMap, returnType, sb);
			sb.append(DELIM);
			for(VariableType param : parameters){
				appendTypeName(typeMap, param, sb);
				sb.append(",");
			}
			abstractSignature = sb.toString();
		}
		return abstractSignature;
	}

	private void appendTypeName(Map<String, String> typeMap, VariableType type, StringBuffer sb){
		if(type.isJSL){
			sb.append(type.name);
		}else{
			if(!typeMap.containsKey(type.name))
				typeMap.put(type.name, MVTManager.TYPE_PREFIX+typeMap.size());
			sb.append(typeMap.get(type.name));
		}
	}

	public String getSignature(){
		if(signature == null){
			StringBuffer sb = new StringBuffer(declaringClass.name);
			sb.append(DELIM);
			sb.append(returnType.name);
			sb.append(DELIM);
			for(VariableType param : parameters){
				sb.append(param.name);
				sb.append(",");
			}
			signature = sb.toString();
		}
		return signature;
	}

	@Override
	public String toString(){
		return getSignature();
	}
}
