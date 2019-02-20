package com.stokedpenguin.util;

import java.io.File;

/**
 * General functions that could be reused in other applications
 * @author don
 */
public class Util {
	/**
	 * This will probably need enhancement if MSW is supported.
	 * @return Absolute path to current user home directory
	 */
	public static File getHomeDir() {
		return new File(System.getenv("HOME"));
	}
}
