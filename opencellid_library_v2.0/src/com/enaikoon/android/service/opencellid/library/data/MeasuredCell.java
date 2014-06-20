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
package com.enaikoon.android.service.opencellid.library.data;

import android.telephony.TelephonyManager;

/**
 * Cell that represents the estimated cell position of the local measurements.
 * Use for cell export when not a real cell exists for a given measurement.
 * @author Roberto
 */
public class MeasuredCell extends Cell {
	protected boolean mUploaded;
	protected boolean mNewCell;

	/**
	 * MeasuredCell constructor
	 * @param pCell {@link Cell}
	 */
	public MeasuredCell(Cell pCell) {
		super(pCell);
		mUploaded = true;
		mNewCell = false;
	}

	/**
	 * MeasuredCell constructor
	 * 
	 * @param aTime
	 * @param aCellid
	 * @param aMcc
	 * @param aMnc
	 * @param aLac
	 * @param netType
	 * @param aLat
	 * @param aLon
	 * @param pNumSamples
	 * @param pUploaded
	 * @param pNewCell
	 */
	public MeasuredCell(long aTime, int aCellid, int aMcc, int aMnc, int aLac, String netType, double aLat, double aLon, int pNumSamples, boolean pUploaded, boolean pNewCell) {
		super(aTime, aCellid, aMcc, aMnc, aLac, netType, aLat, aLon, pNumSamples);
		mUploaded = pUploaded;
		mNewCell = pNewCell;
	}

	/**
	 * MeasuredCell constructor
	 * 
	 * @param m {@link Measurement}
	 */
	public MeasuredCell(Measurement m) {
		this(m.getTimestamp(), m.getCellid(), m.getMcc(), m.getMnc(), m.getLac(), null, m.getLat(), m.getLon(), 1, false, true);
		mUploaded = false;
		mNewCell = true;
	}

	/**
	 * checks if MeasuredCell is uploaded 
	 * @return true if MeasuredCell is uploaded, false otherwise
	 */
	public boolean isUploaded() {
		return mUploaded;
	}

	/**
	 * checks if MeasuredCell is new 
	 * @return true if the MeasuredCell is new, false otherwise
	 */
	public boolean isNewCell() {
		return mNewCell;
	}

	/**
	 * Adds a new factor to the calculations of the latitude and longitude of this cell.
	 * This is a continuous process, applying the formulae P = (Z*n+a)/(n+1)
	 * @param pLat
	 * @param pLon
	 */
	public void addFactor(double lat, double lon, int numSamples) {
		double newLat = (this.mLat * mNumSamples + lat * numSamples) / (mNumSamples + numSamples);
		double newLon = (this.mLon * mNumSamples + lon * numSamples) / (mNumSamples + numSamples);

		this.mLat = newLat;
		this.mLon = newLon;

		mNumSamples += numSamples;
		mUploaded = false;
	}

	/**
	 * Adds a new factor to the calculations of the latitude and longitude of this cell.
	 * This is a continuous process, applying the formulae P = (Z*n+a)/(n+1)
	 * @param pMeasurement {@link Measurement}
	 */
	public void addFactor(Measurement pMeasurement) {
		addFactor(pMeasurement.getLat(), pMeasurement.getLon(), 1);
		this.mTimestamp = Math.max(this.mTimestamp, pMeasurement.getTimestamp());
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}
}
