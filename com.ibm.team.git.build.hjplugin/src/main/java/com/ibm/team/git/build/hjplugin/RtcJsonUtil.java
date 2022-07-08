/*******************************************************************************
 * Copyright (c) 2014, 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.git.build.hjplugin;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Util;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class RtcJsonUtil {
	
	private static final Logger LOGGER = Logger.getLogger(RtcJsonUtil.class.getName());

	// JSON fields for #testConnection
	public static final String JSON_PROP_COMPATIBLE = "compatible"; //$NON-NLS-1$
	private static final String JSON_PROP_IS_JTS = "isJTS"; //$NON-NLS-1$
	public static final String JSON_PROP_SERVER_VERSION = "serverVersion"; //$NON-NLS-1$
	private static final String JSON_PROP_MESSAGE = "message"; //$NON-NLS-1$
	private static final String JSON_PROP_URI = "uri"; //$NON-NLS-1$
	public static final String HTTP_ERROR_MSG_VERSION_INCOMPATIBLE = "Messages.RTCFacadeFacade_incompatible2(serverVersion)"; //$NON-NLS-1$
	
	public static String getReturnValue(String jsonStr) {
		return getReturnValue(JSONSerializer.toJSON(jsonStr));
	}

	public static String getReturnValue(JSON json) {
		JSONObject result = getResultJson(json);
		if (result != null) {
			Object value = result.get("value"); //$NON-NLS-1$
			if (value instanceof String) {
				return ((String) value);
			}
		}
		return null;
	}

	public static String[] getReturnValues(JSON json) {
		JSONObject result = getResultJson(json);
		if (result != null) {
			Object value = result.get("values"); //$NON-NLS-1$
			if (value instanceof JSONArray) {
				JSONArray rArray = ((JSONArray) value);
				if (rArray != null) {
					String[] values = new String[rArray.size()];
					for (int i = 0; i < rArray.size(); i++) {
						values[i] = rArray.getString(i);
					}
					return values;
				}
			}
		}
		return null;
	}

	public static JSONObject getResultJson(String jsonStr) {
		return getResultJson(JSONSerializer.toJSON(jsonStr));
	}

	public static JSONObject getResultJson(JSON json) {
		JSONObject responseObj = getResponseJson(json);
		if (responseObj instanceof JSONObject) {
			Object returnValue = ((JSONObject) responseObj).get("returnValue"); //$NON-NLS-1$
			if (returnValue instanceof JSONObject) {
				return ((JSONObject) returnValue);
			}
		}
		return null;
	}

	public static JSONObject getResponseJson(String jsonStr) {
		return getResponseJson(JSONSerializer.toJSON(jsonStr));
	}

	public static JSONObject getResponseJson(JSON json) {
		if (json instanceof JSONObject) {
			Object soapObj = ((JSONObject) json).get("soapenv:Body"); //$NON-NLS-1$
			if (soapObj instanceof JSONObject) {
				Object responseObj = ((JSONObject) soapObj).get("response"); //$NON-NLS-1$
				if (responseObj instanceof JSONObject) {
					return ((JSONObject) responseObj);
				}
			}
		}
		return null;
	}

	public static Boolean getBoolean(JSON json, String fieldName) {
		if (json instanceof JSONObject
				&& ((JSONObject) json).containsKey(fieldName)) {
			return Boolean.valueOf(((JSONObject) json).getBoolean(fieldName)); 
		}
		return null;
	}

	public static String getString(JSON json, String fieldName) {
		if (json instanceof JSONObject) {
			Object result = ((JSONObject) json).get(fieldName);
			if (result instanceof String) {
				return (String) result;
			}
		}
		return null;
	}

	public static String ensureCompatability(JSON compatibilityCheckResult)
			throws GeneralSecurityException {
		String errorMessage = null;

		// Validate that the server version is sufficient
		Boolean isJTS = getBoolean(compatibilityCheckResult, JSON_PROP_IS_JTS);

		// isJTS might be null in 3.0 RC0 and earlier, because earlier versions
		// of
		// the VersionCompatibilityRestService did not include this
		// functionality.
		// If null, don't throw an error in this block, but instead fall through
		// handle as a version mismatch below
		if ((isJTS != null) && (isJTS == Boolean.TRUE)) {
			errorMessage = "Messages.RTCFacadeFacade_client_not_allowed_to_connect_to_JTS()"; //$NON-NLS-1$
		}

		Boolean compatible = getBoolean(compatibilityCheckResult,
				JSON_PROP_COMPATIBLE);
		if (compatible == null) {
			errorMessage = "Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service()"; //$NON-NLS-1$
		} else if (compatible == Boolean.FALSE) {
			String upgradeURI = getString(compatibilityCheckResult,
					JSON_PROP_URI);
			String upgradeMessage = getString(compatibilityCheckResult,
					JSON_PROP_MESSAGE);
			String serverVersion = getString(compatibilityCheckResult,
					JSON_PROP_SERVER_VERSION);
			if ((upgradeURI == null) || (upgradeMessage == null)
					|| (serverVersion == null)) { 
				errorMessage = "Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service()"; //$NON-NLS-1$
			} else {
				errorMessage = HTTP_ERROR_MSG_VERSION_INCOMPATIBLE; //$NON-NLS-1$
			}
		}
		return errorMessage;
	}
	
	/**
	 * This extracts the server version without the milestone.
	 * 
	 * @param serverVersion
	 * @return the extracted server version without milestone identifiers (S1,M2,RC1 etc.,)
	 * 
	 * Note: This is protected for testing purposes
	 */
	protected static String extractServerVersionWithoutMilestone(String serverVersion) {
		Pattern p = Pattern.compile("\\d\\.\\d(\\.\\d(\\.\\d)?)?");
		Matcher m = p.matcher(serverVersion);
		while (m.find()) {
			return m.group();
		}
		return null;
	}
	
	/**
	 * Check if the serverVersion without milestone is greater than or equal to the minimum server 
	 * version.
	 * 
	 * @param serverVersionWithoutMilestone -The server version without milestone
	 * @param minimumServerVersion - The minimum server version.
	 * @return - <code>true</code> if the server version is greater than or equal to minimum server 
	 * 			version, <code>false</code> otherwise. 
	 * 
	 * Note: This is protected for testing purposes
	 */
	protected static boolean isServerVersionEqualOrHigher(String serverVersionWithoutMilestone, 
						String minimumServerVersion) {
		if (Util.fixEmptyAndTrim(serverVersionWithoutMilestone) == null 
				|| Util.fixEmptyAndTrim(minimumServerVersion) == null) {
			// This indicates invalid input but we already log the fact that 
			// serverVersionWithoutMileStone was null before making this call
			return false;
		}
		String [] serverFields = serverVersionWithoutMilestone.split("\\.");
		String [] minimumServerFields = minimumServerVersion.split("\\.");
		LOGGER.finest("EWM Client fields "  + Arrays.toString(minimumServerFields) +
				"EWM Server fields " + Arrays.toString(serverFields));
		Boolean isEqualOrGreater = null;
		int i = 0, j=0;
		while ( i < serverFields.length  && j < minimumServerFields.length) {
			int sf = (int)serverFields[i].charAt(0);
			int cf = (int)minimumServerFields[j].charAt(0);
			if (sf > cf) {
				isEqualOrGreater = Boolean.TRUE;
				break;
			} else if (sf < cf){
				isEqualOrGreater = Boolean.FALSE;
				break;
			} else {
				// Continue to the next digit
				i++;
				j++;
			}
		}
		if (isEqualOrGreater == null) {
			// The scenario is when the server version is a prefix of the client version 
			// and client version is greater than the server 
			// (or)
			// The client version is a prefix of the server version and server version is greater 
			// than the client 
			// (or)
			// The server and client are of the same version
			if (serverFields.length < minimumServerFields.length) {
				// Clearly the server version is smaller than the client version 
				isEqualOrGreater = Boolean.FALSE;
			}
			else if (serverFields.length > minimumServerFields.length) {
				// The server version is greater than minimum server version
				isEqualOrGreater = Boolean.TRUE;
			} else {
				// Both strings are of equal length and server version is same as minimum 
				// server version
				isEqualOrGreater = Boolean.TRUE;
			}
		}
		LOGGER.finest("Is server version greater than client ? " + isEqualOrGreater);
		return isEqualOrGreater;
	}

}
