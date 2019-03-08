package com.github.thwak.confix.pool;

import java.io.Serializable;

import org.eclipse.jdt.core.dom.IVariableBinding;

public class Variable implements Serializable {
	private static final long serialVersionUID = 2209664254767076382L;
	public String name;
	public VariableType type;
	public boolean isDeclaration = false;
	public boolean isFieldAccess = false;
	public VariableType declType = null;

	public Variable(String name){
		this(name, new VariableType(""));
	}

	public Variable(String name, String typeName){
		this(typeName,  new VariableType(typeName));
	}

	public Variable(String name, IVariableBinding vb){
		this(name, new VariableType(vb.getType()));
	}

	public Variable(String name, VariableType type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public int hashCode() {
		String hash = name + "::" + this.type.name;
		return hash.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Variable){
			Variable v = (Variable)obj;
			return name.equals(v.name) && type.isSameType(v.type);
		}
		return false;
	}

	@Override
	public String toString(){
		return name;
	}
}
