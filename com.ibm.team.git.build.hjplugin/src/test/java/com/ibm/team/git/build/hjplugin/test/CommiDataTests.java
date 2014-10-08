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
package com.ibm.team.git.build.hjplugin.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.team.git.build.hjplugin.RTCUtils;

public class CommiDataTests {
	
	@Test
	public void testWorkItemInCommitComment() {
		String[] wi = new String[] { "1", "2", "3", "4" };
		String comment = String.format(
				"bug %s, defect %s, task %s and workitem %s", wi[0], wi[1],
				wi[2], wi[3]);
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == wi.length);
		Assert.assertTrue(wi[0].equals(result[0]));
		Assert.assertTrue(wi[1].equals(result[1]));
		Assert.assertTrue(wi[2].equals(result[2]));
		Assert.assertTrue(wi[3].equals(result[3]));
	}
	
	@Test
	public void testWorkItemInCommitComment2() {
		String[] wi = new String[] { "1", "2", "3", "4" };
		String comment = String.format(
				"bug %s; defect %s, task %s: workitem %s;", wi[0], wi[1],
				wi[2], wi[3]);
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == wi.length);
		Assert.assertTrue(wi[0].equals(result[0]));
		Assert.assertTrue(wi[1].equals(result[1]));
		Assert.assertTrue(wi[2].equals(result[2]));
		Assert.assertTrue(wi[3].equals(result[3]));
	}
	
	@Test
	public void testNoWorkItemInCommitComment1() {
		String comment = "bug a defect b task c workitem d";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testNoWorkItemInCommitComment2() {
		String comment = "bug defect task workitem";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testNoWorkItemInCommitComment3() {
		String comment = "bug";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testNoWorkItemInCommitComment4() {
		String comment = "defect ";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testNoWorkItemInCommitComment5() {
		String comment = " task";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testNoWorkItemInCommitComment6() {
		String comment = " workitem ";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertTrue(result != null && result.length == 0);
	}
	
	@Test
	public void testWorkItemInCommitCommentMixedCase() {
		String[] wi = new String[] { "1", "2", "3", "4" };
		String comment = String.format(
				"bUg %s; DeFeCT %s, tAsK %s: WoRkITEM %s;", wi[0], wi[1],
				wi[2], wi[3]);
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == wi.length);
		Assert.assertTrue(wi[0].equals(result[0]));
		Assert.assertTrue(wi[1].equals(result[1]));
		Assert.assertTrue(wi[2].equals(result[2]));
		Assert.assertTrue(wi[3].equals(result[3]));
	}
	
	@Test
	public void testWorkItemWithBugKeyCommitComment() {
		String comment = "fixing bug 1";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
		
		comment = "fixing BUG 1";
		result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemWithTaskKeyCommitComment() {
		String comment = "fixing task 1";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
		
		comment = "fixing TASK 1";
		result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemWithDefectKeyCommitComment() {
		String comment = "fixing defect 1";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
		
		comment = "fixing DEFECT 1";
		result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemWithWorkItemKeyCommitComment() {
		String comment = "fixing workitem 1";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
		
		comment = "fixing WORKITEM 1";
		result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemAtStartInCommitComment() {
		String comment = "bug 1 is fixed";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemAtEndInCommitComment() {
		String comment = "fixing task 1";
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == 1);
		Assert.assertTrue("1".equals(result[0]));
	}
	
	@Test
	public void testWorkItemPCharsInCommitComment() {
        String comment = "fix for task 5, workitem 7";
        String[] result = getWorkItemsFromCommitComment(comment);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length == 2);
        Assert.assertTrue("5".equals(result[0]));
        Assert.assertTrue("7".equals(result[1]));
        
        comment = "task 5: fix";
        result = getWorkItemsFromCommitComment(comment);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length == 1);
        Assert.assertTrue("5".equals(result[0]));
        
        comment = "task 5; fix";
        result = getWorkItemsFromCommitComment(comment);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length == 1);
        Assert.assertTrue("5".equals(result[0]));
    }
		
	@Test
	public void testWorkItemInCommitCommentWithDQuote() {
		String[] wi = new String[] { "1", "2", "3", "4" };
		String comment = String.format(
				"\"bug %s defect %s task %s workitem %s\"", wi[0], wi[1],
				wi[2], wi[3]);
		String[] result = getWorkItemsFromCommitComment(comment);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.length == wi.length);
		Assert.assertTrue(wi[0].equals(result[0]));
		Assert.assertTrue(wi[1].equals(result[1]));
		Assert.assertTrue(wi[2].equals(result[2]));
		Assert.assertTrue(wi[3].equals(result[3]));
	}

	private String[] getWorkItemsFromCommitComment(String comment) {
		List<String> wiList = RTCUtils.getWorkItemsFromCommitComment(comment);
		if(wiList != null) {
			return wiList.toArray(new String[0]);
		}else {
			return new String[0];
		}
	}

}
