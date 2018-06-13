/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.git.build.hjplugin.scm;

/**
 * Represents a change set
 */
public class ChangeSetData {
	
	public final String id;
	public final String comment;
	public final String userName;
	
	public ChangeSetData(String id, String comment, String userName) {
		this.id = id;
		this.comment = comment;
		this.userName = userName;
	}
	
	/**
	 * Return the unique identifier for this change set
	 * 
	 * @return a string that represents this change set 
	 */
	public String getChangeSetId() {
		// Concatenate all the strings
		StringBuffer bf = new StringBuffer(); 
		bf.append(this.id).append(this.comment).append(this.userName);
		return bf.toString();
	}
}
