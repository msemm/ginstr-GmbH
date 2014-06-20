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
package com.ginstr.android.service.logservice;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import com.ginstr.android.service.baseservice.BaseService;

/**
* <h3>General description</h3>
* <p>A Service for extended logging management.
* <p>Logs can be written to Android log, to a file log or both.
* <p>To write a log to the enabled output channels use the {@link LogService#writeLog writeLog} methods.
* <p>By default, only logging to Android log is enabled.
* <p>You can enable/disable the entire logging functionality with {@link LogService#setLoggingEnabled setLoggingEnabled}. If logging is disabled, all the logging commands have no effect
* <h3>File logging</h3>
* <p>To enable file logging, use {@link LogService#setFileLoggingEnabled}. To use file logging, you have to add:
* <p>{@code <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />}
* <p>to the manifest of the host app.
* <p>The logs are stored in the SDcard in the external files directory for your app 
* <br>(usually {@code sdcard/Android/data/<yourpackage>/files/})
* <p>You can change the target directory and file name using the appropriate setters.
* @author Alessandro Valbonesi
* @version 2.0
*/
public class LogService extends BaseService {
	
	// service code
	private static final String SERVICE_CODE="LOGSERVICE";

	// if entire logging is enabled or disabled
	private boolean loggingEnabled=true;

	// if logging to Android log is enabled
	private boolean androidLoggingEnabled;

	// if logging to file is enabled
	private boolean fileLoggingEnabled;
	
	// the directory where log files are maintained
	// by default, the ExternalStorageDirectory of the host app
	private File logDirectory;
	
	// the base filename for logs (without extensions, .log is added).
	// if log rotation is enabled, numbers will be added to this base name
	// oterwise, it is used as is.
	// by default, is the name of the app
	private String baseLogFilename;
	
	// to format date/time in log file
	private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

	/**
	 * Creates a LogService.
	 * @param context the current context
	 */
    @TargetApi(Build.VERSION_CODES.FROYO)
	public LogService(Context context) {
    	super(context,SERVICE_CODE);
    	
    	// defaults for enabling output channels
    	setAndroidLoggingEnabled(true);
    	setFileLoggingEnabled(false);

    	// default log directory
    	logDirectory=context.getExternalFilesDir(null);
    	
    	// retrieve logfile default basename
    	// try with app label, use "logfile" if not found
    	String name="";
        int stringId = context.getApplicationInfo().labelRes;
        try {
        	name=context.getString(stringId);
		} catch (Exception e) {
		}
        if (name==null || name.equals("")) {
        	name="logfile";
		}
        baseLogFilename=name;
    	
	}
    
	@Override
	public void onServiceStart() {
	}

	@Override
	public void onServiceStop() {
	}


	/**
     * Write a Log to the enabled logging output channels
	 * @param type the log type (@see Android Log)
	 * @param tag the log tag
	 * @param msg the log message
     */
    public void writeLog(int type, String tag, String msg){
    	writeLog(type, tag, msg, null);
    }

    
	/**
     * Write a Log to the enabled logging output channels
	 * @param type the log type (@see Android Log)
	 * @param tag the log tag
	 * @param msg the log message
     * @param tr Throwable object to extract stack trace
     */
    public void writeLog(int type, String tag, String msg, Throwable tw){
    	
    	if (isStarted()) {
    		
    		if (isLoggingEnabled()) {
    			
        		// write to file log
            	if (fileLoggingEnabled) {
            		writeLogToFile(type, tag, msg, tw);
        		}
            	
        		// write to Android log
            	if (androidLoggingEnabled) {
            		if (tw!=null) {
            			msg+=" - "+Log.getStackTraceString(tw);
    				}
            		Log.println (type, tag, msg);
        		}

			}
        	
    	}else{
    		Log.e(getLogTag(), "Log generation requested while service is not started.");
		}
    	
    }
    
    
	/**
     * Write a Log to the file output channel
	 * @param type the log type (@see Android Log)
	 * @param tag the log tag
	 * @param msg the log message
     * @param tr Throwable object to extract stack trace
     */
    private void writeLogToFile(int type, String tag, String msg, Throwable tw){
		File logFile = getCurrentLogFile();
		
		if (logFile!=null) {
			
			try {
				
				String prefix="";
				switch (type) {
				case Log.ASSERT:
					prefix="A";
					break;
				case Log.DEBUG:
					prefix="D";
					break;
				case Log.ERROR:
					prefix="E";
					break;
				case Log.INFO:
					prefix="I";
					break;
				case Log.VERBOSE:
					prefix="V";
					break;
				case Log.WARN:
					prefix="W";
					break;
				default:
					break;
				}
				
				String time = dateFormat.format(new Date());

				String text = prefix+", "+time+", "+tag+", "+msg;
        		if (tw!=null) {
        			text+=", "+Log.getStackTraceString(tw);
				}
        		text+="\n";

				FileOutputStream fos = new FileOutputStream (logFile, true);
    			fos.write(text.getBytes());
    			fos.close();
    			
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else {
    		String text = "The log file can't be created. Did you add permission WRITE_EXTERNAL_STORAGE to the host app?";
    		Log.e(SERVICE_CODE, text);
			Toast toast = Toast.makeText(getContext(), text, Toast.LENGTH_LONG);
			toast.show();
		}
	
    }
    
    
	/**
     * Write a Error Log to the logging output channels
	 * @param tag the log tag
	 * @param msg the log message
     */
    public void writeErrorLog(String tag, String msg){
    	writeLog(Log.ERROR, tag, msg, null);
    }

	/**
     * Write a Error Log to the logging output channels
	 * @param tag the log tag
	 * @param msg the log message
     * @param tr Throwable object to extract stack trace
     */
    public void writeErrorLog(String tag, String msg, Throwable tw){
    	writeLog(Log.ERROR, tag, msg, tw);
    }
    
	
	/**
	 * Retrieves the file currently used to store the logs
	 * @return the current log file, it can be null
	 */
	private File getCurrentLogFile(){
		File logFile=null;
		if (logDirectory!=null && baseLogFilename!=null && baseLogFilename!="") {
			logFile = new File(logDirectory, baseLogFilename+".log");
		}
		return logFile;
	}
	
	
	
	/**
	 * Checks if logging is enabled
	 * @return true if logging is enabled
	 */
	public boolean isLoggingEnabled() {
		return loggingEnabled;
	}

	/**
	 * Enables or disables entire logging functionality.
	 * <p>If logging is disabled, all the logging commands have no effect
	 * @param flag to enable/disable logging
	 */
	public void setLoggingEnabled(boolean flag) {
		this.loggingEnabled = flag;
	}

	/**
	 * Checks if logging to file is enabled.
	 * @return if file logging is enabled
	 */
	public boolean isFileLoggingEnabled() {
		return fileLoggingEnabled;
	}

	/**
	 * Enables or disables logging to file
	 * @param flag to enable/disable
	 */
	public void setFileLoggingEnabled(boolean flag) {
		this.fileLoggingEnabled = flag;
	}
	
	/**
	 * Sets the directory used to store log files
	 * @param directory the directory
	 */
	public void setLogDirectory(File directory) {
		directory.mkdirs();
		this.logDirectory = directory;
	}

	/**
	 * Sets the filename of the log file
	 * if log rotation is enabled, numbers will be added automatically to this base name
	 * @param filename the filename
	 */
	public void setLogFilename(String filename) {
		this.baseLogFilename = filename;
	}

	/**
	 * Checks if Android logging is enabled.
	 * @return if Android logging is enabled
	 */
	public boolean isAndroidLoggingEnabled() {
		return androidLoggingEnabled;
	}

	/**
	 * Enables logging to Android log
	 * @param flag to enable/disable
	 */
	public void setAndroidLoggingEnabled(boolean flag) {
		this.androidLoggingEnabled = flag;
	}


}
