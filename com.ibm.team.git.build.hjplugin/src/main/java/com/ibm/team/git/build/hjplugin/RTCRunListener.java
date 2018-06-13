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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class RTCRunListener extends RunListener<Run<?, ?>> {

	private static final Logger LOGGER = Logger.getLogger(RTCRunListener.class
			.getName());
	
	@Override
	public void onCompleted(Run<?, ?> build, TaskListener listener) {
		LOGGER.finest("RTCRunListener.onCompleted : Begin"); //$NON-NLS-1$
		try {
			List<BuildParameterAction> bActions = build
					.getActions(BuildParameterAction.class);
			if (bActions != null) {
				for (BuildParameterAction bAction : bActions) {
					RTCLoginInfo loginInfo = new RTCLoginInfo(build.getParent(),
							bAction.getRtcURL(),
							bAction.getCredentialsId(),
							bAction.getTimeout());
					RTCConnector rCon = new RTCConnector(
							bAction.getRtcURL(),
							loginInfo.getUserId(), loginInfo.getPassword(),
							loginInfo.getTimeout(), null, null, false,
							bAction.getRtcBuildUUID(), null, null, null, bAction.iOwnBuildCycle());
					rCon.completeBuild(listener.getLogger(),
							getBuildResult(build.getResult()));
					if (bAction.getTrackbuildWi() != null) {
						rCon.updateWorkItem(listener.getLogger(), bAction
									.getTrackbuildWi(), RTCUtils
									.getCompleteBuildComment(RTCUtils
											.getFullBuildURL(build, null, null),
											build.getFullDisplayName(),
											getBuildStatus(build.getResult())));
					}
				}
			}
		} catch (Exception e) {
			RTCUtils.LogMessage(listener.getLogger(),
					Messages.Error_UpdatingBuildStatus());
			RTCUtils.LogMessage(listener.getLogger(), e.getMessage());
			LOGGER.log(Level.WARNING, Messages.Error_UpdatingBuildStatus(), e);
		}
	}

	private int getBuildResult(Result result) {
		if (Result.SUCCESS == result)
			return 0;
		return 1;
	}

	private static String getBuildStatus(Result result) {
		if (result != null) {
			return result.toString();
		}
		return ""; //$NON-NLS-1$
	}

	@Override
	public void onDeleted(Run<?, ?> r) {
		super.onDeleted(r);
	}

}