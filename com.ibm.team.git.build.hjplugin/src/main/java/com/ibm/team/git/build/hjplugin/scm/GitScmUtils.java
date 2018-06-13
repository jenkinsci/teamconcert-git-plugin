/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation and others.
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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.git.build.hjplugin.Messages;
import com.ibm.team.git.build.hjplugin.RTCUtils;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

public class GitScmUtils {
	private static final Logger LOGGER = Logger.getLogger(GitScmUtils.class.getName());
	
	private static String GIT_SCM_CLASS_NAME = "hudson.plugins.git.GitSCM"; //$NON-NLS-1$
	public static String GIT_CHANGELOGSET_ENTRY_CLASS_NAME = "hudson.plugins.git.GitChangeSet"; //$NON-NLS-1$
	
	public static boolean isGitScmBuild(Run<?, ?> run, PrintStream logger) {
		// Check whether it has change sets which are from Git
	    if (run instanceof AbstractBuild) {
	    	AbstractBuild<?,?> build = (AbstractBuild<?,?>)run;
	    	return GIT_SCM_CLASS_NAME.equals(build.getParent().getScm().getClass().getName());
	    } else {
    		List<ChangeSetData> changeLogData = getIncludedCommits(run, logger);
    		if (changeLogData.size() > 0) {
    			return true;
    		}
    		return false;
	    }
	}

	public static List<ChangeSetData> getIncludedCommits(Run<?,?> run, PrintStream logger) {
		LOGGER.finest("GitScmUtils:getIncludedCommits Begin"); //$NON-NLS-1$
		List<ChangeSetData> changeSetDataList = new ArrayList<>();
		if (run instanceof AbstractBuild) {
			AbstractBuild<?,?> build = (AbstractBuild<?,?>)run;
			List<ChangeLogSet<?>> changeLogSetList = new ArrayList<ChangeLogSet<?>>();
			changeLogSetList.add((ChangeLogSet<?>)build.getChangeSet());
			return getIncludedCommits(changeLogSetList, logger);
		} else {
			/**
			 * The getChangeSets() method was introduced through issue #24141.
			 * The changes are part of Jenkins 2.60 and workflow job 2.11.
			 * We have kept the minimum Jenkins versions required to 1.625.1 and
			 * use the getChangeSets() method through reflection. Once we reach 2.60, we can directly 
			 * use the method .
			 */
			try {if (run.getClass().getMethod("getChangeSets") != null)  { //$NON-NLS-1$
	    		@SuppressWarnings("unchecked")
				List<ChangeLogSet<?>> changeLogSets = (List<ChangeLogSet<?>>)run.getClass().getMethod("getChangeSets").invoke(run); //$NON-NLS-1$
	    		return getIncludedCommits(changeLogSets, logger);
			}} catch (NoSuchMethodException | SecurityException | IllegalAccessException 
					| IllegalArgumentException | InvocationTargetException e){
				// We could massage this and throw a proper exception.
				// Otherwise user doesn't know that the plugin has failed and they have to upgrade workflow and Jenkins
				LOGGER.log(Level.WARNING, "Error getting changeSets from build", e); //$NON-NLS-1$
				RTCUtils.LogMessage(logger, Messages.Error_CalculatingChanges());
				if (e instanceof NoSuchMethodException) {
					LOGGER.warning("No such method \"getChangeSets\" available on class hudson.model.Run. Upgrade Jenkins to 2.60 and above."); //$NON-NLS-1$
					LOGGER.warning("If you are using a pipeline project, then upgrade workflow-job plugin to 2.11 and above"); //$NON-NLS-1$
					RTCUtils.LogMessage(logger, Messages.Upgrade_Jenkins());
				}
			}
		}
		return changeSetDataList;
	}
	
	private static List<ChangeSetData> getIncludedCommits(List<ChangeLogSet<?>> changeLogSets, PrintStream logger) {
		LOGGER.finest("GitScmUtils:getIncludedCommits Begin"); //$NON-NLS-1$ 
		List<ChangeSetData> csList = new ArrayList<ChangeSetData>();
    	try {
    		for(ChangeLogSet<?> changeLogSet : changeLogSets){
    			Object [] changeSetEntries = changeLogSet.getItems();
    			for (Object o : changeSetEntries) {
	    			ChangeLogSet.Entry csEntry = (ChangeLogSet.Entry) o;
	    			if (isGitScmChangeLogSetEntry(csEntry)) {
	    				ChangeSetData csData = createChangeSetData(csEntry);
	    				if (LOGGER.isLoggable(Level.FINE)) {
		    				LOGGER.fine("Commit Details\n"); //$NON-NLS-1$
		    				LOGGER.fine("commitId : " + csData.id + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
		    							  "comment : " + csData.comment	+ "\n" + //$NON-NLS-1$ //$NON-NLS-2$
		    							  "authorId : " + csData.userName); //$NON-NLS-1$
	    				}
	    				csList.add(csData);    		
	    			}
    			}
    		}
    	} catch(Exception e) {
    		RTCUtils.LogMessage(logger, Messages.Error_CalculatingChanges());
    		RTCUtils.LogMessage(logger, e.getMessage());
    		LOGGER.log(Level.WARNING, "Error getting Git commit details from the build", e); //$NON-NLS-1$
    		csList = null;
    	}	
    	return csList;
	}
	
	public static ChangeSetData getChangeSetData(Entry csEntry, PrintStream logger) {
		try {
			if (isGitScmChangeLogSetEntry(csEntry)) {
				return createChangeSetData(csEntry);
			} else {
				return null;
			}
		} catch (Exception e) {
			RTCUtils.LogMessage(logger, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Returns <code>true</code> if the given ChangeLog entry is from a 
	 * Git commit, <code>false</code> otherwise
	 * 
	 * @param change The changelog entry
	 * @return <code>true</code> if it is from a Git commit, <code>false</code> 
	 * otherwise
	 */
	public static boolean isGitScmChangeLogSetEntry(Entry change) {
		return change != null && 
				change.getClass().getName().equals(GIT_CHANGELOGSET_ENTRY_CLASS_NAME);
	}
	
	private static ChangeSetData createChangeSetData(Entry csEntry) throws Exception {
		// Since we know we are using GitChangeSet, we can look for getComment
		String comment = (String) csEntry.getClass().getMethod("getComment").invoke(csEntry); //$NON-NLS-1$
		ChangeSetData csData = new ChangeSetData(csEntry.getCommitId(), comment, 
									csEntry.getAuthor().getId());
		return csData;
	}
}
