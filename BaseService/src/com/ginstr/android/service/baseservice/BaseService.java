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

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * <p>
 * Abstract base class for generic service management.
 * <p>
 * A service is a stand-alone component which can be started and stopped at any
 * time, and while running it may at any time perform operations, allocate and
 * deallocate resources and provide results.
 * 
 * <h2>Creating a service</h2>
 * 
 * <p>
 * To create a service, extend the <code>BaseService</code> class and implement
 * {@link BaseService#onServiceStart onServiceStart} and
 * {@link BaseService#onServiceStop onServiceStop}. <br>
 * In these methods you create and destroy your services and resources.
 * <h2>Starting and stopping a service</h2>
 * <p>
 * A service can be started and stopped at anytime calling
 * {@link BaseService#startService startService} and
 * {@link BaseService#stopService stopService}.
 * 
 * <h2>Listening to service Events</h2>
 * 
 * <p>
 * You can listen on your service for the following events:
 * <li>To be notified when your service starts, use
 * {@link BaseService#addServiceStartListener addServiceStartListener}
 * <li>To be notified when your service stops, use
 * {@link BaseService#addServiceStopListener addServiceStopListener}
 * 
 * <h2>Receiving results from a service</h2>
 * 
 * <p>
 * A service can provide custom results at any time while is running.
 * <p>
 * To receive results you have to add a {@link ServiceResultListener
 * ServiceResultListener} to your service using the
 * {@link BaseService#addServiceResultListener addServiceResultListener} method.
 * <p>
 * Results can be provided by your service in 2 different ways:
 * <li>Internally, by calling {@link BaseService#generateResult generateResult}
 * and passing resulting data,
 * <li>Externally, by calling a external activity with a Intent and getting the
 * result back with an event-based mechanism.
 * <p>
 * To enable the return of external results, you have to support the
 * <code>onActivityResult</code> lifecycle call (see Handling lifecycle calls
 * below)
 * <p>
 * Resulting data is passed along to the event handler:
 * {@link ServiceResultListener#onServiceResult(ServiceResult event)}
 * <p>
 * In case the result is provided by the Service, the ServiceResult object
 * contains the data. You have to cast the data to the specific result type for
 * the service.
 * <p>
 * In case the result is obtained by a call to an external Activity, the
 * ServiceResult object contains a ActivityResult object, which in turn contains
 * the result returned by the Activity (request code, result code, data)
 * 
 * <h2>Handling lifecycle calls received by the parent Activity</h2>
 * 
 * <p>
 * Your service can be notified when the parent Activity receives some lifecycle
 * calls.
 * <p>
 * The supported lifecycle calls are the following:
 * <li><code>onActivityResult</code>
 * <li><code>onNewIntent</code>
 * <p>
 * To enable the support of lifecycle calls, you do the following:
 * <li>Based on the lifecycle call you are interested in, implement the
 * appropriate interface (e.g. {@link ActivityResultReceiver
 * ActivityResultReceiver} for <code>onActivityResult</code>) in your parent
 * Activity and write the required methods (e.g.
 * <code>addActivityResultListener</code> and
 * <code>dispatchActivityResult</code>) with standard event handling technique
 * (keep an ArrayList of listeners).
 * <li>Override the appropriate lifecycle call (e.g.
 * <code>onActivityResult</code>) in your Activity and from there call the
 * dispatcher method (e.g. <code>dispatchActivityResult</code>) where you will
 * dispatch the results to all the registered listeners.
 * <p>
 * 
 * <h2>Logging</h2>
 * 
 * Logging is implemented with an event handling mechanism.
 * <li>To generate log events call the {@link BaseService#generateLog
 * generateLog} or {@link BaseService#generateErrorLog generateErrorLog} methods.
 * <li>To register for receiving events from some external class, add a log
 * event listener to the service using {@link BaseService#addLogEventListener
 * addLogEventListener}.
 * <li>To assign a Log tag to this service use {@link BaseService#setLogTag
 * setLogTag}. If a tag is not assigned, the class name of the service is used.
 * 
 * <h2>Resource management - !!NOT IMPLEMENTED!!</h2>
 * 
 * <p>
 * A Service can obtain localized resources from the Resources server.
 * <p>
 * After downloading, resources are stored in the SharedPreferences folder, in a
 * XML file with the same name of the service code.
 * <p>
 * To download resources from server invoke the
 * {@link BaseService#syncResources syncResources} method.
 * <p>
 * Resources are downloaded asynchronously so they might not be available
 * immediately (we should also have the chance to download them synchronously if
 * this the first time. A fix for this problem must be found)
 * 
 * @author Alessandro Valbonesi
 * @version 1.0
 */
public abstract class BaseService implements ActivityResultListener, ActivityNewIntentListener, ActivityOnResumeListener, ActivityOnPauseListener {

    // list of LogEventListener
    private ArrayList<LogEventListener> logListeners;

    // list of ServiceStartEventListener
    private ArrayList<ServiceStartListener> serviceStartListeners;

    // list of ServiceStopEventListener
    private ArrayList<ServiceStopListener> serviceStopListeners;

    // list of ServiceResultEventListener
    private ArrayList<ServiceResultListener> serviceResultListeners;

    // list of ServiceResultEventListener
    private ArrayList<ServiceNewIntentListener> serviceNewIntentListeners;

    // list of ServiceResumeEventListener
    private ArrayList<ServiceResumeListener> serviceResumeListeners;

    // list of ServicePauseEventListener
    private ArrayList<ServicePauseListener> servicePauseListeners;

    // context in which the service is running
    private Context context;

    // a code to uniquely identify this service - no spaces allowed
    private String serviceCode;

    // if the service has been started
    private boolean started;

    // the logging tag for this service
    private String logTag;

    // language code for resources
    private String langCode = "";

    // domain name for resources
    private String domainName = "en_GB";
    
    // flag for test mode
    private boolean testMode = false;
    

    /**
     * Creates this service.
     * 
     * @param context
     *            the current context where the service will execute.
     * @param serviceCode
     *            a code to identify this service to the resource backend - no
     *            spaces allowed
     * @throws Exception
     */
    public BaseService(Context context, String serviceCode) {
        super();
        this.context = context;

        if (!serviceCode.contains(" ")) {
            this.serviceCode = serviceCode;
        } else {
            throw new InvalidServiceCodeError(serviceCode);
        }

        // creates listener lists
        this.serviceStartListeners = new ArrayList<ServiceStartListener>();
        this.serviceStopListeners = new ArrayList<ServiceStopListener>();
        this.serviceResultListeners = new ArrayList<ServiceResultListener>();
        this.serviceNewIntentListeners = new ArrayList<ServiceNewIntentListener>();
        this.serviceResumeListeners = new ArrayList<ServiceResumeListener>();
        this.servicePauseListeners = new ArrayList<ServicePauseListener>();

        this.logListeners = new ArrayList<LogEventListener>();

        // add listeners to Activity lifecycle
        Activity act = getActivity();
        if (act != null) {

            // listen to onActivityResult
            if (act instanceof ActivityResultReceiver) {
                // casts the parent Activity as a ActivityResultReceiver
                // and adds the service itself as a ActivityResultListener
                ActivityResultReceiver receiver = (ActivityResultReceiver) act;
                receiver.addActivityResultListener(getService());

            }

            // listen to onNewIntent
            // This is called for activities that set launchMode to "singleTop"
            // in their package, or if a client used the
            // FLAG_ACTIVITY_SINGLE_TOP flag when calling startActivity(Intent).
            if (act instanceof ActivityNewIntentReceiver) {
                // casts the parent Activity as a ActivityIntentReceiver
                // and adds the service itself as a ActivityIntentListener
                ActivityNewIntentReceiver receiver = (ActivityNewIntentReceiver) act;
                receiver.addActivityNewIntentListener(getService());

            }

            // listen to onResume
            if (act instanceof ActivityOnResumeReceiver) {
                // casts the parent Activity as a ActivityOnResumeReceiver
                // and adds the service itself as a ActivityOnResumeListener
                ActivityOnResumeReceiver receiver = (ActivityOnResumeReceiver) act;
                receiver.addActivityOnResumeListener(getService());
            }

            // listen to onPause
            if (act instanceof ActivityOnPauseReceiver) {
                // casts the parent Activity as a ActivityOnPauseReceiver
                // and adds the service itself as a ActivityOnPauseListener
                ActivityOnPauseReceiver receiver = (ActivityOnPauseReceiver) act;
                receiver.addActivityOnPauseListener(getService());
            }
        }


    }

    /**
     * Starts the service. Notifies the OnServiceStart listeners.
     */
    public void startService() {
        if (!isStarted()) {
            onServiceStart();
            this.started = true;
            notifyServiceStartListeners();
        }
    }

    /**
     * Starts internal services. Implement this method in your specific subclass
     * and start your services here. Here you should start threads and allocate
     * resources for your service.
     */
    public abstract void onServiceStart();

    /**
     * Stops the service. Notifies the OnServiceStop listeners.
     */
    public void stopService() {
        if (isStarted()) {
            onServiceStop();
            this.started = false;
            notifyServiceStopListeners();
        }
    }

    /**
     * Stops internal services. Implement this method in your specific subclass
     * to stop your services. Here you should stop any running thread and
     * releases resources
     */
    public abstract void onServiceStop();

    /**
     * Adds a LogEvent listener, which will be notified when a log is generated.
     * 
     * @param listener
     *            the listener
     */
    public void addLogEventListener(LogEventListener listener) {
        this.logListeners.add(listener);
    }

    /**
     * Adds a OnServiceStartListener, which will be notified when service is
     * started.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceStartListener(ServiceStartListener listener) {
        this.serviceStartListeners.add(listener);
    }

    /**
     * Notifies the OnServiceStartListener listeners
     */
    private void notifyServiceStartListeners() {
        for (ServiceStartListener listener : serviceStartListeners) {
            listener.onServiceStarted();
        }
    }

    /**
     * Notifies the OnServiceResultListener listeners
     * 
     * @param requestCode
     *            the request code to identify the call
     * @param resultCode
     *            the result code from the called Activity
     * @param data
     *            data returned from the called Activity
     */
    private void notifyServiceResultListeners(int requestCode, int resultCode, Intent data) {
        ActivityResult aResult = new ActivityResult(requestCode, resultCode, data);
        ServiceResult sResult = new ServiceResult(aResult);
        notifyServiceResultListeners(sResult);
    }

    /**
     * Adds a OnServiceStopListener, which will be notified when service is
     * stopped.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceStopListener(ServiceStopListener listener) {
        this.serviceStopListeners.add(listener);
    }

    /**
     * Notifies the OnServiceStopListener listeners
     */
    private void notifyServiceStopListeners() {
        for (ServiceStopListener listener : serviceStopListeners) {
            listener.onServiceStopped();
        }
    }

    /**
     * Adds a OnServiceResultListener, which will be notified when service
     * provides a result.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceResultListener(ServiceResultListener listener) {
        this.serviceResultListeners.add(listener);
    }

    /**
     * Notifies the OnServiceResultListener listeners
     * 
     * @param event
     *            the result event carrying the resulting data
     */
    private void notifyServiceResultListeners(ServiceResult event) {
        for (ServiceResultListener listener : serviceResultListeners) {
            listener.onServiceResult(event);
        }
    }

    /**
     * A result has been received by a external activity.
     * 
     * @param requestCode
     *            the request code to identify the call
     * @param resultCode
     *            the result code from the called Activity
     * @param data
     *            data returned from the called Activity
     */
    public void onActivityResultReceived(int requestCode, int resultCode, Intent data) {
        notifyServiceResultListeners(requestCode, resultCode, data);
    }

    /**
     * Dispatch a ServiceResult to all registered listeners.
     * 
     * @param the
     *            ServiceResult to dispatch
     */
    public void generateResult(ServiceResult result) {
        notifyServiceResultListeners(result);
    }

    /**
     * Adds a OnServiceNewIntentResultListener, which will be notified when
     * service receives a new Intent.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceNewIntentListener(ServiceNewIntentListener listener) {
        this.serviceNewIntentListeners.add(listener);
    }

    /**
     * Notifies the OnNewIntentListener listeners
     * 
     * @param newIntent
     *            the received Intent
     */
    private void notifyNewIntentListeners(Intent newIntent) {
        for (ServiceNewIntentListener listener : serviceNewIntentListeners) {
            listener.onServiceNewIntent(newIntent);
        }
    }

    /**
     * Invoked when a onNewIntent is received by the Activity
     * 
     * @param newIntent
     *            the received Intent
     */
    public void onNewIntentReceived(Intent newIntent) {
        notifyNewIntentListeners(newIntent);
    }

    /**
     * Adds a OnServiceResumeListener, which will be notified when service
     * receives onResume.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceOnResumeListener(ServiceResumeListener listener) {
        this.serviceResumeListeners.add(listener);
    }

    /**
     * Notifies the OnResumeListener listeners
     */
    private void notifyOnResumeListeners() {
        for (ServiceResumeListener listener : serviceResumeListeners) {
            listener.onServiceOnResume();
        }
    }

    /**
     * Invoked when a onResume is received by the Activity
     * 
     */
    public void onResumeReceived() {
        notifyOnResumeListeners();
    }

    /**
     * Adds a OnServicePauseListener, which will be notified when service
     * receives onPause.
     * 
     * @param listener
     *            the listener
     */
    public void addServiceOnPauseListener(ServicePauseListener listener) {
        this.servicePauseListeners.add(listener);
    }

    /**
     * Notifies the OnPauseListener listeners
     */
    private void notifyOnPauseListeners() {
        for (ServicePauseListener listener : servicePauseListeners) {
            listener.onServiceOnPause();
        }
    }

    /**
     * Invoked when a onPause is received by the Activity
     * 
     */
    public void onPauseReceived() {
        notifyOnPauseListeners();
    }

    /**
     * Dispatch a Log event to all registered listeners.
     * 
     * @param type
     *            the type of log (@see Android Log)
     * @param tag
     *            for the log
     * @param msg
     *            the log message
     */
    public void generateLog(int type, String tag, String msg) {
        for (LogEventListener listener : logListeners) {
            listener.onLogReceived(type, tag, msg);
        }
    }

    /**
     * Dispatch a Log event to all registered listeners.
     * <p>
     * The service's log tag is used.
     * 
     * @param type
     *            the type of log (@see Android Log)
     * @param msg
     *            the log message
     */
    public void generateLog(int type, String msg) {
        generateLog(type, getLogTag(), msg);
    }
    
	/**
     * Dispatch a Error Log event to all registered listeners
	 * @param tag the log tag
	 * @param msg the log message
     * @param tr Throwable object to extract stack trace
     */
    public void generateErrorLog(String tag, String msg, Throwable tw){
        for (LogEventListener listener : logListeners) {
            listener.onErrorLogReceived(tag, msg, tw);
        }
    }
    
	/**
     * Dispatch a Error Log event to all registered listeners
     * The service's log tag is used.
	 * @param msg the log message
     * @param tr Throwable object to extract stack trace
     */
    public void generateErrorLog(String msg, Throwable tw){
    	generateErrorLog(getLogTag(), msg, tw);
    }

    /**
     * Retrieves the current context.
     * 
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    /**
     * Retrieves the current context as Activity.
     * 
     * @return the context as Activity or null if context is not a Activity
     */
    public Activity getActivity() {
        Activity act = null;
        if (context != null) {
            if (context instanceof Activity) {
                act = (Activity) context;
            }
        }
        return act;
    }

    /**
     * Retrieves this service.
     * 
     * @return this service
     */
    public BaseService getService() {
        return this;
    }

    /**
     * Checks if the service has been started.
     * 
     * @return true if the service is started
     */
    public boolean isStarted() {
        return this.started;
    }

    /**
     * Sets a Log tag for this service.
     * <p>
     * All log events generated by the service will be tagged with this tag.
     * 
     * @param tag
     *            the tag for the service logs.
     */
    public void setLogTag(String tag) {
        this.logTag = tag;
    }

    /**
     * Returns the logging Tag for this service.
     * 
     * @return the logging tag
     */
    public String getLogTag() {
        String tag = "";
        if (this.logTag == null) {
            tag = getClass().getSimpleName();
        }
        return tag;
    }

    /**
     * Sets the test mode flag for the service.
     * <p>While in test mode, a Service could decide to modify some behaviours,
     * e.g. use test server or enable extended logging.
     * <p>However, the implementation of the exact behaviour is delegated to the specific Service.
     * @param testMode true to enable test mode
     */
	public void setTestMode(boolean testMode) {
		this.testMode = testMode;
	}

    
    /**
     * Retrieves the current test mode.
     * @return the current test mode
     */
    public boolean isTestMode() {
		return testMode;
	}

	/**
     * Error class for invalid service code
     */
    private class InvalidServiceCodeError extends Error {

        // default id, unused
        private static final long serialVersionUID = 1L;

        public InvalidServiceCodeError(String serviceCode) {
            super("Invalid service code: " + serviceCode + ". Must not contain spaces");
        }

    }

}
