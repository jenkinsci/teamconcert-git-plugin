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

import java.security.GeneralSecurityException;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class RtcJsonUtil {

	// JSON fields for #testConnection
	private static final String JSON_PROP_COMPATIBLE = "compatible"; //$NON-NLS-1$
	private static final String JSON_PROP_IS_JTS = "isJTS"; //$NON-NLS-1$
	private static final String JSON_PROP_SERVER_VERSION = "serverVersion"; //$NON-NLS-1$
	private static final String JSON_PROP_MESSAGE = "message"; //$NON-NLS-1$
	private static final String JSON_PROP_URI = "uri"; //$NON-NLS-1$
	
	public static String getReturnValue(String jsonStr) {
		return getReturnValue(JSONSerializer.toJSON(jsonStr));
	}

	public static String getReturnValue(JSON json) {
		JSONObject result = getResultJson(json);
		if (result != null) {
			Object value = result.get("value");
			if (value instanceof String) {
				return ((String) value);
			}
		}
		return null;
	}

	public static String[] getReturnValues(JSON json) {
		JSONObject result = getResultJson(json);
		if (result != null) {
			Object value = result.get("values");
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
			Object returnValue = ((JSONObject) responseObj).get("returnValue");
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
			Object soapObj = ((JSONObject) json).get("soapenv:Body");
			if (soapObj instanceof JSONObject) {
				Object responseObj = ((JSONObject) soapObj).get("response");
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
			return ((JSONObject) json).getBoolean(fieldName);
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
		if ((isJTS != null) && (isJTS == true)) {
			errorMessage = "Messages.RTCFacadeFacade_client_not_allowed_to_connect_to_JTS()";
		}

		Boolean compatible = getBoolean(compatibilityCheckResult,
				JSON_PROP_COMPATIBLE);
		if (compatible == null) {
			errorMessage = "Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service()";
		} else if (compatible == false) {
			String upgradeURI = getString(compatibilityCheckResult,
					JSON_PROP_URI);
			String upgradeMessage = getString(compatibilityCheckResult,
					JSON_PROP_MESSAGE);
			String serverVersion = getString(compatibilityCheckResult,
					JSON_PROP_SERVER_VERSION);
			if ((upgradeURI == null) || (upgradeMessage == null)
					|| (serverVersion == null)) {
				errorMessage = "Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service()";
			} else {
				errorMessage = "Messages.RTCFacadeFacade_incompatible2(serverVersion)";
			}
		}
		return errorMessage;
	}

}
