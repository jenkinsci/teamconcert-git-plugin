/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.git.build.hjplugin.scm;

public class ChangeSetData {
	
	public String id;
	public String comment;
	public String userName;
	
	public ChangeSetData(String id, String comment, String userName) {
		this.id = id;
		this.comment = comment;
		this.userName = userName;
	}
}
