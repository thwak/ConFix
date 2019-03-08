package com.github.thwak.confix.tree;

import java.io.File;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.thwak.confix.util.IOUtils;

public class Parser {

	private String[] classPath = { };
	private String[] sourcePath = { };

	public Parser(String[] classPath, String[] sourcePath){
		this.classPath = classPath;
		this.sourcePath = sourcePath;
	}

	public CompilationUnit parse(File f) {
		String source = IOUtils.readFile(f);
		return parse(source);
	}

	public CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setEnvironment(classPath, sourcePath, null, true);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setUnitName("");
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);

		return cu;
	}
}
