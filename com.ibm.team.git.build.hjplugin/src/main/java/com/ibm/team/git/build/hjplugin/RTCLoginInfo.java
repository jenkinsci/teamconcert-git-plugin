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

import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.Util;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.util.FormValidation;

/**
 * Represents login information to connect to RTC
 */
public class RTCLoginInfo {
	private static final Logger LOGGER = Logger.getLogger(RTCGitBuilder.class.getName());
	
	private String serverUri;
	private String userId;
	private String password;
	private int timeout;
	
	/**
	 * Constructor 
	 * 
	 * @param job
	 * @param serverUri
	 * @param credentialsId
	 * @param timeout
	 * @throws InvalidCredentialsException
	 */
	public RTCLoginInfo(Job<?, ?> job, String serverUri, String credentialsId, int timeout) throws InvalidCredentialsException {
		credentialsId = Util.fixEmptyAndTrim(credentialsId);

		if (credentialsId != null) {
			// figure out userid & password from the credentials
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Looking up credentials for " +  //$NON-NLS-1$
						"credentialId=\"" + credentialsId + //$NON-NLS-1$
						"\" serverURI=\"" + serverUri +  //$NON-NLS-1$
						"\" project=" + (job == null ? "null" : "\"" + job.getFullDisplayName() + "\"")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$ 
			}

			
			
			List<StandardUsernamePasswordCredentials> allMatchingCredentials = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, job , ACL.SYSTEM,
							URIRequirementBuilder.fromUri(serverUri).build());
			StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(allMatchingCredentials, 
							CredentialsMatchers.withId(credentialsId));
			if (credentials != null) {
				this.userId = credentials.getUsername();
				this.password = credentials.getPassword().getPlainText();
			} else {
				throw new InvalidCredentialsException(Messages.RTCLoginInfo_creds_unresolvable());
			}
			
		} else {
			throw new InvalidCredentialsException(Messages.RTCLoginInfo_supply_credenmtials());
		}
		this.serverUri = serverUri;
		this.timeout = timeout;
	}

	public String getServerUri() {
		return serverUri;
	}

	public String getUserId() {
		return userId;
	}

	public String getPassword() {
		return password;
	}

	public int getTimeout() {
		return timeout;
	}
	
	/**
	 * Validate the given credentials against the server
	 * 
	 * @param credentialsId
	 * @param timeout
	 * @return
	 */
	public static FormValidation basicValidate(String credentialsId, String timeout) {
		// validate the timeout value
		FormValidation result = validateTimeout(timeout);
		if (result.kind.equals(FormValidation.Kind.ERROR)) {
			return result;
		}
		// validate credentials
		return validateCredentials(credentialsId);
	}
		
	/**
	 * Validate the timeout
	 * Must be a positive integer
	 * 
	 * @param timeout Timeout to use when connecting to a RTC server
	 * @return An error if the input is valid. 
	 */
	public static FormValidation validateTimeout(String timeout) {
		timeout = Util.fixEmptyAndTrim(timeout);
		if (StringUtils.isEmpty(timeout)) {
			LOGGER.finer("timeout value missing"); //$NON-NLS-1$
			return FormValidation.error(Messages.RTC_timeout_required());
		}
		return FormValidation.validatePositiveInteger(timeout);
	}

	/**
	 * Validate the credentials id 
	 * 
	 * @param credentialsId 
	 * @return 
	 * 
	 */
	public static FormValidation validateCredentials(String credentialsId) {
		credentialsId = Util.fixEmptyAndTrim(credentialsId);
		if (credentialsId == null) {
			return FormValidation.error(Messages.RTC_credentials_required());
		}
		return FormValidation.ok();
	}
}
