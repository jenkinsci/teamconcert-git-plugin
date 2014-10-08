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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.listeners.RunListener;

import com.ibm.team.git.build.hjplugin.Messages;

@Extension
public class RTCRunListener extends RunListener<AbstractBuild<?, ?>> {

	private static final Logger LOGGER = Logger.getLogger(RTCRunListener.class
			.getName());

	@Override
	public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
		try {
			List<RTCGitBuilder> rtcBuilders = getRTCBuilders(build.getProject());
			if (rtcBuilders != null) {
				for (RTCGitBuilder rtcGitBuilder : rtcBuilders) {
					if (rtcGitBuilder.getUseTrackBuildWorkItem()) {
						RTCLoginInfo loginInfo = new RTCLoginInfo(build,
								rtcGitBuilder.getServerURI(),
								rtcGitBuilder.getCredentialsId(),
								rtcGitBuilder.getTimeout());
						RTCConnector rCon = new RTCConnector(
								rtcGitBuilder.getServerURI(),
								loginInfo.getUserId(), loginInfo.getPassword(),
								loginInfo.getTimeout(), null, null, false,
								null, null, null, null);
						rCon.updateWorkItem(listener.getLogger(),
								Integer.toString(rtcGitBuilder
										.getTrackBuildWorkItem()), RTCUtils
										.getBuildStartedComment(listener
												.getLogger(), RTCUtils
												.getFullBuildURL(build, null,
														null), build
												.getFullDisplayName(), null,
												RTCUtils.getBuildUser(build)));
					}
				}
			}
		} catch (Exception e) {
			RTCUtils.LogMessage(listener.getLogger(),
					Messages.Error_UpdatingBuildStatus());
			RTCUtils.LogMessage(listener.getLogger(), e.getMessage());
		}
	}

	private List<RTCGitBuilder> getRTCBuilders(Object project) {
		List<RTCGitBuilder> rBuilds = new ArrayList<RTCGitBuilder>();
		if (project instanceof Project) {
			for (Object builder : ((Project) project).getBuilders()) {
				if (builder instanceof RTCGitBuilder) {
					rBuilds.add((RTCGitBuilder) builder);
				}
			}
		} else if (project instanceof MatrixProject) {
			for (Object builder : ((MatrixProject) project)
					.getBuildWrappersList()) {
				if (builder instanceof RTCGitBuilder) {
					rBuilds.add((RTCGitBuilder) builder);
				}
			}
		}
		return rBuilds;
	}

	@Override
	public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
		try {
			BuildParameterAction bAction = build
					.getAction(BuildParameterAction.class);
			String buildResultUUID = bAction != null ? bAction
					.getRtcBuildUUID() : null;
			List<RTCGitBuilder> rtcBuilders = getRTCBuilders(build.getProject());
			if (rtcBuilders != null) {
				for (RTCGitBuilder rtcGitBuilder : rtcBuilders) {
					if (rtcGitBuilder.getUseTrackBuildWorkItem()) {
						RTCLoginInfo loginInfo = new RTCLoginInfo(build,
								rtcGitBuilder.getServerURI(),
								rtcGitBuilder.getCredentialsId(),
								rtcGitBuilder.getTimeout());
						RTCConnector rCon = new RTCConnector(
								rtcGitBuilder.getServerURI(),
								loginInfo.getUserId(), loginInfo.getPassword(),
								loginInfo.getTimeout(), null, null, false,
								buildResultUUID, null, null, null);
						rCon.completeBuild(listener.getLogger(),
								getBuildResult(build.getResult()));
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
		}
	}

	private int getBuildResult(Result result) {
		if (Result.SUCCESS == result)
			return 0;
		return 1;
	}

	private String getBuildStatus(Result result) {
		if (result != null) {
			return result.toString();
		}
		return "";
	}

	@Override
	public void onDeleted(AbstractBuild<?, ?> r) {
		// TODO
		// com.ibm.team.build.client.ITeamBuildBaseClient.delete(IBuildResultHandle,
		// IProgressMonitor)
		super.onDeleted(r);
	}

}