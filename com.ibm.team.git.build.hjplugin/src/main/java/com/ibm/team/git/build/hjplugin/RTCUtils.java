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

import hudson.Util;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.Cause.UserCause;
import hudson.model.Cause.UserIdCause;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.context.SecurityContextHolder;
import com.ibm.team.git.build.hjplugin.scm.ChangeSetData;
import com.ibm.team.git.build.hjplugin.util.RTCHttpConstants;

/**
 *
 */
public class RTCUtils {

	private static final Logger LOGGER = Logger.getLogger(RTCUtils.class
			.getName());

	private static final String USER_ID_CAUSE_CLASS_NAME = "hudson.model.Cause$UserIdCause"; //$NON-NLS-1$
	private static String BUG_KEY = "bug"; //$NON-NLS-1$
	private static String TASK_KEY = "task"; //$NON-NLS-1$
	private static String DEFECT_KEY = "defect"; //$NON-NLS-1$
	private static String WORKITEM_KEY = "workitem"; //$NON-NLS-1$
	private static String WORKITEM_KEY_2 = "work item"; //$NON-NLS-1$
	private static String RTCWI_KEY = "rtcwi"; //$NON-NLS-1$

	private static final String WI_MODE_COMMENTS = "C"; //$NON-NLS-1$
	private static final String WI_MODE_LINKS = "L"; //$NON-NLS-1$
	private static final String WI_MODE_LINKS_AND_COMMEMNTS = "CL"; //$NON-NLS-1$

	private static final String WI_MODE_COMMENTS_KEY = "withComment"; //$NON-NLS-1$
	private static final String WI_MODE_LINKS_KEY = "withLink"; //$NON-NLS-1$
	private static final String WI_MODE_LINKS_AND_COMMEMNTS_KEY = "withCommentAndLink"; //$NON-NLS-1$

	private static List<String> KEYS = Arrays.asList(new String[] { BUG_KEY,
			TASK_KEY, DEFECT_KEY, WORKITEM_KEY, RTCWI_KEY });
	private static List<Character> PCHARS = Arrays.asList(new Character[] {
			',', ':', ';' });

	public static void LogMessage(PrintStream logger, String msg) {
		if (logger != null && !RTCUtils.IsNullOrEmpty(msg))
			logger.println(msg);
	}

	public static String getBuildURL(Run<?, ?> build,
			PrintStream logger) {
		String sUrl = build.getUrl();
		LOGGER.finer(String.format("Build Status URL : %s", sUrl)); //$NON-NLS-1$
		return sUrl;
	}

	public static String getBuildFullName(Run<?, ?> build,
			PrintStream logger) {
		String name = build.getFullDisplayName();
		LOGGER.finer(String.format("Build Display Name : %s", name)); //$NON-NLS-1$
		return name;
	}
	
	public static String getBuildShortName(Run<?, ?> build,
			PrintStream logger) {
		String name = String.format("#%s", Integer.toString(build.getNumber())); //$NON-NLS-1$
		LOGGER.finer(String.format("Build Short Name : %s", name)); //$NON-NLS-1$
		return name;
	}

	public static String getFullBuildURL(Run<?, ?> build,
			String jenkinsURI, PrintStream logger) {
		String rootUrl = getJenkinsRootURL(build, jenkinsURI, logger);
		if (RTCUtils.IsNullOrEmpty(rootUrl)) {
			LOGGER.warning("Jenkins root URI is null, provide a override URI in this plugin's configuration"); //$NON-NLS-1$
			return null;
		}
		String buildUrl = getBuildURL(build, logger);
		if (RTCUtils.IsNullOrEmpty(buildUrl)) {
			return null;
		}
		return formatURI(rootUrl) + buildUrl;
	}

	public static String getJenkinsRootURL(Run<?, ?> build,
			String jenkinsURI, PrintStream logger) {
		String rUrl = Hudson.getInstance().getRootUrl();
		if (RTCUtils.IsNullOrEmpty(rUrl)) {
			LOGGER.finer("root url is null, will use the url configured by the user"); //$NON-NLS-1$
			rUrl = Util.fixNull(jenkinsURI);
		}
		if (rUrl == null ) {
			LOGGER.warning("Jenkins root URI is null, provide a override URI in this plugin's configuration"); //$NON-NLS-1$
		} else {
			LOGGER.finer(String.format("Jenkins Root URL : %s", rUrl)); //$NON-NLS-1$
		}
		return rUrl;
	}

	public static String getBuildUser(Run<?, ?> build) {
		String userId = SecurityContextHolder.getContext().getAuthentication()
				.getName();
		User u = User.get(userId, false, Collections.emptyMap());
		String userName = null;
		if (u != null) {
			userName = u.getId();
		}
		if (u == null || userName == null) {
			userName = findBuildUserFromCause(build);
		}
		userName = Util.fixNull(userName);
		LOGGER.finer(String.format("Build User Id : %s", userName));
		return userName;
	}

	private static String findBuildUserFromCause(Run<?, ?> build) {
		// If build has been triggered form an upstream build, get UserCause
		// from there to set user build variables
		Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) build
				.getCause(Cause.UpstreamCause.class);
		if (upstreamCause != null) {
			Job<?, ?> job = Jenkins.getInstance().getItemByFullName(
					upstreamCause.getUpstreamProject(), Job.class);
			if (job != null) {
				Run<?, ?> upstream = job.getBuildByNumber(upstreamCause
						.getUpstreamBuild());
				if (upstream != null) {
					findBuildUserFromCause(upstream);
				}
			}
		}

		if (isClassExists(USER_ID_CAUSE_CLASS_NAME)) {
			/* Try to use UserIdCause to get & set jenkins user build variables */
			UserIdCause userIdCause = (UserIdCause) build
					.getCause(UserIdCause.class);
			if (userIdCause != null) {
				String userName = userIdCause.getUserName();
				return userName;
			}
		}

		UserCause userCause = (UserCause) build.getCause(UserCause.class);
		if (userCause != null) {
			String userName = userCause.getUserName();
			return userName;
		}

		return null;
	}

	public static String[] getAllWorkItems(List<ChangeSetData> csData) {
		Set<String> wiSet = new HashSet<String>();
		for (ChangeSetData commit : csData) {
			List<String> wItems = RTCUtils
					.getWorkItemsFromCommitComment(commit.comment);
			wiSet.addAll(wItems);
		}
		return wiSet.toArray(new String[0]);
	}

	public static List<String> getWorkItemsFromCommitComment(
			String commitComment) {
		List<String> wiList = getWorkItemList(commitComment);
		if (wiList != null) {
			List<String> tList = new ArrayList<String>(wiList.size());
			for (String wi : wiList) {
				try {
					Integer.parseInt(wi);
					tList.add(wi);
				} catch (NumberFormatException e) {
					// do nothing
				}
			}
			return tList;
		}
		return null;
	}

	private static List<String> getWorkItemList(String comment) {
		List<String> wiList = new ArrayList<String>();
		if (comment != null) {
		    LOGGER.info(" Comment before replacement " + comment); //$NON-NLS-1$
			comment = comment.replaceAll("^\"|\"$", ""); //$NON-NLS-1$ //$NON-NLS-2$
			comment = comment.replaceAll("[\t\n\r]", " "); //$NON-NLS-1$ //$NON-NLS-2$
			// Replace all "work item" with "workitem"
			// This enables us to support "work item" as a keyword
			comment = comment.replaceAll("[Ww][Oo][Rr][Kk] [Ii][Tt][Ee][Mm]", WORKITEM_KEY); //$NON-NLS-1$
		    LOGGER.info(" Comment after replacement " + comment); //$NON-NLS-1$

			String[] tokens = comment.split(" ");
			int len = tokens.length;
			for (int i = 0; i < len; i++) {
				if (KEYS.contains(tokens[i].toLowerCase()) && i + 1 < len) {
					String token = tokens[i + 1].trim();
					if (PCHARS.contains(token.charAt(token.length() - 1))) {
						token = token.substring(0, token.length() - 1);
					}
					wiList.add(token);
				}
			}
		}
		return wiList;
	}

	public static boolean isClassExists(String name) {
		try {
			Class.forName(name);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public static String getWiMode(String workItemUpdateType) {
		if (!IsNullOrEmpty(workItemUpdateType)) {
			if (workItemUpdateType.compareToIgnoreCase(WI_MODE_COMMENTS_KEY) == 0)
				return WI_MODE_COMMENTS;
			if (workItemUpdateType.compareToIgnoreCase(WI_MODE_LINKS_KEY) == 0)
				return WI_MODE_LINKS;
			if (workItemUpdateType
					.compareToIgnoreCase(WI_MODE_LINKS_AND_COMMEMNTS_KEY) == 0)
				return WI_MODE_LINKS_AND_COMMEMNTS;
		}
		return WI_MODE_COMMENTS;
	}

	public static String getWorkItemUrl(String rtcUrl, String workItemId) {
		if (rtcUrl != null && workItemId != null) {
			return String.format("%s%s%s", formatURI(rtcUrl),
					RTCHttpConstants.WI_FRAGMENT, workItemId);
		}
		return null;

	}

	public static boolean matchesKey(String str) {
		if (str != null) {
			str = str.trim().toLowerCase();
			return (str.endsWith(BUG_KEY) || str.endsWith(DEFECT_KEY)
					|| str.endsWith(TASK_KEY) || str.endsWith(WORKITEM_KEY) || str.endsWith(RTCWI_KEY)
					|| str.endsWith(WORKITEM_KEY_2));
		}
		return false;
	}

	public static boolean IsNullOrEmpty(String s) {
		return IsNullOrEmpty(s, true);
	}

	public static boolean IsNullOrEmpty(String s, boolean checkForEmptySpaces) {
		if ((s == null) || (s.length() <= 0)) {
			return true;
		}

		if (checkForEmptySpaces) {
			if (s.trim().length() <= 0) {
				return true;
			}
		}

		return false;
	}

	public static String getBuildStartedComment(PrintStream out,
			String jBuildUrl, String jBuildLabel, String userURL,
			String userName) {
		try {
			String userInfo = null;
			if (!RTCUtils.IsNullOrEmpty(userURL)
					&& !RTCUtils.IsNullOrEmpty(userName)) {
				userInfo = String.format("<a href=\"%s\">%s</a>", userURL,
						userName);
			} else if (!RTCUtils.IsNullOrEmpty(userName)) {
				userInfo = userName;
			} else {
				userInfo = Messages.WorkItem_BuildRequesterUnkown();
			}
			String buildInfo = "";
			if (!RTCUtils.IsNullOrEmpty(jBuildUrl)
					&& !RTCUtils.IsNullOrEmpty(jBuildLabel)) {
				buildInfo = String.format("<a href=\"%s\">%s</a>", jBuildUrl,
						jBuildLabel);
			} else if (!RTCUtils.IsNullOrEmpty(jBuildLabel)) {
				buildInfo = jBuildLabel;
			}
			return Messages.WorkItem_BuildStartStatusMsg(buildInfo, userInfo);
		} catch (Exception e) {
			LOGGER.finer("Error building build started comment message");
		}
		return null;
	}

	public static String getCompleteBuildComment(String jBuildUrl,
			String jBuildLabel, String status) {
		try {
			String buildInfo = "";
			if (!RTCUtils.IsNullOrEmpty(jBuildUrl)
					&& !RTCUtils.IsNullOrEmpty(jBuildLabel)) {
				buildInfo = String.format("<a href=\"%s\">%s</a>", jBuildUrl,
						jBuildLabel);
			} else if (!RTCUtils.IsNullOrEmpty(jBuildLabel)) {
				buildInfo = jBuildLabel;
			}
			if (status == null) {
				status = "";
			}
			return Messages.WorkItem_BuildCompleteStatusMsg(buildInfo, status);
		} catch (Exception e) {
			LOGGER.finer("Error building build completed comment message");
		}
		return null;
	}

	public static String formatURI(String uri) {
		if (uri != null && !uri.endsWith("/")) {
			uri = uri + "/";
		}
		return uri;
	}

	public static String makeFullURL(String rootURI, String suffix) {
		return formatURI(rootURI) + suffix;
	}

}
