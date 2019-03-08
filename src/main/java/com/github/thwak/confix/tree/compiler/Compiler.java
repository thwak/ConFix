package com.github.thwak.confix.tree.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.util.IOUtils;

public class Compiler {

	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));

	public List<ClassFile> classes;
	public List<IProblem> problems;
	public String className;

	public Compiler(){
		problems = new ArrayList<>();
		classes = new ArrayList<>();
	}

	public boolean compile(File f, String targetPath, String classPath, String version) throws Exception {
		URL[] urls = getUrls(classPath);
		CustomClassLoader loader = new CustomClassLoader(urls, getClass().getClassLoader(), new ArrayList<ClassFile>());
		String source = IOUtils.readFile(f);
		Parser parser = new Parser(new String[]{}, new String[]{});
		CompilationUnit cu = parser.parse(source);
		return compile(loader, source, cu, targetPath, true, version);
	}

	private URL[] getUrls(String classPath) throws MalformedURLException {
		String[] classPaths = classPath.split(":");
		List<URL> urlList = new ArrayList<>();
		for(String cp : classPaths){
			if(cp.toLowerCase().endsWith(".jar")){
				urlList.add(new URL("jar:file://"+cp+"!/"));
			}else{
				urlList.add(new URL("file://"+cp+"/"));
			}
		}
		URL[] urls = new URL[urlList.size()];
		urlList.toArray(urls);
		return urls;
	}

	public boolean compile(File f, String targetPath, String classPath, String source, String target) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		PrintWriter outWriter = new PrintWriter(out);
		PrintWriter errWriter = new PrintWriter(err);
		StringBuffer command = new StringBuffer();
		command.append("-classpath ");
		command.append(classPath);
		command.append(" -d ");
		command.append(targetPath);
		command.append(" -source ");
		command.append(source);
		command.append(" -target ");
		command.append(target);
		command.append(" ");
		command.append(f.getAbsolutePath());
		String cmd = command.toString();
		boolean error = !org.eclipse.jdt.core.compiler.batch.BatchCompiler.compile(cmd, outWriter, errWriter, null);
		if(DEBUG) {
			System.out.println(cmd);
			System.out.println(out.toString());
			System.out.println(err.toString());
		}
		return error;
	}

	public boolean compile(String source, CompilationUnit unit, String path, boolean writeDown, String version) throws IOException, FileNotFoundException{
		return compile(getClass().getClassLoader(), source, unit, path, writeDown, version);
	}

	public boolean compile(ClassLoader loader, String source, CompilationUnit unit, String path, boolean writeDown, String version) throws IOException, FileNotFoundException{

		CompilationUnitImpl cu = new CompilationUnitImpl(unit);
		org.eclipse.jdt.internal.compiler.batch.CompilationUnit newUnit =
				new org.eclipse.jdt.internal.compiler.batch.CompilationUnit(
						source.toCharArray(), new String(cu.getFileName()), "UTF8");

		className = CharOperation.toString(cu.getPackageName()) + "." + new String(cu.getMainTypeName());

		CompilationProgress progress = null;
		CompilerRequestorImpl requestor = new CompilerRequestorImpl();
		CompilerOptions options = new CompilerOptions();
		Map<String,String> optionsMap = new HashMap<>();
		optionsMap.put(CompilerOptions.OPTION_Compliance, version);
		optionsMap.put(CompilerOptions.OPTION_Source, version);
		optionsMap.put(CompilerOptions.OPTION_LineNumberAttribute,CompilerOptions.GENERATE);
		optionsMap.put(CompilerOptions.OPTION_SourceFileAttribute,CompilerOptions.GENERATE);

		options.set(optionsMap);

		org.eclipse.jdt.internal.compiler.Compiler compiler =
				new org.eclipse.jdt.internal.compiler.Compiler(new NameEnvironmentImpl(loader, newUnit),
						DefaultErrorHandlingPolicies.proceedWithAllProblems(),
						options,requestor,new DefaultProblemFactory(Locale.getDefault()),
						null, progress);
		compiler.compile(new ICompilationUnit[]{ newUnit });

		classes = requestor.getClasses();
		problems = requestor.getProblems();

		boolean error = false;
		for (Iterator<IProblem> it = problems.iterator(); it.hasNext();) {
			IProblem problem = it.next();
			if(problem.isError()){
				error = true;
				break;
			}
		}

		if (writeDown) {
			for (ClassFile cf : classes) {
				String filePath	= CharOperation.charToString(cf.fileName());
				String packagePath = path + File.separator + filePath.substring(0, filePath.lastIndexOf("/")+1);
				File packageDir = new File(packagePath);
				if(!packageDir.exists()){
					packageDir.mkdirs();
				}
				File f = new File(path + File.separator + filePath + ".class");
				FileOutputStream fos = new FileOutputStream(f);
				fos.write(cf.getBytes());
				fos.flush();
				fos.close();
			}
		}

		return error;
	}
}
