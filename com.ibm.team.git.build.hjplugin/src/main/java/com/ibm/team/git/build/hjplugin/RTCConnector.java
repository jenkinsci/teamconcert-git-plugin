/******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2011, 2015. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 ******************************************************************************/

package com.ibm.team.git.build.hjplugin;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicNameValuePair;

import com.ibm.team.git.build.hjplugin.http.HttpUtils;
import com.ibm.team.git.build.hjplugin.http.HttpUtils.RtcHttpResult;
import com.ibm.team.git.build.hjplugin.scm.ChangeSetData;
import com.ibm.team.git.build.hjplugin.util.RTCHttpConstants;

public class RTCConnector {

	private final String serverURI;
	private final String buildDefinition;
	private final String workItemUpdateType;
	private final boolean useBuildDefinition;
	private final String jenkinsRootURI;
	private final String jenkinsBuildURI;
	private final String buildName;
	private final String buildResultUUID;
	private final String userId;
	private final String password;
	private final int timeout;

	private ParameterHelper pHelper = new ParameterHelper();

	public RTCConnector(String serverURI, String userId, String password,
			int timeout, String buildDefinition, String workItemUpdateType,
			boolean useBuildDefinition, String buildResultUUID,
			String jenkinsRootURI, String jenkinsBuildURI, String buildName) {
		this.serverURI = RTCUtils.formatURI(serverURI);
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildDefinition = buildDefinition;
		this.workItemUpdateType = workItemUpdateType;
		this.useBuildDefinition = useBuildDefinition;
		this.buildResultUUID = buildResultUUID;
		this.jenkinsBuildURI = jenkinsBuildURI;
		this.jenkinsRootURI = jenkinsRootURI;
		this.buildName = buildName;
	}

	public void updateWorkItem(PrintStream out, String trackbuildWi,
			String comment) {
		if (!RTCUtils.IsNullOrEmpty(trackbuildWi)
				&& !RTCUtils.IsNullOrEmpty(comment)) {
			try {
				HttpClientContext httpContext = HttpUtils.createHttpContext();
				HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
				HttpUtils.performPost(serverURI,
						RTCHttpConstants.SERVICE_UPDATE_RTC_WORKITEM, userId,
						password, timeout, pHelper.getUpdateWorkItemParmData(
								trackbuildWi, comment), null, httpContext);
			} catch (Exception e) {
				RTCUtils.LogMessage(out, Messages.Error_UpdatingWorkItem(trackbuildWi));
				RTCUtils.LogMessage(out, e.getMessage());
			}
		}

	}

	public void updateRTCBuild(PrintStream out) {
		if (buildResultUUID == null) {
			return;
		}
		try {
			HttpClientContext httpContext = HttpUtils.createHttpContext();
			HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);

			HttpUtils.performPost(serverURI,
					RTCHttpConstants.SERVICE_UPDATE_RTC_BUILD_LINK, userId,
					password, timeout, pHelper.getUpdateRTCBuildParmData(),
					null, httpContext);
		} catch (Exception e) {
			RTCUtils.LogMessage(out, Messages.Error_UpdatingBuildResult());
			RTCUtils.LogMessage(out, e.getMessage());

		}
	}

	public void completeBuild(PrintStream out, int status) {
		if (buildResultUUID == null) {
			return;
		}
		try {
			HttpClientContext httpContext = HttpUtils.createHttpContext();
			HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
			HttpUtils.performPost(serverURI,
					RTCHttpConstants.SERVICE_COMPLETE_RTC_BUILD_LINK, userId,
					password, timeout,
					pHelper.getCompleteRTCBuildParmData(status), null, httpContext);
		} catch (Exception e) {
			RTCUtils.LogMessage(out, Messages.Error_UpdatingBuildResultComplete());
			RTCUtils.LogMessage(out, e.getMessage());
		}
	}

	public void publishCommitData(PrintStream out, List<ChangeSetData> csData) {
		if (csData == null || csData.size() == 0) {
			return;
		}
		List<NameValuePair> params = pHelper.getPublishCommitParmData(csData);
		try {
			HttpClientContext httpContext = HttpUtils.createHttpContext();
			HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
			HttpUtils.performPost(serverURI,
					RTCHttpConstants.SERVICE_GITBUILD_LINK, userId, password,
					timeout, params, null, httpContext);
		} catch (Exception e) {
			RTCUtils.LogMessage(out, Messages.Error_CreatingWorkItemLinks());
			RTCUtils.LogMessage(out, e.getMessage());
		}
	}

	public String createRTCBuild(PrintStream out) {
		List<NameValuePair> params = pHelper.getCreateBuildParmData();
		RtcHttpResult result;
		if (useBuildDefinition && buildDefinition != null) {
			try {
				if (isValidBuildDefintion(serverURI, buildDefinition, userId,
						password, timeout)) {
					HttpClientContext httpContext = HttpUtils.createHttpContext();
					HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
					result = HttpUtils.performPost(serverURI,
							RTCHttpConstants.SERVICE_CREATE_RTC_BUILD_LINK,
							userId, password, timeout, params, null, httpContext);
					return result.getResultAsString();
				}
			} catch (Exception e) {
				RTCUtils.LogMessage(out, Messages.Error_CreatingRTCBuild());
				RTCUtils.LogMessage(out, e.getMessage());
			}
		}
		return null;
	}

	public static boolean isValidBuildDefintion(String serverURI,
			String buildDefinition, String userId, String password, int timeout)
			throws InvalidCredentialsException, IOException,
			GeneralSecurityException {
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(
				RTCHttpConstants.PARAM_RTC_BUILD_DEFINITION_ID, buildDefinition));
		RtcHttpResult result = null;
		HttpClientContext httpContext = HttpUtils.createHttpContext();
		HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
		result = HttpUtils.performPost(serverURI,
				RTCHttpConstants.SERVICE_VALIDATE_RTC_BUILD_LINK, userId,
				password, timeout, params, null, httpContext);
		return result.getResultAsBoolean();
	}

	public static String validateAndGetWorkItem(String serverURI,
			String workitemid, String userId, String password, int timeout)
			throws InvalidCredentialsException, IOException,
			GeneralSecurityException {
		if (RTCUtils.IsNullOrEmpty(workitemid)) {
			return null;
		}
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(
				RTCHttpConstants.PARAM_RTC_WORK_ITEM_ID, workitemid));
		RtcHttpResult result = null;
		HttpClientContext httpContext = HttpUtils.createHttpContext();
		HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
		result = HttpUtils.performPost(serverURI,
				RTCHttpConstants.SERVICE_GET_RTC_WORKITEM, userId, password,
				timeout, params, null, httpContext);
		return result.getResultAsString();
	}
	
	public static String[] getWorkItemDetails(String serverURI,
			String[] workitems, String userId, String password, int timeout)
			throws InvalidCredentialsException, IOException,
			GeneralSecurityException {
		if (workitems == null || workitems.length <= 0) {
			return null;
		}
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (String wi : workitems) {
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_WORK_ITEM_ID, wi));
		}
		RtcHttpResult result = null;
		HttpClientContext httpContext = HttpUtils.createHttpContext();
		HttpUtils.validateCredentials(serverURI, userId, password, timeout, httpContext);
		result = HttpUtils.performPost(serverURI,
				RTCHttpConstants.SERVICE_GET_RTC_WORKITEM_DETAILS, userId, password,
				timeout, params, null, httpContext);
		return result.getResultAsStringArray();
	}

	private class ParameterHelper {

		public List<NameValuePair> getCreateBuildParmData() {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_NAME, buildName));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_URL, RTCUtils
							.makeFullURL(jenkinsRootURI, jenkinsBuildURI)));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_DEFINITION_ID,
					buildDefinition));
			return params;
		}

		private List<NameValuePair> getPublishCommitParmData(
				List<ChangeSetData> csData) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_NAME, buildName));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_URL, RTCUtils
							.makeFullURL(jenkinsRootURI, jenkinsBuildURI)));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_INTEGRATION, Boolean
							.toString(useBuildDefinition)));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_DEFINITION_ID,
					buildDefinition));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_RESULT_UUID,
					buildResultUUID));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_WORKITEM_INTEGRATION, RTCUtils
							.getWiMode(workItemUpdateType)));
			fillWorkItems(csData, params);
			return params;
		}

		private void fillWorkItems(List<ChangeSetData> csData,
				List<NameValuePair> params) {
			String[] workitems = RTCUtils.getAllWorkItems(csData);
			if (workitems != null) {
				for (String wi : workitems) {
					params.add(new BasicNameValuePair(
							RTCHttpConstants.PARAM_RTC_WORK_ITEM_ID, wi));
				}
			}
		}
		
		private List<NameValuePair> getUpdateWorkItemParmData(
				String trackbuildWi, String comment) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_WORK_ITEM_ID, trackbuildWi));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_WORK_ITEM_COMMENT, comment));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_RESULT_UUID,
					buildResultUUID));
			return params;
		}

		private List<NameValuePair> getUpdateRTCBuildParmData() {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_NAME, buildName));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_URL, RTCUtils
							.makeFullURL(jenkinsRootURI, jenkinsBuildURI)));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_RESULT_UUID,
					buildResultUUID));
			return params;
		}

		private List<NameValuePair> getCompleteRTCBuildParmData(int status) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_RTC_BUILD_RESULT_UUID,
					buildResultUUID));
			params.add(new BasicNameValuePair(
					RTCHttpConstants.PARAM_JENKINS_BUILD_STATUS, Integer
							.toString(status)));
			return params;
		}
	}
}