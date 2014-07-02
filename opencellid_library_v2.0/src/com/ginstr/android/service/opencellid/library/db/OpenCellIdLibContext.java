/**
 * Copyright 2014 ginstr GmbH
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial 4.0 International License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/4.0/ 
 *  
 *  Unless required by applicable law or agreed to in writing, software 
 *  distributed under the License is distributed on an "AS IS" BASIS, 
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  See the License for the specific language governing permissions and 
 *  limitations under the License. 
 */
package com.ginstr.android.service.opencellid.library.db;

import java.io.File;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.ginstr.android.service.logservice.LogService;

/**
 * <p>
 * This class is needed to operate on the database.
 * Instance of this class is used in every database operation and
 * services writing or reading from database need it.
 * </p>
 * 
 * <p>This class uses its own LogService instance and in every query
 * all the logging uses it. </p>
 * 
 * @author Roberto Gonzalez, Danijel Korunek
 */
public class OpenCellIdLibContext {
	public static final String LOG_FILENAME_PREFIX = "opencellidlibrary";
	
	public static final String SERVER_URL="http://opencellid.org";
	public static final String SERVER_URL_TEST="http://test-ocid.enaikoon.de";
	
	private Context mContext;

	private LogService log;

	private static String mApplicationDirectoryName = Environment.getExternalStorageDirectory() + "/opencellid/";
	private String mMeasurementsDataBaseName = "OCID_measurements.db3";
	private String mCellsDataBaseName = "OCID_cells.db3";

	private MeasurementsDatabase mMeasurementsDatabase;
	private CellsDatabase mCellsDatabase;

	private static OpenCellIdLibContext instance;

	public static OpenCellIdLibContext getInstance() {
		return instance;
	}

	public OpenCellIdLibContext(Context context) {
		mContext = context;
		
		log = new LogService(context);
		log.setAndroidLoggingEnabled(true);
		log.setFileLoggingEnabled(false);
		log.setLogDirectory(new File(OpenCellIdLibContext.getApplicationDirectoryName()));
		log.setLogTag(LOG_FILENAME_PREFIX);
		log.startService();
		
		// create directory since SQL helper cannot
		File dir = new File(mApplicationDirectoryName);
		dir.mkdirs();
		
		mMeasurementsDatabase = new MeasurementsDatabase(context, this);
		
		instance = this;
	}

	/**
	 * @return database size in MB
	 */
	public int getDatabaseSize() {
		try {
			File f = new File(getMeasurementsDataBaseFullPath());
			return (int) f.length() / (1024 * 1024);
		} catch (Exception ex) {
			return 0;
		}
	}

	/**
	 * @return Cells database size in MB
	 */
	public int getCellsDatabaseSize() {
		try {
			File f = new File(getCellsDataBaseFullPath());
			return (int) f.length() / (1024 * 1024);
		} catch (Exception ex) {
			return 0;
		}
	}
	
	/**
	 * Determine if the CELLS database size is to big. It shouldn't exceed
	 * configured maximum size and by growing it should leave minimum free
	 * space on the SD card.
	 *  
	 * @param maxDbSize in Mb	
	 * @param minFreeSpace in Mb
	 * @return false if the database is to big or if by growing 
	 * it would exceed the configured minimum free space 
	 */
	public boolean isCellsDatabaseValid(int maxDbSize, int minFreeSpace) {
		int dbSize = getCellsDatabaseSize();
		int storageFreeSpace = getStorageFreeSpace();

		if (dbSize > maxDbSize || storageFreeSpace < minFreeSpace) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * @return the external storage free space in MB
	 */
	public int getStorageFreeSpace() {
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File path = Environment.getExternalStorageDirectory();
			StatFs stat = new StatFs(path.getPath());

			// tmp variable to make the math on a long value as it would
			// overflow a int one
			long res = stat.getAvailableBlocks() * (long) stat.getBlockSize()
					/ (1024L * 1024L);

			return (int) res;
		} else {
			return 0;
		}
	}

	/**
	 * Determine if the database size is to big. It shouldn't exceed
	 * configured maximum size and by growing it should leave minimum free
	 * space on the SD card.
	 *  
	 * @param maxDbSize in Mb	
	 * @param minFreeSpace in Mb
	 * @return false if the database is to big or if by growing 
	 * it would exceed the configured minimum free space 
	 */
	public boolean isDatabaseValid(int maxDbSize, int minFreeSpace) {
		int dbSize = getDatabaseSize();
		int storageFreeSpace = getStorageFreeSpace();

		if (dbSize > maxDbSize || storageFreeSpace < minFreeSpace) {
			return false;
		} else {
			return true;
		}
	}

	public static String getApplicationDirectoryName() {
		return mApplicationDirectoryName;
	}

	/**
	 * defines the path where the database and log file will be stored
	 * 
	 * @param applicationDirectoryName
	 */
	public OpenCellIdLibContext setApplicationDirectoryName(String applicationDirectoryName) {
		mApplicationDirectoryName = applicationDirectoryName;
		return this;
	}

	/**
	 * 
	 * @param filename
	 * @return the full path of the given file in the application folder
	 */
	public String getApplicationFilePath(String filename) {
		return mApplicationDirectoryName + filename;
	}

	public String getMeasurementsDataBaseFullPath() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ECLAIR_MR1) {
			return mMeasurementsDataBaseName;
		} else {
			return mApplicationDirectoryName + mMeasurementsDataBaseName;
		}
	}

	public String getCellsDataBaseFullPath() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ECLAIR_MR1) {	// <= API 2.1
			return mCellsDataBaseName;
		} else {
			return mApplicationDirectoryName + mCellsDataBaseName;
		}
	}


	public Context getContext() {
		return mContext;
	}

	/**
	 * @return The database that stores the measurements
	 */
	public MeasurementsDatabase getMeasurementsDatabase() {
		return mMeasurementsDatabase;
	}

	/**
	 * The database where the cells are stored.
	 * NEVER cache this instance as it may change;
	 * @return The database where the cells are stored.
	 */
	public CellsDatabase getCellsDatabase() {
		if (mCellsDatabase == null) {
			mCellsDatabase = new CellsDatabase(mContext, this);
		}
		return mCellsDatabase;
	}

	public synchronized String refreshCellsDatabase() {


		CellsDatabase db = mCellsDatabase; 
		if (db != null) {
			mCellsDatabase = null;
			db.close();
		}

		return getCellsDataBaseFullPath();
	}

	/**
	 * @return LogService instance
	 */
	public LogService getLogService() {
		return log;
	}
	
	public static String getServerURL(boolean isTestServer)
	{
		if (isTestServer)
		{
			return SERVER_URL_TEST;
		}
		else
		{
			return SERVER_URL;
		}
	}
}
