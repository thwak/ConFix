package com.github.thwak.confix.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;

public class Node implements Serializable {

	private static final long serialVersionUID = 8020769106117625499L;
	public static final String DELIM = "::";
	public static final int K_CONSTRUCTOR = 0;
	public static final int K_VARIABLE = IBinding.VARIABLE;
	public static final int K_METHOD = IBinding.METHOD;
	public static final int K_TYPE = IBinding.TYPE;

	public int id;
	public String label;
	public transient ASTNode astNode;
	public int type;
	public List<Node> children;
	public Node parent;
	public int startLine;
	public int endLine;
	public int startPos;
	public int length;
	public int posInParent;
	public String value;
	public transient String hashString;
	public boolean isStatement = false;
	public int kind = -1;
	public boolean normalized = false;
	public StructuralPropertyDesc desc;
	public boolean isMatched = true;

	public Node(String label) {
		this(label, -1, "");
	}

	public Node(String label, int type){
		this(label, type, "");
	}

	public Node(String label, int type, String value){
		this.id = -1;
		this.label = label;
		this.type = type;
		this.value = value;
		this.astNode = null;
		this.children = new ArrayList<Node>();
		this.parent = null;
		this.startLine = 0;
		this.endLine = 0;
		this.startPos = 0;
		this.length = 0;
		this.posInParent = 0;
		this.hashString = null;
		this.isStatement = false;
		this.desc = null;
		this.isMatched = false;
	}

	public Node(String label, ASTNode node, String value) {
		this.label = label;
		this.astNode = node;
		this.type = node.getNodeType();
		this.children = new ArrayList<Node>();
		this.parent = null;
		this.value = value;
		this.startPos = node.getStartPosition();
		this.length = node.getLength();
		this.posInParent = 0;
		this.hashString = null;
		if(node.getRoot() instanceof CompilationUnit){
			CompilationUnit cu = (CompilationUnit)node.getRoot();
			this.startLine = cu.getLineNumber(startPos);
			this.endLine = cu.getLineNumber(startPos+length);
		}else{
			this.startLine = 0;
			this.endLine = 0;
		}
		if(node instanceof Statement
				|| (node.getParent() != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)){
			this.isStatement = true;
		}
		if(node instanceof Name){
			IBinding b = ((Name)node).resolveBinding();
			if(b == null){
				if(node.getParent() instanceof MethodDeclaration){
					MethodDeclaration md = (MethodDeclaration)node.getParent();
					if(node.getLocationInParent().equals(MethodDeclaration.NAME_PROPERTY)){
						this.kind = md.isConstructor() ? K_CONSTRUCTOR : K_METHOD;
					}else{
						this.kind = IBinding.VARIABLE;
					}
				}else if(node.getParent() instanceof MethodInvocation){
					MethodInvocation mi = (MethodInvocation)node.getParent();
					this.kind = mi.getName() == node ? K_METHOD : IBinding.VARIABLE;
				}else if(node.getParent() instanceof MemberValuePair){
					this.kind = IBinding.MEMBER_VALUE_PAIR;
				}else if(node.getParent() instanceof Annotation){
					this.kind = IBinding.ANNOTATION;
				}else if(node.getParent() instanceof ImportDeclaration){
					this.kind = IBinding.TYPE;
				}else if(node.getParent() instanceof PackageDeclaration){
					this.kind = IBinding.PACKAGE;
				}else{
					this.kind = IBinding.VARIABLE;
				}
			}else{
				this.kind = b.getKind();
			}
		}else if(node instanceof Type){
			this.kind = IBinding.TYPE;
		}
		if(node.getParent() != null && node.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT){
			ASTNode parent = node.getParent();
			StructuralPropertyDescriptor spd = parent.getLocationInParent();
			desc = new StructuralPropertyDesc(spd, ASTNode.nodeClassForType(parent.getParent().getNodeType()));
		}else{
			StructuralPropertyDescriptor spd = node.getLocationInParent();
			desc = spd == null ? null : new StructuralPropertyDesc(spd, ASTNode.nodeClassForType(node.getParent().getNodeType()));
		}

	}

	public boolean isRoot(){
		return parent == null;
	}

	public void addChild(Node child){
		if(child != null){
			this.children.add(child);
			child.parent = this;
			child.posInParent = this.children.size()-1;
		}
	}

	public String computeLabel(){
		this.label = this.astNode != null ? this.astNode.getClass().getSimpleName() + Node.DELIM + value
				: ASTNode.nodeClassForType(type).getSimpleName() + Node.DELIM + value;
		return this.label;
	}

	public Node getLeft(){
		return parent != null && posInParent > 0 ? parent.children.get(posInParent-1) : null;
	}

	public Node getRight(){
		return parent != null && posInParent < parent.children.size()-1 ? parent.children.get(posInParent+1) : null;
	}

	public Node copy() {
		Node n = new Node(label, type, value);
		n.id = id;
		n.astNode = astNode;
		n.children = new ArrayList<Node>();
		n.parent = null;
		n.startLine = startLine;
		n.endLine = endLine;
		n.startPos = startPos;
		n.length = length;
		n.posInParent = posInParent;
		n.hashString = hashString;
		n.kind = kind;
		n.isStatement = isStatement;
		n.desc = desc != null ? new StructuralPropertyDesc(desc.id, desc.type, desc.className) : null;
		n.isMatched = isMatched;
		n.normalized = normalized;
		return n;
	}
}
