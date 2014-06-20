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

import android.location.Location;

/**
 * Implement this to receive location updates from the GPS service.
 * It does not provide filtered data.
 * 
 * @author ginstr
 */
public interface LocationChanged {
	/**
	 * Provides a new GPS location object.
	 * @param location
	 */
	void onLocationChanged(Location location);
}
