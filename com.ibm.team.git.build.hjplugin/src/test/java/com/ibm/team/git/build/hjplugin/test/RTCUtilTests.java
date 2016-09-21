/******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2011, 2015. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 ******************************************************************************/

package com.ibm.team.git.build.hjplugin.test;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.team.git.build.hjplugin.RTCUtils;

public class RTCUtilTests {

	@Test
    public void testEmptyorNullString1() throws IOException {
        String test = "";
        Assert.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }
	
	@Test
    public void testEmptyorNullString2() throws IOException {
        String test = "  ";
        Assert.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }

	@Test
    public void testEmptyorNullString3() throws IOException {
        String test =null;
        Assert.assertTrue(RTCUtils.IsNullOrEmpty(test));
    }

	@Test
    public void testEmptyorNullString4() throws IOException {
        String test ="dummy";
        Assert.assertFalse(RTCUtils.IsNullOrEmpty(test));
    }
	
	@Test
	public void testClassExists() throws IOException {
		Assert.assertTrue(RTCUtils.isClassExists("hudson.model.Cause$UserIdCause"));
	}
	
	@Test
	public void testFormatURI() throws IOException {
		String uri = "https://localHost:9443/jazz";
		uri = RTCUtils.formatURI(uri);
		Assert.assertTrue(uri.endsWith("/"));
		int length = uri.length();
		uri = RTCUtils.formatURI(uri);
		Assert.assertTrue(uri.endsWith("/") && (uri.length() == length));
	}
	
	@Test
	public void testMakeFullURL() throws IOException {
		String uri = "https://localHost:9443/jazz";
		String suffix = "abc/def";
		String fulluri = RTCUtils.makeFullURL(uri, suffix);
		Assert.assertTrue(fulluri.equals("https://localHost:9443/jazz/abc/def"));
		
		uri = "https://localHost:9443/jazz/";
		fulluri = RTCUtils.makeFullURL(uri, suffix);
		Assert.assertTrue(fulluri.equals("https://localHost:9443/jazz/abc/def"));
	}
	
	@Test
	public void testGetWorkItemUrl() throws IOException {
		String uri = "https://localHost:9443/ccm";
		String wi = "4";
		String wiUrl = RTCUtils.getWorkItemUrl(uri, wi);
		Assert.assertTrue(wiUrl.equals("https://localHost:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/4"));
	}
	
	@Test
	public void testMatchString() throws IOException {
		String comment = null;
		Assert.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "";
		Assert.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "test dummy bu";
		Assert.assertFalse(RTCUtils.matchesKey(comment));
		
		comment = "test workitem";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test bug";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test task";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test defect";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test WORKITEM";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test BuG";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test TaSK";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
		
		comment = "test DEFect";
		Assert.assertTrue(RTCUtils.matchesKey(comment));
	}

	@Test
	public void testPatternedWorkItemRefs() throws IOException {

		try {
			String comment;
			Pattern pattern = Pattern.compile("(?i)\\b(?:bug|task|workitem|defect)[ \\t]+([0-9]+)\\b");
			//Pattern pattern = Pattern.compile("(?i)\\b(?:rtc|wi|bug|task|workitem|defect)-([0-9]+)\\b");

			comment = "test task 123 hm";
			Set<String> strings = RTCUtils.allMatches(comment, pattern);
			System.out.println(comment + "  ==>  " + strings.toString());
			Assert.assertArrayEquals(new String[] {"123"}, strings.toArray(new String[strings.size()]));

			comment = "test task 123 hm task  \t666 yeah";
			strings = RTCUtils.allMatches(comment, pattern);
			System.out.println(comment + "  ==>  " + strings.toString());
			Assert.assertArrayEquals(new String[] {"123", "666"}, strings.toArray(new String[strings.size()]));

			comment = "test taSK 123 hm";
			strings = RTCUtils.allMatches(comment, pattern);
			System.out.println(comment + "  ==>  " + strings.toString());
			Assert.assertArrayEquals(new String[] {"123"}, strings.toArray(new String[strings.size()]));

			comment = "Bug\t 123:hm";
			strings = RTCUtils.allMatches(comment, pattern);
			System.out.println(comment + "  ==>  " + strings.toString());
			Assert.assertArrayEquals(new String[] {"123"}, strings.toArray(new String[strings.size()]));

		} catch (PatternSyntaxException e) {
			Assert.fail(e.toString());
		}
	}
	
	@Test
	public void testWIModeDefault() throws IOException {
		Assert.assertTrue("C".equals(RTCUtils.getWiMode(null)));
		Assert.assertTrue("C".equals(RTCUtils.getWiMode("dummy")));
	}
	
	@Test
	public void testWIModeComments() throws IOException {
		Assert.assertTrue("C".equals(RTCUtils.getWiMode("withComment")));
		Assert.assertTrue("C".equals(RTCUtils.getWiMode("WITHComment")));
	}
	
	@Test
	public void testWIModeLinks() throws IOException {
		Assert.assertTrue("L".equals(RTCUtils.getWiMode("withLink")));
		Assert.assertTrue("L".equals(RTCUtils.getWiMode("WITHLINK")));
	}
	
	@Test
	public void testWICommentAndLink() throws IOException {
		Assert.assertTrue("CL".equals(RTCUtils.getWiMode("withCommentAndLink")));
		Assert.assertTrue("CL".equals(RTCUtils.getWiMode("WITHCommentANDLINK")));
	}
	
}
