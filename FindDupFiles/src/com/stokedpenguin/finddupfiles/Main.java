/* File: Main.java
 * Author: Don Stokes <myFirstName AT myFullName DOT com>
 * Purpose:
 *  Locate files with identical hash values (assumed duplicates).
 * Operation:
 *  Prepare database.
 *  Recursively scan each directory from command line.
 *  For each file, create database record with filename, hash, ...
 *  Populate a table with duplicate file IDs and hashes.
 *  Output a line for each duplicate hash containing filenames with that hash.
 * Dependencies:
 *  derby (database library)
 * IMPORTANT:
 *  See info String below or execute with --help option.
 *  Copyright 2019 Don Stokes
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
/*
 * ToDo:
 * + Optimize subsequent runs by not purging database.
 *    - Traverse records and delete records for missing files.
 *    - Traverse files. Add missing files. Update hash on changed files
 * + Add an option to verify duplicates by byte for byte compare.
 * + See if derby classes support new "try-with-resource" java syntax.
 */

package com.stokedpenguin.finddupfiles;

import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.apache.derby.shared.common.error.DerbySQLIntegrityConstraintViolationException;
import com.stokedpenguin.util.file.DirectoryWalker;
import com.stokedpenguin.util.file.DirectoryWalker.Notification;

public class Main {
	/** SQL for inserting record into File table */
	private static final String sqlFileInsert = 
			"INSERT INTO File(file_name, path_name, size, modify_time, hash_time, hash)" +
			" VALUES(?, ?, ?, ?, ?, ?)";
	/** Name of configuration directory, normally located in HOME directory */
	private String configDirName = ".finddupfiles";
	/** Error(s) detected if non-zero */
	private int exitCode = 0;
	/** Count of errors encountered, like files or directories denied permission. */
	private int errorCount = 0;
	/** Directory names to search for duplicate files */
	private ArrayList<File> dirs = new ArrayList<File>();
	/** Object for calculating md5 sums */
	private MessageDigest md5 = MessageDigest.getInstance("md5");
	/** Increase output when non-zero */
	private int verbosity = 0;
	/** Count of records created */
	private long recordCount = 0;
	/** Flag set if a new database was created during this execution */
	private boolean reportEmpties = false;
	private boolean newDb = false;
	/** Database open during execution */
	private Connection dbConn = null;
	/** Optimize performance by only creating this statement once */
	private PreparedStatement stmntFileInsert = null;
	private static final int verMajor = 1;
	private static final int verMinor = 0;
	/** Text for help command line option */
	public static final String info =
		getVersion()+
		"Find Duplicate Files\n"+
		"Usage: java -jar finddupfiles.jar [options] <directory1> [<directory2> ...]\n"+
		"Options:\n"+
		" --help     (see this text)\n"+
		" --verbose  (see extra output on stderr)\n"+
		"Written by Don Stokes <myFirstName AT myFullName DOT com>\n"+
		"CAUTION: Reported files have same hash. "+
		"There is a very slight chance the files are different with the same hash. "+
		"Use the diff utility to be certain.\n"+
		"ABSOLUTELY NO WARRANY! USE AT YOUR OWN RISK!\n"+
		"See the source code (written in Java) for more information.\n"+
		"If you find this software useful and would like to make a donation, "+
		"please contact me at the email address above. Thanks!\n"+
		"See GPL license in the source code.\n"+
		"Copyright 2019 Don Stokes\n"+
		"";
	
	/**
	 * User readable version string
	 * @return
	 */
	public static final String getVersion() {
		return String.format("version %d.%02d\n", verMajor, verMinor);
	}
	
	/**
	 * This will probably need enhancement if MSW is supported.
	 * @return Absolute path to current user home directory
	 */
	private File getHomeDir() {
		return new File(System.getenv("HOME"));
	}
	
	/**
	 * Create the configuration directory if it doesn't exist.
	 * @return Absolute path to configuration directory.
	 * @throws Exception
	 */
	private File getConfigDir() throws Exception {
		File configDir = new File(getHomeDir().getAbsolutePath() + "/" + configDirName);
		if (!configDir.isDirectory())
			configDir.mkdir();
		return configDir;
	}
	
	/**
	 * Compute the MD5 sum for the specified file.
	 * @param file
	 * @return Hex string notation of MD5 sum.
	 * @throws Exception
	 */
	private String md5Sum(File file) throws Exception {
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
	
	/**
	 * 
	 * @param args
	 * @return False if execution should be aborted
	 */
	private boolean parseArgs(String[] args) {
		boolean ret = true;
		if (args.length == 0) {
			System.err.println("Usage: <executable> [args] <directories>  # Try --help option.");
			return false;
		}
		for (int curArg = 0; curArg < args.length; curArg++) {
			if (args[curArg].equals("--help")) {
				System.err.printf(info);
				ret = false;
			} else if (args[curArg].equals("--verbose")) {
					verbosity = 1;
			} else {
				File dir = new File(args[curArg]);
				if (dir.isDirectory())
					dirs.add(dir);
				else {
					exitCode = 1;
					System.err.println("directory does not exist: " + dir.getAbsolutePath());
				}
			}
		}
		return ret;
	}
	
	/**
	 * Create tables on a new database
	 * @throws Exception
	 */
	private void createTables() throws Exception {
		if (verbosity > 0)
			System.err.println("Creating tables ...");
		String sqlTableFile =
			"CREATE TABLE File (id INT PRIMARY KEY generated always as identity" +
			", file_name VARCHAR(256), path_name VARCHAR(2048)" +
			", size BIGINT" +
			", modify_time BIGINT, hash_time BIGINT" +
			", hash VARCHAR(64))";
		String sqlTableDuplicate =
				"CREATE TABLE Duplicate (id INT PRIMARY KEY, hash VARCHAR(64))";
 		Statement stmnt = dbConn.createStatement();
 		stmnt.execute(sqlTableFile);
 		stmnt.execute(sqlTableDuplicate);
 		dbConn.commit();
 		stmnt.close();
		if (verbosity > 0)
			System.err.println("... tables created.");
	}

	/**
	 * Delete old data from previous traversal on existing database.
	 * @throws Exception
	 */
	private void purgeRows() throws Exception {
		if (verbosity > 0)
			System.err.println("Purging rows ...");
		String sqlDelFile = "DELETE FROM File";
		String sqlDelDuplicate = "DELETE FROM Duplicate";
 		Statement stmnt = dbConn.createStatement();
 		stmnt.execute(sqlDelFile);
 		stmnt.execute(sqlDelDuplicate);
 		dbConn.commit();
 		stmnt.close();
		if (verbosity > 0)
			System.err.println("... rows purged.");
	}
	
	/**
	 * Open the database.
	 * Create if necessary.
	 * @return
	 * @throws Exception
	 */
	private Connection makeDbConn() throws Exception {
		// Prevent derby from pooping derby.log all over the place
		System.setProperty("derby.system.home", getConfigDir().getAbsolutePath());
		File dbDir = new File(getConfigDir().getAbsolutePath() + "/db");
		newDb = !dbDir.isDirectory();
		String strConn = "jdbc:derby:" + dbDir.getAbsolutePath() + ";create=true";
		if (verbosity > 0)
			System.err.println("Opening database ...");
		dbConn = DriverManager.getConnection(strConn);
		dbConn.setAutoCommit(false);
		if (verbosity > 0)
			System.err.println("... database open.");
		if (newDb) {
			createTables();
		}
		stmntFileInsert = dbConn.prepareStatement(sqlFileInsert);
		return dbConn;
	}

	/**
	 * Query the database for duplicate checksums and report findings.
	 * @return
	 * @throws Exception
	 */
	public long queryDups() throws Exception{
		long dupCnt = 0;
		long srchCnt = 0;
		
		Statement stmntAll = dbConn.createStatement();
		ResultSet rsltAll = stmntAll.executeQuery("SELECT * FROM File");
		PreparedStatement stmntDups = dbConn.prepareStatement("SELECT * FROM File WHERE hash = ?");
		PreparedStatement stmntDup  = dbConn.prepareStatement("INSERT INTO Duplicate (id, hash) VALUES (?, ?)");
		while (rsltAll.next()) {
			if (verbosity > 0 && srchCnt % 1000 == 0)
				System.err.println(Long.toString(srchCnt) + " records serached.");
			boolean foundDup = false;
			stmntDups.setString(1, rsltAll.getString("hash"));
			ResultSet rsltDups = stmntDups.executeQuery();
			while (rsltDups.next()) {
				// Not the same file?
				if (rsltAll.getLong("id") != rsltDups.getLong("id")) {
					// Don't report empty files unless requested
					if (rsltAll.getLong("size") > 0 || reportEmpties) {
						foundDup = true;
						if (verbosity > 0) System.err.println(
							"DUPLICATE: " +
							rsltAll.getString("path_name") +
							"/" +
							rsltAll.getString("file_name") +
							" " +
							rsltDups.getString("path_name") +
							"/" +
							rsltDups.getString("file_name")
							);
						try {
							stmntDup.setInt   (1, rsltDups.getInt("id"));
							stmntDup.setString(2, rsltDups.getString("hash"));
							stmntDup.execute();
						} catch (DerbySQLIntegrityConstraintViolationException e) {
							// Intentionally ignored
						}
					}
				}
			}
			if (foundDup) {
				try {
					stmntDup.setInt   (1, rsltAll.getInt("id"));
					stmntDup.setString(2, rsltAll.getString("hash"));
					stmntDup.execute();				
				} catch (DerbySQLIntegrityConstraintViolationException e) {
					// Intentionally ignored
				}
			}
			rsltDups.close();
			srchCnt++;
		}
		dbConn.commit();
		rsltAll.close();
		stmntDups.close();
		stmntAll.close();

		return dupCnt;
	}
	
	/**
	 * Constructor for the application.
	 * @param args Command line parameters
	 * @throws Exception
	 */
	public Main(String[] args) throws Exception {
		if (!parseArgs(args))
			exitCode = 1; // Cmd ln problem - abort
		if (verbosity > 0)
			System.err.printf(getVersion());
		if (exitCode == 0)
			makeDbConn();
	}
	
	/**
	 * Guts of the execution.
	 * Called after initialization.
	 */
	public void run() throws Exception {
		// Until optimized db updates are implemented, clear old data.
		if (!newDb)
			purgeRows();
		for (File dir : dirs) {
			populateDb(dir);
		}
		queryDups();
		report(System.out);
		if (errorCount > 0) {
			System.err.printf("%d ERRORS WERE ENCOUNTERED\n", errorCount);
			exitCode = 1;
		}
	}

	/**
	 * Insert a new record into the database for specified file.
	 * @param file
	 * @throws Exception
	 */
	private void insertFile(File file) throws Exception {
		if (verbosity > 0 && recordCount % 1000 == 0)
			System.err.println(Long.toString(recordCount) + " records processed.");
		String hash = null;
		try {
			hash = md5Sum(file);
		} catch (Throwable t) {
			System.err.println("ERROR: " + t.getMessage());
			errorCount++;
		}
		if (hash != null) {
			stmntFileInsert.setString(1, file.getName());
			stmntFileInsert.setString(2, file.getParent());
			stmntFileInsert.setLong(  3, file.length());
			stmntFileInsert.setLong(  4, file.lastModified());
			stmntFileInsert.setLong(  5, System.currentTimeMillis());
			stmntFileInsert.setString(6, hash);
			stmntFileInsert.executeUpdate();
			dbConn.commit();
			recordCount++;
		}
	}
	
	/**
	 * Traverse specified directory and insert a db record for each file.
	 * @param dir
	 * @return
	 * @throws Exception
	 */
	private long populateDb(File dir) throws Exception {
		long ret = 0;	
		DirectoryWalker dw = new DirectoryWalker(dir.getAbsolutePath());
		dw.setContinueOnErrors(true);
		dw.walk(new Notification() {
			@Override
			public boolean onFile(File file) {
				boolean ret = true;
				try {
					insertFile(file);
				} catch (Throwable t) {
					ret = false;
					exitCode = 1;
					System.err.println(t);
				}
				return ret;
			}
			@Override
			public boolean onDir(File file) {
				// Nothing to do
				return true;
			}
		});
		errorCount += dw.getErrorCount();
		if (verbosity > 0) {
			System.err.println(Long.toString(recordCount) + " records created");
		}
		return ret;
	}
	
	/**
	 * Query distinct hashes and report a line containing each file having that hash
	 * @param out
	 * @return
	 * @throws Exception
	 */
	private int report(PrintStream out) throws Exception {
		int hashCnt = 0;
		String sqlHash = "SELECT DISTINCT hash FROM Duplicate";
		String sqlFile = "SELECT file_name, path_name FROM File WHERE hash = ?";
		Statement stmntHash = dbConn.createStatement();
		PreparedStatement stmntFile = dbConn.prepareStatement(sqlFile);
		ResultSet rsltHash = stmntHash.executeQuery(sqlHash);
		while (rsltHash.next()) {
			hashCnt++;
			stmntFile.setString(1, rsltHash.getString("hash"));
			ResultSet rsltFile = stmntFile.executeQuery();
			int fileCnt = 0;
			while (rsltFile.next()) {
				if (fileCnt == 0)
					out.print("DUPLICATES:");
				out.print(" ");
				out.print(rsltFile.getString("path_name"));
				out.print("/");
				out.print(rsltFile.getString("file_name"));
				fileCnt++;
			}
			out.println();
			rsltFile.close();
		}
		rsltHash.close();
		stmntFile.close();
		stmntHash.close();
		dbConn.commit();
		return hashCnt;
	}
	
	/**
	 * Close all open resources
	 * @throws Exception
	 */
	public void terminate() throws Exception {
		if (stmntFileInsert != null) {
			stmntFileInsert.close();
			stmntFileInsert = null;
		}
		if (dbConn != null) {
			dbConn.close();
			dbConn = null;
		}
	}
	
	/**
	 * Accessor method for exit code property.
	 * @return
	 */
	public int getExitCode() {
		return exitCode;
	}
	
	/**
	 * Entry point for application.
	 * @param args
	 */
	public static void main(String[] args) {
		boolean showExceptionStack = true;
		int exitCode = 0;
		try {
			// Create the application object
			Main app = new Main(args);
			// Check for problems
			exitCode = app.exitCode;
			// Run the application if no initialization problems
			if (exitCode == 0) {
				app.run();
			}
			// Free resources
			app.terminate();
		} catch (Throwable t) {
			System.err.println(t);
			if (showExceptionStack)
				t.printStackTrace(System.err);
			exitCode = 1;
		}
		System.exit(exitCode);
	}
}
