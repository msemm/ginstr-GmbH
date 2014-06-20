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
package com.ginstr.android.service.opencellid.library.data;

/**
 * A CellID+Location -pair.
 * 
 * @author Marcus Wolschon (Marcus@Wolschon.biz)
 */
public class Measurement {
	private long timestamp;
	private int cellid;
	private int lac;
	private Network net;
	private int gsmSignalStrength;
	private double lat;
	private double lon;
	private float speed;
	private float bearing;
	private boolean uploaded;
	private String networkType;
	private Float accuracy;

	/**
	 * Measurement constructor
	 * 
	 * @param aTimestamp
	 * @param aCellid
	 * @param aMcc
	 * @param aMnc
	 * @param aLac
	 * @param aGsmSignalStrength
	 * @param aLat
	 * @param aLon
	 * @param aSpeed
	 * @param aBearing
	 * @param aNetwork
	 *            may be null
	 */
	public Measurement(final long aTimestamp, final int aCellid, final int aMcc, final int aMnc,
			final int aLac, final String netType, final int aGsmSignalStrength, final double aLat, final double aLon,
			final float aSpeed, final float aBearing, final String aNetwork,
			final boolean isUploaded, String networkType, Float accuracy) {
		super();

		this.timestamp = aTimestamp;
		this.cellid = aCellid;
		this.lac = aLac;
		this.net = new Network(aTimestamp, aMcc, aMnc, netType, aNetwork, false);
		this.gsmSignalStrength = aGsmSignalStrength;
		this.lat = aLat;
		this.lon = aLon;
		this.speed = aSpeed;
		this.bearing = aBearing;
		this.uploaded = isUploaded;
		this.networkType = networkType;
		this.accuracy = accuracy;
	}

	/**
	 * checks if Measurement is uploaded 
	 * @return true if Measurement is uploaded, false otherwise
	 */
	public boolean isUploaded() {
		return uploaded;
	}

	/**
	 * gets the Measurement time stamp value
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return this.timestamp;
	}

	/**
	 * gets the Measurement cell id value
	 * @return the cellid
	 */
	public int getCellid() {
		return this.cellid;
	}

	
	/**
	 * gets the Measurement mcc value
	 * @return the mcc
	 */
	public int getMcc() {
		return this.net.getMcc();
	}

	/**
	 * gets the Measurement mnc value
	 * @return the mnc
	 */
	public int getMnc() {
		return this.net.getMnc();
	}

	/**
	 * gets the Measurement lac value
	 * @return the lac
	 */
	public int getLac() {
		return this.lac;
	}

	/**
	 * Get the GSM Signal Strength, valid values are (0-31, 99) as defined in TS
	 * 27.007 8.5 (http://www.xs4all.nl/~m10/mac/downloads/3GPP-27007-630.pdf) 0
	 * -113 dBm or less 1 -111 dBm 2...30 -109... -53 dBm 31 -51 dBm or greater
	 * 99 not known or not detectable (Windows Mobile would have
	 * LINEDEVSTATUS.dwSignalLevel in the range 0x00000000 (weakest signal) to
	 * 0x0000FFFF (strongest signal).)
	 * 
	 * @return the gsmSignalStrength
	 */
	public int getGsmSignalStrength() {
		return this.gsmSignalStrength;
	}

	/**
	 * gets the signal strength value
	 * @see http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.2_r1.1/com/android/server/status/StatusBarPolicy.java#StatusBarPolicy.updateSignalStrength%28%29
	 * @see http://stackoverflow.com/questions/7782028/display-androids-getgsmsignalstrength-value-in-percentage
	 * @param signal
	 * @return int signal value
	 */
	public static int gsmSignalStrengthTodBm(final int signal) {
		// FileLog.writeToLog(Meassurement.class.getName() +
		// ":gsmSignalStrengthTodBm(): gsmSignalStrengthTodBm(" + signal + ")");
		if (signal < 0) {
			// FileLog.writeToLog(Meassurement.class.getName() +
			// ":gsmSignalStrengthTodBm(): gsmSignalStrengthTodBm(" + signal +
			// ") invalid input-range (too low)");
			return -128;
			// return 0;
		}
		/*
		 * if (signal > 31 && signal != 99) {
		 * FileLog.writeToLog(Meassurement.class.getName() +
		 * ":gsmSignalStrengthTodBm(): gsmSignalStrengthTodBm(" + signal +
		 * ") invalid input-range (too high)"); // return 0; }
		 */
		if (signal == 0) {
			return -113;
		}
		if (signal == 1) {
			return -111;
		}
		if (signal == 99) {
			return 0;
		}
		if (signal >= 32) {
			return -51;
		}
		return -109 - (signal - 2) * ((53 - 109) / (30 - 2));

	}

	/**
	 * gets signal strength in dBm value
	 * @return signal strength in dBm
	 */
	public int getGsmSignalStrengthIndBm() {
		return gsmSignalStrengthTodBm(getGsmSignalStrength());
	}

	/**
	 * gets the latitude value
	 * @return the latitude value
	 */
	public double getLat() {
		return this.lat;
	}

	/**
	 * gets the longitude value
	 * @return the longitude value
	 */
	public double getLon() {
		return this.lon;
	}

	/**
	 * gets the speed value
	 * @return the speed
	 */
	public float getSpeed() {
		return this.speed;
	}

	/**
	 * gets the bearing value
	 * @return the bearing
	 */
	public float getBearing() {
		return this.bearing;
	}

	/**
	 * gets the network name
	 * @return may be null
	 */
	public String getNetworkName() {
		return net.getName();
	}

	/**
	 * gets the Network object
	 * @return network object
	 */
	public Network getNetwork() {
		return net;
	}

	/**
	 * Compares a measurement to this one to see if they belong to
	 * the same tower cell.
	 * 
	 * @param other Measurement
	 * @return true if they're the same
	 */
	public boolean sameCell(Measurement other) {
		return other != null && other.getCellid() == cellid && other.getMnc() == getMnc() && other.getMcc() == getMcc();
	}

	@Override
	public String toString() {
		StringBuilder txt = new StringBuilder("[");
		txt.append(timestamp)
		.append(" | ").append(cellid)
		.append(" | ").append(getMcc())
		.append(" | ").append(getMnc())
		.append(" | ").append(lac)
		.append(" | ").append(gsmSignalStrength)
		.append(" | ").append(lat)
		.append(" | ").append(lon)
		.append(" | ").append(speed)
		.append(" | ").append(bearing)
		.append(" | ").append(getNetworkName()) 
		.append(" | ").append(uploaded)
		.append("]");

		return txt.toString();
	}
	
	/**
	 * gets the network type
	 * @return String network type, it may be null
	 */
	public String getNetworkType() {
		return networkType;
	}
	
	/**
	 * gets the accuracy value
	 * @return Float accuracy, it may be null
	 */
	public Float getAccuracy() {
		return accuracy;
	}		
}
