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
package com.ginstr.android.service.opencellid.collect.data;

/**
 * Public definitions of ACTIONS, EXTRAS_KEYS, PREFKEYS and default values for global settings broadcast changes.
 * @author Roberto, Danijel
 *
 */
public class ConfigurationConstants {

    /**
     * Global ACTION for configuration changes broadcasting
     */
    public static final String GLOBAL_SETTTINGS_RECEIVER_ACTION = "com.ginstr.android.service.opencellid.setGlobalSettings";
    
    /**
     * Turn off GPS after the following inactivity time interval, in millis.
     */
    public static final String PREFKEY_LIST_TURN_GPS_OFF_LONG = "list_turngpsoff";
    public static final long TURN_GPS_OFF_DEFAULT = 300000L;
    public static final long TURN_GPS_TIME_OFF_ALWAYS_RUNNING = 0L;

    /**
     * disable power saving while charging 
     */
    public static final String PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN = "prefkey_powersaving_disable_while_charging";
    public static final boolean DISABLE_POWER_SAVING_WHILE_CHARGING_DEFAULT = true;
    
    /**
     * Enable all power saving features on this battery charge level, except if PREFKEY_DISABLE_POWER_SAVING_WHILE_CHARGING_BOOLEAN is in effect.
     */
    public static final String PREFKEY_ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_INT = "prefkey_powersaving_enable_on_battery_level";
    public static final int ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DEFAULT = 50;
    public static final int ENABLE_POWER_SAVING_ON_BATTERY_PERCENTAGE_DISABLED = 0;
    
    /*
     * Cell quality related settings
     */
    
    /**
     * Minimum distance between two consecutive measures of the same cell, in meters.
     */
    public static final String PREFKEY_MIN_DISTANCE_BETWEEN_MEASURES_INT= "prefkey_min_distance_between_measures";
    public static final int MIN_DISTANCE_BETWEEN_MEASURES_DISABLED = 0;
    public static final int MIN_DISTANCE_BETWEEN_MEASURES_DEFAULT = 100; // 100 meters
    
    
    /**
     * Minimum time difference two consecutive measures of the same cell, in millis.
     */
    public static final String PREFKEY_MIN_TIME_BETWEEN_MEASURES_LONG= "prefkey_min_time_between_measures";
    public static final int MIN_TIME_BETWEEN_MEASURES_DISABLED = 0;
    public static final int MIN_TIME_BETWEEN_MEASURES_DEFAULT = 5000; // 5 seconds

    /**
     * Exclude dimming of the screen when collecting data to read signal strength changes
     * and cell changes. 
     */
    public static final String PREFKEY_DIM_SCREEN_WHEN_COLLECTING_BOOLEAN = "prefkey_dim_screen_when_collecting";
    public static final boolean DIM_SCREEN_WHEN_COLLECTING_DEFAULT = false;
    
    /** Screen dimming will run every x ms */
    public static final String PREFKEY_DIM_SCREEN_THREAD_SLEEP_TIME_LONG = "prefkey_dim_screen_thread_sleep_time";
    public static final long DIM_SCREEN_THREAD_SLEEP_TIME = 120000L;
    
    /** Database size in megabytes */
    public static final String PREFKEY_MAX_DB_SIZE_INT = "pref_db_size";
    public static final int MAX_DB_SIZE_DEFAULT = 500; 
    
    /** Database size shouldn't exceed minimum free space on SD card. */
    public static final String PREFKEY_MIN_FREE_SPACE_INT = "pref_min_free_space";
    public static final int MIN_FREE_SPACE = 50; // 50 Mb
    
    /** Key to set notification title */
    public static final String PREFKEY_NOTIFICATION_TITLE = "pref_notif_title";
    public static final String NOTIFICATION_TITLE = "OpenCellIDService is collecting cells";
    
    /** Key to set notification text */
    public static final String PREFKEY_NOTIFICATION_TEXT = "pref_notif_text";
    public static final String NOTIFICATION_TEXT = "Cell towers are being located with the help of GPS.";
    
    /** Key to enable/disable of uploading of networks */
    public static final String PREFKEY_COLLECT_NETWORKS = "pref_collect_networks";
    public static final boolean COLLECT_NETWORKS_DEFAULT = true;
    
    /** Key to enable/disable passive GPS mode */
    public static final String PREFKEY_GPS_PASSIVE_MODE = "pref_gps_passive_mode";
    public static final boolean GPS_PASSIVE_MODE_DEFAULT = false;
    
    /** Key to enable/disable foreground mode */
    public static final String PREFKEY_FOREGROUND_SERVICE_MODE = "pref_foreground_service_mode";
    public static final boolean FOREGROUND_SERVICE_MODE_DEFAULT = true;    
     
}
