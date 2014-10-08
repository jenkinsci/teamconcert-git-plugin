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

import com.ibm.team.git.build.hjplugin.util.RTCHttpConstants;

import hudson.model.Action;

public class RTCBuildResultAction implements Action{

	private String buildResultUUID;
	private String serverURI;

	public RTCBuildResultAction(String serverURI, String buildResultUUID) {
		this.buildResultUUID = buildResultUUID;
        this.serverURI = serverURI;
	}
	
	public String getIconFileName() {
		// TODO Use a Jenkins one for now
		if (!RTCUtils.IsNullOrEmpty(serverURI) && !RTCUtils.IsNullOrEmpty(buildResultUUID)) {
			return "star-gold.gif"; //$NON-NLS-1$
		}
		// show nothing in task list
		return null; 
	}

	public String getDisplayName() {
		if (!RTCUtils.IsNullOrEmpty(serverURI) && !RTCUtils.IsNullOrEmpty(buildResultUUID)) {
			return Messages.RTCBuildResultAction_display_name();
		}
		return null;
	}

	public String getUrlName() {
		if (!RTCUtils.IsNullOrEmpty(serverURI) && !RTCUtils.IsNullOrEmpty(buildResultUUID)) {
			return RTCUtils.makeFullURL(serverURI, RTCHttpConstants.BUILD_RESULT_ITEM_OID + buildResultUUID);
		}
		return null;
	}

}
