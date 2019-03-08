package com.github.thwak.confix.pool;

import script.model.EditOp;
import tree.TreeNode;

public class Replace extends EditOp {
	private static final long serialVersionUID = 3506613550090374117L;

	public Replace(TreeNode node, TreeNode location, int position) {
		super(node, location, position);
	}

	@Override
	public String getType(){
		return "replace";
	}

	@Override
	public String toOpString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM
				+ node.getParent().getLabel() + EditOp.SYM_OPEN + node.getParent().getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + node.indexInParent() + " with "
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + position;
	}
}
