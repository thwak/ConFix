package com.github.thwak.confix.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.TestResult;
import com.github.thwak.confix.coverage.Tester;
import com.github.thwak.confix.patch.PatchInfo;
import com.github.thwak.confix.patch.PatchStrategy;
import com.github.thwak.confix.patch.PatchUtils;
import com.github.thwak.confix.patch.Patcher;
import com.github.thwak.confix.patch.StrategyFactory;
import com.github.thwak.confix.patch.TargetLocation;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.tree.compiler.Compiler;
import com.github.thwak.confix.util.IOUtils;

public class ConFix {

	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
	public static final int PASS = 0;
	public static final int COMPILE_ERROR = 1;
	public static final int TEST_FAILURE = 2;
	public static final int TEST_TIMEOUT = 3;
	public static final int BREAK_FUNC = 4;
	public static final int TRIGGER_TEST_FAILURE = 5;
	public static final int RELEVANT_TEST_FAILURE = 6;

	public static String testClassPath;
	public static String compileClassPath;
	public static String[] testClassPathEntries;
	public static String[] compileClassPathEntries;
	public static String libClassPath;
	public static List<String> modifiedClasses = new ArrayList<>();
	public static CoverageManager coverage;
	public static String sourceDir;
	public static String targetDir;
	public static String tempDir;
	public static List<String> poolList;
	public static ChangePool pool;
	public static int patchCount = 20;
	public static int maxTrials = 10;
	public static String candidateDir = "candidates";
	public static String patchDir = "patches";
	public static String jvm;
	public static String version;
	public static long timeout;
	public static List<String> triggerTests = new ArrayList<>();
	public static List<String> relTests = new ArrayList<>();
	public static List<String> allTests = new ArrayList<>();
	public static Set<String> brokenTests = new HashSet<>();
	public static long seed;
	public static int numOfTriggers;
	public static long timeBudget;
	public static String pStrategyKey;
	public static String cStrategyKey;
	public static String flMetric;
	public static int maxPoolLoad;
	public static int maxChangeCount;

	public static void main(String[] args) {
		//Load necessary information.
		loadProperties("confix.properties");
		loadTests();
		loadCoverage();
		if(coverage == null || coverage.getNegCoveredClasses().size() == 0){
			System.out.println("No class/coverage information.");
			return;
		}else if(poolList.size() == 0){
			System.out.println("No change pool is specified.");
			return;
		}
		long startTime = System.currentTimeMillis();
		seed = seed == -1 ? new Random(startTime).nextInt(100) : seed;
		Random r = new Random(seed);
		System.out.println("Random Seed:" + seed);

		//For Loc Info collection.
		String oldLocKey = "";
		String currentLocKey = null;
		Change oldApplied = null;
		String locPoolPath = "";
		StringBuffer sbLoc = new StringBuffer("Pool,CheckedLines,CheckedLoc,CheckedChange,AppliedChange");

		int totalCompileError = 0;
		int totalTestFailure = 0;
		int totalCandidateNum = 0;
		for (String poolPath : poolList) {
			loadChangePool(poolPath);
			locPoolPath = poolPath;
			//Initialize patcher and strategies.
			int candidateNum = 1;
			int compileError = 0;
			int testFailure = 0;
			int locNum = 0;
			int changeNum = 0;
			int applied = 0;
			int locChangeCount = 0;
			boolean success = false;
			boolean terminate = false;
			String targetClass = null;
			Patcher patcher = null;
			System.out.println("Preparing patch generation...");
			PatchStrategy pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, r, flMetric,
					cStrategyKey, sourceDir, compileClassPathEntries);
			pStrategy.finishUpdate();
			IOUtils.storeContent("coveredlines.txt", pStrategy.getLineInfo());
			System.out.println("Done.");

			pool.poolName = poolPath.substring(poolPath.lastIndexOf('/')+1);

			//Generating patch candidates.
			while (candidateNum <= patchCount) {
				int trial = 0;
				int returnCode = -1;
				TargetLocation loc = pStrategy.selectLocation();
				targetClass = loc == null ? "" : loc.className;
				currentLocKey = pStrategy.getCurrentLocKey();
				if(!oldLocKey.equals(currentLocKey)) {
					oldLocKey = currentLocKey;
					locNum++;
					locChangeCount = 0;
				}
				patcher = pStrategy.patcher();
				if(patcher == null)
					break;
				Change change = pStrategy.selectChange();
				if(change != null) {
					changeNum++;
					locChangeCount++;
				}
				if(locChangeCount > maxChangeCount) {
					pStrategy.nextLoc();
					continue;
				}
				Set<String> candidates = new HashSet<>();
				do {
					PatchInfo info = new PatchInfo(targetClass, change, loc);
					try {
						returnCode = patcher.apply(loc, change, info);
					} catch (Exception e) {
						if(DEBUG) {
							System.out.println("Change Application Error.");
							System.out.println("Fix Location");
							System.out.println(loc);
							System.out.println("Applied Change");
							System.out.println(change);
							e.printStackTrace();
						}
						returnCode = Patcher.C_NOT_APPLIED;
						break;
					}
					trial++;
					if (returnCode == Patcher.C_NOT_INST) {
						break;
					} else {
						if (returnCode == Patcher.C_APPLIED) {
							System.out.println("Patch Candidate-" + candidateNum + " is generated.");
							if(change != null && !change.equals(oldApplied)) {
								oldApplied = change;
								applied++;
							}
							String editText = PatchUtils.getEditText(info, pool);
							String newSource = patcher.getNewSource();
							String candidateFileName = storeCandidate(newSource, editText, targetClass, change);
							IOUtils.delete(new File(tempDir));
							int result = verify(candidateFileName);
							if (result == PASS) {
								String patchFileName = storePatch(newSource, editText, targetClass, change);
								System.out.println("A Patch Found! - " + patchFileName);
								System.out.println("Candidate Number:" + candidateNum);
								String elapsedTime = PatchUtils.getElapsedTime(System.currentTimeMillis()-startTime);
								totalCompileError += compileError;
								totalTestFailure += testFailure;
								totalCandidateNum += candidateNum;
								StringBuffer sb = new StringBuffer();
								sb.append("Seed:");
								sb.append(seed);
								sb.append("|Pool:");
								sb.append(poolPath);
								sb.append("|PatchNum:");
								sb.append(totalCandidateNum);
								sb.append("|Time:");
								sb.append(elapsedTime.trim());
								sb.append("|CompileError:");
								sb.append(totalCompileError);
								sb.append("|TestFailure:");
								sb.append(totalTestFailure);
								sb.append("|Concretize:");
								sb.append(info.getConcretize());
								sb.append("\n");
								sb.append(patchFileName);
								sb.append("\n");
								IOUtils.storeContent("patch_info", sb.toString(), true);
								success = true;
								break;
							} else {
								if (result == COMPILE_ERROR) {
									compileError++;
								} else if (result == TEST_FAILURE || result == TEST_TIMEOUT || result == BREAK_FUNC
										|| result == TRIGGER_TEST_FAILURE || result == RELEVANT_TEST_FAILURE) {
									testFailure++;
								}
							}
							candidateNum++;
							System.out.println();
							if (candidateNum > patchCount || result == TEST_TIMEOUT || result == BREAK_FUNC
									|| !candidates.add(editText))
								break;
						} else if (returnCode == Patcher.C_NO_FIXLOC) {
							break;
						} else if (returnCode == Patcher.C_NO_CHANGE) {
							break;
						}
					}
					if(isTimeBudgetPassed(startTime)) {
						terminate = true;
						System.out.println("Time Budget is passed.");
						break;
					}
				} while (trial < maxTrials);
				//Reset.
				change = null; loc = null; candidates = null;
				if (success || terminate || returnCode == Patcher.C_NO_FIXLOC)
					break;
			}
			if (success || terminate) {
				System.out.println("Elapsed Time: "+PatchUtils.getElapsedTime(System.currentTimeMillis()-startTime));
				printLocInfo(pStrategy.getCurrentLineIndex()+1, locNum, changeNum, applied, locPoolPath, sbLoc);
				System.out.println("Compile Errors:" + compileError);
				System.out.println("Test Failures:" + testFailure);
				IOUtils.storeContent("lines-"+pool.poolName+".txt", pStrategy.getLocInfo());
				break;
			} else {
				System.out.println("No patch found.");
				System.out.println("Elapsed Time: "+PatchUtils.getElapsedTime(System.currentTimeMillis()-startTime));
				System.out.println("Compile Errors:" + compileError);
				System.out.println("Test Failures:" + testFailure);
				totalCompileError += compileError;
				totalTestFailure += testFailure;
				totalCandidateNum += candidateNum;
				printLocInfo(pStrategy.getCurrentLineIndex()+1, locNum, changeNum, applied, locPoolPath, sbLoc);
				IOUtils.storeContent("lines-"+pool.poolName+".txt", pStrategy.getLocInfo());
			}
		}
		IOUtils.storeContent("locinfo.csv", sbLoc.toString());
	}

	private static void printLocInfo(int lines, int locNum, int changeNum, int applied, String poolPath, StringBuffer sb) {
		System.out.println("Checked Lines:"+lines);
		System.out.println("Checked Fix Locs:"+locNum);
		System.out.println("Checked Changes:"+changeNum);
		System.out.println("Applied Changes:"+applied);
		sb.append("\n");
		sb.append(poolPath);
		sb.append(",");
		sb.append(lines);
		sb.append(",");
		sb.append(locNum);
		sb.append(",");
		sb.append(changeNum);
		sb.append(",");
		sb.append(applied);
	}

	private static boolean isTimeBudgetPassed(long startTime) {
		if(timeBudget < 0)
			return false;
		long expire = startTime + (timeBudget * 60 * 60 * 1000);
		return System.currentTimeMillis() >= expire;
	}

	private static void loadTests() {
		String trigger = IOUtils.readFile("tests.trigger");
		String relevant = IOUtils.readFile("tests.relevant");
		String all = IOUtils.readFile("tests.all");
		Set<String> testSet = new HashSet<>();
		String[] tests = trigger.split("\n");
		numOfTriggers = tests.length;
		for(String test : tests){
			//Get the class name only for trigger tests.
			if(!test.startsWith("#"))
				testSet.add(test.split("::")[0]);
		}
		triggerTests.addAll(testSet);
		relTests.addAll(Arrays.asList(relevant.split("\n")));
		allTests.addAll(Arrays.asList(all.split("\n")));
		File f = new File("tests.broken");
		if(f.exists()) {
			String broken = IOUtils.readFile("tests.broken");
			tests = broken.split("\n");
			for(String t : tests)
				brokenTests.add(t.replace("::", "#"));
		}
	}

	private static int verify(String patchFileName) {
		//Compile a given patch candidate.
		if(!compileCheck(patchFileName)){
			return COMPILE_ERROR;
		}else{
			return testCheck();
		}
	}

	public static boolean compileCheck(String patchFileName){
		File patchFile = new File(patchFileName);
		Compiler compiler = new Compiler();
		try {
			boolean error = compiler.compile(patchFile, tempDir, compileClassPath, version, version);
			if(error){
				System.out.println("Compile error.");
				return false;
			}
		} catch (Exception e) {
			System.out.println("Compile error.");
			return false;
		}
		return true;
	}

	private static void removeBrokenTests(TestResult result) {
		if(result != null) {
			Set<String> falseAlarm = new HashSet<>();
			for(String test : result.failedTests) {
				if(brokenTests.contains(test))
					falseAlarm.add(test);
			}
			result.failedTests.removeAll(falseAlarm);
			result.failCnt = result.failedTests.size();
		}
	}

	public static int testCheck(){
		Tester tester = new Tester(jvm, timeout);
		//Run failed tests.
		TestResult result = null;
		String classPath = tempDir + File.pathSeparator + testClassPath;
		try {
			result = tester.runTestsWithJUnitCore(triggerTests, classPath);
			removeBrokenTests(result);
			if(result != null
					&& result.failCnt > 0){
				System.out.println("Trigger tests - " + result.failCnt + " Tests Failed.");
				if(result.failCnt > numOfTriggers)
					return BREAK_FUNC;
				return TRIGGER_TEST_FAILURE;
			}else if(result == null || result.runCnt == 0){
				System.out.println("An error occurs while running trigger tests - no records.");
				return TEST_TIMEOUT;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("An error occurs while running trigger tests.");
			return TRIGGER_TEST_FAILURE;
		}

		//If passed, run relevant tests.
		if(result != null
				&& result.runCnt > 0
				&& result.failCnt == 0){
			try {
				result = tester.runTestsWithJUnitCore(relTests, classPath);
				removeBrokenTests(result);
				if(result != null
						&& result.failCnt > 0){
					System.out.println("Relevant tests - " + result.failCnt + " Tests Failed.");
					return RELEVANT_TEST_FAILURE;
				}else if(result == null || result.runCnt == 0){
					System.out.println("An error occurs while running relevant tests. - no records.");
					return TEST_TIMEOUT;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("An error occurs while running relevant tests.");
				return RELEVANT_TEST_FAILURE;
			}
		}

		//If passed, run all tests.
		if(result != null
				&& result.runCnt > 0
				&& result.failCnt == 0){
			try {
				result = tester.runTestsWithJUnitCore(allTests, classPath);
				removeBrokenTests(result);
				if(result != null
						&& result.failCnt > 0){
					System.out.println("All tests - " + result.failCnt + " Tests Failed.");
					return TEST_FAILURE;
				}else if(result == null || result.runCnt == 0){
					System.out.println("An error occurs while running all tests. - no records.");
					return TEST_TIMEOUT;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("An error occurs while running all tests.");
				return TEST_FAILURE;
			}
		}

		//If passed, return true.
		if(result != null
				&& result.runCnt > 0
				&& result.failCnt == 0){
			return PASS;
		}else{
			return TEST_FAILURE;
		}
	}

	private static String storePatch(String newSource, String patch, String targetClass, Change change) {
		int lastDotIndex = targetClass.lastIndexOf('.');
		String packageName = targetClass.substring(0, lastDotIndex);
		String fileName = targetClass.substring(lastDotIndex+1) + ".java";
		File dir = new File(patchDir);
		File[] dirs = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() && f.getName().matches("[0-9]+");
			}
		});
		int patchId = dirs == null ? 0 : dirs.length;
		String patchPath = patchDir + File.separator + patchId + File.separator;
		String packagePath = patchPath + packageName.replaceAll("\\.", File.separator+File.separator);
		File packageDir = new File(packagePath);
		if(!packageDir.exists())
			packageDir.mkdirs();
		String filePath = Paths.get(packagePath + File.separator + fileName).toString();
		IOUtils.storeContent(filePath, newSource);
		IOUtils.storeContent(patchPath + "edit", patch);
		IOUtils.storeObject(patchPath + "change.obj", change);

		return filePath;
	}

	private static String storeCandidate(String newSource, String patch, String targetClass, Change change) {
		int lastDotIndex = targetClass.lastIndexOf('.');
		String packageName = targetClass.substring(0, lastDotIndex);
		String fileName = targetClass.substring(lastDotIndex+1) + ".java";
		String candidatePath = candidateDir + File.separator + "candidate" + File.separator;
		String packagePath = candidatePath + packageName.replaceAll("\\.", File.separator+File.separator);
		File dir = new File(packagePath);
		if(!dir.exists())
			dir.mkdirs();
		String filePath = Paths.get(packagePath + File.separator + fileName).toString();
		IOUtils.storeContent(filePath, newSource);
		IOUtils.storeContent(candidatePath + "edit", patch);
		IOUtils.storeObject(candidatePath + "change.obj", change);

		return filePath;
	}

	private static void loadChangePool(String poolPath) {
		System.out.print("Loading Change Pool...");
		pool = new ChangePool();
		pool.loadFrom(new File(poolPath));
		pool.maxLoadCount = maxPoolLoad;
		System.out.println("Done.");
		System.out.println("Pool:"+poolPath);
	}

	private static void loadProperties(String fileName) {
		Properties props = new Properties();
		File f = new File(fileName);
		try {
			FileInputStream fis = new FileInputStream(f);
			props.load(fis);
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		libClassPath = PatchUtils.getStringProperty(props, "cp.lib", "");
		testClassPath = PatchUtils.getStringProperty(props, "cp.test", "");
		compileClassPath = PatchUtils.getStringProperty(props, "cp.compile", "");
		String priority = PatchUtils.getStringProperty(props, "cp.test.priority", "local");
		if(libClassPath.length() > 0) {
			if("cfix".equals(priority)) {
				testClassPath = libClassPath + File.pathSeparatorChar + testClassPath;
			} else {
				testClassPath = testClassPath + File.pathSeparatorChar + libClassPath;
			}
		}
		testClassPathEntries = testClassPath.split(File.pathSeparator);
		compileClassPathEntries = compileClassPath.split(File.pathSeparator);
		sourceDir = PatchUtils.getStringProperty(props, "src.dir", "src/main/java");
		targetDir = PatchUtils.getStringProperty(props, "target.dir", "target/classes");
		modifiedClasses = PatchUtils.getListProperty(props, "classes.modified", ",");
		poolList = PatchUtils.getListProperty(props, "pool.path", ",");
		jvm = PatchUtils.getStringProperty(props, "jvm", "/usr/bin/java");
		version = PatchUtils.getStringProperty(props, "version", "1.7");
		timeout = Long.parseLong(PatchUtils.getStringProperty(props, "timeout", "10"))*1000;
		patchCount = Integer.parseInt(PatchUtils.getStringProperty(props, "patch.count", "20"));
		maxTrials = Integer.parseInt(PatchUtils.getStringProperty(props, "max.trials", "10"));
		maxChangeCount = Integer.parseInt(PatchUtils.getStringProperty(props, "max.change.count", "25"));
		maxPoolLoad = Integer.parseInt(PatchUtils.getStringProperty(props, "max.pool.load", "1000"));
		seed = Long.parseLong(PatchUtils.getStringProperty(props, "seed", "-1"));
		tempDir = new File("tmp").getAbsolutePath();
		timeBudget = Long.parseLong(PatchUtils.getStringProperty(props, "time.budget", "-1"));
		pStrategyKey = PatchUtils.getStringProperty(props, "patch.strategy", "flfreq");
		cStrategyKey = PatchUtils.getStringProperty(props, "concretize.strategy", "tc");
		flMetric = PatchUtils.getStringProperty(props, "fl.metric", "ochiai");
	}

	private static void loadCoverage(){
		System.out.print("Loading Coverage Information....");
		coverage = (CoverageManager)IOUtils.readObject("coverage-info.obj");
		System.out.println("Done.");
	}
}
