package com.github.thwak.confix.pool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import com.github.thwak.confix.tree.TreeUtils;

public class MVTManager {
	public static final String TYPE_PREFIX = "Type";
	public static final String VAR_PREFIX = "var";
	public static final String METHOD_PREFIX = "method";
	public static final String STRING_PREFIX = "str";
	private Map<String, String> varNames = new HashMap<>();
	private Map<String, String> methodNames = new HashMap<>();
	private Map<String, String> typeNames = new HashMap<>();
	public Map<String, Variable> variables = new HashMap<>();
	public Map<String, Method> methods = new HashMap<>();
	public Map<String, VariableType> types = new HashMap<>();
	public Set<String> imports = new HashSet<>();
	public Map<String, String> strings = new HashMap<>();

	public Variable getVariable(ASTNode node, IVariableBinding vb){
		if(vb == null){
			String varName = node.toString();
			if(!varNames.containsKey(varName))
				varNames.put(varName, VAR_PREFIX+varNames.size());
			Variable v = new Variable(varNames.get(varName), getType());
			if(node != null
					&& node.getParent() != null){
				if(node.getParent() instanceof VariableDeclaration){
					v.isDeclaration = true;
				}else if(node.getParent() instanceof FieldAccess
						&& node.getLocationInParent().equals(FieldAccess.NAME_PROPERTY)){
					v.isFieldAccess = true;
					VariableType declType = getType(null, null);
					v.declType = declType;
				}
			}
			if(!variables.containsKey(v.name)){
				variables.put(v.name, v);
			}else{
				Variable var = variables.get(v.name);
				var.isDeclaration = var.isDeclaration || v.isDeclaration;
				var.isFieldAccess = var.isFieldAccess || v.isFieldAccess;
			}
			return v;
		}else{
			VariableType type = getType(null, vb.getType());
			String varName = vb.getName();
			if(!varNames.containsKey(varName))
				varNames.put(varName, VAR_PREFIX+varNames.size());
			Variable v = new Variable(varNames.get(varName), type);
			if(node != null
					&& node.getParent() != null){
				if(node.getParent() instanceof VariableDeclaration){
					v.isDeclaration = true;
				}else if(node.getParent() instanceof FieldAccess
						&& node.getLocationInParent().equals(FieldAccess.NAME_PROPERTY)){
					v.isFieldAccess = true;
					IVariableBinding fb = ((FieldAccess)node.getParent()).resolveFieldBinding();
					VariableType declType = getType(null, fb.getDeclaringClass());
					v.declType = declType;
				}
			}
			if(!variables.containsKey(v.name)){
				variables.put(v.name, v);
			}else{
				Variable var = variables.get(v.name);
				var.isDeclaration = var.isDeclaration || v.isDeclaration;
				var.isFieldAccess = var.isFieldAccess || v.isFieldAccess;
				if(v.declType != null)
					var.declType = v.declType;
			}
			return v;
		}
	}

	public static VariableType generateType(ITypeBinding tb){
		if(tb == null){
			return new VariableType(TYPE_PREFIX);
		}else{
			VariableType type = new VariableType(generateTypeName(tb), TreeUtils.isJSL(tb), tb.isGenericType());
			return type;
		}
	}

	private static String generateTypeName(ITypeBinding tb){
		String strType = tb.getQualifiedName();
		if(tb.isParameterizedType()){
			ITypeBinding baseType = tb.getErasure();
			strType = strType.replace(baseType.getQualifiedName(), generateTypeName(baseType));
			for(ITypeBinding argType : tb.getTypeArguments()){
				strType = strType.replace(argType.getQualifiedName(), generateTypeName(argType));
			}
		}else if(tb.isWildcardType()){
			ITypeBinding bndType = tb.getBound();
			if (bndType != null) {
				strType = strType.replace(bndType.getQualifiedName(), generateTypeName(bndType));
			}
		}else if(tb.isArray()){
			ITypeBinding elmType = tb.getElementType();
			strType = strType.replace(elmType.getQualifiedName(), generateTypeName(elmType));
		}
		return strType;
	}

	public VariableType getType(){
		return getType(null, null);
	}

	public VariableType getType(ITypeBinding tb){
		return getType(null, tb);
	}

	public VariableType getType(ASTNode node, ITypeBinding tb){
		if(tb == null){
			VariableType type = null;
			if(node == null){
				type = new VariableType(TYPE_PREFIX);
			}else{
				String strType = node.toString();
				if(!typeNames.containsKey(strType))
					typeNames.put(strType, TYPE_PREFIX+typeNames.size());
				type = new VariableType(typeNames.get(strType));
			}
			types.put(type.name, type);
			return type;
		}else{
			if(node != null){
				if(tb.isParameterizedType() && node.getNodeType() != ASTNode.PARAMETERIZED_TYPE){
					tb = tb.getErasure();
				}else if(tb.isArray() && node.getNodeType() != ASTNode.ARRAY_TYPE){
					tb = tb.getElementType();
				}
			}
			VariableType type = new VariableType(getTypeName(tb), TreeUtils.isJSL(tb), tb.isGenericType());
			if(!type.isJSL){
				types.put(type.name, type);
			}else{
				Set<String> importNames = collectImportNames(tb);
				for(String importName : importNames){
					imports.add(importName);
				}
			}
			return type;
		}
	}

	public static Set<String> collectImportNames(ITypeBinding tb){
		Set<String> importNames = new HashSet<>();
		if (tb != null && !tb.isPrimitive()) {
			if (tb.isParameterizedType()) {
				String name = tb.getErasure().getQualifiedName();
				if(name.startsWith("java") && !name.startsWith("java.lang"))
					importNames.add(name);
				for (ITypeBinding arg : tb.getTypeArguments()) {
					importNames.addAll(collectImportNames(arg));
				}
			} else if (tb.isArray()) {
				importNames.addAll(collectImportNames(tb.getElementType()));
			} else if (tb.isWildcardType()) {
				if (tb.getBound() != null)
					importNames.addAll(collectImportNames(tb.getBound()));
			} else {
				String name = tb.getQualifiedName();
				if(name.startsWith("java") && !name.startsWith("java.lang"))
					importNames.add(name);
			}
		}
		return importNames;
	}

	private String getTypeName(ITypeBinding tb){
		String strType = tb.getQualifiedName();
		if(tb.isParameterizedType()){
			ITypeBinding baseType = tb.getErasure();
			strType = strType.replace(baseType.getQualifiedName(), getTypeName(baseType));
			for(ITypeBinding argType : tb.getTypeArguments()){
				strType = strType.replace(argType.getQualifiedName(), getTypeName(argType));
			}
		}else if(tb.isWildcardType()){
			ITypeBinding bndType = tb.getBound();
			if (bndType != null) {
				strType = strType.replace(bndType.getQualifiedName(), getTypeName(bndType));
			}
		}else if(tb.isArray()){
			ITypeBinding elmType = tb.getElementType();
			strType = strType.replace(elmType.getQualifiedName(), getTypeName(elmType));
		}else if(!TreeUtils.isJSL(tb)){
			if(!typeNames.containsKey(strType))
				typeNames.put(strType, TYPE_PREFIX+typeNames.size());
			strType = typeNames.get(strType);
		}
		return strType;
	}

	public Method getMethod(ASTNode node, IMethodBinding mb){
		if(mb == null){
			Method m = null;
			if(node == null){
				m = new Method(METHOD_PREFIX, getType(), getType());
			}else{
				String name = node.toString();
				if(!methodNames.containsKey(name))
					methodNames.put(name, METHOD_PREFIX+methodNames.size());
				m = new Method(methodNames.get(name), getType(), getType());
			}
			methods.put(m.name, m);
			return m;
		}else{
			String name;
			if(node != null && node.getParent() != null
					&& node.getParent().getNodeType() == ASTNode.METHOD_DECLARATION
					|| TreeUtils.isJSL(mb.getDeclaringClass())){
				name = mb.getName();
			}else{
				if(!methodNames.containsKey(mb.getName()))
					methodNames.put(mb.getName(), METHOD_PREFIX+methodNames.size());
				name = methodNames.get(mb.getName());
			}
			Method m = new Method(name, getType(mb.getDeclaringClass()), getType(mb.getReturnType()));
			for(ITypeBinding param : mb.getParameterTypes()){
				m.parameters.add(getType(param));
			}
			if(methodNames.containsKey(mb.getName())){
				methods.put(m.name, m);
			}
			return m;
		}
	}

	public String getString(String str) {
		if(!strings.containsKey(str)) {
			strings.put(str, STRING_PREFIX+strings.size());
		}
		return strings.get(str);
	}
}
