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
package com.ginstr.android.gps.core.filtering;


import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.ginstr.android.gps.core.filtering.GPSReport.ErrorReason;
import com.ginstr.android.service.logservice.LogService;

/**
 * GPSDataValidator checks the validity of a GPS signal both internally 
 * and against a previous GPS signal.
 * <p>Signals are validated through the {@link GPSDataValidator#validateGPSData validateGPSData} method.
 * <p>When a valid location is acquired, it is stored as the <code>lastValidLocation</code>
 *  in the instance of GPSDataValidator.
 * <p>Signals are validated checking location properties for internal congruency, 
 * and if a last valid location is present, also the dynamics of the movement 
 * are checked to see if they acceptable (speed, acceleration etc...)
 * <p>After a invalid signal is acquired, the next 3 signals are discarded 
 * as invalid even if they look valid, due to GPS re-sync issues.
 * <p>You can eventually set the last valid location manually using {@link GPSDataValidator#setLastValidLocation setLastValidLocation} 
 * 
 * <h2>Usage pattern</h2>
 * 
 * <p>1) instantiate a new <code>GPSDataValidator</code> to start a validation session
 * <pre>
 * <code>
 * GPSDataValidator validator = new GPSDataValidator();
 * </code>
 * </pre>
 * <p>2) read GPS and validate locations
 * <pre>
 * <code>
 * while(!stop){ 
 * 	Location newLocation = myGPS.getLocation(); 
 * 	GPSReport report = validator.validateGPSData(newLocation);
 * 	if(report.isSuccess()){ 
 * 		doSomething(newLocation); 
 * 	}else{
 * 		Log.i(report.getLogText()); 
 * 	} 
 * }
 * </code>
 * </pre>
 * 
 * <h2>Logging</h2>
 * 
 * <p>A internal Logging Service is used to log events. 
 * This service can be obtained calling {@link GPSDataValidator#getLogService getLogService}.
 * <p>When a signal doesn't pass validation, a log event is raised with the specific error details.
 * <p>To register for receiving log events, add a LogEventListener to the Logging Service:
 * <pre>
 * <code>
 * GPSDataValidator validator = new GPSDataValidator();
 * validator.getLogService().addLogEventListener(new LogEventListener() {
 *     public void onLogReceived(int type, String tag, String msg) {
 *         // do something here with the log info
 *     }
 * });
 * </code>
 * </pre>
 * 
 * <h2>Strict mode</h2>
 * 
 * <p>This setting controls the strictness of some checkings.
 * <p>By default strict checking is off. To enable strict checking 
 * use {@link GPSDataValidator#setStrictCheck setStrictCheck(true)} 
 * <p>When Strict mode is on, the following rules apply:
 * <li><strong>Accuracy</strong> lower than 20m.</li>
 * <li><strong>DOP</strong> (Diluition of Precision) must be low.</li>
 * <li><strong>Satellites:</strong> Signal has to refer to minimum of 4 satellites to be valid.</li>
 */
public class GPSDataValidator {

	public static final String HVDOP_MIN_3 = "HDOP and VDOP < 3";
	public static final String HVDOPS_MIN_5_AND_8 = "HDOP and VDOP < 5 and (HDOP + VDOP) < 8";
	public static final String LOCATION_LAT_LON_IS_ZERO = "newLocation.getLatitude is zero and newLocation.getLongitude is zero";
	public static final String NEW_LOCATION_TIME_BEFORE_LAST_LOCATION_TIME = "new location time < last location time";
	public static final String ACCELERATION_TOO_HIGH = "Acceleration too high";
	public static final String NUMBER_OF_USED_SATELLITES_MIN_3 = "number of used satellites < 3";
	public static final String NUMBER_OF_USED_SATELLITES_MIN_4 = "number of used satellites < 4";
	public static final String MIN_DISTANCE_LESS_THAN = "minimum distance is less than ";
	public static final String SPACE = " ";
	public static final String METHOD = "Method:" + SPACE;

	// the last valid GPS reading
	private Location lastValidLocation;
	
	// current GPS reading
	private Location newLocation;
	
	// number of visible satellites
	private int numberOfSatellites;
	
	// current HDOP value
	private double HDOP;
	
	// current VDOP value
	private double VDOP;

	// flag to control strict checking
	private boolean strictCheck;
	
	// flag to control minimum distance between two valid points; default 5m
	private int minDistance=5;	

	// count of valid GPS readings remaining to be omitted after a invalid
	// reading
	private short omitValidGPSDataCount;
	
	private LogService logService;

	/**
	 * Constructor
	 */
	public GPSDataValidator(Context context) {
		this.strictCheck = false;
		this.omitValidGPSDataCount = 0;
		this.logService = new LogService(context);
		this.logService.startService();
	}

	/**
	 * Checks the GPS accuracy.
	 * 
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport checkAccuracy(Location newLocation) {
		int allowedAccuracy = 40;
		GPSReport report = new GPSReport(newLocation, this.strictCheck);

		if ((lastValidLocation == null || (newLocation.getTime() - lastValidLocation.getTime()) > 60000) && this.strictCheck) {
			allowedAccuracy = 20;
		}

		if (newLocation.getAccuracy() >= allowedAccuracy) {
			report.setErrorReason(ErrorReason.ACCURACY);
			report.setErrorDetails(getLogString("Location accuracy (" + newLocation.getAccuracy() + " > allowed accuracy (" + allowedAccuracy + ")"));
			
		}
		return report;
	}

	/**
	 * Checks if altitude is valid.
	 * 
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport checkAltitude(Location newLocation) {
		
		GPSReport report = new GPSReport(newLocation, this.strictCheck);

		if (newLocation.getAltitude() > 15000 || newLocation.getAltitude() < -500) {
			report.setErrorReason(ErrorReason.ALTITUDE);
			report.setErrorDetails(getLogString("Location altitude > 15000 or < -500 (" + newLocation.getAltitude() + ")"));

		} else if (newLocation.getAltitude() == 0) {
			report.setErrorReason(ErrorReason.ALTITUDE);
			report.setErrorDetails(getLogString("Location altitude is zero"));
		}

		if (lastValidLocation == null || newLocation.getTime() == lastValidLocation.getTime()) {

			return report;

		} else if (newLocation.getLatitude() == 0 && newLocation.getLongitude() == 0) {

			report.setErrorReason(ErrorReason.ALTITUDE);
			report.setErrorDetails(getLogString("Location latitude and Location longitude are both zero"));

		} else {

			float distance = (float) (newLocation.getAltitude() - lastValidLocation.getAltitude());
			float delta_time = (newLocation.getTime() - lastValidLocation.getTime()) / 1000;

			if (delta_time == 0) {
				return report;
			}

			float speedMS = distance / delta_time;

			if (Math.abs(speedMS) > 100) {
				report.setErrorReason(ErrorReason.ALTITUDE);
				report.setErrorDetails(getLogString("speed is > |100|ms (" + speedMS + ")"));
			}
		}
		return report;
	}

	/**
	 * Checks the values retrieved from NMEA parser.
	 * 
	 * @param HDOP HDOP value from NMEA parser.
	 * @param VDOP VDOP value from NMEA parser.
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport checkDOPValues(double HDOP, double VDOP, Location newLocation) {
		GPSReport report = new GPSReport(newLocation, this.strictCheck);

		if ((lastValidLocation == null || (newLocation.getTime() - lastValidLocation.getTime()) > 60000) && this.strictCheck) {
			boolean isValid = (HDOP < 3 && VDOP < 3);

			if (!isValid) {
				report.setErrorReason(ErrorReason.DOP);
				report.setErrorDetails(getLogString(HVDOP_MIN_3));
			}

		} else {

			boolean isValid = (HDOP < 5 && VDOP < 5 && (HDOP + VDOP) < 8);

			if (!isValid) {
				report.setErrorReason(ErrorReason.DOP);
				report.setErrorDetails(getLogString(HVDOPS_MIN_5_AND_8));
			}
		}
		return report;
	}

	/**
	 * Checks the acceleration between last valid location and current location.
	 * <p>Acceleration is checked as delta speed between loc1 and loc2
	 * <p>Speed in loc1 comes from loc1 reading, speed in loc2 is calculated from distance,
	 * acceleration is delta speed.
	 * <br>Acceleration must not exceed 3 ms2.
	 * 
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport checkAcceleration(Location newLocation) {
		GPSReport report = new GPSReport(newLocation, this.strictCheck);
		try {
			if (lastValidLocation == null || newLocation.getTime() == lastValidLocation.getTime()) {

				return report;

			} else if (newLocation.getLatitude() == 0 && newLocation.getLongitude() == 0) {
				report.setErrorReason(ErrorReason.ACCELERATION);
				report.setErrorDetails(getLogString(LOCATION_LAT_LON_IS_ZERO));

			} else {
				if (newLocation.getTime() < lastValidLocation.getTime()) {
					report.setErrorReason(ErrorReason.ACCELERATION);
					report.setErrorDetails(getLogString(NEW_LOCATION_TIME_BEFORE_LAST_LOCATION_TIME));
				}

				float delta_time = (newLocation.getTime() - lastValidLocation.getTime()) / 1000;
				float distance = (int) (newLocation.distanceTo(lastValidLocation));

				if (delta_time == 0) {
					return report;
				}

				float average_speed = distance / delta_time;
				float delta_speed = average_speed - lastValidLocation.getSpeed();
				float acceleration = delta_speed / delta_time;
				float math = Math.abs(acceleration);
				if (math > 3) {
					report.setErrorReason(ErrorReason.ACCELERATION);
					report.setErrorDetails(getLogString(ACCELERATION_TOO_HIGH)+" (actual "+acceleration+", max 3 ms2)");
				}
			}
		} catch (Exception ex) {
			Log.e("GPSDataValidator", "catch", ex);
			report.setErrorReason(ErrorReason.ACCELERATION);
			report.setErrorDetails(getLogString(ex.getMessage()));
			return report;
		}
		return report;
	}

	/**
	 * Checks the number of used satellites.
	 * 
	 * @param numberOfUsedSatellites count of used satellites.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport gpsFixCheck(Location location, int numberOfUsedSatellites) {
		boolean isValid;
		GPSReport report = new GPSReport(location, this.strictCheck);
		String msg;

		if (!strictCheck) {
			isValid = (numberOfUsedSatellites >= 3);
			msg = NUMBER_OF_USED_SATELLITES_MIN_3;
		} else {
			isValid = (numberOfUsedSatellites >= 4);
			msg = NUMBER_OF_USED_SATELLITES_MIN_4;
		}

		if (!isValid) {
			report.setErrorReason(ErrorReason.FIX);
			report.setErrorDetails(getLogString(msg));
		}
		return report;
	}

	/**
	 * Checks if the time of new location is greater than the time of last valid
	 * location and calculates the time difference between valid GPS time and
	 * current phone's time.
	 * 
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport gpsTimeCheck(Location newLocation) {
		GPSReport report = new GPSReport(newLocation, this.strictCheck);

		if (lastValidLocation != null && newLocation != null) {
			boolean result = (newLocation.getTime() > lastValidLocation.getTime());

			if (!result) {
				report.setErrorReason(ErrorReason.TIME);
				report.setErrorDetails(getLogString(NEW_LOCATION_TIME_BEFORE_LAST_LOCATION_TIME));
			}
		}

		return report;
	}

	/**
	 * Checks if the position of the Location object is 0,0.
	 * 
	 * @param newLocation new location which should be checked.
	 * @return GPSReport object with result checking.
	 */
	private GPSReport latLngCheck(Location newLocation) {
		GPSReport report = new GPSReport(newLocation, this.strictCheck);
		if (newLocation.getLatitude() == 0 && newLocation.getLongitude() == 0) {
			report.setErrorReason(ErrorReason.POSITION0);
			report.setErrorDetails(getLogString(LOCATION_LAT_LON_IS_ZERO));
		}
		return report;
	}
	
	/**
	 * check minimum distance between two valid points
	 * 
	 * @param newLocation object which should be compared with lastValidLocation
	 * @return true if the distance is greater than 5m, false otherwise
	 */
	private GPSReport checkMinimumDistance(Location newLocation)
	{
		GPSReport report = new GPSReport(newLocation, this.strictCheck);
		
		if (lastValidLocation != null && newLocation.distanceTo(lastValidLocation) < minDistance)
		{
			report.setErrorReason(ErrorReason.MINDISTANCE);
			report.setErrorDetails(getLogString(MIN_DISTANCE_LESS_THAN + minDistance + "m"));
		}
		
		return report;
	}

	/**
	 * Validates a GPS location signal against the last valid signal.
	 * <p>Signal is validated both internally and against the last valid location stored.
	 * @param newLocation new location which should be checked.
	 * @param fix the number of used satellites.
	 * @param HDOP HDOP value from NMEA parser.
	 * @param VDOP VDOP value from NMEA parser.
	 * @return GPSReport object with result checking.
	 */
	public GPSReport validateGPSData(Location location) {

		newLocation=location;
		
		GPSReport report = null;
		boolean stateCheck = true;

		// check lat-lon != 0
		if (stateCheck) {
			report = latLngCheck(newLocation);
			stateCheck = postCheckResult(report);
		}

		// check time sequence of the 2 signals
		if (stateCheck) {
			report = gpsTimeCheck(newLocation);
			stateCheck = postCheckResult(report);
		}

		// check # of used satellites
		if (stateCheck) {
			report = gpsFixCheck(newLocation, numberOfSatellites);
			stateCheck = postCheckResult(report);
		}

		// check max acceleration given time and positions
		if (stateCheck) {
			report = checkAcceleration(newLocation);
			stateCheck = postCheckResult(report);
		}

		// check valid altitude
		if (stateCheck) {
			report = checkAltitude(newLocation);
			stateCheck = postCheckResult(report);
		}

		// check min allowed accuracy
		if (stateCheck) {
			report = checkAccuracy(newLocation);
			stateCheck = postCheckResult(report);
		}

		// check DOP values
		if (stateCheck) {
			report = checkDOPValues(HDOP, VDOP, newLocation);
			stateCheck = postCheckResult(report);
		}
		
		// check minimum distance between two valid points
		if (stateCheck) {
			report = checkMinimumDistance(newLocation);
			stateCheck = postCheckResult(report);
		}		

		// omit a number of valid signals after a invalid signal
		if (stateCheck) {
			if (this.omitValidGPSDataCount > 0) {
				this.omitValidGPSDataCount--;
				report = new GPSReport(newLocation, isStrictCheck());
				report.setErrorReason(ErrorReason.OMITTED);
				report.setErrorDetails(getLogString("Omitted after a invalid reading, "+omitValidGPSDataCount+" omissions left"));
			} else {
				setLastValidLocation(newLocation);
			}
		}

		return report;
	}
	
	/**
	 * Validates a GPS location signal against the last valid signal.
	 * <p>Signal is validated both internally and against the last valid location stored.
	 * @param newLocation new location which should be checked.	 * 
	 * @param oldLocation last valid location.
	 * @return GPSReport object with result checking.
	 */	
	public GPSReport validateGPSData(Location newLocation, Location oldLocation) 
	{
		this.lastValidLocation=oldLocation;
		
		return validateGPSData(newLocation);
	}
	
	
	/**
	 * Method to format a string for errorDetail
	 * 
	 * @param msg the message to format
	 * @return formatted string for error log
	 */
	private static String getLogString(String msg) {
		String methodName="";
		StackTraceElement[] stacktrace = new Throwable().getStackTrace();
		if (stacktrace.length>=2) {
			StackTraceElement element=stacktrace[1];
			methodName = element.getMethodName();
		}
		return METHOD + methodName + SPACE + msg;
	}

	/**
	 * Checks the result after a signal checking.
	 * <p>If the result is no success, sets the omit counter to 3
	 * and writes a error log
	 * 
	 * @param report the report to check
	 * @return false if report has errors, true if no errors
	 */
	private boolean postCheckResult(GPSReport report) {
		if (!report.isSuccess()) {
			this.omitValidGPSDataCount = 3;
			this.logService.writeLog(Log.INFO, this.logService.getLogTag(), report.getErrorDetails());
		}
		return report.isSuccess();
	}

	/**
	 * Sets the last valid location
	 * 
	 * @param loc the last valid location
	 */
	public void setLastValidLocation(Location loc) {
		this.lastValidLocation = loc;
	}

	/**
	 * Returns the last valid location
	 * 
	 * @return the last valid location
	 */
	public Location getLastValidLocation() {
		return this.lastValidLocation;
	}

	/**
	 * Toggles strict checking
	 * @param strict true to enable strict checking, false to disable
	 */
	public void setStrictCheck(boolean strict) {
		this.strictCheck = strict;
	}

	/**
	 * Returns the current value for strict checking mode
	 * @return the strict checking mode
	 */
	public boolean isStrictCheck() {
		return this.strictCheck;
	}
	
	/**
	 * Returns the Log Service providing Log events about validation
	 * @return the Log Service
	 */
	public LogService getLogService(){
		return this.logService;
	}
	
	/**
	 * defines minimum distance between old and new location objects
	 * @param distanceInMeters
	 */
	public void setMinimumDistance(int distanceInMeters) {
		this.minDistance = distanceInMeters;
	}
	
	/**
	 * defines current number of visible satellites
	 * @param numberOfSatellites
	 */
	public void setNumberOfSatellites(int numberOfSatellites)
	{
		this.numberOfSatellites=numberOfSatellites;
	}	
	
	/**
	 * defines current HDOP value
	 * @param HDOP
	 */
	public void setHDOP(double HDOP)
	{
		this.HDOP=HDOP;
	}	
	
	/**
	 * defines current VDOP value
	 * @param VDOP
	 */
	public void setVDOP(double VDOP)
	{
		this.VDOP=VDOP;
	}
}
