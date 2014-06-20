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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.enaikoon.android.service.opencellid.library.data.Cell;
import com.enaikoon.android.service.opencellid.library.db.CellsTable.CellsDBIterator;

/**
 * Used to manipulate with records in 'Cells' table.
 * 
 * @author Roberto
 */
public class CellsDatabase extends SQLiteOpenHelper {
	private static final String TAG = CellsDatabase.class.getSimpleName();

	public static interface DBListener {
		public void progress(long progress, long total);

		public boolean isCancelled();
	}

	private final OpenCellIdLibContext mLibContext;

	private static final int DATABASE_VERSION = 1;

	private void logDebug(String message) {
		mLibContext.getLogService().writeLog(Log.DEBUG, TAG, message);
	}

	private void logWarning(Exception ex) {
		mLibContext.getLogService().writeLog(Log.WARN, TAG, "", ex);
	}

	public CellsDatabase(final Context context,
			final OpenCellIdLibContext libContext) {
		super(context, libContext.getCellsDataBaseFullPath(), null,
				DATABASE_VERSION);

		mLibContext = libContext;

		logDebug("CellsDatabase() constructor");
	}

	/*
	 * SQLiteOpenHelper lifecycle methods
	 */

	@Override
	public void onCreate(final SQLiteDatabase db) {
		logDebug("onCreate()");

		CellsTable.createTables(db);

		db.execSQL("CREATE TABLE cellsstats (name VARCHAR, value LONG, PRIMARY KEY (name))");
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, final int oldVersion,
			final int newVersion) {
		logDebug("onUpgrade(): Upgrading db from version " + oldVersion
				+ " to " + newVersion);

		CellsTable.upgradeTables(db, oldVersion, newVersion);
	}

	/**
	 * Inserts a tower record on the table
	 * 
	 * @param t
	 */
	public void addTower(Cell t) {
		logDebug("addTower(" + t.getCellid() + ", " + t.getMnc() + ", "
				+ t.getMcc() + ", " + t.getLac() + ")");

		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();

		values.put(CellsTable.CELLS_COLUMN_TIMESTAMP, t.getTimestamp());
		values.put(CellsTable.CELLS_COLUMN_CELLID, t.getCellid());
		values.put(CellsTable.CELLS_COLUMN_MNC, t.getMnc());
		values.put(CellsTable.CELLS_COLUMN_MCC, t.getMcc());
		values.put(CellsTable.CELLS_COLUMN_LAC, t.getLac());
		values.put(CellsTable.CELLS_COLUMN_LAT, t.getLat());
		values.put(CellsTable.CELLS_COLUMN_LON, t.getLon());
		values.put(CellsTable.CELLS_COLUMN_NSAMPLES, t.getNumSamples());

		if (db.replaceOrThrow(CellsTable.CELLS_TABLENAME, null, values) == -1) {
			logDebug("addTower(): Error inserting network");
		}
	}

	public void clearAllTowers() {
		logDebug("clearAllTowers()");

		SQLiteDatabase db = getWritableDatabase();

		db.delete(CellsTable.CELLS_TABLENAME, null, null);

		db.delete(CELLSTATS_TABLENAME, null, null);

	}

	public SQLiteStatement getTowerBatchInsertOrReplaceStatement() {
		logDebug("getTowerBatchInsertStatement()");

		SQLiteDatabase db = getWritableDatabase();

		SQLiteStatement statement = db
				.compileStatement("INSERT OR REPLACE INTO "
						+ CellsTable.CELLS_TABLENAME + " ("
						+ CellsTable.CELLS_COLUMN_TIMESTAMP + ", "
						+ CellsTable.CELLS_COLUMN_CELLID + ", "
						+ CellsTable.CELLS_COLUMN_MCC + ", "
						+ CellsTable.CELLS_COLUMN_MNC + ", "
						+ CellsTable.CELLS_COLUMN_LAC + ", "
						+ CellsTable.CELLS_COLUMN_LAT + ", "
						+ CellsTable.CELLS_COLUMN_LON + ", "
						+ CellsTable.CELLS_COLUMN_NSAMPLES
						+ ") VALUES(?,?,?,?,?,?,?,?);");

		return statement;
	}

	public long insertBatchTower(SQLiteStatement stm, Cell tower) {
		try {
			stm.bindLong(1, tower.getTimestamp());
			stm.bindLong(2, tower.getCellid());
			stm.bindLong(3, tower.getMcc());
			stm.bindLong(4, tower.getMnc());
			stm.bindLong(5, tower.getLac());
			stm.bindDouble(6, tower.getLat());
			stm.bindDouble(7, tower.getLon());
			stm.bindLong(8, tower.getNumSamples());

			long res = stm.executeInsert();

			return res;
		} catch (SQLException e) {
			return -2;
		}
	}

	public CellsDBIterator getAllCellsForTimeRange(long from, long to) {
		logDebug("getAllCellsForTimeRange(" + from + "," + to + ")");

		SQLiteDatabase db = getReadableDatabase();
		SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		query.setTables(CellsTable.CELLS_TABLENAME);
		query.appendWhere(CellsTable.CELLS_COLUMN_TIMESTAMP + " >= " + from + " AND " + 
						  CellsTable.CELLS_COLUMN_TIMESTAMP + " <= " + to);
		Cursor c = query.query(db, null, null, null, null, null, null);

		return new CellsDBIterator(c);
	}
	
	public CellsDBIterator getAllTowers() {
		logDebug("getAllTowers()");

		SQLiteDatabase db = getReadableDatabase();
		SQLiteQueryBuilder query = new SQLiteQueryBuilder();

		query.setTables(CellsTable.CELLS_TABLENAME);

		Cursor c = query.query(db, null, null, null, null, null, null);

		return new CellsDBIterator(c);
	}

	public CellsDBIterator getTowersInRegion(double north, double east,
			double south, double west) {
		logDebug("getTowsersInRegion(" + north + ", " + west + ", " + south
				+ ", " + east + ")");

		SQLiteDatabase db = getReadableDatabase();
		SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		query.setTables(CellsTable.CELLS_TABLENAME);

		query.appendWhere(CellsTable.CELLS_COLUMN_LAT + " BETWEEN " + south
				+ " AND " + north + " AND " + CellsTable.CELLS_COLUMN_LON
				+ " BETWEEN " + west + " AND " + east);

		Cursor c = query.query(db, null, null, null, null, null, null);

		return new CellsDBIterator(c);
	}

	public Cell getTowerCell(int cellid, int lac, int mcc, int mnc) {
		logDebug("getTowerCell()");

		// long start = System.currentTimeMillis();

		SQLiteDatabase db = getReadableDatabase();
		SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		query.setTables(CellsTable.CELLS_TABLENAME);

		query.appendWhere(CellsTable.CELLS_COLUMN_MCC + " = " + mcc + " AND ");
		query.appendWhere(CellsTable.CELLS_COLUMN_MNC + " = " + mnc + " AND ");
		query.appendWhere(CellsTable.CELLS_COLUMN_CELLID + " = " + cellid
				+ " AND ");
		query.appendWhere(CellsTable.CELLS_COLUMN_LAC + " = " + lac);

		Cursor c = query.query(db, null, null, null, null, null, null, "1");

		try {
			CellsDBIterator dbIterator = new CellsDBIterator(c);

			logDebug("getTowerCell() q=" + query.toString() + " RESULT C="
					+ c.getCount());

			if (dbIterator.getCount() == 1) {
				return dbIterator.next();
			}

			return null;
		} finally {
			c.close();
			// Log.w(TAG, "getTowerCell() run in " + (System.currentTimeMillis()
			// - start) + " ms");
		}
	}

	/**
	 * @return may be null
	 */
	public Cell getLastUpdatedCell() {
		logDebug("getLastUpdatedCell()");
		try {
			SQLiteDatabase db = getReadableDatabase();
			SQLiteQueryBuilder query = new SQLiteQueryBuilder();
			query.setTables(CellsTable.CELLS_TABLENAME);

			Cursor c = query.query(db, null, null, null, null, null,
					CellsTable.CELLS_COLUMN_TIMESTAMP + " DESC ", "1");

			try {
				CellsTable.CellsDBIterator dbIterator = new CellsTable.CellsDBIterator(c);
				if (dbIterator.hasNext()) {
					return dbIterator.next();
				}
			} finally {
				c.close();
			}
		} catch (Exception e) {
			logWarning(e);
		}

		return null;
	}

	/*
	 * Stats related methods
	 */

	private static final String CELLSTATS_TABLENAME = "cellsstats";
	private static final String CELLSTATS_COLUMN_NAME = "name";
	private static final String CELLSTATS_COLUMN_VALUE = "value";

	private static final String STATS_GENERATED_TIMESTAMP = "SELECT UNIX_TIMESTAMP(NOW()) FROM CELLS";
	private static final String STATS_UPDATED_TIMESTAMP = "UPDATED CELLS";
	private static final String STATS_SELECT_COUNT = "SELECT COUNT(*) FROM CELLS";
	private static final String STATS_SELECT_MCC_COUNT = "SELECT COUNT(*) FROM CELLS WHERE mcc=%d";
	private static final String STATS_SELECT_MCC_MNC_COUNT = "SELECT COUNT(*) FROM CELLS WHERE mcc=%d AND mnc=%d";

	public void updateCellsStatsTable(
			final Map<Integer, Set<Integer>> mccMncMap,
			final boolean updateLastCell, DBListener listener) {
		Log.d(TAG, "updateCellsStatsTable() : " + mccMncMap);

		long numIndexes = updateLastCell ? 1 : 0;
		long currentIndex = 0;

		int oldTotalMcc = 0;
		int newTotalMcc = 0;

		// generate SQL sentences for all stats

		Map<String, Integer> mccSqls = new HashMap<String, Integer>();
		Map<String, Integer> mncSqls = new HashMap<String, Integer>();

		for (Integer mcc : mccMncMap.keySet()) {
			String mccSql = String.format(STATS_SELECT_MCC_COUNT, mcc);

			mccSqls.put(mccSql, 0);

			oldTotalMcc += getIntStat(mccSql);

			numIndexes++;

			for (Integer mnc : mccMncMap.get(mcc)) {
				mncSqls.put(
						String.format(STATS_SELECT_MCC_MNC_COUNT, mcc, mnc), 0);

				numIndexes++;
			}
		}

		if (listener != null) {
			listener.progress(0, numIndexes);

			if (listener.isCancelled()) {
				return;
			}
		}

		// Query new values for all stats

		SQLiteDatabase db = getWritableDatabase();

		// SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		// query.setTables(CellsTable.CELLS_TABLENAME);

		for (String mccSql : mccSqls.keySet()) {

			Cursor c = db.rawQuery(mccSql, null);

			try {
				if (c.moveToFirst()) {
					int valueCol = c.getColumnIndex("COUNT(*)");

					int count = c.getInt(valueCol);
					mccSqls.put(mccSql, count);

					newTotalMcc += count;
				}
			} finally {

				c.close();

				if (listener != null) {
					listener.progress(currentIndex++, numIndexes);

					if (listener.isCancelled()) {
						return;
					}
				}
			}
		}

		for (String mncSql : mncSqls.keySet()) {

			Cursor c = db.rawQuery(mncSql, null);
			// Cursor c = query.query(db, new String[] { "COUNT(*) AS COUNT" },
			// mncSql, null, null, null, null);

			try {
				if (c.moveToFirst()) {
					int valueCol = c.getColumnIndex("COUNT(*)");

					int count = c.getInt(valueCol);
					mncSqls.put(mncSql, count);
				}
			} finally {

				c.close();

				if (listener != null) {
					listener.progress(currentIndex++, numIndexes);

					if (listener.isCancelled()) {
						return;
					}
				}

			}
		}

		// and update statscells table
		SQLiteStatement stm = db.compileStatement("INSERT OR REPLACE INTO "
				+ CELLSTATS_TABLENAME + " (" + CELLSTATS_COLUMN_NAME + ", "
				+ CELLSTATS_COLUMN_VALUE + ") VALUES(?,?);");

		// imported timestamp
		if (updateLastCell) {
			Cell lastCell = getLastUpdatedCell();

			updateIndex(stm, STATS_GENERATED_TIMESTAMP, lastCell.getTimestamp());

			if (listener != null) {
				listener.progress(currentIndex++, numIndexes);

				if (listener.isCancelled()) {
					return;
				}
			}
		}

		// Last updated
		updateIndex(stm, STATS_UPDATED_TIMESTAMP, System.currentTimeMillis());

		// Total cell count
		updateIndex(stm, STATS_SELECT_COUNT, getAllTowerCount() - oldTotalMcc
				+ newTotalMcc);

		// Per MCC count
		for (Map.Entry<String, Integer> mccKS : mccSqls.entrySet()) {
			updateIndex(stm, mccKS.getKey(), mccKS.getValue());
		}

		// Per MCC/MNC count
		for (Map.Entry<String, Integer> mncKS : mncSqls.entrySet()) {
			updateIndex(stm, mncKS.getKey(), mncKS.getValue());
		}
	}

	private void updateIndex(SQLiteStatement stm, String name, long value) {
		stm.bindString(1, name);
		stm.bindLong(2, value);

		stm.executeInsert();
	}

	public int getAllTowerCount() {

		int t = getIntStat(STATS_SELECT_COUNT);

		logDebug("getAllTowerCount() = " + t);

		return t > 0 ? t : 0;
	}

	public int getAllTowerWithMccCount(int mcc) {
		logDebug("getAllTowerWithMccCount(" + mcc + ")");

		return getIntStat(String.format(STATS_SELECT_MCC_COUNT, mcc));
	}

	public int getAllTowerWithMccAndMncCount(int mcc, int mnc) {
		logDebug("getAllTowerWithMccAndMncCount(" + mcc + ", " + mnc + ")");

		return getIntStat(String.format(STATS_SELECT_MCC_MNC_COUNT, mcc, mnc));
	}

	public long getGeneratedTimestamp() {

		// server generates seconds, not milliseconds
		long time = getLongStat(STATS_GENERATED_TIMESTAMP);

		logDebug("getCellsGeneratedTimestamp() = " + time);

		return time;
	}

	public long updateGeneratedTimestamp(long time) {
		logDebug("updateGeneratedTimestamp() = " + time);

		SQLiteDatabase db = getWritableDatabase();
		SQLiteStatement stm = db.compileStatement("INSERT OR REPLACE INTO "
				+ CELLSTATS_TABLENAME + " (" + CELLSTATS_COLUMN_NAME + ", "
				+ CELLSTATS_COLUMN_VALUE + ") VALUES(?,?);");

		updateIndex(stm, STATS_GENERATED_TIMESTAMP, time);

		return time;

	}

	public long getLastUpdatedTimestamp() {

		long t = getLongStat(STATS_UPDATED_TIMESTAMP);

		logDebug("getLastUpdatedTimestamp() = " + t);

		return t;
	}

	private int getIntStat(String name) {
		SQLiteDatabase db = getReadableDatabase();

		Log.d(TAG, "getIntStat(" + name + ")");

		SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		query.setTables(CELLSTATS_TABLENAME);

		Cursor c = query.query(db, new String[] { CELLSTATS_COLUMN_VALUE },
				CELLSTATS_COLUMN_NAME + " = ?", new String[] { name }, null,
				null, null, "1");

		try {
			if (c.moveToFirst()) {
				int valueCol = c.getColumnIndex(CELLSTATS_COLUMN_VALUE);
				return c.getInt(valueCol);
			}

			return -1;
		} finally {
			c.close();
		}
	}

	private long getLongStat(String name) {

		Log.d(TAG, "getLongStat(" + name + ")");

		SQLiteDatabase db = getReadableDatabase();

		SQLiteQueryBuilder query = new SQLiteQueryBuilder();
		query.setTables(CELLSTATS_TABLENAME);

		Cursor c = query.query(db, new String[] { CELLSTATS_COLUMN_VALUE },
				CELLSTATS_COLUMN_NAME + " = ?", new String[] { name }, null,
				null, null, "1");

		try {
			if (c.moveToFirst()) {
				int valueCol = c.getColumnIndex(CELLSTATS_COLUMN_VALUE);
				return c.getLong(valueCol);
			}

			return -1L;
		} finally {
			c.close();
		}
	}
}