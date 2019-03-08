package com.github.thwak.confix.pool;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.github.thwak.confix.tree.Node;

import script.model.EditOp;

public class PatchPool implements Serializable {
	private static final long serialVersionUID = 7837250691435633019L;
	public Map<String, ContextIdentifier> identifiers;
	public Map<String, Map<String, Patch>> patches;

	public PatchPool(String[] poolNames, ContextIdentifier[] identifiers) {
		this.identifiers = new HashMap<>();
		patches = new HashMap<>();
		for(int i=0; i<poolNames.length; i++) {
			this.identifiers.put(poolNames[i], identifiers[i]);
			patches.put(poolNames[i], new HashMap<String, Patch>());
		}
	}

	public void add(String bugId, Script script) {
		for(String poolName : identifiers.keySet()) {
			Map<String, Patch> map = patches.get(poolName);
			if(!map.containsKey(bugId))
				map.put(bugId, new Patch(bugId));
			Patch patch = map.get(bugId);
			for(Change c : script.changes.keySet()){
				List<EditOp> ops = script.changes.get(c);
				for(EditOp op : ops) {
					Context context = getIdentifier(poolName).getContext(op);
					String fileName = polish(c.id);
					updateMethod(c);
					ContextChange cc = new ContextChange(context, c);
					patch.add(fileName, cc);
				}
			}
		}
	}

	private String polish(String fileName) {
		fileName = fileName.replaceAll("\\/\\/", "/");
		int idx = fileName.indexOf(':');
		return idx >= 0 ? fileName.substring(0, idx) : fileName;
	}

	public ContextIdentifier getIdentifier(String poolName) {
		return identifiers.get(poolName);
	}

	public Set<String> poolNames() {
		return identifiers.keySet();
	}

	private void updateMethod(Change c) {
		Node n = c.node;
		while(n.parent != null && n.parent.type != ASTNode.METHOD_DECLARATION){
			n = n.parent;
		}
		StringBuffer sb = new StringBuffer(c.id);
		if(n.parent == null)
			sb.append(":");
		else{
			sb.append(":");
			if(n.parent.astNode != null){
				MethodDeclaration md = (MethodDeclaration)n.parent.astNode;
				sb.append(md.getName().toString());
			}
			sb.append(":");
			sb.append(n.parent.startPos);
		}
		c.id = sb.toString();
	}

	public class ContextChange implements Serializable {
		private static final long serialVersionUID = 5062014197509108377L;
		public Context context;
		public Change change;

		public ContextChange(Context context, Change change) {
			this.context = context;
			this.change = change;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj instanceof ContextChange) {
				ContextChange cc = (ContextChange)obj;
				return cc.context.equals(context) && cc.change.equals(change);
			}
			return false;
		}
	}
}
