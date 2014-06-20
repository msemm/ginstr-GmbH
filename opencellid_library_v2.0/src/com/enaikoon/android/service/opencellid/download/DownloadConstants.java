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
package com.enaikoon.android.service.opencellid.download;

/**
 * Configuration constants for download service.
 * 
 * @author Danijel
 * @author Dinko Ivkovic
 */
public class DownloadConstants {
	/**
     * Global ACTION for configuration changes broadcasting
     */
    public static final String DOWNLOAD_SETTTINGS_RECEIVER_ACTION = "com.enaikoon.android.service.opencellid.download.setConfig";
	
	/**
	 * Used to retrieve a setting for interval of time in which new data will be checked.
	 */
	public static final String PREF_NEW_DATA_CHECK_INTERVAL_KEY = "pref_new_data_check_interval";
	/**
	 * Default time to query servers for new cell data.
	 */
	public static final Long NEW_DATA_CHECK_INTERVAL_DEFAULT = 24 * 60 * 60 * 1000L; // once a day
	
	/**
	 * Used to retrieve a setting for the cells database size.
	 */
	public static final String PREF_CELLS_DATABASE_SIZE_KEY = "pref_cells_database_size_key";
	/**
	 * Default size of cells database in Mb.
	 */
	public static final int CELLS_DATABASE_SIZE_DEFAULT = 300; 
	
	/**
	 * Key used to read minimum free space which the growth of
	 * cells database must not exceed.
	 */
	public static final String PREF_MIN_FREE_SPACE_KEY = "pref_min_free_space_for_cells";
	/**
	 * Default free space in Mb which the growth of cells database must not
	 * exceed.
	 */
	public static final int MIN_FREE_SPACE_DEFAULT = 50;
	
	/**
	 * Key used to read the download url.
	 */
	public static final String PREF_DOWNLOAD_URL_KEY = "pref_download_url";
	/**
	 * Default download URL.
	 */
	public static final String DOWNLOAD_URL_DEFAULT = "http://opencellid.org/downloadCsv";

	/**
	 * Used to pass API key to download service.
	 */
	public static final String PREF_API_KEY_KEY = "pref_api_key";
	
    /**
     * Configuration key to check if the app is running in test or production environment
     */
    public static final String PREF_TEST_ENVIRONMENT_KEY = "pref_test_environment";
    
    /**
     * Default value for test environment
     */
    public static final boolean PREF_TEST_ENVIRONMENT = false;  

}
