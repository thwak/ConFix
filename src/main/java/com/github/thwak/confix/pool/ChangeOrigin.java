package com.github.thwak.confix.pool;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import com.github.thwak.confix.util.IOUtils;

import java.util.Set;

public class ChangeOrigin {
	private static final String ORIGIN_FILE_NAME = "origins.obj";
	private static final String ORIGIN_DIR_NAME = "origins";
	public Map<Change, Map<String, Integer>> origins;
	private File poolDir;

	public ChangeOrigin() {
		this(new File("origin"));
	}

	public ChangeOrigin(File poolDir) {
		this.poolDir = poolDir;
		origins = new HashMap<>();
	}

	public File getPoolDir(){
		return poolDir;
	}

	public void setPoolDir(File poolDir){
		this.poolDir = poolDir;
		String originDirPath = poolDir.getAbsolutePath() + File.separator + ORIGIN_DIR_NAME;
		File originDir = new File(originDirPath);
		if(!originDir.exists() && !originDir.mkdirs()){
			System.out.println("Can't set the origin directory to - "+poolDir.getAbsolutePath());
		}
	}

	public void add(Change c) {
		if(origins.get(c) == null) {
			loadOrigin(c);
		}
		Map<String, Integer> originMap = origins.get(c);
		originMap.put(c.id, originMap.containsKey(c.id) ? originMap.get(c.id)+1 : 1);
	}

	public void loadFrom(File poolDir) {
		File originFile = new File(poolDir + File.separator + ORIGIN_FILE_NAME);
		if(originFile.exists()){
			Set<Change> changes = (Set<Change>)IOUtils.readObject(originFile);
			for(Change c : changes){
				origins.put(c, null);
			}
		}
	}

	public void storeOrigin(Change c) {
		String originDirPath = poolDir.getAbsolutePath() + File.separator + ORIGIN_DIR_NAME;
		File fChange = new File(originDirPath + File.separator + c.hash + ".obj");
		IOUtils.storeObject(fChange, origins.get(c));
	}

	public void loadOrigin(Change c) {
		if(origins.get(c) == null)
			origins.put(c, new HashMap<String, Integer>());
		if(poolDir != null){
			String originDirPath = poolDir.getAbsolutePath() + File.separator + ORIGIN_DIR_NAME;
			File fChange = new File(originDirPath + File.separator + c.hash + ".obj");
			if (fChange.exists()) {
				Map<String, Integer> origins = (Map<String, Integer>) IOUtils.readObject(fChange);
				if (origins != null) {
					this.origins.get(c).putAll(origins);
				}
			}
		}
	}

	public void storeOrigins(){
		File originFile = new File(poolDir.getAbsolutePath() + File.separator + ORIGIN_FILE_NAME);
		IOUtils.storeObject(originFile, origins);
	}

	public void loadOrigins(){
		File originFile = new File(poolDir.getAbsolutePath() + File.separator + ORIGIN_FILE_NAME);
		if(originFile.exists())
			origins = (Map<Change, Map<String, Integer>>)IOUtils.readObject(originFile);
	}

	public void update() {
		File originFile = new File(poolDir.getAbsolutePath() + File.separator + ORIGIN_FILE_NAME);
		Set<Change> changes = originFile.exists() ? (Set<Change>)IOUtils.readObject(originFile) : new HashSet<Change>();
		changes.addAll(origins.keySet());
		IOUtils.storeObject(originFile, changes);
		//Update origins for the current changes.
		for (Change c : origins.keySet()) {
			storeOrigin(c);
		}
	}

	public void storeTo(File poolDir, boolean saveAll){
		if(poolDir.isFile()){
			System.out.println("Can't store to file - "+poolDir.getAbsolutePath());
			System.out.println("You must provide a pool directory.");
			return;
		}
		if(poolDir.exists() || poolDir.mkdirs()){
			this.poolDir = poolDir;
			String path = poolDir.getAbsolutePath();
			File originFile = new File(path + File.separator + ORIGIN_FILE_NAME);
			IOUtils.storeObject(originFile, new HashSet<>(origins.keySet()));
			if(saveAll) {
				String originDirPath = path + File.separator + ORIGIN_DIR_NAME;
				File originDir = new File(originDirPath);
				originDir.mkdir();
				for (Change c : origins.keySet()) {
					File fChange = new File(originDir + File.separator + c.hash + ".obj");
					IOUtils.storeObject(fChange, origins.get(c));
				}
			}
		}
	}

	public void merge(ChangeOrigin origin) {
		for(Change c : origin.origins.keySet()) {
			merge(c, origin.origins.get(c));
		}
	}

	public void merge(Change c, Map<String, Integer> origins) {
		if(this.origins.get(c) != null){
			Map<String, Integer> map = this.origins.get(c);
			for(Entry<String, Integer> e : origins.entrySet()){
				String key = e.getKey();
				Integer val = e.getValue();
				map.put(key, map.containsKey(key) ? map.get(key)+val : val);
			}
		}else{
			this.origins.put(c, new HashMap<>(origins));
		}
	}

	public void clear(){
		origins.clear();
	}
}
