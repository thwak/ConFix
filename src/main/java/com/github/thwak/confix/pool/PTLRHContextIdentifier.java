package com.github.thwak.confix.pool;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

import script.model.EditOp;
import tree.TreeNode;

public class PTLRHContextIdentifier extends ContextIdentifier {

	private static final long serialVersionUID = -5894565579742344496L;

	@Override
	public Context getContext(EditOp op) {
		if(op.getType().equals(Change.INSERT)){
			StringBuffer sb = new StringBuffer();
			sb.append("P:");
			TreeNode parent = null;
			parent = op.getNode().getParent();
			if(parent.getType() == ASTNode.BLOCK && parent.getParent() != null)
				parent = parent.getParent();
			if(parent != null){
				if(parent.getMatched() != null){
					parent = parent.getMatched();
				}
				sb.append(TreeUtils.getTypeName(parent.getType()));
				StructuralPropertyDescriptor desc = null;
				ASTNode astNode = op.getNode().getASTNode();
				if(astNode.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)
					desc = astNode.getParent().getLocationInParent();
				else
					desc = astNode.getLocationInParent();
				if(desc != null){
					sb.append(TreeUtils.SYM_OPEN);
					sb.append(desc.getId());
					sb.append(TreeUtils.SYM_CLOSE);
				}
			}
			TreeNode node = op.getNode();
			TreeNode left = node.getLeft();
			TreeNode right = node.getRight();
			while(right != null && !right.isMatched())
				right = right.getRight();
			//Get hash of old nodes as contexts.
			if(left != null && left.isMatched())
				left = left.getMatched();
			if(right != null && right.isMatched())
				right = right.getMatched();
			sb.append(",L:");
			sb.append(left == null ? "" : getHash(left));
			sb.append(",R:");
			sb.append(right == null ? "" : getHash(right));
			return new Context(sb.toString());
		}else{
			return getContext(op.getNode());
		}
	}

	@Override
	public Context getContext(TreeNode node){
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		TreeNode parent = null;
		if(node.getParent() != null && node.getParent().getType() == ASTNode.BLOCK){
			parent = node.getParent().getParent();
		}else{
			parent = node.getParent();
		}
		if(parent != null){
			sb.append(TreeUtils.getTypeName(parent.getType()));
			StructuralPropertyDescriptor desc = null;
			ASTNode astNode = node.getASTNode();
			if(astNode.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)
				desc = astNode.getParent().getLocationInParent();
			else
				desc = astNode.getLocationInParent();
			if(desc != null){
				sb.append(TreeUtils.SYM_OPEN);
				sb.append(desc.getId());
				sb.append(TreeUtils.SYM_CLOSE);
			}
		}
		TreeNode left = node.getLeft();
		TreeNode right = node.getRight();
		sb.append(",L:");
		sb.append(left == null ? "" : getHash(left));
		sb.append(",R:");
		sb.append(right == null ? "" : getHash(right));
		return new Context(sb.toString());
	}

	@Override
	public Context getContext(Node node) {
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		Node parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
		sb.append(getParentType(node, parent));
		if(parent != null && node.desc != null){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append(node.desc.id);
			sb.append(TreeUtils.SYM_CLOSE);
		}
		Node left = node.getLeft();
		sb.append(",L:");
		sb.append(left == null ? "" : getHash(left));
		sb.append(",R:");
		//Find the first unmatched right node.
		Node right = node.getRight();
		while(right != null && !right.isMatched)
			right = right.getRight();
		sb.append(right == null ? "" : getHash(right));
		return new Context(sb.toString());
	}

	@Override
	public Context getLeftContext(Node node) {
		Node left = node.getLeft();
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		Node parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
		sb.append(getParentType(node, parent));
		if(parent != null && node.desc != null){
			sb.append(TreeUtils.SYM_OPEN);
			if(parent.type == ASTNode.INFIX_EXPRESSION) {
				if(node.desc.id.equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId()) ||
						node.desc.id.equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId()))
					sb.append(InfixExpression.LEFT_OPERAND_PROPERTY.getId());
				else
					sb.append(node.desc.id);
			} else if(parent.type == ASTNode.METHOD_INVOCATION
					&& node.desc.id.equals(MethodInvocation.NAME_PROPERTY.getId())) {
				sb.append(MethodInvocation.EXPRESSION_PROPERTY.getId());
			} else {
				sb.append(node.desc.id);
			}
			sb.append(TreeUtils.SYM_CLOSE);
		}
		sb.append(",L:");
		sb.append(left == null ? "" : getHash(left));
		sb.append(",R:");
		sb.append(getHash(node));
		return new Context(sb.toString());
	}

	@Override
	public Context getRightContext(Node node){
		Node right = node.getRight();
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		Node parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
		sb.append(getParentType(node, parent));
		if(parent != null && node.desc != null){
			sb.append(TreeUtils.SYM_OPEN);
			if(parent.type == ASTNode.INFIX_EXPRESSION) {
				if(node.desc.id.equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId()))
					sb.append(InfixExpression.RIGHT_OPERAND_PROPERTY.getId());
				else if(node.desc.id.equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId()))
					sb.append(InfixExpression.EXTENDED_OPERANDS_PROPERTY.getId());
				else
					sb.append(node.desc.id);
			} else if(parent.type == ASTNode.IF_STATEMENT
					&& node.desc.id.equals(IfStatement.THEN_STATEMENT_PROPERTY.getId())) {
				sb.append(IfStatement.ELSE_STATEMENT_PROPERTY.getId());
			} else {
				sb.append(node.desc.id);
			}
			sb.append(TreeUtils.SYM_CLOSE);
		}
		sb.append(",L:");
		sb.append(getHash(node));
		sb.append(",R:");
		sb.append(right == null ? "" : getHash(right));
		return new Context(sb.toString());
	}

	private String getHash(TreeNode node) {
		StringBuffer sb = new StringBuffer();
		sb.append(TreeUtils.SYM_OPEN);
		sb.append(node.getType());
		switch(node.getType()){
		case ASTNode.MODIFIER:
		case ASTNode.INFIX_EXPRESSION:
		case ASTNode.PREFIX_EXPRESSION:
		case ASTNode.POSTFIX_EXPRESSION:
		case ASTNode.ASSIGNMENT:
			sb.append(TreeUtils.getValue(node.getASTNode()));
			break;
		}
		for(TreeNode child : node.children){
			sb.append(getHash(child));
		}
		sb.append(TreeUtils.SYM_CLOSE);

		return sb.toString();
	}

	private String getHash(Node node) {
		StringBuffer sb = new StringBuffer();
		sb.append(TreeUtils.SYM_OPEN);
		sb.append(node.type);
		switch(node.type){
		case ASTNode.MODIFIER:
		case ASTNode.INFIX_EXPRESSION:
		case ASTNode.PREFIX_EXPRESSION:
		case ASTNode.POSTFIX_EXPRESSION:
		case ASTNode.ASSIGNMENT:
			sb.append(node.value);
			break;
		}
		for(Node child : node.children){
			sb.append(getHash(child));
		}
		sb.append(TreeUtils.SYM_CLOSE);

		return sb.toString();
	}
}
