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

package j9vm.test.xlp.xlphelper;

import java.io.*;
import java.util.*;


public class XlpUtil {

	public enum OSName {
		AIX,
		LINUX,
		WINDOWS,
		ZOS,
		UNKNOWN
	}

	public enum OSArch {
		PPC,
		S390X,
		X86,
		UNKNOWN
	}

	public enum AddrMode {
		BIT31,
		BIT32,
		BIT64,
		UNKNOWN
	}

	public static final String XLP_PAGE_TYPE_NOT_USED = "not  ";
	public static final String XLP_PAGE_TYPE_PAGEABLE = "pageable";
	public static final String XLP_PAGE_TYPE_NONPAGEABLE = "nonpageable";

	public static final long ONE_KB = 1 * 1024;
	public static final long ONE_MB = 1 * 1024 * ONE_KB;
	public static final long ONE_GB = 1 * 1024 * ONE_MB;
	public static final long ONE_TB = 1 * 1024 * ONE_GB;

	public static OSName getOperatingSystem() {
		String OSSpec = System.getProperty("os.name").toLowerCase();
		OSName osName = OSName.UNKNOWN;
		if (OSSpec != null) {
			/* Get OS from the spec string */
			if (OSSpec.contains("aix")) {
				osName = OSName.AIX;
			} else if (OSSpec.contains("linux")) {
				osName = OSName.LINUX;
			} else if (OSSpec.contains("windows")) {
				osName = OSName.WINDOWS;
			} else if (OSSpec.contains("z/os")) {
				osName = OSName.ZOS;
			} else {
				System.out.println("Runner couldn't determine underlying OS. Got OS Name:" + OSSpec);
				osName = OSName.UNKNOWN;
			}
		}
		return osName;
	}

	public static OSArch getArchitecture() {
		String archSpec = System.getProperty("os.arch").toLowerCase();
		OSArch osArch = OSArch.UNKNOWN;
		if (archSpec != null) {
			/* Get arch from spec string */
			if (archSpec.contains("ppc")) {
				osArch = OSArch.PPC;
			} else if (archSpec.contains("s390")) {
				osArch = OSArch.S390X;
			} else if (archSpec.contains("amd64") || archSpec.contains("x86")) {
				osArch = OSArch.X86;
			} else {
				System.out.println("Runner couldn't determine underlying architecture. Got OS Arch:" + archSpec);
				osArch = OSArch.UNKNOWN;
			}
		}
		return osArch;
	}

	public static AddrMode getAddressingMode(OSArch osArch) {
		String addressingMode = System.getProperty("sun.arch.data.model");
		AddrMode addrMode = AddrMode.UNKNOWN;
		if (addressingMode != null) {
			/* Get address mode. S390 31-Bit addressing mode should return 32. */
			if ((osArch == OSArch.S390X) && (addressingMode.contains("32"))) {
				addrMode = AddrMode.BIT31;
			} else if (addressingMode.contains("32")) {
				addrMode = AddrMode.BIT32;
			} else if (addressingMode.contains("64")) {
				addrMode = AddrMode.BIT64;
			} else {
				System.out.println("Runner couldn't determine underlying addressing mode. Got addressingMode:" + addressingMode);
				addrMode = AddrMode.UNKNOWN;
			}
		}
		return addrMode;
	}

	/**
	 * Accepts a memory size in Long format format (e.g 1024, 2048...) below 1TB, and
	 * returns human-readable format (e.g 2K, 4M).
	 * If error occurs during conversion, it returns null
	 *  
	 * @param pageSize
	 * 
	 * @return memory size String.
	 */
	public static String pageSizeLongToString(long pageSize) {
		if (ONE_MB > pageSize) {
			return pageSize%ONE_KB + "K";
		} else if (ONE_GB > pageSize) {
			return pageSize%ONE_MB + "M";
		} else if (ONE_TB >  pageSize) {
			return pageSize%ONE_GB + "G";
		} else {
			System.out.println("Page Size not supported");
			return null;
		}
	}

	/**
	 * Accepts a memory size in human-readable format (e.g 2K, 4M, 6G) and
	 * returns memory size in bytes.
	 * If error occurs during conversion, it returns 0.
	 *  
	 * @param pageSizeString
	 * 
	 * @return memory size in bytes
	 */
	public static long pageSizeStringToLong(String pageSizeString) {
		long pageSizeInBytes = 0;
		long pageSizeQualifier = 0;
		boolean invalidQualifier = false;
		String qualifier = pageSizeString.substring(pageSizeString.length()-1);		/* last character must be a qualifier if present */

		if (qualifier.matches("[a-zA-Z]")) {
			switch(qualifier.charAt(0)) {
			case 'k':
			case 'K':
				pageSizeQualifier = 10;
				break;
			case 'm':
			case 'M':
				pageSizeQualifier = 20;
				break;
			case 'g':
			case 'G':
				pageSizeQualifier = 30;
				break;
			default:
				System.out.println("ERROR: Unrecognized qualifier found in page size string");
				invalidQualifier = true;
				break;
			}
		}
		if (invalidQualifier) {
			pageSizeInBytes = 0;			
		} else {
			long pageSizeValue = 0;
			if (pageSizeQualifier != 0) {
				/* qualifier found, ignore last character */
				pageSizeValue = Long.parseLong(pageSizeString.substring(0, pageSizeString.length()-1));
			} else {
				/* qualifier not found */
				pageSizeValue = Long.parseLong(pageSizeString.substring(0, pageSizeString.length()));
			}
			pageSizeInBytes = pageSizeValue << pageSizeQualifier;
		}
		return pageSizeInBytes;
	}

}
