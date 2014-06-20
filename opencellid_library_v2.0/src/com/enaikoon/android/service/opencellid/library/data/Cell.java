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

/**
 * This class is used to store cell data. <br/>
 * 
 * @author Roberto
 */
public class Cell {

	protected long mTimestamp;
	protected int mCellid;
	protected int mLac;
	protected String networkType;
	protected Network mNet;
	protected double mLat;
	protected double mLon;
	protected int mNumSamples;

	/**
	 * Construct a new Cell object from an existing copy.
	 * @param pCell Existing Cell object.
	 */
	public Cell(Cell pCell) {
		this.mTimestamp = pCell.getTimestamp();
		this.mCellid = pCell.getCellid();
		this.mLac = pCell.getLac();
		this.mNet = new Network(0, pCell.getMcc(), pCell.getMnc(),
							    pCell.getNetworkType(), null, false);
		this.mLat = pCell.getLat();
		this.mLon = pCell.getLon();
		this.mNumSamples = pCell.getNumSamples();
	}

	/**
	 * Cell object constructor
	 * 
	 * @param aTime
	 * @param aCellid
	 * @param aMcc 
	 * @param aMnc
	 * @param aLac
	 * @param netType
	 * @param aLat
	 * @param aLon
	 * @param aNumSamples
	 */
	public Cell(final long aTime, final int aCellid, final int aMcc, final int aMnc,
			final int aLac, final String netType, final double aLat, final double aLon,
			final int aNumSamples) {
		super();

		this.mTimestamp = aTime;
		this.mCellid = aCellid;
		this.mLac = aLac;
		this.mNet = new Network(0, aMcc, aMnc, netType, null, false);
		this.mLat = aLat;
		this.mLon = aLon;
		this.mNumSamples = aNumSamples;
	}

	/**
	 * retrieve time stamp of the measurement
	 * @return time stamp of the measurement
	 */
	public long getTimestamp() {
		return mTimestamp;
	}

	/**
	 * retrieve the cell id
	 * @return the cellid
	 */
	public int getCellid() {
		return this.mCellid;
	}

	/**
	 * retrieve the mcc value
	 * @return the mcc
	 */
	public int getMcc() {
		return this.mNet.getMcc();
	}

	/**
	 * retrieve the mnc value
	 * @return the mnc
	 */
	public int getMnc() {
		return this.mNet.getMnc();
	}

	/**
	 * retrieve the lac value
	 * @return the lac
	 */
	public int getLac() {
		return this.mLac;
	}

	/**
	 * retrieve the latitude value
	 * @return double latitude
	 */
	public double getLat() {
		return this.mLat;
	}

	/**
	 * retrieve the longitude value
	 * @return double longitude
	 */
	public double getLon() {
		return this.mLon;
	}

	/**
	 * retrieve the number of samples
	 * @return int number of samples
	 */
	public int getNumSamples() {
		return mNumSamples;
	}

	/**
	 * retrieve the network name
	 * @return String network name, it may be null
	 */
	public String getNetworkName() {
		return mNet.getName();
	}
	
	/**
	 * retrieve the network type 
	 * @return String denoting network type
	 */
	public String getNetworkType() {
		return networkType;
	}

	/**
	 * retrieve the Network object
	 * @return Network object in which the cell is located
	 */
	public Network getNetwork() {
		return mNet;
	}

	/**
	 * Compares two cells data
	 * 
	 * @param mcc Mobile country code
	 * @param mcc Mobile network code
	 * @param cellid Cell tower ID
	 * @param lac Local area code
	 * @return true if this Cell object is the same compared to the input data
	 */
	public boolean sameCell(int mcc, int mnc, int cellid, int lac) {
		return getMcc() == mcc && getMnc() == mnc && mCellid == cellid && mLac == lac;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + mCellid;
		result = prime * result + mLac;
		result = prime * result + ((mNet == null) ? 0 : mNet.hashCode());
		return result;
	}

	/**
	 * Compares two Cell objects.
	 * @return true if they are the same
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Cell)) {
			return false;
		}
		Cell other = (Cell) obj;
		return sameCell(other.getMcc(), other.getMnc(), other.getCellid(), other.getLac());
	}
}
