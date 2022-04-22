package com.github.thwak.confix.pool;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.util.IOUtils;

import script.ScriptGenerator;
import script.model.EditOp;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

public class ChangePoolGenerator {
	public ChangePool pool;

	public ChangePoolGenerator(ContextIdentifier identifier){
		pool = new ChangePool(identifier);
	}

	public void collect(Script script){
		for(Change c : script.changes.keySet()){
			ContextIdentifier identifier = pool.getIdentifier();
			List<EditOp> ops = script.changes.get(c);
			for(EditOp op : ops) {
				Context context = identifier.getContext(op);
				updateMethod(c);
				pool.add(context, c);
			}
		}
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

	public void collect(String id, File bFile, File aFile, String[] classPathEntries, String[] sourcePathEntries) {
		collect(id, bFile, aFile, classPathEntries, sourcePathEntries, true);
	}

	public void collect(String id, File bFile, File aFile, String[] classPathEntries, String[] sourcePathEntries, boolean discardDelMov) {
		try {
			//Generate EditScript from before and after.
			String oldCode = IOUtils.readFile(bFile);
			String newCode = IOUtils.readFile(aFile);
			Tree before = TreeBuilder.buildTreeFromFile(bFile, classPathEntries, sourcePathEntries);
			Tree after = TreeBuilder.buildTreeFromFile(aFile, classPathEntries, sourcePathEntries);
			EditScript editScript = ScriptGenerator.generateScript(before, after);
			//Convert EditScript to Script.
			editScript = Converter.filter(editScript);
			EditScript combined = Converter.combineEditOps(editScript);
			if(discardDelMov)
				combined = Converter.filterRemainingDelMov(combined);
			Script script = Converter.convert(id, combined, oldCode, newCode);
			collect(script);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
