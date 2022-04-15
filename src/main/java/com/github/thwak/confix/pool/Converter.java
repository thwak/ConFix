package com.github.thwak.confix.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

import script.model.Delete;
import script.model.EditOp;
import script.model.EditScript;
import script.model.Insert;
import script.model.Move;
import script.model.Update;
import tree.TreeNode;

public class Converter {

	public static EditScript polish(EditScript editScript) {
		EditScript polished = Converter.combineEditOps(editScript);
		polished = Converter.filter(polished);
		return polished;
	}

	public static EditScript filter(EditScript editScript) {
		EditScript filtered = new EditScript();
		for (EditOp op : editScript.getEditOps()) {
			// Don't collect changes related to import declarations and new features.
			if (op.getNode().getType() == ASTNode.IMPORT_DECLARATION
					|| op.getNode().getType() == ASTNode.METHOD_DECLARATION
					|| op.getNode().getType() == ASTNode.ENUM_DECLARATION
					|| op.getNode().getType() == ASTNode.ENUM_CONSTANT_DECLARATION
					|| op.getNode().getType() == ASTNode.COMPILATION_UNIT
					|| op.getNode().getType() == ASTNode.JAVADOC
					|| op.getNode().getType() == ASTNode.PACKAGE_DECLARATION) {
				continue;
			} else if (op.getType().equals(Change.UPDATE)) {
				ASTNode astNode = op.getNode().getASTNode();
				if (astNode instanceof Name && astNode.getParent() != null) {
					StructuralPropertyDescriptor loc = astNode.getLocationInParent();
					if (loc.equals(SingleVariableDeclaration.NAME_PROPERTY)
							|| loc.equals(VariableDeclarationFragment.NAME_PROPERTY)
							|| loc.equals(MethodDeclaration.NAME_PROPERTY)
							|| loc.equals(TypeDeclaration.NAME_PROPERTY)
							|| loc.equals(EnumDeclaration.NAME_PROPERTY)
							|| loc.equals(EnumConstantDeclaration.NAME_PROPERTY))
						continue;
				}
			} // else if (op.getType().equals(Change.MOVE) ||
				// op.getType().equals(Change.DELETE)) {
				// Discard delete / move operations.
				// continue;
				// }
				// removed part where delete / move operations are discarded due to loss of
				// replace operation.
			filtered.addEditOp(op);
		}
		return filtered;
	}

	public static EditScript combineEditOps(EditScript script) {
		EditScript newScript = new EditScript();
		// Categorize each type of edit operations.
		List<EditOp> editOps = script.getEditOps();
		Map<String, List<EditOp>> opMap = new HashMap<>();
		for (EditOp op : editOps) {
			if (!opMap.containsKey(op.getType()))
				opMap.put(op.getType(), new ArrayList<EditOp>());
			opMap.get(op.getType()).add(op);
		}
		Map<String, List<EditOp>> combined = new HashMap<>();
		for (String type : opMap.keySet())
			combined.put(type, new ArrayList<EditOp>());

		// First check ordering changes in moved nodes.
		if (opMap.containsKey(Change.MOVE)) {
			for (EditOp ord : opMap.get(Change.MOVE)) {
				TreeNode oldParent = ord.getNode().getParent();
				TreeNode newParent = ord.getLocation();
				if (newParent.getMatched() == oldParent) {
					List<TreeNode> ancestors = new ArrayList<>();
					while (oldParent != null) {
						ancestors.add(oldParent);
						oldParent = oldParent.getParent();
					}
					for (EditOp mov : opMap.get(Change.MOVE)) {
						if (ancestors.contains(mov.getNode()))
							combined.get(Change.MOVE).add(ord);
					}
				}
			}
			opMap.get(Change.MOVE).removeAll(combined.get(Change.MOVE));
			combined.get(Change.MOVE).clear();
		}

		// Identify moved nodes in insert/delete.
		Map<EditOp, EditOp> movInIns = new HashMap<>();
		Map<EditOp, EditOp> movInDel = new HashMap<>();
		if (opMap.containsKey(Change.MOVE) && (opMap.containsKey(Change.DELETE) || opMap.containsKey(Change.INSERT))) {
			List<EditOp> insAndDel = new ArrayList<>();
			if (opMap.containsKey(Change.INSERT))
				insAndDel.addAll(opMap.get(Change.INSERT));
			if (opMap.containsKey(Change.DELETE))
				insAndDel.addAll(opMap.get(Change.DELETE));
			for (EditOp op : insAndDel) {
				TreeNode n = op.getNode();
				Stack<TreeNode> stack = new Stack<>();
				stack.push(n);
				while (!stack.isEmpty()) {
					n = stack.pop();
					for (TreeNode child : n.children) {
						if (child.isMatched()) {
							for (EditOp mov : opMap.get(Change.MOVE)) {
								if (op.getType().equals(Change.INSERT) && mov.getNode() == child.getMatched())
									movInIns.put(mov, op);
								else if (op.getType().equals(Change.DELETE) && mov.getNode() == child)
									movInDel.put(mov, op);
							}
						} else {
							stack.push(child);
						}
					}
				}
			}
			// Ignore moves inside both inserts and deletes.
			Set<EditOp> ignore = new HashSet<>(movInIns.keySet());
			ignore.retainAll(movInDel.keySet());
			movInDel.keySet().removeAll(ignore);
			movInIns.keySet().removeAll(ignore);
			opMap.get(Change.MOVE).removeAll(ignore);

			// Generate replaces for moves occurred in the same location as inserts/deletes.
			for (Entry<EditOp, EditOp> entry : movInDel.entrySet()) {
				if (Collections.frequency(movInDel.values(), entry.getValue()) > 1)
					continue;
				TreeNode movedTo = entry.getKey().getNode().getMatched();
				TreeNode deleted = entry.getValue().getNode();
				if (movedTo.getParent().getMatched() == deleted.getParent()) {
					StructuralPropertyDescriptor mLoc = identifyLocation(movedTo);
					StructuralPropertyDescriptor dLoc = identifyLocation(deleted);
					if (mLoc != null && mLoc.equals(dLoc)) {
						Replace r = new Replace(deleted, movedTo, entry.getValue().getPosition());
						newScript.addEditOp(r);
						combined.get(entry.getKey().getType()).add(entry.getKey());
						combined.get(entry.getValue().getType()).add(entry.getValue());
					}
				}
			}
			movInDel.keySet().removeAll(combined.get(Change.MOVE));
			for (Entry<EditOp, EditOp> entry : movInIns.entrySet()) {
				if (Collections.frequency(movInIns.values(), entry.getValue()) > 1)
					continue;
				TreeNode movedFrom = entry.getKey().getNode();
				TreeNode inserted = entry.getValue().getNode();
				if (movedFrom.getParent().getMatched() == inserted.getParent()) {
					StructuralPropertyDescriptor mLoc = identifyLocation(movedFrom);
					StructuralPropertyDescriptor iLoc = identifyLocation(inserted);
					if (mLoc != null && mLoc.equals(iLoc)) {
						Replace r = new Replace(movedFrom, inserted, entry.getKey().getPosition());
						newScript.addEditOp(r);
						combined.get(entry.getKey().getType()).add(entry.getKey());
						combined.get(entry.getValue().getType()).add(entry.getValue());
					}
				}
			}
			movInIns.keySet().removeAll(combined.get(Change.MOVE));

			// For remaining moves, generate new inserts/deletes instead of moves.
			for (EditOp mov : movInDel.keySet()) {
				Insert ins = new Insert(mov.getNode().getMatched());
				if (!opMap.containsKey(Change.INSERT))
					opMap.put(Change.INSERT, new ArrayList<EditOp>());
				if (!combined.containsKey(Change.INSERT))
					combined.put(Change.INSERT, new ArrayList<EditOp>());
				opMap.get(Change.INSERT).add(ins);
				opMap.get(Change.MOVE).remove(mov);
			}
			for (EditOp mov : movInIns.keySet()) {
				Delete del = new Delete(mov.getNode());
				if (!opMap.containsKey(Change.DELETE))
					opMap.put(Change.DELETE, new ArrayList<EditOp>());
				if (!combined.containsKey(Change.DELETE))
					combined.put(Change.DELETE, new ArrayList<EditOp>());
				opMap.get(Change.DELETE).add(del);
				opMap.get(Change.MOVE).remove(mov);
			}
		}

		// Remove all combined edit operations.
		for (String type : combined.keySet()) {
			opMap.get(type).removeAll(combined.get(type));
			combined.get(type).clear();
		}

		// Check updates in inserted nodes.
		if (opMap.containsKey(Change.UPDATE) && opMap.containsKey(Change.INSERT)) {
			for (EditOp upd : opMap.get(Change.UPDATE)) {
				List<TreeNode> nodes = new ArrayList<>();
				TreeNode newNode = upd.getLocation();
				nodes.add(newNode);
				TreeNode parent = newNode.getParent();
				while (parent != null) {
					nodes.add(parent);
					parent = parent.getParent();
				}
				for (EditOp ins : opMap.get(Change.INSERT)) {
					if (nodes.contains(ins.getNode()))
						combined.get(Change.UPDATE).add(upd);
				}
			}
			opMap.get(Change.UPDATE).removeAll(combined.get(Change.UPDATE));
			combined.get(Change.UPDATE).clear();
		}

		// Identify insert-delete pairs.
		if (opMap.containsKey(Change.DELETE) && opMap.containsKey(Change.INSERT)) {
			for (EditOp del : opMap.get(Change.DELETE)) {
				TreeNode dParent = del.getNode().getParent();
				StructuralPropertyDescriptor dLoc = del.getNode().getASTNode().getLocationInParent();
				if (dParent != null && dLoc != null) {
					for (EditOp ins : opMap.get(Change.INSERT)) {
						TreeNode iParent = ins.getNode().getParent();
						StructuralPropertyDescriptor iLoc = ins.getNode().getASTNode().getLocationInParent();
						if (dParent.getMatched() == iParent && iLoc != null && dLoc.getId().equals(iLoc.getId())) {
							// For statements property, check node type.
							if (dLoc.getId().equals("statements")
									&& del.getNode().getType() != ins.getNode().getType()) {
								continue;
							}
							if (dLoc.isChildListProperty() && !dLoc.getId().equals("statements")
									&& del.getPosition() != ins.getPosition()) {
								continue;
							}
							Replace r = new Replace(del.getNode(), ins.getNode(), del.getPosition());
							newScript.addEditOp(r);
							combined.get(Change.DELETE).add(del);
							combined.get(Change.INSERT).add(ins);
							break;
						}
					}
				}
			}
			for (String type : combined.keySet()) {
				opMap.get(type).removeAll(combined.get(type));
			}
		}
		for (String type : opMap.keySet()) {
			newScript.addEditOps(opMap.get(type));
		}
		return newScript;
	}

	private static StructuralPropertyDescriptor identifyLocation(TreeNode node) {
		StructuralPropertyDescriptor loc;
		if (node.getASTNode().getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)
			loc = node.getASTNode().getParent().getLocationInParent();
		else
			loc = node.getASTNode().getLocationInParent();
		return loc;
	}

	public static Script convert(String id, EditScript editScript, String oldCode, String newCode) {
		return convert(id, editScript, oldCode, newCode, true);
	}

	public static Script convert(String id, EditScript editScript, String oldCode, String newCode, boolean normalize) {
		Script script = new Script();
		TreeNode oldTreeNodeRoot = null;
		TreeNode newTreeNodeRoot = null;
		Node oldRoot = null;
		Node newRoot = null;
		for (EditOp op : editScript.getEditOps()) {
			if (!(op instanceof Insert)) {
				oldTreeNodeRoot = getRoot(op.getNode());
				oldRoot = convert(oldTreeNodeRoot);
			}
			if (!(op instanceof Delete)) {
				newTreeNodeRoot = getRoot(op.getLocation());
				newRoot = convert(newTreeNodeRoot);
			}
			if (oldRoot != null && newRoot != null)
				break;
		}
		for (EditOp op : editScript.getEditOps()) {
			Node node = null;
			Node location = null;
			int nodeId = op.getNode().getId();
			int locationId = op instanceof Move ? op.getNode().getMatched().getId() : op.getLocation().getId();
			if (op instanceof Delete) {
				List<Node> traverse = TreeUtils.traverse(oldRoot);
				for (Node n : traverse) {
					if (n.id == nodeId) {
						node = n;
					} else if (n.id == locationId) {
						location = n;
					}
					if (node != null && location != null)
						break;
				}
			} else if (op instanceof Insert) {
				List<Node> traverse = TreeUtils.traverse(newRoot);
				for (Node n : traverse) {
					if (n.id == nodeId) {
						node = n;
					} else if (n.id == locationId) {
						location = n;
					}
					if (node != null && location != null)
						break;
				}
			} else {
				List<Node> traverse = TreeUtils.traverse(oldRoot);
				for (Node n : traverse) {
					if (n.id == nodeId) {
						node = n;
						break;
					}
				}
				traverse = TreeUtils.traverse(newRoot);
				for (Node n : traverse) {
					if (n.id == locationId) {
						location = n;
						break;
					}
				}
			}
			if (location == null && locationId == -1) {
				location = new Node("root");
			}
			MVTManager manager = new MVTManager();
			node = getCopiedNode(node);
			if (location.type == ASTNode.BLOCK
					&& location.parent != null
					&& (op instanceof Insert || op instanceof Delete)) {
				location = location.parent;
			}

			// For move operations, parents should be assigned.
			if (op instanceof Move) {
				location = getCopiedNode(location);
			} else {
				location = TreeUtils.deepCopy(location);
			}

			if (normalize) {
				if (op instanceof Update || op instanceof Replace) {
					TreeUtils.normalize(node, false);
					TreeUtils.normalize(manager, location, false);
				} else {
					TreeUtils.normalize(manager, node, false);
					TreeUtils.normalize(location, false);
				}
			}

			Change change = new Change(id, op.getType(), node, location);
			if (op instanceof Insert) {
				updateEdit(manager, change, node, newCode);
			} else if (op instanceof Update
					|| op instanceof Replace) {
				updateEdit(manager, change, location, newCode);
				change.locationCode = getNormalizedCode(node, oldCode);
			} else {
				updateEdit(manager, change, node, oldCode);
			}
			VariableType nodeType = identifyNodeType(manager, node);
			change.requirements.setNodeType(node.isStatement, nodeType);
			script.add(change, op);
		}

		return script;
	}

	private static Node getCopiedNode(Node node) {
		Node parent = node.parent;
		Node copied = TreeUtils.deepCopy(node);
		if (node.parent != null) {
			copied.parent = node.parent.copy();
		}
		if (parent.type == ASTNode.BLOCK
				&& parent.parent != null) {
			copied.parent.parent = parent.parent.copy();
		}
		return copied;
	}

	private static void updateEdit(MVTManager manager, Change change, Node node, String source) {
		int startPos = node.startPos;
		int endPos = node.startPos + node.length;
		String code = source.substring(startPos, endPos);
		List<Node> nodes = TreeUtils.traverse(node);
		StringBuffer sb = new StringBuffer();
		int lastAppendedIndex = 0;
		for (Node n : nodes) {
			if (n.startPos > endPos) {
				startPos = n.startPos;
				endPos = n.startPos + n.length;
				code = source.substring(startPos, endPos);
				lastAppendedIndex = 0;
				node = n;
				sb.append("\n");
			}
			if (n.normalized) {
				int startIndex = n.startPos - startPos;
				sb.append(code.substring(lastAppendedIndex, startIndex));
				sb.append(n.value);
				lastAppendedIndex = startIndex + n.length;
			}
		}
		if (lastAppendedIndex < code.length())
			sb.append(code.substring(lastAppendedIndex));
		change.code = sb.toString();
		change.requirements.updateRequirements(manager);
	}

	public static String getNormalizedCode(Node node, String source) {
		int startPos = node.startPos;
		int endPos = node.startPos + node.length;
		String code = source.substring(startPos, endPos);
		List<Node> nodes = TreeUtils.traverse(node);
		StringBuffer sb = new StringBuffer();
		int lastAppendedIndex = 0;
		for (Node n : nodes) {
			if (n.startPos > endPos) {
				startPos = n.startPos;
				endPos = n.startPos + n.length;
				code = source.substring(startPos, endPos);
				lastAppendedIndex = 0;
				node = n;
				sb.append("\n");
			}
			if (n.normalized) {
				int startIndex = n.startPos - startPos;
				sb.append(code.substring(lastAppendedIndex, startIndex));
				sb.append(n.value);
				lastAppendedIndex = startIndex + n.length;
			}
		}
		if (lastAppendedIndex < code.length())
			sb.append(code.substring(lastAppendedIndex));
		return sb.toString();
	}

	private static VariableType identifyNodeType(MVTManager manager, Node node) {
		if (node.astNode instanceof Expression) {
			Expression expr = (Expression) node.astNode;
			ITypeBinding tb = expr.resolveTypeBinding();
			if (TreeUtils.isJSL(tb)) {
				return new VariableType(tb);
			} else {
				return new VariableType("");
			}
		} else {
			return null;
		}
	}

	private static TreeNode getRoot(TreeNode node) {
		TreeNode n = node;
		TreeNode p = node.getParent();
		while (p != null && !p.getLabel().equals("root")) {
			n = n.getParent();
			p = p.getParent();
		}
		return n;
	}

	public static Node convert(TreeNode node) {
		ASTNode astNode = node.getASTNode();
		Node n = TreeUtils.getNode(astNode);
		n.id = node.getId();
		n.startPos = node.getStartPosition();
		n.length = node.getLength();
		n.isMatched = node.isMatched();
		for (TreeNode child : node.children) {
			n.addChild(convert(child));
		}

		return n;
	}

}
