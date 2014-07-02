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
package com.ginstr.android.gps.core;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Build;

import com.ginstr.android.service.baseservice.BaseService;
import com.ginstr.android.gps.core.filtering.GPSDataValidator;
import com.ginstr.android.gps.core.filtering.GPSReport;
import com.ginstr.android.gps.core.interfaces.GPSProvider;
import com.ginstr.android.gps.core.interfaces.GPSStatusChanged;
import com.ginstr.android.gps.core.interfaces.LocationChanged;
import com.ginstr.android.gps.core.interfaces.ValidLocationChanged;

/**
 * Provides GPS data and listeners. 
 * <p>This class provides access to the system GPS location services.</p>
 * <p>
 * This class allows you to get GPS location changes, GPS status changes, 
 * provider enabled/disabled change and filtered GPS locations.
 * </p>
 * 
 * If you need are callback to be invoked when location is changed, you need
 * call {@link #setOnLocationChanged(LocationChanged)} with param your listener.<br/>
 * Example:
 * 
 * <pre>
 * <code> 
 * gpsService.setOnLocationChanged(new LocationChanged()
 * 	public void onLocationChanged(Location location) {
 * 		... 
 * 	}
 * });
 * </code>
 * </pre>
 * 
 * If you need are callback to be invoked when status is changed, you need call
 * {@link #setOnStatusChanged(GPSStatusChanged)} with param your listener.<br/>
 * Example:
 * 
 * <pre>
 * <code> 
 * gpsService.setOnStatusChanged(new GPSStatusChanged() {
 * 	public void onStatusChanged(String s, int i, Bundle bundle) {
 * 		...
 * 	}
 * });
 * 
 * </code>
 * </pre>
 * 
 * If you need are callback to be invoked when GPS provider is changed, you need
 * call {@link #setProviderListener(GPSProvider)} with param your listener.<br/>
 * Example:
 * 
 * <pre>
 * <code>
 * gpsService.setProviderListener(new GPSProvider() {
 * 	public void onProviderEnabled(String s) {
 * 		...
 * 	}
 * 
 * 	public void onProviderDisabled(String s) {
 * 		...
 * 	}
 * });
 * </code>
 * </pre>
 * 
 * If you want to receive only filtered location data, you should call 
 * {@link #setOnValidLocationChanged(ValidLocationChanged)} with a implementation
 * of your listener. <br/>
 * 
 * <pre>
 * <code>
 * gpsService.setOnValidLocationChanged(new ValidLocationChanged() {
 * 	onValidLocationChanged(Location location) {
 * 		...
 * 	}
 * });
 * </code>
 * </pre>
 * 
 * @author ginstr
 */
@TargetApi(Build.VERSION_CODES.ECLAIR)
public class GPSService extends BaseService {

	private static final String SERVICE_NAME="GPS_SERVICE";
	private Location lastValidLocation;	// the last valid location received
	private boolean strictCheck; // true to perform strict signal validation
	private LocationManager locationManager;
	private LocationChanged locationChanged;
	private ValidLocationChanged validLocationChanged;
	private GPSStatusChanged statusChanged;
	private GPSProvider provider;
	private Context context;
	private double hdop;
	private double vdop;
	private GpsStatus lastGpsStatus;
	private int inUse;
	private int minDistance=5; // minimum distance between 2 valid points
	private String locationProvider=LocationManager.GPS_PROVIDER;
	
	/**
	 * Initialize LocationManager in the current context.
	 * @param context Valid Context instance - best would be application context object.
	 *            
	 */
	public GPSService(Context context) {
		super(context, SERVICE_NAME);
		this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		this.context = context;
	}

	/**
	 * Register a callback to be invoked when location is changed.
	 * 
	 * @param listener location change listener.
	 */
	public void setOnLocationChanged(LocationChanged listener) {
		this.locationChanged = listener;
	}
	
	/**
	 * Register a callback to be invoked when a new valid location is received.
	 * <p>A new location is validated against the previous valid
     * location found, and the event is launched only if the new location is valid
     * @param listener the listener.
	 */
	public void setOnValidLocationChanged(ValidLocationChanged listener) {
		this.validLocationChanged = listener;
	}


	/**
	 * Register a callback to be invoked when GPS status changed.
	 * <p>
	 * </p>
	 * 
	 * @param listener status changed listener.
	 */
	public void setOnStatusChanged(GPSStatusChanged listener) {
		this.statusChanged = listener;
	}

	/**
	 * Register a callback to be invoked when GPS provider is changed.
	 * <p>
	 * </p>
	 * 
	 * @param listener
	 *            on changed provider listener.
	 */
	public void setProviderListener(GPSProvider listener) {
		this.provider = listener;
	}


	/**
	 * Adds an NMEA listener.
	 * <p>
	 * </p>
	 * 
	 * @param listener
	 *            GPS status listener.<br/>
	 * @throws Throwable.<br/>
	 */
	public void addNmeaListener(GpsStatus.NmeaListener listener) throws Throwable {
		this.locationManager.addNmeaListener(listener);
	}
	
	/**
	 * Removes a NMEA listener.
	 * <p>
	 * </p>
	 * 
	 * @param listener
	 *            Nmea listener.<br/>
	 * @throws Throwable.<br/>
	 */
	public void removeNmeaListener(GpsStatus.NmeaListener listener) throws Throwable {
		this.locationManager.removeNmeaListener(listener);
	}	

	/**
	 * Adds a GPS status listener.
	 * <p>
	 * </p>
	 * 
	 * @param listener
	 *            GPS status listener.<br/>
	 */
	public void addGpsStatusListener(GpsStatus.Listener listener) {
		this.locationManager.addGpsStatusListener(listener);
	}
	
	/**
	 * Removes a GPS status listener.
	 * <p>
	 * </p>
	 * 
	 * @param listener
	 *            GPS status listener.<br/>
	 */
	public void removeGpsStatusListener(GpsStatus.Listener listener) {
		this.locationManager.removeGpsStatusListener(listener);
	}	

	/**
	 * Retrieves information about the current status of the GPS engine.
	 * <p>
	 * </p>
	 * 
	 * @param status
	 *            object containing GPS status details, or null.<br/>
	 * @return status object containing updated GPS status or null if service is stopped
	 */
	public GpsStatus getGPSStatus(GpsStatus status) {
		if (this.locationManager != null)
			return this.locationManager.getGpsStatus(status);
		return null;
	}

	/**
	 * Get count uses satellites.
	 * <p>
	 * </p>
	 * 
	 * @param satellites
	 *            list of all satellites.<br/>
	 * @return count uses satellites.
	 */
	public int getInUseSatellite(List<GpsSatellite> satellites) {
		int inUse = 0;
		for (GpsSatellite sat : satellites) {
			if (sat.getSnr() > 1f) {
				if (sat.usedInFix()) {
					inUse++;
				}
			}
		}
		return inUse;
	}

	/**
	 * Get count view satellites.
	 * <p>
	 * </p>
	 * 
	 * @param satellites
	 *            list of all satellites.<br/>
	 * @return count view satellites.
	 */
	public int getInViewSatellite(List<GpsSatellite> satellites) {
		int inView = 0;
		for (GpsSatellite sat : satellites) {
			if (sat.getSnr() > 1f) {
				inView++;
			}
		}
		return inView;
	}

	/**
	 * Get list of all satellites by GPS status.
	 * <p>
	 * </p>
	 * 
	 * @param gpsStatus GPS status - shouldn't be null.<br/>
	 * @return list of all satellites.
	 */
	public List<GpsSatellite> getGpsSatellite(GpsStatus gpsStatus) {
		List<GpsSatellite> satellites = new ArrayList<GpsSatellite>();
		Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
		for (GpsSatellite sat : sats) {
			satellites.add(sat);
		}
		return satellites;
	}

	/**
	 * Validates a GPS location signal
	 * 
	 * @param lastValidLocation
	 *            - the last valid location against which the 
	 *            new location has to be checked
	 * @param newLocation
	 *            - new location which should be checked
	 * @param fix
	 *            - the number of used satellites
	 * @param HDOP
	 *            - HDOP value from NMEA parser
	 * @param VDOP
	 *            - VDOP value from NMEA parser
	 * 
	 * @param strictCheck
	 *            - some values will be checked more strictly - Accuracy: lower
	 *            than 20m - DOP (Diluition of Precision) must be low - Signal
	 *            has to refer to minimum of 4 satellites to be valid
	 * 
	 * @return true if all checks are valid, false otherwise
	 */
	private boolean validateGPSData(Location lastValidLocation, Location newLocation, int fix, double HDOP, double VDOP, boolean strictCheck, int minDistance) {
		GPSDataValidator validator = new GPSDataValidator(context);
		validator.setStrictCheck(strictCheck);
		validator.setMinimumDistance(minDistance);
		validator.setNumberOfSatellites(fix);
		validator.setHDOP(HDOP);
		validator.setVDOP(VDOP);
		GPSReport report = validator.validateGPSData(newLocation, lastValidLocation);
		return report.isSuccess();
	}
	
	/**
	 * Toggles strict checking
	 * @param strict true to enable strict checking, false to disable
	 */
	public void setStrictCheck(boolean strict) {
		this.strictCheck = strict;
	}
	
	/**
	 * Defines minimum distance between two locations
	 * @param minimumDistance distance in meters
	 */
	public void setMinDistance(int minimumDistance) {
		this.minDistance = minimumDistance;
	}	

	/**
	 * Starts requesting for location updates from the GPS provider.
	 */
	@Override
	public void onServiceStart() {
		if (locationManager == null)
			locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		this.locationManager.requestLocationUpdates(locationProvider, 0, 0, locationListener);
		this.locationManager.addNmeaListener(nmeaListener);
		this.locationManager.addGpsStatusListener(gpsStatusListener);
	}

	/**
	 * Stops requesting for GPS location updates.
	 */
	@Override
	public void onServiceStop() {
		this.locationManager.removeUpdates(locationListener);
		this.locationManager.removeNmeaListener(nmeaListener);
		this.locationManager.removeGpsStatusListener(gpsStatusListener);
		this.locationManager = null;
	}
	
	/**
	 * Used to get location data.
	 */
	LocationListener locationListener = new LocationListener()
	{
		@Override
		public void onLocationChanged(Location location) {
			if (location!=null && location.getProvider().equals(LocationManager.GPS_PROVIDER))
			{
				if (locationChanged != null) {
					locationChanged.onLocationChanged(location);
				}	
				
				// check if the new location is valid and if true launch a valid location event
				boolean valid = validateGPSData(lastValidLocation, location, inUse, hdop, vdop, strictCheck, minDistance);
				if (valid) {
					// update last valid location
					lastValidLocation = location;
					// launch valid location event
					if (validLocationChanged != null)
						validLocationChanged.onValidLocationChanged(location);
				}	
			}
		}

		@Override
		public void onStatusChanged(String s, int i, Bundle bundle) {
			if (statusChanged != null) {
				statusChanged.onStatusChanged(s, i, bundle);
			}
		}

		@Override
		public void onProviderEnabled(String s) {
			if (provider != null) {
				provider.onProviderEnabled(s);
			}
		}

		@Override
		public void onProviderDisabled(String s) {
			if (provider != null) {
				provider.onProviderDisabled(s);
			}
		}
	};
	
	/**
	 * Listener used to get HDOP, VDOP and PDOP values to check accuracy.
	 * It's used to internally to provide support for location filtering.
	 * 
	 * @see http://en.wikipedia.org/wiki/NMEA_0183
	 * @see http://en.wikipedia.org/wiki/Dilution_of_precision_%28GPS%29
	 * @see http://www.kh-gps.de/nmea-faq.htm
	 * 
	 * $GPGSA,A,3,19,28,14,18,27,22,31,39,,,,,1.7,1.0,1.3*35 <br/>
	 * GSA  = GPS receiver operating mode, SVs used for navigation, and DOP values. <br/>
	 * 1    = Mode: <br/>
	 *        M=Manual, forced to operate in 2D or 3D <br/>
	 *        A=Automatic, 3D/2D <br/>
	 * 2    = Mode: <br/>
	 *        1=Fix not available <br/>
	 *        2=2D <br/>
	 *        3=3D <br/>
	 * 3-14 = IDs of SVs used in position fix (null for unused fields) <br/>
	 * 15   = PDOP <br/>
	 * 16   = HDOP <br/>
	 * 17   = VDOP <br/>
	 * 18   = Checksum <br/>
	 */
	private NmeaListener nmeaListener = new NmeaListener() {
		
		@Override
		public void onNmeaReceived(long timestamp, String nmea) {
			if (nmea != null && nmea.startsWith("$GPGSA")) {
				String[] values = nmea.split("[,|\\*]");

				try {
					hdop = Double.valueOf(values[16]).intValue();
					vdop = Double.valueOf(values[17]).intValue();
				} catch (Exception e) {
					hdop = -1;
					vdop = -1;
				}
			}
		}
	};
	
	/** Internal GPS status listener to provide support for location filtering. */
	GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
		@Override
		public void onGpsStatusChanged(int event) {
			if (locationManager != null) {
				lastGpsStatus = locationManager.getGpsStatus(null);
				inUse = getInUseSatellite(getGpsSatellite(lastGpsStatus));
			}
		}
	};
	
	
	/**
	 * Sets the location provider used for obtaining geographic locations.
	 * Defaults to LocationManager.GPS_PROVIDER
	 * @param locationProvider the provider to be used
	 */
	public void setLocationProvider(String locationProvider)
	{
		this.locationProvider=locationProvider;
	}
}