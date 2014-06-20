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
package com.enaikoon.android.service.opencellid.upload.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.enaikoon.android.service.opencellid.library.data.ApiKeyHandler;
import com.enaikoon.android.service.opencellid.library.data.Measurement;
import com.enaikoon.android.service.opencellid.library.data.Network;
import com.enaikoon.android.service.opencellid.library.db.MeasurementsDatabase;
import com.enaikoon.android.service.opencellid.library.db.MeasurementsTable.MeasurementsDBIterator;
import com.enaikoon.android.service.opencellid.library.db.NetworksTable.NetworkDBIterator;
import com.enaikoon.android.service.opencellid.library.db.OpenCellIdLibContext;
import com.enaikoon.android.service.opencellid.upload.OpenCellIDUploadService;
import com.enaikoon.android.service.opencellid.upload.utils.NetUtils;

/**
 * This service will upload data to OpenCellID servers.
 * Use {@link OpenCellIDUploadService} to control it.
 * 
 * @author fox
 * @author Roberto Gonzalez
 * @author Danijel
 * @author Dinko Ivkovic
 */
public class MeasurementsUploaderService extends Service {
	private OpenCellIdLibContext mLibContext;
	private MeasurementsDatabase mDatabase;
	
	/**
	 * MeasurementsUploaderService parameters
	 */
	private String appId;
	private String apiKey = "";
	private String openCellUrl;
	private String networksUrl;
	private long newDataCheckInterval = UploadConstants.NEW_DATA_CHECK_INTERVAL_LONG_DEFAULT;
	private boolean wifiOnly = UploadConstants.PREF_ONLY_WIFI_UPLOAD_DEFAULT;
	private boolean testEnvironment = UploadConstants.PREF_TEST_ENVIRONMENT;
	
	private Thread uploadThread;
	private static volatile boolean uploadThreadRunning;
	
	private HttpClient httpclient;
	private HttpPost httppost;
	
	private static final String MULTIPART_FILENAME = "upload.csv";
	private static final int MEASUREMENTS_BATCH_SIZE = 1024;
	
	/**
	 * Configuration change receiver. Anyone can send broadcasts to this service
	 * with changed parameters.
	 */
	private BroadcastReceiver configurationReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			
			writeToLog("MeasurementsUploaderService configurationReceiver.onReceive()");
			
			if (intent!=null)
			{
				Bundle extras = intent.getExtras();
				getExtraParameters(extras);	
			}
		}
	};

	private void writeToLog(String message) {
		mLibContext.getLogService().writeLog(Log.INFO, OpenCellIdLibContext.LOG_FILENAME_PREFIX, message);
	}

	private void writeExceptionToLog(Exception ex) {
		mLibContext.getLogService().writeErrorLog(OpenCellIdLibContext.LOG_FILENAME_PREFIX, "", ex);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mLibContext = OpenCellIdLibContext.getInstance() == null ? new OpenCellIdLibContext(getApplicationContext()) : OpenCellIdLibContext.getInstance();
		
		writeToLog("MeasurementsUploaderService onCreate()");
		
		// android.os.Debug.waitForDebugger();
		
		registerReceiver(configurationReceiver, new IntentFilter(UploadConstants.CONFIGURATION_ACTION));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		writeToLog("MeasurementsUploaderService onStartCommand()");
		
		mLibContext = OpenCellIdLibContext.getInstance() == null ? new OpenCellIdLibContext(getApplicationContext()) : OpenCellIdLibContext.getInstance();
		
		mDatabase = mLibContext.getMeasurementsDatabase();
		
		// check the received intent for configuration parameters
		configurationReceiver.onReceive(this, intent);

		//start upload process
		startUploading();
		
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		writeToLog("MeasurementsUploaderService onDestroy()");
		
		// stop upload process
		uploadThreadRunning = false;
		
		if (uploadThread != null) {
			try {
				uploadThread.interrupt();
			} catch (Exception e) {}
			uploadThread = null;
		}
		
		unregisterReceiver(configurationReceiver);
	}
	
	/**
	 * reads API key
	 */
	private void retrieveApiKey() {
		if (apiKey == null || apiKey.length() == 0) {
			String temp = ApiKeyHandler.getApiKey();
			if (temp == null)
				writeToLog("Cannot get API key!");
			else
				apiKey = temp;
		}
	}
	
	/**
	 * Starts uploading data.
	 */
	private void startUploading() {
		if (uploadThread != null) {
			writeToLog("Upload thread active");
			return;
		}
		
		uploadThread = new Thread(new Runnable() {
			@Override
			public void run() {
				uploadThreadRunning = true;
				
				while (uploadThreadRunning) {
					//get API key
					retrieveApiKey();
					
					//get a list of non uploaded measurements
					MeasurementsDBIterator dbIterator = mDatabase.getNonUploadedMeasurements();
					boolean success = true;
					String errorMsg = "";
					int max = 0;
					
					int count = 0;
					
					// stop uploading if the upload is working only when the wifi is active 
					if (wifiOnly && !NetUtils.isWifiConnected(getApplicationContext())) {
						writeToLog("WiFi only and WiFi is not connected.");
						try {
							Thread.sleep(newDataCheckInterval);
						} catch (Exception e) {}
						
						continue;
					}
					
					try {
						writeToLog("Checking if there are records for upload...");
						if (dbIterator.getCount() > 0) {
							
							httpclient = new DefaultHttpClient();
							httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);

							writeToLog("MeasurementsUploaderService startUploading() - openCellUrl=" + openCellUrl);
							
							httppost = new HttpPost(openCellUrl);

							max = dbIterator.getCount();
							count = 0;

							NumberFormat latLonFormat = NumberFormat.getNumberInstance(Locale.US);
							latLonFormat.setMaximumFractionDigits(10);

							while (dbIterator.hasNext() && uploadThreadRunning) {
								//upload measurements as a batch file
								count = uploadMeasurementsBatch(dbIterator, latLonFormat, count, max);
								Intent progress = new Intent(OpenCellIDUploadService.BROADCAST_PROGRESS_ACTION);
							    progress.putExtra(OpenCellIDUploadService.XTRA_MAXPROGRESS, max);
							    progress.putExtra(OpenCellIDUploadService.XTRA_PROGRESS, count);
							    sendBroadcast(progress);
							}

							// set all measurements as uploaded
							if (uploadThreadRunning) {
								mDatabase.setAllMeasurementsUploaded();
							}
							
							
							// start uploading networks data
							if (uploadThreadRunning) {
								uploadNetworks();
							}
							
							writeToLog("Uploaded " + count + " of " + max + " records.");
							httpclient.getConnectionManager().shutdown();
							success = true;
						} else {
							writeToLog("No records for upload right now.");
						}
					
					} catch(Exception e) {
						writeExceptionToLog(e);
						success = false;
						errorMsg = e.getMessage();
					} finally {
						dbIterator.close();
					}
					
					Intent progress = new Intent(OpenCellIDUploadService.BROADCAST_PROGRESS_ACTION);
					progress.putExtra(OpenCellIDUploadService.XTRA_MAXPROGRESS, max);
					progress.putExtra(OpenCellIDUploadService.XTRA_PROGRESS, count);
					progress.putExtra(OpenCellIDUploadService.XTRA_DONE, true);
					progress.putExtra(OpenCellIDUploadService.XTRA_SUCCESS, success);
					
					if (!success) {
					    progress.putExtra(OpenCellIDUploadService.XTRA_FAILURE_MSG, errorMsg);
					}
					sendBroadcast(progress);
					
					try {
						Thread.sleep(newDataCheckInterval);
					} catch (Exception e) {}
				}
			}
		});
		uploadThread.start();
	}
	
	/**
	 * uploads measurements data as a batch file
	 * @param dbIterator
	 * @param latLonFormat
	 * @param count
	 * @param max
	 * @return number of uploaded measurements
	 */
	private int uploadMeasurementsBatch(MeasurementsDBIterator dbIterator, NumberFormat latLonFormat, int count, int max) {
		writeToLog("uploadMeasurementsBatch(" + count + ", " + max + ")");

		try {
			StringBuilder sb = new StringBuilder("lat,lon,mcc,mnc,lac,cellid,signal,measured_at,rating,speed,direction,act\n");

			int thisBatchSize = 0;
			while (thisBatchSize < MEASUREMENTS_BATCH_SIZE && dbIterator.hasNext() && uploadThreadRunning) {
				Measurement meassurement = dbIterator.next();

				sb.append(latLonFormat.format(meassurement.getLat())).append(",");
				sb.append(latLonFormat.format(meassurement.getLon())).append(",");
				sb.append(meassurement.getMcc()).append(",");
				sb.append(meassurement.getMnc()).append(",");
				sb.append(meassurement.getLac()).append(",");
				sb.append(meassurement.getCellid()).append(",");
				sb.append(meassurement.getGsmSignalStrength()).append(",");
				sb.append(meassurement.getTimestamp()).append(",");
				sb.append((meassurement.getAccuracy()!=null)?meassurement.getAccuracy():"").append(",");				
				sb.append((int) meassurement.getSpeed()).append(",");
				sb.append((int) meassurement.getBearing()).append(",");
				sb.append((meassurement.getNetworkType()!=null)?meassurement.getNetworkType():"");
				sb.append("\n");
				
				thisBatchSize++;
			}

			HttpResponse response = null;

			writeToLog("Upload request URL: " + httppost.getURI());

			if (uploadThreadRunning) {
				String csv = sb.toString();

				writeToLog("Upload data: " + csv);

				MultipartEntity mpEntity = new MultipartEntity();
				mpEntity.addPart("key", new StringBody(apiKey));
				mpEntity.addPart("appId", new StringBody(appId));
				mpEntity.addPart("datafile", new InputStreamBody(new ByteArrayInputStream(csv.getBytes()), "text/csv", MULTIPART_FILENAME));

				ByteArrayOutputStream bArrOS = new ByteArrayOutputStream();
				// reqEntity is the MultipartEntity instance
				mpEntity.writeTo(bArrOS);
				bArrOS.flush();
				ByteArrayEntity bArrEntity = new ByteArrayEntity(bArrOS.toByteArray());
				bArrOS.close();

				bArrEntity.setChunked(false);
				bArrEntity.setContentEncoding(mpEntity.getContentEncoding());
				bArrEntity.setContentType(mpEntity.getContentType());

				httppost.setEntity(bArrEntity);

				response = httpclient.execute(httppost);
				if (response == null) {
					writeToLog("Upload: null HTTP-response");
					throw new IllegalStateException("no HTTP-response from server");
				}

				HttpEntity resEntity = response.getEntity();

				writeToLog("Upload: " + response.getStatusLine().getStatusCode() + " - " + response.getStatusLine());

				if (resEntity != null) {
					resEntity.consumeContent();
				}

				if (response.getStatusLine() == null) {
					writeToLog(": " + "null HTTP-status-line");
					throw new IllegalStateException("no HTTP-status returned");
				}

				if (response.getStatusLine().getStatusCode() != 200) {
					throw new IllegalStateException("HTTP-status code returned : " + response.getStatusLine().getStatusCode());
				}
			}

			return count + thisBatchSize;

		} catch (IOException e) {
			throw new IllegalStateException("IO-Error: " + e.getMessage());
		}
	}

	/**
	 * Used to upload entries from networks table to OCID servers.
	 */
	private void uploadNetworks() {
		writeToLog("uploadNetworks()");

		String existingFileName = "uploadNetworks.csv";
		String data = null;

		NetworkDBIterator dbIterator = mDatabase.getNonUploadedNetworks();
		try {
			if (dbIterator.getCount() > 0) {
				//timestamp, mcc, mnc, net (network type), nen (network name)
				StringBuilder sb = new StringBuilder("timestamp,mcc,mnc,net,nen" + ((char) 0xA));

				while (dbIterator.hasNext() && uploadThreadRunning) {
					Network network = dbIterator.next();
					sb.append(network.getTimestamp()).append(",");
					sb.append(network.getMcc()).append(",");
					sb.append(network.getMnc()).append(",");
					sb.append(network.getType()).append(",");
					sb.append(network.getName());
					sb.append(((char) 0xA));
				}

				data = sb.toString();

			} else {
				writeToLog("No networks for upload.");
				return;
			}
		} finally {
			dbIterator.close();
		}

		writeToLog("uploadNetworks(): " + data);

		if (uploadThreadRunning) {
			
			try {
				httppost = new HttpPost(networksUrl);
				
				HttpResponse response = null;

				writeToLog("Upload request URL: " + httppost.getURI());

				if (uploadThreadRunning) {
					MultipartEntity mpEntity = new MultipartEntity();
					mpEntity.addPart("apikey", new StringBody(apiKey));
					mpEntity.addPart("datafile", new InputStreamBody(new ByteArrayInputStream(data.getBytes()), "text/csv", existingFileName));
					
					ByteArrayOutputStream bArrOS = new ByteArrayOutputStream();
					// reqEntity is the MultipartEntity instance
					mpEntity.writeTo(bArrOS);
					bArrOS.flush();
					ByteArrayEntity bArrEntity = new ByteArrayEntity(bArrOS.toByteArray());
					bArrOS.close();

					bArrEntity.setChunked(false);
					bArrEntity.setContentEncoding(mpEntity.getContentEncoding());
					bArrEntity.setContentType(mpEntity.getContentType());

					httppost.setEntity(bArrEntity);

					response = httpclient.execute(httppost);
					if (response == null) {
						writeToLog("Upload: null HTTP-response");
						throw new IllegalStateException("no HTTP-response from server");
					}

					HttpEntity resEntity = response.getEntity();

					writeToLog("Response: " + response.getStatusLine().getStatusCode() + " - " + response.getStatusLine());
					
					if (resEntity != null) {
						writeToLog("Response content: " + EntityUtils.toString(resEntity));
						resEntity.consumeContent();
					}
				}

				if (uploadThreadRunning) {
					if (response == null) {
						writeToLog(": " + "null response");

						throw new IllegalStateException("no response");
					}
					if (response.getStatusLine() == null) {
						writeToLog(": " + "null HTTP-status-line");

						throw new IllegalStateException("no HTTP-status returned");
					}
					if (response.getStatusLine().getStatusCode() == 200) {
						mDatabase.setAllNetworksUploaded();
					} else if (response.getStatusLine().getStatusCode() != 200) {
						throw new IllegalStateException(response.getStatusLine().getStatusCode() + " HTTP-status returned");
					}
				}

			} catch (Exception e) {
				// httppost cancellation throws exceptions
				if (uploadThreadRunning) {
					writeExceptionToLog(e);
				}
			}
		}
	}
	
	/**
	 * No binder is needed.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	/**
	 * checks intent configuration parameters
	 * @param extras
	 */
	private void getExtraParameters(Bundle extras)
	{
		if (extras != null)
		{
			writeToLog("configurationReceiver.onReceive() extras : " + extras.keySet());

			if (extras != null) {
				appId = extras.getString(UploadConstants.PREF_APPID_KEY);
				
				if (extras.containsKey(UploadConstants.PREF_API_KEY))
					apiKey = extras.getString(UploadConstants.PREF_API_KEY);
				
				if (extras.containsKey(UploadConstants.PREF_OPENCELL_UPLOAD_URL_KEY))
					openCellUrl = extras.getString(UploadConstants.PREF_OPENCELL_UPLOAD_URL_KEY);
				
				if (extras.containsKey(UploadConstants.PREF_OPENCELL_NETWORK_UPLOAD_URL_KEY))
					networksUrl = extras.getString(UploadConstants.PREF_OPENCELL_NETWORK_UPLOAD_URL_KEY);
				
				testEnvironment = extras.getBoolean(UploadConstants.PREF_TEST_ENVIRONMENT_KEY, UploadConstants.PREF_TEST_ENVIRONMENT);	
				
				if (openCellUrl == null)
				{
					if (testEnvironment)
					{
						openCellUrl = UploadConstants.OPEN_CELL_TEST_UPLOAD_URL;
					}
					else
					{
						openCellUrl = UploadConstants.OPEN_CELL_DEFAULT_UPLOAD_URL;	
					}	
				}
				
				if (networksUrl == null)
				{
					if (testEnvironment)
					{
						networksUrl = UploadConstants.OPENCELL_NETWORKS_TEST_UPLOAD_URL;
					}
					else
					{
						networksUrl = UploadConstants.OPENCELL_NETWORKS_UPLOAD_URL;	
					}	
				}				
				
				if (extras.containsKey(UploadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY))
					newDataCheckInterval = extras.getLong(UploadConstants.PREF_NEW_DATA_CHECK_INTERVAL_KEY);
				
				if (newDataCheckInterval < UploadConstants.NEW_DATA_CHECK_INTERVAL_LONG_DEFAULT)
					newDataCheckInterval = UploadConstants.NEW_DATA_CHECK_INTERVAL_LONG_DEFAULT;
				
				if (extras.containsKey(UploadConstants.PREF_ONLY_WIFI_UPLOAD_KEY))
					wifiOnly = extras.getBoolean(UploadConstants.PREF_ONLY_WIFI_UPLOAD_KEY);
			}
			
			writeToLog("onConfigurationReceiver ()");
			writeToLog("apiKey = " + apiKey);
			writeToLog("openCellUrl = " + openCellUrl);
			writeToLog("networks url = " + networksUrl);
			writeToLog("newDataCheckInterval = " + newDataCheckInterval);
			writeToLog("wifiOnly = " + wifiOnly);
			writeToLog("testEnvironment = " + testEnvironment);			
		}
	}	
}
