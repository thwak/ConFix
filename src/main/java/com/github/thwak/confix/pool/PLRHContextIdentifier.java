package com.github.thwak.confix.pool;

import org.eclipse.jdt.core.dom.ASTNode;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

import script.model.EditOp;
import tree.TreeNode;

public class PLRHContextIdentifier extends ContextIdentifier {

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
				parent = parent.getMatched();
				int pos = op.getPosition();
				TreeNode dummy = new TreeNode();
				if(pos >= 0 && pos < parent.children.size()){
					parent.children.add(pos, dummy);
				}else{
					pos = parent.children.size();
					parent.children.add(dummy);
				}
				sb.append(getHash(parent, dummy));
				parent.children.remove(pos);
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
			sb.append(getHash(parent));
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
	public Context getContext(Node node){
		return getContext(node, false);
	}

	private Context getContext(Node node, boolean exclude) {
		StringBuffer sb = new StringBuffer();
		sb.append("P:");
		if(exclude){
			Node parent = node.parent;
			if (parent != null) {
				if(parent.type == ASTNode.BLOCK)
					sb.append(parent.parent == null ? "" : getHash(parent.parent, node));
				else
					sb.append(getHash(parent, node));
			}
		}else{
			Node parent = node.parent != null && node.parent.type == ASTNode.BLOCK ? node.parent.parent : node.parent;
			sb.append(parent == null ? "" : getHash(parent));
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
		if(node.parent != null){
			Node dummy = new Node("dummy", -1);
			dummy.posInParent = node.posInParent;
			dummy.parent = node.parent;
			node.parent.children.add(node.posInParent, dummy);
			Context c = getContext(dummy, true);
			node.parent.children.remove(node.posInParent);
			return c;
		}else{
			Node left = node.getLeft();
			StringBuffer sb = new StringBuffer();
			sb.append("P:");
			sb.append(",L:");
			sb.append(left == null ? "" : getHash(left));
			sb.append(",R:");
			sb.append(getHash(node));
			return new Context(sb.toString());
		}
	}

	@Override
	public Context getRightContext(Node node){
		if(node.parent != null){
			Node dummy = new Node("dummy", -1);
			dummy.posInParent = node.posInParent+1;
			dummy.parent = node.parent;
			if(dummy.posInParent == node.parent.children.size())
				node.parent.children.add(dummy);
			else
				node.parent.children.add(dummy.posInParent, dummy);
			Context c = getContext(dummy, true);
			node.parent.children.remove(dummy.posInParent);
			return c;
		}else{
			Node right = node.getRight();
			StringBuffer sb = new StringBuffer();
			sb.append("P:");
			sb.append(",L:");
			sb.append(getHash(node));
			sb.append(",R:");
			sb.append(right == null ? "" : getHash(right));
			return new Context(sb.toString());
		}
	}

	private String getHash(Node node) {
		StringBuffer sb = new StringBuffer();
		sb.append(TreeUtils.SYM_OPEN);
		sb.append(node.type);
		for(Node child : node.children){
			sb.append(getHash(child));
		}
		sb.append(TreeUtils.SYM_CLOSE);

		return sb.toString();
	}

	private String getHash(Node node, Node exclude) {
		StringBuffer sb = new StringBuffer();
		if(node.equals(exclude)){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append("N");
			sb.append(TreeUtils.SYM_CLOSE);
		}else if(node.isMatched){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append(node.type);
			for(Node child : node.children){
				sb.append(getHash(child, exclude));
			}
			sb.append(TreeUtils.SYM_CLOSE);
		}

		return sb.toString();
	}

	private String getHash(TreeNode node) {
		StringBuffer sb = new StringBuffer();
		sb.append(TreeUtils.SYM_OPEN);
		sb.append(node.getType());
		for(TreeNode child : node.children){
			sb.append(getHash(child));
		}
		sb.append(TreeUtils.SYM_CLOSE);

		return sb.toString();
	}

	private String getHash(TreeNode node, TreeNode exclude) {
		StringBuffer sb = new StringBuffer();
		if(node.equals(exclude)){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append("N");
			sb.append(TreeUtils.SYM_CLOSE);
		}else if(node.isMatched()){
			sb.append(TreeUtils.SYM_OPEN);
			sb.append(node.getType());
			for(TreeNode child : node.children){
				sb.append(getHash(child, exclude));
			}
			sb.append(TreeUtils.SYM_CLOSE);
		}

		return sb.toString();
	}
}
