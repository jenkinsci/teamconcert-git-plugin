/******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2011, 2026. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 ******************************************************************************/

package com.ibm.team.git.build.hjplugin.test;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.ibm.team.git.build.hjplugin.RTCUtils;

public class RTCUtilTests {

	@Test
    public void testEmptyorNullString1() throws IOException {
        String test = "";
        Assertions.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }
	
	@Test
    public void testEmptyorNullString2() throws IOException {
        String test = "  ";
        Assertions.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }

	@Test
    public void testEmptyorNullString3() throws IOException {
        String test =null;
        Assertions.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }

	@Test
    public void testEmptyorNullString4() throws IOException {
        String test ="dummy";
        Assertions.assertFalse(RTCUtils.IsNullOrEmpty(test));
    }
	
	@Test
	public void testClassExists() throws IOException {
		Assertions.assertTrue(RTCUtils.isClassExists("hudson.model.Cause$UserIdCause"));
	}
	
	@Test
	public void testFormatURI() throws IOException {
		String uri = "https://localHost:9443/jazz";
		uri = RTCUtils.formatURI(uri);
		Assertions.assertTrue(uri.endsWith("/"));
		int length = uri.length();
		uri = RTCUtils.formatURI(uri);
		Assertions.assertTrue(uri.endsWith("/") && (uri.length() == length));
	}
	
	@Test
	public void testMakeFullURL() throws IOException {
		String uri = "https://localHost:9443/jazz";
		String suffix = "abc/def";
		String fulluri = RTCUtils.makeFullURL(uri, suffix);
		Assertions.assertTrue(fulluri.equals("https://localHost:9443/jazz/abc/def"));
		
		uri = "https://localHost:9443/jazz/";
		fulluri = RTCUtils.makeFullURL(uri, suffix);
		Assertions.assertTrue(fulluri.equals("https://localHost:9443/jazz/abc/def"));
	}
	
	@Test
	public void testGetWorkItemUrl() throws IOException {
		String uri = "https://localHost:9443/ccm";
		String wi = "4";
		String wiUrl = RTCUtils.getWorkItemUrl(uri, wi);
		Assertions.assertTrue(wiUrl.equals("https://localHost:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/4"));
	}
	
	@Test
	public void testMatchString() throws IOException {
		String comment = null;
		Assertions.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "";
		Assertions.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "test dummy bu";
		Assertions.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "test workitem";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test bug";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test task";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test defect";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test WORKITEM";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test BuG";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test TaSK";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test DEFect";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test abcd rtcwi";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test abcd work item";
		Assertions.assertTrue(RTCUtils.matchesKey(comment));
	}
	
	@Test
	public void testWIModeDefault() throws IOException {
		Assertions.assertTrue("C".equals(RTCUtils.getWiMode(null)));
		Assertions.assertTrue("C".equals(RTCUtils.getWiMode("dummy")));
	}
	
	@Test
	public void testWIModeComments() throws IOException {
		Assertions.assertTrue("C".equals(RTCUtils.getWiMode("withComment")));
		Assertions.assertTrue("C".equals(RTCUtils.getWiMode("WITHComment")));
	}
	
	@Test
	public void testWIModeLinks() throws IOException {
		Assertions.assertTrue("L".equals(RTCUtils.getWiMode("withLink")));
		Assertions.assertTrue("L".equals(RTCUtils.getWiMode("WITHLINK")));
	}
	
	@Test
	public void testWICommentAndLink() throws IOException {
		Assertions.assertTrue("CL".equals(RTCUtils.getWiMode("withCommentAndLink")));
		Assertions.assertTrue("CL".equals(RTCUtils.getWiMode("WITHCommentANDLINK")));
	}
}
