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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.enaikoon.android.service.opencellid.library.data.Network;

/**
 * 
 * Database definition for networks table.
 * 
 * @author Roberto
 * 
 */
public class NetworksTable {

	public static final String NETWORKS_TABLENAME = "networks";

	public static final String NETWORKS_COLUMN_TIMESTAMP = "timestamp";
	public static final String NETWORKS_COLUMN_MNC = "mnc";
	public static final String NETWORKS_COLUMN_MCC = "mcc";
	public static final String NETWORKS_COLUMN_TYPE = "type";
	public static final String NETWORKS_COLUMN_NAME = "name";
	public static final String NETWORKS_COLUMN_UPLOADED = "uploaded";

	/**
	 * Create the network table and related indexes
	 */
	static void createTables(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + NETWORKS_TABLENAME + " ("
					+ NETWORKS_COLUMN_TIMESTAMP + " LONG,"
					+ NETWORKS_COLUMN_MNC + " INTEGER," 
					+ NETWORKS_COLUMN_MCC + " INTEGER," 
					+ NETWORKS_COLUMN_TYPE + " VARCHAR(64),"
					+ NETWORKS_COLUMN_NAME + " VARCHAR(128)," 
					+ NETWORKS_COLUMN_UPLOADED + " INTEGER, " 
					+ "PRIMARY KEY(" + NETWORKS_COLUMN_NAME + "));");

		// Index by uploaded
		db.execSQL("CREATE INDEX IF NOT EXISTS " + NETWORKS_TABLENAME + "Idx on "
				+ NETWORKS_TABLENAME + " (" + NETWORKS_COLUMN_UPLOADED + " ) ");
	}

	static void dropTables(final SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + NETWORKS_TABLENAME);
		db.execSQL("DROP INDEX IF EXISTS " + NETWORKS_TABLENAME + "Idx");
	}

	static void upgradeTables(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		if (oldVersion < 12) {
			dropTables(db);
			createTables(db);
		}
	}

	public static class NetworkDBIterator implements DBIterator<Network> {
		private final Cursor c;
		final int timecol;
		final int mncCol;
		final int mccCol;
		final int typeCol;
		final int nameCol;
		final int uplCol;

		public NetworkDBIterator(Cursor cursor) {
			super();

			c = cursor;

			timecol = c.getColumnIndex(NETWORKS_COLUMN_TIMESTAMP);
			mncCol = c.getColumnIndex(NETWORKS_COLUMN_MNC);
			mccCol = c.getColumnIndex(NETWORKS_COLUMN_MCC);
			typeCol = c.getColumnIndex(NETWORKS_COLUMN_TYPE);
			nameCol = c.getColumnIndex(NETWORKS_COLUMN_NAME);
			uplCol = c.getColumnIndex(NETWORKS_COLUMN_UPLOADED);
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
		public Network next() {
			c.moveToNext();
			Network n = new Network(
					c.getLong(timecol),
					c.getInt(mccCol),
					c.getInt(mncCol),
					c.getString(typeCol),
					c.getString(nameCol),
					c.getInt(uplCol) > 0);
			return n;
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
