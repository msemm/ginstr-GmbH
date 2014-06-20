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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.ginstr.android.service.opencellid.library.db.CellsDatabase;
import com.ginstr.android.service.opencellid.library.db.OpenCellIdLibContext;
/**
 * <p>
 * This class holds a few static functions for importing data into
 * OpenCellId database. </p>
 * <p>
 * Records can be imported from CSV or CLFv3 files into 'cells',
 * 'measurements', 'measured_cells' and 'networks' tables.
 * </p>
 * 
 * @author Danijel
 * @author Dinko Ivkovic
 *
 */
public class Importer {
	private static final String TAG = Importer.class.getSimpleName();
	
	private static final String CSV_SEPARATOR = "[;,\\t]";
	
	private static boolean working;
	
	private Importer() {}
	
	/**
	 * <p>Import measurements from a CSV or CLFv3 file.</p>
	 * <p>CSV file has to have structure and column names:<br/>
	 * <code>timestamp,cellid,mnc,mcc,lac,reception,lat,lon,speed,heading,uploaded</code>
	 * </p>
	 * 
	 * <p>
	 * CLFv3 file has to have columns:<br/><br/>
	 * <code>MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW</code><br/><br/>
	 * However, those column names must not be specified in the file. Instead,
	 * file should hold the following header:<br/>
	 * <code>//cell list exchange format v3.0//\n</br>
	 * </p>
	 * <p>Note: this function will run as a transaction. If one exception is thrown,
	 * the whole transaction is aborted. </p>
	 * 
	 * @param libContext valid OpenCellIdLibContext object
	 * @param isCsv if true, import from a CSV file. Otherwise from CLFv3.
	 * @param filename existing file. Specify full path.
	 * @return number of imported records
	 * @throws IllegalArgumentException if libContext is null or filename empty, null or non-existing
	 */
	public static int importMeasurements(OpenCellIdLibContext libContext, boolean isCsv, String filename) throws IllegalArgumentException {
		int count = 0;
		
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename null or empty");
		
		File file = new File(filename);
		if (!file.exists())
			throw new IllegalArgumentException("file doesn't exist with name: " + filename);
		
		try {
			// open the file for reading
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

			try {
				// skip title row in CSV or CLFv3
				// "timestamp,cellid,mnc,mcc,lac,reception,lat,lon,speed,heading,uploaded\n
				// or
				// //cell list exchange format v3.0//\n
				reader.readLine();
				SQLiteDatabase db = libContext.getMeasurementsDatabase().getWritableDatabase();
				SQLiteStatement stmt = libContext.getMeasurementsDatabase().getMeasurementImportStatement();
				
				db.beginTransaction();
				try {
					String line = null;
					while ((line = reader.readLine()) != null) {
						if (line.length() > 0) {
							
							String [] parts = line.split(CSV_SEPARATOR);
							
							// check the file data format
							if (isCsv) {
								stmt.bindLong(1, Long.parseLong(parts[0]));
								stmt.bindLong(2, Long.parseLong(parts[1]));
								stmt.bindLong(3, Long.parseLong(parts[2]));
								stmt.bindLong(4, Long.parseLong(parts[3]));
								stmt.bindLong(5, Long.parseLong(parts[4]));
								stmt.bindLong(6, Long.parseLong(parts[5]));
								stmt.bindDouble(7, Double.parseDouble(parts[6]));
								stmt.bindDouble(8, Double.parseDouble(parts[7]));
								stmt.bindDouble(9, Double.parseDouble(parts[8]));
								stmt.bindDouble(10, Double.parseDouble(parts[9]));
								stmt.bindLong(11, Long.parseLong(parts[10]));
							} else {
								// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
								int mcc = Integer.parseInt(parts[0].substring(0, 2));
								int mnc = Integer.parseInt(parts[0].substring(3));
								int cellId = Integer.parseInt(parts[1]);
								int lac = Integer.parseInt(parts[2]);
								double lat = Double.parseDouble(parts[4]);
								double lon = Double.parseDouble(parts[5]);
								
								stmt.bindLong(1, System.currentTimeMillis());
								stmt.bindLong(2, cellId);
								stmt.bindLong(3, mnc);
								stmt.bindLong(4, mcc);
								stmt.bindLong(5, lac);
								stmt.bindLong(6, -1);
								stmt.bindDouble(7, lat);
								stmt.bindDouble(8, lon);
								stmt.bindDouble(9, 0.0);
								stmt.bindDouble(10, 0.0);
								stmt.bindLong(11, 0);
							}
							
							long rowId = stmt.executeInsert();
							if (rowId >= 0)
								count++;
							
						}
					}
					db.setTransactionSuccessful();
				} catch (Exception e) {
					count = 0; // no rows inserted, transaction aborted
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				} finally {
					db.endTransaction();
					stmt.close();
				}
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				}
			}	
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}	    
	
		return count;
	}
	
	/**
	 * <p>Import measured cells from a CSV or CLFv3 file.</p>
	 * <p>CSV file has to have structure and a header with column names:<br/>
	 * <code>timestamp,mcc,mnc,cellid,lac,lat,lon,nsamples,uploaded,newcell\n</code>
	 * </p>
	 * 
	 * <p>
	 * CLFv3 file has to have columns:<br/><br/>
	 * <code>MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW</code><br/><br/>
	 * However, those column names must not be specified in the file. Instead,
	 * file should hold the following header:<br/>
	 * <code>//cell list exchange format v3.0//\n</br>
	 * </p>
	 * <p>Note: this function will run as a transaction. If one exception is thrown,
	 * the whole transaction is aborted. </p>
	 * 
	 * @param libContext valid OpenCellIdLibContext object
	 * @param isCsv if true, import from a CSV file. Otherwise from CLFv3.
	 * @param filename existing file. Specify full path.
	 * @return number of imported records
	 * @throws IllegalArgumentException if libContext is null or filename empty, null or non-existing
	 */
	public static int importMeasuredCells(OpenCellIdLibContext libContext, boolean isCsv, String filename) throws IllegalArgumentException {
		int count = 0;
		
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename null or empty");
		
		File file = new File(filename);
		if (!file.exists())
			throw new IllegalArgumentException("file doesn't exist with name: " + filename);
		
		try {
			// open the file for reading
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

			try {
				// skip title row in CSV or CLFv3
				// "timestamp,mcc,mnc,cellid,lac,lat,lon,nsamples,uploaded,newcell\n"
				// or
				// //cell list exchange format v3.0//\n
				reader.readLine();
				SQLiteDatabase db = libContext.getMeasurementsDatabase().getWritableDatabase();
				SQLiteStatement stmt = libContext.getMeasurementsDatabase().getMeasuredCellImportStatement();
				
				db.beginTransaction();
				try {
					String line = null;
					while ((line = reader.readLine()) != null) {
						if (line.length() > 0) {
							
							String [] parts = line.split(CSV_SEPARATOR);
							
							// check the file data format
							if (isCsv) {
								stmt.bindLong(1, Long.parseLong(parts[0]));
								stmt.bindLong(2, Long.parseLong(parts[1]));
								stmt.bindLong(3, Long.parseLong(parts[2]));
								stmt.bindLong(4, Long.parseLong(parts[3]));
								stmt.bindLong(5, Long.parseLong(parts[4]));
								stmt.bindDouble(6, Double.parseDouble(parts[5]));
								stmt.bindDouble(7, Double.parseDouble(parts[6]));
								stmt.bindDouble(8, Double.parseDouble(parts[7]));
								stmt.bindDouble(9, Double.parseDouble(parts[8]));
								stmt.bindLong(10, Long.parseLong(parts[9]));
							} else {
								// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
								int mcc = Integer.parseInt(parts[0].substring(0, 2));
								int mnc = Integer.parseInt(parts[0].substring(3));
								int cellId = Integer.parseInt(parts[1]);
								int lac = Integer.parseInt(parts[2]);
								double lat = Double.parseDouble(parts[4]);
								double lon = Double.parseDouble(parts[5]);
								
								stmt.bindLong(1, System.currentTimeMillis());
								stmt.bindLong(2, cellId);
								stmt.bindLong(3, mnc);
								stmt.bindLong(4, mcc);
								stmt.bindLong(5, lac);
								stmt.bindDouble(6, lat);
								stmt.bindDouble(7, lon);
								stmt.bindDouble(8, 0.0);
								stmt.bindDouble(9, 0.0);
								stmt.bindLong(10, 0);
							}
							
							long rowId = stmt.executeInsert();
							if (rowId >= 0)
								count++;
							
						}
					}
					db.setTransactionSuccessful();
				} catch (Exception e) {
					count = 0; // no rows inserted, transaction aborted
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				} finally {
					db.endTransaction();
					stmt.close();
				}
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				}
			}	
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}	    
	
		return count;
	}
	
	/**
	 * <p>Import networks from a CSV file.</p>
	 * <p>CSV file has to have structure and a header with column names:<br/>
	 * <code>timestamp,mnc,mcc,type,name,uploaded\n</code>
	 * </p>
	 * 
	 * <p>Note: this function will run as a transaction. If one exception is thrown,
	 * the whole transaction is aborted. </p>
	 * 
	 * @param libContext valid OpenCellIdLibContext object
	 * @param filename existing file. Specify full path.
	 * @return number of imported records
	 * @throws IllegalArgumentException if libContext is null or filename empty, null or non-existing
	 */
	public static int importNetworks(OpenCellIdLibContext libContext, String filename) throws IllegalArgumentException {
		int count = 0;
		
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename null or empty");
		
		File file = new File(filename);
		if (!file.exists())
			throw new IllegalArgumentException("file doesn't exist with name: " + filename);
		
		try {
			// open the file for reading
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

			try {
				// skip title row in CSV 
				// "timestamp,mnc,mcc,type,name,uploaded\n"
				
				reader.readLine();
				SQLiteDatabase db = libContext.getMeasurementsDatabase().getWritableDatabase();
				SQLiteStatement stmt = libContext.getMeasurementsDatabase().getNetworksImportStatement();
				
				db.beginTransaction();
				try {
					String line = null;
					while ((line = reader.readLine()) != null) {
						if (line.length() > 0) {
							
							String [] parts = line.split(CSV_SEPARATOR);
							
							stmt.bindLong(1, Long.parseLong(parts[0]));
							stmt.bindLong(2, Long.parseLong(parts[1]));
							stmt.bindLong(3, Long.parseLong(parts[2]));
							stmt.bindString(4, parts[3]);
							stmt.bindString(5, parts[4]);
							stmt.bindLong(6, Long.parseLong(parts[5]));
							
							
							long rowId = stmt.executeInsert();
							if (rowId >= 0)
								count++;
							
						}
					}
					db.setTransactionSuccessful();
				} catch (Exception e) {
					count = 0; // no rows inserted, transaction aborted
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				} finally {
					db.endTransaction();
					stmt.close();
				}
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				}
			}	
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}	    
		
		return count;
	}
	
	/**
	 * <p>Import cells from a CSV or CLFv3 file.</p>
	 * <p>CSV file has to have structure and a header with column names:<br/>
	 * <code>latitude,longitude,mcc,mnc,lac,cell_id,samples,created,updated\n</code>
	 * </p>
	 * 
	 * <p>
	 * CLFv3 file has to have columns:<br/><br/>
	 * <code>MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW</code><br/><br/>
	 * However, those column names must not be specified in the file. Instead,
	 * file should hold the following header:<br/>
	 * <code>//cell list exchange format v3.0//\n</br>
	 * </p>
	 * <p>Note: this function will run as a transaction. If one exception is thrown,
	 * the whole transaction is aborted. </p>
	 * 
	 * @param libContext valid OpenCellIdLibContext object
	 * @param isCsv if true, import from a CSV file. Otherwise from CLFv3.
	 * @param filename existing file. Specify full path.
	 * @return number of imported records
	 * @throws IllegalArgumentException if libContext is null or filename empty, null or non-existing
	 */
	public static int importCells(OpenCellIdLibContext libContext, boolean isCsv, String filename) throws IllegalArgumentException {
		int count = 0;
		
		if (libContext == null)
			throw new IllegalArgumentException("libContext is null");
		
		if (filename == null || filename.length() == 0)
			throw new IllegalArgumentException("filename null or empty");
		
		File file = new File(filename);
		if (!file.exists())
			throw new IllegalArgumentException("file doesn't exist with name: " + filename);
		
		try {
			// open the file for reading
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

			try {
				// skip title row in CSV or CLFv3
				// latitude,longitude,mcc,mnc,lac,cell_id,samples,created,updated
				// or
				// //cell list exchange format v3.0//\n
				reader.readLine();
				SQLiteDatabase db = libContext.getCellsDatabase().getWritableDatabase();
				SQLiteStatement stmt = libContext.getCellsDatabase().getTowerBatchInsertOrReplaceStatement();
				
				Map<Integer, Set<Integer>> mccMncMap = new HashMap<Integer, Set<Integer>>(); 
				
				working = true;
				
				db.beginTransaction();
				try {
					String line = null;
					while ((line = reader.readLine()) != null) {
						if (line.length() > 0) {
							
							String [] parts = line.split(CSV_SEPARATOR);
							
							double lat = 0.0;
							double lon = 0.0;
							int mcc, mnc, lac, cellid, samples;
							long timestamp = 0;
							
							// check the file data format
							if (isCsv) {
								lat = Double.parseDouble(parts[0]);
								lon = Double.parseDouble(parts[1]);
								mcc = Integer.parseInt(parts[2]);
								mnc = Integer.parseInt(parts[3]);
								lac = Integer.parseInt(parts[4]);
								cellid = Integer.parseInt(parts[5]);
								samples = Integer.parseInt(parts[6]);
								timestamp = Long.parseLong(parts[8]);
							} else {
								// MCCMNC;CID;LAC;RNC;LAT;LON;POS-RAT;DESC;SYS;LABEL;AZI;HEIGHT;BW
								mcc = Integer.parseInt(parts[0].substring(0, 2));
								mnc = Integer.parseInt(parts[0].substring(3));
								cellid = Integer.parseInt(parts[1]);
								lac = Integer.parseInt(parts[2]);
								lat = Double.parseDouble(parts[4]);
								lon = Double.parseDouble(parts[5]);
								samples = 0;
								timestamp = System.currentTimeMillis();
							}
							
							Cell cell = new Cell(timestamp, cellid, mcc, mnc, lac, 
												 null, lat, lon, samples);
							long rowId = libContext.getCellsDatabase().insertBatchTower(stmt, cell);
							if (rowId >= 0)
								count++;
							
							Set<Integer> mncs = mccMncMap.get(cell.getMcc());
							if (mncs == null) {
								mncs = new HashSet<Integer>();
								mccMncMap.put(cell.getMcc(), mncs);
							}
							mncs.add(cell.getMnc());	
						}
					}
					
					libContext.getCellsDatabase().updateCellsStatsTable(mccMncMap, true, new CellsDatabase.DBListener() {

						@Override
						public void progress(long progress, long total) {
							// nothing
						}

						@Override
						public boolean isCancelled() {
							return working;
						}
					});
					
					db.setTransactionSuccessful();

				} catch (Exception e) {
					count = 0; // no rows inserted, transaction aborted
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				} finally {
					db.endTransaction();
					stmt.close();	
					working = false;
				}
				
				
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
				}
			}	
		} catch (Exception e) {
			libContext.getLogService().writeErrorLog(TAG, e.getMessage(), e);
		}	    
		
		return count;
	}
	
}