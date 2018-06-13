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
package com.ibm.team.git.build.hjplugin;

import hudson.model.InvisibleAction;

/**
 * Action to hold information about the configuration and 
 * runtime parameters related to the plugin  
 *
 */
public class BuildParameterAction extends InvisibleAction {
	private static final String[] EMPTY_ARRAY = new String[0];
	private final String rtcURL;
	private final String rtcBuildUUID;
	private final boolean shouldAnnotate;
	private final boolean ownsBuildCycle;
	private String credentialsId;
	private String trackbuildwi;
	private final int timeout;
	private String[] workitems;
	private String[] changeSetIds = new String[0];

	/**
	 * @param credentialsId
	 * @param timeout
	 * @param rtcURL
	 * @param rtcBuildUUID
	 * @param ownsBuildCycle - Whether the step invocation owns the build life cycle. 
	 * 					This is <code>true</code> if the plugin starts a build result
	 *					instead of reusing the buildresultUUID build parameter 
	 * @param trackbuildwi
	 * @param shouldAnnotate
	 */
	public BuildParameterAction(String credentialsId, int timeout,
			String rtcURL, String rtcBuildUUID, boolean ownsBuildCycle,
			String trackbuildwi,
			boolean shouldAnnotate) {
		this.credentialsId = credentialsId;
		this.rtcURL = rtcURL;
		this.rtcBuildUUID = rtcBuildUUID;
		this.shouldAnnotate = shouldAnnotate;
		this.ownsBuildCycle = ownsBuildCycle;
		this.trackbuildwi = trackbuildwi;
		this.timeout = timeout;
		this.changeSetIds = new String[0];
	}
	
	/**
	 * @return
	 */
	public String getTrackbuildWi() {
		return trackbuildwi;
	}

	/**
	 * @return
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @return
	 */
	public String getCredentialsId() {
		return credentialsId;
	}

	/**
	 * @return
	 */
	public String getRtcURL() {
		return rtcURL;
	}

	/**
	 * @return
	 */
	public String getRtcBuildUUID() {
		return rtcBuildUUID;
	}

	/**
	 * @return
	 */
	public boolean shouldAnnotate() {
		return shouldAnnotate;
	}

	/**
	 * @return
	 */
	public boolean iOwnBuildCycle() {
		return ownsBuildCycle;
	}

	/**
	 * @return
	 */
	public String[] getWorkitems() {
		return workitems;
	}

	/**
	 * @param workitems
	 */
	public void setWorkitems(String[] workitems) {
		this.workitems = workitems;
	}

	/**
	 * Set the change set Ids associated with this 
	 * buildResultAction
	 * 
	 * @param changeSetIds An array of change set ids
	 */
	public void setChangeSetIds(String[] changeSetIds) {
		if (changeSetIds != null) {
			this.changeSetIds = changeSetIds;
		}
	}
	
	/**
	 * Return the list of changeSetIds associated with this 
	 * BuildResultAction
	 * 
	 * This can be null for old builds which don't have the 
	 * change set id info. I am trying to set it to non nul in 
	 * constructor but it has no effect.
	 * 
	 * @return a list of changeSetIds. never <code>null</code>
	 */
	public String[] getChangeSetIds() {
		if (changeSetIds == null) {
			return EMPTY_ARRAY;
		}
		return changeSetIds;
	}
}
