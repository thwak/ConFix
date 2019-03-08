package com.github.thwak.confix.patch;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.CodeFragment;
import com.github.thwak.confix.pool.CodePool;
import com.github.thwak.confix.pool.TokenVector;
import com.github.thwak.confix.tree.CodeVisitor;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.tree.TreeUtils;

public class HashMatchStrategy extends NeighborFirstStrategy {

	public static final String C_METHOD_HASH = "hash-local";
	public static final String C_METHOD_HASH_PKG = "hash-package";

	protected CodePool codePool;
	protected List<CodeFragment> fragments;
	protected TokenVector v;
	protected int currIndex;
	protected CodePool pkgCodePool;
	protected String srcDir;

	public HashMatchStrategy(String srcDir, Random r) {
		this(srcDir, new CodePool(), new CodePool(), r);
	}

	public HashMatchStrategy(String srcDir, CodePool codePool, Random r) {
		this(srcDir,codePool, new CodePool(), r);
	}

	public HashMatchStrategy(String srcDir, CodePool codePool, CodePool pkgCodePool, Random r) {
		super(r);
		this.codePool = codePool;
		this.fragments = new ArrayList<>();
		this.v = null;
		this.currIndex = 0;
		this.pkgCodePool = pkgCodePool;
		this.srcDir = srcDir;
	}

	@Override
	public boolean instCheck(Change c, TargetLocation loc) {
		return super.instCheck(c, loc);
	}

	@Override
	public ASTNode instantiate(Change c, TargetLocation loc, PatchInfo info) {
		if(currChange == null || c != currChange) {
			currChange = c;
			int reqCount = checkRequirements(c);
			assignSingleNode = reqCount == 1;
			hasReqs = reqCount > 0;
			fragments.clear();
			tried.clear();
			currIndex = 0;
		}
		if(currLoc == null || loc != currLoc) {
			currLoc = loc;
			List<Node> nodes = getStatementNodes(loc);
			nodes.add(loc.node);
			v = new TokenVector(nodes);
			fragments.clear();
			tried.clear();
			currIndex = 0;
		}
		if(!hasReqs) {
			info.cMethods.add(C_METHOD_HASH);
			Node copied = TreeUtils.deepCopy(c.type.equals(Change.INSERT) ? c.node : c.location);
			return TreeUtils.generateNode(copied, loc.node.astNode.getAST());
		}
		Node target = null;
		switch(c.type) {
		case Change.UPDATE:
			return instantiateUpdate(c, loc, info);
		case Change.INSERT:
			if(c.node.type == ASTNode.SIMPLE_NAME
			|| c.node.type == ASTNode.QUALIFIED_NAME
			|| c.node.type == ASTNode.SIMPLE_TYPE
			|| c.node.type == ASTNode.QUALIFIED_TYPE) {
				return instantiateInsertOne(c, loc, info);
			}
			target = c.node;
			break;
		case Change.REPLACE:
			target = c.location;
			break;
		}
		String inst = C_METHOD_HASH;
		if(fragments.size() == 0) {
			fragments.addAll(codePool.getCodeFragments(target, v, r));
			if(fragments.size() == 0) {
				inst = C_METHOD_HASH_PKG;
				fragments.addAll(pkgCodePool.getCodeFragments(target, v, r));
			}
		}
		if(currIndex >= fragments.size()) {
			return super.instantiate(c, loc, info);
		}
		CodeFragment cf = fragments.get(currIndex++);
		Node copied = TreeUtils.deepCopy(target);
		List<Node> nodes = TreeUtils.traverse(copied);
		List<Node> cfNodes = TreeUtils.traverse(cf.getNode());
		if(nodes.size() != cfNodes.size())
			return super.instantiate(c, loc, info);
		for(int i=0; i<nodes.size(); i++){
			Node node = nodes.get(i);
			Node cfNode = cfNodes.get(i);
			node.value = cfNode.value;
			if(assignSingleNode && node.normalized && cfNode.value != null)
				tried.add(cfNode.value);
		}
		info.cMethods.add(inst);
		AST ast = loc.node.astNode.getAST();
		ASTNode astNode = null;
		astNode = TreeUtils.generateNode(copied, ast);
		return astNode;
	}

	protected ASTNode instantiateInsertOne(Change c, TargetLocation loc, PatchInfo info) {
		Node copied = null;
		Node diff = c.node.copy();
		if(loc.kind == TargetLocation.INSERT_UNDER) {
			copied = TreeUtils.deepCopy(loc.node);
			copied.children.add(diff);
		} else {
			copied = TreeUtils.deepCopy(loc.node.parent);
			int pos = loc.node.posInParent;
			if(loc.kind == TargetLocation.INSERT_BEFORE) {
				copied.children.add(pos, diff);
			} else if(loc.kind == TargetLocation.INSERT_AFTER) {
				if(pos == copied.children.size()-1)
					copied.children.add(diff);
				else
					copied.children.add(pos+1, diff);
			}
		}
		if(fragments.size() == 0) {
			if(copied != null) {
				//Find diff by one code fragments.
				Map<CodeFragment, Integer> cfMap = codePool.getCode(copied);
				if(cfMap != null) {
					TreeMap<Integer, List<CodeFragment>> candidates = new TreeMap<>();
					for(CodeFragment cf : cfMap.keySet()) {
						Node candidate = getDiffByOne(copied, cf.getNode(), diff);
						int freq = cfMap.get(cf);
						if(candidate != null) {
							if(!candidates.containsKey(freq))
								candidates.put(freq, new ArrayList<CodeFragment>());
							candidates.get(freq).add(new CodeFragment(candidate, ""));
						}
					}
					for(Integer freq : candidates.descendingKeySet()) {
						fragments.addAll(candidates.get(freq));
					}
				}
			}
		}
		if(currIndex >= fragments.size())
			return super.instantiate(c, loc, info);
		info.cMethods.add(C_METHOD_HASH);
		CodeFragment cf = fragments.get(currIndex++);
		copied = TreeUtils.deepCopy(loc.node);
		copied.value = cf.getNode().value;
		tried.add(copied.value);
		AST ast = loc.node.astNode.getAST();
		ASTNode astNode = null;
		astNode = TreeUtils.generateNode(copied, ast);
		return astNode;
	}

	protected ASTNode instantiateUpdate(Change c, TargetLocation loc, PatchInfo info) {
		if(fragments.size() == 0) {
			Node parent = loc.node.parent;
			if(parent != null) {
				//Find diff by one code fragments.
				Map<CodeFragment, Integer> cfMap = codePool.getCode(parent);
				if(cfMap != null) {
					TreeMap<Integer, List<CodeFragment>> candidates = new TreeMap<>();
					for(CodeFragment cf : cfMap.keySet()) {
						Node candidate = getDiffByOne(parent, cf.getNode(), loc.node);
						int freq = cfMap.get(cf);
						if(candidate != null) {
							if(!candidates.containsKey(freq))
								candidates.put(freq, new ArrayList<CodeFragment>());
							candidates.get(freq).add(new CodeFragment(candidate, ""));
						}
					}
					for(Integer freq : candidates.descendingKeySet()) {
						fragments.addAll(candidates.get(freq));
					}
				}
			}
		}
		if(currIndex >= fragments.size())
			return super.instantiate(c, loc, info);
		info.cMethods.add(C_METHOD_HASH);
		CodeFragment cf = fragments.get(currIndex++);
		Node copied = TreeUtils.deepCopy(loc.node);
		copied.value = cf.getNode().value;
		tried.add(copied.value);
		AST ast = loc.node.astNode.getAST();
		ASTNode astNode = null;
		astNode = TreeUtils.generateNode(copied, ast);
		return astNode;
	}

	private Node getDiffByOne(Node ref, Node n, Node diff) {
		List<Node> refNodes = TreeUtils.traverse(ref);
		List<Node> nodes = TreeUtils.traverse(n);
		Node ret = null;
		if(refNodes.size() == nodes.size()) {
			for(int i=0; i<nodes.size(); i++) {
				ref = refNodes.get(i);
				n = nodes.get(i);
				if(ref.equals(diff)) {
					if(!diff.label.equals(n.label)) {
						ret = n;
					} else {
						return null;
					}
				} else if(!ref.label.equals(n.label)) {
					return null;
				}
			}
		}
		return ret;
	}

	@Override
	public void collectGlobalMaterials(Parser parser, String className, Node root, CompilationUnit cu) {
		super.collectGlobalMaterials(parser, className, root, cu);
		if(!StrategyFactory.codePools.containsKey(className)) {
			codePool.addClass(className, root);
			StrategyFactory.codePools.put(className, codePool);
		}
		String packageName = cu.getPackage().getName().getFullyQualifiedName();
		if(!StrategyFactory.pkgCodePools.containsKey(packageName)) {
			String pkgPath = srcDir + File.separator + packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
			File pkgDir = new File(pkgPath);
			File[] javaFiles = pkgDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".java");
				}
			});
			String fileName = className.substring(className.lastIndexOf('.')) + ".java";
			for(File f : javaFiles) {
				if(f.getName().equals(fileName))
					continue;
				CompilationUnit tmp = parser.parse(f);
				Node n = new Node("root");
				CodeVisitor visitor = new CodeVisitor(n);
				tmp.accept(visitor);
				pkgCodePool.addClass(f.getName().replace(".java", ""), n);
			}
			StrategyFactory.pkgCodePools.put(packageName, pkgCodePool);
		}
	}

	public CodePool getCodePool() {
		return codePool;
	}

	public CodePool getPackageCodePool() {
		return pkgCodePool;
	}
}
