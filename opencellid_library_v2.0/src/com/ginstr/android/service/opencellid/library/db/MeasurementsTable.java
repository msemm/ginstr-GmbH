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

import com.ginstr.android.service.opencellid.library.data.Measurement;

/**
 * Database definition for measurements table
 * 
 * @author Roberto
 * 
 */
public class MeasurementsTable {
	static final String MEASUREMENTS_TABLENAME = "measurements";

	static final String MEASUREMENTS_COLUMN_TIMESTAMP = "timestamp";
	static final String MEASUREMENTS_COLUMN_CELLID = "cellid";
	static final String MEASUREMENTS_COLUMN_MNC = "mnc";
	static final String MEASUREMENTS_COLUMN_MCC = "mcc";
	static final String MEASUREMENTS_COLUMN_LAC = "lac";
	static final String MEASUREMENTS_COLUMN_LAT = "lat";
	static final String MEASUREMENTS_COLUMN_LON = "lon";
	static final String MEASUREMENTS_COLUMN_SPEED = "speed";
	static final String MEASUREMENTS_COLUMN_HEADING = "heading";
	static final String MEASUREMENTS_COLUMN_RECEPTION = "reception";
	static final String MEASUREMENTS_COLUMN_UPLOADED = "uploaded";
	static final String MEASUREMENTS_COLUMN_NETWORK_TYPE = "network_type";
	static final String MEASUREMENTS_COLUMN_ACCURACY = "accuracy"; 

	/*
	 * Measurements table and related indexes
	 */
	static void createTables(final SQLiteDatabase db) {

		db.execSQL("CREATE TABLE " + MEASUREMENTS_TABLENAME + " ("
				+ MEASUREMENTS_COLUMN_TIMESTAMP + " LONG,"
				+ MEASUREMENTS_COLUMN_CELLID + " INTEGER, "
				+ MEASUREMENTS_COLUMN_MNC + " INTEGER,"
				+ MEASUREMENTS_COLUMN_MCC + " INTEGER, "
				+ MEASUREMENTS_COLUMN_LAC + " INTEGER,"
				+ MEASUREMENTS_COLUMN_RECEPTION + " INTEGER, "
				+ MEASUREMENTS_COLUMN_LAT + " DOUBLE,"
				+ MEASUREMENTS_COLUMN_LON + " DOUBLE,"
				+ MEASUREMENTS_COLUMN_SPEED + " DOUBLE,"
				+ MEASUREMENTS_COLUMN_HEADING + " DOUBLE,"
				+ MEASUREMENTS_COLUMN_UPLOADED + " INTEGER, "
				+ MEASUREMENTS_COLUMN_NETWORK_TYPE + " VARCHAR(10), "
				+ MEASUREMENTS_COLUMN_ACCURACY + " FLOAT, "
				+ " PRIMARY KEY(" + MEASUREMENTS_COLUMN_TIMESTAMP + " , " + MEASUREMENTS_COLUMN_CELLID + "));");

		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREMENTS_TABLENAME + "Idx on "
				+ MEASUREMENTS_TABLENAME + " (" + MEASUREMENTS_COLUMN_MNC + " , " + MEASUREMENTS_COLUMN_MCC + " , "
				+ MEASUREMENTS_COLUMN_CELLID + " , " + MEASUREMENTS_COLUMN_LAC + " ) ");

		db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREMENTS_TABLENAME + "Idx2 on "
				+ MEASUREMENTS_TABLENAME + " (" + MEASUREMENTS_COLUMN_UPLOADED + " ) ");
	}

	static void dropTables(final SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + MEASUREMENTS_TABLENAME);
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREMENTS_TABLENAME + "Idx");
		db.execSQL("DROP INDEX IF EXISTS " + MEASUREMENTS_TABLENAME + "Idx2");
	}

	static void upgradeTables(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		if (oldVersion == 3) {
			db.execSQL("CREATE INDEX IF NOT EXISTS " + MEASUREMENTS_TABLENAME + "Idx on "
					+ MEASUREMENTS_TABLENAME + " (" + MEASUREMENTS_COLUMN_CELLID + " , " + MEASUREMENTS_COLUMN_LAC
					+ " , " + MEASUREMENTS_COLUMN_MNC + " , " + MEASUREMENTS_COLUMN_MCC + " ) ");
		} else if (oldVersion < 3) {
			dropTables(db);
			createTables(db);
		} else if (oldVersion < 13) {
			try
			{
				db.execSQL("ALTER TABLE " + MEASUREMENTS_TABLENAME + " ADD " + MEASUREMENTS_COLUMN_NETWORK_TYPE + " VARCHAR(50)");
			} catch (Exception ex)
			{
				ex.printStackTrace();
			}
		} else if (oldVersion < 14) {
			try
			{
				db.execSQL("ALTER TABLE " + MEASUREMENTS_TABLENAME + " ADD " + MEASUREMENTS_COLUMN_ACCURACY + " FLOAT");
			} catch (Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}

	public static class MeasurementsDBIterator implements DBIterator<Measurement> {
		private final Cursor c;
		final int timecol;
		final int cellcol;
		final int lacCol;
		final int mncCol;
		final int mccCol;
		final int signalCol;
		final int speedCol;
		final int headingCol;
		final int latCol;
		final int lonCol;
		final int uplCol;
		final int networkTypeCol;
		final int accuracyCol;

		public MeasurementsDBIterator(Cursor cursor) {
			super();

			c = cursor;

			timecol = c.getColumnIndex(MEASUREMENTS_COLUMN_TIMESTAMP);
			cellcol = c.getColumnIndex(MEASUREMENTS_COLUMN_CELLID);
			lacCol = c.getColumnIndex(MEASUREMENTS_COLUMN_LAC);
			mncCol = c.getColumnIndex(MEASUREMENTS_COLUMN_MNC);
			mccCol = c.getColumnIndex(MEASUREMENTS_COLUMN_MCC);
			signalCol = c.getColumnIndex(MEASUREMENTS_COLUMN_RECEPTION);
			speedCol = c.getColumnIndex(MEASUREMENTS_COLUMN_SPEED);
			headingCol = c.getColumnIndex(MEASUREMENTS_COLUMN_HEADING);
			latCol = c.getColumnIndex(MEASUREMENTS_COLUMN_LAT);
			lonCol = c.getColumnIndex(MEASUREMENTS_COLUMN_LON);
			uplCol = c.getColumnIndex(MEASUREMENTS_COLUMN_UPLOADED);
			networkTypeCol = c.getColumnIndex(MEASUREMENTS_COLUMN_NETWORK_TYPE);
			accuracyCol = c.getColumnIndex(MEASUREMENTS_COLUMN_ACCURACY);
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
		public Measurement next() {
			c.moveToNext();
			Measurement m = new Measurement(c.getLong(timecol),
					c.getInt(cellcol), c.getInt(mccCol), c.getInt(mncCol),
					c.getInt(lacCol), null,
					c.getInt(signalCol), c.getDouble(latCol),
					c.getDouble(lonCol), c.getInt(speedCol),
					c.getInt(headingCol), "", c.getInt(uplCol) > 0, c.getString(networkTypeCol), c.getFloat(accuracyCol));
			return m;
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
