package com.github.thwak.confix.coverage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import com.github.thwak.confix.util.IOUtils;

public class Tester {
	public static final long DEFAULT_TIMEOUT = 10000;
	public static final String SAMPLE_TEST_RUNNER = "com.github.thwak.cfix.coverage.SampleTestRunner";
	public static final String SINGLE_TEST_CLASS_RUNNER = "com.github.thwak.cfix.coverage.SingleTestClassRunner";
	public static final String JUNIT_RUNNER = "org.junit.runner.JUnitCore";
	protected static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
	public static final String TZ_INFO = System.getProperty("user.timezone");

	private String jvm;
	public long timeout;

	public Tester(String jvm){
		this.jvm = jvm;
		timeout = DEFAULT_TIMEOUT;
	}

	public Tester(String jvm, long timeout){
		this.jvm = jvm;
		this.timeout = timeout;
	}

	public TestListener runTests(List<String> testClasses, String classPath) throws IOException {
		TestListener listener = new TestListener();

		for(String testClassName : testClasses){
			CommandLine command = CommandLine.parse(jvm);
			command.addArgument("-Xms512m");
			command.addArgument("-Xmx2048m");
			command.addArgument("-classpath");
			command.addArgument(classPath);
			command.addArgument("-Duser.timezone="+TZ_INFO);
			command.addArgument("-Duser.language=en");
			command.addArgument(SAMPLE_TEST_RUNNER);
			command.addArgument(testClassName);
			command.addArgument("sample");
			command.addArgument("false");

			ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
			DefaultExecutor executor = new DefaultExecutor();
			executor.setWatchdog(watchdog);

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			executor.setExitValue(0);
			executor.setStreamHandler(new PumpStreamHandler(out));

			try {
				executor.execute(command);
				if(DEBUG)
					System.out.println(out.toString());
			} catch (ExecuteException e) {
				System.err.println("Exit Value:"+e.getExitValue());
				e.printStackTrace();
				System.out.println("Error while running test class "+testClassName);
				System.err.println(out.toString());
				TestListener testListener = new TestListener();
				testListener.failedTests.add(testClassName+"#"+"all");
				listener.update(testListener);
			} catch (IOException e) {
				e.printStackTrace();
				TestListener testListener = new TestListener();
				testListener.failedTests.add(testClassName+"#"+"all");
				listener.update(testListener);
			}

			TestListener testListener = new TestListener();
			testListener = readTestResult();
			listener.update(testListener);
		}

		return listener;
	}

	public TestListener runSampleTests(String classPath, String className, String sampleFileName) throws IOException {
		CommandLine command = CommandLine.parse(jvm);
		command.addArgument("-Xms512m");
		command.addArgument("-Xmx2048m");
		command.addArgument("-classpath");
		command.addArgument(classPath);
		command.addArgument("-Duser.timezone="+TZ_INFO);
		command.addArgument("-Duser.language=en");
		command.addArgument(SAMPLE_TEST_RUNNER);
		command.addArgument(className);
		command.addArgument(sampleFileName);
		command.addArgument("true");

		ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
		DefaultExecutor executor = new DefaultExecutor();
		executor.setWatchdog(watchdog);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		executor.setExitValue(0);
		executor.setStreamHandler(new PumpStreamHandler(out));

		try {
			executor.execute(command);
		} catch (ExecuteException e) {
			System.err.println("Exit Value:"+e.getExitValue());
			e.printStackTrace();
			System.out.println("Error while running test class "+className+" with sample "+sampleFileName);
			System.err.println(out.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		TestListener listener = readTestResult();
		return listener;
	}

	public void runSampleTestsWithJacoco(String classPath, String className, String sampleFileName, String jacocoPath, boolean append) throws IOException {
		CommandLine command = CommandLine.parse(jvm);
		command.addArgument("-Xms512m");
		command.addArgument("-Xmx2048m");
		command.addArgument("-classpath");
		command.addArgument(classPath);
		command.addArgument("-Duser.timezone="+TZ_INFO);
		command.addArgument("-Duser.language=en");
		command.addArgument("-javaagent:"+jacocoPath+"=excludes=org.junit.*,append="+append);
		command.addArgument(SAMPLE_TEST_RUNNER);
		command.addArgument(className);
		command.addArgument(sampleFileName);
		command.addArgument("true");

		ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
		DefaultExecutor executor = new DefaultExecutor();
		executor.setWatchdog(watchdog);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		executor.setExitValue(0);
		executor.setStreamHandler(new PumpStreamHandler(out));

		try {
			executor.execute(command);
		} catch (ExecuteException e) {
			System.err.println("Exit Value:"+e.getExitValue());
			e.printStackTrace();
			System.out.println("Error while running test class "+className+" with sample "+sampleFileName);
			System.out.println(out.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public TestResult runTestsWithJUnitCore(List<String> testClasses, String classPath) throws ClassNotFoundException, IOException {
		TestResult result = new TestResult(0, 0, 0);
		for(String testClassName : testClasses){
			if(DEBUG)
				System.out.println("Executing "+testClassName);
			TestResult r = runTest(testClassName, classPath);
			if(DEBUG)
				System.out.println("Result:"+r.failCnt+"/"+r.runCnt);
			result.update(r);
		}
		return result;
	}

	public TestResult runTest(String testClassName, String classPath) throws ClassNotFoundException, IOException {
		TestResult result = null;
		CommandLine command = CommandLine.parse(jvm);
		command.addArgument("-Xms512m");
		command.addArgument("-Xmx2048m");
		command.addArgument("-classpath");
		command.addArgument(classPath);
		command.addArgument("-Duser.timezone="+TZ_INFO);
		command.addArgument("-Duser.language=en");

		command.addArgument("org.junit.runner.JUnitCore");
		command.addArgument(testClassName);

		ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
		DefaultExecutor executor = new DefaultExecutor();

		executor.setWatchdog(watchdog);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		executor.setExitValue(0);
		executor.setStreamHandler(new PumpStreamHandler(out));

		int exitValue = -1;
		try {
			exitValue = executor.execute(command);
		} catch (ExecuteException e) {
			exitValue = e.getExitValue();
		}

		result = new TestResult(0, 0, exitValue);
		String junitResult = out.toString();
		result.parseJunitResult(junitResult);
		//Remove warning, initializationError.
		for(String failedTest : result.failedTests){
			if(failedTest.trim().contains("#warning")
					|| failedTest.trim().contains("#initializationError")){
				result.failCnt--;
			}
		}
		if(DEBUG){
			System.out.println(junitResult);
		}

		return result;
	}

	private TestListener readTestResult() {
		TestListener listener = (TestListener)IOUtils.readObject("result");
		if(listener == null)
			listener = new TestListener();
		return listener;
	}
}
