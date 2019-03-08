package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.MVTManager;
import com.github.thwak.confix.pool.Method;
import com.github.thwak.confix.pool.Requirements;
import com.github.thwak.confix.pool.Variable;
import com.github.thwak.confix.pool.VariableType;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.tree.TreeUtils;

public class ConcretizationStrategy {

	protected static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
	public static final int UPD_SIM_WEIGHT = 10;
	private static final String C_METHOD_TC = "type-compatible";
	public Materials global;
	public Materials materials;
	public Random r;
	public Set<String> importNames;
	public Set<String> declaredTypeNames;

	public ConcretizationStrategy(Random r){
		global = new Materials();
		materials = new Materials();
		this.r = r;
		importNames = new HashSet<>();
		declaredTypeNames = new HashSet<>();
	}

	@SuppressWarnings("unchecked")
	public boolean resolveType(TargetLocation loc){
		ASTNode astNode = loc.node.astNode;
		if(astNode instanceof Expression){
			Expression expr = (Expression)astNode;
			if(loc.kind == TargetLocation.DEFAULT){
				ITypeBinding tb = expr.resolveTypeBinding();
				if(tb != null)
					loc.compatibleTypes.add(tb);
			}else if(loc.kind == TargetLocation.INSERT_BEFORE || loc.kind == TargetLocation.INSERT_AFTER){
				ASTNode parent = astNode.getParent();
				int index = -1;
				IMethodBinding mb = null;
				switch(parent.getNodeType()) {
				case ASTNode.METHOD_INVOCATION:
					if(loc.desc.getId().equals(MethodInvocation.ARGUMENTS_PROPERTY.getId())) {
						MethodInvocation m = (MethodInvocation)parent;
						List<ASTNode> arguments = m.arguments();
						index = arguments.indexOf(astNode);
						mb = m.resolveMethodBinding();
					}
					return findCompatibleTypes(loc, mb, index);
				case ASTNode.SUPER_METHOD_INVOCATION:
					if(loc.desc.getId().equals(SuperMethodInvocation.ARGUMENTS_PROPERTY.getId())) {
						SuperMethodInvocation m = (SuperMethodInvocation)parent;
						List<ASTNode> arguments = m.arguments();
						index = arguments.indexOf(astNode);
						mb = m.resolveMethodBinding();
					}
					return findCompatibleTypes(loc, mb, index);
				case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
					if(loc.desc.getId().equals(SuperConstructorInvocation.ARGUMENTS_PROPERTY.getId())) {
						SuperConstructorInvocation m = (SuperConstructorInvocation)parent;
						List<ASTNode> arguments = m.arguments();
						index = arguments.indexOf(astNode);
						mb = m.resolveConstructorBinding();
					}
					return findCompatibleTypes(loc, mb, index);
				case ASTNode.CONSTRUCTOR_INVOCATION:
					if(loc.desc.getId().equals(ConstructorInvocation.ARGUMENTS_PROPERTY.getId())) {
						ConstructorInvocation m = (ConstructorInvocation)parent;
						List<ASTNode> arguments = m.arguments();
						index = arguments.indexOf(astNode);
						mb = m.resolveConstructorBinding();
					}
					return findCompatibleTypes(loc, mb, index);
				case ASTNode.CLASS_INSTANCE_CREATION:
					if(loc.desc.getId().equals(ClassInstanceCreation.ARGUMENTS_PROPERTY.getId())) {
						ClassInstanceCreation m = (ClassInstanceCreation)parent;
						List<ASTNode> arguments = m.arguments();
						index = arguments.indexOf(astNode);
						mb = m.resolveConstructorBinding();
					}
					return findCompatibleTypes(loc, mb, index);
				}
			}
		}
		return true;
	}

	private boolean findCompatibleTypes(TargetLocation loc, IMethodBinding mb, int index) {
		if(index == -1 || mb == null)
			return false;
		int argIndex = loc.kind == TargetLocation.INSERT_BEFORE ? index : index+1;
		boolean hasCompitible = false;
		ITypeBinding[] oldParams = mb.getParameterTypes();
		ITypeBinding decl = mb.getDeclaringClass();
		IMethodBinding[] methods = decl.getDeclaredMethods();
		for(IMethodBinding m : methods) {
			if(!m.getName().equals(mb.getName()))
				continue;
			ITypeBinding[] params = m.getParameterTypes();
			if(params.length == oldParams.length+1) {
				boolean match = true;
				for(int i=0; i<params.length; i++) {
					if(i < argIndex) {
						if(!params[i].equals(oldParams[i])) {
							match = false;
							break;
						}
					} else if (i > argIndex){
						if(!params[i].equals(oldParams[i-1])) {
							match = false;
							break;
						}
					}
				}
				if(match) {
					hasCompitible = true;
					loc.compatibleTypes.add(params[argIndex]);
				}
			}
		}
		return hasCompitible;
	}

	public void collectMaterials(Node root) {
		List<Node> nodes = TreeUtils.traverse(root);
		for(Node n : nodes) {
			if(n.type == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment)n.astNode;
				IVariableBinding vb = vdf.resolveBinding();
				//If it is a local variable declaration.
				if(Materials.isLocal(vb)) {
					ITypeBinding tb = vb.getType();
					VariableType type = MVTManager.generateType(tb);
					String varName = vdf.getName().getIdentifier();
					String varKey = vb.getKey();
					Variable v = new Variable(varName, type);
					materials.addLocalVariable(type, v, varKey, n.startLine);
					if(tb != null){
						String keyPrefix = varKey.substring(0, varKey.lastIndexOf('#')+1);
						for(IVariableBinding field : tb.getDeclaredFields()){
							if(Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())){
								type = new VariableType(field.getType());
								v = new Variable(varName + "." + field.getName(), type);
								materials.addLocalVariable(type, v, keyPrefix+v.name, n.startLine);
								v = new Variable(field.getName(), type);
								v.declType = MVTManager.generateType(field.getDeclaringClass());
								materials.addField(type, v);
							}
						}
						for(IMethodBinding mb : tb.getDeclaredMethods()){
							if(Modifier.isPublic(mb.getModifiers())
									&& !Modifier.isStatic(mb.getModifiers())
									&& !mb.isConstructor()){
								materials.addMethod(mb);
							}
						}
					}
				}
			}
			if(n.type == ASTNode.STRING_LITERAL) {
				materials.addString(n.value);
			}
			switch(n.kind){
			case IBinding.TYPE:
				if(n.astNode instanceof Type &&
						!(n.astNode instanceof PrimitiveType)){
					VariableType type = null;
					Type t = (Type)n.astNode;
					ITypeBinding tb = t.resolveBinding();
					if(tb != null){
						if(tb.isParameterizedType() && n.type != ASTNode.PARAMETERIZED_TYPE){
							if(tb.getErasure() != null)
								type = MVTManager.generateType(tb.getErasure());
						}else if(tb.isArray() && n.type != ASTNode.ARRAY_TYPE){
							if(tb.getElementType() != null)
								type = MVTManager.generateType(tb.getElementType());
						}else{
							type = MVTManager.generateType(tb);
						}
						//Collect static fields and methods.
						if(!global.types.contains(type))
							collectStaticMembers(tb, materials);
					}
					if(type != null && !type.isJSL){
						if(n.astNode instanceof SimpleType || n.astNode instanceof QualifiedType){
							materials.addType(type);
							materials.addImports(type, tb);
						}else{
							collectTypes(materials, tb);
						}
					}
				}
				break;
			case IBinding.VARIABLE:
				if(n.astNode instanceof Name){
					collectVariables(n);
				}
				break;
			case IBinding.METHOD:
				if(n.astNode instanceof Name){
					Name name = (Name)n.astNode;
					IMethodBinding mb = (IMethodBinding)name.resolveBinding();
					if(mb != null && !mb.isConstructor())
						materials.addMethod(mb);
				}
				break;
			}
		}
	}

	protected void collectTypes(Materials materials, ITypeBinding tb) {
		if (tb != null && !tb.isPrimitive()) {
			if (tb.isParameterizedType()) {
				tb = tb.getErasure();
				collectTypes(materials, tb);
			} else if (tb.isArray()) {
				tb = tb.getElementType();
				collectTypes(materials, tb);
			} else if (tb.isWildcardType()) {
				ITypeBinding bndType = tb.getBound();
				if (bndType != null) {
					collectTypes(materials, bndType);
				}
			} else {
				materials.addType(tb);
			}
		}
	}

	protected void collectStaticMembers(ITypeBinding tb, Materials materials) {
		for(IVariableBinding field : tb.getDeclaredFields()){
			if(Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())){
				VariableType type = new VariableType(field.getType());
				Variable v = new Variable(tb.getName() + "." + field.getName(), type);
				materials.addVariable(type, v);
			}
		}
		for(IMethodBinding mb : tb.getDeclaredMethods()){
			if(Modifier.isPublic(mb.getModifiers()) && Modifier.isStatic(mb.getModifiers())
					&& !mb.isConstructor()){
				materials.addMethod(mb);
			}
		}
	}

	protected void collectVariables(Node n) {
		Variable v = null;
		VariableType type = null;
		Name name = (Name)n.astNode;
		IBinding binding = name.resolveBinding();
		if(binding != null){
			if(binding.getKind() == IBinding.TYPE){
				//Need to handle types in case of recursive calls.
				ITypeBinding tb = (ITypeBinding)binding;
				type = MVTManager.generateType(tb);
				collectTypes(materials, tb);
			}else if(binding.getKind() == IBinding.VARIABLE){
				IVariableBinding vb = (IVariableBinding)binding;
				ITypeBinding tb = vb.getType();
				type = MVTManager.generateType(tb);
				collectTypes(materials, tb);
				v = new Variable(n.value, type);
				boolean isLocal = Materials.isLocal(vb);
				if(name.getParent() instanceof FieldAccess
						&& name.getLocationInParent().equals(FieldAccess.NAME_PROPERTY)){
					v.isFieldAccess = true;
					IVariableBinding fb = ((FieldAccess)name.getParent()).resolveFieldBinding();
					VariableType declType = MVTManager.generateType(fb.getDeclaringClass());
					v.declType = declType;
					materials.addField(type, v);
				}else{
					if(!isLocal) {
						materials.addVariable(type, v);
						Node qNode = n.copy();
						while(name.isQualifiedName()){
							name = ((QualifiedName)name).getQualifier();
							if(name.resolveBinding() != null){
								qNode.value = TreeUtils.getValue(name);
								qNode.astNode = name;
								collectVariables(qNode);
							}
						}
					}
				}
				//Collect fields and methods of the variable type.
				if(tb != null){
					for(IVariableBinding field : tb.getDeclaredFields()){
						if(Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())){
							type = new VariableType(field.getType());
							v = new Variable(n.value + "." + field.getName(), type);
							if(!isLocal) {
								materials.addVariable(type, v);
							}
							v = new Variable(field.getName(), type);
							v.declType = MVTManager.generateType(field.getDeclaringClass());
							materials.addField(type, v);
						}
					}
					for(IMethodBinding mb : tb.getDeclaredMethods()){
						if(Modifier.isPublic(mb.getModifiers())
								&& !Modifier.isStatic(mb.getModifiers())
								&& !mb.isConstructor()){
							materials.addMethod(mb);
						}
					}
				}
			}
		}
	}

	public void updateLocInfo(TargetLocation loc) {
		Node method = PatchUtils.findMethod(loc.node);
		if(method.type == ASTNode.METHOD_DECLARATION) {
			MethodDeclaration md = (MethodDeclaration)method.astNode;
			IMethodBinding mb = md.resolveBinding();
			if(mb != null)
				loc.methodKey = mb.getKey();
			loc.blockIndex = PatchUtils.resolveBlockIndex(method, loc.node);
		}
	}



	/**
	 * Check whether all types, variables, and methods in the given change can be instantiated with
	 * collected values from the given target location.
	 * @param change a change to be applied.
	 * @param loc a target location where the change will be applied.
	 * @return {@code true} if the given {@code change} can be instantiated with information in {@code loc}, {@code false} otherwise.
	 */
	public boolean instCheck(Change change, TargetLocation loc) {
		if(change.type.equals(Change.UPDATE)
				&& change.node.type == loc.node.type
				&& change.node.children.size() > 0){
			if(change.node.hashString == null){
				change.node.hashString = TreeUtils.getTypeHash(change.node);
			}
			if(loc.node.hashString == null){
				loc.node.hashString = TreeUtils.getTypeHash(loc.node);
			}
			return change.node.hashString.equals(loc.node.hashString);
		}
		Requirements reqs = change.requirements;

		int count = materials.genericTypes.size();
		if(change.type.equals(Change.UPDATE)
				&& loc.kind == Node.K_TYPE) {
			count--;	//Deduct one for an update.
		}
		if(reqs.types.size() > materials.types.size()
				&& reqs.genericTypes.size() > count){
			if(DEBUG)
				System.out.println("Not enough types.");
			return false;
		}

		for(VariableType type : reqs.variables.keySet()){
			if(type.isJSL || change.type.equals(Change.UPDATE)){
				//For update, check loc's type instead.
				if(change.type.equals(Change.UPDATE)) {
					type = MVTManager.generateType(loc.getType());
				}
				List<Variable> vars = new ArrayList<>();
				if (materials.variables.containsKey(type))
					vars.addAll(materials.variables.get(type));
				vars.addAll(materials.getLocalVariables(loc.methodKey, type, loc.blockIndex, loc.node.startLine));
				if (global.variables.containsKey(type))
					vars.addAll(global.variables.get(type));
				count = vars.size();
				if(change.type.equals(Change.UPDATE))
					for(Variable v : vars)
						if(v.name.equals(loc.node.value))
							count--;
				if (reqs.variables.containsKey(type) && reqs.variables.get(type).size() > count) {
					if(DEBUG)
						System.out.println("Not enough variables of type - " + type);
					return false;
				}
			}
		}

		for(VariableType type : reqs.fields.keySet()){
			if (type.isJSL || change.type.equals(Change.UPDATE)) {
				if(change.type.equals(Change.UPDATE)) {
					type = MVTManager.generateType(loc.getType());
				}
				List<Variable> fields = new ArrayList<>();
				if(materials.fields.containsKey(type))
					fields.addAll(materials.fields.get(type));
				count = fields.size();
				if(change.type.equals(Change.UPDATE))
					for(Variable f : fields)
						if(f.name.equals(loc.node.value))
							count--;
				if(reqs.fields.containsKey(type) && reqs.fields.get(type).size() > count){
					if(DEBUG)
						System.out.println("Not enough fields of type - "+type);
					return false;
				}
			}
		}

		for(String absSignature : reqs.methods.keySet()){
			if(change.type.equals(Change.UPDATE)){
				if(loc.node.kind == Node.K_METHOD){
					if(loc.node.astNode instanceof Name){
						Name name = (Name)loc.node.astNode;
						IMethodBinding mb = (IMethodBinding)name.resolveBinding();
						Method m = new Method(mb);
						absSignature = m.getAbstractSignature();
					}
				}else{
					VariableType newReturnType = MVTManager.generateType(loc.getType());
					Method m = (Method)reqs.methods.get(absSignature).toArray()[0];
					Method newMethod = new Method(m.name, m.declaringClass, newReturnType);
					newMethod.parameters.addAll(m.parameters);
					absSignature = newMethod.getAbstractSignature();
				}
			}
			if(!materials.methods.containsKey(absSignature)){
				if(DEBUG)
					System.out.println("No methods of abstract signature - "+absSignature);
				return false;
			}else{
				count = materials.methods.get(absSignature).size();
				if(change.type.equals(Change.UPDATE)
						&& loc.node.kind == Node.K_METHOD) {
					count--;
				}
				if(reqs.methods.containsKey(absSignature) &&
						reqs.methods.get(absSignature).size() > count){
					if(DEBUG)
						System.out.println("Not enough methods of abstract signature - "+absSignature);
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Adapt a given abstract change {@code c} to a given target location {@code loc} with collected materials,
	 * and returns an {@code ASTNode} which can be used for change application.
	 *
	 * @param c a change to be applied.
	 * @param loc a location where the change will be applied.
	 * @return an instance of {@code ASTNode} can be used to modify an AST.
	 */
	public ASTNode instantiate(Change c, TargetLocation loc, PatchInfo info){
		info.cMethods.add(C_METHOD_TC);
		//If the change is an update in intermediate node,
		//copy all values from loc except for updated value.
		if(c.type.equals(Change.UPDATE)
				&& c.node.children.size() > 0){
			return updateIntermediate(c, loc);
		}

		importNames.clear();
		Map<String, String> varMapping = new HashMap<>();
		Map<String, String> methodMapping = new HashMap<>();
		Map<String, String> typeMapping = new HashMap<>();
		Map<VariableType, VariableType> typeMap = new HashMap<>();
		Requirements reqs = c.requirements;

		//Assign methods.
		if(reqs.methods.size() > 0) {
			if(c.type.equals(Change.UPDATE)
					&& c.node.type == loc.node.type
					&& c.node.kind == loc.node.kind){
				for(Set<Method> cMethods : reqs.methods.values()){
					if(cMethods.size() > 0){
						//Getting abstract signature.
						String absSignature = null;
						if(loc.node.kind == IBinding.METHOD){
							Name name = (Name)loc.node.astNode;
							IMethodBinding mb = (IMethodBinding)name.resolveBinding();
							absSignature = new Method(mb).getAbstractSignature();
						}else{
							VariableType newReturnType = MVTManager.generateType(loc.getType());
							Method m = (Method)cMethods.toArray()[0];
							Method newMethod = new Method(m.name, m.declaringClass, newReturnType);
							newMethod.parameters.addAll(m.parameters);
							absSignature = newMethod.getAbstractSignature();
						}

						String oldName = loc.node.value;
						Set<Method> locMethods = new HashSet<>();
						for(Method m : materials.methods.get(absSignature)) {
							if(!oldName.equals(m.name))
								locMethods.add(m);
						}
						Set<Method> similarMethods = new HashSet<>();
						TreeMap<Integer, List<Method>> map = new TreeMap<>();
						for(Method cm : locMethods){
							int dist = levenshteinDistance(oldName, cm.name);
							if(!map.containsKey(dist))
								map.put(dist, new ArrayList<Method>());
							map.get(dist).add(cm);
						}
						if(!map.isEmpty() && map.firstKey() != null)
							similarMethods.addAll(map.firstEntry().getValue());
						for(Method method : cMethods){
							List<Method> compatibleMethods = new ArrayList<>(locMethods);
							for(int i=1; i<UPD_SIM_WEIGHT; i++)
								compatibleMethods.addAll(similarMethods);
							if(compatibleMethods.size() > 0){
								Method newMethod = compatibleMethods.get(r.nextInt(compatibleMethods.size()));
								if(!methodMapping.containsKey(method.name)){
									methodMapping.put(method.name, newMethod.name);
									//Prevent double mapping.
									locMethods.remove(newMethod);
									similarMethods.remove(newMethod);
									updateTypeMap(method, newMethod, typeMap);
									if(materials.importsForMethods.containsKey(newMethod)){
										importNames.addAll(materials.importsForMethods.get(newMethod));
									}
								}
							}else{
								if(DEBUG)
									System.out.println("Not enough methods to assign.");
								return null;
							}
						}
					}
				}
			}else{
				for(String absSignature : reqs.methods.keySet()){
					Set<Method> cMethods = reqs.methods.get(absSignature);
					Set<Method> locMethods = new HashSet<>(materials.methods.get(absSignature));
					if(global.methods.containsKey(absSignature))
						locMethods.addAll(global.methods.get(absSignature));
					for(Method method : cMethods){
						List<Method> compatibleMethods = new ArrayList<>();
						for(Method m : locMethods){
							if(checkCompatible(method, m, reqs, materials, typeMap))
								compatibleMethods.add(m);
						}
						if(compatibleMethods.size() > 0){
							Method newMethod = compatibleMethods.get(r.nextInt(compatibleMethods.size()));
							if(!methodMapping.containsKey(method.name)){
								methodMapping.put(method.name, newMethod.name);
								locMethods.remove(newMethod);
								updateTypeMap(method, newMethod, typeMap);
								if(materials.importsForMethods.containsKey(newMethod)){
									importNames.addAll(materials.importsForMethods.get(newMethod));
								}
							}
						}else{
							if(DEBUG)
								System.out.println("Not enough methods to assign.");
							return null;
						}
					}
				}
			}
		}

		//For variable updates, match variable types.
		if(c.type.equals(Change.UPDATE) && c.node.kind == Node.K_VARIABLE){
			if(c.requirements.variables.size() == 1){
				VariableType t = (VariableType)c.requirements.variables.keySet().toArray()[0];
				if(!t.isJSL){
					VariableType locType = MVTManager.generateType(loc.getType());
					typeMap.put(t, locType);
				}
			}else if(c.requirements.fields.size() == 1){
				VariableType t = (VariableType)c.requirements.fields.keySet().toArray()[0];
				if(!t.isJSL){
					VariableType locType = MVTManager.generateType(loc.getType());
					typeMap.put(t, locType);
				}
			}
		}

		//Assign types.
		VariableType t = null;
		if(c.type.equals(Change.UPDATE)
				&& c.node.type == loc.node.type
				&& c.node.kind == Node.K_TYPE) {
			t = MVTManager.generateType(loc.getType());
		}
		if(!assignTypes(typeMap, reqs, materials, false, t)){
			if(DEBUG)
				System.out.println("Not enough types to assign.");
			return null;
		}

		if(!assignTypes(typeMap, reqs, materials, true, t)){
			if(DEBUG)
				System.out.println("Not enough generic types to assign.");
			return null;
		}

		if(c.type.equals(Change.REPLACE)){
			//In case of replace, keep declared variable names unchanged.
			List<Node> locNodes = TreeUtils.traverse(loc.node);
			List<Node> cNodes = TreeUtils.traverse(c.node);
			for(int i=0; i<locNodes.size(); i++){
				Node cNode = cNodes.get(i);
				if(reqs.declaredVariables.contains(cNode.value)){
					Node locNode = locNodes.get(i);
					if(locNode.value != null && !varMapping.containsKey(cNode.value))
						varMapping.put(cNode.value, locNode.value);
				}
			}
		}else{
			int count = reqs.declaredVariables.size();
			for(String oldValue : reqs.declaredVariables){
				String newValue = oldValue;
				while(materials.varNames.contains(newValue)){
					newValue = TreeUtils.VAR_PREFIX+count++;
				}
				if(!varMapping.containsKey(oldValue))
					varMapping.put(oldValue, newValue);
			}
		}

		//Field assignments.
		if(reqs.fields.size() > 0) {
			if(c.type.equals(Change.UPDATE)
					&& c.node.type == loc.node.type
					&& c.node.kind == Node.K_VARIABLE){
				VariableType type = MVTManager.generateType(loc.getType());
				Set<Variable> locFields = new HashSet<>();
				if(materials.fields.containsKey(type))
					locFields.addAll(materials.fields.get(type));
				Name name = (Name)loc.node.astNode;
				String oldName = loc.node.value;
				IVariableBinding vb = (IVariableBinding)name.resolveBinding();
				VariableType declType = MVTManager.generateType(vb.getDeclaringClass());
				for(Set<Variable> fields : reqs.fields.values()){
					for (Variable field : fields) {
						List<Variable> compatibleFields = new ArrayList<>();
						for (Variable locField : locFields) {
							if (declType.isSameType(locField.declType) && !oldName.equals(locField.name))
								compatibleFields.add(locField);
						}
						if (compatibleFields.size() == 0) {
							if (DEBUG) {
								System.out.print("Not enough fields to assign - ");
								System.out.print(field.name);
								System.out.print("(");
								System.out.print(field.type);
								System.out.print("/");
								System.out.print(declType);
								System.out.println(")");
							}
							return null;
						}
						TreeMap<Integer, List<Variable>> map = new TreeMap<>();
						for (Variable f : compatibleFields) {
							int dist = levenshteinDistance(oldName, f.name);
							if (!map.containsKey(dist))
								map.put(dist, new ArrayList<Variable>());
							map.get(dist).add(f);
						}
						if (!map.isEmpty())
							for(int i=1; i<UPD_SIM_WEIGHT; i++)
								compatibleFields.addAll(map.firstEntry().getValue());
						if(compatibleFields.size() > 0){
							Variable newField = compatibleFields.get(r.nextInt(compatibleFields.size()));
							if(!varMapping.containsKey(field.name)){
								varMapping.put(field.name, newField.name);
								locFields.remove(newField);
							}
						}else{
							if(DEBUG) {
								System.out.print("Not enough fields to assign - ");
								System.out.print(field.name);
								System.out.print("(");
								System.out.print(field.type);
								System.out.print("/");
								System.out.print(declType);
								System.out.println(")");
							}
							return null;
						}
					}
				}
			}else{
				for(VariableType type : reqs.fields.keySet()){
					Set<Variable> fields = reqs.fields.get(type);
					if(typeMap.containsKey(type))
						type = typeMap.get(type);
					Set<Variable> locFields = new HashSet<>();
					if(materials.fields.containsKey(type))
						locFields.addAll(materials.fields.get(type));
					for(Variable field : fields){
						VariableType declType = typeMap.containsKey(field.declType) ? typeMap.get(field.declType) : field.declType;
						if(locFields.size() == 0){
							if(DEBUG) {
								System.out.print("Not enough fields to assign.");
								System.out.print(field.name);
								System.out.print("(");
								System.out.print(field.type);
								System.out.print("/");
								System.out.print(declType);
								System.out.println(")");
							}
							return null;
						}
						List<Variable> compatibleFields = new ArrayList<>();
						for(Variable locField : locFields){
							if(declType.isSameType(locField.declType))
								compatibleFields.add(locField);
						}
						if(compatibleFields.size() > 0){
							Variable newField = compatibleFields.get(r.nextInt(compatibleFields.size()));
							if(!varMapping.containsKey(field.name)){
								varMapping.put(field.name, newField.name);
								locFields.remove(newField);
							}
						}else{
							if(DEBUG) {
								System.out.print("Not enough fields to assign - ");
								System.out.print(field.name);
								System.out.print("(");
								System.out.print(field.type);
								System.out.print("/");
								System.out.print(declType);
								System.out.println(")");
							}
							return null;
						}
					}
				}
			}
		}

		//Other variables.
		if(reqs.variables.size() > 0) {
			if(c.type.equals(Change.UPDATE)
					&& c.node.type == loc.node.type
					&& c.node.kind == loc.node.kind){
				String oldName = loc.node.value;
				VariableType type = MVTManager.generateType(loc.getType());
				List<Variable> locVariables = new ArrayList<>();
				if (materials.variables.containsKey(type))
					for(Variable v : materials.variables.get(type))
						if(!oldName.equals(v.name))
							locVariables.add(v);
				Set<Variable> localVars = materials.getLocalVariables(loc.methodKey, type, loc.blockIndex, loc.node.startLine);
				for(Variable v : localVars) {
					if(!oldName.equals(v.name))
						locVariables.add(v);
				}
				for (Set<Variable> vars : reqs.variables.values()) {
					if(vars.size() > 0 && locVariables.size() == 0){
						if(DEBUG)
							System.out.println("Not enough variables to assign.");
						if (global.variables.containsKey(type))
							for(Variable v : global.variables.get(type))
								if(!oldName.equals(v.name))
									locVariables.add(v);
						if(locVariables.size() == 0) {
							if(DEBUG)
								System.out.println("Not enough global variables to assign.");
							return null;
						}
					}
					for (Variable var : vars) {
						List<Variable> candidates = new ArrayList<>();
						candidates.addAll(locVariables);
						TreeMap<Integer, List<Variable>> map = new TreeMap<>();
						for (Variable f : candidates) {
							int dist = levenshteinDistance(oldName, f.name);
							if(!map.containsKey(dist))
								map.put(dist, new ArrayList<Variable>());
							map.get(dist).add(f);
						}
						//Give more weight to the most similar ones.
						if (!map.isEmpty())
							for(int i=1; i<UPD_SIM_WEIGHT; i++)
								candidates.addAll(map.firstEntry().getValue());
						Variable newVar = candidates.get(r.nextInt(candidates.size()));
						if (!varMapping.containsKey(var.name)) {
							varMapping.put(var.name, newVar.name);
							locVariables.remove(newVar);
						}
					}
				}
			}else{
				for(VariableType type : reqs.variables.keySet()){
					Set<Variable> variables = reqs.variables.get(type);
					if(typeMap.containsKey(type))
						type = typeMap.get(type);
					List<Variable> locVariables = new ArrayList<>();
					if(materials.variables.containsKey(type))
						locVariables.addAll(materials.variables.get(type));
					locVariables.addAll(materials.getLocalVariables(loc.methodKey, type, loc.blockIndex, loc.node.startLine));
					for (Variable var : variables) {
						if(locVariables.size() == 0){
							if(DEBUG)
								System.out.println("Not enough local variables to assign.");
							if(global.variables.containsKey(type))
								locVariables.addAll(global.variables.get(type));
							if(locVariables.size() == 0) {
								if(DEBUG)
									System.out.println("Not enough global variables to assign.");
								return null;
							}
						}
						List<Variable> candidates = locVariables;
						Variable newVar = candidates.get(r.nextInt(candidates.size()));
						if (!varMapping.containsKey(var.name)) {
							varMapping.put(var.name, newVar.name);
							locVariables.remove(newVar);
						}
					}
				}
			}
		}

		for(Entry<VariableType, VariableType> e : typeMap.entrySet()){
			if(!typeMapping.containsKey(e.getKey().name))
				typeMapping.put(e.getKey().name, e.getValue().name);
			if(!typeMapping.containsKey(e.getKey().qualifiedName))
				typeMapping.put(e.getKey().qualifiedName, e.getValue().qualifiedName);
		}

		Map<String, String> strMapping = new HashMap<>();
		if(reqs.strings.size() > 0) {
			List<String> literals = new ArrayList<>(materials.strings.keySet());
			for(String absStr : reqs.strings) {
				int index = PatchUtils.rouletteWheelSelection(literals, materials.strings, materials.maxStrFreq, r);
				if(index < 0) {
					strMapping.put(absStr, "\"\"");
				} else {
					strMapping.put(absStr, literals.get(index));
					literals.remove(index);
				}
			}
		}

		importNames.removeAll(declaredTypeNames);

		Node copied = TreeUtils.deepCopy(c.type.equals(Change.UPDATE) || c.type.equals(Change.REPLACE) ? c.location : c.node);
		List<Node> nodes = TreeUtils.traverse(copied);
		for(Node n : nodes){
			if(n.value != null){
				Map<String, String> mapping = null;
				switch(n.kind){
				case Node.K_VARIABLE:
					mapping = varMapping;
					break;
				case Node.K_TYPE:
					mapping = typeMapping;
					break;
				case Node.K_METHOD:
					mapping = methodMapping;
					break;
				default:
					mapping = varMapping;
				}
				if(n.type == ASTNode.STRING_LITERAL && n.normalized) {
					mapping = strMapping;
				}
				if(mapping.containsKey(n.value)){
					n.value = mapping.get(n.value);
				}
			}
		}
		AST ast = loc.node.astNode.getAST();
		ASTNode astNode = null;
		astNode = TreeUtils.generateNode(copied, ast);
		return astNode;
	}

	protected int selectString(List<String> list) {
		if(list.size() == 0)
			return -1;
		int index = -1;
		while(index < 0) {
			int idx = r.nextInt(list.size());
			double freq = materials.strings.get(list.get(idx));
			if(freq/materials.maxStrFreq >= r.nextDouble()) {
				index = idx;
				break;
			}
		}
		return index;
	}

	protected ASTNode updateIntermediate(Change c, TargetLocation loc) {
		Node copied = TreeUtils.deepCopy(c.type.equals(Change.UPDATE) || c.type.equals(Change.REPLACE) ? c.location : c.node);
		List<Node> nodes = TreeUtils.traverse(copied);
		List<Node> locNodes = TreeUtils.traverse(loc.node);
		if(nodes.size() != locNodes.size())
			return null;
		for(int i=1; i<nodes.size(); i++){
			Node node = nodes.get(i);
			Node locNode = locNodes.get(i);
			node.value = locNode.value;
		}
		AST ast = loc.node.astNode.getAST();
		ASTNode astNode = null;
		astNode = TreeUtils.generateNode(copied, ast);
		return astNode;
	}

	public int levenshteinDistance(String str1, String str2){
		if(str1.equals(str2))
			return 0;
		else if(str1.length() == 0)
			return str2.length();
		else if(str2.length() == 0)
			return str1.length();
		int[] vec0 = new int[str2.length()+1];
		int[] vec1 = new int[str2.length()+1];
		for(int i=0; i<vec0.length; i++)
			vec0[i] = i;
		for(int i=0; i<str1.length(); i++){
			vec1[0] = i+1;
			for(int j=0; j<str2.length(); j++){
				int cost = str1.charAt(i) == str2.charAt(j) ? 0 : 1;
				vec1[j+1] = Math.min(vec1[j]+1, vec0[j+1]+1);
				if(vec1[j+1] > vec0[j] + cost)
					vec1[j+1] = vec0[j] + cost;
			}
			System.arraycopy(vec1, 0, vec0, 0, vec0.length);
		}
		return vec1[str2.length()];
	}

	protected boolean assignTypes(Map<VariableType, VariableType> typeMap, Requirements reqs, Materials materials, boolean isGeneric, VariableType oldType) {
		Set<VariableType> reqTypes = isGeneric ? reqs.genericTypes : reqs.types;
		Set<VariableType> remainingTypes = new HashSet<>(isGeneric ? materials.genericTypes : materials.types);
		remainingTypes.addAll(isGeneric ? global.genericTypes : global.types);
		remainingTypes.removeAll(typeMap.values());
		if(oldType != null) {
			remainingTypes.remove(oldType);
		}
		for(VariableType type : reqTypes){
			if(!typeMap.containsKey(type)){
				int reqVarCount = reqs.variables.containsKey(type) ? reqs.variables.get(type).size() : 0;
				int reqFieldCount = reqs.fields.containsKey(type) ? reqs.fields.get(type).size() : 0;
				int reqDeclaredFieldCount = 0;
				for(Set<Variable> fields : reqs.fields.values())
					for(Variable field : fields)
						if(type.isSameType(field.declType))
							reqDeclaredFieldCount++;
				if(reqVarCount > 0
						|| reqFieldCount > 0
						|| reqDeclaredFieldCount > 0){
					List<VariableType> candidates = new ArrayList<>();
					for(VariableType locType : remainingTypes){
						int locVarCount = materials.variables.containsKey(locType) ? materials.variables.get(locType).size() : 0;
						int locFieldCount = materials.fields.containsKey(locType) ? materials.fields.get(locType).size() : 0;
						int locDeclaredFieldCount = 0;
						for(Set<Variable> fields : materials.fields.values())
							for(Variable field : fields)
								if(locType.isSameType(field.declType))
									locDeclaredFieldCount++;
						if(locVarCount >= reqVarCount
								&& locFieldCount >= reqFieldCount
								&& locDeclaredFieldCount >= reqDeclaredFieldCount)
							candidates.add(locType);
					}
					if(candidates.size() == 0){
						return false;
					}
					//Check similarity for update changes.
					if(oldType != null) {
						TreeMap<Integer, List<VariableType>> map = new TreeMap<>();
						for (VariableType t : candidates) {
							int dist = levenshteinDistance(oldType.name, t.name);
							if (!map.containsKey(dist))
								map.put(dist, new ArrayList<VariableType>());
							map.get(dist).add(t);
						}
						if (!map.isEmpty())
							for(int i=1; i<UPD_SIM_WEIGHT; i++)
								candidates.addAll(map.firstEntry().getValue());
					}
					VariableType newType = candidates.get(r.nextInt(candidates.size()));
					typeMap.put(type, newType);
					if(materials.importsForTypes.containsKey(newType))
						importNames.addAll(materials.importsForTypes.get(newType));
					remainingTypes.remove(newType);
				}else{
					List<VariableType> candidates = new ArrayList<>(remainingTypes);
					if(candidates.size() == 0)
						return false;
					VariableType newType = candidates.get(r.nextInt(candidates.size()));
					typeMap.put(type, newType);
					if(materials.importsForTypes.containsKey(newType))
						importNames.addAll(materials.importsForTypes.get(newType));
					remainingTypes.remove(newType);
				}
			}
		}
		return true;
	}

	protected void updateTypeMap(Method method, Method newMethod, Map<VariableType, VariableType> typeMap) {
		Map<VariableType, VariableType> mapping = new HashMap<>();
		if(findMappings(method.declaringClass, newMethod.declaringClass, mapping)){
			for(Entry<VariableType, VariableType> e : mapping.entrySet()){
				if(!typeMap.containsKey(e.getKey())){
					typeMap.put(e.getKey(), e.getValue());
				}
			}
		}
		mapping.clear();
		if(findMappings(method.returnType, newMethod.returnType, mapping)){
			for(Entry<VariableType, VariableType> e : mapping.entrySet()){
				if(!typeMap.containsKey(e.getKey())){
					typeMap.put(e.getKey(), e.getValue());
				}
			}
		}
		if(method.parameters.size() == newMethod.parameters.size()){
			for(int i=0; i<method.parameters.size(); i++){
				VariableType oldType = method.parameters.get(i);
				VariableType newType = newMethod.parameters.get(i);
				mapping.clear();
				if(findMappings(oldType, newType, mapping)){
					for(Entry<VariableType, VariableType> e : mapping.entrySet()){
						if(!typeMap.containsKey(e.getKey())){
							typeMap.put(e.getKey(), e.getValue());
						}
					}
				}
			}
		}
	}

	protected boolean checkCompatible(Method cMethod, Method locMethod, Requirements reqs, Materials materials, Map<VariableType, VariableType> typeMap) {
		Map<VariableType, VariableType> mapping = new HashMap<>();
		if(cMethod.declaringClass.isCompatible(locMethod.declaringClass)){
			mapping.put(cMethod.declaringClass, locMethod.declaringClass);
			findMappings(cMethod.declaringClass, locMethod.declaringClass, mapping);
		}
		if(cMethod.returnType.isCompatible(locMethod.returnType)){
			mapping.put(cMethod.returnType, locMethod.returnType);
			findMappings(cMethod.returnType, locMethod.returnType, mapping);
		}
		for(int i=0; i<cMethod.parameters.size(); i++){
			VariableType cParamType = cMethod.parameters.get(i);
			VariableType locParamType = locMethod.parameters.get(i);
			if(cParamType.isCompatible(locParamType)){
				mapping.put(cParamType, locParamType);
				findMappings(cParamType, locParamType, mapping);
			}
		}
		for(Entry<VariableType, VariableType> entry : mapping.entrySet()){
			if(typeMap.containsKey(entry.getKey())){
				if(!typeMap.get(entry.getKey()).isSameType(entry.getValue()))
					return false;
			}else{
				int reqVarCount = reqs.variables.containsKey(entry.getKey()) ? reqs.variables.get(entry.getKey()).size() : 0;
				int reqFieldCount = reqs.fields.containsKey(entry.getKey()) ? reqs.fields.get(entry.getKey()).size() : 0;
				int locVarCount = materials.variables.containsKey(entry.getValue()) ? materials.variables.get(entry.getValue()).size() : 0;
				int locFieldCount = materials.fields.containsKey(entry.getValue()) ? materials.fields.get(entry.getValue()).size() : 0;
				if(reqVarCount > locVarCount
						|| reqFieldCount > locFieldCount)
					return false;
			}
		}
		return true;
	}

	protected boolean findMappings(VariableType cType, VariableType locType, Map<VariableType, VariableType> mapping) {
		boolean containsNonJSL = false;
		String[] cTypes = cType.qualifiedName.split("[\\[\\],<>]");
		String[] locTypes = locType.qualifiedName.split("[\\[\\],<>]");
		if(cTypes.length == locTypes.length){
			for(int idx=0; idx<cTypes.length; idx++){
				if(cTypes[idx].trim().startsWith(MVTManager.TYPE_PREFIX)){
					VariableType type1 = new VariableType(cTypes[idx], false, false);
					VariableType type2 = new VariableType(locTypes[idx], false, false);
					mapping.put(type1, type2);
					containsNonJSL = true;
				}
			}
		}
		return containsNonJSL;
	}

	@SuppressWarnings("unchecked")
	public void collectGlobalMaterials(Parser parser, String className, Node root, CompilationUnit cu){
		global = new Materials();
		//Collect all imported types.
		List<ImportDeclaration> importDecls = cu.imports();
		for(ImportDeclaration decl : importDecls){
			//Must be a type.
			ITypeBinding tb = decl.getName().resolveTypeBinding();
			if(tb != null){
				VariableType type = MVTManager.generateType(tb);
				if(type.isGeneric){
					//In case of generic types, it's possible that types in JSL are included in requirements.
					global.genericTypes.add(type);
				}else if(!type.isJSL){
					//Otherwise, collect non-JSL types only.
					global.types.add(type);
				}
				collectStaticMembers(tb, global);
			}
		}
	}
}
