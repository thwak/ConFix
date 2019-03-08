package com.github.thwak.confix.coverage;

import org.junit.runner.JUnitCore;

public class SingleTestClassRunner {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length < 1){
			System.out.println("Usage:SingleTestClassRunner {TestClass}");
			return;
		}
		int exitValue = 0;

		try {
			JUnitCore.runClasses(Class.forName(args[0]));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			exitValue = 1;
		}

		System.exit(exitValue);
	}

}
