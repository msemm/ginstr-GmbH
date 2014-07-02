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
package com.ginstr.android.service.opencellid.download;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.ginstr.android.service.baseservice.BaseService;

/**
 * <h2>General description</h2>
 * <p>This service is used for downloading cells from OpenCellID servers.
 * Data is stored into 'cells' table in cells database.
 * It will run as a 'singleton'.</p>
 * 
 * <p>
 * To run it you need to include permission to your AndroidManifest.xml:<br/>
 * <code>android.permission.ACCESS_NETWORK_STATE</code>
 * </p>
 * 
 * <h2>Usage</h2>
 * 
 * <p>When you create an instance of this service, you can pass the configuration parameters
 * in the bundle. Otherwise service will run with defaults.
 * </p>
 * 
 * <p>Service can be started so that you create an instance of this object and then call
 * <code>startService()</code>.
 * </p>
 * 
 * <p>All the setter function for configuration will update parameters when the service is running.
 * The effect might take place in the next cycle. </p>
 * 
 * <p>You can configure database size and minimum space to determine database growth by
 * calling {@link #setMaxCellsDatabaseSize(int)} and {@link #setMinFreeSpace(int).</p>
 * 
 * <p>Download URL can be set by {@link #setDownloadUrl(String)}.</p>
 * 
 * <p>Server data will be checked every configured time interval continuously.
 * You can set it by calling {@link #setServerUpdateTime(long)}.
 * </p>
 * <p>Download progress will be broadcasted so if you want to receive updates
 * on downloads, you can register a BroadcastReceiver object in your activity for
 * the following action: {@link CellsDownloadService#DOWNLOAD_PROGRESS_ACTION}. <br/>
 * You can read progress extra with {@link CellsDownloadService#EXTRA_PROGRESS},
 * total bytes extra with {@link CellsDownloadService#EXTRA_TOTAL} and
 * error description with {@link CellsDownloadService#EXTRA_ERROR}. </p>
 * 
 * <p>Service can be stopped by calling <code>stopService()</code></p>
 * 
 * @author Dinko Ivkovic
 */
public class OpenCellIDDownloadService extends BaseService {
	private Intent serviceIntent;
	
	int dbSize = DownloadConstants.CELLS_DATABASE_SIZE_DEFAULT;
	int minFreeSpaceLeft = DownloadConstants.MIN_FREE_SPACE_DEFAULT;
	long dataCheckInterval = DownloadConstants.NEW_DATA_CHECK_INTERVAL_DEFAULT;
	String downloadUrl = DownloadConstants.DOWNLOAD_URL_DEFAULT;
	String apiKey=null;
	boolean testEnvironment=DownloadConstants.PREF_TEST_ENVIRONMENT;
	int maxLogFileSize = DownloadConstants.MAX_LOG_SIZE_DEFAULT;
	boolean logToFileEnabled=DownloadConstants.LOG_TO_FILE_DEFAULT;
	
	/**
	 * Construct a service with predefined defaults.
	 * Settings from DownloadConstants file will be used.
	 * 
	 * @param context application context
	 */
	public OpenCellIDDownloadService(Context context) {
		super(context, "OPENCELLID_DOWNLOAD");
		serviceIntent = new Intent(getContext(), CellsDownloadService.class);
	}

	@Override
	public void onServiceStart() {
		if (!isStarted()) {
			//define parameters for cell collect service
			serviceIntent.putExtra(DownloadConstants.PREF_CELLS_DATABASE_SIZE_KEY, dbSize);
			serviceIntent.putExtra(DownloadConstants.PREF_MIN_FREE_SPACE_KEY, minFreeSpaceLeft);
			serviceIntent.putExtra(DownloadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY, dataCheckInterval);
			serviceIntent.putExtra(DownloadConstants.PREF_DOWNLOAD_URL_KEY, downloadUrl);
			serviceIntent.putExtra(DownloadConstants.PREF_API_KEY_KEY, apiKey);
			serviceIntent.putExtra(DownloadConstants.PREF_TEST_ENVIRONMENT_KEY, testEnvironment);
			serviceIntent.putExtra(DownloadConstants.PREFKEY_MAX_LOG_SIZE_INT, maxLogFileSize);
			serviceIntent.putExtra(DownloadConstants.PREFKEY_LOG_TO_FILE, logToFileEnabled);
			
			// start collect service
			getContext().startService(serviceIntent);
		}
	}

	@Override
	public void onServiceStop() {
		getContext().stopService(serviceIntent);
	}

	/**
	 * checks if the service is started
	 * @return true if service is running.
	 */
	@Override
	public boolean isStarted() {
		ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (CellsDownloadService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}

		return false;
	}
	/**
	 * Sets the maximum size of cells database in megabytes.
	 * If database growth exceeds this number, new entries won't
	 * be inserted.
	 * 
	 * @param size in Mb
	 */
	public void setMaxCellsDatabaseSize(int size) {
		dbSize=size;
		
		Intent i = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		i.putExtra(DownloadConstants.PREF_CELLS_DATABASE_SIZE_KEY, size);
		getContext().sendBroadcast(i);
	}
	
	/**
	 * Sets the amount of free space which must be left on the
	 * SD card. Database growth won't exceed this limit.
	 * 
	 * @param freeSpace in Mb
	 */
	public void setMinFreeSpace(int freeSpace) {
		minFreeSpaceLeft = freeSpace;
		
		Intent i = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		i.putExtra(DownloadConstants.PREF_MIN_FREE_SPACE_KEY, freeSpace);
		getContext().sendBroadcast(i);
	}
	
	/**
	 * Sets the interval in which the server will be queried for new cells.
	 * Data will be downloaded and inserted into cells database.
	 * 
	 * @param interval in milliseconds
	 */
	public void setServerUpdateTime(long interval) {
		dataCheckInterval=interval;
		
		Intent i = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		i.putExtra(DownloadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY, interval);
		getContext().sendBroadcast(i);
	}
	
	/**
	 * Sets the download URL.<br/>
	 * Download URL should have the following parameters:<br/>
	 * - key - used to download data. Random key or the one you got from registration. If the key is not supplied,
	 * service will attempt to read it from a file or request a new one from the server. <br/>
	 * - timestamp - to get towers younger than the timestamp <br/>
	 * - lon1 - left border of the area; value should be in range [-180;180] <br/>
	 * - lon2 - right border of the area; value should be in range [-180;180] <br/>
	 * - lat1 - bottom border of the area; value should be in range [-90;90] <br/>
	 * - lat2 - top border of the area; value should be in range [-90;90] <br/>
	 * - searchtype - to get towers younger than the timestamp according field: "N" for "created", "U" or "A" for "updated". ("U" and "A" are the same because "updated" field is always younger or equal than "created")<br/>
	 * <p>"Normal" case where lon1 &lt; lon2:
	 * If you are in position with long=6.0, and you need area with +/-10 degrees, you have to set in params: lon1=-4.0&lon2=10
	 * </p>
	 * <p>Special case where lon1 might be greater than lon2:<br/>
	 * If you are in "Pacific area" position with long=179.0, and you need area with +/-10 degrees, you have to set in params: lon1=169.0&lon2=-171
	 * </p>
	 * <p>
	 * Example: <br/>
	 * <code>http://opencellid.org/cell/downloadCells?timestamp=1393286900000&lon1=6.0&lat1=50.01&lon2=7.0&lat2=51.0&searchtype=A&key=testkey<br/>
	 * </p>
	 * @param url Must not be null and should be valid
	 */
	public void setDownloadUrl(String url) {
		downloadUrl=url;
		
		Intent i = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		i.putExtra(DownloadConstants.PREF_DOWNLOAD_URL_KEY, url);
		getContext().sendBroadcast(i);
	}
	
	/**
	 * Sets the API key used to download cells. Without this key you cannot download.
	 * If it's empty or null, service will request a random key from the server.
	 * 
	 * @param apiKey
	 */
	public void setApiKey(String apiKey) {
		this.apiKey=apiKey;
		
		Intent i = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		i.putExtra(DownloadConstants.PREF_API_KEY_KEY, apiKey);
		getContext().sendBroadcast(i);
	}
	
	/**
	 * Sets the log file size in megabytes
	 * @param size in Mb
	 */
	public void setLogFileSize(int size) {
		maxLogFileSize=size;
		
		Intent cfg = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(DownloadConstants.PREFKEY_MAX_LOG_SIZE_INT, size);
		getContext().sendBroadcast(cfg);
	}	
	
	/**
	 * Defines if the log to file feature is enabled
	 * @param flag true to enable log to file
	 */
	public void setLogToFileEnabled(boolean flag) {
		logToFileEnabled=flag;
		
		Intent cfg = new Intent(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(DownloadConstants.PREFKEY_LOG_TO_FILE, flag);
		getContext().sendBroadcast(cfg);
	}		
}
