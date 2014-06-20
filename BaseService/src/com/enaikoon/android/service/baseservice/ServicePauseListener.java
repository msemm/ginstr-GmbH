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

/**
 * Interface to be implemented by service onPause listeners.
 * 
 * @author BOJAN POLANEC , <br>
 *         https://www.odesk.com/users/~01e376241da8e9afd9<br>
 */
public interface ServicePauseListener {
    /**
     * Invoked when the service receives a onPause lifecycle call by the
     * Activity.
     */
    void onServiceOnPause();

}
