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

import com.ginstr.android.service.opencellid.library.data.MeasuredCell;

/**
 * Database definition for 'measuredcells' table.
 * 
 * @author ginstr
 *
 */
public class MeasuredCellsTable {
	public static final String MEASUREDCELLS_TABLENAME = "measuredcells";

	public static final String MEASUREDCELLS_COLUMN_TIMESTAMP = "timestamp";
	public static final String MEASUREDCELLS_COLUMN_MCC = "mcc";
	public static final String MEASUREDCELLS_COLUMN_MNC = "mnc";
	public static final String MEASUREDCELLS_COLUMN_CELLID = "cellid";
	public static final String MEASUREDCELLS_COLUMN_LON = "lon";
	public static final String MEASUREDCELLS_COLUMN_LAC = "lac";
	public static final String MEASUREDCELLS_COLUMN_LAT = "lat";
	public static final String MEASUREDCELLS_COLUMN_NSAMPLES = "nsamples";
	public static final String MEASUREDCELLS_COLUMN_UPLOADED = "uploaded";
	public static final String MEASUREDCELLS_COLUMN_NEWCELL = "newcell";


	/**
	 * Creates the towers table and indexes
	 * @param db
	 */
	static void createTables(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + MEASUREDCELLS_TABLENAME + " ("
				+ MEASUREDCELLS_COLUMN_TIMESTAMP + " LONG,"
				+ MEASUREDCELLS_COLUMN_MCC + " INTEGER, "
				+ MEASUREDCELLS_COLUMN_MNC + " INTEGER,"
				+ MEASUREDCELLS_COLUMN_CELLID + " INTEGER, "
				+ MEASUREDCELLS_COLUMN_LAC + " INTEGER,"
				+ MEASUREDCELLS_COLUMN_LAT + " DOUBLE,"
				+ MEASUREDCELLS_COLUMN_LON + " DOUBLE,"
				+ MEASUREDCELLS_COLUMN_NSAMPLES + " INTEGER,"
				+ MEASUREDCELLS_COLUMN_UPLOADED + " INTEGER,"
				+ MEASUREDCELLS_COLUMN_NEWCELL + " INTEGER,"
				+ "PRIMARY KEY("+ MEASUREDCELLS_COLUMN_MCC + " , " + MEASUREDCELLS_COLUMN_MNC + " , " + MEASUREDCELLS_COLUMN_CELLID + " , "  + MEASUREDCELLS_COLUMN_LAC + "));");

		// Index by lat&lon
		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_LatLon on "
				+ MEASUREDCELLS_TABLENAME + " (" + MEASUREDCELLS_COLUMN_LAT + ", " + MEASUREDCELLS_COLUMN_LON + " ) ");

		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_Uploaded on "
				+ MEASUREDCELLS_TABLENAME + " (" + MEASUREDCELLS_COLUMN_UPLOADED + " ) ");

		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_NewCell on "
				+ MEASUREDCELLS_TABLENAME + " (" + MEASUREDCELLS_COLUMN_NEWCELL + " ) ");

		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_Time on "
				+ MEASUREDCELLS_TABLENAME + " (" + MEASUREDCELLS_COLUMN_TIMESTAMP + " ) ");
	}

	static void dropTables(final SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + MEASUREDCELLS_TABLENAME);
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_LatLon");
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_Uploaded");
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_NewCell");
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREDCELLS_TABLENAME + "Idx_Time");
	}

	static void upgradeTables(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		if (oldVersion == 9) {
			createTables(db);

		} else  if (oldVersion < 8) {
			dropTables(db);
			createTables(db);
		}
	}

	public static class MeasuredCellsDBIterator implements DBIterator<MeasuredCell> {
		private final Cursor c;

		final int timeCol;
		final int mccCol;
		final int mncCol;
		final int cidcol;
		final int lacCol;
		final int latCol;
		final int lonCol;
		final int nSamplesCol;
		final int uploadedCol;
		final int newCellCol;

		public MeasuredCellsDBIterator(Cursor cursor) {
			super();

			c = cursor;

			timeCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_TIMESTAMP);
			mccCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_MCC);
			mncCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_MNC);
			cidcol = c.getColumnIndex(MEASUREDCELLS_COLUMN_CELLID);
			lacCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_LAC);
			latCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_LAT);
			lonCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_LON);
			nSamplesCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_NSAMPLES);
			uploadedCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_UPLOADED);
			newCellCol = c.getColumnIndex(MEASUREDCELLS_COLUMN_NEWCELL);
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
		public MeasuredCell next() {
			c.moveToNext();
			MeasuredCell t = new MeasuredCell(
					c.getLong(timeCol),
					c.getInt(cidcol),
					c.getInt(mccCol),
					c.getInt(mncCol),
					c.getInt(lacCol),
					null,
					c.getDouble(latCol),
					c.getDouble(lonCol),
					c.getInt(nSamplesCol),
					c.getInt(uploadedCol) > 0,
					c.getInt(newCellCol) > 0);
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
