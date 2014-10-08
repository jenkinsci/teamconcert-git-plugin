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

import hudson.Extension;
import hudson.MarkupText;
import hudson.MarkupText.SubText;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


@Extension
public class RTCGitChangelogAnnotator extends ChangeLogAnnotator {

	private static final String REGEX = "\\b[0-9.]*[0-9]\\b";
	private static final Logger LOGGER = Logger
			.getLogger(RTCGitChangelogAnnotator.class.getName());

	@Override
	public void annotate(AbstractBuild<?, ?> build, Entry change,
			MarkupText text) {

		BuildParameterAction bAction = build
				.getAction(BuildParameterAction.class);

		if (bAction != null && bAction.shouldAnnotate()
				&& bAction.getRtcURL() != null) {
			LOGGER.log(Level.FINE, "In RTC annotate");
			HashMap<String, String> wiMap = getWorkitemsInfo(build, bAction);
			annotateWithRtc(change, text, wiMap, bAction.getRtcURL());
		}
	}

	private HashMap<String, String> getWorkitemsInfo(AbstractBuild<?, ?> build,
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

			RTCLoginInfo loginInfo = new RTCLoginInfo(build,
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
		}
		return null;
	}

	private void annotateWithRtc(Entry change, MarkupText text,
			HashMap<String, String> wiMap, String rtcURL) {
		String tStr = text.getText();
		Pattern pattern = null;
		try {
			pattern = Pattern.compile(REGEX);
		} catch (PatternSyntaxException e) {
			LOGGER.log(Level.WARNING, "Cannot compile pattern: {0}", REGEX);
			return;
		}

		for (SubText token : text.findTokens(pattern)) {
			Integer key = null;
			try {
				key = getWorkItemId(token);
				if (!RTCUtils.matchesKey(tStr.substring(0, token.start()))) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}
			String toolTip = getWorkItemToolTip(wiMap, key.toString());
			if (RTCUtils.IsNullOrEmpty(toolTip)) {
				token.surroundWith(String.format("<a href='%s'>",
						RTCUtils.getWorkItemUrl(rtcURL, key.toString())),
						"</a>");
			} else {
				token.surroundWith(String.format("<a href='%s' tooltip='%s'>",
						RTCUtils.getWorkItemUrl(rtcURL, key.toString()), toolTip),
						"</a>");
			}
		}
	}

	private String getWorkItemToolTip(HashMap<String, String> wiMap, String key) {
		if(wiMap != null && !RTCUtils.IsNullOrEmpty(key)) {
			return wiMap.get(key);
		}
		return null;
	}

	private static int getWorkItemId(SubText token) {
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
