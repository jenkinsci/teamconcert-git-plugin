/*******************************************************************************
 * Copyright (c) 2014, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.git.build.hjplugin;

import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.git.build.hjplugin.util.Helper;

import hudson.Extension;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.Builder;

@Extension
public class RTCRunListener extends RunListener<Run<?, ?>> {

	private static final Logger LOGGER = Logger.getLogger(RTCRunListener.class
			.getName());
	
	@Override
	public void onStarted(Run<?, ?> build, TaskListener listener) {
		LOGGER.finest("RTCRunListener.onStarted : Begin");
		if(build.getParent() instanceof FreeStyleProject) {
			FreeStyleProject project = (FreeStyleProject)build.getParent();
			List<Builder> list = project.getBuilders();
			for(Builder builder : list)
			{
				if(builder instanceof RTCGitBuilder)
				{
					RTCGitBuilder g = (RTCGitBuilder)builder;
					
					String resolvedTrackBuildWorkItem = null;
					PrintStream logger = listener.getLogger();
					try {
						resolvedTrackBuildWorkItem = Helper.resolveFieldParameterToValue(build, g.getTrackBuildWorkItem(), listener);
						LOGGER.info(String.format("Resolving track build work item %s", resolvedTrackBuildWorkItem)); //$NON-NLS-1$
						BuildParameterAction bAction = new BuildParameterAction(g.getCredentialsId(), g.getTimeout(), g.getServerURI(),
								g.getRtcBuildUUID(), g.getOwnsBuildCycle(),
								(g.getUseTrackBuildWorkItem() ? resolvedTrackBuildWorkItem : null),
								g.getAnnotateChangeLog());
						
						build.addAction(bAction);
						g.setBuildParameterAction(bAction);
						
						RTCLoginInfo loginInfo = null;
						
						loginInfo = new RTCLoginInfo(build.getParent(),
								bAction.getRtcURL(),
								bAction.getCredentialsId(),
								bAction.getTimeout());
						RTCConnector rCon = new RTCConnector(
								bAction.getRtcURL(),
								loginInfo.getUserId(), loginInfo.getPassword(),
								loginInfo.getTimeout(), null, null, false,
								bAction.getRtcBuildUUID(), null, null, null, bAction.iOwnBuildCycle());
						
						if (bAction.getTrackbuildWi() != null) {
							rCon.updateWorkItem(listener.getLogger(),
									resolvedTrackBuildWorkItem, RTCUtils
									.getBuildStartedComment(listener
											.getLogger(), RTCUtils
											.getFullBuildURL(build, null,
													logger), build
											.getFullDisplayName(), null,
											RTCUtils.getBuildUser(build)));									
						}
					}
					catch (Exception e) {
						RTCUtils.LogMessage(listener.getLogger(),
								Messages.Error_UpdatingBuildStatus());
						RTCUtils.LogMessage(listener.getLogger(), e.getMessage());
						LOGGER.log(Level.WARNING, Messages.Error_UpdatingBuildStatus(), e);
					}
					
				}
					
			}
			
		}		
	}
	
	@Override
	public void onCompleted(Run<?, ?> build, TaskListener listener) {
		LOGGER.finest("RTCRunListener.onCompleted : Begin"); //$NON-NLS-1$
		try {
			List<BuildParameterAction> bActions = build
					.getActions(BuildParameterAction.class);
			if (bActions != null) {
				for (BuildParameterAction bAction : bActions) {
					RTCLoginInfo loginInfo = new RTCLoginInfo(build,
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