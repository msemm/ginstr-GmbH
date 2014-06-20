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

import com.ginstr.android.service.baseservice.ServiceResult;
import android.content.Intent;


/**
* Interface to be implemented by service result listeners.
* @author Alessandro Valbonesi
* @version 1.0
*/
public interface ServiceResultListener {
	/**
	 * Invoked when the service provides a result supplied by a external Activity.
     * @param requestCode the request code to identify the call
     * @param resultCode the result code from the called Activity
     * @param data data returned from the called Activity
	 */
    void onServiceResult(int requestCode, int resultCode, Intent data);

	/**
	 * Invoked when the service provides a internally supplied result.
     * @param event the result event carrying the resulting data
	 */
    void onServiceResult(ServiceResult event);

}
