package com.megatech.fms.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.helpers.Logger;

import java.io.File;

/**
 * Dọn cơ sở dữ liệu trước khi mở, để bản cập nhật không chết vì lệch schema.
 *
 * <p>Bối cảnh: {@code AppDatabase} định nghĩa các migration 5→8, 8→11, 8→12, 8→14 nhưng
 * <b>chỉ đăng ký</b> 9→10, 10→11, 11→12. Máy nào còn ở DB version ≤ 8 sẽ không có đường đi
 * và rơi vào {@code fallbackToDestructiveMigration()} — Room xoá sạch DB ngay lúc mở, giữa
 * lúc nhân viên đang dùng.
 *
 * <p>Lớp này làm việc đó một cách <b>chủ động và có ghi log</b>: đọc {@code PRAGMA user_version}
 * trước khi Room mở DB, và nếu version nằm ngoài dải có đường migration thì xoá file DB để
 * Room tạo lại sạch. Khác biệt so với việc để Room tự xử lý:
 * <ul>
 *   <li>Có log rõ đã xoá vì lý do gì, ở version nào — phục vụ dựng ma trận version thực địa.</li>
 *   <li>Xoá cả {@code -wal} và {@code -shm}, tránh sót mảnh gây lỗi lần mở sau.</li>
 *   <li>Chạy một lần, trước khi có kết nối nào mở ra.</li>
 * </ul>
 *
 * <p><b>Cảnh báo:</b> thao tác này xoá dữ liệu offline chưa đồng bộ của máy cũ. Chỉ áp dụng cho
 * dải version không còn đường migration; máy ở version 9 trở lên đi theo migration bình thường
 * và không bị đụng tới.
 */
public final class DatabaseMaintenance {

    private DatabaseMaintenance() {
    }

    private static final String LOG_TAG = "DB";

    /**
     * Version thấp nhất còn có đường migration tới bản hiện tại.
     * Dưới mốc này thì không có cách nào lên được, chỉ còn xoá và tạo lại.
     */
    public static final int MIN_MIGRATABLE_VERSION = 9;

    private static boolean checked = false;

    /**
     * Kiểm tra và dọn nếu cần. Gọi TRƯỚC khi dựng Room.
     *
     * @return true nếu đã xoá DB và sẽ tạo lại
     */
    public static synchronized boolean prepare(Context context) {
        if (checked) return false;
        checked = true;

        try {
            File dbFile = context.getDatabasePath(BuildConfig.DB_FILE);
            if (!dbFile.exists()) {
                Logger.appendLog(LOG_TAG, String.format(
                        "DB chưa tồn tại, sẽ tạo mới. appVersion=%d db=%s",
                        BuildConfig.VERSION_CODE, BuildConfig.DB_FILE));
                return false;
            }

            int userVersion = readUserVersion(dbFile);

            // Log này là nguồn dữ liệu để dựng ma trận "app version -> DB version" thực địa.
            // Cố ý KHÔNG ghi bất kỳ dữ liệu nghiệp vụ nào.
            Logger.appendLog(LOG_TAG, String.format(
                    "Mở DB: appVersion=%d db=%s user_version=%d",
                    BuildConfig.VERSION_CODE, BuildConfig.DB_FILE, userVersion));

            if (userVersion <= 0 || userVersion >= MIN_MIGRATABLE_VERSION) {
                return false;
            }

            Logger.appendLog(LOG_TAG, String.format(
                    "DB version %d nằm dưới mốc migration %d, xoá và tạo lại để tránh lỗi mở DB.",
                    userVersion, MIN_MIGRATABLE_VERSION));
            deleteDatabaseFiles(dbFile);
            return true;

        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "prepare lỗi: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Đọc {@code PRAGMA user_version} mà không đi qua Room, để không kích hoạt kiểm tra schema.
     * Trả về -1 nếu không đọc được (file hỏng, không phải SQLite...).
     */
    public static int readUserVersion(File dbFile) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            return db.getVersion();
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Không đọc được user_version: " + ex.getMessage());
            return -1;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                    // đóng lỗi ở đây không đáng để che mất kết quả đọc
                }
            }
        }
    }

    /** Xoá file DB kèm hai file phụ, thiếu một cái là lần mở sau vẫn lỗi. */
    private static void deleteDatabaseFiles(File dbFile) {
        deleteQuietly(dbFile);
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-wal"));
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-shm"));
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-journal"));
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            Logger.appendLog(LOG_TAG, "Không xoá được " + file.getName());
        }
    }
}
