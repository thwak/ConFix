package com.github.thwak.confix.patch;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.github.thwak.confix.pool.Context;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

public class FixLocationIdentifier {

	protected PatchStrategy pStrategy;
	protected ConcretizationStrategy iStrategy;

	public FixLocationIdentifier(PatchStrategy pStrategy, ConcretizationStrategy iStrategy){
		this.pStrategy = pStrategy;
		this.iStrategy = iStrategy;
	}

	public void findLocations(String className, Node root, List<TargetLocation> fixLocs){
		iStrategy.collectMaterials(root);
		TreeUtils.computeTypeHash(root);
		List<Node> nodes = TreeUtils.traverse(root);
		for(Node n : nodes){
			if (pStrategy.inRange(className, n)) {
				StructuralPropertyDescriptor desc = n.astNode.getLocationInParent();
				Context c = pStrategy.collector().getContext(n);
				TargetLocation loc = new TargetLocation(className, TargetLocation.DEFAULT, c, n, desc);
				if(iStrategy.resolveType(loc) && pStrategy.isTarget(loc)){
					fixLocs.add(loc);
				}
				//Insert Before/After only if the parent can accept.
				if(desc != null
						&& (desc.isChildListProperty()
								|| desc.getId().equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId())
								|| desc.getId().equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId()))) {
					c = pStrategy.collector().getLeftContext(n);
					loc = new TargetLocation(className, TargetLocation.INSERT_BEFORE, c, n, desc);
					if(iStrategy.resolveType(loc) && pStrategy.isTarget(loc)) {
						iStrategy.updateLocInfo(loc);
						fixLocs.add(loc);
					}
					c = pStrategy.collector().getRightContext(n);
					loc = new TargetLocation(className, TargetLocation.INSERT_AFTER, c, n, desc);
					if(iStrategy.resolveType(loc) && pStrategy.isTarget(loc)) {
						iStrategy.updateLocInfo(loc);
						fixLocs.add(loc);
					}
				} else if(desc != null
						&& desc.getId().equals(IfStatement.THEN_STATEMENT_PROPERTY.getId())
						&& n.getRight() == null) {
					//Add else to the if statement.
					c = pStrategy.collector().getRightContext(n);
					loc = new TargetLocation(className, TargetLocation.INSERT_AFTER, c, n, desc);
					if(iStrategy.resolveType(loc) && pStrategy.isTarget(loc)) {
						iStrategy.updateLocInfo(loc);
						fixLocs.add(loc);
					}
				} else if(desc != null
						&& desc.getId().equals(MethodInvocation.NAME_PROPERTY.getId())
						&& n.getLeft() == null) {
					//Add expression to method calls.
					c = pStrategy.collector().getLeftContext(n);
					loc = new TargetLocation(className, TargetLocation.INSERT_BEFORE, c, n, desc);
					if(iStrategy.resolveType(loc) && pStrategy.isTarget(loc)) {
						iStrategy.updateLocInfo(loc);
						fixLocs.add(loc);
					}
				}
				//Insert Under if there is no child to insert before/after.
				if(n.children.size() == 0
						&& n.type != ASTNode.SIMPLE_NAME
						&& n.type != ASTNode.SIMPLE_TYPE
						&& n.type != ASTNode.QUALIFIED_NAME
						&& n.type != ASTNode.QUALIFIED_TYPE) {
					List<StructuralPropertyDescriptor> spdList = null;
					if(n.astNode != null) {
						spdList = n.astNode.structuralPropertiesForType();
					} else {
						AST ast = AST.newAST(AST.JLS8);
						ASTNode astNode = ast.createInstance(n.type);
						spdList = astNode.structuralPropertiesForType();
					}
					for(StructuralPropertyDescriptor spd : spdList) {
						if(!spd.isSimpleProperty()) {
							c = pStrategy.collector.getUnderContext(n, spd);
							loc = new TargetLocation(className, TargetLocation.INSERT_UNDER, c, n, spd);
							if(iStrategy.resolveType(loc)) {
								if(pStrategy.isTarget(loc)){
									iStrategy.updateLocInfo(loc);
									fixLocs.add(loc);
								}
							}
						}
					}
				}
			}
		}
	}
}
