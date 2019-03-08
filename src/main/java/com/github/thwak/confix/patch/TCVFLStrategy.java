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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.thwak.confix.coverage.CoverageInfo;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.MVTManager;
import com.github.thwak.confix.pool.Method;
import com.github.thwak.confix.pool.Requirements;
import com.github.thwak.confix.pool.Variable;
import com.github.thwak.confix.pool.VariableType;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.tree.TreeUtils;

public class TCVFLStrategy extends ConcretizationStrategy {

	public static final int UPD_SIM_WEIGHT = 10;
	public static final int W_NEG_COVERED = 10;
	public static final int W_POS_COVERED = 2;
	public static final int W_NON_COVERED = 1;
	public static final double MIN_DEFAULT_SCORE = 0.5d;
	public static final String INST_TCVFL = "tc-vfl";

	protected CoverageInfo info;
	protected Map<Object, Set<Integer>> occurrence;
	protected Map<Object, Double> scores;

	public TCVFLStrategy(CoverageInfo info, Random r){
		super(r);
		this.info = info;
		this.occurrence = new HashMap<>();
		this.scores = new HashMap<>();
	}

	@Override
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
							addOccurrence(type, n.startLine);
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
					if(mb != null && !mb.isConstructor()) {
						Method m = materials.addMethod(mb);
						addOccurrence(m, n.startLine);
					}
				}
				break;
			}
		}
		computeVFLScore();
	}

	protected void computeVFLScore() {
		for(Object e : occurrence.keySet()) {
			Set<Integer> lines = occurrence.get(e);
			double score = MIN_DEFAULT_SCORE;
			if(info != null && lines != null) {
				int sum = 0;
				for(Integer line : lines) {
					if(info.isNegCovered(line)) {
						sum += W_NEG_COVERED;
					} else if(info.isPosCovered(line)) {
						sum += W_POS_COVERED;
					} else {
						sum += W_NON_COVERED;
					}
				}
				score = (double)sum/lines.size();
			}
			scores.put(e, score);
		}
	}

	protected void collectTypes(Materials materials, ITypeBinding tb, int line) {
		if (tb != null && !tb.isPrimitive()) {
			if (tb.isParameterizedType()) {
				tb = tb.getErasure();
				collectTypes(materials, tb, line);
			} else if (tb.isArray()) {
				tb = tb.getElementType();
				collectTypes(materials, tb, line);
			} else if (tb.isWildcardType()) {
				ITypeBinding bndType = tb.getBound();
				if (bndType != null) {
					collectTypes(materials, bndType, line);
				}
			} else {
				VariableType type = new VariableType(tb);
				materials.addType(type);
				addOccurrence(type, line);
			}
		}
	}

	protected void addOccurrence(Object e, int line) {
		if(!occurrence.containsKey(e))
			occurrence.put(e, new HashSet<Integer>());
		occurrence.get(e).add(line);
	}

	@Override
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

	@Override
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
					addOccurrence(v, n.startLine);
				}else{
					if(!isLocal) {
						materials.addVariable(type, v);
						addOccurrence(v, n.startLine);
						Node qNode = n.copy();
						while(name.isQualifiedName()){
							name = ((QualifiedName)name).getQualifier();
							if(name.resolveBinding() != null){
								qNode.value = TreeUtils.getValue(name);
								qNode.astNode = name;
								collectVariables(qNode);
							}
						}
					} else {
						//Add occurrences for local variables.
						addOccurrence(v, n.startLine);
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

	@Override
	public ASTNode instantiate(Change c, TargetLocation loc, PatchInfo info){
		info.cMethods.add(INST_TCVFL);
		//If the change is an update in intermediate node,
		//copy all values from loc except for updated value.
		if(c.type.equals(Change.UPDATE)
				&& c.node.children.size() > 0){
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
							if(compatibleMethods.size() > 0){
								Method newMethod = compatibleMethods.get(rouletteWheelSelection(compatibleMethods, similarMethods));
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
							Method newMethod = compatibleMethods.get(rouletteWheelSelection(compatibleMethods, null));
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
						if(compatibleFields.size() > 0){
							Variable newField = compatibleFields.get(rouletteWheelSelection(compatibleFields, new HashSet<>(map.firstEntry().getValue())));
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
							Variable newField = compatibleFields.get(rouletteWheelSelection(compatibleFields, null));
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
						Set<Variable> preferred = null;
						if (!map.isEmpty())
							preferred = new HashSet<>(map.firstEntry().getValue());
						Variable newVar = candidates.get(rouletteWheelSelection(candidates, preferred));
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
						Variable newVar = candidates.get(rouletteWheelSelection(candidates, null));
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


	@Override
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
					Set<VariableType> similarTypes = new HashSet<>();
					if(oldType != null) {
						TreeMap<Integer, List<VariableType>> map = new TreeMap<>();
						for (VariableType t : candidates) {
							int dist = levenshteinDistance(oldType.name, t.name);
							if (!map.containsKey(dist))
								map.put(dist, new ArrayList<VariableType>());
							map.get(dist).add(t);
						}
						if (!map.isEmpty())
							similarTypes.addAll(map.firstEntry().getValue());
					}
					VariableType newType = candidates.get(rouletteWheelSelection(candidates, similarTypes));
					typeMap.put(type, newType);
					if(materials.importsForTypes.containsKey(newType))
						importNames.addAll(materials.importsForTypes.get(newType));
					remainingTypes.remove(newType);
				}else{
					List<VariableType> candidates = new ArrayList<>(remainingTypes);
					if(candidates.size() == 0)
						return false;
					VariableType newType = candidates.get(rouletteWheelSelection(candidates, null));
					typeMap.put(type, newType);
					if(materials.importsForTypes.containsKey(newType))
						importNames.addAll(materials.importsForTypes.get(newType));
					remainingTypes.remove(newType);
				}
			}
		}
		return true;
	}

	protected int rouletteWheelSelection(List<? extends Object> list, Set<? extends Object> preferred) {
		int index = -1;
		double wMax = 0.0d;
		Map<Object, Double> weightMap = new HashMap<>();
		for(Object e : list) {
			double w = scores.containsKey(e) ? scores.get(e) : MIN_DEFAULT_SCORE;
			if(preferred != null && preferred.contains(e))
				w = w * UPD_SIM_WEIGHT;
			weightMap.put(e, w);
			if(w > wMax)
				wMax = w;
		}

		while(index < 0) {
			int idx = r.nextInt(list.size());
			Object e = list.get(idx);
			if(weightMap.get(e)/wMax >= r.nextDouble()) {
				index = idx;
				break;
			}
		}
		return index;
	}

	@Override
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
