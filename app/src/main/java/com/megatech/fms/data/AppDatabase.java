package com.megatech.fms.data;


import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.data.dao.AirlineDao;
import com.megatech.fms.data.dao.AirportsDao;
import com.megatech.fms.data.dao.BM2503Dao;
import com.megatech.fms.data.dao.BM2504Dao;
import com.megatech.fms.data.dao.BM2505Dao;
import com.megatech.fms.data.dao.BM2508Dao;
import com.megatech.fms.data.dao.CheckTrucksDao;
import com.megatech.fms.data.dao.FlightDao;
import com.megatech.fms.data.dao.InvoiceDao;
import com.megatech.fms.data.dao.LogEntryDao;
import com.megatech.fms.data.dao.ParkingLotDao;
import com.megatech.fms.data.dao.ProductDao;
import com.megatech.fms.data.dao.ReceiptDao;
import com.megatech.fms.data.dao.RefuelItemDao;
import com.megatech.fms.data.dao.ReviewDao;
import com.megatech.fms.data.dao.ShiftDao;
import com.megatech.fms.data.dao.TruckDao;
import com.megatech.fms.data.dao.TruckFuelDao;
import com.megatech.fms.data.dao.UserDao;
import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.CheckTrucks;
import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.data.entity.Invoice;
import com.megatech.fms.data.entity.LogEntry;
import com.megatech.fms.data.entity.ParkingLot;
import com.megatech.fms.data.entity.Receipt;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.data.entity.Review;
import com.megatech.fms.data.entity.Shift;
import com.megatech.fms.data.entity.Truck;
import com.megatech.fms.data.entity.TruckFuel;
import com.megatech.fms.data.entity.User;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.data.entity.Product;

@Database(entities = {RefuelItem.class,
        Airline.class,
        ParkingLot.class,
        Flight.class,
        Truck.class,
        User.class,
        Shift.class,
        TruckFuel.class,
        Invoice.class,
        BM2505.class,
        BM2508.class,
        BM2503.class,
        BM2504.class,
        CheckTrucks.class,
        Airports.class,
        Receipt.class,
        LogEntry.class,
        Review.class,
        BM2505Container.class,
        Product.class
        },
        version = 10,
        exportSchema = false
        )
@TypeConverters({Converters.class,
        RefuelItem.REFUEL_ITEM_STATUS.class,
        RefuelItem.FLIGHT_STATUS.class,
        RefuelItem.ITEM_PRINT_STATUS.class,
        RefuelItem.ITEM_POST_STATUS.class,
        RefuelItem.REFUEL_ITEM_TYPE.class,
        INVOICE_TYPE.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public static Migration MIGRATION_5_8 = new Migration(5, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS CheckTrucks (" +
                    "id INTEGER NOT NULL," +
                    "DateCreated INTEGER ," +
                    "isDeleted INTEGER NOT NULL," +
                    "dateUpdated INTEGER ," +
                    "isLocalModified INTEGER NOT NULL," +
                    "isSynced INTEGER NOT NULL," +
                    "jsonData TEXT," +
                    "localId INTEGER NOT NULL PRIMARY KEY ," +
                    "uniqueId TEXT)" );
            database.execSQL("CREATE TABLE IF NOT EXISTS Airports (" +
                    "id INTEGER NOT NULL," +
                    "code TEXT ," +
                    "name TEXT ," +
                    "taxCode TEXT ," +
                    "address TEXT ," +
                    "isDeleted INTEGER NOT NULL," +
                    "dateUpdated INTEGER ," +
                    "isLocalModified INTEGER NOT NULL," +
                    "isSynced INTEGER NOT NULL," +
                    "jsonData TEXT," +
                    "localId INTEGER NOT NULL PRIMARY KEY ," +
                    "uniqueId TEXT)" );
            // Kiểm tra xem cột 'unit' đã tồn tại trong bảng TruckFuel hay chưa
            Cursor cursor = database.query("PRAGMA table_info(TruckFuel)");
            boolean columnExists = false;

            // Duyệt qua kết quả trả về để kiểm tra cột 'unit'
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (columnName.equals("unit")) {
                    columnExists = true;
                    break;
                }
            }
            cursor.close();

            // Nếu cột 'unit' chưa tồn tại, thì thêm cột 'unit' vào bảng TruckFuel
            if (!columnExists) {
                database.execSQL("ALTER TABLE TruckFuel ADD COLUMN unit TEXT DEFAULT 'Lit'");
                database.execSQL("UPDATE TruckFuel SET unit = 'Lit' WHERE unit IS NULL");
            }
        }
    };
    public static Migration MIGRATION_8_11 = new Migration(8, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Airports (" +
                    "id INTEGER NOT NULL," +
                    "code TEXT ," +
                    "name TEXT ," +
                    "taxCode TEXT ," +
                    "address TEXT ," +
                    "isDeleted INTEGER NOT NULL," +
                    "dateUpdated INTEGER ," +
                    "isLocalModified INTEGER NOT NULL," +
                    "isSynced INTEGER NOT NULL," +
                    "jsonData TEXT," +
                    "localId INTEGER NOT NULL PRIMARY KEY ," +
                    "uniqueId TEXT)" );

        }
    };
    public static Migration MIGRATION_8_12 = new Migration(8, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS BM2508 (" +
                    "id INTEGER NOT NULL," +
                    "DateCreated INTEGER ," +
                    "isDeleted INTEGER NOT NULL," +
                    "dateUpdated INTEGER ," +
                    "isLocalModified INTEGER NOT NULL," +
                    "isSynced INTEGER NOT NULL," +
                    "jsonData TEXT," +
                    "localId INTEGER NOT NULL PRIMARY KEY ," +
                    "uniqueId TEXT)" );

        }

    };
    public static Migration MIGRATION_8_14 = new Migration(8, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
          //  database.execSQL("ALTER TABLE BM2508 ADD COLUMN FlightId INTEGER");
            database.execSQL("ALTER TABLE Flight ADD COLUMN aircraftType TEXT");
        }

    };
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE RefuelItem ADD COLUMN clientSeq INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE RefuelItem ADD COLUMN serverRevision INTEGER NOT NULL DEFAULT 0");
        }
    };
    public abstract RefuelItemDao refuelItemDao();
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, BuildConfig.DB_FILE)
                            .addMigrations(MIGRATION_9_10)
                            .fallbackToDestructiveMigration()  // lưới an toàn cho máy version cổ
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ParkingLotDao parkingLotDao();

    public abstract TruckDao truckDao();

    public abstract AirlineDao airlineDao();
    public abstract AirportsDao AirportsDao();

    public abstract UserDao userDao();

    public abstract ShiftDao shiftDao();

    public abstract TruckFuelDao truckFuelDao();
    public abstract BM2505Dao bm2505Dao();
    public abstract BM2503Dao bm2503Dao();
    public abstract BM2504Dao bm2504Dao();
    public abstract ProductDao ProductDao();
    public abstract BM2508Dao bm2508Dao();

    public abstract CheckTrucksDao checkTrucksDao();

    public abstract InvoiceDao invoiceDao();

    public  abstract FlightDao flightDao();

    public abstract ReceiptDao receiptDao();

    public abstract LogEntryDao logEntryDao();

    public abstract ReviewDao reviewDao();
}
