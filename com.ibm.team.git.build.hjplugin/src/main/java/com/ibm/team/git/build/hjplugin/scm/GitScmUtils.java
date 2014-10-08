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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.ibm.team.git.build.hjplugin.RTCUtils;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import com.ibm.team.git.build.hjplugin.Messages;

public class GitScmUtils {
	private static final Logger LOGGER = Logger.getLogger(GitScmUtils.class.getName());
	
	private static String GIT_SCM_CLASS_NAME = "hudson.plugins.git.GitSCM";
	public static boolean isGitScmBuild(AbstractBuild<?, ?> build) {
		try {
			return GIT_SCM_CLASS_NAME.equals(build.getParent().getScm().getClass().getName());
		} catch (Exception e) {
			//do nothing 
		}
		
		return false;
	}
	
	public static List<ChangeSetData> getIncludedCommits(AbstractBuild<?,?> build, PrintStream logger) {
		LOGGER.finer("In Get Included Commits"); 
		List<ChangeSetData> csList = new ArrayList<ChangeSetData>();
    	try {
    		for(Object changeSet : build.getChangeSet().getItems()){
    			ChangeLogSet.Entry csEntry = (ChangeLogSet.Entry) changeSet;
    			ChangeSetData csData = new ChangeSetData(csEntry.getCommitId(), csEntry.getMsg(), csEntry.getAuthor().getId());
    			csList.add(csData);
    		}
    	} catch(Exception e) {
    		RTCUtils.LogMessage(logger, Messages.Error_CalculatingChanges());
    		RTCUtils.LogMessage(logger, e.getMessage());
    		csList = null;
    	}	
    	return csList;
	}

}
