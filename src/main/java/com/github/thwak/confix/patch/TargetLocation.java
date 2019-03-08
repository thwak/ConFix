package com.github.thwak.confix.patch;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.Context;
import com.github.thwak.confix.pool.Requirements;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

public class TargetLocation {
	public static final int DEFAULT = 0;
	public static final int INSERT_BEFORE = 1;
	public static final int INSERT_AFTER = 2;
	public static final int MOVE_LOC = 3;
	public static final int INSERT_UNDER = 4;
	public String className;
	public Context context;
	public Node node;
	public int kind;
	public Set<ITypeBinding> compatibleTypes;
	public StructuralPropertyDescriptor desc;
	public String methodKey;
	public int[] blockIndex;

	public TargetLocation(String className, int kind, Context context, Node node){
		this(className, kind, context, node, null);
	}

	public TargetLocation(String className, int type, Context context, ASTNode astNode){
		this(className, type, context, TreeUtils.getNode(astNode));
	}

	public TargetLocation(String className, int kind, Context context, Node node, StructuralPropertyDescriptor desc){
		this.className = className;
		this.kind = kind;
		this.context = context;
		this.node = node;
		this.desc = desc;
		compatibleTypes = new HashSet<>();
		methodKey = "";
		blockIndex = new int[0];
	}

	public String getTypeName(){
		switch(kind){
		case INSERT_BEFORE:
			return "Insert Before";
		case INSERT_AFTER:
			return "Insert After";
		case DEFAULT:
			return "Default";
		case MOVE_LOC:
			return "Move Location";
		case INSERT_UNDER:
			return "Insert Under";
		}
		return "Unknown";
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer(getTypeName());
		sb.append("\n");
		sb.append(node.label);
		sb.append("\n");
		sb.append(className);
		sb.append(":");
		sb.append(node.startLine);
		sb.append("\n");
		sb.append("Context:");
		sb.append(context.toString());
		return sb.toString();
	}

	public boolean isCompatible(Change c) {
		Requirements reqs = c.requirements;
		if(c.node.type == ASTNode.SIMPLE_NAME
				|| c.node.type == ASTNode.QUALIFIED_NAME
				|| c.node.type == ASTNode.SIMPLE_TYPE
				|| c.node.type == ASTNode.QUALIFIED_TYPE)
			return true;
		else if(compatibleTypes.size() == 0)
			return true;
		for(ITypeBinding tb : compatibleTypes) {
			if(reqs.isCompatible(tb))
				return true;
		}
		return false;
	}

	public ITypeBinding getType() {
		if(compatibleTypes.size() > 0)
			return compatibleTypes.iterator().next();
		return null;
	}
}
