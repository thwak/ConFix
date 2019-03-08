package com.github.thwak.confix.tree.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;

public class SimpleFileASTRequestor extends FileASTRequestor {

	public List<CompilationUnit> compilationUnits = new ArrayList<CompilationUnit>();
	public Map<String, IBinding> bindings = new HashMap<String, IBinding>();

	@Override
	public void acceptAST(String sourceFilePath, CompilationUnit ast){
		compilationUnits.add(ast);
	}

	@Override
	public void acceptBinding(String bindingKey, IBinding binding){
		bindings.put(bindingKey, binding);
	}

}
