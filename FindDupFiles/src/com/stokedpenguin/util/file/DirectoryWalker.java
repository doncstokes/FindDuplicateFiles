/* File: DirectoryWalker.java
 * Author: Don Stokes <myFirstName AT myFullName DOT com>
 * Purpose:
 *  Traverse a directory tree and call back to caller with file/dir names.
 * Copyright 2019 Don Stokes
 */
/*******************************************************************************
     This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package com.stokedpenguin.util.file;

import java.io.File;

public class DirectoryWalker {
	
	private String topDir = null;
	private int errorCount = 0;
	private boolean continueOnErrors = false;
	
	/**
	 * Interface for callback
	 * Implemented by client
	 * @author don
	 */
	public static interface Notification {
		boolean onFile(File file);
		boolean onDir(File file);
	}
	
	/**
	 * Constructor
	 * @param parentDir  Directory for starting traversal
	 * @throws Exception
	 */
	public DirectoryWalker(String parentDir) throws Exception {
		topDir = parentDir;
		if (!new File(topDir).isDirectory())
			throw new Exception("directory not found: " + topDir);
	}
	
	/**
	 * Public function to start traversal
	 * @param notification
	 * @throws Exception
	 */
	public void walk(Notification notification) throws Exception{
		recurse(new File(topDir), notification);
	}
	
	/**
	 * Recursive function to traverse directories
	 * @param fileDir       Directory to traverse
	 * @param notification  Callback Interface Implementation
	 * @return              false if traversal should abort
	 * @throws Exception
	 */
	private boolean recurse(File fileDir, Notification notification) throws Exception {
		boolean ret = true;
		try {
			File[] files = fileDir.listFiles();
			if (files == null) {
				throw new Exception("directory not accessible: " + fileDir.getAbsolutePath());
			}
			for (File file : files) {
				if (file.isFile()) {
					ret = notification.onFile(file);
					if (!ret)
						break;
				}
			}
			if (ret) {
				for (File file : files) {
					if (file.isDirectory()) {
						ret = notification.onDir(file);
						recurse(file, notification);
						if (!ret)
							break;
					}
				}			
			}
		} catch (Throwable t) {
			errorCount++;
			if (!continueOnErrors)
				throw t;
			System.err.println("ERROR: "+ t.getMessage());
		}
		return ret;
	}

	/**
	 * Accessor method for ErrorCount property
	 * @return
	 */
	public int getErrorCount() {
		return errorCount;
	}

	/**
	 * Mutator method ContinueOnErrors property
	 * @param continueOnErrors
	 */
	public void setContinueOnErrors(boolean continueOnErrors) {
		this.continueOnErrors = continueOnErrors;
	}

	/**
	 * Unit Test
	 * @param args  Directory to traverse
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: <executable> <directory>");
			System.exit(1);
		}
		String dir = args[0];
		Notification notification = new Notification() {
			@Override
			public boolean onFile(File file) {
				System.out.printf("FILE: %s\n", file.getAbsolutePath());
				return true;
			}
			@Override
			public boolean onDir(File file) {
				System.out.printf("DIR : %s\n", file.getAbsolutePath());
				return true;
			}
		};
		try {
			DirectoryWalker dw = new DirectoryWalker(dir);
			dw.setContinueOnErrors(true);
			dw.walk(notification);
		} catch (Throwable t) {
			System.err.println(t.toString());
			System.exit(1);
		}
	}
}
