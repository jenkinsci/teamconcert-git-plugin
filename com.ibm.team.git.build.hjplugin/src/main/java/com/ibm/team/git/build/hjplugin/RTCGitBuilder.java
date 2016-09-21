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

import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import net.sf.json.JSON;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.git.build.hjplugin.http.HttpUtils;
import com.ibm.team.git.build.hjplugin.scm.ChangeSetData;
import com.ibm.team.git.build.hjplugin.scm.GitScmUtils;
import com.ibm.team.git.build.hjplugin.util.Helper;
import com.ibm.team.git.build.hjplugin.util.RTCHttpConstants;
import com.ibm.team.git.build.hjplugin.util.ValidationResult;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.ibm.team.git.build.hjplugin.Messages;

public class RTCGitBuilder extends Builder {
	private static final Logger LOGGER = Logger.getLogger(RTCGitBuilder.class
			.getName());

	private String serverURI;
	private boolean jenkinsRootURIOverride;
	private String jenkinsRootURI;
	private boolean useBuildDefinition;
	private String buildDefinition;
	private String workItemUpdateType;
	private boolean annotateChangeLog;
	private boolean ownsBuildCycle;
	private String rtcBuildUUID;
	private String credentialsId;
	private int timeout;
	boolean useWorkItems;
	int trackBuildWorkItem;
	boolean useTrackBuildWorkItem;
	boolean customWorkItemLinkFormat;
	private String workItemLinkFormat;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public RTCGitBuilder(String serverURI, String credentialsId, int timeout,
			boolean jenkinsRootURIOverride, String jenkinsRootURI,
			boolean useBuildDefinition, String buildDefinition,
			boolean annotateChangeLog, boolean useWorkItems,
			String workItemUpdateType, boolean useTrackBuildWorkItem,
			int trackBuildWorkItem, boolean customWorkItemLinkFormat,
			String workItemLinkFormat) {
		this.serverURI = serverURI;
		this.credentialsId = credentialsId;
		this.timeout = timeout;
		this.jenkinsRootURIOverride = jenkinsRootURIOverride;
		this.jenkinsRootURI = jenkinsRootURI;
		this.useBuildDefinition = useBuildDefinition;
		this.buildDefinition = buildDefinition;
		this.annotateChangeLog = annotateChangeLog;
		this.workItemUpdateType = workItemUpdateType;
		this.ownsBuildCycle = false;
		this.useWorkItems = useWorkItems;
		this.useTrackBuildWorkItem = useTrackBuildWorkItem;
		this.trackBuildWorkItem = trackBuildWorkItem;
		this.customWorkItemLinkFormat = customWorkItemLinkFormat;
		this.workItemLinkFormat = workItemLinkFormat;
	}

	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		PrintStream logger = listener.getLogger();
		try {
			if (GitScmUtils.isGitScmBuild(build)) {
				String jRootURI = RTCUtils.getRootURL(build,
						this.jenkinsRootURI, logger);
				String bURI = RTCUtils.getBuildURL(build, logger);
				String buildName = RTCUtils.getBuildShortName(build, logger);
				RTCLoginInfo loginInfo = getLoginInfo(build);
				RTCConnector rCon = new RTCConnector(serverURI,
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), buildDefinition,
						workItemUpdateType, workItemLinkFormat, useBuildDefinition,
						rtcBuildUUID, jRootURI, bURI, buildName);
				if (useBuildDefinition) {
					rtcBuildUUID = build.getEnvironment(listener).get(
							RTCHttpConstants.BUILD_RESULT_UUID);
					if (RTCUtils.IsNullOrEmpty(rtcBuildUUID)) {
						rtcBuildUUID = rCon.createRTCBuild(logger);
						if (rtcBuildUUID != null) {
							ownsBuildCycle = true;
						}
					}
				}
				
				//add action to show the rtc build result
				if(!RTCUtils.IsNullOrEmpty(rtcBuildUUID)) {
					RTCBuildResultAction brAction = new RTCBuildResultAction(serverURI, rtcBuildUUID);
					build.getActions().add(brAction);
				}
			}
		} catch (Exception e) {
		}

		return true;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		try {
			PrintStream logger = listener.getLogger();
			// String jUser = RTCUtils.getBuildUser(build, logger);
			BuildParameterAction bAction = new BuildParameterAction(credentialsId, timeout, serverURI,
					rtcBuildUUID, ownsBuildCycle,
					(useTrackBuildWorkItem ? Integer.toString(trackBuildWorkItem) : null),
					annotateChangeLog,
					(customWorkItemLinkFormat ? workItemLinkFormat : null));
			build.getActions().add(bAction);

			if (GitScmUtils.isGitScmBuild(build)) {
				LOGGER.finer("Git SCM Build");
				String jRootURI = RTCUtils.getRootURL(build,
						this.jenkinsRootURI, logger);
				String bURI = RTCUtils.getBuildURL(build, logger);
				String buildName = RTCUtils.getBuildFullName(build, logger);
				RTCLoginInfo loginInfo = getLoginInfo(build);
				RTCConnector rCon = new RTCConnector(serverURI,
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), buildDefinition,
						workItemUpdateType, workItemLinkFormat,
						useBuildDefinition, rtcBuildUUID,
						jRootURI, bURI, buildName);

				List<ChangeSetData> csData = GitScmUtils.getIncludedCommits(build, logger);
				bAction.setWorkitems(RTCUtils.getAllWorkItems(csData,
						customWorkItemLinkFormat ? workItemLinkFormat : null));
				rCon.publishCommitData(logger, csData);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	private RTCLoginInfo getLoginInfo(AbstractBuild<?, ?> build)
			throws InvalidCredentialsException {
		return new RTCLoginInfo(build, getServerURI(), getCredentialsId(),
				getTimeout());
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This get displayed at 'Add build step' button.
		 */
		public String getDisplayName() {
			return Messages.RTC_plugin_display_name();
		}

		public FormValidation doCheckJobConnection(
				@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("timeout") String timeout) {

			// validate the connection
			ValidationResult result = validateConnectInfo(serverURI, credId,
					timeout);

			// validate the connection
			if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return result.validationResult;
			} else {
				FormValidation connectCheck = checkConnect(result.loginInfo);
				return Helper.mergeValidationResults(result.validationResult,
						connectCheck);
			}
		}

		public FormValidation doCheckTimeout(@QueryParameter String timeout) {
			return RTCLoginInfo.validateTimeout(timeout);
		}

		public FormValidation doTrackBuildWorkItem(
				@QueryParameter String trackBuildWorkItem) {
			trackBuildWorkItem = Util.fixEmptyAndTrim(trackBuildWorkItem);
			if (RTCUtils.IsNullOrEmpty(trackBuildWorkItem)) {
				return FormValidation.ok();
			}
			return FormValidation.validatePositiveInteger(trackBuildWorkItem);
		}

		public FormValidation doValidateWorkItemLinkFormat(
				@QueryParameter("workItemLinkFormat") String workItemLinkFormat) {
			if (RTCUtils.IsNullOrEmpty(workItemLinkFormat)) {
				return FormValidation.ok();
			}
			try {
				//noinspection ResultOfMethodCallIgnored
				Pattern.compile(workItemLinkFormat);
			} catch (Exception e) {
				return FormValidation.error(Messages.RTC_workitemlinkformat_check_invalid());
			}
			return FormValidation.ok(Messages.RTC_workitemlinkformat_check_success());
		}

		public FormValidation doValidateTrackBuildWorkItem(
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("trackBuildWorkItem") String trackBuildWorkItem,
				@QueryParameter("timeout") String timeout) {

			// validate the info for connecting to the server
			ValidationResult connectInfoCheck = validateConnectInfo(serverURI,
					credId, timeout);
			if (connectInfoCheck.validationResult.kind
					.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} else {
				// connection info is good now validate the build definition
				FormValidation wiCheck = checkWorkItem(connectInfoCheck.loginInfo, trackBuildWorkItem);
				if(wiCheck.equals(FormValidation.Kind.OK)) {
					return wiCheck;
				}
				return Helper
						.mergeValidationResults(
								connectInfoCheck.validationResult,
								wiCheck);
			}
		}

		public FormValidation doValidateBuildDefinition(
				@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("buildDefinition") String buildDef,
				@QueryParameter("timeout") String timeout) {

			// validate the info for connecting to the server
			ValidationResult connectInfoCheck = validateConnectInfo(serverURI,
					credId, timeout);
			if (connectInfoCheck.validationResult.kind
					.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} else {
				// connection info is good now validate the build definition
				FormValidation buildDefinitionCheck = checkBuildDefinition(
						connectInfoCheck.loginInfo, buildDef);
				return Helper
						.mergeValidationResults(
								connectInfoCheck.validationResult,
								buildDefinitionCheck);
			}
		}

		public FormValidation doCheckCredentialsId(
				@QueryParameter("credentialsId") String credentialsId) {
			credentialsId = Util.fixEmptyAndTrim(credentialsId);
			if (credentialsId == null) {
				return FormValidation
						.error(Messages.RTC_credentials_required());
			}
			return FormValidation.ok();
		}

		public ListBoxModel doFillCredentialsIdItems(
				@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter String serverURI) {
			return new StandardListBoxModel()
					.withEmptySelection()
					.withMatching(
							CredentialsMatchers
									.instanceOf(StandardUsernamePasswordCredentials.class),
							CredentialsProvider.lookupCredentials(
									StandardUsernamePasswordCredentials.class,
									project, ACL.SYSTEM, URIRequirementBuilder
											.fromUri(serverURI).build()));
		}

		private FormValidation checkConnect(RTCLoginInfo loginInfo) {

			try {
				String errorMessage = testConnection(loginInfo.getServerUri(),
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout());
				if (errorMessage != null && errorMessage.length() != 0) {
					return FormValidation.error(errorMessage);
				}
			} catch (InvocationTargetException e) {
				Throwable eToReport = e.getCause();
				if (eToReport == null) {
					eToReport = e;
				}
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkConnect attempted with " + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(
							Level.FINER,
							"checkConnect invocation failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
				}
				return FormValidation.error(eToReport,
						Messages.RTC_failed_to_connect(eToReport.getMessage()));
			} catch (Exception e) {
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkConnect attempted with " + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER,
							"checkConnect failed " + e.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(e,
						Messages.RTC_failed_to_connect(e.getMessage()));
			}
			return FormValidation.ok(Messages.RTC_connect_success());
		}

		private static String testConnection(String serverURI, String userId,
				String password, int timeout) throws Exception {
			String errorMessage = null;
			String uri = RTCHttpConstants.URI_COMPATIBILITY_CHECK;
			try {

				// Validate that the server version is sufficient
				JSON json = HttpUtils.performGet(serverURI, uri, userId,
						password, timeout, null, null).getJson();
				errorMessage = RtcJsonUtil.ensureCompatability(json);

				if (errorMessage == null) {
					// Make sure the credentials are good
					HttpUtils.validateCredentials(serverURI, userId, password,
							timeout, null);
				}
			} catch (org.apache.http.auth.InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			}
			return errorMessage;
		}

		private ValidationResult validateConnectInfo(final String serverURI,
				String credId, final String timeout) {
			credId = Util.fixEmptyAndTrim(credId);
			ValidationResult result = new ValidationResult();

			// validate the authentication information
			FormValidation basicValidate = RTCLoginInfo.basicValidate(credId,
					timeout);
			if (basicValidate.kind.equals(FormValidation.Kind.ERROR)) {
				result.validationResult = Helper.mergeValidationResults(
						result.validationResult, basicValidate);
				return result;
			}

			try {
				result.loginInfo = new RTCLoginInfo(null, serverURI, credId,
						Integer.parseInt(timeout));
				result.validationResult = FormValidation.ok();
			} catch (InvalidCredentialsException e) {
				result.validationResult = FormValidation.error(e,
						e.getMessage());
			}
			return result;
		}

		private FormValidation checkBuildDefinition(RTCLoginInfo loginInfo,
				String buildDefinition) {
			try {
				if (RTCConnector.isValidBuildDefintion(
						loginInfo.getServerUri(), buildDefinition,
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout())) {
					return FormValidation.ok(Messages
							.RTC_build_definition_success());
				}
			} catch (Exception e) {

			}
			return FormValidation
					.error(Messages.RTC_build_definition_invalid());
		}

		private FormValidation checkWorkItem(RTCLoginInfo loginInfo,
				String workitemid) {
			try {
				String wiSummary = RTCConnector.validateAndGetWorkItem(
						loginInfo.getServerUri(), workitemid,
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout());
				if (wiSummary != null && wiSummary.length() > 0) {
					return FormValidation.ok(Messages
							.RTC_workitem_check_success(wiSummary));
				}
			} catch (Exception e) {

			}
			return FormValidation.error(Messages.RTC_workitem_check_invalid());
		}
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getServerURI() {
		return serverURI;
	}

	public boolean getJenkinsRootURIOverride() {
		return jenkinsRootURIOverride;
	}

	public String getJenkinsRootURI() {
		return jenkinsRootURI;
	}

	public boolean getUseBuildDefinition() {
		return useBuildDefinition;
	}

	public String getBuildDefinition() {
		return buildDefinition;
	}

	public String getWorkItemUpdateType() {
		return workItemUpdateType;
	}

	public boolean getAnnotateChangeLog() {
		return annotateChangeLog;
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	public int getTimeout() {
		return timeout;
	}

	public int getTrackBuildWorkItem() {
		return trackBuildWorkItem;
	}

	public boolean getUseWorkItems() {
		return useWorkItems;
	}

	public boolean getUseTrackBuildWorkItem() {
		return useTrackBuildWorkItem;
	}

	public boolean getCustomWorkItemLinkFormat() {
		return customWorkItemLinkFormat;
	}

	public String getWorkItemLinkFormat() {
		return workItemLinkFormat;
	}
}
