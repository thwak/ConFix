package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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

public class NeighborFirstStrategy extends ConcretizationStrategy {

	public static final String C_METHOD_GLOBAL = "global";
	public static final String C_METHOD_LOCAL_MEMBER = "local+members";
	public static final String C_METHOD_NEIGHBOR = "neighbors";

	protected Map<Node, Materials> stmtMatMap;
	protected Map<String, Materials> methodMatMap;
	protected Set<String> tried;
	protected TargetLocation currLoc;
	protected Change currChange;
	protected boolean assignSingleNode;
	protected boolean hasReqs;

	public NeighborFirstStrategy(Random r) {
		super(r);
		stmtMatMap = new HashMap<>();
		methodMatMap = new HashMap<>();
		tried = new HashSet<>();
		this.currLoc = null;
		this.currChange = null;
		this.assignSingleNode = false;
		this.hasReqs = false;
	}

	@Override
	public boolean instCheck(Change change, TargetLocation loc) {
		//Try all.
		return true;
	}

	@Override
	public ASTNode instantiate(Change c, TargetLocation loc, PatchInfo info) {
		if(currChange == null || c != currChange) {
			currChange = c;
			int reqCount = checkRequirements(c);
			assignSingleNode = reqCount == 1;
			hasReqs = reqCount > 0;
			tried.clear();
		}
		if(currLoc == null || loc != currLoc) {
			currLoc = loc;
			tried.clear();
		}
		info.cMethods.add(C_METHOD_NEIGHBOR);
		if(!hasReqs) {
			Node copied = TreeUtils.deepCopy(c.type.equals(Change.INSERT) ? c.node : c.location);
			return TreeUtils.generateNode(copied, loc.node.astNode.getAST());
		}
		List<Materials> neighbors = findMaterials(loc);
		Requirements reqs = c.requirements;
		importNames.clear();
		if(c.type.equals(Change.UPDATE)
				&& c.node.kind == loc.node.kind
				&& c.node.type == loc.node.type){
			return instantiateUpdate(c, loc, neighbors, info);
		} else if(c.type.equals(Change.INSERT)
				&& loc.compatibleTypes.size() > 0
				&& (c.node.type == ASTNode.SIMPLE_NAME
				|| c.node.type == ASTNode.QUALIFIED_NAME
				|| c.node.type == ASTNode.SIMPLE_TYPE
				|| c.node.type == ASTNode.QUALIFIED_TYPE)) {
			return instantiateInsertOne(c, loc, neighbors, info);
		} else {
			Map<String, String> varMapping = new HashMap<>();
			Map<String, String> methodMapping = new HashMap<>();
			Map<String, String> typeMapping = new HashMap<>();
			Map<String, String> strMapping = new HashMap<>();
			Map<VariableType, VariableType> typeMap = new HashMap<>();

			if(reqs.methods.size() > 0) {
				for(String absSignature : reqs.methods.keySet()){
					Set<Method> cMethods = reqs.methods.get(absSignature);
					Set<Method> candidates = new HashSet<>();
					Set<Method> localMethods = new HashSet<>();
					Set<Method> globalMethods = new HashSet<>();
					for(Materials m : neighbors) {
						if(m.methods.containsKey(absSignature))
							candidates.addAll(m.methods.get(absSignature));
					}
					if(materials.methods.containsKey(absSignature)) {
						localMethods.addAll(materials.methods.get(absSignature));
						localMethods.removeAll(candidates);
					}
					if(global.methods.containsKey(absSignature)) {
						globalMethods.addAll(global.methods.get(absSignature));
						globalMethods.removeAll(candidates);
						globalMethods.removeAll(localMethods);
					}
					for(Method method : cMethods){
						List<Method> compatibleMethods = new ArrayList<>();
						for(Method m : candidates){
							if(checkCompatible(method, m, reqs, typeMap))
								compatibleMethods.add(m);
						}
						if(compatibleMethods.size() == 0) {
							if(localMethods.size() > 0) {
								candidates.addAll(localMethods);
								localMethods.clear();
								for(Method m : candidates) {
									if(checkCompatible(method, m, reqs, typeMap))
										compatibleMethods.add(m);
								}
								if(compatibleMethods.size() > 0) {
									info.cMethods.add(C_METHOD_LOCAL_MEMBER);
								}
							} else if(globalMethods.size() > 0){
								candidates.addAll(globalMethods);
								globalMethods.clear();
								for(Method m : candidates) {
									if(checkCompatible(method, m, reqs, typeMap))
										compatibleMethods.add(m);
								}
								if(compatibleMethods.size() > 0)
									info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
						if(compatibleMethods.size() > 0){
							Method newMethod = compatibleMethods.get(r.nextInt(compatibleMethods.size()));
							if(!methodMapping.containsKey(method.name)){
								methodMapping.put(method.name, newMethod.name);
								candidates.remove(newMethod);
								updateTypeMap(method, newMethod, typeMap);
								if(assignSingleNode)
									tried.add(newMethod.name);
							}
						}else{
							if(DEBUG)
								System.out.println("Not enough methods to assign.");
							return null;
						}
					}
				}
			}

			if(reqs.fields.size() > 0) {
				for(VariableType type : reqs.fields.keySet()){
					Set<Variable> fields = reqs.fields.get(type);
					if(typeMap.containsKey(type))
						type = typeMap.get(type);
					Set<Variable> candidates = new HashSet<>();
					for(Materials m : neighbors)
						if(m.fields.containsKey(type))
							candidates.addAll(m.fields.get(type));
					Set<Variable> localFields = new HashSet<>();
					Set<Variable> globalFields = new HashSet<>();
					if(materials.fields.containsKey(type)) {
						localFields.addAll(materials.fields.get(type));
						localFields.removeAll(candidates);
					}
					if(global.fields.containsKey(type)) {
						globalFields.addAll(global.fields.get(type));
						globalFields.removeAll(candidates);
						globalFields.removeAll(localFields);
					}
					for(Variable field : fields){
						VariableType declType = typeMap.containsKey(field.declType) ? typeMap.get(field.declType) : field.declType;
						List<Variable> compatibleFields = new ArrayList<>();
						for(Variable f : candidates){
							if(declType.isSameType(f.declType)
									&& !(assignSingleNode && tried.contains(f.name)))
								compatibleFields.add(f);
						}
						if(compatibleFields.size() == 0) {
							if(localFields.size() > 0) {
								candidates.addAll(localFields);
								localFields.clear();
								for(Variable f : candidates){
									if(declType.isSameType(f.declType)
											&& !(assignSingleNode && tried.contains(f.name)))
										compatibleFields.add(f);
								}
								if(compatibleFields.size() > 0) {
									info.cMethods.add(C_METHOD_LOCAL_MEMBER);
								}
							} else if(globalFields.size() > 0) {
								candidates.addAll(globalFields);
								globalFields.clear();
								for(Variable f : candidates){
									if(declType.isSameType(f.declType)
											&& !(assignSingleNode && tried.contains(f.name)))
										compatibleFields.add(f);
								}
								if(compatibleFields.size() > 0)
									info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
						if(compatibleFields.size() > 0){
							Variable newField = compatibleFields.get(r.nextInt(compatibleFields.size()));
							if(!varMapping.containsKey(field.name)){
								varMapping.put(field.name, newField.name);
								candidates.remove(newField);
								if(assignSingleNode)
									tried.add(newField.name);
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

			if(!assignTypes(typeMap, reqs, neighbors, false, info)){
				if(DEBUG)
					System.out.println("Not enough types to assign.");
				return null;
			}

			if(!assignTypes(typeMap, reqs, neighbors, true, info)){
				if(DEBUG)
					System.out.println("Not enough generic types to assign.");
				return null;
			}

			//Handle Declared Variables.
			if(c.type.equals(Change.REPLACE)){
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
				Set<String> varNames = new HashSet<>();
				for(Materials m : neighbors)
					varNames.addAll(m.varNames);
				varNames.addAll(this.materials.varNames);
				for(String oldValue : reqs.declaredVariables){
					String newValue = oldValue;
					while(varNames.contains(newValue)){
						newValue = TreeUtils.VAR_PREFIX+count++;
					}
					if(!varMapping.containsKey(oldValue))
						varMapping.put(oldValue, newValue);
				}
			}

			if(reqs.variables.size() > 0) {
				for(VariableType type : reqs.variables.keySet()){
					Set<Variable> variables = reqs.variables.get(type);
					if(typeMap.containsKey(type))
						type = typeMap.get(type);
					List<Variable> candidates = new ArrayList<>();
					Set<Variable> vars = new HashSet<>();
					Set<Variable> localVars = new HashSet<>();
					Set<Variable> globalVars = new HashSet<>();
					for(Materials m : neighbors) {
						if(m.variables.containsKey(type)) {
							for(Variable v : m.variables.get(type))
								if(!(assignSingleNode && tried.contains(v.name)))
									vars.add(v);
						}
					}
					candidates.addAll(vars);
					localVars.addAll(materials.getLocalVariables(loc, type));
					if(materials.variables.containsKey(type))
						localVars.addAll(materials.variables.get(type));
					localVars.removeAll(vars);
					if(global.variables.containsKey(type)) {
						globalVars.addAll(global.variables.get(type));
						globalVars.removeAll(vars);
						globalVars.removeAll(localVars);
					}
					for (Variable var : variables) {
						if(candidates.size() == 0){
							if(localVars.size() > 0) {
								if(assignSingleNode) {
									for(Variable v : localVars)
										if(!tried.contains(v.name))
											candidates.add(v);
								} else {
									candidates.addAll(localVars);
								}
								localVars.clear();
								info.cMethods.add(C_METHOD_LOCAL_MEMBER);
							} else if(globalVars.size() > 0){
								if(assignSingleNode) {
									for(Variable v : globalVars)
										if(!tried.contains(v.name))
											candidates.add(v);
								} else {
									candidates.addAll(localVars);
								}
								globalVars.clear();
								info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
						if(candidates.size() > 0) {
							Variable newVar = candidates.get(r.nextInt(candidates.size()));
							if (!varMapping.containsKey(var.name)) {
								varMapping.put(var.name, newVar.name);
								candidates.remove(newVar);
								if(assignSingleNode)
									tried.add(newVar.name);
							}
						} else {
							if(DEBUG)
								System.out.println("Not enough variables to assign.");
							return null;
						}
					}
				}
			}

			if(reqs.strings.size() > 0) {
				List<String> list = new ArrayList<>();
				Map<String, Integer> freqMap = new HashMap<>();
				int maxFreq = combineStringLiterals(neighbors, list, freqMap, null);
				for(String absStr : reqs.strings) {
					int index = PatchUtils.rouletteWheelSelection(list, freqMap, maxFreq, r);
					if(index < 0) {
						strMapping.put(absStr, "\"\"");
					} else {
						strMapping.put(absStr, list.get(index));
						list.remove(index);
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

			Node copied = TreeUtils.deepCopy(c.type.equals(Change.REPLACE) ? c.location : c.node);
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
			try {
				astNode = TreeUtils.generateNode(copied, ast);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			return astNode;
		}
	}

	protected int checkRequirements(Change c) {
		int count = 0;
		List<Node> nodes = TreeUtils.traverse(c.type.equals(Change.INSERT) ? c.node : c.location);
		for(Node n : nodes)
			if(n.normalized && n.value != null)
				count++;
		return count;
	}

	protected ASTNode instantiateInsertOne(Change c, TargetLocation loc, List<Materials> neighbors, PatchInfo info) {
		Requirements reqs = c.requirements;
		List<ITypeBinding> typeCandidates = new ArrayList<>(loc.compatibleTypes);
		VariableType locType = new VariableType(typeCandidates.get(r.nextInt(typeCandidates.size())));
		String newValue = null;
		if(reqs.variables.size() > 0) {
			List<Variable> vars = new ArrayList<>();
			for(Materials m : neighbors) {
				appendVariables(m, null, locType, vars);
			}
			if(vars.size() == 0) {
				for(Variable v : materials.getLocalVariables(loc, locType))
					if(!tried.contains(v.name))
						vars.add(v);
				appendVariables(materials, null, locType, vars);
				if(vars.size() > 0) {
					info.cMethods.add(C_METHOD_LOCAL_MEMBER);
				} else {
					appendVariables(global, null, locType, vars);
					if(vars.size() > 0) {
						info.cMethods.add(C_METHOD_GLOBAL);
					}
				}
			}
			if(vars.size() > 0) {
				Variable v = vars.get(r.nextInt(vars.size()));
				newValue = v.name;
			}
		} else if(reqs.types.size() > 0) {
			newValue = locType.name;
		}
		if(newValue == null)
			return null;
		Node copied = TreeUtils.deepCopy(c.node);
		copied.value = newValue;
		tried.add(newValue);
		ASTNode astNode = TreeUtils.generateNode(copied, loc.node.astNode.getAST());
		return astNode;
	}

	protected ASTNode instantiateUpdate(Change c, TargetLocation loc, List<Materials> neighbors, PatchInfo info) {
		Requirements reqs = c.requirements;
		if(c.node.children.size() > 0) {
			return updateIntermediate(c, loc);
		} else {
			String newValue = null;
			String oldValue = loc.node.value;
			if(loc.node.astNode instanceof Name) {
				Name name = (Name)loc.node.astNode;
				IBinding b = name.resolveBinding();
				if(reqs.methods.size() > 0) {
					if(b != null && b.getKind() != IBinding.METHOD)
						return null;
					IMethodBinding mb = (IMethodBinding)b;
					String absSignature = new Method(mb).getAbstractSignature();
					List<Method> methods = new ArrayList<>();
					for(Materials m : neighbors) {
						appendMethods(m, oldValue, absSignature, methods);
					}
					if(methods.size() == 0) {
						appendMethods(materials, oldValue, absSignature, methods);
						if(methods.size() > 0) {
							info.cMethods.add(C_METHOD_LOCAL_MEMBER);
						} else {
							appendMethods(global, oldValue, absSignature, methods);
							if(methods.size() > 0) {
								info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
					}
					if(methods.size() > 0) {
						Method newMethod = methods.get(r.nextInt(methods.size()));
						newValue = newMethod.name;
					} else {
						if(DEBUG)
							System.out.println("Not enough variables to assign.");
						return null;
					}
				} else if(reqs.variables.size() > 0) {
					if(b != null && b.getKind() != IBinding.VARIABLE)
						return null;
					ITypeBinding tb = name.resolveTypeBinding();
					VariableType vt = new VariableType(tb);
					List<Variable> vars = new ArrayList<>();
					for(Materials m : neighbors) {
						appendVariables(m, oldValue, vt, vars);
					}
					if(vars.size() == 0) {
						for(Variable v : materials.getLocalVariables(loc, vt))
							if(!oldValue.equals(v.name) && !tried.contains(v.name))
								vars.add(v);
						appendVariables(materials, oldValue, vt, vars);
						if(vars.size() > 0) {
							info.cMethods.add(C_METHOD_LOCAL_MEMBER);
						} else {
							appendVariables(global, oldValue, vt, vars);
							if(vars.size() > 0) {
								info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
					}
					if(vars.size() > 0) {
						Variable v = vars.get(r.nextInt(vars.size()));
						newValue = v.name;
					} else {
						return null;
					}
				} else if(reqs.types.size() > 0) {
					List<VariableType> types = new ArrayList<>();
					for(Materials m : neighbors) {
						appendTypes(m, oldValue, types);
					}
					if(types.size() == 0) {
						appendTypes(materials, oldValue, types);
						if(types.size() > 0) {
							info.cMethods.add(C_METHOD_LOCAL_MEMBER);
						} else {
							appendTypes(global, oldValue, types);
							if(types.size() > 0) {
								info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
					}
					if(types.size() > 0) {
						VariableType t = types.get(r.nextInt(types.size()));
						newValue = t.name;
					} else {
						return null;
					}
				} else if(reqs.fields.size() > 0) {
					IVariableBinding vb = (IVariableBinding)name.resolveBinding();
					VariableType declType = MVTManager.generateType(vb.getDeclaringClass());
					ITypeBinding tb = name.resolveTypeBinding();
					VariableType vt = new VariableType(tb);
					List<Variable> vars = new ArrayList<>();
					for(Materials m : neighbors) {
						appendFields(m, oldValue, declType, vt, vars);
					}
					if(vars.size() == 0) {
						appendFields(materials, oldValue, declType, vt, vars);
						if(vars.size() > 0) {
							info.cMethods.add(C_METHOD_LOCAL_MEMBER);
						} else {
							appendFields(global, oldValue, declType, vt, vars);
							if(vars.size() > 0) {
								info.cMethods.add(C_METHOD_GLOBAL);
							}
						}
					}
					if(vars.size() > 0) {
						Variable v = vars.get(r.nextInt(vars.size()));
						newValue = v.name;
					} else {
						return null;
					}
				} else {
					newValue = c.location.value;
				}
			} else if(reqs.strings.size() > 0) {
				List<String> list = new ArrayList<>();
				Map<String, Integer> freqMap = new HashMap<>();
				int maxFreq = combineStringLiterals(neighbors, list, freqMap, oldValue);
				if(list.size() == 0) {
					List<Materials> mList = new ArrayList<>();
					mList.add(global);
					maxFreq = combineStringLiterals(mList, list, freqMap, oldValue);
					if(list.size() > 0)
						info.cMethods.add(C_METHOD_GLOBAL);
				}
				if(list.size() > 0) {
					int index = PatchUtils.rouletteWheelSelection(list, freqMap, maxFreq, r);
					newValue = list.get(index);
				} else {
					newValue = "\"\"";	//empty string.
				}
			} else {
				newValue = c.location.value;
			}
			Node copied = TreeUtils.deepCopy(loc.node);
			copied.value = newValue;
			tried.add(newValue);
			ASTNode astNode = TreeUtils.generateNode(copied, loc.node.astNode.getAST());
			return astNode;
		}
	}

	protected void appendFields(Materials m, String oldValue, VariableType declType, VariableType vt,
			List<Variable> vars) {
		if(m.fields.containsKey(vt)) {
			for(Variable v : m.fields.get(vt))
				if(declType.isSameType(v.declType)
						&& !oldValue.equals(v.name)
						&& !tried.contains(v.name))
					vars.add(v);
		}
	}

	protected void appendTypes(Materials m, String oldValue, List<VariableType> types) {
		for(VariableType type : m.types)
			if(!oldValue.equals(type.name)
					&& !tried.contains(type.name))
				types.add(type);
	}

	protected void appendVariables(Materials m, String oldValue, VariableType vt, List<Variable> vars) {
		if(m.variables.containsKey(vt)) {
			for(Variable v : m.variables.get(vt))
				if(!(oldValue != null && oldValue.equals(v.name))
						&& !tried.contains(v.name))
					vars.add(v);
		}
	}

	protected void appendMethods(Materials materials, String oldValue, String absSignature, List<Method> methods) {
		if(materials.methods.containsKey(absSignature)) {
			for(Method method : materials.methods.get(absSignature))
				if(!(oldValue != null && oldValue.equals(method.name))
						&& !tried.contains(method.name))
					methods.add(method);
		}
	}

	protected int combineStringLiterals(List<Materials> materials, List<String> list, Map<String, Integer> freqMap, String oldValue) {
		int maxFreq = 0;
		for(Materials m : materials) {
			for(String str : m.strings.keySet()) {
				if(oldValue != null && str.equals(oldValue)
						|| tried.contains(str))
					continue;
				if(freqMap.containsKey(str))
					freqMap.put(str, freqMap.get(str)+m.strings.get(str));
				else
					freqMap.put(str, m.strings.get(str));
			}
			if(maxFreq < m.maxStrFreq)
				maxFreq = m.maxStrFreq;
		}
		list.addAll(freqMap.keySet());
		return maxFreq;
	}

	public List<Materials> findMaterials(TargetLocation loc) {
		List<Materials> list = new ArrayList<>();
		List<Node> stmtNodes = getStatementNodes(loc);
		for(Node stmt : stmtNodes) {
			if(stmtMatMap.containsKey(stmt))
				list.add(stmtMatMap.get(stmt));
		}
		return list;
	}

	protected boolean assignTypes(Map<VariableType, VariableType> typeMap, Requirements reqs, List<Materials> neighbors, boolean isGeneric, PatchInfo info) {
		Set<VariableType> reqTypes = isGeneric ? reqs.genericTypes : reqs.types;
		Set<VariableType> mappedTypes = new HashSet<>();
		List<VariableType> candidates = new ArrayList<>();
		Set<VariableType> localTypes = new HashSet<>();
		Set<VariableType> globalTypes = new HashSet<>();
		Set<VariableType> types = null;
		for(Materials m : neighbors) {
			types = isGeneric ? m.genericTypes : m.types;
			for(VariableType t : types)
				if(!mappedTypes.contains(t)
						&& !(assignSingleNode && tried.contains(t.name)))
					mappedTypes.add(t);
		}
		candidates.addAll(mappedTypes);
		mappedTypes.addAll(typeMap.values());
		types = isGeneric ? materials.genericTypes : materials.types;
		for(VariableType t : types)
			if(!mappedTypes.contains(t)
					&& !(assignSingleNode && tried.contains(t.name)))
				localTypes.add(t);
		mappedTypes.addAll(localTypes);
		types = isGeneric ? global.genericTypes : global.types;
		for(VariableType t : types)
			if(!mappedTypes.contains(t)
					&& !(assignSingleNode && tried.contains(t.name)))
				globalTypes.add(t);
		for(VariableType type : reqTypes){
			if(!typeMap.containsKey(type)){
				if(candidates.size() == 0) {
					if(localTypes.size() > 0) {
						candidates.addAll(localTypes);
						localTypes.clear();
						if(candidates.size() > 0)
							info.cMethods.add(C_METHOD_LOCAL_MEMBER);
					} else if(globalTypes.size() > 0) {
						candidates.addAll(globalTypes);
						globalTypes.clear();
						if(candidates.size() > 0)
							info.cMethods.add(C_METHOD_GLOBAL);
					}
				}
				if(candidates.size() > 0) {
					VariableType newType = candidates.get(r.nextInt(candidates.size()));
					typeMap.put(type, newType);
					candidates.remove(newType);
					tried.add(newType.name);
				} else {
					return false;
				}
			}
		}
		return true;
	}

	protected boolean checkCompatible(Method cMethod, Method locMethod, Requirements reqs, Map<VariableType, VariableType> typeMap) {
		//Avoid already tried methods for single node assignment.
		if(assignSingleNode && tried.contains(locMethod.name)) {
			return false;
		}
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
			}
		}
		return true;
	}

	@Override
	public void collectMaterials(Node root) {
		//Collect materials for each statement.
		List<Node> nodes = TreeUtils.traverse(root);
		Materials materials = null;
		Set<Variable> params = new HashSet<>();
		Set<ITypeBinding> types = new HashSet<>();
		for(Node n : nodes) {
			if(n.type == ASTNode.METHOD_DECLARATION) {
				params.clear();
				types.clear();
				for(Node child : n.children) {
					if(child.type == ASTNode.SINGLE_VARIABLE_DECLARATION) {
						SingleVariableDeclaration svd = (SingleVariableDeclaration)child.astNode;
						SimpleName param = svd.getName();
						IVariableBinding vb = (IVariableBinding)param.resolveBinding();
						ITypeBinding tb = vb.getType();
						VariableType vt = new VariableType(tb);
						Variable v = new Variable(param.getIdentifier(), vt);
						params.add(v);
						types.addAll(getTypes(tb));
					}
				}
				MethodDeclaration md = (MethodDeclaration)n.astNode;
				IMethodBinding mb = md.resolveBinding();
				if(mb != null) {
					ITypeBinding tb = mb.getReturnType();
					if(tb != null) {
						types.addAll(getTypes(tb));
					}
				}
			}
			if(TreeUtils.isStatement(n) && !stmtMatMap.containsKey(n)) {
				materials = new Materials();
				stmtMatMap.put(n, materials);
			} else {
				Node stmt = TreeUtils.findStatement(n);
				if(!stmtMatMap.containsKey(stmt)) {
					materials = new Materials();
					stmtMatMap.put(n, materials);
				}
			}
			for(Variable v : params)
				materials.addVariable(v.type, v);
			for(ITypeBinding tb : types)
				materials.addType(tb);
		}
		for(Node stmt : stmtMatMap.keySet()) {
			materials = stmtMatMap.get(stmt);
			nodes = TreeUtils.traverse(stmt);
			for(Node n : nodes) {
				collectMVTS(n, materials);
			}
			collectParent(stmt, materials);
		}
	}

	protected void collectParent(Node stmt, Materials materials) {
		Node p = stmt.parent;
		if(p != null && p.type == ASTNode.BLOCK) {
			p = p.parent;
			if(p != null && p.type != ASTNode.METHOD_DECLARATION) {
				for(Node child : p.children) {
					if(child.type != ASTNode.BLOCK) {
						List<Node> nodes = TreeUtils.traverse(child);
						for(Node n : nodes)
							collectMVTS(n, materials);
					}
				}
			}
		}
	}

	protected List<ITypeBinding> getTypes(ITypeBinding tb) {
		List<ITypeBinding> types = new ArrayList<>();
		if (tb != null && !tb.isPrimitive()) {
			if (tb.isParameterizedType()) {
				tb = tb.getErasure();
				getTypes(tb);
			} else if (tb.isArray()) {
				tb = tb.getElementType();
				getTypes(tb);
			} else if (tb.isWildcardType()) {
				ITypeBinding bndType = tb.getBound();
				if (bndType != null) {
					getTypes(bndType);
				}
			} else {
				types.add(tb);
			}
		}
		return types;
	}

	protected void collectMVTS(Node n, Materials materials) {
		switch(n.kind){
		case IBinding.TYPE:
			if(n.astNode instanceof Type
					&& !(n.astNode instanceof PrimitiveType)){
				VariableType type = null;
				Type t = (Type)n.astNode;
				ITypeBinding tb = t.resolveBinding();
				if(tb != null && !tb.isPrimitive()){
					if(tb.isParameterizedType() && n.type != ASTNode.PARAMETERIZED_TYPE){
						if(tb.getErasure() != null)
							type = MVTManager.generateType(tb.getErasure());
					}else if(tb.isArray() && n.type != ASTNode.ARRAY_TYPE){
						if(tb.getElementType() != null)
							type = MVTManager.generateType(tb.getElementType());
					}else{
						type = MVTManager.generateType(tb);
					}
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
				collectVariables(n, materials);
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
		if(n.type == ASTNode.STRING_LITERAL) {
			materials.addString(n.value);
		}
	}

	protected void collectVariables(Node n, Materials materials) {
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
				if(name.getParent() instanceof FieldAccess
						&& name.getLocationInParent().equals(FieldAccess.NAME_PROPERTY)){
					v.isFieldAccess = true;
					IVariableBinding fb = ((FieldAccess)name.getParent()).resolveFieldBinding();
					VariableType declType = MVTManager.generateType(fb.getDeclaringClass());
					v.declType = declType;
					materials.addField(type, v);
				}else{
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
		}
	}

	@Override
	public void collectGlobalMaterials(Parser parser, String className, Node root, CompilationUnit cu) {
		//Collect member variables and declared methods.
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding b = node.resolveBinding();
				for(IVariableBinding vb : b.getDeclaredFields()) {
					VariableType type = new VariableType(vb.getType());
					String vName = Modifier.isStatic(vb.getModifiers()) ? b.getName() + "." + vb.getName() : vb.getName();
					Variable v = new Variable(vName, type);
					materials.addVariable(type, v);
				}
				for(IMethodBinding mb : b.getDeclaredMethods()) {
					if(!mb.isConstructor()) {
						materials.addMethod(mb);
					}
				}
				for(ITypeBinding tb : b.getDeclaredTypes()) {
					collectTypes(materials, tb);
				}
				return false;
			}
		});
		List<Node> nodes = TreeUtils.traverse(root);
		for(Node n : nodes) {
			if(n.type == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment)n.astNode;
				IVariableBinding vb = vdf.resolveBinding();
				//If it is a local variable declaration.
				if(Materials.isLocal(vb)) {
					String varKey = vb.getKey();
					ITypeBinding tb = vb.getType();
					VariableType type = MVTManager.generateType(tb);
					String varName = vdf.getName().getIdentifier();
					Variable v = new Variable(varName, type);
					materials.addLocalVariable(type, v, varKey, n.startLine);
				}
			}
			collectMVTS(n, global);
		}
	}

	public List<Node> getStatementNodes(TargetLocation loc) {
		List<Node> nodes = new ArrayList<>();
		Node stmt = TreeUtils.findStatement(loc.node);
		if (stmt != null) {
			nodes.add(stmt);
			switch(loc.kind) {
			case TargetLocation.DEFAULT:
				if(stmt.getLeft() != null)
					nodes.add(stmt.getLeft());
				if(stmt.getRight() != null)
					nodes.add(stmt.getRight());
				break;
			case TargetLocation.INSERT_BEFORE:
				if(stmt.getLeft() != null)
					nodes.add(stmt.getLeft());
				break;
			case TargetLocation.INSERT_AFTER:
				if(stmt.getRight() != null)
					nodes.add(stmt.getRight());
				break;
			case TargetLocation.INSERT_UNDER:
				break;
			}
		}
		return nodes;
	}
}
