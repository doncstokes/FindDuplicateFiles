package com.stokedpenguin.util;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;

public class Md5 {
	private MessageDigest md5 = MessageDigest.getInstance("md5");
	
	/**
	 * Constructor
	 * Required because member initialization may throw exception.
	 * @throws Exception
	 */
	public Md5() throws Exception {}

	/**
	 * Compute the MD5 sum for the specified file.
	 * @param file
	 * @return Hex string notation of MD5 sum.
	 * @throws Exception
	 */
	public String md5Sum(File file) throws Exception {
		md5.reset();
		FileInputStream fis = new FileInputStream(file);
		byte[] buffer = new byte[512];
		long total = 0;
		int count;
		while ((count = fis.read(buffer)) > 0) {
			md5.update(buffer, 0, count);
			total += count;
		}
		fis.close();
		if (total != file.length())
			System.err.println("file read size mismatch on " + file.getAbsolutePath() + " expected " + file.length() + " read " + total);
		byte[] bs = md5.digest();
		BigInteger bi = new BigInteger(1, bs);
		String hex = bi.toString(16);
		// Add leading zeros if not 32 characters
		while (hex.length() < 32) hex = "0" + hex;
		return hex;
	}
}
