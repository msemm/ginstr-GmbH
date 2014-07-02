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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.ginstr.android.service.opencellid.collect.data.ConfigurationConstants;
import com.ginstr.android.service.opencellid.library.data.ApiKeyHandler;
import com.ginstr.android.service.opencellid.library.data.Importer;
import com.ginstr.android.service.opencellid.library.db.OpenCellIdLibContext;

/**
 * 
 * This service is used to download and import cells into cells database.
 * It's managed from OpenCellIdDownloadService class.
 * 
 * @author Danijel
 * @author Dinko Ivkovic
 *
 */
public class CellsDownloadService extends Service {

	private static final String LOG_TAG = CellsDownloadService.class.getSimpleName();
	
	/**
	 * Register a broadcast receiver to receive updates from this server.
	 * Extras will be total size, progress and error if any.
	 */
	public static final String DOWNLOAD_PROGRESS_ACTION = "com.ginstr.android.service.opencellid.download.DOWNLOAD_PROGRESS";
	/**
	 * Used to read an integer denoting progress in bytes download.
	 */
	public static final String EXTRA_PROGRESS = "progress";
	
	/**
	 * Used to read an integer denoting the total amount of bytes to download.
	 */
	public static final String EXTRA_TOTAL = "total";
	
	/**
	 * Used to read a string hold an error message if it happened.
	 */
	public static final String EXTRA_ERROR = "error";
	
	// default parameter values
	private static volatile long sleepTime = DownloadConstants.NEW_DATA_CHECK_INTERVAL_DEFAULT;
	private int cellsDbSize = DownloadConstants.CELLS_DATABASE_SIZE_DEFAULT;
	private int minFreeSpace = DownloadConstants.MIN_FREE_SPACE_DEFAULT;
	private String downloadUrl = DownloadConstants.DOWNLOAD_URL_DEFAULT;
	private String apiKey = "";
	private boolean testEnvironment = false;
	private int maxLogFileSize = DownloadConstants.MAX_LOG_SIZE_DEFAULT;
	private boolean logToFileEnabled=DownloadConstants.LOG_TO_FILE_DEFAULT;
	
	private OpenCellIdLibContext libContext;
	
	/**
	 * name of file which is used to store downloaded cell data
	 */
	private String filename;
	
	private static volatile boolean running;
	
	private class DownloadThread extends Thread {
		@Override
		public void run() {
			running = true;
			filename = OpenCellIdLibContext.getApplicationDirectoryName() + "download.tmp";
			
			while (running) {
				int progress = 0;
				int total = 0;
				
				// check if the database is valid
				if (!libContext.isCellsDatabaseValid(cellsDbSize, minFreeSpace)) {
					String error = "Cells database is to big (greater than " + cellsDbSize + " Mb) or " +
						           "there's to little space on the SD card (less than " + minFreeSpace + " Mb)." +
								   "Download has been aborted.";
					logError(error, null);
					sendProgress(0, 0, error);
				} else {
					boolean downloaded = false;
					//check if the apiKey is already provided
					if (apiKey == null || apiKey.length() == 0) {
						// get api key
						apiKey = ApiKeyHandler.getApiKey();
						if (apiKey == null) {
							logError("Cannot retrieve API key. Waiting for next run", null);
							sendProgress(0, 0, "Cannot retrieve API key. Waiting for next run...");
							
							// sleep
							try {
								Thread.sleep(sleepTime);
							} catch (Exception e) {}
							
							continue;
						}						
					}
					
					logDebug("Api key is: " + apiKey);
					try {
						// add api key to download URL
						if (!downloadUrl.contains("key=" + apiKey)) {
							downloadUrl += "&key=" + apiKey;
						}
						
						URL url = new URL(downloadUrl);
						HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
						urlConnection.setRequestMethod("GET");
						urlConnection.setConnectTimeout(20000);
						urlConnection.setReadTimeout(20000);

						logDebug("URL: " + downloadUrl);
						logDebug("ReadTimeOut: "+urlConnection.getReadTimeout() + " ConnectTimeOut: " + urlConnection.getConnectTimeout());


						File from = new File(filename);

						// delete old download file
						if (from.exists()) {
							from.delete();
						}
						urlConnection.setDoInput(true);
						
						urlConnection.connect();
						total = urlConnection.getContentLength();

						sendProgress(progress, total, "");

						// write cell data to defined file
						FileOutputStream fos = new FileOutputStream(from, false);
						InputStream inputStream = new BufferedInputStream(new GZIPInputStream(urlConnection.getInputStream()));

						byte[] buffer = new byte[1024*32];
						int read = 0;
						while (running && (read = inputStream.read(buffer)) > 0 ) {
							fos.write(buffer, 0, read);
							progress += read;

							sendProgress(progress, total, "");
						}

						fos.flush();
						fos.close();

						urlConnection.disconnect();

						logDebug("Download finished!");
						downloaded = true;
					} catch (Exception e) {
						logError("Download failed", e);
						sendProgress(progress, total, e.getMessage());
						downloaded = false;
					}
					
					// check if the cell data is downloaded successfully 
					if (downloaded) {
						// import the cell data into database
						try {
							int num = Importer.importCells(libContext, true, filename);
							logDebug("Imported " + num + " cells");
						} catch (Exception e) {
							logError("Failed to import cells", e);
						}
					}
				}
				
				// sleep for defined time
				try {
					Thread.sleep(sleepTime);
				} catch (Exception e) {}
			}
		}
	}
	
	/**
	 * thread responsible for cell data download
	 */
	private DownloadThread downloadThread;
	
	/**
	 * broadcast receiver responsible for service configuration parameters
	 */
	private BroadcastReceiver configReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle extras = intent.getExtras();
			getExtraParameters(extras);
		}
	};
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		android.os.Debug.waitForDebugger();
		
		libContext = new OpenCellIdLibContext(this);
		
		registerReceiver(configReceiver, 
					     new IntentFilter(DownloadConstants.DOWNLOAD_SETTTINGS_RECEIVER_ACTION));
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		running = false;
		if (downloadThread != null) {
			downloadThread.interrupt();
			downloadThread = null;
		}
		
		unregisterReceiver(configReceiver);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		running = true;
		
		// check if configuration parameters are provided by intent
		if (intent != null)
			configReceiver.onReceive(getApplicationContext(), intent);
		
		// start download thread
		if (downloadThread == null) {
			downloadThread = new DownloadThread();
			downloadThread.start();
		}
		return Service.START_STICKY;
	}
	
	private void logDebug(String msg) {
		libContext.getLogService().writeLog(Log.DEBUG, LOG_TAG, msg);
	}
	
	private void logError(String msg, Throwable t) {
		libContext.getLogService().writeErrorLog(LOG_TAG, msg, t);
	}
	
	private void sendProgress(int progress, int total, String error) {
		Intent i = new Intent(DOWNLOAD_PROGRESS_ACTION);
		i.putExtra(EXTRA_ERROR, error);
		i.putExtra(EXTRA_PROGRESS, progress);
		i.putExtra(EXTRA_TOTAL, total);
		libContext.getContext().sendBroadcast(i);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	/**
	 * reads the parameters from the received Intent object
	 * @param extras
	 */
	private void getExtraParameters(Bundle extras)
	{
		if (extras != null)
		{
			logDebug("configurationReceiver.onReceive() extras : " + extras.keySet());

			if (extras.containsKey(DownloadConstants.PREF_CELLS_DATABASE_SIZE_KEY)) {
				cellsDbSize = extras.getInt(DownloadConstants.PREF_CELLS_DATABASE_SIZE_KEY, 
												 DownloadConstants.CELLS_DATABASE_SIZE_DEFAULT);
			}
			
			if (extras.containsKey(DownloadConstants.PREF_MIN_FREE_SPACE_KEY)) {
				minFreeSpace = extras.getInt(DownloadConstants.PREF_MIN_FREE_SPACE_KEY, 
												  DownloadConstants.MIN_FREE_SPACE_DEFAULT);
			}
			
			if (extras.containsKey(DownloadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY)) {
				sleepTime = extras.getLong(DownloadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY, 
												DownloadConstants.NEW_DATA_CHECK_INTERVAL_DEFAULT);
			}
			
			if (extras.containsKey(DownloadConstants.PREF_DOWNLOAD_URL_KEY)) {
				downloadUrl = extras.getString(DownloadConstants.PREF_DOWNLOAD_URL_KEY);
			}
			
			if (extras.containsKey(DownloadConstants.PREF_API_KEY_KEY)) {
				apiKey = extras.getString(DownloadConstants.PREF_API_KEY_KEY);
			}
			
			testEnvironment = extras.getBoolean(DownloadConstants.PREF_TEST_ENVIRONMENT_KEY, DownloadConstants.PREF_TEST_ENVIRONMENT);	
			
			// check if the maxLogFileSize parameter is provided through intent			
			if (extras.containsKey(DownloadConstants.PREFKEY_MAX_LOG_SIZE_INT))
			{
				maxLogFileSize = extras.getInt(DownloadConstants.PREFKEY_MAX_LOG_SIZE_INT);
				
				libContext.getLogService().setMaxLogFileSize(maxLogFileSize);
			}
			
			// check if the logToFileEnabled parameter is provided through intent			
			if (extras.containsKey(DownloadConstants.PREFKEY_LOG_TO_FILE))
			{
				logToFileEnabled = extras.getBoolean(DownloadConstants.PREFKEY_LOG_TO_FILE);
				
				libContext.getLogService().setFileLoggingEnabled(logToFileEnabled);
			}			
		}
	}	
}
