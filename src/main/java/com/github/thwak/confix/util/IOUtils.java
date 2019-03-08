package com.github.thwak.confix.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;

public class IOUtils {

	public static String readFile(File f){
		StringBuffer sb = new StringBuffer();
		BufferedReader br = null;
		FileReader fr = null;
		try {
			fr = new FileReader(f);
			br = new BufferedReader(fr);
			char[] cbuf = new char[500];
			int len = 0;
			while((len=br.read(cbuf))>-1){
				sb.append(cbuf, 0, len);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fr != null)	fr.close();
				if(br != null)	br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

	public static String readFile(String filePath){
		return readFile(new File(filePath));
	}

	public static void storeDataToFile(Collection<? extends Object> data, String fileName) throws IOException {
		File outputFile = new File(fileName);
		FileOutputStream fos = new FileOutputStream(outputFile);
		PrintWriter pw = new PrintWriter(fos);
		for(Object obj : data){
			pw.println(obj);
		}
		pw.flush();
		pw.close();
	}

	public static void storeContent(String filePath, String content){
		storeContent(new File(filePath), content, false);
	}

	public static void storeContent(String filePath, String content, boolean append){
		storeContent(new File(filePath), content, append);
	}

	public static void storeContent(File f, String content, boolean append){
		FileOutputStream fos = null;
		PrintWriter pw = null;
		try {
			fos = new FileOutputStream(f, append);
			pw = new PrintWriter(fos);
			pw.print(content);
			pw.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fos != null)
					fos.close();
				if(pw != null)
					pw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public static void storeObject(String filePath, Object obj){
		storeObject(new File(filePath), obj);
	}

	public static void storeObject(File f, Object obj){
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = new FileOutputStream(f);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(obj);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fos != null)
					fos.close();
				if(oos != null)
					oos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static Object readObject(String filePath){
		return readObject(new File(filePath));
	}

	public static Object readObject(File f) {
		FileInputStream fis = null;
		ObjectInputStream is = null;
		Object obj = null;
		try {
			fis = new FileInputStream(f);
			is = new ObjectInputStream(fis);
			obj = is.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(fis != null)
					fis.close();
				if(is != null)
					is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return obj;
	}

	public static void delete(File file){
		try {
			Files.walkFileTree(file.toPath(), new FileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.TERMINATE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					if(exc != null)
						return FileVisitResult.TERMINATE;
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
