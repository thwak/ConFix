package com.github.thwak.confix.tree.compiler;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class CompilationUnitImpl implements ICompilationUnit {

	private CompilationUnit cu;
	
	public CompilationUnitImpl(CompilationUnit cu){
		this.cu = cu;
	}
	
	public char[] getFileName() {
		AbstractTypeDeclaration classType = (AbstractTypeDeclaration) cu.types().get(0);
		String name = classType.getName().getFullyQualifiedName() + ".java";
		
		return name.toCharArray();
	}

	public char[] getContents() {
		// TODO Auto-generated method stub
		char[] contents = null;
		try {
			Document doc = new Document();
			TextEdit edits = cu.rewrite(doc, null);
			edits.apply(doc);
			String source = doc.get();
			if(source != null){
				contents = source.toCharArray();
			}
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return contents;
	}

	public char[] getMainTypeName() {
		AbstractTypeDeclaration classType = (AbstractTypeDeclaration) cu.types().get(0);
		
		return classType.getName().getFullyQualifiedName().toCharArray();
	}

	public char[][] getPackageName() {
		// TODO Auto-generated method stub
		if(this.cu.getPackage() != null){
			String[] names = getSimpleNames(this.cu.getPackage().getName().getFullyQualifiedName());
			char[][] packages = new char[names.length][];
			for(int i=0; i<names.length; ++i){
				packages[i] = names[i].toCharArray();
			}
			return packages;
		}else{
			return null;
		}
		
	}

	public String getFileName(String path){
		TypeDeclaration classType = (TypeDeclaration) cu.types().get(0);
		String name = classType.getName().getFullyQualifiedName();
		String packageName = CharOperation.toString(getPackageName());
		
		if(packageName != null){
			return path + packageName+"/"+name;
		}else{
			return path + name;
		}
	}
	
	private String[] getSimpleNames(String fullyQualifiedName) {
		// TODO Auto-generated method stub
		String[] names = fullyQualifiedName.split("\\.");
		
		return names;
	}

	@Override
	public boolean ignoreOptionalProblems() {
		return false;
	}
	
	

}
