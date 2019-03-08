package com.github.thwak.confix.tree;

import java.io.Serializable;
import java.lang.reflect.Field;

import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.jdt.core.dom.SimplePropertyDescriptor;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;

public class StructuralPropertyDesc implements Serializable {

	private static final long serialVersionUID = 6005436050193012761L;
	public static final int SIMPLE_PROPERTY = 0;
	public static final int CHILD_PROPERTY = 1;
	public static final int CHILDLIST_PROPERTY = 2;
	public String id;
	public int type;
	public String className;

	public StructuralPropertyDesc(String id, int type, String className) {
		super();
		this.id = id;
		this.type = type;
		this.className = className;
	}

	public StructuralPropertyDesc(StructuralPropertyDescriptor spd, Class<? extends ASTNode> nodeClass){
		this.id = spd.getId();
		this.className = nodeClass.getName();
		if(spd.isSimpleProperty()){
			type = SIMPLE_PROPERTY;
		}else if(spd.isChildProperty()){
			type = CHILD_PROPERTY;
		}else if(spd.isChildListProperty()){
			type = CHILDLIST_PROPERTY;
		}else{
			type = -1;
		}
	}

	public StructuralPropertyDescriptor getDescriptor(){
		try {
			Class nodeClass = Class.forName(className);
			Field[] fields = nodeClass.getFields();
			for(Field field : fields){
				try {
					//To avoid deprecated MODIFIERS_PROPERTY.
					if(id.equals("modifiers")){
						if(field.getType().getSimpleName().equals("ChildListPropertyDescriptor")){
							ChildListPropertyDescriptor descriptor = (ChildListPropertyDescriptor) field.get(new Object());
							if (descriptor.getId().equals(id))
								return descriptor;
						}
					}else if (field.getType().getSimpleName().equals("SimplePropertyDescriptor")) {
						SimplePropertyDescriptor descriptor = (SimplePropertyDescriptor) field.get(new Object());
						if (descriptor.getId().equals(id))
							return descriptor;
					} else if (field.getType().getSimpleName().equals("ChildPropertyDescriptor")) {
						ChildPropertyDescriptor descriptor = (ChildPropertyDescriptor) field.get(new Object());
						if (descriptor.getId().equals(id))
							return descriptor;
					} else if (field.getType().getSimpleName().equals("ChildListPropertyDescriptor")) {
						ChildListPropertyDescriptor descriptor = (ChildListPropertyDescriptor) field.get(new Object());
						if (descriptor.getId().equals(id))
							return descriptor;
					}
				} catch (IllegalAccessException e) {

				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean equals(Object obj){
		if(obj instanceof StructuralPropertyDesc){
			StructuralPropertyDesc desc = (StructuralPropertyDesc)obj;
			return id.equals(desc.id) && className.equals(desc.className);
		}else if(obj instanceof StructuralPropertyDescriptor) {
			StructuralPropertyDescriptor spd = (StructuralPropertyDescriptor)obj;
			return id.equals(spd.getId()) && className.equals(spd.getNodeClass().getName());
		}
		return false;
	}

	@Override
	public String toString(){
		return className + "#" + id;
	}
}
