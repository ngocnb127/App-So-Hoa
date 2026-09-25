package com.megatech.fms.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

/** Kiểm tra trực tiếp DDL migration thêm ranh giới replica/membership. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppDatabaseMigrationTest {

    @Test
    public void migration13To14AddsReplicaColumnsWithoutChangingExistingRows() {
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(null)
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE RefuelItem ("
                                        + "localId INTEGER PRIMARY KEY NOT NULL, "
                                        + "uniqueId TEXT)");
                            }

                            @Override
                            public void onUpgrade(
                                    SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                            }
                        })
                        .build());

        try {
            SupportSQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO RefuelItem(localId, uniqueId) VALUES(1, 'legacy')");

            AppDatabase.MIGRATION_13_14.migrate(db);

            Map<String, String> defaults = new HashMap<>();
            try (Cursor cursor = db.query("PRAGMA table_info(RefuelItem)")) {
                int nameIndex = cursor.getColumnIndexOrThrow("name");
                int defaultIndex = cursor.getColumnIndexOrThrow("dflt_value");
                while (cursor.moveToNext()) {
                    defaults.put(cursor.getString(nameIndex),
                            cursor.isNull(defaultIndex) ? null : cursor.getString(defaultIndex));
                }
            }
            assertTrue(defaults.containsKey("remoteReplica"));
            assertTrue(defaults.containsKey("remoteOthersUidsJson"));
            assertEquals("0", defaults.get("remoteReplica"));

            try (Cursor cursor = db.query("SELECT remoteReplica, remoteOthersUidsJson "
                    + "FROM RefuelItem WHERE localId = 1")) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
                assertTrue(cursor.isNull(1));
            }
        } finally {
            helper.close();
        }
    }
}
