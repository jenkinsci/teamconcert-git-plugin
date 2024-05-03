/*******************************************************************************
 * Copyright (c) 2014, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.git.build.hjplugin;

import hudson.Extension;
import hudson.Functions;
import hudson.MarkupText;
import hudson.MarkupText.SubText;
import hudson.model.Run;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.ibm.team.git.build.hjplugin.http.HttpUtils;
import com.ibm.team.git.build.hjplugin.scm.ChangeSetData;
import com.ibm.team.git.build.hjplugin.scm.GitScmUtils;


/**
 * Annotates the change log with RTC work item information if a work item
 * reference is found.
 * 
 * Valid work item keywords are bug, task, defect, rtcwi, workitem, work item,
 * work-item, item, issue, feature, ewm, story, epic, testplan, test plan,
 * test-plan, ccb and wi.
 *
 */
@Extension
public class RTCGitChangelogAnnotator extends ChangeLogAnnotator {

	private static final String REGEX = "\\b[0-9.]*[0-9]\\b"; //$NON-NLS-1$
	private static final Logger LOGGER = Logger
			.getLogger(RTCGitChangelogAnnotator.class.getName());

	@Override
	public void annotate(Run<?, ?> build, Entry change,
			MarkupText text) {
		LOGGER.log(Level.FINEST, "RTCGitChangelogAnnotator.annotate : Enter"); //$NON-NLS-1$
		// There could be multiple BuildResultActions. We could state 
		// that Annotate change log option should be used only in the last RTCGitbuilder
		// In that case, we have iterate over multiple actions and stop at the one 
		// that has the shouldAnnotate() option set.
		
		// Also each bAction is tied to a particular ChangeLog entry.
		// The entry should be annotated only for that change log entry.
		// Or take the last bAction that has "annotate change log enabled 
		// and annotate work items using that, since we know that it will include
		// work item info from all the previous change log sets (of multiple git checkouts).
		
		
		// The above approaches don't work if each TC Plugin connects to a different RTC server (which is possible)
		// So the way to handle this correctly is as follows
		
	    // 1. Each TC Git Plugin one handles the ChangeLogSet that were not previously handled
		// 2. In this method, we need to find the BAction that handled a specific ChangelogSet 
		// and then annotate the changelog entry that is passed to this method.
		if (!GitScmUtils.isGitScmChangeLogSetEntry(change)) {
			LOGGER.log(Level.FINE, "Not a git changeset"); //$NON-NLS-1$
			return;
		}

		ChangeSetData csData = GitScmUtils.getChangeSetData(change, null);
		if (csData == null) {
			LOGGER.log(Level.FINE, "No change set data"); //$NON-NLS-1$
			return;
		}

		LOGGER.log(Level.FINE, "Checking all actions"); //$NON-NLS-1$
		List<BuildParameterAction> bActions = build
				.getActions(BuildParameterAction.class);

		BuildParameterAction bActionToUse = null;
		for (BuildParameterAction bAction : bActions) {
			if (bAction != null) {
				for (String changeSetId : bAction.getChangeSetIds()) {
					if (csData.getChangeSetId().equals(changeSetId)) {
						bActionToUse = bAction;
						break;
					}
				}
			}
			if (bActionToUse != null) {
				break;
			}
		}
		LOGGER.log(Level.FINE, "Found bAction to annotate"); //$NON-NLS-1$

		// For older BuildParameterAction's, there is no changeSetId array,
		// So default to using the last bAction
		// One other scenario is where this plugin was called before a 
		// Git checkout and that is why we didn't record the change set.
		// That is a rare scenario which is being ignored.
		if (bActionToUse == null) {
			LOGGER.log(Level.FINE, "No Build Action to use. Getting the first one."); //$NON-NLS-1$
			bActionToUse = build.getAction(BuildParameterAction.class);
		}
		if (bActionToUse != null && bActionToUse.shouldAnnotate()
				&& bActionToUse.getRtcURL() != null) {
			HashMap<String, String> wiMap = getWorkitemsInfo(build, bActionToUse);
			annotateWithRtc(change, text, wiMap, bActionToUse.getRtcURL());
		} else {
			LOGGER.log(Level.FINE, "Found bAction to annotate but bAction annotate is false" + //$NON-NLS-1$
								Boolean.toString(bActionToUse.shouldAnnotate()));
		}
	}

	private static HashMap<String, String> getWorkitemsInfo(Run<?, ?> build,
			BuildParameterAction bAction) {
		if (bAction == null) {
			return null;
		}
		try {
			String[] workitems = bAction.getWorkitems();
			String creds = bAction.getCredentialsId();
			if (workitems == null || RTCUtils.IsNullOrEmpty(creds)
					|| RTCUtils.IsNullOrEmpty(bAction.getRtcURL())) {
				return null;
			}

			RTCLoginInfo loginInfo = new RTCLoginInfo(build.getParent(),
					bAction.getRtcURL(), creds, bAction.getTimeout());
			String[] wiDetails = RTCConnector.getWorkItemDetails(
					loginInfo.getServerUri(), workitems, loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout());
			if (wiDetails != null) {
				HashMap<String, String> wiMap = new HashMap<String, String>();
				for (String wiInfo : wiDetails) {
					if (!RTCUtils.IsNullOrEmpty(wiInfo)) {
						int index = wiInfo.indexOf(':');
						if (index != -1) {
							String wi = wiInfo.substring(0, index).trim();
							if (!wiMap.containsKey(wi)) {
								wiMap.put(wi, wiInfo);
							}
						}
					}
				}
				return wiMap;
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "getWorkitemsInfo: Error getting work items from bAction", e); //$NON-NLS-1$
		}
		return null;
	}

	private static void annotateWithRtc(Entry change, MarkupText text,
			HashMap<String, String> wiMap, String rtcURL) {
		String tStr = text.getText();
		Pattern pattern = null;
		try {
			pattern = Pattern.compile(REGEX);
		} catch (PatternSyntaxException e) {
			LOGGER.log(Level.SEVERE, String.format("Cannot compile pattern: %s", REGEX), e); //$NON-NLS-1$
			return;
		}

		for (SubText token : text.findTokens(pattern)) {
			Integer key = null;
			try {
				key = getWorkItemId(token);
				if (!RTCUtils.matchesKey(tStr.substring(0, token.start() - 1))) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}
			String toolTip = getWorkItemToolTip(wiMap, key.toString());
			String sanitizedRtcURL = Functions.htmlAttributeEscape(rtcURL);
			if ((sanitizedRtcURL.startsWith(HttpUtils.SCHEME_HTTP) || sanitizedRtcURL.startsWith(HttpUtils.SCHEME_HTTPS))) {
				if (RTCUtils.IsNullOrEmpty(toolTip)) {
					token.surroundWith(String.format("<a href='%s'>", //$NON-NLS-1$
					RTCUtils.getWorkItemUrl(sanitizedRtcURL, key.toString())), "</a>"); //$NON-NLS-1$
				} else {
					String sanitizedToolTip = Functions.htmlAttributeEscape(toolTip);
					token.surroundWith(String.format("<a href='%s' tooltip='%s'>", //$NON-NLS-1$
					RTCUtils.getWorkItemUrl(sanitizedRtcURL, key.toString()), sanitizedToolTip), "</a>"); //$NON-NLS-1$
				}
			}
		}
	}

	private static String getWorkItemToolTip(HashMap<String, String> wiMap, String key) {
		if(wiMap != null && !RTCUtils.IsNullOrEmpty(key)) {
			return wiMap.get(key);
		}
		return null;
	}

	private static Integer getWorkItemId(SubText token) {
		String id = null;
		for (int i = 0;; i++) {
			id = token.group(i);

			try {
				return Integer.valueOf(id);
			} catch (NumberFormatException e) {
				continue;
			}
		}
	}
}
