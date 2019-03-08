package com.github.thwak.confix.junit;

import java.util.List;

import org.junit.Ignore;
import org.junit.internal.builders.IgnoredClassRunner;
import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.RunnerBuilder;

public class TestRunnerBuilder extends RunnerBuilder {

	public List<String> sampledTests;
	public boolean sampleOnly;
	
	public TestRunnerBuilder(List<String> sampledTests, boolean sampleOnly){
		this.sampledTests = sampledTests;
		this.sampleOnly = sampleOnly;
	}
	
	@Override
	public Runner runnerForClass(Class<?> testClass) throws Throwable {
		
		if(isIgnored(testClass)){
			return new IgnoredClassRunner(testClass);
		}else if(hasSuiteAnnotation(testClass)){
			RunnerBuilder builder = new TestRunnerBuilder(sampledTests, sampleOnly);
			return new Suite(testClass, builder);
		}else if(hasSuiteMethod(testClass)){
			return new SampleSuiteMethod(testClass, sampledTests, sampleOnly);
		}else if(isPre4Test(testClass)){
			return new SampleJUnit38TestRunner(testClass, sampledTests, sampleOnly);
		}else{
			return new SampleJUnit4TestRunner(testClass, sampledTests, sampleOnly);
		}
	}

	public static boolean isPre4Test(Class<?> testClass) {
		return junit.framework.TestCase.class.isAssignableFrom(testClass);
	}
	
	public static boolean hasSuiteMethod(Class<?> testClass) {
		try {
			testClass.getMethod("suite");
		} catch (NoSuchMethodException e) {
			return false;
		}
		return true;
	}
	
	public static boolean hasSuiteAnnotation(Class<?> testClass){
		return testClass.getAnnotation(Suite.SuiteClasses.class) != null;
	}
	
	public static boolean isIgnored(Class<?> testClass){
		return testClass.getAnnotation(Ignore.class) != null;
	}
}
