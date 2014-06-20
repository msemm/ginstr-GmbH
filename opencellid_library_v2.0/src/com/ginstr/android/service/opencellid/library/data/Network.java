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
 * Used for records from 'networks' table.
 * 
 * @author Roberto
 * @author Danijel
 * @author Dinko Ivkovic
 */
public class Network {

	/**
	 *  network type constants
	 */
	public static final String GSM_NETWORK = "GSM";
	public static final String CDMA_NETWORK = "CDMA";
	
	/**
	 * Network parameters
	 */
	private long timestamp;
	private int mcc;
	private int mnc;
	private String type;
	private String name;
	private boolean uploaded;

	/**
	 * Network constructor
	 * 
	 * @param aTimestamp
	 * @param aMcc
	 * @param aMnc
	 * @param netType
	 * @param aNetwork
	 * @param isUploaded
	 */
	public Network(final long aTimestamp, final int aMcc, final int aMnc, final String netType, final String aNetwork, final boolean isUploaded) {
		super();
		this.timestamp = aTimestamp;
		this.mcc = aMcc;
		this.mnc = aMnc;
		this.type = netType;
		this.name = aNetwork;
		this.uploaded = isUploaded;
	}

	/**
	 * gets the timestamp value
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return this.timestamp;
	}

	/**
	 * sets the timestamp value
	 * @param aTimestamp
	 *            the timestamp to set
	 */
	public void setTimestamp(long aTimestamp) {
		this.timestamp = aTimestamp;
	}

	/**
	 * checks if the Network is uploaded
	 * @return true if the Network is uploaded, false otherwise
	 */
	public boolean isUploaded() {
		return uploaded;
	}

	/**
	 * sets the uploaded Network state
	 * @param uploaded
	 *            the uploaded to set
	 */
	public void setUploaded(boolean uploaded) {
		this.uploaded = uploaded;
	}

	/**
	 * gets the Network mcc value
	 * @return the mcc
	 */
	public int getMcc() {
		return this.mcc;
	}

	/**
	 * sets the Network mcc value
	 * @param aMcc
	 *            the mcc to set
	 */
	public void setMcc(int aMcc) {
		this.mcc = aMcc;
	}

	/**
	 * gets the Network mnc value
	 * @return the mnc
	 */
	public int getMnc() {
		return this.mnc;
	}

	/**
	 * sets the Network mnc value
	 * @param aMnc
	 *            the mnc to set
	 */
	public void setMnc(int aMnc) {
		this.mnc = aMnc;
	}

	/**
	 * gets the Network type
	 * @return type of the network
	 */
	public String getType() {
		return type;
	}
	/**
	 * Sets the network type as a name.
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * gets the Network name
	 * @return aNet
	 */
	public String getName() {
		return name;
	}

	/**
	 * sets the Network name
	 * @param network name
	 */
	public void setName(String net) {
		this.name = net;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + mcc;
		result = prime * result + mnc;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Network other = (Network) obj;
		if (mcc != other.mcc)
			return false;
		if (mnc != other.mnc)
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder txt = new StringBuilder("Network ").append(name).append(" (");
		txt.append("MNC:").append(mnc).append(", ");
		txt.append("TYPE: ").append(type).append(", ");
		txt.append("MCC:").append(mcc);
		txt.append(")");

		return txt.toString();
	}
}
