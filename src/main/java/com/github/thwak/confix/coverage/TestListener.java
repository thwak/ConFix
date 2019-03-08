package com.github.thwak.confix.coverage;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class TestListener extends RunListener implements Serializable {

	private static final long serialVersionUID = 8742221274123722911L;
	public List<String> passedTests;
	public List<String> failedTests;
	
	public TestListener(){
		passedTests = new ArrayList<String>();
		failedTests = new ArrayList<String>();
	}
	
	public void update(TestListener listener){
		passedTests.addAll(listener.passedTests);
		failedTests.addAll(listener.failedTests);
	}
	
	public void clear(){
		passedTests.clear();
		failedTests.clear();
	}
	
	public void store(){
		String fileName = "result";
		FileOutputStream fos = null;
		ObjectOutputStream os = null;
		
		try {
			fos = new FileOutputStream(fileName);
			os = new ObjectOutputStream(fos);
			os.writeObject(this);
			os.flush();			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fos != null) fos.close();
				if(os != null) os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void testFinished(Description description) throws Exception {
		super.testFinished(description);
		String name = description.getClassName() + "#" + description.getMethodName();
		if(!failedTests.contains(name)){
			passedTests.add(name);
		}
	}

	@Override
	public void testFailure(Failure failure) throws Exception {
		super.testFailure(failure);
		String name = failure.getDescription().getClassName() + "#" + failure.getDescription().getMethodName();
		failedTests.add(name);
	}
	
}
