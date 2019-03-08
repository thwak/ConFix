package com.github.thwak.confix.coverage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;

import com.github.thwak.confix.util.IOUtils;

public class CoverageAnalyzer {
	public static final String TEST_FAILED = "tests.failed";
	public static final String TEST_PASSED = "tests.passed";

	public TestListener listener;
	public List<String> classFiles;
	public List<String> testClasses;
	public String classPath;
	public String targetPath;
	public String jacocoPath;
	public String testJVM;
	public Map<String, CoverageInfo> coverage;
	public long timeout;

	public CoverageAnalyzer() {
		super();
		classFiles = new ArrayList<String>();
		testClasses = new ArrayList<>();
		coverage = new HashMap<>();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CoverageAnalyzer analyzer = new CoverageAnalyzer();
		try {
			analyzer.loadConfig();
			analyzer.runTests();
			analyzer.analyzeCoverage();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadConfig() {
		Properties props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new File("cfix.properties"));
			props.load(fis);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fis != null)
					fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		testJVM = getStringProperty(props, "jvm", "/usr/bin/java");
		jacocoPath = getStringProperty(props, "jacoco", "../../lib/jacocoagent.jar");
		classPath = getStringProperty(props, "cp.test", "");
		String libPath = getStringProperty(props, "cp.lib", "");
		String priority = getStringProperty(props, "cp.test.priority", "local");
		if(libPath.length() > 0) {
			if("cfix".equals(priority)) {
				classPath = libPath + File.pathSeparatorChar + classPath;
			} else {
				classPath = classPath + File.pathSeparatorChar + libPath;
			}
		}
		targetPath = getStringProperty(props, "target.dir", "target/classes");
		String relevant = IOUtils.readFile("tests.relevant");
		testClasses.addAll(Arrays.asList(relevant.split("\n")));
		List<String> modifiedClasses = getListProperty(props, "classes.modified", ",");
		findClassFiles(modifiedClasses, targetPath);
		timeout = Long.parseLong(getStringProperty(props, "timeout", "10"))*1000;
	}

	private void findClassFiles(List<String> modifiedClasses, String targetPath) {
		for(String name : modifiedClasses){
			String path = targetPath + File.separator + name.replaceAll("\\.", File.separator+File.separator) + ".class";
			classFiles.add(path);

			//Include all inner class files.
			int lastDotIndex = name.lastIndexOf('.');
			String dirPath = name.substring(0, lastDotIndex).replaceAll("\\.", File.separator+File.separator);
			String className = name.substring(lastDotIndex+1);
			File dir = new File(targetPath + File.separator + dirPath);
			String regex = className+"\\.class|"+className+"\\$.*\\.class";
			List<String> fileNames = getFileNames(dir, regex);
			for(String fileName : fileNames){
				classFiles.add(targetPath + File.separator + dirPath + File.separator + fileName);
			}
		}
	}

	private List<String> getFileNames(File dir, String regex) {
		List<String> fileNames = new ArrayList<>();
		for(File f : dir.listFiles()){
			if(f.isDirectory()){
				fileNames.addAll(getFileNames(f, regex));
			}else if(f.getName().matches(regex)){
				fileNames.add(f.getName());
			}
		}
		return fileNames;
	}

	private static List<String> getListProperty(Properties props, String key, String delim) {
		List<String> list = new ArrayList<>();
		String value = props.getProperty(key);
		if(value != null){
			String[] entries = value.split(delim);
			list.addAll(Arrays.asList(entries));
		}
		return list;
	}

	private String getStringProperty(Properties props, String key, String defaultValue) {
		return props.getProperty(key) == null ? defaultValue : props.getProperty(key);
	}

	public void deleteExecutionDataFile() {
		File executionDataFile = new File("jacoco.exec");
		executionDataFile.delete();
	}

	public void analyzeCoverage() throws Exception {
		System.out.println("Analyzing Coverage...");

		Tester tester = new Tester(testJVM, timeout);
		System.out.println("Get coverage for failed tests - "+listener.failedTests.size());
		if (listener.failedTests.size() > 0) {
			deleteExecutionDataFile();
			for (String testClassName : testClasses) {
				tester.runSampleTestsWithJacoco(classPath, testClassName, TEST_FAILED, jacocoPath, true);
			}
			analyzeCoverageData(true);
		}

		System.out.println("Get coverage for passed tests - "+listener.passedTests.size());
		if (listener.passedTests.size() > 0) {
			deleteExecutionDataFile();
			for (String testClassName : testClasses) {
				tester.runSampleTestsWithJacoco(classPath, testClassName, TEST_PASSED, jacocoPath, true);
			}
			analyzeCoverageData(false);
		}
		IOUtils.storeObject("coverage.obj", coverage);
	}

	/**
	 * @param coveredLines
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void analyzeCoverageData(boolean isNegative)
			throws FileNotFoundException, IOException {
		ExecutionDataStore executionData = new ExecutionDataStore();
		executionData = readExecutionData();

		final CoverageBuilder coverageBuilder = new CoverageBuilder();
		final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
		for (String className : classFiles) {
			InputStream targetClass = getTargetClass(className);
			analyzer.analyzeClass(targetClass, className);
		}

		for (final IClassCoverage cc : coverageBuilder.getClasses()){
			String className = cc.getName().replaceAll("\\/", "\\.");
			if(!coverage.containsKey(className))
				coverage.put(className, new CoverageInfo(className));
			for (int i = cc.getFirstLine(); i <= cc.getLastLine(); i++){
				int status = cc.getLine(i).getStatus();
				if(isCovered(status)){
					if(isNegative)
						coverage.get(className).addNegCoverage(i);
					else
						coverage.get(className).addPosCoverage(i);
				}
			}
			coverage.get(className).setLineCount(cc.getLineCounter().getTotalCount());
		}
	}

	private boolean isCovered(final int status) {
		switch (status) {
		case ICounter.NOT_COVERED:
			return false;
		case ICounter.PARTLY_COVERED:
			return true;
		case ICounter.FULLY_COVERED:
			return true;
		}
		return false;
	}

	public InputStream getTargetClass(String resource) throws IOException {
		FileInputStream is = new FileInputStream(resource);
		return is;
	}

	/**
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private ExecutionDataStore readExecutionData() throws FileNotFoundException, IOException {
		FileInputStream in = new FileInputStream(new File("jacoco.exec"));
		ExecutionDataStore executionDataStore = new ExecutionDataStore();
		SessionInfoStore sessionInfoStore = new SessionInfoStore();
		ExecutionDataReader reader = new ExecutionDataReader(in);
		reader.setSessionInfoVisitor(sessionInfoStore);
		reader.setExecutionDataVisitor(executionDataStore);
		while(reader.read()){
		}
		in.close();

		return executionDataStore;
	}

	public void runTests() throws ClassNotFoundException, IOException {
		System.out.println("Running All Tests");
		Tester tester = new Tester(testJVM, timeout);
		listener = tester.runTests(testClasses, classPath);
		storeTestList();
	}

	private void storeTestList() throws IOException{
		IOUtils.storeDataToFile(listener.passedTests, TEST_PASSED);
		IOUtils.storeDataToFile(listener.failedTests, TEST_FAILED);
	}

}
