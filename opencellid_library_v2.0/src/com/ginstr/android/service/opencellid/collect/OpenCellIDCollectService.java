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
package com.ginstr.android.service.opencellid.collect;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;

import com.ginstr.android.service.baseservice.BaseService;
import com.ginstr.android.service.opencellid.collect.data.CellIDCollectionService;
import com.ginstr.android.service.opencellid.collect.data.ConfigurationConstants;
import com.ginstr.android.service.opencellid.library.db.OpenCellIdLibContext;

/**
 * <h2>General description</h2>
 * <p>
 * Collection service will request GPS location updates in order to get
 * GPS coordinates. Also, it will read network signal strength changes
 * and GSM locations to collect data about GSM tower cells.
 * All the data is filtered and there is a number of settings which
 * regulate data collection. </p>
 * <p>
 * One important setting is screen dimming. You should turn this option on
 * in order to collect signal strength changes when phone screen is off.
 * On Android versions before 4.3, screen will turn dim for the configured
 * amount of time.</p>
 * 
 * <h2>Usage and code examples</h2>
 * 
 * <p>
 * Your application manifest should hold the following permissions in order to use this library: <br/>
 *   android.permission.WRITE_EXTERNAL_STORAGE <br/>
 *   android.permission.INTERNET <br/>
 *   android.permission.ACCESS_FINE_LOCATION<br/>
 *   android.permission.ACCESS_COARSE_LOCATION <br/>
 *   android.permission.READ_PHONE_STATE <br/>
 *   android.permission.WAKE_LOCK </br>
 *   android.permission.ACCESS_NETWORK_STATE
 * </p>
 * 
 * <p>
 * Also you should put a service tag in your AndroidManifest.xml to reference the following: <br/><br/>
 * <code>com.ginstr.android.service.opencellid.data.collect.CellIDCollectionService</code> <br/><br/>
 * Intent filter for this service tag should have 2 actions: <br/><br/>
 * <code>com.ginstr.android.service.opencellid.data.action.START_COLLECTING</code> <br/>
 * <code>com.ginstr.android.service.opencellid.data.action.STOP_COLLECTING</code>
 * 
 * </p>
 * 
 * <p>To create this service, just instantiate it. <br/>
 * Example: <br/>
 * <code> <br/>
 * OpenCellIDCollectService cellCollectService = new OpenCellIDCollectService(this);
 * </code>
 * </p>
 *  
 * <p>To configure service, just set any parameter you want. Parameter values are propagated
 * to the service when it is running and effects in behavior are obvious after some timer expires
 * or thread runs. If you don't configure service, it will run with default values. 
 * </p>
 * Example: <br/>
 * 
 * <p>
 * <code>
 * cellCollectService.setDbSize(500); <br/>
 * cellCollectService.setMinFreeSpaceLeft(100); <br/>
 * cellCollectService.setDimScreenActivityTime(7000); <br/>
 * cellCollectService.setDimScreenWhenCollecting(true); <br/>
 * cellCollectService.setDimThreadSleepTime(20000); <br/>
 * cellCollectService.setDisablePowerSavingWhileCharging(true); <br/>
 * cellCollectService.setGpsTimeout(300000); <br/>
 * cellCollectService.setMinSameCellDistance(100); <br/>
 * cellCollectService.setMinSameCellTimeDifference(5000); <br/>
 * cellCollectService.setPowerSavingOnPercentage(50);<br/>
 * </code>
 * </p>
 * 
 * <p>To start the service, call {@link #startService()} and to stop it call {@link #stopService()}. <br/>
 * Example:<br/><br/>
 * <code>cellCollectService.startService();</code><br/>
 * </p>
 * <p>Since the service requests data from GPS, it needs to run for a minute or two to get any data. </p>
 * <code>cellCollectService.stopService();</code>
 * <br/><br/>
 * 
 * @author Danijel
 * @author Dinko Ivkovic
 */
public class OpenCellIDCollectService extends BaseService {
	
	private Intent serviceIntent;
	
	//default values for service parameters 
	long gpsTimeout=ConfigurationConstants.TURN_GPS_OFF_DEFAULT;
	boolean disablePowerSavingWhileCharging = ConfigurationConstants.DISABLE_POWER_SAVING_WHILE_CHARGING_DEFAULT;
	int powerSavingOnPercentage = ConfigurationConstants.ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DEFAULT;
	int minSameCellDistance = ConfigurationConstants.MIN_DISTANCE_BETWEEN_MEASURES_DEFAULT;
	long minSameCellTimeDifference = ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DEFAULT;
	boolean dimScreenWhenCollecting = ConfigurationConstants.DIM_SCREEN_WHEN_COLLECTING_DEFAULT;
	long dimThreadSleepTime = ConfigurationConstants.DIM_SCREEN_THREAD_SLEEP_TIME;
	int dbSize = ConfigurationConstants.MAX_DB_SIZE_DEFAULT;
	int minFreeSpaceLeft = ConfigurationConstants.MIN_FREE_SPACE;
	String notificationDataTitle=ConfigurationConstants.NOTIFICATION_TITLE;
	String notificationDataText=ConfigurationConstants.NOTIFICATION_TEXT;
	boolean collectNetworks=ConfigurationConstants.COLLECT_NETWORKS_DEFAULT;
	boolean gpsPassiveMode=ConfigurationConstants.GPS_PASSIVE_MODE_DEFAULT;
	boolean foregroundServiceMode=ConfigurationConstants.FOREGROUND_SERVICE_MODE_DEFAULT;
	
	/**
	 * Constructor.
	 * @param context A valid Context object, best would be application context.
	 */
	public OpenCellIDCollectService(Context context) {
		super(context, "OPENCELLID_COLLECT");
		serviceIntent = new Intent(getContext(), CellIDCollectionService.class);
	}

	/**
	 * Use {@link #startService()} to start the service.
	 */
	public void onServiceStart() {
		if (!isStarted()) {
			//define parameters for cell collect service
			serviceIntent.setAction(CellIDCollectionService.START_COLLECTING_ACTION);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_LIST_TURN_GPS_OFF_LONG, gpsTimeout);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN, disablePowerSavingWhileCharging);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_INT, powerSavingOnPercentage);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_MIN_DISTANCE_BETWEEN_MEASURES_INT, minSameCellDistance);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_MIN_TIME_BETWEEN_MEASURES_LONG, minSameCellTimeDifference);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_DIM_SCREEN_WHEN_COLLECTING_BOOLEAN, dimScreenWhenCollecting);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_DIM_SCREEN_THREAD_SLEEP_TIME_LONG, dimThreadSleepTime);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_MAX_DB_SIZE_INT, dbSize);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_MIN_FREE_SPACE_INT, minFreeSpaceLeft);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_NOTIFICATION_TITLE, notificationDataTitle);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_NOTIFICATION_TEXT, notificationDataText);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_COLLECT_NETWORKS, collectNetworks);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_GPS_PASSIVE_MODE, gpsPassiveMode);
			serviceIntent.putExtra(ConfigurationConstants.PREFKEY_FOREGROUND_SERVICE_MODE, foregroundServiceMode);
			
			// start collect service
			getContext().startService(serviceIntent);
		}
	}

	/**
	 * Use {@link #stopService()} to stop the service.
	 */
	public void onServiceStop() {
		serviceIntent.setAction(CellIDCollectionService.START_COLLECTING_ACTION);
		getContext().stopService(serviceIntent);
	}
	
	/**
	 * @return true if service is running in the background.
	 */
	@Override
	public boolean isStarted() {
		ActivityManager manager = (ActivityManager) getContext().getSystemService(CellIDCollectionService.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (CellIDCollectionService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}

		return false;
	}
	
	/**
	 * Turn off GPS after the following inactivity time interval, in millis.
	 * Configuration change is propagated immediately.
	 * @param time in ms
	 */
	public void setGpsTimeout(long time) {
		gpsTimeout=time;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_LIST_TURN_GPS_OFF_LONG, time);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Disable power saving features while charging.
	 * @param boolean - true to disable, false otherwise
	 */
	public void setDisablePowerSavingWhileCharging(boolean disable) {
		disablePowerSavingWhileCharging=disable;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN, disable);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Start power saving when battery level is lower than specified
	 * percentage. This has no effect if power saving features are disabled
	 * while charging.
	 * @param percent number from 0-100
	 */
	public void setPowerSavingOnPercentage(int percent) {
		powerSavingOnPercentage=percent;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_INT, percent);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Minimum distance between two consecutive measures of the same cell, in meters.
	 * Cell measurement is not saved if distance is lower than the configured value.
	 * @param meters
	 */
	public void setMinSameCellDistance(int meters) {
		minSameCellDistance = meters;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_MIN_DISTANCE_BETWEEN_MEASURES_INT, meters);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Minimum time difference two consecutive measures of the same cell.
	 * @param time in ms
	 */
	public void setMinSameCellTimeDifference(long time) {
		minSameCellTimeDifference = time;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_MIN_TIME_BETWEEN_MEASURES_LONG, time);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Causes the screen to be turned on (in dimmed state) periodically after a given time 
	 * since it went to sleep, to allow the system to acquire telephony updates. 
	 * This option is off by default. You can turn it on to collect more measurements but 
	 * <p>this will need some more battery.
	 * @param wake true to activate the screen wake
	 */
	public void setWakeScreenOnTimer(boolean wake) {
		dimScreenWhenCollecting=wake;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_DIM_SCREEN_WHEN_COLLECTING_BOOLEAN, wake);
		getContext().sendBroadcast(cfg);
	}
	
	
	/**
	 * If the option to wake the screen periodically is on, then the 
	 * screen will be turned on after this given time since it last went to sleep.
	 * @param period period in ms
	 */
	public void setWakeScreenPeriod(long period) {
		dimThreadSleepTime=period;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_DIM_SCREEN_THREAD_SLEEP_TIME_LONG, period);
		getContext().sendBroadcast(cfg);
	}

	
	/**
	 * reads the number of all measurements in database
	 * @return Number of all measurements in database
	 */
	public int getAllMeasurementsCount() {
		OpenCellIdLibContext lib = new OpenCellIdLibContext(getContext());
		return lib.getMeasurementsDatabase().getAllMeasurementsCount();
	}
	
	/**
	 * reads the number of all measurements which haven't been uploaded
	 * @return number of all measurements which haven't been uploaded.
	 */
	public int getNonUploadedMeasurementsCount() {
		OpenCellIdLibContext lib = new OpenCellIdLibContext(getContext());
		return lib.getMeasurementsDatabase().getNonUploadedMeasurements().getCount();
	}
	
	/**
	 * reads the number of all measured cells
	 * @return number of all measured cells.
	 */
	public int getAllMeasuredCellsCount() {
		OpenCellIdLibContext lib = new OpenCellIdLibContext(getContext());
		return lib.getMeasurementsDatabase().getAllMeasuredCellsCount();
	}
	
	/**
	 * Sets the database size in megabytes
	 * @param size in Mb
	 */
	public void setDbSize(int size) {
		dbSize=size;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_MAX_DB_SIZE_INT, size);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Sets the value which denotes free space on the SD card. Database
	 * size shouldn't exceed that value.
	 * 
	 * @param freeSpace in Mb
	 */
	public void setMinFreeSpaceLeft(int freeSpace) {
		minFreeSpaceLeft = freeSpace;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_MIN_FREE_SPACE_INT, freeSpace);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Set the title and the text of the notification shown when service is collecting
	 * data. This is useful for localization.
	 * 
	 * @param title Title of the notification
	 * @param text Text of the notification
	 */
	public void setNotificationData(String title, String text) {
		notificationDataTitle=title;
		notificationDataText=text;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_NOTIFICATION_TITLE, title);
		cfg.putExtra(ConfigurationConstants.PREFKEY_NOTIFICATION_TEXT, text);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Sets the flag to collect and store networks to database.
	 * @param collect if true, networks will be collected and stored.
	 */
	public void setCollectNetworks(boolean collect) {
		collectNetworks=collect;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_COLLECT_NETWORKS, collect);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * Use GPS only if the GPS is already used by any othew app
	 * Configuration change is propagated immediately.
	 * @param isPassive
	 */
	public void setGPSPassiveMode(boolean isPassive) {
		gpsPassiveMode=isPassive;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_GPS_PASSIVE_MODE, isPassive);
		getContext().sendBroadcast(cfg);
	}
	
	/**
	 * starts the service in foreground mode
	 * @param runInForeground - true if the service should be started if foreground mode, false otherwise
	 */
	public void setForegroundSeviceMode(boolean runInForeground)
	{
		foregroundServiceMode=runInForeground;
		
		Intent cfg = new Intent(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		cfg.putExtra(ConfigurationConstants.PREFKEY_FOREGROUND_SERVICE_MODE, foregroundServiceMode);
		getContext().sendBroadcast(cfg);		
	}
}
