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
package com.ginstr.android.service.opencellid.library.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import com.ginstr.android.service.opencellid.library.data.Cell;
import com.ginstr.android.service.opencellid.library.data.MeasuredCell;
import com.ginstr.android.service.opencellid.library.data.Measurement;
import com.ginstr.android.service.opencellid.library.data.Network;
import com.ginstr.android.service.opencellid.library.db.CellsTable.CellsDBIterator;
import com.ginstr.android.service.opencellid.library.db.MeasuredCellsTable.MeasuredCellsDBIterator;
import com.ginstr.android.service.opencellid.library.db.MeasurementsDatabase;
import com.ginstr.android.service.opencellid.library.db.MeasurementsTable.MeasurementsDBIterator;
import com.ginstr.android.service.opencellid.library.db.NetworksTable.NetworkDBIterator;
import com.ginstr.android.service.opencellid.library.db.OpenCellIdLibContext;

/**
 * Holds a collection of static functions for exporting data from
 * local OpenCellID database.
 * 
 * @author Danijel
 * @author Dinko Ivkovic
 *
 */
public class Exporter {
	private static final String TAG = Exporter.class.getSimpleName();
	
	private Exporter() {}

	/**
	 * Exports records from 'measurements' table into a CSV or CLFv3 file.
	 * 
	 * @param libContext a valid OpenCellIdLibContext context
	 * @param isCsv true if the output should be into a CSV file
	 * @param from time in milliseconds
	 * @param to time in milliseconds
	 * @param filename output file name. It should full path.
	 * @return number of exported records
	 * @throws IllegalArgumentException if libContext is null, from >= to, filename null or empty
	 */
	public static int exportMeasurements(OpenCellIdLibContext libContext, boolean isCsv, 
										 long from, long to, String filename) throws IllegalArgumentException {
		
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (from >= to)
			throw new IllegalArgumentException("from >= to");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename is null or empty");
		
		MeasurementsDatabase db = libContext.getMeasurementsDatabase();
		
		int count = 0;
		
		try {
			// get a list of all measurements in defined time range
			MeasurementsDBIterator iterator = db.getAllMeasurementsForTimeRange(from, to);
			
			if (iterator.getCount() > 0) {
				File f = new File(filename);
				
				if (!f.getParentFile().exists()) {
					f.getParentFile().mkdirs();
				}
	
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
				
				// check the export format
				if (isCsv)
					writer.append("timestamp,cellid,mnc,mcc,lac,reception,lat,lon,speed,heading,uploaded\n");
				else
					writer.append("//cell list exchange format v3.0//\n");
				
				while (iterator.hasNext()) {
					Measurement t = iterator.next();
	
					StringBuilder sb = new StringBuilder();
					if (isCsv) {
						sb.append(t.getTimestamp()).append(",");
						sb.append(t.getCellid()).append(",");
						sb.append(t.getMnc()).append(",");
						sb.append(t.getMcc()).append(",");
						sb.append(t.getLac()).append(",");
						sb.append(t.getGsmSignalStrengthIndBm()).append(",");
						sb.append(t.getLat()).append(",");
						sb.append(t.getLon()).append(",");
						sb.append(t.getSpeed()).append(",");
						sb.append(t.getBearing()).append(",");
						sb.append(t.isUploaded() == true ? "1" : "0");
						sb.append("\n");
					} else {
						// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
						
						sb.append("" + t.getMcc() + t.getMnc() + ";");
						sb.append(t.getCellid() + ";");
						sb.append("-1;"); //RNC unknown
						sb.append(t.getLat() + ";"); // LAT
						sb.append(t.getLon() + ";"); // LON
						sb.append("-1;"); // POS-RAT
						sb.append("OpenCellID community data;");
						sb.append("0;"); // Radio system
						sb.append("Unknown;"); // LABEL
						sb.append("-1;"); // AZI
						sb.append("-1;"); // height
						sb.append("-1"); // bandwidth
						sb.append("\n");
					}
					writer.append(sb.toString());
	
					count++;
				}
	
				writer.flush();
				writer.close();
			}
			
			iterator.close();
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}
		
		return count;
	}
	/**
	 * Exports all records from table 'measured_cells' into a CSV or CLFv3 file.
	 * 
	 * @param libContext a valid OpenCellIdLibContext context
	 * @param isCsv true if the output should be into a CSV file
	 * @param from time in milliseconds
	 * @param to time in milliseconds
	 * @param filename output file name. It should full path.
	 * @return number of exported records
	 * @throws IllegalArgumentException if libContext is null, from >= to, filename null or empty
	 */
	public static int exportMeasuredCells(OpenCellIdLibContext libContext, boolean isCsv, 
										  long from, long to, String filename) throws IllegalArgumentException {
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (from >= to)
			throw new IllegalArgumentException("from >= to");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename is null or empty");
		
		int count = 0;
		
		try {
			// get a list of all measured cells in defined time range
			MeasuredCellsDBIterator iterator = libContext.getMeasurementsDatabase().getAllMeasuredCellsForTimeRange(from, to);
			
			if (iterator.getCount() > 0) {
				File f = new File(filename);
				if (!f.getParentFile().exists()) {
					f.getParentFile().mkdirs();
				}
	
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
				
				// check the export format
				if (isCsv)
					writer.append("timestamp,mcc,mnc,cellid,lac,lat,lon,nsamples,uploaded,newcell\n");
				else
					writer.append("//cell list exchange format v3.0//\n");
				
				while (iterator.hasNext()) {
					MeasuredCell t = iterator.next();
	
					StringBuilder sb = new StringBuilder();
					if (isCsv) {
						sb.append(t.getTimestamp()).append(",");
						sb.append(t.getMcc()).append(",");
						sb.append(t.getMnc()).append(",");
						sb.append(t.getCellid()).append(",");
						sb.append(t.getLac()).append(",");
						sb.append(t.getLat()).append(",");
						sb.append(t.getLon()).append(",");
						sb.append(t.getNumSamples()).append(",");
						sb.append(t.isUploaded() == true ? "1" : "0").append(",");
						sb.append(t.isNewCell() == true ? "1" : "0");
						sb.append("\n");
					} else {
						// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
						
						sb.append("" + t.getMcc() + t.getMnc() + ";");
						sb.append(t.getCellid() + ";");
						sb.append("-1;"); //RNC unknown
						sb.append(t.getLat() + ";"); // LAT
						sb.append(t.getLon() + ";"); // LON
						sb.append("-1;"); // POS-RAT
						sb.append("OpenCellID community data;");
						sb.append("0;"); // Radio system
						sb.append("Unknown;"); // LABEL
						sb.append("-1;"); // AZI
						sb.append("-1;"); // height
						sb.append("-1"); // bandwidth
						sb.append("\n");
					}
					writer.append(sb.toString());
	
					count++;
				}
	
				writer.flush();
				writer.close();
			}
			
			iterator.close();
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}
		
		return count;
	}
	
	/**
	 * Exports all records from table 'networks' into a CSV file.
	 * 
	 * @param libContext a valid OpenCellIdLibContext context
	 * @param filename output file name. It should full path.
	 * @return number of exported records
	 * @throws IllegalArgumentException if libContext is null, filename null or empty
	 */
	public static int exportNetworks(OpenCellIdLibContext libContext, String filename) throws IllegalArgumentException {
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename is null or empty");
		
		int count = 0;
		
		try {
			// get a list of all collected networks
			NetworkDBIterator iterator = libContext.getMeasurementsDatabase().getAllNetworks();
			if (iterator.getCount() > 0) {
				File f = new File(filename);
				if (!f.getParentFile().exists()) {
					f.getParentFile().mkdirs();
				}
		
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
				writer.append("timestamp,mnc,mcc,type,name,uploaded\n");
				
				while (iterator.hasNext()) {
					Network net = iterator.next();
					
					StringBuilder sb = new StringBuilder();
					sb.append(net.getTimestamp() + ",");
					sb.append(net.getMnc() + ",");
					sb.append(net.getMcc() + ",");
					sb.append(net.getType() + ",");
					sb.append(net.getName() + ",");
					sb.append((net.isUploaded() == true ? "1" : "0") + "\n");
					
					writer.append(sb.toString());
					
					count++;
				}
				
				writer.flush();
				writer.close();
			}
			
			iterator.close();
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}
		
		return count;
	}
	
	/**
	 * Export all cells from Cells table into a CSV or CLFv3 file.
	 * 
	 * <p>CSV file has to have structure and a header with column names:<br/>
	 * <code>latitude,longitude,mcc,mnc,lac,cell_id,samples,created,updated\n</code>
	 * </p>
	 * 
	 * @param libContext a valid OpenCellIdLibContext context
	 * @param isCsv true if the output should be into a CSV file
	 * @param from time in milliseconds
	 * @param to time in milliseconds
	 * @param filename output file name. It should full path.
	 * @return number of records exported
	 */
	public static int exportCells(OpenCellIdLibContext libContext, boolean isCsv, long from, long to, String filename) throws IllegalStateException {
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (from >= to)
			throw new IllegalArgumentException("from >= to");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename is null or empty");
		
		int count = 0;
		
		try {
			// get a list of all cells in defined time range
			CellsDBIterator dbIterator = libContext.getCellsDatabase().getAllCellsForTimeRange(from, to);
			
			if (dbIterator.getCount() > 0) {
				File f = new File(filename);
				if (!f.getParentFile().exists()) {
					f.getParentFile().mkdirs();
				}

				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
				
				// check the export format
				if (isCsv)
					writer.append("latitude,longitude,mcc,mnc,lac,cell_id,samples,created,updated\n");
				else
					writer.append("//cell list exchange format v3.0//\n");
				
				while (dbIterator.hasNext()) {
					Cell t = dbIterator.next();

					StringBuilder sb = new StringBuilder();
					if (isCsv) {
						sb.append(t.getLat()).append(",");
						sb.append(t.getLon()).append(",");
						sb.append(t.getMcc()).append(",");
						sb.append(t.getMnc()).append(",");
						sb.append(t.getLac()).append(",");
						sb.append(t.getCellid()).append(",");
						sb.append(t.getNumSamples()).append(",");
						sb.append(0).append(",");
						sb.append(t.getTimestamp()).append(",");
						sb.append("\n");
					} else {
						// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
						
						sb.append("" + t.getMcc() + t.getMnc() + ";");
						sb.append(t.getCellid() + ";");
						sb.append("-1;"); //RNC unknown
						sb.append(t.getLat() + ";"); // LAT
						sb.append(t.getLon() + ";"); // LON
						sb.append("-1;"); // POS-RAT
						sb.append("OpenCellID community data;");
						sb.append("0;"); // Radio system
						sb.append("Unknown;"); // LABEL
						sb.append("-1;"); // AZI
						sb.append("-1;"); // height
						sb.append("-1"); // bandwidth
						sb.append("\n");
					}
					writer.append(sb.toString());

					count++;
				}

				writer.flush();
				writer.close();
			}
			
			dbIterator.close();
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}

		return count;
	}
	
}
