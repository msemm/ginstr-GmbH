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
package com.ginstr.android.gps.core.interfaces;

import android.os.Bundle;

/**
 * Implement this to receive data when GPS status changes.
 * 
 * @author ginstr
 */
public interface GPSStatusChanged {
	/**
	 * When GPS status changes, this will be called. <br/>
	 * See also {@link android.location.LocationListener#onStatusChanged(String, int, Bundle)}
	 * @param provider Name of the GPS provider
	 * @param status Status
	 * @param extras Extras
	 */
	void onStatusChanged(String provider, int status, Bundle extras);
}
