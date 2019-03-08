package com.github.thwak.confix.coverage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestResult implements Serializable {

	private static final long serialVersionUID = 6277416588685520199L;
	public int runCnt = 0;
	public int failCnt = 0;
	public int exitValue = -1;
	public List<String> failures = new ArrayList<String>();
	public List<String> failedTests = new ArrayList<String>();

	public TestResult(int runCnt, int failCnt, int exitValue) {
		super();
		this.runCnt = runCnt;
		this.failCnt = failCnt;
		this.exitValue = exitValue;
	}

	public void parseJunitResult(String junitResult) {
		String resultPattern = "(Tests run:\\s+)([0-9]+)(,\\s+Failures:\\s+)([0-9]+)";
		Pattern pattern = Pattern.compile(resultPattern);
		Matcher matcher = pattern.matcher(junitResult);
		if(matcher.find()){
			runCnt = Integer.parseInt(matcher.group(2));
			failCnt = Integer.parseInt(matcher.group(4));
			String failurePattern = "^(\\d+\\)\\s+)(.+)(\\()(.+)(\\))$";
			pattern = Pattern.compile(failurePattern);
			matcher = pattern.matcher(junitResult);
			String[] lines = junitResult.split("\n");
			StringBuffer sb = new StringBuffer();
			boolean append = false;
			for(String line : lines){
				if(line.startsWith("FAILURES!!!") || line.startsWith("Tests run:")){
					if(sb != null && sb.length() > 0){
						failures.add(sb.toString());
						sb = new StringBuffer();
					}
					append = false;
				}

				matcher = pattern.matcher(line);
				if(matcher.find()){
					if(sb.length() > 0){
						failures.add(sb.toString());
					}
					if (!matcher.group(2).trim().equals("initializationError")
							&& !matcher.group(2).trim().equals("warning")) {
						append = true;
						sb = new StringBuffer(matcher.group(0));
						sb.append("\n");
						failedTests.add(matcher.group(4) + "#"
								+ matcher.group(2));
					} else {
						append = false;
						sb = new StringBuffer();
						runCnt--;
						failCnt--;
					}

				}else if(append){
					sb.append(line);
					sb.append("\n");
				}
			}
		}else{
			//In case that all tests are passed.
			String okPattern = "(OK\\s+\\()([0-9]+)(\\s+test[s]*\\))";
			pattern = Pattern.compile(okPattern);
			matcher = pattern.matcher(junitResult);
			if(matcher.find()){
				runCnt = Integer.parseInt(matcher.group(2));
				failCnt = 0;
			}
		}
	}

	public void update(TestResult result) {
		runCnt += result.runCnt;
		failCnt += result.failCnt;
		failures.addAll(result.failures);
		failedTests.addAll(result.failedTests);
		exitValue = exitValue > result.exitValue ? exitValue : result.exitValue;
	}
}
