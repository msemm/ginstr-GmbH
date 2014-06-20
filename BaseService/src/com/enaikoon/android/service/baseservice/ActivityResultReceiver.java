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
package com.enaikoon.android.service.baseservice;

import android.content.Intent;

/**
 * Interface for implementing a object capable to receive result from a external
 * activity
 * 
 * @author Alessandro Valbonesi
 * @version 1.0
 */
public interface ActivityResultReceiver {

	/**
	 * Adds a new listener to the list of listeners
	 * 
	 * @param listener
	 *            the listener to add
	 */
	public void addActivityResultListener(ActivityResultListener listener);

	/**
	 * Dispatches a Activity result to all the registered listeners
	 * 
	 * @param requestCode
	 *            the request code
	 * @param requestCode
	 *            the result code
	 * @param data
	 *            extra data returned along with the result
	 */
	public void dispatchActivityResult(int requestCode, int resultCode,
			Intent data);

}
