package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SwitchStatement;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

import script.model.EditOp;
import tree.TreeNode;

public class ContextIdentifier implements Serializable {

	private static final long serialVersionUID = -2764338562007998666L;
	public static final String SYM_OPEN = "(";
	public static final String SYM_CLOSE = ")";

	public Context getContext(EditOp op) {
		return new Context();
	}

	public Context getContext(TreeNode node){
		return new Context();
	}

	public Context getContext(Node node) {
		return new Context();
	}

	public Context getLeftContext(Node node){
		return new Context();
	}

	public Context getRightContext(Node node){
		return new Context();
	}

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
			if(!spd.isSimpleProperty() && !spdIds.contains(spd.getId())) {
				sb.append("P:");
				addAncestors(node, spd.getId(), sb, 1);
				sb.append(",L:,R:");
				list.add(new Context(sb.toString()));
			}
		}
		return list;
	}

	public Context getUnderContext(Node node, StructuralPropertyDescriptor desc) {
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		addAncestors(node, desc.getId(), sb, 1);
		sb.append(",L:,R:");
		return new Context(sb.toString());
	}

	protected void addAncestors(Node node, String desc, StringBuffer sb, int level) {
		sb.append(SYM_OPEN);
		sb.append(TreeUtils.getTypeName(node.type));
		sb.append(TreeUtils.SYM_OPEN);
		sb.append(desc);
		sb.append(TreeUtils.SYM_CLOSE);
		sb.append(SYM_CLOSE);
		Node parent = node;
		for(int i=1; i<level; i++) {
			sb.append(SYM_OPEN);
			node = parent;
			parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
			if(parent == null){
				break;
			}else{
				sb.append(TreeUtils.getTypeName(parent.type));
				if(node.desc != null){
					sb.append(TreeUtils.SYM_OPEN);
					sb.append(node.desc.id);
					sb.append(TreeUtils.SYM_CLOSE);
				}
			}
			sb.append(SYM_CLOSE);
		}
	}

	protected void addNodeType(Node node, StringBuffer sb) {
		sb.append(TreeUtils.getTypeName(node.type));
		if(node.desc != null){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append(node.desc.id);
			sb.append(TreeUtils.SYM_CLOSE);
		}
	}

	protected String getParentType(Node node, Node parent) {
		if(parent == null) {
			return "";
		} else {
			if(parent.type == ASTNode.SWITCH_STATEMENT
					&& node.type != ASTNode.SWITCH_CASE
					&& node.desc.id.equals(SwitchStatement.STATEMENTS_PROPERTY.getId())) {
				return TreeUtils.getTypeName(ASTNode.SWITCH_CASE);
			} else {
				return TreeUtils.getTypeName(parent.type);
			}
		}
	}
}
