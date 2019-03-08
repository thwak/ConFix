package com.github.thwak.confix.junit;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import junit.framework.Test;
import junit.framework.TestSuite;

public class SampledTestSuite extends TestSuite {

	private String fName;
	private Vector<Test> fTests= new Vector<Test>(10); // Cannot convert this to List because it is used directly by some test runners
	private List<String> sampledTests;
	private boolean sampleOnly;
	
	public SampledTestSuite(Class<?> theClass, List<String> sampledTests, boolean sampleOnly) {
		this.sampledTests = sampledTests;
		this.sampleOnly = sampleOnly;
		addTestsFromTestClass(theClass);
	}
	
	private void addTestsFromTestClass(final Class<?> theClass) {
		fName= theClass.getName();
		try {
			getTestConstructor(theClass); // Avoid generating multiple error messages
		} catch (NoSuchMethodException e) {
//			addTest(warning("Class "+theClass.getName()+" has no public constructor TestCase(String name) or TestCase()"));
			return;
		}

		if (!Modifier.isPublic(theClass.getModifiers())) {
//			addTest(warning("Class "+theClass.getName()+" is not public"));
			return;
		}

		Class<?> superClass= theClass;
		List<String> names= new ArrayList<String>();
		while (Test.class.isAssignableFrom(superClass)) {
			for (Method each : superClass.getDeclaredMethods()){
				String name = theClass.getName()+"#"+each.getName();
				if(sampleOnly && !sampledTests.contains(name)){
					continue;
				}
				addTestMethod(each, names, theClass);
			}
			superClass= superClass.getSuperclass();
		}
//		if (fTests.size() == 0)
//			addTest(warning("No tests found in "+theClass.getName()));
	}

	private void addTestMethod(Method m, List<String> names, Class<?> theClass) {
		String name = m.getName();
		if (names.contains(name))
			return;
		if (! isPublicTestMethod(m)) {
			if (isTestMethod(m))
				addTest(warning("Test method isn't public: "+ m.getName() + "(" + theClass.getCanonicalName() + ")"));
			return;
		}
		names.add(name);
		addTest(createTest(theClass, name));
	}
	
	private boolean isPublicTestMethod(Method m) {
		return isTestMethod(m) && Modifier.isPublic(m.getModifiers());
	}
	 
	private boolean isTestMethod(Method m) {
		return 
			m.getParameterTypes().length == 0 && 
			m.getName().startsWith("test") && 
			m.getReturnType().equals(Void.TYPE);
	}

	/**
	 * @return the fName
	 */
	public String getName() {
		return fName;
	}

	/**
	 * @param fName the fName to set
	 */
	public void setName(String fName) {
		this.fName = fName;
	}
	
}
