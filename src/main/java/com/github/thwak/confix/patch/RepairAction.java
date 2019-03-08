package com.github.thwak.confix.patch;

import com.github.thwak.confix.pool.Change;

public class RepairAction {
	public TargetLocation loc;
	public String locCode;
	public String newCode;
	public String type;
	public Change change;

	public RepairAction(String type, TargetLocation loc, String locCode, String newCode, Change change){
		this.type = type;
		this.loc = loc;
		this.locCode = locCode;
		this.newCode = newCode;
		this.change = change;
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer();
		switch(type){
		case Change.INSERT:
			sb.append(type);
			sb.append("\n");
			sb.append(newCode);
			sb.append("\n");
			sb.append("at ");
			sb.append(loc);
			sb.append("\n");
			break;
		case Change.DELETE:
			sb.append(type);
			sb.append("\n");
			sb.append(locCode);
			sb.append("\n");
			sb.append("at line ");
			sb.append(loc.node.startLine);
			sb.append("\n");
			break;
		case Change.UPDATE:
			sb.append(type);
			sb.append("\n");
			sb.append(locCode);
			sb.append("\n");
			sb.append("to\n");
			sb.append(newCode);
			sb.append("\n");
			sb.append("at line ");
			sb.append(loc.node.startLine);
			sb.append("\n");
			break;
		case Change.REPLACE:
			sb.append(type);
			sb.append("\n");
			sb.append(locCode);
			sb.append("\n");
			sb.append("with\n");
			sb.append(newCode);
			sb.append("\n");
			sb.append("at line ");
			sb.append(loc.node.startLine);
			sb.append("\n");
			break;
		}
		sb.append("Applied Change\n");
		sb.append(change.toString());
		return sb.toString();
	}
}
