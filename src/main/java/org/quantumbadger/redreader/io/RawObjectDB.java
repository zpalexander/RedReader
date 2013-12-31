/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.io;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.quantumbadger.redreader.common.UnexpectedInternalStateException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

public class RawObjectDB<K, E extends WritableObject<K>> extends SQLiteOpenHelper {

	private final Class<E> clazz;
	private static final int DB_VERSION = 1;

	private final Field[] fields;
	private final String[] fieldNames;

	private static final String TABLE_NAME = "objects",
			FIELD_ID = "RawObjectDB_id";

	public RawObjectDB(final Context context, final String dbFilename, final Class<E> clazz) {

		super(context.getApplicationContext(), dbFilename, null, DB_VERSION);
		this.clazz = clazz;

		final LinkedList<Field> fields = new LinkedList<Field>();
		for(final Field field : clazz.getFields()) {
			if((field.getModifiers() & Modifier.TRANSIENT) == 0) fields.add(field);
		}

		this.fields = fields.toArray(new Field[fields.size()]);

		fieldNames = new String[this.fields.length];
		for(int i = 0; i < this.fields.length; i++) fieldNames[i] = this.fields[i].getName();
	}

	private String getFieldTypeString(Class<?> fieldType) {

		if(fieldType == String.class)
			return " TEXT";

		else if(fieldType == Integer.class
				|| fieldType == Long.class
				|| fieldType == Integer.TYPE
				|| fieldType == Long.TYPE) {
			return " INTEGER";
		}

		else if(fieldType == Boolean.class
				|| fieldType == Boolean.TYPE) {
			return " INTEGER";

		} else {
			throw new UnexpectedInternalStateException();
		}
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {

		final StringBuilder query = new StringBuilder("CREATE TABLE ");
		query.append(TABLE_NAME);
		query.append('(');
		query.append(FIELD_ID);
		query.append(" TEXT PRIMARY KEY ON CONFLICT REPLACE");

		for(final Field field : fields) {
			query.append(',');
			query.append(field.getName());
			query.append(getFieldTypeString(field.getType()));
		}

		query.append(')');

		Log.i("RawObjectDB query string", query.toString());

		if(1==1) throw new RuntimeException("DEBUG");

		db.execSQL(query.toString());
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		// TODO detect version from static field/WritableObject method, delete all data, start again
	}

	public synchronized Collection<E> getAll() {

		final SQLiteDatabase db = getReadableDatabase();

		try {

			final Cursor cursor = db.query(TABLE_NAME, fieldNames, null, null, null, null, null);

			try {

				final LinkedList<E> result = new LinkedList<E>();
				while(cursor.moveToNext()) result.add(readFromCursor(cursor));
				return result;

			} catch(InstantiationException e) {
				throw new RuntimeException(e);

			} catch(IllegalAccessException e) {
				throw new RuntimeException(e);

			} finally { cursor.close(); }
		} finally { db.close(); }
	}

	public synchronized E getById(final K id) {
		final ArrayList<E> queryResult = getByField(FIELD_ID, id.toString());
		if(queryResult.size() != 1) return null;
		else return queryResult.get(0);
	}

	public synchronized ArrayList<E> getByField(final String field, final String value) {

		final SQLiteDatabase db = getReadableDatabase();

		try {

			final Cursor cursor = db.query(TABLE_NAME, fieldNames, String.format("%s=?", field),
					new String[] {value}, null, null, null);

			try {
				final ArrayList<E> result = new ArrayList<E>(cursor.getCount());
				while(cursor.moveToNext()) result.add(readFromCursor(cursor));
				return result;

			} catch(InstantiationException e) {
				throw new RuntimeException(e);

			} catch(IllegalAccessException e) {
				throw new RuntimeException(e);

			} finally { cursor.close(); }
		} finally { db.close(); }
	}

	private E readFromCursor(final Cursor cursor) throws IllegalAccessException, InstantiationException {

		final E obj = clazz.newInstance();

		for(int i = 0; i < fields.length; i++) {

			final Field field = fields[i];
			final Class<?> fieldType = field.getType();

			if(fieldType == String.class) {
				field.set(obj, cursor.getString(i));

			} else if(fieldType == Integer.class
					|| fieldType == Integer.TYPE) {
				field.setInt(obj, cursor.getInt(i));

			} else if(fieldType == Long.class
					|| fieldType == Long.TYPE) {
				field.setLong(obj, cursor.getLong(i));

			} else if(fieldType == Boolean.class
					|| fieldType == Boolean.TYPE) {
				field.setBoolean(obj, cursor.getInt(i) != 0);

			} else if(fieldType == WritableHashSet.class) {
				field.set(obj, new WritableHashSet<String>(cursor.getString(i)));

			} else {
				throw new UnexpectedInternalStateException();
			}
		}

		return obj;
	}

	public synchronized void put(E object) {

		final SQLiteDatabase db = getWritableDatabase();

		try {
			final ContentValues values = new ContentValues(fields.length + 1);
			db.insert(TABLE_NAME, null, toContentValues(object, values));

		} catch(IllegalAccessException e) {
			throw new RuntimeException(e);

		} finally { db.close(); }
	}

	public synchronized void putAll(final Collection<E> objects) {

		final SQLiteDatabase db = getWritableDatabase();

		try {

			final ContentValues values = new ContentValues(fields.length + 1);

			for(final E object : objects) {
				db.insert(TABLE_NAME, null, toContentValues(object, values));
			}

		} catch(IllegalAccessException e) {
			throw new RuntimeException(e);

		} finally { db.close(); }
	}

	private ContentValues toContentValues(final E obj, final ContentValues result) throws IllegalAccessException {

		result.put(FIELD_ID, obj.getKey().toString());

		for(int i = 0; i < fields.length; i++) {

			final Field field = fields[i];
			final Class<?> fieldType = field.getType();

			if(fieldType == String.class) {
				result.put(fieldNames[i], (String) field.get(obj));

			} else if(fieldType == Integer.class
					|| fieldType == Integer.TYPE) {
				result.put(fieldNames[i], field.getInt(obj));

			} else if(fieldType == Long.class
					|| fieldType == Long.TYPE) {
				result.put(fieldNames[i], field.getLong(obj));

			} else if(fieldType == Boolean.class
					|| fieldType == Boolean.TYPE) {
				result.put(fieldNames[i], field.getBoolean(obj) ? 1 : 0);

			} else if(fieldType == WritableHashSet.class) {
				result.put(fieldNames[i], field.get(obj).toString());

			} else {
				throw new UnexpectedInternalStateException();
			}
		}

		return result;
	}

}
