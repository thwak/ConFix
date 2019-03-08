package com.github.thwak.confix.pool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

import script.model.EditOp;
import tree.TreeNode;

public class PNTContextIdentifier extends ContextIdentifier {

	private static final long serialVersionUID = -8352611691723991826L;
	private int level;

	public PNTContextIdentifier(int level) {
		this.level = level;
	}

	@Override
	public Context getContext(EditOp op) {
		if(op.getType().equals(Change.INSERT)){
			StringBuffer sb = new StringBuffer();
			sb.append("P:");
			TreeNode parent = op.getNode();
			TreeNode node = null;
			for(int i=0; i<level; i++) {
				sb.append(SYM_OPEN);
				node = parent;
				parent = node.getParent();
				if(parent != null && parent.getType() == ASTNode.BLOCK && parent.getParent() != null)
					parent = parent.getParent();
				if(parent == null)
					break;
				else{
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
				sb.append(SYM_CLOSE);
			}
			return new Context(sb.toString());
		}else{
			return getContext(op.getNode());
		}
	}

	@Override
	public Context getContext(TreeNode node){
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		TreeNode parent = node;
		for(int i=0; i<level; i++){
			sb.append(SYM_OPEN);
			node = parent;
			parent = node.getParent();
			if(parent != null && parent.getType() == ASTNode.BLOCK){
				parent = node.getParent().getParent();
			}
			if(parent == null)
				break;
			else{
				sb.append(TreeUtils.getTypeName(parent.getType()));
				StructuralPropertyDescriptor desc = null;
				ASTNode astNode = node.getASTNode();
				if(astNode.getParent() != null && astNode.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)
					desc = astNode.getParent().getLocationInParent();
				else
					desc = astNode.getLocationInParent();
				if(desc != null){
					sb.append(TreeUtils.SYM_OPEN);
					sb.append(desc.getId());
					sb.append(TreeUtils.SYM_CLOSE);
				}
			}
			sb.append(SYM_CLOSE);
		}
		return new Context(sb.toString());
	}

	@Override
	public Context getContext(Node node) {
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		addAncestors(node, sb);
		return new Context(sb.toString());
	}

	@Override
	public Context getLeftContext(Node node) {
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		addAncestors(node, sb);
		return new Context(sb.toString());
	}

	@Override
	public Context getRightContext(Node node){
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		addAncestors(node, sb);
		return new Context(sb.toString());
	}

	private void addAncestors(Node node, StringBuffer sb) {
		Node parent = node;
		for(int i=0; i<level; i++) {
			sb.append(SYM_OPEN);
			node = parent;
			parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
			if(parent == null){
				break;
			}else{
				sb.append(TreeUtils.getTypeName(parent.type));
				if(node.desc != null){
					sb.append(TreeUtils.SYM_OPEN);
					if(parent.type == ASTNode.INFIX_EXPRESSION) {
						if(node.desc.id.equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId()))
							sb.append(InfixExpression.LEFT_OPERAND_PROPERTY.getId());
						else if(node.desc.id.equals(InfixExpression.EXTENDED_OPERANDS_PROPERTY.getId()))
							sb.append(InfixExpression.RIGHT_OPERAND_PROPERTY.getId());
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
			}
			sb.append(SYM_CLOSE);
		}
	}

	@Override
	public List<Context> getUnderContext(Node node) {
		List<Context> list = new ArrayList<>();
		Set<String> spdIds = new HashSet<>();
		for(Node child : node.children) {
			if(child.desc != null)
				spdIds.add(child.desc.id);
		}
		List<StructuralPropertyDescriptor> spdList = null;
		StringBuffer sb = new StringBuffer();
		if(node.astNode != null) {
			spdList = node.astNode.structuralPropertiesForType();
		} else {
			AST ast = AST.newAST(AST.JLS8);
			ASTNode astNode = ast.createInstance(node.type);
			spdList = astNode.structuralPropertiesForType();
		}
		for(StructuralPropertyDescriptor spd : spdList) {
			if(!spdIds.contains(spd.getId())) {
				sb.append("P:");
				addAncestors(node, spd.getId(), sb, level);
				list.add(new Context(sb.toString()));
			}
		}
		return list;
	}
}
