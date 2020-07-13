/*******************************************************************************
 * Copyright (c) 2001, 2020 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/

package j9vm.test.xlp;

import java.io.*;
import java.util.*;

import j9vm.test.xlp.xlphelper.*;
import j9vm.test.xlp.xlphelper.XlpUtil.*;


public class XlpCodeCache implements XlpTestInterface {

	/* Reference to large page test cases */
	private ArrayList<XlpOption> xlpTestCaseList = null;
	private ArrayList<Pair<Long, String>> supportedLargePages = null;
	private final String unsupportedLargePagesMsg = "System configuration does not support option '-XX:+UseLargePagesCodeCache";
	private final String componentLabel = "UseLargePagesCodeCache";

	public ArrayList<Pair<Long, String>> getSupportedLargePages() throws IllegalArgumentException {
		/* Z/OS Codecache would only supports pageable large pages */
		String pageType = (osName == OSName.ZOS) ? XlpUtil.XLP_PAGE_TYPE_PAGEABLE : XlpUtil.XLP_PAGE_TYPE_NOT_USED;
		ArrayList<Pair<Long, String>> supportedPages = null;

		switch (osArch) {
			case X86:
				supportedPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(4 * XlpUtil.ONE_KB, pageType)); add(new Pair(2 * XlpUtil.ONE_MB, pageType)); add(new Pair(4 * XlpUtil.ONE_MB, pageType)); add(new Pair(7 * XlpUtil.ONE_MB, pageType)); }};
				break;
			case S390X:
				supportedPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(4 * XlpUtil.ONE_KB, pageType)); add(new Pair(1 * XlpUtil.ONE_MB, pageType)); add(new Pair(7 * XlpUtil.ONE_MB, pageType)); }};
				if (osName == OSName.ZOS)
					supportedPages.add(new Pair(2 * XlpUtil.ONE_GB, pageType));
				break;
			case PPC:
				if (osName == OSName.AIX) {
					supportedPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(4 * XlpUtil.ONE_KB, pageType)); add(new Pair(64 * XlpUtil.ONE_KB, pageType)); add(new Pair(16 * XlpUtil.ONE_MB, pageType)); }};
					if (addrMode == AddrMode.BIT64) {
						supportedPages.add(new Pair(16 * XlpUtil.ONE_GB, pageType));
					}
				} else if (osName == OSName.LINUX) {
					supportedPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(64 * XlpUtil.ONE_KB, pageType)); add(new Pair(2 * XlpUtil.ONE_MB, pageType)); add(new Pair(16 * XlpUtil.ONE_MB, pageType)); }};
				} else {
					throw new IllegalArgumentException("Unsupported Operating System");
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported Architecture");
			}

		return supportedPages;
	}

	public ArrayList<Pair<Long, String>> getDefaultLargePages() {
		ArrayList<Pair<Long, String>> defaultPages = null;

		switch(osArch) {
			case PPC:
				defaultPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(64 * XlpUtil.ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED)); }};
				if (osName == OSName.LINUX)
					defaultPages.add(new Pair(16 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED));
				break;
			case S390X:
				if (osName == OSName.ZOS)
					defaultPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(1 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE)); }};
				else if (osName == OSName.LINUX)
					defaultPages = new ArrayList<Pair<Long, String>>() {{ add(new Pair(1 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED)); }};
				break;
			default:
				break;
		}
		return defaultPages;
	}

	/* Populate xlpOptionsList with all test cases. */
	public ArrayList<XlpOption> populateXlpTestCases() {

		/* Use Reference if already allocated */
		if (supportedLargePages == null)
		{
			try {
				supportedLargePages = getSupportedLargePages();
			} catch (IllegalArgumentException e) {
				System.out.println("ERROR: Failed to determine underlying OS. This test needs to know underlying OS.");
				e.printStackTrace();
				return null;
			}
		}

		ArrayList<XlpOption> xlpTestCaseList= new ArrayList<>();

		/* Size Independent Test Cases */
		xlpTestCaseList.add(new XlpOption(null, false));
		xlpTestCaseList.add(new XlpOption("-XX:+UseLargePages", true));
		xlpTestCaseList.add(new XlpOption("-XX:+UseLargePagesCodeCache", true));
		xlpTestCaseList.add(new XlpOption("-Xlp", true));

		for (int i = 0; i < supportedLargePages.size(); i++) {
			Pair<Long, String> currLP = supportedLargePages.get(i);
			long currLargePageSize = currLP.getKey();
			String currLargePageType = currLP.getValue();

			/* Convert the large page size to qualifer string format */
			String currLargePageSizeString = XlpUtil.pageSizeLongToString(currLargePageSize);
			/* Report failure if test conversion fails. */
			if (currLargePageSizeString == null) {
				System.out.println("Could not convert large page size to string. Large page size:" + currLargePageSize);
				return null;
			}

			/* Formulate Base Large Page Option Varations */
			String currXlpSizeString = "-Xlp" + currLargePageSizeString; /* -Xlp<size> */
			String currXlpCodeCacheString = "-Xlp:codecache:pagesize=" + currLargePageSizeString; /* -Xlp:codecache:pagesize= */
			String currLargePageSizeInBytesString = "-XX:LargePageSizeInBytes=" + currLargePageSizeString; /* -XX:LargePageSizeInBytes */
			String currLargePageSizeInBytesCodeCacheString = "-XX:LargePageSizeInBytesCodeCache=" + currLargePageSizeString; /* -XX:LargePageSizeInBytesCodeCache */

			/* ZOS - Append required [non]pageable */
			if (osName == OSName.ZOS)
				currXlpCodeCacheString += "," + currLargePageType;

			/* -Xlp<Size> */
			xlpTestCaseList.add(new XlpOption(currXlpSizeString, currLargePageSize, currLargePageType));

			/* -XX:+UseLargePages -XX:LargePageSizeInBytes=<size> */
			xlpTestCaseList.add(new XlpOption("-XX:+UseLargePages " + currLargePageSizeInBytesString, currLargePageSize, currLargePageType));
			/* -XX:+UseLargePages -XX:LargePageSizeInBytesCodeCache=<size> */
			xlpTestCaseList.add(new XlpOption("-XX:+UseLargePages " + currLargePageSizeInBytesCodeCacheString, currLargePageSize, currLargePageType));
			/* -XX:+UseLargePagesCodeCache -XX:LargePageSizeInBytes=<size> */
			xlpTestCaseList.add(new XlpOption("-XX:+UseLargePagesCodeCache " + currLargePageSizeInBytesString, currLargePageSize, currLargePageType));
			/* -XX:+UseLargePagesCodeCache -XX:LargePageSizeInBytesCodeCache=<size> */
			xlpTestCaseList.add(new XlpOption("-XX:+UseLargePagesCodeCache " + currLargePageSizeInBytesCodeCacheString, currLargePageSize, currLargePageType));

			/* -Xlp:codecache:pagesize=<size> */
			xlpTestCaseList.add(new XlpOption(currXlpCodeCacheString, currLargePageSize, currLargePageType));

			/* -Xlp and -Xlp<Size> */
			xlpTestCaseList.add(new XlpOption("Xlp " + currXlpSizeString, currLargePageSize, currLargePageType));
			xlpTestCaseList.add(new XlpOption(currXlpSizeString + " Xlp", currLargePageSize, currLargePageType));

			/* -Xlp and -Xlp:codecache:pagesize=<size> */
			xlpTestCaseList.add(new XlpOption("-Xlp " + currXlpCodeCacheString, currLargePageSize, currLargePageType));
			xlpTestCaseList.add(new XlpOption(currXlpCodeCacheString + " Xlp", currLargePageSize, currLargePageType));

			/* Combination Tests */
			if (i != 0) {
				Pair<Long, String> prevLP = supportedLargePages.get(i - 1);
				long prevLargePageSize = prevLP.getKey();
				String prevLargePageType = prevLP.getValue();

				/* Convert the large page size to qualifer string format */
				String prevLargePageSizeString = XlpUtil.pageSizeLongToString(prevLargePageSize);

				String prevXlpSizeString = "-Xlp" + prevLargePageSizeString; /* -Xlp<size> */
				String prevXlpCodeCacheString = "-Xlp:codecache:pagesize=" + prevLargePageSizeString; /* -Xlp:codecache:pagesize= */
				String prevLargePageSizeInBytesString = "-XX:LargePageSizeInBytes=" + prevLargePageSizeString; /* -XX:LargePageSizeInBytes */
				String prevLargePageSizeInBytesCodeCacheString = "-XX:LargePageSizeInBytesCodeCache=" + prevLargePageSizeString; /* -XX:LargePageSizeInBytesCodeCache */

				/* ZOS - Append required [non]pageable */
				if (osName == OSName.ZOS)
					prevXlpCodeCacheString += "," + prevLargePageType;

				/* Test -Xlp<size> and -Xlp:codecache=pagesize=<size> */
				xlpTestCaseList.add(new XlpOption(prevXlpSizeString + " " + currXlpCodeCacheString, currLargePageSize, currLargePageType));
				xlpTestCaseList.add(new XlpOption(currXlpCodeCacheString + " " + prevXlpSizeString, prevLargePageSize, prevLargePageType));
				xlpTestCaseList.add(new XlpOption(prevXlpCodeCacheString + " " + currXlpSizeString, currLargePageSize, currLargePageType));
				xlpTestCaseList.add(new XlpOption(currXlpSizeString + " " + prevXlpCodeCacheString, prevLargePageSize, prevLargePageType));

				/* Test Multiple -Xlp<size> */
				xlpTestCaseList.add(new XlpOption(prevXlpSizeString + " " + currXlpSizeString, currLargePageSize, currLargePageType));
				xlpTestCaseList.add(new XlpOption(currXlpSizeString + " " + prevXlpSizeString, prevLargePageSize, prevLargePageType));

				/* Test Multiple -Xlp:codecache:pagesize=  options. */
				xlpTestCaseList.add(new XlpOption(prevXlpCodeCacheString + " " + currXlpCodeCacheString, currLargePageSize, currLargePageType));
				xlpTestCaseList.add(new XlpOption(currXlpCodeCacheString + " " + prevXlpCodeCacheString, prevLargePageSize, prevLargePageType));

				/* Test Multiple pagesize parameters in -Xlp:codecache:pagesize=<size1>,pagesize=<size2> */
				xlpTestCaseList.add(new XlpOption("-Xlp:codecache:pagesize=" + prevLargePageSizeString  + ",pagesize=" + currLargePageSizeString, currLargePageSize, currLargePageType));
				xlpTestCaseList.add(new XlpOption("-Xlp:codecache:pagesize=" + currLargePageSizeString + ",pagesize=" + prevLargePageSizeString, prevLargePageSize, prevLargePageType));
			}
		}

		return xlpTestCaseList;
	}

	public String getUnsupportedLargePagesMsg() {
		return unsupportedLargePagesMsg;
	}

	public String getComponentLabel() {
		return componentLabel;
	}
}