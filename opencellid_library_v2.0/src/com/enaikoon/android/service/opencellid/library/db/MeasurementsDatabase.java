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
package com.enaikoon.android.service.opencellid.library.db;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.enaikoon.android.service.opencellid.library.data.Cell;
import com.enaikoon.android.service.opencellid.library.data.MeasuredCell;
import com.enaikoon.android.service.opencellid.library.data.Measurement;
import com.enaikoon.android.service.opencellid.library.data.Network;
import com.enaikoon.android.service.opencellid.library.db.MeasuredCellsTable.MeasuredCellsDBIterator;
import com.enaikoon.android.service.opencellid.library.db.MeasurementsTable.MeasurementsDBIterator;
import com.enaikoon.android.service.opencellid.library.db.NetworksTable.NetworkDBIterator;

/** 
 * Used to insert cell measurements.
 * 
 * @author Marcus Wolschon (Marcus@Wolschon.biz)
 * @author Roberto
 * @author Danijel
 */
public class MeasurementsDatabase extends SQLiteOpenHelper {
	private static final String TAG = MeasurementsDatabase.class.getSimpleName();

	public static interface CellDBListener {
		public void newMeasurement(final Measurement aMeasurement);
	}

	private final OpenCellIdLibContext mLibContext;

	private static final int DATABASE_VERSION = 14;

	private Measurement myLastMeasurement = null;

	private int myTotalMeasurementCountCache = -1;
	private int myTodayMeasurementCountCache = -1;
	private long myTodayMeasurementCountCache_ts = -1;
	private int myTotalMeasurementUploadedCountCache = -1;

	private void logDebug(String message) {
		mLibContext.getLogService().writeLog(Log.DEBUG, TAG, message);
	}

	private void writeExceptionToLog(Exception ex) {
		mLibContext.getLogService().writeErrorLog(TAG, ex.getMessage(), ex);
	}


	public MeasurementsDatabase(final Context context, final OpenCellIdLibContext libContext) {
		super(context, libContext.getMeasurementsDataBaseFullPath(), null, DATABASE_VERSION);

		mLibContext = libContext;
	}

	/*
	 * SQLiteOpenHelper lifecycle methods
	 */

	 @Override
	 public void onCreate(final SQLiteDatabase db) {
		 logDebug("onCreate()");

		 MeasuredCellsTable.createTables(db);
		 NetworksTable.createTables(db);
		 MeasurementsTable.createTables(db);
	 }

	 @Override
	 public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		 logDebug("onUpgrade(): Upgrading db from version " + oldVersion + " to " + newVersion);

		 MeasuredCellsTable.upgradeTables(db, oldVersion, newVersion);
		 NetworksTable.upgradeTables(db, oldVersion, newVersion);
		 MeasurementsTable.upgradeTables(db, oldVersion, newVersion);

		 // recreates the MeasuredCells table data
		 if (oldVersion == 9) {
			 rebuildMeasuredCells(db);
		 }
	 }

	 /*
	  * Measurements table listeners  
	  */

	 private Set<CellDBListener> mListeners = new HashSet<CellDBListener>();

	 public void addListener(final CellDBListener aListener) {
		 logDebug("addListener() : " + aListener.getClass().getSimpleName());

		 this.mListeners.add(aListener);
	 }

	 public void removeListener(final CellDBListener aListener) {
		 logDebug("removeListener() : " + aListener.getClass().getSimpleName());

		 this.mListeners.remove(aListener);
	 }

	 public void clearListeners() {
		 logDebug("clearListeners()");

		 this.mListeners.clear();
	 }

	 private void notifyListeners(Measurement measurement) {
		 logDebug("notifyListeners()");

		 for (CellDBListener trafficDBListener : mListeners) {
			 try {
				 logDebug("notifyListeners() notifying " + trafficDBListener.getClass().getSimpleName());

				 trafficDBListener.newMeasurement(measurement);

			 } catch (Exception e) {
				 writeExceptionToLog(e);
			 }
		 }
	 }

	 public void clearAllMeasurements() {
		 SQLiteDatabase db = getWritableDatabase();
		 db.delete(MeasurementsTable.MEASUREMENTS_TABLENAME, null, null);

		 myTotalMeasurementCountCache = 0;
		 myTotalMeasurementUploadedCountCache = 0;
		 myTodayMeasurementCountCache = -1;
		 myTodayMeasurementCountCache_ts = -1;
	 }

	 public int getAllMeasurementsCount() {
		 if (myTotalMeasurementCountCache >= 0) {
			 logDebug("getAllMeasurementsCount(): using cache =" + myTotalMeasurementCountCache);

			 // redundant sanity check
			 if (myTodayMeasurementCountCache > myTotalMeasurementCountCache) {
				 myTotalMeasurementCountCache = myTodayMeasurementCountCache;
			 }
			 return myTotalMeasurementCountCache;
		 }
		 logDebug("getAllMeasurementsCount(): NOT USING CACHE");

		 SQLiteDatabase db = getReadableDatabase();

		 myTotalMeasurementCountCache = (int)DatabaseUtils.queryNumEntries(db, MeasurementsTable.MEASUREMENTS_TABLENAME);

		 return myTotalMeasurementCountCache;
	 }

	 public int getAllMeasurementsOfTodayCount() {
		 long today = getStartOfToday();

		 if (myTodayMeasurementCountCache_ts == today) {
			 logDebug("getAllMeasurementsOfTodayCount(): using cache =" + myTodayMeasurementCountCache);
			 return myTodayMeasurementCountCache;
		 }
		 logDebug("getAllMeasurementsAfterCount(): NOT USING CACHE");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " > ?");

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, new String[] { "" + today }, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 myTodayMeasurementCountCache = c.getInt(countcol);
				 myTodayMeasurementCountCache_ts = today;

				 return myTodayMeasurementCountCache;
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }

	 public int getAllMeasurementsUploadedCount() {
		 if (myTotalMeasurementUploadedCountCache > -1) {
			 logDebug("getAllMeasurementsUploadedCount(): using cache =" + myTotalMeasurementUploadedCountCache);
			 return myTotalMeasurementUploadedCountCache;
		 }
		 logDebug("getAllMeasurementsUploadedCount(): NOT USING CACHE");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + " > 0");

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, null, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 myTotalMeasurementUploadedCountCache = c.getInt(countcol);
				 return myTotalMeasurementUploadedCountCache;
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }
	 
	 /**
	  * Erases all the uploaded measurements older than 7 days.
	  * @return number of deleted records
	  */
	 public int eraseUploadedMeasurementsOlderThan7Days() {
		 
		 logDebug("eraseUploadedMeasurementsOlderThan7Days()");
		 
		 SQLiteDatabase db = getWritableDatabase();
		 
		 int num = 0;
		 try {
			 long aWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
			 
			 num = db.delete(MeasurementsTable.MEASUREMENTS_TABLENAME, 
								 "" + MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + " > 0 AND " +
								 MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " < " + aWeekAgo, null);
		 } catch (Exception e) {
			 writeExceptionToLog(e);
		 } 
		 
		 db.close();
		 return num;
	 }

	 /**
	  * Insert a new register in the Measurement table.
	  * Uses transactions to sinchronize the networks table with any new results from the measurement
	  * 
	  * @param pMeasurement the meassurement to add
	  * @param storeMeasurement if false, only the listeners are informed
	  * @param storeNetwork if true, network will also be stored
	  */
	 public void addMeasurement(final Measurement pMeasurement, boolean storeMeasurement, boolean storeNetwork) {
		 logDebug("addMeasurement()");

		 SQLiteDatabase db = getWritableDatabase();
		 ContentValues values = new ContentValues();

		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP, pMeasurement.getTimestamp());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_MNC, pMeasurement.getMnc());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_MCC, pMeasurement.getMcc());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_LAC, pMeasurement.getLac());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_CELLID, pMeasurement.getCellid());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_LAT, pMeasurement.getLat());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_LON, pMeasurement.getLon());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_SPEED, pMeasurement.getSpeed());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_HEADING, pMeasurement.getBearing());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_RECEPTION, pMeasurement.getGsmSignalStrength());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED, pMeasurement.isUploaded() ? 1 : 0);
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_NETWORK_TYPE, pMeasurement.getNetworkType());
		 values.put(MeasurementsTable.MEASUREMENTS_COLUMN_ACCURACY, pMeasurement.getAccuracy());

		 if (!storeMeasurement) {
			 logDebug("addMeasurement(): collection disabled at the moment");
		 } else {
			 db.beginTransaction();
			 try {

				 if (db.insert/* WithOnConflict */(MeasurementsTable.MEASUREMENTS_TABLENAME, MeasurementsTable.MEASUREMENTS_COLUMN_CELLID, values) == -1) {
					 logDebug("addMeasurement(): Error inserting cell");
				 } else {
					 if (myTotalMeasurementCountCache > -1) {
						 myTotalMeasurementCountCache++;
					 }
					 if (myTodayMeasurementCountCache > -1) {
						 myTodayMeasurementCountCache++;
					 }

					 MeasuredCell eCell = getMeasuredCell(pMeasurement.getMcc(), pMeasurement.getMnc(), pMeasurement.getCellid(), pMeasurement.getLac());

					 if (eCell != null) {
						 eCell.addFactor(pMeasurement);
						 updateMeasuredCell(eCell);
					 } else {
						 Cell cell = mLibContext.getCellsDatabase().getTowerCell( pMeasurement.getCellid(), pMeasurement.getLac(), pMeasurement.getMcc(), pMeasurement.getMnc());

						 if (cell != null) {
							 eCell = new MeasuredCell(cell);
							 eCell.addFactor(pMeasurement);
						 } else {
							 eCell = new MeasuredCell(pMeasurement);
						 }
						 addMeasuredCell(eCell);
					 }
					 
					 if (storeNetwork)
						 addNetwork(pMeasurement.getNetwork());
				 }

				 db.setTransactionSuccessful();
			 } finally {
				 db.endTransaction();
			 }
		 }

		 this.myLastMeasurement = pMeasurement;
		 notifyListeners(pMeasurement);
	 }

	 
	 public SQLiteStatement getMeasurementImportStatement() {
		 SQLiteDatabase db = getWritableDatabase();
		 // "timestamp,cellid,mnc,mcc,lac,reception,lat,lon,speed,heading,uploaded\n
			SQLiteStatement statement = db
					.compileStatement("INSERT OR REPLACE INTO "
							+ MeasurementsTable.MEASUREMENTS_TABLENAME + " ("
							+ MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_CELLID + ", "	
							+ MeasurementsTable.MEASUREMENTS_COLUMN_MNC + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_MCC + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_LAC + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_RECEPTION + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_LAT + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_LON + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_SPEED + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_HEADING + ", " 
							+ MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED  + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_NETWORK_TYPE  + ", "
							+ MeasurementsTable.MEASUREMENTS_COLUMN_ACCURACY
							+ ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?);");

			return statement;
	 }
	 
	 public SQLiteStatement getMeasuredCellImportStatement() {
		 // "timestamp,mcc,mnc,cellid,lac,lat,lon,nsamples,uploaded,newcell\n"
		 SQLiteDatabase db = getWritableDatabase();
		 SQLiteStatement statement = db
					.compileStatement("INSERT OR REPLACE INTO "
							+ MeasuredCellsTable.MEASUREDCELLS_TABLENAME + " ("
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_MCC + ", "	
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_MNC + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_CELLID + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAC + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_NSAMPLES + ", "
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED + ", " 
							+ MeasuredCellsTable.MEASUREDCELLS_COLUMN_NEWCELL 
							+ ") VALUES(?,?,?,?,?,?,?,?,?,?);");

			return statement;
	 }
	 
	 public SQLiteStatement getNetworksImportStatement() {
		 // "timestamp,mnc,mcc,type,name,uploaded\n"
		 SQLiteDatabase db = getWritableDatabase();
		 SQLiteStatement statement = db
				 .compileStatement("INSERT OR REPLACE INTO "
						 + NetworksTable.NETWORKS_TABLENAME + " ("
						 + NetworksTable.NETWORKS_COLUMN_TIMESTAMP + ", "
						 + NetworksTable.NETWORKS_COLUMN_MNC + ", "	
						 + NetworksTable.NETWORKS_COLUMN_MCC + ", "
						 + NetworksTable.NETWORKS_COLUMN_TYPE + ", "
						 + NetworksTable.NETWORKS_COLUMN_NAME + ", "
						 + NetworksTable.NETWORKS_COLUMN_UPLOADED
						 + ") VALUES(?,?,?,?,?,?);");

		 return statement;
	 }
	 
	 /**
	  * @return may be null
	  */
	 public Measurement getLastMeasurement() {
		 logDebug("getLastMeasurement()");
		 if (myLastMeasurement == null) {
			 try {
				 SQLiteDatabase db = getReadableDatabase();
				 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
				 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

				 Cursor c = query.query(db, null, null, null, null, null, MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " DESC ", "1");

				 try {
					 MeasurementsTable.MeasurementsDBIterator dbIterator = new MeasurementsTable.MeasurementsDBIterator(c);
					 if (dbIterator.hasNext()) {
						 myLastMeasurement = dbIterator.next();
					 }
				 } finally {
					 c.close();
				 }
			 } catch (Exception e) {
				 writeExceptionToLog(e);
			 }
		 }
		 return myLastMeasurement;
	 }

	 public void setAllMeasurementsUploaded() {
		 logDebug("setMeasurementsAllUploaded()");

		 SQLiteDatabase db = getWritableDatabase();

		 db.beginTransaction();
		 try {

			 db.execSQL("UPDATE " + MeasurementsTable.MEASUREMENTS_TABLENAME + " SET " + MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + " = 1");

			 setAllMeasuredCellsUploaded();

			 db.setTransactionSuccessful();
		 } finally {
			 db.endTransaction();
		 }

		 myTotalMeasurementUploadedCountCache = myTotalMeasurementCountCache;
		 notifyListeners(myLastMeasurement);
	 }

	 /**
	  * All non uploaded to server measurements
	  * @return a DBIterator on all non uploaded measurements
	  */
	 public MeasurementsDBIterator getNonUploadedMeasurements() {
		 logDebug("getNonUploadedMeasurements()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + " < 1");

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasurementsDBIterator(c);
	 }
	 
	 /**
	  * Returns all measurements for the specific time frame.
	  * @param from time in milliseconds passed from 00:00:00 1-1-1970
	  * @param to time in milliseconds passed from 00:00:00 1-1-1970
	  * @return a DBIterator on all retrieved records
	  */
	 public MeasurementsDBIterator getAllMeasurementsForTimeRange(long from, long to) {
		 logDebug("getAllMeasurementsForTimeRange(" + from + "," + to + ")");
		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " >= " + from + " AND "
				 		  + MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " <= " + to);

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasurementsDBIterator(c);
	 }

	 private int getFirstMeasurement_last_mcc = -1;
	 private int getFirstMeasurement_last_mnc = -1;
	 private int getFirstMeasurement_last_cellid = -1;
	 private int getFirstMeasurement_last_lac = -1;
	 private long getFirstMeasurement_last_result = -1;

	 /**
	  * 
	  * @param cellid
	  * @param lac
	  * @param mcc
	  * @param mnc
	  * @param timestamp
	  * @return the first timestamp this cell was seen by this client
	  */
	 public long getFirstMeasurement(int cellid, int lac, int mcc, int mnc, long timestamp) {

		 // use cached data if same cell
		 if (getFirstMeasurement_last_mnc == mnc &&
				 getFirstMeasurement_last_mcc == mcc && 
				 getFirstMeasurement_last_cellid == cellid &&
				 getFirstMeasurement_last_lac == lac) {
			 getFirstMeasurement_last_result = Math.min(getFirstMeasurement_last_result, timestamp);

			 return getFirstMeasurement_last_result;
		 }
		 // writeToLog.d("getFirstMeasurement()",  "getFirstMeasurement(timestamp=" + timestamp + ")");
		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MNC + " = " + mnc + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MCC + " = " + mcc + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_CELLID + " = " + cellid + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_LAC + " = " + lac + " ");

		 Cursor c = query.query(db, null, null, null, null, null, MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " ASC", "1");

		 long retval = timestamp;
		 try {
			 if (c.moveToNext()) {
				 int timecol = c.getColumnIndex(MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP);
				 retval = Math.min(retval, c.getLong(timecol));
			 }
		 } finally {
			 c.close();
		 }

		 // cache in case the same cell is asked twice
		 getFirstMeasurement_last_mnc = mnc;
		 getFirstMeasurement_last_mcc = mcc;
		 getFirstMeasurement_last_cellid = cellid;
		 getFirstMeasurement_last_lac = lac;
		 getFirstMeasurement_last_result = retval;

		 return retval;
	 }

	 /**
	  * Return all Measurement in the database ordered from first stored to last.
	  * @return a DBIterator to traverse the results
	  */
	 public MeasurementsDBIterator getAllMeasurements() {
		 logDebug("getAllMeasurements()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 Cursor c = query.query(db, null, null, null, null, null, MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + " ASC ");

		 return new MeasurementsDBIterator(c);

	 }

	 /**
	  * Return all Measurement of a given cell
	  * @param cellid
	  * @param lac
	  * @param mcc
	  * @param mnc
	  * @return a DBIterator to transverse the results
	  */
	 public MeasurementsDBIterator getAllMeasurementsOfCell(int cellid, int lac, int mcc, int mnc, Boolean uploaded) {
		 logDebug("getAllMeasurementsOfCell(" + cellid + ", " + lac + ", " + mcc + ", " + mnc + ")");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MNC + " = " + mnc + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MCC + " = " + mcc + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_CELLID + " = " + cellid + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_LAC + " = " + lac);

		 if (uploaded != null) {
			 query.appendWhere(" AND " + MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + " = " + (uploaded? "1" : "0"));
		 }

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasurementsDBIterator(c);
	 }

	 /**
	  * Return the number of Measurements of a given mcc (country)
	  * @param mcc
	  * @return the count of the measurements in the table with the given mcc
	  */
	 public int getAllMeasurementsWithMccCount(int mcc) {
		 logDebug("getAllMeasurementsWithMccCount(" + mcc + ")");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MCC + " = " + mcc);

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, null, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 return c.getInt(countcol);
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }

	 /**
	  * Return the number of all Measurements of a given cell
	  * @param mcc
	  * @param mnc
	  * @return the count of the measurements in the table with the given mcc and mnc
	  */
	 public int getAllMeasurementsWithMccAndMncCount(int mcc, int mnc) {
		 logDebug("getAllMeasurementsWithMccAndMncCount(" + mcc + ", " + mnc + ")");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MNC + " = " + mnc + " AND ");
		 query.appendWhere(MeasurementsTable.MEASUREMENTS_COLUMN_MCC + " = " + mcc + " ");

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, null, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 return c.getInt(countcol);
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }

	 /*
	  * Network table methods
	  */

	 /**
	  * Inserts a new network record on the table
	  * @param n
	  */
	 public void addNetwork(Network n) {

		 logDebug("addNetwork(" + n.getMnc() + ", " + n.getMcc() + ", " + n.getName() + ")");

		 if (n.getName() != null && n.getName().length() > 0) {
			 SQLiteDatabase db = getWritableDatabase();
			 ContentValues values = new ContentValues();

			 values.put(NetworksTable.NETWORKS_COLUMN_TIMESTAMP, System.currentTimeMillis());
			 values.put(NetworksTable.NETWORKS_COLUMN_MNC, n.getMnc());
			 values.put(NetworksTable.NETWORKS_COLUMN_MCC, n.getMcc());
			 values.put(NetworksTable.NETWORKS_COLUMN_TYPE, n.getType());
			 values.put(NetworksTable.NETWORKS_COLUMN_NAME, n.getName());
			 values.put(NetworksTable.NETWORKS_COLUMN_UPLOADED, 0);

			 if (db.replaceOrThrow(NetworksTable.NETWORKS_TABLENAME, null, values) == -1) {
				 logDebug("addNetwork(): Error inserting network");
			 }
		 }
	 }

	 public NetworkDBIterator getAllNetworks() {
		 logDebug("getAllNetworks()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(NetworksTable.NETWORKS_TABLENAME);

		 Cursor c = query.query(db, null, null, null, null, null, NetworksTable.NETWORKS_COLUMN_TIMESTAMP + " ASC ");

		 return new NetworkDBIterator(c);
	 }

	 public NetworkDBIterator getNonUploadedNetworks() {
		 logDebug("getNonUploadedNetworks()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(NetworksTable.NETWORKS_TABLENAME);

		 query.appendWhere(NetworksTable.NETWORKS_COLUMN_UPLOADED + " < 1");

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new NetworkDBIterator(c);
	 }

	 public void setAllNetworksUploaded() {
		 logDebug("setAllNetworksUploaded()");

		 SQLiteDatabase db = getWritableDatabase();	
		 db.execSQL("UPDATE " + NetworksTable.NETWORKS_TABLENAME + " SET " + NetworksTable.NETWORKS_COLUMN_UPLOADED + " = 1");
	 }

	 /*
	  * MeasuredCells methods
	  */


	 public MeasuredCell getMeasuredCell(int pMcc, int pMnc, int pCellId, int pLac) {
		 logDebug("getMeasuredCell()");
		 long start = System.currentTimeMillis();

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MCC + " = " + pMcc + " AND ");
		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MNC + " = " + pMnc + " AND ");
		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_CELLID + " = " + pCellId + " AND ");
		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAC + " = " + pLac);

		 Cursor c = query.query(db, null, null, null, null, null, null, "1");

		 try {
			 MeasuredCellsDBIterator dbIterator = new MeasuredCellsDBIterator(c);

			 logDebug("getMeasuredCell() q=" + query.toString() + " RESULT C=" + c.getCount());

			 if (dbIterator.getCount() == 1) {
				 return dbIterator.next();
			 }

			 return null;
		 } finally {
			 c.close();
			 Log.w(TAG, "getMeasuredCell() run in " + (System.currentTimeMillis() - start) + " ms");
		 }
	 }

	 public void rebuildMeasuredCells(SQLiteDatabase db) {
		 logDebug("rebuildMeasuredCells()");

		 if (db == null) {
			 db = getWritableDatabase();
		 }

		 db.delete(MeasuredCellsTable.MEASUREDCELLS_TABLENAME, null, null);

		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		 query.setTables(MeasurementsTable.MEASUREMENTS_TABLENAME);

		 // public Cursor query (SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder)
		 Cursor c = query.query(
				 // SQLiteDatabase db
				 db, 
				 // String[] projectionIn
				 new String[] {
						 MeasurementsTable.MEASUREMENTS_COLUMN_MCC,
						 MeasurementsTable.MEASUREMENTS_COLUMN_MNC,
						 MeasurementsTable.MEASUREMENTS_COLUMN_CELLID,
						 MeasurementsTable.MEASUREMENTS_COLUMN_LAC,

						 "MIN(" + MeasurementsTable.MEASUREMENTS_COLUMN_UPLOADED + ") AS " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED,
						 "AVG(" + MeasurementsTable.MEASUREMENTS_COLUMN_LAT + ") AS " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT,
						 "AVG(" + MeasurementsTable.MEASUREMENTS_COLUMN_LON + ") AS " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON,
						 "MAX(" + MeasurementsTable.MEASUREMENTS_COLUMN_TIMESTAMP + ") AS " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP,
						 "COUNT(*) AS " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_NSAMPLES
				 }, 
				 // String selection
				 null,
				 // String[] selectionArgs
				 null,
				 // String groupBy
				 MeasurementsTable.MEASUREMENTS_COLUMN_MCC + ", "
				 + MeasurementsTable.MEASUREMENTS_COLUMN_MNC + ", "
				 + MeasurementsTable.MEASUREMENTS_COLUMN_CELLID + ", "
				 + MeasurementsTable.MEASUREMENTS_COLUMN_LAC,
				 // String having
				 null,
				 // String sortOrder        	
				 null);
		 try {
			 int mccCol = c.getColumnIndex(MeasurementsTable.MEASUREMENTS_COLUMN_MCC);
			 int mncCol = c.getColumnIndex(MeasurementsTable.MEASUREMENTS_COLUMN_MNC);
			 int cidCol = c.getColumnIndex(MeasurementsTable.MEASUREMENTS_COLUMN_CELLID);
			 int lacCol = c.getColumnIndex(MeasurementsTable.MEASUREMENTS_COLUMN_LAC);

			 int latCol = c.getColumnIndex(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT);
			 int lonCol = c.getColumnIndex(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON);
			 int timeCol = c.getColumnIndex(MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP);
			 int uploadedCol = c.getColumnIndex(MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED);	    
			 int numSamplesCol = c.getColumnIndex(MeasuredCellsTable.MEASUREDCELLS_COLUMN_NSAMPLES);

			 while (c.moveToNext()) {
				 boolean uploaded = c.getInt(uploadedCol) > 0;

				 MeasuredCell eCell = new MeasuredCell(
						 c.getLong(timeCol),
						 c.getInt(cidCol),
						 c.getInt(mccCol),
						 c.getInt(mncCol),
						 c.getInt(lacCol),
						 null,
						 c.getDouble(latCol),
						 c.getDouble(lonCol),
						 c.getInt(numSamplesCol),
						 uploaded,
						 !uploaded);

				 addMeasuredCell(db, eCell);
			 }
		 } finally {
			 c.close();
		 }
	 }

	 public int addMeasuredCell(final MeasuredCell pCell) {
		 SQLiteDatabase db = getWritableDatabase();
		 return addMeasuredCell(db, pCell);
	 }

	 private int addMeasuredCell(final SQLiteDatabase db, final MeasuredCell pCell) {
		 logDebug("addMeasuredCell()");

		 ContentValues values = new ContentValues();

		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP, pCell.getTimestamp());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MNC, pCell.getMnc());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MCC, pCell.getMcc());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAC, pCell.getLac());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_CELLID, pCell.getCellid());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT, pCell.getLat());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON, pCell.getLon());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED, pCell.isUploaded() ? 1 : 0);
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_NEWCELL, pCell.isNewCell() ? 1 : 0);

		 int res = (int)db.insert/* WithOnConflict */(MeasuredCellsTable.MEASUREDCELLS_TABLENAME, MeasuredCellsTable.MEASUREDCELLS_COLUMN_CELLID, values);

		 if (res == -1) {
			 logDebug("addMeasuredCell(): Error inserting measured cell");
		 }

		 return res;
	 }

	 public int updateMeasuredCell(MeasuredCell pCell) {
		 logDebug("updateMeasuredCell()");

		 SQLiteDatabase db = getWritableDatabase();
		 ContentValues values = new ContentValues();

		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP, pCell.getTimestamp());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT, pCell.getLat());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON, pCell.getLon());
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED, pCell.isUploaded() ? 1 : 0);
		 values.put(MeasuredCellsTable.MEASUREDCELLS_COLUMN_NEWCELL, pCell.isNewCell() ? 1 : 0);

		 StringBuilder where = new StringBuilder();
		 where.append(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MCC).append(" = ").append(pCell.getMcc()).append(" AND ");
		 where.append(MeasuredCellsTable.MEASUREDCELLS_COLUMN_MNC).append(" = ").append(pCell.getMnc()).append(" AND ");
		 where.append(MeasuredCellsTable.MEASUREDCELLS_COLUMN_CELLID).append(" = ").append(pCell.getCellid()).append(" AND ");
		 where.append(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAC).append(" = ").append(pCell.getLac());

		 int res = (int)db.update/* WithOnConflict */(MeasuredCellsTable.MEASUREDCELLS_TABLENAME, values, where.toString(), null);

		 if (res == -1) {
			 logDebug("updateMeasuredCell(): Error updating measured cell");
		 }

		 return res;
	 }

	 public void clearAllMeasuredCells() {
		 SQLiteDatabase db = getWritableDatabase();
		 db.delete(MeasuredCellsTable.MEASUREDCELLS_TABLENAME, null, null);
	 }

	 /**
	  * Return all MeasuredCells in the database ordered from first stored to last.
	  * @return a DBIterator to traverse the results
	  */
	 public MeasuredCellsDBIterator getAllMeasuredCells() {
		 logDebug("getAllMeasuredCells()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasuredCellsDBIterator(c);

	 }
	 
	 /**
	  * Returned all measured cells for a specified time interval.
	  * @param from time in milliseconds passed from 00:00:00 1-1-1970
	  * @param to time in milliseconds passed from 00:00:00 1-1-1970
	  * @return a DBIterator for the retrieved set
	  */
	 public MeasuredCellsDBIterator getAllMeasuredCellsForTimeRange(long from, long to) {
		 logDebug("getAllMeasuredCellsForTimeFrame(" + from + "," + to + ")");
		 
		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);
		 
		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP + " >= " + from +
				 " AND " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP + " <= " + to);
		 
		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasuredCellsDBIterator(c);
	 }

	 public int getAllMeasuredCellsCount() {
		 SQLiteDatabase db = getReadableDatabase();
		 return (int)DatabaseUtils.queryNumEntries(db, MeasuredCellsTable.MEASUREDCELLS_TABLENAME);
	 }

	 /**
	  * Return all MeasuredCells in the database with its newCell column with the given value, in the selected geographical scope.
	  * @return a DBIterator to traverse the results
	  */
	 public MeasuredCellsDBIterator getMeasuredCellsInRegion(boolean newCell, double north, double east, double south, double west) {
		 logDebug("getMeasuredCellsInRegion(" + newCell + ", " + north + ", " + east + ", " + south + ", " + west + ")");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT + " BETWEEN " + south + " AND " + north + " AND " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON + " BETWEEN " + west + " AND " + east);
		 query.appendWhere("AND " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_NEWCELL + "=" + (newCell? "1" : "0"));

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasuredCellsDBIterator(c);
	 }

	 public int getAllMeasuredCellsOfTodayCount() {
		 logDebug("getAllMeasuredCellsOfTodayCount()");

		 long today = getStartOfToday();

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_TIMESTAMP + " > ?");

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, new String[] { "" + today }, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 int res = c.getInt(countcol);

				 return res;
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }

	 public int getAllMeasuredCellsNonUploadedCount() {
		 logDebug("getAllMeasuredCellsNonUploadedCount()");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED + " < 1");

		 Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" }, null, null, null, null, null);

		 try {
			 if (c.moveToFirst()) {
				 int countcol = c.getColumnIndex("COUNT");
				 int res = c.getInt(countcol);

				 return res;
			 }
			 return 0;
		 } finally {
			 c.close();
		 }
	 }

	 public MeasuredCellsDBIterator getMeasuredCellsInRegion(double north, double east, double south, double west) {
		 logDebug("getMeasuredCellsInRegion(" + north + ", " + west + ", " + south + ", " + east + ")");

		 SQLiteDatabase db = getReadableDatabase();
		 SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		 query.setTables(MeasuredCellsTable.MEASUREDCELLS_TABLENAME);

		 query.appendWhere(MeasuredCellsTable.MEASUREDCELLS_COLUMN_LAT + " BETWEEN " + south + " AND " + north + " AND " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_LON + " BETWEEN " + west + " AND " + east);

		 Cursor c = query.query(db, null, null, null, null, null, null);

		 return new MeasuredCellsDBIterator(c);
	 }

	 public void setAllMeasuredCellsUploaded() {
		 logDebug("setMeasuredCellsAllUploaded()");

		 SQLiteDatabase db = getWritableDatabase();
		 db.execSQL("UPDATE " + MeasuredCellsTable.MEASUREDCELLS_TABLENAME + " SET " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_UPLOADED + "=1, " + MeasuredCellsTable.MEASUREDCELLS_COLUMN_NEWCELL + "=0");
	 }

	 /**
	  * Used in database operations.
	  * @return start of today in milliseconds
	  */
	 public static long getStartOfToday() {
		 Calendar today = new GregorianCalendar();
		 today.set(Calendar.HOUR_OF_DAY, 0);
		 today.set(Calendar.MINUTE, 0);
		 today.set(Calendar.SECOND, 0);
		 today.set(Calendar.MILLISECOND, 0);

		 long startOfToday = today.getTimeInMillis();
		 return startOfToday;
	 }
}