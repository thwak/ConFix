package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ITypeBinding;

public class Requirements implements Serializable {
	private static final long serialVersionUID = 55273969673062719L;
	public Set<VariableType> types;
	public Set<VariableType> genericTypes;
	public Map<String, Set<Method>> methods;
	public Map<VariableType, Set<Variable>> variables;
	public Map<VariableType, Set<Variable>> fields;
	public Set<String> declaredVariables;
	public Set<String> imports;
	public boolean isStatement;
	public VariableType nodeType;
	public Set<String> strings;

	public Requirements() {
		types = new HashSet<>();
		genericTypes = new HashSet<>();
		methods = new HashMap<>();
		variables = new HashMap<>();
		fields = new HashMap<>();
		declaredVariables = new HashSet<>();
		imports = new HashSet<>();
		isStatement = false;
		nodeType = null;
		strings = new HashSet<>();
	}

	public void setNodeType(boolean isStatement, VariableType nodeType) {
		this.isStatement = isStatement;
		this.nodeType = isStatement ? null : nodeType;
	}

	public void updateRequirements(MVTManager manager){
		for(VariableType type : manager.types.values()){
			if(type.isGeneric){
				genericTypes.add(type);
			}else{
				types.add(type);
			}
		}
		for(Variable v : manager.variables.values()){
			if(v.isDeclaration){
				declaredVariables.add(v.name);
			}else if(v.isFieldAccess){
				if(!fields.containsKey(v.type))
					fields.put(v.type, new HashSet<Variable>());
				fields.get(v.type).add(v);
			}else{
				if(!variables.containsKey(v.type))
					variables.put(v.type, new HashSet<Variable>());
				variables.get(v.type).add(v);
			}
		}
		for(Method m : manager.methods.values()){
			String absSignature = m.getAbstractSignature();
			if(!methods.containsKey(absSignature))
				methods.put(absSignature, new HashSet<Method>());
			methods.get(absSignature).add(m);
		}
		imports.addAll(manager.imports);
		strings.addAll(manager.strings.values());
	}

	public boolean isCompatible(ITypeBinding type){
		return nodeType == null
				|| nodeType != null && (nodeType.isSameType(type) || nodeType.qualifiedName.equals(""));
	}
}
