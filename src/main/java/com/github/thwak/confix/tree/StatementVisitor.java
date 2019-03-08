package com.github.thwak.confix.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Statement;

public class StatementVisitor extends ASTVisitor {
	private Stack<Node> stack;
	private List<Node> statements;

	public StatementVisitor(Node root){
		stack = new Stack<>();
		stack.push(root);
		statements = new ArrayList<Node>();
	}

	public List<Node> statements(){
		return Collections.unmodifiableList(statements);
	}

	@Override
	public boolean preVisit2(ASTNode node) {
		String value = TreeUtils.getValue(node);
		String label = node.getClass().getSimpleName() + Node.DELIM + value;
		Node n = new Node(label, node, value);
		if(!stack.isEmpty()){
			stack.peek().addChild(n);
		}

		if(node instanceof Statement
				&& !(node instanceof Block)){
			statements.add(n);
		}else if(node instanceof Name){
			return false;
		}
		stack.push(n);

		return super.preVisit2(node);
	}

	@Override
	public void postVisit(ASTNode node) {
		if(!(node instanceof Name))
			stack.pop();
		super.postVisit(node);
	}
}
