package com.github.thwak.confix.pool;

import java.io.Serializable;

import org.eclipse.jdt.core.dom.ITypeBinding;

import com.github.thwak.confix.tree.TreeUtils;

public class VariableType implements Serializable {
	private static final long serialVersionUID = -278831478126202941L;
	public static final int DEFAULT = 0;
	public static final int ARRAY = 1;
	public static final int PARAMETERIZED = 2;
	public String qualifiedName;
	public String name;
	public boolean isJSL;
	public boolean isGeneric;

	public VariableType(String typeName){
		this(typeName, false, false);
	}

	public VariableType(String typeName, boolean isJSL){
		this(typeName, isJSL, false);
	}

	public VariableType(String typeName, boolean isJSL, boolean isGeneric){
		qualifiedName = typeName;
		name = typeName.indexOf('.') > -1 ? typeName.substring(typeName.lastIndexOf('.')+1, typeName.length()) : typeName;
		this.isJSL = isJSL;
		this.isGeneric = isGeneric;
	}

	public VariableType(ITypeBinding tb){
		if(tb != null){
			qualifiedName = tb.getQualifiedName();
			name = tb.getName();
			isJSL = TreeUtils.isJSL(tb);
			isGeneric = tb.isGenericType();
		}else{
			qualifiedName = "";
			name = "";
			isJSL = false;
			isGeneric = false;
		}
	}

	public boolean isSameType(String typeName){
		return qualifiedName.equals(typeName);
	}

	public boolean isSameType(VariableType type){
		return qualifiedName.equals(type.qualifiedName);
	}

	public boolean isSameType(ITypeBinding tb){
		return (tb != null && qualifiedName.equals(tb.getQualifiedName()))
				|| (tb == null && qualifiedName.equals(""));
	}

	public boolean isCompatible(VariableType type){
		String punct1 = qualifiedName.replaceAll("[\\._0-9a-zA-Z]", "");
		String punct2 = type.qualifiedName.replaceAll("[\\._0-9a-zA-Z]", "");
		if(!punct1.equals(punct2)){
			return false;
		}else{
			if(punct1.equals("")
					&& !qualifiedName.startsWith(MVTManager.TYPE_PREFIX)
					&& !type.qualifiedName.startsWith(MVTManager.TYPE_PREFIX)){
				return qualifiedName.equals(type.qualifiedName);
			}else{
				return true;
			}
		}
	}

	@Override
	public int hashCode() {
		return qualifiedName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof VariableType){
			VariableType type = (VariableType)obj;
			return qualifiedName.equals(type.qualifiedName);
		}
		return false;
	}

	@Override
	public String toString(){
		return qualifiedName;
	}
}
