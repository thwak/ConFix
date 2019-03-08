package com.github.thwak.confix.tree;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;

import com.github.thwak.confix.pool.MVTManager;
import com.github.thwak.confix.pool.Method;
import com.github.thwak.confix.pool.Variable;
import com.github.thwak.confix.pool.VariableType;

import tree.TreeBuilder;
import tree.TreeNode;

public class TreeUtils {
	public static final String TYPE_PREFIX = "Type";
	public static final String VAR_PREFIX = "var";
	public static final String METHOD_PREFIX = "method";
	public static final String LITERAL_PREFIX = "literal";
	public static final String SYM_OPEN = "{";
	public static final String SYM_CLOSE = "}";
	public static final String SYM_NORM = "@@";

	/**
	 * @param n a root node of a subtree to traverse.
	 * @return a list of nodes in depth-first order.
	 */
	public static List<Node> traverse(Node n){
		List<Node> nodes = new ArrayList<>();
		Stack<Node> stack = new Stack<>();
		stack.push(n);
		while(!stack.isEmpty()){
			n = stack.pop();
			nodes.add(n);
			for(int i=n.children.size()-1; i>=0; i--)
				stack.push(n.children.get(i));
		}
		return nodes;
	}

	public static List<TreeNode> traverse(TreeNode n){
		List<TreeNode> nodes = new ArrayList<>();
		Stack<TreeNode> stack = new Stack<>();
		stack.push(n);
		while(!stack.isEmpty()){
			n = stack.pop();
			nodes.add(n);
			for(int i=n.children.size()-1; i>=0; i--)
				stack.push(n.children.get(i));
		}
		return nodes;
	}

	public static List<Node> levelOrder(Node n) {
		List<Node> nodes = new ArrayList<>();
		nodes.add(n);
		int index = 0;
		while(index < nodes.size()) {
			nodes.addAll(nodes.get(index).children);
			index++;
		}
		return nodes;
	}

	public static String computeSHA256Hash(String hashString) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
			md.update(hashString.getBytes());
			byte bytes[] = md.digest();
			StringBuffer sb = new StringBuffer();
			for(byte b : bytes){
				sb.append(Integer.toString((b&0xff) + 0x100, 16).substring(1));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String computeHashString(Node node){
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(node.label);
		for(Node child : node.children){
			sb.append(computeHashString(child));
		}
		sb.append(SYM_CLOSE);
		node.hashString = sb.toString();
		return node.hashString;
	}

	public static String getShortHash(Node node) {
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(node.type);
		sb.append(Node.DELIM);
		if(node.value != null)
			sb.append(node.value);
		for(Node child : node.children){
			sb.append(getShortHash(child));
		}
		sb.append(SYM_CLOSE);
		return sb.toString();
	}

	public static String computeShortHashString(Node node){
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(node.type);
		sb.append(Node.DELIM);
		if(node.value != null)
			sb.append(node.value);
		for(Node child : node.children){
			sb.append(computeShortHashString(child));
		}
		sb.append(SYM_CLOSE);
		node.hashString = sb.toString();
		return node.hashString;
	}

	public static String getHashString(Node node){
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(node.label);
		for(Node child : node.children){
			sb.append(getHashString(child));
		}
		sb.append(SYM_CLOSE);
		return sb.toString();
	}

	public static String computeTypeNameHash(Node node){
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(getTypeName(node.type));
		for(Node child : node.children){
			sb.append(computeTypeNameHash(child));
		}
		sb.append(SYM_CLOSE);
		node.hashString = sb.toString();
		return node.hashString;
	}

	public static String getTypeNameHash(Node node) {
		return getTypeNameHash(node, new StringBuffer());
	}

	public static String getTypeNameHash(Node node, StringBuffer sb){
		sb.append(SYM_OPEN);
		sb.append(getTypeName(node.type));
		for(Node child : node.children){
			getTypeNameHash(child, sb);
		}
		sb.append(SYM_CLOSE);
		return sb.toString();
	}

	public static String computeTypeHash(Node node){
		StringBuffer sb = new StringBuffer(100);
		sb.append(SYM_OPEN);
		sb.append(node.type);
		for(Node child : node.children){
			sb.append(computeTypeHash(child));
		}
		sb.append(SYM_CLOSE);
		node.hashString = sb.toString();
		return node.hashString;
	}

	public static String getTypeHash(Node node) {
		return getTypeHash(node, new StringBuffer());
	}

	public static String getTypeHash(Node node, StringBuffer sb){
		sb.append(SYM_OPEN);
		sb.append(node.type);
		for(Node child : node.children){
			getTypeHash(child, sb);
		}
		sb.append(SYM_CLOSE);
		return sb.toString();
	}

	public static String getValue(ASTNode node) {
		String value = null;
		if(node instanceof Assignment)
			value = ((Assignment)node).getOperator().toString();
		if(node instanceof BooleanLiteral
				|| node instanceof Modifier
				|| node instanceof SimpleType
				|| node instanceof QualifiedType
				|| node instanceof PrimitiveType)
			value = node.toString();
		if(node instanceof CharacterLiteral)
			value = ((CharacterLiteral)node).getEscapedValue();
		if(node instanceof NumberLiteral)
			value = ((NumberLiteral)node).getToken();
		if(node instanceof StringLiteral)
			value = ((StringLiteral)node).getEscapedValue();
		if(node instanceof InfixExpression)
			value = ((InfixExpression)node).getOperator().toString();
		if(node instanceof PrefixExpression)
			value = ((PrefixExpression)node).getOperator().toString();
		if(node instanceof PostfixExpression)
			value = ((PostfixExpression)node).getOperator().toString();
		if(node instanceof SimpleName)
			value = ((SimpleName)node).getIdentifier();
		if(node instanceof QualifiedName)
			value = ((QualifiedName)node).getFullyQualifiedName();
		return value;
	}

	public static String getLabel(ASTNode node){
		return getLabel(getTypeName(node), getValue(node));
	}

	public static String getLabel(String typeName, String value){
		return typeName + Node.DELIM + value;
	}

	public static String getTypeName(ASTNode node){
		return node.getClass().getSimpleName();
	}

	public static String getTypeName(int type){
		return type == -1 ? "root" : ASTNode.nodeClassForType(type).getSimpleName();
	}

	public static Node getNode(ASTNode node){
		String typeName = getTypeName(node);
		String value = getValue(node);
		return new Node(getLabel(typeName, value), node, value);
	}

	public static String computeLabel(Node n, String value){
		if(n.astNode != null){
			return n.astNode.getClass().getSimpleName() + Node.DELIM + value;
		}else{
			try{
				Class nodeClass = ASTNode.nodeClassForType(n.type);
				return nodeClass.getSimpleName() + Node.DELIM + value;
			}catch(IllegalArgumentException e){
				int delimIdx = n.label.indexOf(Node.DELIM);
				return delimIdx > -1 ? n.label.substring(0, delimIdx) + Node.DELIM + value : n.label;
			}
		}
	}

	public static String computeLabel(Node n){
		return computeLabel(n, n.value);
	}

	public static MVTManager normalize(Node node, boolean normalizeLiterals){
		MVTManager manager = new MVTManager();
		normalize(manager, node, normalizeLiterals);
		return manager;
	}

	public static void normalize(MVTManager manager, Node node, boolean normalizeLiterals) {
		Map<String, String> literals = new HashMap<>();
		List<Node> nodes = traverse(node);
		for(Node n : nodes){
			if(n.astNode instanceof Name){
				Name name = (Name)n.astNode;
				IBinding b = name.resolveBinding();
				int kind = b == null ? n.kind : b.getKind();
				switch (kind) {
				case IBinding.TYPE:
					VariableType type = manager.getType(n.astNode, (ITypeBinding)b);
					normalizeNode(n, type.name);
					break;
				case IBinding.VARIABLE:
					Variable var = manager.getVariable(n.astNode, (IVariableBinding)b);
					normalizeNode(n, var.name);
					break;
				case IBinding.METHOD:
					Method method = manager.getMethod(n.astNode, (IMethodBinding)b);
					normalizeNode(n, method.name);
					break;
				}
			}else if(n.astNode instanceof SimpleType
					|| n.astNode instanceof QualifiedType){
				ITypeBinding tb = ((Type)n.astNode).resolveBinding();
				VariableType type = manager.getType(n.astNode, tb);
				normalizeNode(n, type.name);
			}else if(n.astNode instanceof StringLiteral) {
				String str = manager.getString(n.value);
				normalizeNode(n, str);
			}else if(normalizeLiterals && (n.astNode instanceof NumberLiteral
					|| n.astNode instanceof CharacterLiteral
					|| n.astNode instanceof StringLiteral)){
				if(!literals.containsKey(n.astNode.toString()))
					literals.put(n.astNode.toString(), LITERAL_PREFIX+literals.size());
				normalizeNode(n, literals.get(n.astNode.toString()));
			}
		}
	}

	public static void normalizeNode(Node n, String value){
		if(value != null && value.equals(n.value))
			n.normalized = false;
		else
			n.normalized = true;
		n.value = value;
		n.label = computeLabel(n);
	}

	public static String convertToTypeHash(String hash) {
		String typeHash = hash.replaceAll("::\\w+\\}", "}");
		typeHash = typeHash.replaceAll("::\\w+\\{", "{");
		return typeHash;
	}

	public static ASTNode generateNode(Node n, AST ast){
		ASTNode astNode = ast.createInstance(n.type);
		if(astNode instanceof SimpleName
				|| astNode instanceof QualifiedName){
			astNode = ast.newName(n.value);
		}else if(astNode instanceof SimpleType
				|| astNode instanceof QualifiedType){
			astNode = ast.newSimpleType(ast.newName(n.value));
		}else if(astNode instanceof ArrayType){
			//Remove the dimension created as default.
			((ArrayType)astNode).dimensions().remove(0);
		}else{
			updateValue(astNode, n);
		}
		for(Node child : n.children){
			ASTNode childNode = generateNode(child, ast);
			StructuralPropertyDescriptor descriptor = child.desc.getDescriptor();
			if(descriptor.isChildListProperty()){
				List<ASTNode> list = (List<ASTNode>)astNode.getStructuralProperty(descriptor);
				list.add(childNode);
			}else{
				try {
					astNode.setStructuralProperty(descriptor, childNode);
				} catch (Exception e) {
					return null;
				}
			}
		}
		if(n.isStatement && astNode instanceof Expression)
			astNode = ast.newExpressionStatement((Expression)astNode);
		return astNode;
	}

	private static void updateValue(ASTNode node, Node n) {
		if(node instanceof Assignment)
			((Assignment)node).setOperator(Assignment.Operator.toOperator(n.value));
		if(node instanceof BooleanLiteral)
			((BooleanLiteral)node).setBooleanValue(Boolean.parseBoolean(n.value));
		if(node instanceof Modifier)
			((Modifier)node).setKeyword(ModifierKeyword.toKeyword(n.value));
		if(node instanceof PrimitiveType)
			((PrimitiveType)node).setPrimitiveTypeCode(PrimitiveType.toCode(n.value));
		if(node instanceof CharacterLiteral)
			((CharacterLiteral)node).setEscapedValue(n.value);
		if(node instanceof NumberLiteral)
			((NumberLiteral)node).setToken(n.value);
		if(node instanceof StringLiteral)
			((StringLiteral)node).setEscapedValue(n.value);
		if(node instanceof InfixExpression)
			((InfixExpression)node).setOperator(InfixExpression.Operator.toOperator(n.value));
		if(node instanceof PrefixExpression)
			((PrefixExpression)node).setOperator(PrefixExpression.Operator.toOperator(n.value));
		if(node instanceof PostfixExpression)
			((PostfixExpression)node).setOperator(PostfixExpression.Operator.toOperator(n.value));
	}

	public static Node deepCopy(Node node) {
		Node copied = node.copy();
		for(Node child : node.children){
			copied.addChild(deepCopy(child));
		}
		return copied;
	}

	public static boolean isJSL(ITypeBinding tb){
		if(tb != null){
			if(tb.isArray()){
				tb = tb.getElementType();
				return isJSL(tb);
			}else if(tb.isParameterizedType()){
				tb = tb.getErasure();
				return isJSL(tb);
			}else{
				return tb.isPrimitive() || tb.getQualifiedName().startsWith("java");
			}
		}else{
			return false;
		}
	}

	public static String computeSourcePath(String path, String code) {
		CompilationUnit cu = TreeBuilder.getCompilationUnit(code);
		String packageName = getPackageName(cu);
		packageName = packageName.replaceAll("\\.", "\\/");
		int idx = path.indexOf(packageName);
		if(idx >= 0) {
			return path.substring(0, idx);
		} else {
			idx = path.indexOf("/org/");
			if(idx < 0)
				return "";
			else
				return path.substring(0, idx);
		}
	}

	public static String getPackageName(CompilationUnit cu) {
		if(cu == null)
			return "";
		PackageVisitor v = new PackageVisitor();
		cu.accept(v);
		return v.getPackageName();
	}

	public static String getPackageName(Node root) {
		Node n = findNode(root, ASTNode.COMPILATION_UNIT);
		if(n != null) {
			return getPackageName((CompilationUnit)n.astNode);
		}
		return "";
	}

	public static Node findNode(Node n, int type) {
		if(n.type == type) {
			return n;
		} else {
			for(Node child : n.children) {
				Node r = findNode(child, type);
				if(r != null)
					return r;
			}
		}
		return null;
	}

	public static Node findStatement(Node node) {
		if(node == null)
			return null;
		if(isStatement(node))
			return node;
		return findStatement(node.parent);
	}

	public static boolean isStatement(Node node) {
		if(node == null) {
			return false;
		} else if(node.astNode instanceof Statement)
			return true;
		else if(node.astNode instanceof Expression) {
			ASTNode p = node.astNode.getParent();
			if(p != null && p.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
				return true;
			}
		}
		return false;
	}

	private static class PackageVisitor extends ASTVisitor {
		String packageName = "";
		public String getPackageName() {
			return packageName;
		}
		@Override
		public boolean visit(PackageDeclaration node) {
			packageName = node.getName().getFullyQualifiedName();
			return false;
		}
	}
}
