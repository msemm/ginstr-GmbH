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
package com.ginstr.android.service.opencellid.collect.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.ginstr.android.gps.core.GPSService;
import com.ginstr.android.gps.core.interfaces.ValidLocationChanged;
import com.ginstr.android.service.opencellid.R;
import com.ginstr.android.service.opencellid.library.data.Cell;
import com.ginstr.android.service.opencellid.library.data.Measurement;
import com.ginstr.android.service.opencellid.library.data.Network;
import com.ginstr.android.service.opencellid.library.db.MeasurementsDatabase;
import com.ginstr.android.service.opencellid.library.db.OpenCellIdLibContext;

/**
 * This service collects cellids of the Android-device in the background.
 * 
 * @author Marcus Wolschon (Marcus@Wolschon.biz)
 * @author Roberto Gonzalez
 * @author Danijel Korunek
 * @author Dinko Ivkovic
 */
public class CellIDCollectionService extends Service
{
	/**
	 * foreground service signatures
	 */
	private static final Class[] mStartForegroundSignature = new Class[] { int.class, Notification.class };
	private static final Class[] mStopForegroundSignature = new Class[] { boolean.class };

	private Method mStartForeground;
	private Object[] mStartForegroundArgs = new Object[2];
	
	/**
	 * intent actions
	 */
	public static final String START_COLLECTING_ACTION = "com.ginstr.android.service.opencellid.data.action.START_COLLECTING";
	public static final String STOP_COLLECTING_ACTION = "com.ginstr.android.service.opencellid.data.action.STOP_COLLECTING";

	/**
	 * notification id
	 */
	public static final int NOTIFICATION_ID = 1;

	/**
	 * last valid GPS location object
	 */
	private Location lastGpsLocation;
	
	/**
	 * last retrieved GSM signal strength
	 */
	private SignalStrength lastSignalStrength;
	
	/**
	 * last saved measurement
	 */
	private Measurement lastMeasurement;
	
	/**
	 * location of the last saved measurement
	 */
	private Location lastMeasurementLocation;

	public static final double MAX_CELL_ACTUATION_RADIUS_M = 34880;

	private TelephonyManager telephonyManager;
	
	/**
	 * list of saved measurements in the current session
	 */
	private Vector<Measurement> vMeasurement = new Vector<Measurement>();
	

	/**
	 * valid location changed listener
	 */
	ValidLocationChanged validLocListener = new ValidLocationChanged()
	{

		@Override
		public void onValidLocationChanged(Location location)
		{
			//use current location as last received location
			lastGpsLocation = location;
			
			logDebug("Valid location retrieved! " + location.getLatitude() + " lon-" + location.getLongitude(), null);

			try
			{
				// if the screen is off, do not collect the cell info 
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				if (!(pm.isScreenOn()))
				{
					logWarning("onValidLocationChanged() - screen is off. resumeCollecting aborted!", null);
					return;
				}
				
				// if the service is started, resume cell data collecting
				if (serviceStatus == SERVICE_STATUS.STARTED)
				{
					resumeCollecting();
				}
			} catch (Exception e)
			{
				logDebug("", e);
			}
		}
	};

	/**
	 * Used to collect signal strengths and cell locations.
	 */
	private PhoneStateListener phoneStateListener = new PhoneStateListener()
	{
		public void onCellLocationChanged(CellLocation location)
		{
			logDebug("New cell location retrieved.", null);
			try
			{
				// if the service is started, resume cell data collecting
				if (serviceStatus == SERVICE_STATUS.STARTED)
				{
					resumeCollecting();
				}
			} catch (Exception e)
			{
				logDebug("", e);
			}

		};

		public void onSignalStrengthsChanged(SignalStrength signalStrength)
		{
			if (signalStrength != null)
			{
				// save the current signal strength
				lastSignalStrength = signalStrength;
				logDebug("SignalStrengthsChanged(): " + signalStrength.getGsmSignalStrength(), null);
			}
		};
	};

	/**
	 * Configuration changes receiver. All changes on global configuration gets
	 */
	private BroadcastReceiver configurationReceiver = new BroadcastReceiver()
	{

		@Override
		public void onReceive(Context context, Intent intent)
		{
			// check intent parameters
			Bundle extras = intent.getExtras();
			getExtraParameters(extras);
		}
	};

	/**
	 * Preferences parameters.
	 * 
	 * @see ConfigurationConstants for actual meaning of these
	 */
	private long gpsTimeOut = ConfigurationConstants.TURN_GPS_OFF_DEFAULT;
	private boolean disablePowerSavingWhileCharging = ConfigurationConstants.DISABLE_POWER_SAVING_WHILE_CHARGING_DEFAULT;
	private int enablePowerSavingOnBatteryLevel = ConfigurationConstants.ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DEFAULT;

	private int minDistanceSameCell = ConfigurationConstants.MIN_DISTANCE_BETWEEN_MEASURES_DEFAULT;
	private int minTimeDifferenceSameCell = ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DEFAULT;
	private int maxDbSize = ConfigurationConstants.MAX_DB_SIZE_DEFAULT;
	private int minStorageSpaceLeft = ConfigurationConstants.MIN_FREE_SPACE;
	private String notifTitle = ConfigurationConstants.NOTIFICATION_TITLE;
	private String notifText = ConfigurationConstants.NOTIFICATION_TEXT;
	private boolean collectNetworks = ConfigurationConstants.COLLECT_NETWORKS_DEFAULT;

	/**
	 * Name of our power-management lock.
	 */
	private static final String POWERLOCK_NAME = "WakeLock.Local";

	private OpenCellIdLibContext libContext;

	/**
	 * power-lock to prevent suspension of the device.
	 */
	private WakeLock myPowerLock;

	/**
	 * Used for storing cell measurements.
	 */
	private MeasurementsDatabase measurementsDatabase;

	/** Gps service for requesting location updates and getting GPS status. */
	private GPSService gpsService;

	private enum SERVICE_STATUS
	{
		STOPPED, // The collecting service is not working. PWS in use
		STARTED, // Collecting!
		SUSPENDED, // Suspended awaiting for battery level update
	};

	private SERVICE_STATUS serviceStatus = SERVICE_STATUS.STOPPED;

	private boolean enableDimScreenOption = ConfigurationConstants.DIM_SCREEN_WHEN_COLLECTING_DEFAULT;
	private long dimThreadSleepTime = ConfigurationConstants.DIM_SCREEN_THREAD_SLEEP_TIME;
	private boolean gpsPassiveModeEnabled = ConfigurationConstants.GPS_PASSIVE_MODE_DEFAULT;
	private boolean foregroundServiceModeEnabled = ConfigurationConstants.FOREGROUND_SERVICE_MODE_DEFAULT;
	
	private int maxLogFileSize = ConfigurationConstants.MAX_LOG_SIZE_DEFAULT;
	private boolean logToFileEnabled=ConfigurationConstants.LOG_TO_FILE_DEFAULT;

	private void logDebug(String message, Throwable t)
	{
		libContext.getLogService().writeLog(Log.DEBUG, OpenCellIdLibContext.LOG_FILENAME_PREFIX, message, t);
	}

	private void logWarning(String message, Throwable t)
	{
		libContext.getLogService().writeLog(Log.WARN, OpenCellIdLibContext.LOG_FILENAME_PREFIX, message, t);
	}

	private void logError(String message, Throwable t)
	{
		libContext.getLogService().writeLog(Log.ERROR, OpenCellIdLibContext.LOG_FILENAME_PREFIX, message, t);
	}

	/**
	 * Starts GPS updates and listening for GPS location changes.
	 */
	private void startGpsUpdates()
	{ 
		//check if the passive mode is enabled and use it  
		if (gpsPassiveModeEnabled && (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO))
		{
			gpsService.setLocationProvider(LocationManager.PASSIVE_PROVIDER);
		} else
		{
			gpsService.setLocationProvider(LocationManager.GPS_PROVIDER);
		}

		gpsService.startService();
		try
		{
			gpsService.setOnValidLocationChanged(validLocListener);
		} catch (Throwable e)
		{
			logError("Error adding listeners to gpsservice", e);
		}
	}

	/**
	 * Stops requesting GPS location updates and listening for location GPS
	 * changes changes.
	 */
	private void stopGpsUpdates()
	{
		try
		{
			gpsService.setOnValidLocationChanged(null);
			gpsService.stopService();
		} catch (Throwable e)
		{
			logError("Error removing listeners from gpsservice", e);
		}
	}

	/**
	 * Initialization.
	 * 
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate()
	{
		super.onCreate();

		// android.os.Debug.waitForDebugger();
		
		try
		{
			mStartForeground = getClass().getMethod("startForeground", mStartForegroundSignature);
		} catch (NoSuchMethodException e)
		{
			// Running on an older platform.
			mStartForeground = null;
		}		

		libContext = new OpenCellIdLibContext(this);
		
		// log device information
		writeDeviceInfoToLog();

		logDebug("onCreate()", null);

		// initialize measurement database
		measurementsDatabase = libContext.getMeasurementsDatabase();
		
		// retrieve the last measurement data from the database
		lastMeasurement = measurementsDatabase.getLastMeasurement();
		
		// initialize gpsService
		gpsService = new GPSService(this);
		gpsService.setLogTag(OpenCellIdLibContext.LOG_FILENAME_PREFIX);
		gpsService.setMinDistance(0);

		telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

		// obtain power-management-lock
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		try
		{
			myPowerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, POWERLOCK_NAME);
			myPowerLock.setReferenceCounted(true);
		} catch (Exception e)
		{
			logDebug("", e);
		}

		// Check sticky Intent on battery status
		IntentFilter powerStatusFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		powerStatusIntent = registerReceiver(null, powerStatusFilter);

		// Register for configuration changes
		IntentFilter configFilter = new IntentFilter(ConfigurationConstants.GLOBAL_SETTTINGS_RECEIVER_ACTION);
		registerReceiver(configurationReceiver, configFilter);
	}

	/**
	 * Clean up.
	 * 
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy()
	{
		logDebug("service onDestroy ()!", null);

		unregisterReceiver(configurationReceiver);

		stopCollecting();

		// backup to release the powerlock, just in case...
		if (myPowerLock != null)
		{
			if (myPowerLock.isHeld())
			{
				try
				{
					myPowerLock.release();
				} catch (Exception e)
				{
					logWarning("", e);
				}
			}
		}

		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{

		logDebug("onStartCommand()", null);

		if (intent != null)
		{
			String action = intent.getAction();

			if (START_COLLECTING_ACTION.equals(action))
			{

				// use current intent configuration!
				configurationReceiver.onReceive(this, intent);

				if (serviceStatus == SERVICE_STATUS.STOPPED)
				{
					startCollecting();
				}

			} else if (STOP_COLLECTING_ACTION.equals(action))
			{

				stopCollecting();

			} else
			{
				// no op
			}
		}

		logDebug("onStartCommand(): DONE", null);

		// in case the service is killed by OS, re-deliver intent parameters
		return START_REDELIVER_INTENT;
	}

	/**
	 * No binder is needed.
	 */
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	/**
	 * Used to denote if dimScreenThread is running.
	 */
	public static volatile boolean dimThreadRunning = false;

	/**
	 * While running on Android versions 17 and less, dimScreenThread will hold
	 * the phone screen dimmed for a few seconds. The reason for this is to get
	 * around the bug where PhoneStateListener is not updating cell location
	 * changes and signal changes when screen is off. However, it's up to the
	 * user of this library to enable screen dimming option.
	 */
	private Thread dimScreenThread;

	private class DimScreenThread extends Thread
	{
		@Override
		public void run()
		{
			// check if the dim screen feature is enabled and exit thread if not
			if (!enableDimScreenOption)
			{
				logWarning("Screen dimming is disabled by a configuration parameter", null);
				return;
			}

			dimThreadRunning = true;
			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			String dimLockName = "DimLock";

			PowerManager.WakeLock wlDim = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, dimLockName);
			wlDim.setReferenceCounted(true);

			logDebug("dimThread Running", null);

			while (dimThreadRunning)
			{
				// check if the dim screen feature is enabled and exit thread if not
				if (!enableDimScreenOption)
				{
					logWarning("Screen dimming is disabled by a configuration parameter", null);
					dimThreadRunning = false;
					break;
				}
			
				logDebug("wlDim.acquire()", null);
				
				// activate the screen
				wlDim.acquire();				

				// check for cell id change
				CellLocation.requestLocationUpdate();						
				
				// deactivate the screen
				if (wlDim.isHeld())
					wlDim.release();

				logDebug("wlDim.release()", null);
				
				try
				{
					Thread.sleep(dimThreadSleepTime);
					
					// check for the last valid location age
					while(lastGpsLocation==null || (lastGpsLocation.getTime()+5000)<new Date().getTime())
					{
						Thread.sleep(2000);	
					}
				} catch (Exception e)
				{
					logError(e.getMessage(), e);
				}
			}
			logDebug("dimThread stopped", null);
		}
	}

	/**
	 * Starts collecting cell measurements.
	 */
	private void startCollecting()
	{
		logWarning("startCollecting()", null);

		// check if the cells data collecting is already started
		if (serviceStatus == SERVICE_STATUS.STOPPED || serviceStatus == SERVICE_STATUS.SUSPENDED)
		{

			if (serviceStatus == SERVICE_STATUS.STOPPED)
			{
				myPowerLock.acquire();
			}

			startPowerSaving();

			startGpsUpdates();

			startScreenDimming();

			telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

			serviceStatus = SERVICE_STATUS.SUSPENDED;

			showNotification();

			resumeCollecting();
		}
	}

	/**
	 * starts the screen dimming
	 */
	private void startScreenDimming()
	{
		if (dimScreenThread == null)
		{
			dimScreenThread = new DimScreenThread();
		}

		try
		{
			dimScreenThread.start();
		} catch (Exception ex)
		{
		}
	}

	/**
	 * stops the screen dimming
	 */
	private void stopScreenDimming()
	{
		if (dimScreenThread != null)
		{
			dimScreenThread.interrupt();
		}
		dimThreadRunning = false;
		dimScreenThread = null;
	}

	/**
	 * starts or resumes the data collecting
	 */
	private void resumeCollecting()
	{
		logWarning("resumeCollecting()", null);

		if (serviceStatus == SERVICE_STATUS.STARTED || serviceStatus == SERVICE_STATUS.SUSPENDED)
		{

			// start or reset GPS timer
			refreshPowerSavingTimer();

			// checks if at least one gps location is retrieved 
			if (lastGpsLocation != null)
			{
				// save new cell location
				Thread thread = new Thread(new Runnable()
				{

					@Override
					public void run()
					{

						logDebug("onLocationChanged(): run()", null);

						try
						{
							// checks if the last valid gps position is refreshed 
							if ((lastGpsLocation.getTime()+5000)>=new Date().getTime())
							{
								final CellLocation cellLocation = telephonyManager.getCellLocation();
								final String mccmnc = telephonyManager.getNetworkOperator();
								final Location myLocation = lastGpsLocation;
								final long myTimestamp = System.currentTimeMillis();
			
								// save the cell data
								persistCellLocation(cellLocation, myLocation, mccmnc, myTimestamp);	
							}
							else
							{
								logDebug("onLocationChanged(): old GPS location!", null);		
							}
						} catch (Exception e)
						{
							logError("", e);
						}

					}
				});

				thread.setPriority(Thread.MIN_PRIORITY);
				thread.start();
			}

			if (serviceStatus == SERVICE_STATUS.SUSPENDED)
			{

				logWarning("resumeCollecting() > LOCATION UPDATED ADDED", null);

				startGpsUpdates();

				// request location update from network provider
				// it will not update signal strength
				CellLocation.requestLocationUpdate();

				serviceStatus = SERVICE_STATUS.STARTED;
			}
		}
	}

	/**
	 * Stops collecting cell measures no matter status the service is. Also
	 * stops the PowerSaving.
	 */
	private void stopCollecting()
	{
		logWarning("stopCollecting()", null);

		if (serviceStatus != SERVICE_STATUS.STOPPED)
		{

			// remove any registered listener
			suspendCollecting();

			// removes the notification
			hideNotification();

			serviceStatus = SERVICE_STATUS.STOPPED;

			logDebug("onDestroy(): myPhoneStateListener removed from listener", null);
			telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);

			stopScreenDimming();

			stopPowerSaving();

			// stops any previous pending GPS timer task
			if (mPowerSavingSuspendTimerHandler != null)
				mPowerSavingSuspendTimerHandler.removeCallbacks(mPowerSavingTurnOffGPSTask);

			if (myPowerLock.isHeld())
			{
				try
				{
					myPowerLock.release();
				} catch (Exception e)
				{
					logWarning("", e);
				}
			}
		}
	}

	/**
	 * Suspends collecting cells but leaves the PowerSaving running
	 */
	private void suspendCollecting()
	{
		logWarning("suspendCollecting()", null);

		if (serviceStatus == SERVICE_STATUS.STARTED)
		{
			serviceStatus = SERVICE_STATUS.SUSPENDED;

			try
			{
				logDebug("suspendCollecting() > REMOVED LOCATION LISTENER", null);
				stopGpsUpdates();
			} catch (Exception e)
			{
				logDebug("", e);
			}
		}
	}

	/**
	 * Sticky Intent. Initializes on Service.onCreate()
	 */
	private Intent powerStatusIntent;

	/**
	 * Receives power charging status notifications. If STARTED or SUSPENDED
	 * enters the PS loop to check new power charging status against user
	 * preferences.
	 */
	private BroadcastReceiver mPowerReceiver = new BroadcastReceiver()
	{

		@Override
		public void onReceive(Context context, Intent intent)
		{
			logDebug("mPowerReceiver.onReceive()", null);

			if (serviceStatus != SERVICE_STATUS.STOPPED)
			{
				mPowerSavingLoopHandler.post(mPowerSavingLoopTask);
			}
		}
	};

	/**
	 * Handler for the GPS timer loop
	 */
	private Handler mPowerSavingSuspendTimerHandler = new Handler();

	/**
	 * Task to stop the LocationManager sending us Location updates after the
	 * GPS time out
	 */
	private Runnable mPowerSavingTurnOffGPSTask = new Runnable()
	{

		@Override
		public void run()
		{
			logWarning("GPSTimer.run() > STOP GPS", null);

			suspendCollecting();
		}
	};

	/**
	 * Resets the timer to switch off the GPS no further activity before it ends
	 */
	private void refreshPowerSavingTimer()
	{
		logDebug("refreshPowerSavingTimer()", null);

		// stops any previous pending GPS timer task
		mPowerSavingSuspendTimerHandler.removeCallbacks(mPowerSavingTurnOffGPSTask);

		if (serviceStatus == SERVICE_STATUS.STARTED)
		{
			// and starts the new one, if configured so
			if (gpsTimeOut != ConfigurationConstants.TURN_GPS_TIME_OFF_ALWAYS_RUNNING)
			{
				mPowerSavingSuspendTimerHandler.postDelayed(mPowerSavingTurnOffGPSTask, gpsTimeOut);
			}
		}
	}

	/**
	 * Background handler to allow GPS timeout or battery level activation
	 */
	private Handler mPowerSavingLoopHandler = new Handler();

	/**
	 * Delay between loop checkings
	 */
	private static final long POWER_SAVING_BATTERY_TASK_DELAY_MILLIS = 60 * 1000L; // 1
																					// minute

	private Runnable mPowerSavingLoopTask = new Runnable()
	{

		@Override
		public void run()
		{

			logWarning("PowerSaving loop()", null);

			if (serviceStatus == SERVICE_STATUS.STARTED || serviceStatus == SERVICE_STATUS.SUSPENDED)
			{

				// Disable PowerSaving at all if device is charging.
				// Will be activated again when the phone disconnects from the
				// power by the broadcast receiver
				if (disablePowerSavingWhileCharging && isBatteryCharging())
				{

					logWarning("PowerSaving loop() > EXIT LOOP: Charging", null);

					// if suspended, restart it again
					if (serviceStatus == SERVICE_STATUS.SUSPENDED)
					{
						resumeCollecting();
					}

					// no further processing needed so do not add the task again
					// in the handler queue.
					return;
				}

				// Stop collecting if battery level below threshold
				if (enablePowerSavingOnBatteryLevel != ConfigurationConstants.ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DISABLED)
				{
					if (getBatteryLevel() <= enablePowerSavingOnBatteryLevel)
					{
						logWarning("PowerSaving loop() > LOW BATTERY LEVEL (" + getBatteryLevel() + "<=" + enablePowerSavingOnBatteryLevel + ")", null);

						// Battery level below threshold
						if (serviceStatus == SERVICE_STATUS.STARTED)
						{
							suspendCollecting();
						}

					} else
					{
						logWarning("PowerSavingloop() > HIGH BATTERY LEVEL(" + getBatteryLevel() + ">" + enablePowerSavingOnBatteryLevel + ")", null);

						if (serviceStatus == SERVICE_STATUS.SUSPENDED)
						{
							// Battery level already higher threshold
							resumeCollecting();
						}
					}
				}

				// check next time
				mPowerSavingLoopHandler.postDelayed(this, POWER_SAVING_BATTERY_TASK_DELAY_MILLIS);
			}
		}
	};

	/**
	 * Check the charging status of the phone battery. Uses the sticky Intent
	 * retrieved on onCreate()
	 * 
	 * @return true is charging, false otherwise.
	 */
	private boolean isBatteryCharging()
	{
		int chargingStatus = powerStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		boolean isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargingStatus == BatteryManager.BATTERY_STATUS_FULL;

		return isCharging;
	}

	/**
	 * Checks the battery level. Uses the sticky Intent retrieved on onCreate()
	 * 
	 * @return number from 0 to 100 representing the % of the current battery
	 *         level.
	 */
	private int getBatteryLevel()
	{
		int level = powerStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = powerStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		int batteryPct = level * 100 / scale;

		return batteryPct;
	}

	/**
	 * Starts the PowerSaving: - on STOPPED starts GPS timer, starts battery
	 * loop and register power charging receiver - on SUSPENDED starts GPS timer
	 * and starts battery loop - on STARTED does nothing
	 */
	private void startPowerSaving()
	{
		logWarning("startPowerSaving()", null);

		if (serviceStatus == SERVICE_STATUS.STOPPED || serviceStatus == SERVICE_STATUS.SUSPENDED)
		{

			// Register for power changing/discharging events
			if (serviceStatus == SERVICE_STATUS.STOPPED)
			{
				IntentFilter powerChargingFilter = new IntentFilter();
				powerChargingFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
				powerChargingFilter.addAction(Intent.ACTION_POWER_CONNECTED);

				registerReceiver(mPowerReceiver, powerChargingFilter);

			}

			// And start checking loop
			mPowerSavingLoopHandler.post(mPowerSavingLoopTask);
		}
	}

	/**
	 * Stops the PowerSaving: - on STOPPED stops GPS timer, stops battery loop
	 * and power charging receiver - on SUSPENDED stops GPS timer - on STARTED
	 * does nothing
	 */
	private void stopPowerSaving()
	{
		logWarning("stopPowerSaving()", null);

		if (serviceStatus == SERVICE_STATUS.STOPPED || serviceStatus == SERVICE_STATUS.SUSPENDED)
		{

			// remove any pending GPS timer task
			mPowerSavingLoopHandler.removeCallbacks(mPowerSavingTurnOffGPSTask);

			if (serviceStatus == SERVICE_STATUS.STOPPED)
			{
				// remove any pending battery level check task
				mPowerSavingLoopHandler.removeCallbacks(mPowerSavingLoopTask);

				unregisterReceiver(mPowerReceiver);
			}
		}
	}

	/**
	 * ONLY to be called from resumeCollection() Checks all data and persist the
	 * measurement.
	 * 
	 * @param cellLocation
	 * @param lastLocation
	 * @param mccmnc
	 * @param timeStamp
	 */
	private synchronized void persistCellLocation(final CellLocation cellLocation, final Location lastLocation, final String mccmnc, final long timeStamp)
	{
		logDebug("persistCellLocation(): cellLocation!=null==" + (cellLocation != null) + ", lastLocation!=null==" + (lastLocation != null) + ", mccmnc="
				+ mccmnc + ", timesStamp=" + timeStamp, null);

		try
		{
			// first we check if database is to big and try to delete
			// measurements older than 7 days
			if (!libContext.isDatabaseValid(maxDbSize, minStorageSpaceLeft))
			{
				logDebug("database to big or to little free space left... deleting old measurements", null);

				int num = measurementsDatabase.eraseUploadedMeasurementsOlderThan7Days();

				logDebug("Erased " + num + " of old records.", null);
			}

			if (libContext.isDatabaseValid(maxDbSize, minStorageSpaceLeft))
			{
				// Only GSM cells
				if (cellLocation instanceof GsmCellLocation && lastSignalStrength != null && lastSignalStrength.isGsm()
						&& telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM)
				{

					// Valid NetWork Operator
					GsmCellLocation gsmcell = (GsmCellLocation) cellLocation;
					int cellid = gsmcell.getCid();
					if (mccmnc == null || mccmnc.length() < 4)
					{
						logDebug(
								"persistCellLocation(): no current NetworkOperator (mccmnc == null || mccmnc.length() < 4) = "
										+ (mccmnc == null || mccmnc.length() < 4), null);
						return;
					}

					// Build the Measurement to persist
					int mcc = Integer.parseInt(mccmnc.substring(0, 3));
					int mnc = Integer.parseInt(mccmnc.substring(3));
					int lac = gsmcell.getLac();
					int gsmSignalStrength = lastSignalStrength.getGsmSignalStrength();
					String network = telephonyManager.getNetworkOperatorName();

					if (network == null || network.trim().length() == 0)
					{
						network = telephonyManager.getSimOperatorName();
					}

					if (mcc <= 0 || mcc == Integer.MAX_VALUE || mnc <= 0 || mnc == Integer.MAX_VALUE || lac <= 0 || lac == Integer.MAX_VALUE || cellid <= 0
							|| cellid == Integer.MAX_VALUE)
					{
						logDebug("Cell cannot be saved. mcc=" + mcc + " mnc=" + mnc + " lac=" + lac + " cellid=" + cellid, null);
						return;
					}

					Measurement saveme = new Measurement(timeStamp, cellid, mcc, mnc, lac, Network.GSM_NETWORK, gsmSignalStrength, lastLocation.getLatitude(),
							lastLocation.getLongitude(), lastLocation.getSpeed(), // 0.0
																					// if
																					// no
																					// speed
							lastLocation.getBearing(), // 0.0 if no bearing
							network, false, getNetworkType(), lastLocation.getAccuracy());

					
					// creating a temporary Vector<Measurement> object
					Vector<Measurement> vMsrTMP = (Vector<Measurement>) vMeasurement.clone();
					
					// checks if similar measurement is already stored in this session
					boolean saveMeasurement=true;
					for (Measurement measurement:vMsrTMP)
					{
						if (measurement.sameCell(saveme))
						{
							Location oldLocation = new Location("");
							oldLocation.setLatitude(measurement.getLat());
							oldLocation.setLongitude(measurement.getLon());
							
							Location newLocation = new Location("");
							newLocation.setLatitude(saveme.getLat());
							newLocation.setLongitude(saveme.getLon());	
							
							// Minimum distance between two measures of the same
							// cell
							if (minDistanceSameCell != ConfigurationConstants.MIN_DISTANCE_BETWEEN_MEASURES_DISABLED)
							{
								float distance = oldLocation.distanceTo(newLocation);
								if (distance < minDistanceSameCell)
								{
									logDebug("persistCellLocation(): ignoring measurement as " + distance + " distance is less than " + minDistanceSameCell
											+ " meters", null);
									
									saveMeasurement=false;
									return;
								}
							}

							// Minimum time between two measures of the same cell
							if (minTimeDifferenceSameCell != ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DISABLED)
							{
								long timeInterval = saveme.getTimestamp() - lastMeasurement.getTimestamp();
								if (timeInterval < minTimeDifferenceSameCell)
								{
									logDebug("persistCellLocation(): ignoring measurement as " + timeInterval + " time interval is less than "
											+ minTimeDifferenceSameCell + " miliseconds ago", null);

									saveMeasurement=false;
									return;
								}
							}
						}
					}
					
					// check if similar measurement is already stored in the database
					if (saveMeasurement)
					{
						// measurement within cell tower activity radius
						Cell cell = libContext.getCellsDatabase().getTowerCell(saveme.getCellid(), saveme.getLac(), saveme.getMcc(), saveme.getMnc());
						if (cell != null)
						{
							float[] distanceToCell = new float[1];
							Location.distanceBetween(cell.getLat(), cell.getLon(), saveme.getLat(), saveme.getLon(), distanceToCell);

							if (distanceToCell[0] > MAX_CELL_ACTUATION_RADIUS_M)
							{
								logDebug("persistCellLocation(): ignoring measurement as the distance to cell" + distanceToCell[0]
										+ " is greater than max actuation radius " + MAX_CELL_ACTUATION_RADIUS_M, null);
								return;
							}
						}
					}

					logDebug("persistCellLocation(): all checks passed! persist the measurement.", null);

					// Persist the measurement
					measurementsDatabase.addMeasurement(saveme, true, collectNetworks);
					lastMeasurement = saveme;
					lastMeasurementLocation = lastLocation;
					
					// add newly saved measurement in thew list of saved measurements in this session
					vMeasurement.add(saveme);
					if (vMeasurement.size()>50)
					{
						logDebug("persistCellLocation(): temp measurenemts size > 50. Remove oldest element!", null);
						
						// remove old temporary measurement
						vMeasurement.remove(0);
					}
				
				// only CDMA cells
				} else if (cellLocation != null && cellLocation instanceof CdmaCellLocation && lastSignalStrength != null
						&& telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA)
				{
					CdmaCellLocation cdmaLoc = (CdmaCellLocation) cellLocation;
					int cellId = cdmaLoc.getBaseStationId();
					int mnc = -1;
					int lac = cdmaLoc.getNetworkId();
					int mcc = -1;
					String networkName = "CDMA unknown";

					try
					{
						Class<?> c = Class.forName("android.os.SystemProperties");
						Method get = c.getMethod("get", String.class);

						// Gives MCC + MNC
						String homeOperator = ((String) get.invoke(c, "ro.cdma.home.operator.numeric"));
						String country = homeOperator.substring(0, 3); // the
																		// last
																		// three
																		// digits
																		// is
																		// MNC
						mcc = Integer.parseInt(country);
						mnc = Integer.parseInt(homeOperator.substring(3));

						// give network name
						networkName = ((String) get.invoke(c, "ro.cdma.home.operator.alpha"));

					} catch (Exception e)
					{
						logDebug("Error getting mcc+mnc from CDMA using reflection", e);
						mnc = cdmaLoc.getSystemId();
						mcc = -1;
						networkName = telephonyManager.getNetworkOperatorName();
					}

					int signalStrength = lastSignalStrength.getCdmaDbm();

					if (mcc <= 0 || mnc <= 0 || lac <= 0 || cellId <= 0)
					{
						logDebug("Cell cannot be saved. mcc=" + mcc + " mnc=" + mnc + " lac=" + lac + " cellid=" + cellId, null);
						return;
					}

					Measurement saveme = new Measurement(timeStamp, cellId, mcc, mnc, lac, Network.CDMA_NETWORK, signalStrength, lastLocation.getLatitude(),
							lastLocation.getLongitude(), lastLocation.getSpeed(), // 0.0
																					// if
																					// no
																					// speed
							lastLocation.getBearing(), // 0.0 if no bearing
							networkName, false, getNetworkType(), lastLocation.getAccuracy());

					// if the cell changed, always include the measurement
					if (saveme.sameCell(lastMeasurement))
					{
						// Minimum distance between two measures of the same
						// cell
						if (minDistanceSameCell != ConfigurationConstants.MIN_DISTANCE_BETWEEN_MEASURES_DISABLED && lastMeasurementLocation != null)
						{
							float distance = lastLocation.distanceTo(lastMeasurementLocation);
							if (distance < minDistanceSameCell)
							{
								logDebug("persistCellLocation(): ignoring measurement as " + distance + " distance is less than " + minDistanceSameCell
										+ " meters", null);
								return;
							}
						}

						// Minimum time between two measures of the same cell
						if (minTimeDifferenceSameCell != ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DISABLED)
						{
							long timeInterval = saveme.getTimestamp() - lastMeasurement.getTimestamp();
							if (timeInterval < minTimeDifferenceSameCell)
							{
								logDebug("persistCellLocation(): ignoring measurement as " + timeInterval + " time interval is less than "
										+ minTimeDifferenceSameCell + " miliseconds ago", null);
								return;
							}
						}
					}

					logDebug("persistCellLocation(): all checks passed! persist the measurement.", null);

					// Persist the measurement
					measurementsDatabase.addMeasurement(saveme, true, collectNetworks);
					lastMeasurement = saveme;
					lastMeasurementLocation = lastLocation;
				} else
				{
					logDebug("persistCellLocation(): unknown cell location", null);
				}
			} else
			{
				logError("persistCellLocation(): database to big or there's to little free space left", null);
			}
		} catch (Exception e)
		{
			logError("", e);
		}
	}

	/**
	 * shows the notification in the notification bar
	 */
	private void showNotification()
	{
		if (foregroundServiceModeEnabled)
		{
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

			Notification notif = new Notification(R.drawable.icon_status_collecting, notifTitle, System.currentTimeMillis());
			notif.tickerText = notifText;

			PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), // add
																												// this
					PendingIntent.FLAG_UPDATE_CURRENT);

			notif.setLatestEventInfo(getApplicationContext(), notifTitle, notifText, contentIntent);
			notif.flags |= Notification.FLAG_ONGOING_EVENT;

			// If we have the new startForeground API, then use it.
			if (mStartForeground != null)
			{
				mStartForegroundArgs[0] = Integer.valueOf(NOTIFICATION_ID);
				mStartForegroundArgs[1] = notif;
				try
				{
					mStartForeground.invoke(this, mStartForegroundArgs);
				} catch (InvocationTargetException e)
				{
					// Should not happen.
					Log.w(this.getClass().getName(), "Unable to invoke startForeground", e);
				} catch (IllegalAccessException e)
				{
					// Should not happen.
					Log.w(this.getClass().getName(), "Unable to invoke startForeground", e);
				}
				return;
			}				
		}
		else
		{
			hideNotification();
		}
	}

	/**
	 * hides the notification form the notification bar
	 */
	private void hideNotification()
	{
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(NOTIFICATION_ID);
	}

	/**
	 * reads the parameters from the received Intent object
	 * @param extras
	 */
	private void getExtraParameters(Bundle extras)
	{
		if (extras != null)
		{
			logDebug("configurationReceiver.onReceive() extras : " + extras.keySet(), null);

			// check if the maxDbSize parameter is provided through intent
			if (extras.containsKey(ConfigurationConstants.PREFKEY_MAX_DB_SIZE_INT))
			{
				maxDbSize = extras.getInt(ConfigurationConstants.PREFKEY_MAX_DB_SIZE_INT, ConfigurationConstants.MAX_DB_SIZE_DEFAULT);
			}

			// check if the minStorageSpaceLeft parameter is provided through intent
			if (extras.containsKey(ConfigurationConstants.PREFKEY_MIN_FREE_SPACE_INT))
			{
				minStorageSpaceLeft = extras.getInt(ConfigurationConstants.PREFKEY_MIN_FREE_SPACE_INT, ConfigurationConstants.MIN_FREE_SPACE);
			}

			// check if the gpsTimeOut parameter is provided through intent
			boolean checkPowerSaving = false;
			if (extras.containsKey(ConfigurationConstants.PREFKEY_LIST_TURN_GPS_OFF_LONG))
			{
				gpsTimeOut = extras.getLong(ConfigurationConstants.PREFKEY_LIST_TURN_GPS_OFF_LONG, ConfigurationConstants.TURN_GPS_OFF_DEFAULT);
				checkPowerSaving = true;
			}

			// check if the disablePowerSavingWhileCharging parameter is provided through intent
			if (extras.containsKey(ConfigurationConstants.PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN))
			{
				disablePowerSavingWhileCharging = extras.getBoolean(ConfigurationConstants.PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN,
						ConfigurationConstants.DISABLE_POWER_SAVING_WHILE_CHARGING_DEFAULT);
				checkPowerSaving = true;
			}

			// check if the enablePowerSavingOnBatteryLevel parameter is provided through intent
			if (extras.containsKey(ConfigurationConstants.PREFKEY_ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_INT))
			{
				enablePowerSavingOnBatteryLevel = extras.getInt(ConfigurationConstants.PREFKEY_ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_INT,
						ConfigurationConstants.ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DEFAULT);
				checkPowerSaving = true;
			}

			// change Power Saving if related properties were set
			if (checkPowerSaving && serviceStatus != SERVICE_STATUS.STOPPED)
			{
				logDebug("onReceiveConfigurationChanges() > Change config", null);
				startPowerSaving();
			}

			// check if the minDistanceSameCell parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_MIN_DISTANCE_BETWEEN_MEASURES_INT))
			{
				minDistanceSameCell = extras.getInt(ConfigurationConstants.PREFKEY_MIN_DISTANCE_BETWEEN_MEASURES_INT,
						ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DEFAULT);
			}

			// check if the minTimeDifferenceSameCell parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_MIN_TIME_BETWEEN_MEASURES_LONG))
			{
				minTimeDifferenceSameCell = (int)(extras.getLong(ConfigurationConstants.PREFKEY_MIN_TIME_BETWEEN_MEASURES_LONG,
						ConfigurationConstants.MIN_TIME_BETWEEN_MEASURES_DEFAULT));
			}

			// change Dim screen if related properties were set
			if (extras.containsKey(ConfigurationConstants.PREFKEY_DIM_SCREEN_WHEN_COLLECTING_BOOLEAN))
			{
				enableDimScreenOption = extras.getBoolean(ConfigurationConstants.PREFKEY_DIM_SCREEN_WHEN_COLLECTING_BOOLEAN);
				if (enableDimScreenOption)
					startScreenDimming();
				else
					stopScreenDimming();
			}

			// check if the dimThreadSleepTime parameter is provided through intent
			if (extras.containsKey(ConfigurationConstants.PREFKEY_DIM_SCREEN_THREAD_SLEEP_TIME_LONG))
			{
				dimThreadSleepTime = extras.getLong(ConfigurationConstants.PREFKEY_DIM_SCREEN_THREAD_SLEEP_TIME_LONG);
			}

			// check if the foregroundServiceModeEnabled parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_FOREGROUND_SERVICE_MODE))
			{
				foregroundServiceModeEnabled = extras.getBoolean(ConfigurationConstants.PREFKEY_FOREGROUND_SERVICE_MODE);
			}				

			// check if the notification should be visible			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_NOTIFICATION_TITLE))
			{
				notifTitle = extras.getString(ConfigurationConstants.PREFKEY_NOTIFICATION_TITLE);
				if (notifTitle == null || notifTitle.length() == 0)
					notifTitle = ConfigurationConstants.NOTIFICATION_TITLE;

				showNotification();
			}		

			// check if the notification should be visible			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_NOTIFICATION_TEXT))
			{
				notifText = extras.getString(ConfigurationConstants.PREFKEY_NOTIFICATION_TEXT);
				if (notifText == null || notifText.length() == 0)
					notifText = ConfigurationConstants.NOTIFICATION_TEXT;

				showNotification();
			}

			// check if the collectNetworks parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_COLLECT_NETWORKS))
			{
				collectNetworks = extras.getBoolean(ConfigurationConstants.PREFKEY_COLLECT_NETWORKS);
			}

			// check if the gpsPassiveModeEnabled parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_GPS_PASSIVE_MODE))
			{
				gpsPassiveModeEnabled = extras.getBoolean(ConfigurationConstants.PREFKEY_GPS_PASSIVE_MODE);
			}
					
			// check if the maxLogFileSize parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_MAX_LOG_SIZE_INT))
			{
				maxLogFileSize = extras.getInt(ConfigurationConstants.PREFKEY_MAX_LOG_SIZE_INT);
				
				libContext.getLogService().setMaxLogFileSize(maxLogFileSize);
			}
			
			// check if the logToFileEnabled parameter is provided through intent			
			if (extras.containsKey(ConfigurationConstants.PREFKEY_LOG_TO_FILE))
			{
				logToFileEnabled = extras.getBoolean(ConfigurationConstants.PREFKEY_LOG_TO_FILE);
				
				libContext.getLogService().setFileLoggingEnabled(logToFileEnabled);
			}			
		}
	}

	/**
	 * checks which network type is currently used 
	 * @return Network type
	 */
	private String getNetworkType()
	{
		TelephonyManager teleMan = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		int networkType = teleMan.getNetworkType();

		switch (networkType)
		{
			case TelephonyManager.NETWORK_TYPE_1xRTT:
				return "1xRTT";
			case TelephonyManager.NETWORK_TYPE_CDMA:
				return "CDMA";
			case TelephonyManager.NETWORK_TYPE_EDGE:
				return "EDGE";
			case TelephonyManager.NETWORK_TYPE_EHRPD:
				return "eHRPD";
			case TelephonyManager.NETWORK_TYPE_EVDO_0:
				return "EVDO_0";
			case TelephonyManager.NETWORK_TYPE_EVDO_A:
				return "EVDO_A";
			case TelephonyManager.NETWORK_TYPE_EVDO_B:
				return "EVDO_B";
			case TelephonyManager.NETWORK_TYPE_GPRS:
				return "GPRS";
			case TelephonyManager.NETWORK_TYPE_HSDPA:
				return "HSDPA";
			case TelephonyManager.NETWORK_TYPE_HSPA:
				return "HSPA";
			case TelephonyManager.NETWORK_TYPE_HSPAP:
				return "HSPA+";
			case TelephonyManager.NETWORK_TYPE_HSUPA:
				return "HSUPA";
			case TelephonyManager.NETWORK_TYPE_IDEN:
				return "iDEN";
			case TelephonyManager.NETWORK_TYPE_LTE:
				return "LTE";
			case TelephonyManager.NETWORK_TYPE_UMTS:
				return "UMTS";
			case TelephonyManager.NETWORK_TYPE_UNKNOWN:
				return "";
			default:
				return "New type of network: " + networkType; 
		}
	}
	
	/**
	 * write device information into log
	 */
	private void writeDeviceInfoToLog()
	{
		String report = "\n";
		report += "Device: " + Build.DEVICE + "\n";
		report += "Manufacturer: " + Build.MANUFACTURER + "\n";
		report += "Model: " + Build.MODEL + "\n";
		report += "Product name: " + Build.PRODUCT + "\n";
		report += "Android version: " + Build.VERSION.RELEASE + "\n\n";
		
		libContext.getLogService().writeLog(Log.INFO, "Device info", report);
	}
}
