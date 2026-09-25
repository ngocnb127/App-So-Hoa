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
import com.megatech.fms.data.dao.BM2506Dao;
import com.megatech.fms.data.dao.BM2508Dao;
import com.megatech.fms.data.dao.BM2509Dao;
import com.megatech.fms.data.dao.BM7501Dao;
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
import com.megatech.fms.data.dao.TruckInvoiceDao;
import com.megatech.fms.data.dao.UserDao;
import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.BM2506;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.BM2509;
import com.megatech.fms.data.entity.BM7501;
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
import com.megatech.fms.data.entity.TruckInvoice;
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
        BM7501.class,
        BM2503.class,
        BM2504.class,
        CheckTrucks.class,
        Airports.class,
        Receipt.class,
        LogEntry.class,
        Review.class,
        BM2505Container.class,
        Product.class,
        TruckInvoice.class,
        BM2506.class,
        BM2509.class
        },
        version = 14,
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
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `TruckInvoice` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` INTEGER NOT NULL, `truckId` INTEGER NOT NULL, `flightCode` TEXT, `billNo` TEXT, `billDate` INTEGER, `invoiceNumber` TEXT, `signNo` TEXT, `loginTaxCode` TEXT, `flightId` INTEGER NOT NULL, `electronicInvoiceId` TEXT, `status` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_TruckInvoice_invoiceId` ON `TruckInvoice` (`invoiceId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_TruckInvoice_billDate` ON `TruckInvoice` (`billDate`)");
        }
    };
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE BM2508 ADD COLUMN isAttachmentPending INTEGER NOT NULL DEFAULT 0");
        }
    };
    /**
     * Thêm bảng BM7501 (phiếu yêu cầu hút nhiên liệu).
     *
     * <p>Chỉ tạo bảng mới, không đụng bảng cũ — nên không có rủi ro mất dữ liệu cho máy
     * đang ở version 12. Chỉ mục unique phải khai ở ĐÂY khớp với @Index của entity, nếu
     * lệch thì Room sẽ báo lỗi validate schema lúc mở DB.
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS BM7501 ("
                    + "localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "id INTEGER NOT NULL,"
                    + "jsonData TEXT,"
                    + "isSynced INTEGER NOT NULL,"
                    + "isLocalModified INTEGER NOT NULL,"
                    + "dateUpdated INTEGER,"
                    + "isDeleted INTEGER NOT NULL,"
                    + "uniqueId TEXT,"
                    + "refuelItemUniqueId TEXT,"
                    + "revisionNumber INTEGER NOT NULL,"
                    + "supersedesUniqueId TEXT,"
                    + "localNumber TEXT,"
                    + "serverNumber TEXT,"
                    + "localRevision INTEGER NOT NULL,"
                    + "businessStatus TEXT,"
                    + "syncStatus TEXT,"
                    + "truckId INTEGER NOT NULL,"
                    + "enteredByUserId INTEGER NOT NULL,"
                    + "dateCreated INTEGER,"
                    + "signedAt INTEGER,"
                    + "printedAt INTEGER,"
                    + "reprintCount INTEGER NOT NULL,"
                    + "signedSnapshotHash TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_BM7501_refuelItemUniqueId_revisionNumber "
                    + "ON BM7501 (refuelItemUniqueId, revisionNumber)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_BM7501_localNumber ON BM7501 (localNumber)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "index_BM7501_refuelItemUniqueId ON BM7501 (refuelItemUniqueId)");
        }
    };

    /** Thêm bảng BM2506 (biên bản lấy mẫu) và BM2509 (kiểm tra đối chứng). Chỉ tạo bảng mới. */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            for (String table : new String[]{"BM2506", "BM2509"})
                database.execSQL("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "time INTEGER,"
                        + "truckId INTEGER NOT NULL,"
                        + "id INTEGER NOT NULL,"
                        + "localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "jsonData TEXT,"
                        + "isSynced INTEGER NOT NULL,"
                        + "isLocalModified INTEGER NOT NULL,"
                        + "dateUpdated INTEGER,"
                        + "isDeleted INTEGER NOT NULL,"
                        + "uniqueId TEXT)");
        }
    };

    public abstract RefuelItemDao refuelItemDao();
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Máy ở DB version không còn đường migration sẽ được dọn chủ động ở đây,
                    // có ghi log, thay vì để Room âm thầm huỷ DB lúc đang dùng.
                    DatabaseMaintenance.prepare(context.getApplicationContext());

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, BuildConfig.DB_FILE)
                            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                            .fallbackToDestructiveMigration()  // lưới an toàn cho máy version cổ
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract BM7501Dao bm7501Dao();

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
    public abstract BM2506Dao bm2506Dao();
    public abstract BM2509Dao bm2509Dao();

    public abstract CheckTrucksDao checkTrucksDao();

    public abstract InvoiceDao invoiceDao();
    public abstract TruckInvoiceDao truckInvoiceDao();

    public  abstract FlightDao flightDao();

    public abstract ReceiptDao receiptDao();

    public abstract LogEntryDao logEntryDao();

    public abstract ReviewDao reviewDao();
}
