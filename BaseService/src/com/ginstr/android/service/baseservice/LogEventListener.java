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
package com.ginstr.android.service.baseservice;

/**
* Interface to be implemented by log event listeners.
* @author Alessandro Valbonesi
* @version 1.0
*/

public interface LogEventListener {
	
	/**
	 * Invoked when a log event is received
	 * @param type the type of log (@see Android Log)
	 * @param tag for the log
 	 * @param msg the log message 
	 */
    void onLogReceived( int type, String tag, String msg);
    
	/**
	 * Invoked when a error log event is received
	 * @param tag for the log
 	 * @param msg the log message 
 	 * @param tw the throwable for the error 
	 */
    void onErrorLogReceived(String tag, String msg, Throwable tw);

}
