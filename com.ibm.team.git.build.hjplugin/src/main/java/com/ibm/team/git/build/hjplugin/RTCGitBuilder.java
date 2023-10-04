/*******************************************************************************
 * Copyright (c) 2014, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.git.build.hjplugin;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSON;

public class RTCGitBuilder extends Builder implements SimpleBuildStep {
	private static final Logger LOGGER = Logger.getLogger(RTCGitBuilder.class
			.getName());

	private String serverURI;
	private String jenkinsRootURI;
	private String buildDefinition;
	private String workItemUpdateType;
	private String rtcBuildUUID;
	private String credentialsId;
	private int timeout;
	private String trackBuildWorkItem;

	private boolean jenkinsRootURIOverride = false;
	private boolean useBuildDefinition = false;
	private boolean annotateChangeLog = true;
	private boolean ownsBuildCycle = false;
	private boolean useWorkItems = false;
	private boolean useTrackBuildWorkItem = false;
	
	private BuildParameterAction buildParameterAction;
	
	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@Deprecated
	public RTCGitBuilder(String serverURI, String credentialsId, int timeout,
			boolean jenkinsRootURIOverride, String jenkinsRootURI,
			boolean useBuildDefinition, String buildDefinition,
			boolean annotateChangeLog, boolean useWorkItems,
			String workItemUpdateType, boolean useTrackBuildWorkItem,
			int trackBuildWorkItem) {
		this(serverURI, credentialsId, timeout, jenkinsRootURIOverride, jenkinsRootURI, useBuildDefinition, 
				buildDefinition, annotateChangeLog, useWorkItems, workItemUpdateType, 
				useTrackBuildWorkItem, Integer.toString(trackBuildWorkItem));	
	}
		
	@Deprecated
	public RTCGitBuilder(String serverURI, String credentialsId, int timeout,
			boolean jenkinsRootURIOverride, String jenkinsRootURI,
			boolean useBuildDefinition, String buildDefinition,
			boolean annotateChangeLog, boolean useWorkItems,
			String workItemUpdateType, boolean useTrackBuildWorkItem,
			String trackBuildWorkItem) {
		this.serverURI = serverURI;
		this.credentialsId = credentialsId;
		this.annotateChangeLog = annotateChangeLog;
		this.ownsBuildCycle = false;

		setTimeout(timeout);
		setJenkinsRootURIOverride(jenkinsRootURIOverride);
		setJenkinsRootURI(jenkinsRootURI);
		setUseBuildDefinition(useBuildDefinition);
		setBuildDefinition(buildDefinition);
		setWorkItemUpdateType(workItemUpdateType);
		setUseWorkItems(useWorkItems);
		setUseTrackBuildWorkItem(useTrackBuildWorkItem);
		setTrackBuildWorkItem(trackBuildWorkItem);
	}
	
	@DataBoundConstructor
	public RTCGitBuilder(String serverURI, String credentialsId, boolean annotateChangeLog) {
		this.serverURI = serverURI;
		this.credentialsId = credentialsId;
		this.annotateChangeLog = annotateChangeLog;
		this.ownsBuildCycle = false;	
	}
		
	public String getRtcBuildUUID() {
		return rtcBuildUUID;
	}
	
	public boolean getOwnsBuildCycle() {
		return ownsBuildCycle;
	}
	
	@DataBoundSetter
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	@DataBoundSetter
	public void setJenkinsRootURIOverride(boolean jenkinsRootURIOverride) {
		this.jenkinsRootURIOverride = jenkinsRootURIOverride;
	}
	
	@DataBoundSetter
	public void setJenkinsRootURI(String jenkinsRootURI) {
		this.jenkinsRootURI = jenkinsRootURI;
	}
	
	@DataBoundSetter
	public void setUseBuildDefinition(boolean useBuildDefinition) {
		this.useBuildDefinition = useBuildDefinition;
	}
	
	@DataBoundSetter
	public void setBuildDefinition(String buildDefinition) {
		this.buildDefinition = Util.fixEmptyAndTrim(buildDefinition);
	}
	
	@DataBoundSetter
	public void setWorkItemUpdateType(String workItemUpdateType) {
		this.workItemUpdateType = workItemUpdateType;
	}

	@DataBoundSetter
	public void setUseWorkItems(boolean useWorkItems) {
		this.useWorkItems = useWorkItems;
	}

	@DataBoundSetter
	public void setUseTrackBuildWorkItem(boolean useTrackBuildWorkItem) {
		this.useTrackBuildWorkItem = useTrackBuildWorkItem;
	}
	
	@DataBoundSetter
	public void setTrackBuildWorkItem(String trackBuildWorkItem) {
		this.trackBuildWorkItem = trackBuildWorkItem;
	}

	public void prebuild(Run<?, ?> build, TaskListener listener) throws IOException, 
											InterruptedException, InvalidCredentialsException {
		LOGGER.info("RTCGitBuilder.prebuild : Begin"); //$NON-NLS-1$
		PrintStream logger = listener.getLogger();
		String jRootURI = RTCUtils.getJenkinsRootURL(build,
				this.jenkinsRootURI, logger);
		String bURI = RTCUtils.getBuildURL(build, logger);
		String buildName = RTCUtils.getBuildShortName(build, logger);
		RTCLoginInfo loginInfo = getLoginInfo2(build);
		// I shouldn't worry about the build result UUID unless there is build definition integration
		// One more test case, start build from RTC but do not have build definition integration
		if (this.useBuildDefinition) {
			// if getBuildDefinition() == null, then throw an error
			if (getBuildDefinition() == null) {
				// TODO needs translation
				throw new IOException(Messages.RTCGitBuilder_BuildDefinitionRequired());
			}
			LOGGER.info("Looking for rtcBuildResultUUID from the build/environment"); //$NON-NLS-1$
			this.rtcBuildUUID = Helper.getStringBuildParameter(build, RTCHttpConstants.BUILD_RESULT_UUID, listener);
			RTCConnector rCon = new RTCConnector(serverURI,
					loginInfo.getUserId(), loginInfo.getPassword(),
					loginInfo.getTimeout(), buildDefinition,
					workItemUpdateType, useBuildDefinition,
					rtcBuildUUID, jRootURI, bURI, buildName, false);

			// First check whether we need to create a build result by checking whether rtcBuildUUID is null or empty
			// If it is not null, then check whether the build definition ids match.
			boolean shouldCreateBuildResult = RTCUtils.IsNullOrEmpty(rtcBuildUUID);
			if (this.rtcBuildUUID != null) {
				LOGGER.info("rtcBuildResultUUID found, verifying whether it is from the same build definition configured"); //$NON-NLS-1$
				String buildDefinitionId = null;
				try {
					buildDefinitionId = rCon.getBuildDefinitionId(this.serverURI, loginInfo.getUserId(), loginInfo.getPassword(), 
							this.timeout, rtcBuildUUID, listener);
				} catch (org.apache.http.auth.InvalidCredentialsException | GeneralSecurityException | IOException exp) {
					// Log the exception and continue;
					if (LOGGER.isLoggable(Level.WARNING)) {
						LOGGER.log(Level.WARNING, String.format("Unable to get build definition id for build result %s", this.rtcBuildUUID), //$NON-NLS-1$ 
								exp);
					}
					buildDefinitionId = null;
				}
				// Check whether it is from the same build definition and 
				// if not, then shouldCreateBuildResult should be true
				// If buildDefinitionId is null or different, then I will create a buildResult 
				if (!(getBuildDefinition().equals(buildDefinitionId))) {
					LOGGER.info("Configured build definition id does not match the build definition id of the build result"); //$NON-NLS-1$
					LOGGER.info("Creating a new build result"); //$NON-NLS-1$
					shouldCreateBuildResult = true;
				}
			}
			if (shouldCreateBuildResult) {
				this.rtcBuildUUID = rCon.createRTCBuild(logger);
				if (this.rtcBuildUUID != null) {
					this.ownsBuildCycle = true;
				}
			}
		}
		
		//add action to show the RTC build result
		if(!RTCUtils.IsNullOrEmpty(this.rtcBuildUUID)) {
			LOGGER.info(String.format("Creating a new build result action for %s", this.rtcBuildUUID)); //$NON-NLS-1$
			RTCBuildResultAction brAction = new RTCBuildResultAction(this.serverURI, this.rtcBuildUUID);
			build.addAction(brAction);
		}
	}
	
	public void perform(Run<?, ?> run, FilePath arg1, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		
		try { 
			// If there is no Git invocation before  calling RTCGitBuilder,
			// then there is no change set to annotate or 
			// work item information to publish to the build or link Jenkins build 
			// to work items.
			prebuild(run, listener);
			PrintStream logger = listener.getLogger();
			LOGGER.info(String.format("Resolving track build work item %s", this.trackBuildWorkItem)); //$NON-NLS-1$
			String resolvedTrackBuildWorkItem = Helper.resolveFieldParameterToValue(run, this.trackBuildWorkItem, listener);
			LOGGER.info(String.format("Resolving track build work item %s", resolvedTrackBuildWorkItem)); //$NON-NLS-1$
			
			if(!(run.getParent() instanceof FreeStyleProject)) {
				buildParameterAction = new BuildParameterAction(credentialsId, timeout, serverURI,
						rtcBuildUUID, ownsBuildCycle,
						(useTrackBuildWorkItem ? resolvedTrackBuildWorkItem : null),
						annotateChangeLog);
				
				run.addAction(buildParameterAction);
			}else {
				buildParameterAction = getBuildParameterAction();
				buildParameterAction.setRtcBuildUUID(rtcBuildUUID);
				buildParameterAction.setownsBuildCycle(ownsBuildCycle);
			}
			
			String jRootURI = RTCUtils.getJenkinsRootURL(run,
					this.jenkinsRootURI, logger);
			String bURI = RTCUtils.getBuildURL(run, logger);
			String buildName = RTCUtils.getBuildFullName(run, logger);
			RTCLoginInfo loginInfo = getLoginInfo2(run);
			RTCConnector rCon = new RTCConnector(serverURI,
					loginInfo.getUserId(), loginInfo.getPassword(),
					loginInfo.getTimeout(), buildDefinition,
					workItemUpdateType, useBuildDefinition, rtcBuildUUID,
					jRootURI, bURI, buildName, ownsBuildCycle);
			
			// First update the track Build work item 
			// Use the resolved trackBuildWorkItem
			if(!(run.getParent() instanceof FreeStyleProject)) {
			rCon.updateWorkItem(listener.getLogger(),
					resolvedTrackBuildWorkItem, RTCUtils
					.getBuildStartedComment(listener
							.getLogger(), RTCUtils
							.getFullBuildURL(run, jRootURI,
									logger), run
							.getFullDisplayName(), null,
							RTCUtils.getBuildUser(run)));
		}
			/**
			 *  Note that multiple invocations of RTCGitBuilder will annotate the 
			 *  work items over and over. Also, we will get all the work items in 
			 *  all of the Git commits.
			 *  Some of the Git commits might not belong to the invocation of 
			 *  this builder.
			 *  First filter commits that have been  processed by previous builders, ie.,
			 *  those commits that have been added to previous build result actions.
			 *  Then add information to the current build result action 
			 *  about the git commits that were processed by this invocation of 
			 *  builder.
			 */
			
			List<ChangeSetData> csData = getCsData(run, listener); 
			String[] workItems = RTCUtils.getAllWorkItems(csData);
			buildParameterAction.setWorkitems(workItems);
			// Also add the unique change set details for this action
			buildParameterAction.setChangeSetIds(getChangeSetIdsFromCsData(csData).toArray(new String[0]));
			String format = String.format("work items are %s", Arrays.toString(workItems)); //$NON-NLS-1$
			LOGGER.info(format);
			rCon.publishCommitData(logger, csData, listener);
			
		}
		catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error running RTCGitBuilder step", e); //$NON-NLS-1$
		}
	}

	private RTCLoginInfo getLoginInfo(Job<?,?> job)
			throws InvalidCredentialsException {
		return new RTCLoginInfo(job, getServerURI(), getCredentialsId(),
				getTimeout());
	}
	
	private RTCLoginInfo getLoginInfo2(Run<?,?> build)
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
							"\" password=\"" + "*********" + //$NON-NLS-1$ //$NON-NLS-2$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
				}
				if (LOGGER.isLoggable(Level.WARNING)) {
					LOGGER.log(Level.WARNING,
							"checkConnect failed " + e.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(e,
						Messages.RTC_failed_to_connect(e.getMessage()));
			}
			return FormValidation.ok(Messages.RTC_connect_success());
		}

		private String testConnection(String serverURI, String userId,
				String password, int timeout) throws Exception {
			String errorMessage = null;
			String uri = RTCHttpConstants.URI_COMPATIBILITY_CHECK;
			try {
				if (LOGGER.isLoggable(Level.INFO)) {
					LOGGER.info(String.format("Performing connection test against server %s", serverURI)); //$NON-NLS-1$
				}
				// Validate that the server version is sufficient
				JSON json = HttpUtils.performGet(serverURI, uri, userId,
						password, timeout, null, null).getJson();
				errorMessage = RtcJsonUtil.ensureCompatability(json);
				
				Boolean compatible = false;
				String serverVersion = Util.fixEmptyAndTrim(RtcJsonUtil.getString(json, RtcJsonUtil.JSON_PROP_SERVER_VERSION));
				if(serverVersion != null && RtcJsonUtil.HTTP_ERROR_MSG_VERSION_INCOMPATIBLE.equals(errorMessage))
				{
					String serverVersionWithoutMileStone = RtcJsonUtil.extractServerVersionWithoutMilestone(serverVersion);
					if (serverVersionWithoutMileStone != null) {
						boolean isServerVersionHigher = RtcJsonUtil.isServerVersionEqualOrHigher(serverVersionWithoutMileStone, RTCHttpConstants.MINIMUM_SERVER_VERSION);
						if (isServerVersionHigher) {
							JSON compatibilityCheckResult = HttpUtils.performGet(serverURI, RTCHttpConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + serverVersion, userId,
									password, timeout, null, null).getJson();
							compatible = RtcJsonUtil.getBoolean(compatibilityCheckResult,
									RtcJsonUtil.JSON_PROP_COMPATIBLE);
							errorMessage = compatible != null && compatible == true ? null : errorMessage;
						}
					}
				}
				
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
				result.loginInfo = new RTCLoginInfo((Job<?, ?>)null, serverURI, credId,
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
				LOGGER.info(String.format("Checking build definition id %s in server %s", //$NON-NLS-1$
							buildDefinition, loginInfo.getServerUri()));
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

	public String getTrackBuildWorkItem() {
		return trackBuildWorkItem;
	}

	public boolean getUseWorkItems() {
		return this.useWorkItems;
	}

	public boolean getUseTrackBuildWorkItem() {
		return this.useTrackBuildWorkItem;
	}
	
	public void setBuildParameterAction(BuildParameterAction action) {
		this.buildParameterAction = action;
	}
	
	public BuildParameterAction getBuildParameterAction() {
		return buildParameterAction;
	}
	
	private static List<String> getChangeSetIdsFromActions(List<BuildParameterAction> actions) {
		List<String> changeSetIds = new ArrayList<>();
		for (BuildParameterAction action : actions) {
			for (String changeSetId : action.getChangeSetIds()) {
				changeSetIds.add(changeSetId);
			}
		}
		return changeSetIds;
	}
	
	private static List<String> getChangeSetIdsFromCsData(List<ChangeSetData> csDataList) {
		List<String> changeSetIds = new ArrayList<>();
		for (ChangeSetData csData : csDataList) {
			changeSetIds.add(csData.getChangeSetId());
		}
		return changeSetIds;
	}
	
	private List<ChangeSetData> getCsData(Run<?,?> run, TaskListener listener) {
		List<ChangeSetData> csData = GitScmUtils.getIncludedCommits(run, listener.getLogger());
		// Filter commits already handled by other BuildResultActions
		return filterCsData(run, csData);
	}
	
	private List<ChangeSetData> filterCsData(Run<?,?> run, List<ChangeSetData> csDataList) {
		LOGGER.finest("RTCGitBuilder.filterCsData : begin"); //$NON-NLS-1$
		// For every build result action that we have in the build (except the last one), 
		// check whether changeSetIds from csData is part of that Build Result Action.
		// If yes, then don't add it to the filtered list. Otherwise add it.
		List<ChangeSetData> filteredCsData = new ArrayList<>();
		Set<String> changeSetIdsFromActions = new HashSet<>(getChangeSetIdsFromActions(
							run.getActions(BuildParameterAction.class)));
		for (ChangeSetData csData : csDataList) {
			if (!changeSetIdsFromActions.contains(csData.getChangeSetId())) {
				filteredCsData.add(csData);
			}
		}
		return filteredCsData;
	}
}
