package com.github.thwak.confix.junit;

import java.util.List;

import junit.framework.Test;

import org.junit.internal.runners.JUnit38ClassRunner;

public class SampleJUnit38TestRunner extends JUnit38ClassRunner {

	public SampleJUnit38TestRunner(Class<?> klass, List<String> sampledTests, boolean sampleOnly) {
		this(new SampledTestSuite(klass, sampledTests, sampleOnly));
	}

	public SampleJUnit38TestRunner(Test test) {
		super(test);
	}
	
}
