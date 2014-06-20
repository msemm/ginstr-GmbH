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
package com.enaikoon.android.service.opencellid.upload.data;

import android.os.Environment;

/**
 * Public definitions of ACTIONS, EXTRAS_KEYS, PREFKEYS and default values for global settings broadcast changes.
 * @author Danijel
 * @author Dinko Ivkovic
 *
 */
public class UploadConstants {
	/**
	 * Action for sending broadcast configuration
	 */
	public static final String CONFIGURATION_ACTION = "com.enaikoon.android.service.opencellid.upload.data.setConfiguration";
   
    /**
     * Configuration key for reading a public key for sending measurements to opencellid database
     */
    public static final String PREF_API_KEY = "prefkey_api_key";
    
    /**
     * Configuration key used to read upload URL for OpenCellID servers.
     */
    public static final String PREF_OPENCELL_UPLOAD_URL_KEY = "pref_opencell_upload_url";
    
    /**
     * Default upload URL for OpenCellId servers.
     */
    public static final String OPEN_CELL_DEFAULT_UPLOAD_URL = "http://www.opencellid.org/measure/uploadCsv";
    
    /**
     * Used to set network upload URL.
     */
    public static final String PREF_OPENCELL_NETWORK_UPLOAD_URL_KEY = "network_upload_url";
    
    /**
     * Default upload URL for networks on OpenCellId servers.
     */
    public static final String OPENCELL_NETWORKS_UPLOAD_URL = "http://opencellid.org/gsmCell/mccmnc/addNetworks";
    
    /**
     * Configuration key used to read interval in ms for new data checks.
     */
    public static final String PREF_NEW_DATA_CHECK_INTERVAL_KEY = "pref_new_data_check_interval";

    /**
     * Default value for periodic database checks for new data - 30 seconds.
     */
    public static final long NEW_DATA_CHECK_INTERVAL_LONG_DEFAULT = 30000L;
    
    /**
     * Configuration key to check if only WiFi network will be used to upload data.
     */
    public static final String PREF_ONLY_WIFI_UPLOAD_KEY = "pref_only_wifi_upload";
    
    /**
     * defalut value if only WiFi network will be used to upload data.
     */
    public static final boolean PREF_ONLY_WIFI_UPLOAD_DEFAULT = true;    
    
    /**
     * Configuration key to check if the app is running in test or production environment
     */
    public static final String PREF_TEST_ENVIRONMENT_KEY = "pref_test_environment";
    
    /**
     * Default value for test environment
     */
    public static final boolean PREF_TEST_ENVIRONMENT = false;    
    
    /**
     * Test URL for OpenCellId servers.
     */
    public static final String OPEN_CELL_TEST_UPLOAD_URL = "http://test-ocid.enaikoon.de/measure/uploadCsv";   
    
    /**
     * Test URL for networks on OpenCellId servers.
     */
    public static final String OPENCELL_NETWORKS_TEST_UPLOAD_URL = "http://test-ocid.enaikoon.de/gsmCell/mccmnc/addNetworks"; 
    
    /**
     * Configuration key for reading an application id
     */
    public static final String PREF_APPID_KEY = "prefkey_appid_key";  
    
    /**
     * exception message displayed in case when application ID parameter is not valid
     */
	public static final String CONSTRUCTOR_EXCEPTION_MESSAGE = "appId parameter is not well formed!";    
}
