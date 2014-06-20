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
package com.ginstr.android.service.opencellid.library.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.TelephonyManager;

import com.ginstr.android.service.opencellid.library.data.Cell;


/**
 * Database definition for cells towers table
 * 
 * @author Roberto
 *
 */
public class CellsTable {
	public static final String CELLS_TABLENAME = "cells";

	public static final String CELLS_COLUMN_TIMESTAMP = "timestamp";
	public static final String CELLS_COLUMN_MCC = "mcc";
	public static final String CELLS_COLUMN_MNC = "mnc";
	public static final String CELLS_COLUMN_CELLID = "cellid";
	public static final String CELLS_COLUMN_LON = "lon";
	public static final String CELLS_COLUMN_LAC = "lac";
	public static final String CELLS_COLUMN_LAT = "lat";
	public static final String CELLS_COLUMN_NSAMPLES = "nsamples";

	/**
	 * Creates the towers table and indexes
	 * @param db
	 */
	static void createTables(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + CELLS_TABLENAME + " ("
				+ CELLS_COLUMN_TIMESTAMP + " LONG,"
				+ CELLS_COLUMN_MCC + " INTEGER, "
				+ CELLS_COLUMN_MNC + " INTEGER,"
				+ CELLS_COLUMN_CELLID + " INTEGER, "
				+ CELLS_COLUMN_LAC + " INTEGER,"
				+ CELLS_COLUMN_LAT + " DOUBLE,"
				+ CELLS_COLUMN_LON + " DOUBLE,"
				+ CELLS_COLUMN_NSAMPLES + " INTEGER,"
				+ "PRIMARY KEY("+ CELLS_COLUMN_MCC + " , " + CELLS_COLUMN_MNC + " , " + CELLS_COLUMN_CELLID + " , "  + CELLS_COLUMN_LAC + "));");

		// Index by lat&lon
		db.execSQL("CREATE INDEX IF NOT EXISTS " + CELLS_TABLENAME + "Idx_LatLon on "
				+ CELLS_TABLENAME + " (" + CELLS_COLUMN_LAT + ", " + CELLS_COLUMN_LON + " ) ");

		// Index by time
		db.execSQL("CREATE INDEX IF NOT EXISTS " + CELLS_TABLENAME + "Idx_Time on "
				+ CELLS_TABLENAME + " (" + CELLS_COLUMN_TIMESTAMP + " ) ");

	}

	static void dropTables(final SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + CELLS_TABLENAME);
		db.execSQL("DROP INDEX IF EXISTS " + CELLS_TABLENAME + "Idx_LatLon");
		db.execSQL("DROP INDEX IF EXISTS " + CELLS_TABLENAME + "Idx_Time");
	}

	static void upgradeTables(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		if (oldVersion < 8) {
			dropTables(db);
			createTables(db);
		}
	}

	public static class CellsDBIterator implements DBIterator<Cell> {
		private final Cursor c;

		final int timeCol;
		final int mccCol;
		final int mncCol;
		final int cidcol;
		final int lacCol;
		final int latCol;
		final int lonCol;
		final int nSamplesCol;

		public CellsDBIterator(Cursor cursor) {
			super();

			c = cursor;

			timeCol = c.getColumnIndex(CELLS_COLUMN_TIMESTAMP);
			mccCol = c.getColumnIndex(CELLS_COLUMN_MCC);
			mncCol = c.getColumnIndex(CELLS_COLUMN_MNC);
			cidcol = c.getColumnIndex(CELLS_COLUMN_CELLID);
			lacCol = c.getColumnIndex(CELLS_COLUMN_LAC);
			latCol = c.getColumnIndex(CELLS_COLUMN_LAT);
			lonCol = c.getColumnIndex(CELLS_COLUMN_LON);
			nSamplesCol = c.getColumnIndex(CELLS_COLUMN_NSAMPLES);
		}

		@Override
		public void close() {
			if (!c.isClosed()) {
				c.close();
			}
		}

		@Override
		public boolean hasNext() {
			boolean last = c.getCount() == 0 || c.isLast();
			if (last) {
				c.close();
			}
			return !last;
		}

		@Override
		public Cell next() {
			c.moveToNext();

			Cell t = new Cell(
					c.getLong(timeCol),
					c.getInt(cidcol),
					c.getInt(mccCol),
					c.getInt(mncCol),
					c.getInt(lacCol),
					null,
					c.getDouble(latCol),
					c.getDouble(lonCol),
					c.getInt(nSamplesCol));

			return t;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();

		}

		@Override
		public int getCount() {
			return c.getCount();
		}
	}
}
