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

package com.ibm.team.git.build.hjplugin.util;

public class RTCHttpConstants {
	/**
     * URI for checking compatibility of the RTC server. We need 5.0 or higher in order to
     * use the Rest services.
     */
    public static final String URI_COMPATIBILITY_CHECK = "versionCompatibility?clientVersion=5.0.2"; //$NON-NLS-1$
    
    //rtc service url's
	public static final String SERVICE_GITBUILD_LINK = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/LinkBuild"; //$NON-NLS-1$
	public static final String SERVICE_CREATE_RTC_BUILD_LINK = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/CreateBuild"; //$NON-NLS-1$
	public static final String SERVICE_VALIDATE_RTC_BUILD_LINK = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/ValidateBuildDefinition"; //$NON-NLS-1$
	public static final String SERVICE_GET_RTC_WORKITEM = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/ValidateWorkItem"; //$NON-NLS-1$
	public static final String SERVICE_COMPLETE_RTC_BUILD_LINK = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/CompleteBuild"; //$NON-NLS-1$
	public static final String SERVICE_UPDATE_RTC_BUILD_LINK = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/UpdateBuild"; //$NON-NLS-1$
	public static final String SERVICE_UPDATE_RTC_WORKITEM = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/UpdateWorkItem"; //$NON-NLS-1$
	public static final String SERVICE_GET_RTC_WORKITEM_DETAILS = "service/com.ibm.team.git.common.internal.IGitBuildLinkRestService/WorkItemDetails"; //$NON-NLS-1$
	
	//service parameters
	public static final String PARAM_JENKINS_BUILD_NAME = "jenkinsbuildname"; //$NON-NLS-1$
	public static final String PARAM_JENKINS_BUILD_URL = "jenkinsbuildurl"; //$NON-NLS-1$
	public static final String PARAM_RTC_BUILD_INTEGRATION = "buildintegration"; //$NON-NLS-1$
	public static final String PARAM_RTC_BUILD_DEFINITION_ID = "builddefid"; //$NON-NLS-1$
	public static final String PARAM_RTC_BUILD_RESULT_UUID = "buildresultuuid"; //$NON-NLS-1$
	public static final String PARAM_JENKINS_BUILD_STATUS = "status"; //$NON-NLS-1$
	public static final String PARAM_RTC_WORKITEM_INTEGRATION = "workitemintegration"; //$NON-NLS-1$
	public static final String PARAM_RTC_WORK_ITEM_ID = "wi"; //$NON-NLS-1$
	public static final String PARAM_RTC_WORK_ITEM_COMMENT = "comment"; //$NON-NLS-1$
	
	public static final String WI_FRAGMENT = "resource/itemName/com.ibm.team.workitem.WorkItem/"; //$NON-NLS-1$
	public static final String BUILD_RESULT_UUID = "buildResultUUID"; //$NON-NLS-1$
	public static final String BUILD_RESULT_ITEM_OID = "resource/itemOid/com.ibm.team.build.BuildResult/";
}
