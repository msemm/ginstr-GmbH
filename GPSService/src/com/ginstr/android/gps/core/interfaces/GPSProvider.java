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

/**
 * Interface for provider {@link #onProviderDisabled(String)} and
 * {@link #onProviderEnabled(String)} when GPSProvider change. <br/>
 * 
 * Implementing this you will receive data whether the GPS provider
 * was enabled or disabled.
 * 
 * @author ginstr
 */
public interface GPSProvider {
	/**
	 * Method which will be called if the GPS provider was enabled.
	 * 
	 * @param s provider name
	 */
	void onProviderEnabled(String s);

	/**
	 * Method which will be called after GPS provider was disabled.
	 * @param s provider name
	 */
	void onProviderDisabled(String s);
}
