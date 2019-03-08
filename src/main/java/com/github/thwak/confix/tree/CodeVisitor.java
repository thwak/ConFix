package com.github.thwak.confix.tree;

import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;

public class CodeVisitor extends ASTVisitor {
	public Node root;
	private Stack<Node> stack;

	public CodeVisitor(Node root){
		this.root = root;
		stack = new Stack<>();
		stack.push(root);
	}

	@Override
	public void preVisit(ASTNode node) {
		Node n = TreeUtils.getNode(node);
		if(!(node instanceof ExpressionStatement)){
			if(!stack.isEmpty())
				stack.peek().addChild(n);
			stack.push(n);
		}
	}

	@Override
	public void postVisit(ASTNode node) {
		if(!(node instanceof ExpressionStatement))
			stack.pop();
	}

	@Override
	public boolean visit(QualifiedName node){
		return false;
	}

	@Override
	public boolean visit(SimpleType node){
		return false;
	}

	@Override
	public boolean visit(QualifiedType node){
		return false;
	}

	@Override
	public boolean visit(PrimitiveType node){
		return false;
	}
}
