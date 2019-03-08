package com.github.thwak.confix.pool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.thwak.confix.util.IOUtils;
import com.github.thwak.confix.util.IndexMap;

public class ChangePool {

	private static final String CONTEXT_FILE_NAME = "context.obj";
	private static final String IDENTIFIER_FILE_NAME = "identifier.obj";
	private static final String CHANGE_DIR_NAME = "changes";
	private static final String HASH_ID_FILE_NAME = "hash_id_map.obj";
	public String poolName;
	private File poolDir;
	public Map<Context, ContextInfo> contexts;
	public IndexMap<Change> changes;
	public IndexMap<String> hashIdMap;
	public int maxLoadCount = 1000;
	private ContextIdentifier identifier;

	public ChangePool(){
		this(new File("pool"), new ContextIdentifier());
	}

	public ChangePool(File poolDir){
		this(poolDir, new ContextIdentifier());
	}

	public ChangePool(ContextIdentifier identifier){
		this(new File("pool"), identifier);
	}

	public ChangePool(File poolDir, ContextIdentifier identifier){
		this.poolDir = poolDir;
		this.identifier = identifier;
		contexts = new HashMap<>();
		changes = new IndexMap<>();
		hashIdMap = new IndexMap<>();
	}

	public void clearAll(){
		contexts.clear();
		changes.clear();
		hashIdMap.clear();
	}

	public void clear() {
		changes.clear();
	}

	public void add(Context context, Change change){
		add(context, change, 1);
	}

	public void add(Context context, Change change, int freq) {
		if(!hashIdMap.contains(change.hash)) {
			store(change);
		}
		int changeId = hashIdMap.add(change.hash);
		ContextInfo info = null;
		if(!contexts.containsKey(context)) {
			info = new ContextInfo();
			contexts.put(context, info);
		} else {
			info = contexts.get(context);
		}
		info.addChange(changeId, change, freq);
	}

	private void store(Change c) {
		File changeDir = new File(poolDir.getAbsolutePath() + File.separator + CHANGE_DIR_NAME);
		File fChange = new File(changeDir + File.separator + c.hash + ".obj");
		IOUtils.storeObject(fChange, c);
	}

	public List<Integer> getChangeIds(Context c){
		if(contexts.containsKey(c)) {
			return new ArrayList<>(contexts.get(c).getChanges());
		}
		return Collections.emptyList();
	}

	public Iterator<Integer> changeIterator(Context c) {
		if(contexts.containsKey(c)) {
			return contexts.get(c).getChanges().iterator();
		}
		return Collections.emptyIterator();
	}

	public int getFrequency(Context c){
		if(contexts.containsKey(c)) {
			return contexts.get(c).freq;
		} else {
			return 0;
		}
	}

	public int getFrequency(Context c, int changeId) {
		return contexts.containsKey(c) ? contexts.get(c).getChangeFreq(changeId) : 0;
	}

	public int getFrequency(Context context, Change change) {
		int id = hashIdMap.getIndex(change.hash);
		ContextInfo info = contexts.get(context);
		return info != null ? info.getChangeFreq(id) : 0;
	}

	public File getPoolDir(){
		return poolDir;
	}

	public void setPoolDir(File poolDir){
		this.poolDir = poolDir;
		String changeDirPath = poolDir.getAbsolutePath() + File.separator + CHANGE_DIR_NAME;
		File changeDir = new File(changeDirPath);
		if(!changeDir.exists() && !changeDir.mkdirs()){
			System.out.println("Can't set the pool directory to - "+poolDir.getAbsolutePath());
		}
	}

	public void loadFrom(File poolDir) {
		if(poolDir.isFile()){
			System.out.println("Can't load from file - "+poolDir.getAbsolutePath());
			System.out.println("You must provide a pool directory.");
			return;
		}else if(poolDir.exists()){
			//Load contexts only.
			this.poolDir = poolDir;
			String path = poolDir.getAbsolutePath();
			File contextFile = new File(path + File.separator + CONTEXT_FILE_NAME);
			if(contextFile.exists()){
				contexts = (Map<Context, ContextInfo>)IOUtils.readObject(contextFile);
			}
			File hashIdFile = new File(path + File.separator + HASH_ID_FILE_NAME);
			if(hashIdFile.exists()){
				hashIdMap = (IndexMap<String>)IOUtils.readObject(hashIdFile);
			}
			File identifierFile = new File(path + File.separator + IDENTIFIER_FILE_NAME);
			if(identifierFile.exists()){
				identifier = (ContextIdentifier)IOUtils.readObject(identifierFile);
			}
		}
	}

	public void load() {
		loadFrom(poolDir);
	}

	public void store(){
		storeTo(poolDir, true);
	}

	public void store(boolean saveAll){
		storeTo(poolDir, saveAll);
	}

	public void storeTo(File poolDir){
		storeTo(poolDir, false);
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
			File contextFile = new File(path + File.separator + CONTEXT_FILE_NAME);
			IOUtils.storeObject(contextFile, contexts);
			File hashIdFile = new File(path + File.separator + HASH_ID_FILE_NAME);
			IOUtils.storeObject(hashIdFile, hashIdMap);
			File identifierFile = new File(path + File.separator + IDENTIFIER_FILE_NAME);
			IOUtils.storeObject(identifierFile, identifier);
			if (saveAll) {
				String changeDirPath = path + File.separator + CHANGE_DIR_NAME;
				File changeDir = new File(changeDirPath);
				if(!changeDir.exists())
					changeDir.mkdir();
				for (Integer id : changes.indexSet()) {
					Change c = changes.get(id);
					File fChange = new File(changeDir + File.separator + c.hash + ".obj");
					IOUtils.storeObject(fChange, c);
				}
			}
		}
	}

	public Set<Context> getContexts() {
		return contexts.keySet();
	}

	public ContextIdentifier getIdentifier() {
		return identifier;
	}

	public void setIdentifier(ContextIdentifier identifier){
		this.identifier = identifier;
	}

	public void storeIdentifier() {
		File identifierFile = new File(poolDir.getAbsolutePath() + File.separator + IDENTIFIER_FILE_NAME);
		IOUtils.storeObject(identifierFile, identifier);
	}

	public void loadIdentifier() {
		File identifierFile = new File(poolDir.getAbsolutePath() + File.separator + IDENTIFIER_FILE_NAME);
		if(identifierFile.exists())
			identifier = (ContextIdentifier)IOUtils.readObject(identifierFile);
	}

	public Change getChange(int id) {
		loadChange(id);
		return changes.get(id);
	}

	public void loadChange(int id) {
		if(!changes.hasIndex(id) && hashIdMap.hasIndex(id)) {
			if(changes.size() >= maxLoadCount)
				changes.clear();
			//#TODO: Store/Load changes with one big RandomAcessFile.
			String changeDirPath = poolDir.getAbsolutePath() + File.separator + CHANGE_DIR_NAME;
			File changeDir = new File(changeDirPath);
			File fChange = new File(changeDir + File.separator + hashIdMap.get(id) + ".obj");
			Change c = (Change)IOUtils.readObject(fChange);
			changes.put(id, c);
		}
	}

	public int getId(Change c) {
		return hashIdMap.getIndex(c.hash);
	}

	public int loadCount() {
		return changes.size();
	}

	public int getChangeCount() {
		return hashIdMap.size();
	}

	public int getChangeCount(Context c) {
		ContextInfo info = contexts.get(c);
		if(info != null) {
			return info.changes.size();
		}
		return 0;
	}
}