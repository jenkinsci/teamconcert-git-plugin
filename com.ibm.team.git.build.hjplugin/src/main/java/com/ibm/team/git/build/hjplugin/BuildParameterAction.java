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
package com.ibm.team.git.build.hjplugin;

import hudson.model.InvisibleAction;
import org.apache.commons.lang.StringUtils;

public class BuildParameterAction extends InvisibleAction {

	private final String rtcURL;
	private final String rtcBuildUUID;
	private final boolean shouldAnnotate;
	private final boolean ownsBuildCycle;
	private String credentialsId;
	private String workItemLinkFormat;
	private String trackbuildwi;
	private final int timeout;
	private String[] workitems;

	public BuildParameterAction(String credentialsId, int timeout,
			String rtcURL, String rtcBuildUUID, boolean ownsBuildCycle,
			String trackbuildwi,
			boolean shouldAnnotate,
			String workItemLinkFormat) {
		this.credentialsId = credentialsId;
		this.rtcURL = rtcURL;
		this.rtcBuildUUID = rtcBuildUUID;
		this.shouldAnnotate = shouldAnnotate;
		this.workItemLinkFormat = StringUtils.isBlank(workItemLinkFormat) ? null : workItemLinkFormat;
		this.ownsBuildCycle = ownsBuildCycle;
		this.trackbuildwi = trackbuildwi;
		this.timeout = timeout;
	}
	
	public String getTrackbuildWi() {
		return trackbuildwi;
	}

	public int getTimeout() {
		return timeout;
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	public String getRtcURL() {
		return rtcURL;
	}

	public String getRtcBuildUUID() {
		return rtcBuildUUID;
	}

	public boolean shouldAnnotate() {
		return shouldAnnotate;
	}

	public boolean iOwnBuildCycle() {
		return ownsBuildCycle;
	}

	public String[] getWorkitems() {
		return workitems;
	}

	public void setWorkitems(String[] workitems) {
		this.workitems = workitems;
	}

	public String getWorkItemLinkFormat() {
		return workItemLinkFormat;
	}
}
