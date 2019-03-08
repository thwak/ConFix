package com.github.thwak.confix.tree.compiler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;

public class CompilerRequestorImpl implements ICompilerRequestor {

	private List<IProblem> problems;
	private List<ClassFile> classes;

	public CompilerRequestorImpl(){
		this.problems = new ArrayList<IProblem>();
		this.classes = new ArrayList<ClassFile>();
	}

	@Override
	public void acceptResult(CompilationResult result) {
		boolean errors = false;
		if (result.hasProblems()) {
			IProblem[] problems = result.getProblems();
			for (int i = 0; i < problems.length; i++) {
				if (problems[i].isError())
					errors = true;

				this.problems.add(problems[i]);
			}
		}
		if (!errors) {
			ClassFile[] classFiles = result.getClassFiles();
			for (int i = 0; i < classFiles.length; i++)
				this.classes.add(classFiles[i]);
		}
	}

	public List<IProblem> getProblems(){
		return problems;
	}

	public List<ClassFile> getClasses(){
		return classes;
	}
}
