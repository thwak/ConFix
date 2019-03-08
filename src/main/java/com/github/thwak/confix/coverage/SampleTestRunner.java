package com.github.thwak.confix.coverage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.JUnitCore;

import com.github.thwak.confix.junit.TestRunnerBuilder;

public class SampleTestRunner {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length < 2){
			System.out.println("Usage:SampleTestRunner {TestClass} {SampleFile} [SampleOnly]");
			return;
		}

		try {
			String testClassName = args[0];
			String sampleFileName = args[1];
			boolean sampleOnly = args.length == 3 ? Boolean.parseBoolean(args[2]) : true;
			List<String> sampledTests = readSampleFile(sampleFileName);
			JUnitCore core = new JUnitCore();
			TestListener listener = new TestListener();
			core.addListener(listener);
			TestRunnerBuilder builder = new TestRunnerBuilder(sampledTests, sampleOnly);
			core.run(builder.runnerForClass(Class.forName(testClassName)));
			listener.store();

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}

	}

	private static List<String> readSampleFile(String sampleFileName) throws FileNotFoundException, IOException {
		List<String> sampledTests = new ArrayList<String>();
		File sampleFile = new File(sampleFileName);
		if (sampleFile.exists()) {
			BufferedReader br = new BufferedReader(new FileReader(sampleFile));
			String testName = null;
			while ((testName = br.readLine()) != null) {
				sampledTests.add(testName.trim());
			}
			br.close();
		}
		return sampledTests;
	}

}
