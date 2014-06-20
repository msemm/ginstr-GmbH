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
package com.enaikoon.android.gps.core.filtering;

import android.location.Location;

/**
 * Wrapper reporting the result of a single GPS data validation
 * @author: Alessandro Valbonesi
 */
public class GPSReport {
	
	/**
	 * Enum of error reasons with descriptions
	 */
	public enum ErrorReason
	{
		NONE("none"), ACCURACY("Accuracy"), ALTITUDE("Altitude"), DOP("Dop"), DISTANCE("Distance"), ACCELERATION("Acceleration too high"), FIX("Fix"), TIME("Time"), POSITION0("Zero position"), OMITTED("Omitted after invalid reading"), MINDISTANCE("Minimum distance less than 5m");

		private String descr="";
		
		private ErrorReason(String descr){
			this.descr = descr;
		}
		
		private String getDescription(){
			return descr;
		}
	}

	// location
	private Location location;

	// whether strictChecking was enabled in validation
	private boolean strictCheck;

	// error reason in case of error
	private ErrorReason reason;
	
	// optional error detail
	private String errDetail="";
	
	/**
	 * Constructor
	 * @param location location
	 * @return whether strictChecking was enabled in validation
	 * @param reason the reason for the error
	 * @param errDetail optional error detail
	 * */
	public GPSReport(Location location, boolean strict, ErrorReason reason, String errDetail) {
		this.location=location;
		this.strictCheck=strict;
		this.reason=reason;
		this.errDetail=errDetail;
	}
	
	/**
	 * Constructor for a positive report
	 * @param location location
	 * @return whether strictChecking was enabled in validation
	 */
	public GPSReport(Location location, boolean strict) {
		this(location, strict, null, null);
	}

	
	/**
	 * @return a text for the log
	 * composed of error description and error details
	 */
	public String getLogText(){
		String text = "";
		if (!isSuccess()) {
			text+=getErrorReason().getDescription();
			if (!(getErrorDetails().equals(""))) {
				text+=" - "+getErrorDetails();
			}
		}
		return text;
	}

	/**
	 * @return the validated location
	 */
	public Location getLocation() {
		return location;
	}

	/**
	 * @return whether strictChecking was enabled in validation
	 */
	public boolean isStrictCheck() {
		return strictCheck;
	}

	/**
	 * @return the error reason
	 */
	public ErrorReason getErrorReason(){
		return reason;
	}
	
	/**
	 * Sets the error reason
	 * @param reason the error reason
	 */
	public void setErrorReason(ErrorReason reason){
		this.reason=reason;
	}

	
	/**
	 * @return the error details
	 */
	public String getErrorDetails(){
		return errDetail;
	}

	/**
	 * Sets the error details
	 * @param details the error details
	 */
	public void setErrorDetails(String details){
		this.errDetail=details;
	}

	/**
	 * @return true if validation succeeded, false otherwise
	 */
	public boolean isSuccess(){
		return (reason==null);
	}


	
}
