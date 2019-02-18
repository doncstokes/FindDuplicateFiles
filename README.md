# FindDuplicateFiles
Utility to locate duplicate files (same hash) in directory trees.
Implemented in Java with Derby database.

## Usage
These instructions are intended for Linux/UNIX; adjust them for MSW if you're into that.
See the build suggestion below for creating an executable jar file. After building the executable jar file, I placed it in
my bin directory under my HOME directory.
The utility provides the option --help for runtime usage instructions.
Output is written to the standard output stream - one line per matching hash. Errors are written to standard error stream.
Exit code is non-zero when errors are detected.
Execute the utility using the following command. A bash script can simplify this by combining the first three terms.
### Command Line Invocation
java -jar ~/bin/filedupfiles.jar [options] <dir1> [... <dirN>]

### Sample Output
DUPLICATES: /home/don/test/file01.txt /home/don/test/file02.txt /home/don/test/dir1/file03.txt

## CAUTION
This utility relies on duplicate files having the same MD5 hash.
It is conceivable but unlikely that two files can have the same MD5 hash yet different contents.
VERIFY DUPLICATION BEFORE DELETING ANY DATA!
This is easily done with the GNU "diff" or any similar utility.
I am concidering an enhancement that would allow a command line option to specify a byte for byte comparison before deciding
files are duplicates. This would impact performance significantly on big files.

## Limitations
This utility has only been tested on GNU/Linux, but with a tweek, should function on MS Windows.
The program contains a function for obtaining the home directory (getHomeDir)
that uses the environment variable "HOME" which exists on Linux/UNIX systems.
I don't remember if this exists on MS Windows. To support MSW, getHomeDir should be enhanced to detect the running OS and use
The correct environment variable(s) to locate the user home directory. Access to the user home directory is required for creating
the database directory that stores the file hashes and found duplicate file records.
A workaround for MSW that should allow this version to work is define the "HOME" environment variable containing
the directory name of your home directory.

## Dependencies
I am currently using openjdk-8 for my Java version, but the most exotic Java feature used in this source is generics.
You could probably use a previous version of Java if you require it.
This utility uses a realational database to record the MD5 hashes for all files that are searched.
I chose "derby" as the RDBM. See the link below. This library is implemented in Java and can operate in embedded or server modes.
This utility currently uses embedded mode and stores the database in ~/.finddupfiles/db/.
This library was already built in my GNU/Linux distribution and I was able to install it using "apt".
Derby also includes a command line shell that allows interactive SQL access to the database.
As the database contains the file and path names for all searched files, it could be useful for quickly locating files
by query.

## Build
I originally built this using the "eclipse" IDE. Create a new workspace called "FindDuplicateFiles" and
a java project called "FindDupFiles". Move the contents of this git project into that workspace.
If using "eclipse", modify the project build path to include the external archive "derby.jar".
To package the executable, eclipse has a feature to export the project to an executable jar file.

## Notes
On subsequent executions, the database rows are purged and recreated.
I am considering an enhancement that updates the database on subsequent executions instead of recreating it.

## Tips
Use the derby ij interactive shell to examine the database contents. You may find the data useful for automating duplicate management.

## Links
* https://db.apache.org/derby
