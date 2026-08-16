package com.megatech.fms.data;

import android.content.Context;
import android.os.Environment;

import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.Receipt;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.data.entity.Review;
import com.megatech.fms.helpers.Logger;

import java.io.File;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dọn dữ liệu nghiệp vụ quá hạn: bảng trong Room và file ảnh/chữ ký ngoài thư mục
 * {@code Pictures} của ứng dụng.
 *
 * <h3>Nguyên tắc bất di bất dịch: KHÔNG xoá thứ chưa lên được server</h3>
 *
 * <p>Mọi câu xoá đều loại trừ bản ghi còn {@code isLocalModified} (hoặc {@code id = 0},
 * tức chưa từng được server cấp định danh). Một máy mất mạng nhiều ngày vẫn giữ nguyên
 * dữ liệu chờ gửi, dù nó đã quá hạn lưu. Xoá theo tuổi mà không xét cờ này thì mỗi lần
 * đường truyền hỏng vài ngày là mất trắng dữ liệu — đúng thứ toàn bộ lớp đồng bộ đang
 * chống lại.
 *
 * <p>File cũng vậy: file đang được một bản ghi CHƯA gửi tham chiếu tới thì không xoá, dù
 * đã quá hạn. Xoá nhầm sẽ làm hàng đợi gửi ảnh BM2508 dừng vĩnh viễn vì không còn file.
 *
 * <h3>Bảng KHÔNG bị đụng tới</h3>
 *
 * <p>Danh mục dùng chung — {@code Truck}, {@code Airline}, {@code User}, {@code Shift},
 * {@code Airports}, {@code Product}, {@code ParkingLot}, {@code BM2505Container} — không có
 * ngữ nghĩa thời gian. Xoá chúng chỉ làm rỗng các danh sách chọn cho tới lượt đồng bộ kế
 * tiếp, không giải phóng được bao nhiêu dung lượng.
 */
public final class DataRetention {

    private DataRetention() {
    }

    private static final String LOG_TAG = "RETENTION";

    /** Số ngày giữ lại. Dữ liệu cũ hơn mốc này bị xoá ở lượt dọn kế tiếp. */
    public static final int RETENTION_DAYS = 3;

    /**
     * Mốc cắt: 00:00 của ngày hiện tại lùi {@link #RETENTION_DAYS} ngày.
     *
     * <p>Cắt theo đầu ngày chứ không theo "đúng 72 giờ trước": nghiệp vụ tính theo ngày làm
     * việc, và mốc trôi theo từng phút sẽ làm dữ liệu biến mất ngay giữa ca.
     */
    public static long cutoffMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS);
        return calendar.getTimeInMillis();
    }

    /** Kết quả một lượt dọn — dùng cho log và cho test. */
    public static final class Result {
        public int rowsDeleted;
        public int filesDeleted;
        public int filesKept;

        @Override
        public String toString() {
            return String.format(java.util.Locale.US,
                    "rows=%d files=%d filesKept=%d", rowsDeleted, filesDeleted, filesKept);
        }
    }

    public static Result purge(Context context, AppDatabase db) {
        return purge(context, db, cutoffMillis());
    }

    /**
     * Dọn một lần sau khi đăng nhập thành công, chạy nền.
     *
     * <p>Đây là thời điểm hợp lý duy nhất: dữ liệu chỉ già đi theo NGÀY, nên gắn vào chu kỳ
     * đồng bộ (~30 giây/lần) chỉ tạo ra hàng nghìn lượt quét không dọn thêm được gì. Đăng
     * nhập cũng là lúc máy chắc chắn rảnh — chưa vào ca, không có mẻ nào đang bơm.
     *
     * <p>Nuốt mọi lỗi: dọn dữ liệu cũ không bao giờ được phép cản trở việc đăng nhập.
     */
    public static void purgeAfterLogin(Context context) {
        if (context == null) return;

        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                purge(appContext, AppDatabase.getInstance(appContext));
            } catch (Throwable t) {
                Logger.appendLog(LOG_TAG, "Dọn sau đăng nhập lỗi: " + t.getMessage());
            }
        }, "FMS-Retention").start();
    }

    public static Result purge(Context context, AppDatabase db, long cutoff) {
        Result result = new Result();
        if (db == null) return result;

        try {
            result.rowsDeleted = purgeTables(db, cutoff);
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Dọn bảng lỗi: " + ex.getMessage());
        }

        try {
            purgeFiles(context, db, cutoff, result);
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Dọn file lỗi: " + ex.getMessage());
        }

        // Chỉ ghi log khi THỰC SỰ có xoá. Lượt dọn chạy mỗi chu kỳ đồng bộ (~30 giây) và
        // tuyệt đại đa số là không có gì để xoá; ghi log mọi lượt sẽ thêm ~2.880 dòng vô
        // nghĩa mỗi ngày mỗi xe, đúng loại nhiễu làm chìm mất những dòng đáng đọc.
        if (result.rowsDeleted > 0 || result.filesDeleted > 0)
            Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                    "Dọn dữ liệu quá %d ngày: %s", RETENTION_DAYS, result));

        return result;
    }

    private static int purgeTables(AppDatabase db, long cutoff) {
        int total = 0;

        total += db.refuelItemDao().deleteOlderThan(cutoff);
        total += db.receiptDao().deleteOlderThan(cutoff);
        total += db.truckFuelDao().deleteOlderThan(cutoff);
        total += db.invoiceDao().deleteOlderThan(cutoff);
        total += db.bm2503Dao().deleteOlderThan(cutoff);
        total += db.bm2504Dao().deleteOlderThan(cutoff);
        total += db.bm2505Dao().deleteOlderThan(cutoff);
        total += db.bm2508Dao().deleteOlderThan(cutoff);
        total += db.checkTrucksDao().deleteOlderThan(cutoff);
        total += db.reviewDao().deleteOlderThan(cutoff);
        total += db.bm7501Dao().deleteOlderThan(cutoff);
        total += db.logEntryDao().deleteOlderThan(cutoff);
        total += db.truckInvoiceDao().deleteOlderThan(cutoff);

        // Flight là dữ liệu kế hoạch thuần của server, không có bản nháp local. Nhưng phải
        // xoá SAU cùng và chỉ những chuyến không còn phiếu nào trỏ tới, nếu không một phiếu
        // đang chờ gửi sẽ mất luôn thông tin chuyến để hiển thị.
        total += db.flightDao().deleteOlderThanUnreferenced(cutoff);

        return total;
    }

    /**
     * Xoá ảnh chụp màn hình, ảnh chữ ký và ảnh review đã quá hạn.
     *
     * <p>Giữ lại mọi file còn được một bản ghi CHƯA gửi tham chiếu tới, bất kể tuổi.
     */
    private static void purgeFiles(Context context, AppDatabase db, long cutoff, Result result) {
        if (context == null) return;

        File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (folder == null || !folder.isDirectory()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        Set<String> keepPaths = pathsInUse(db);

        for (File file : files) {
            if (!file.isFile()) continue;
            if (file.lastModified() >= cutoff) continue;

            if (keepPaths.contains(file.getAbsolutePath()) || keepPaths.contains(file.getName())) {
                result.filesKept++;
                continue;
            }

            if (file.delete()) result.filesDeleted++;
            else Logger.appendLog(LOG_TAG, "Không xoá được " + file.getName());
        }
    }

    /**
     * Đường dẫn (và tên file) đang được các bản ghi chưa gửi tham chiếu.
     *
     * <p>Đọc TRỰC TIẾP từ chuỗi jsonData, không dựng model: constructor của các model
     * nghiệp vụ có tác dụng phụ (đọc setting, khởi tạo Crashlytics) nên chỉ để lấy vài
     * đường dẫn mà dựng chúng là vừa nặng vừa dễ ném lỗi. Một lỗi ở đây đồng nghĩa với
     * mất lớp bảo vệ và file bị xoá nhầm.
     *
     * <p>Gom cả đường dẫn tuyệt đối lẫn tên file suy ra từ quy ước đặt tên, vì các nhánh
     * chụp ảnh trong app dùng hai cách khác nhau.
     */
    private static Set<String> pathsInUse(AppDatabase db) {
        Set<String> keep = new HashSet<>();

        try {
            for (Receipt item : db.receiptDao().getModified()) {
                addPathsFromJson(keep, item.getJsonData());
                addScreenshotOf(keep, item.getNumber());
            }
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Đọc receipt chờ gửi lỗi: " + ex.getMessage());
        }

        try {
            for (BM2508 item : db.bm2508Dao().getPendingAttachments()) {
                addPathsFromJson(keep, item.getJsonData());
            }
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Đọc BM2508 chờ gửi ảnh lỗi: " + ex.getMessage());
        }

        try {
            for (RefuelItem item : db.refuelItemDao().getModified()) {
                addScreenshotOf(keep, item.getUniqueId());
                addPathsFromJson(keep, item.getJsonData());
            }
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Đọc phiếu chờ gửi lỗi: " + ex.getMessage());
        }

        try {
            for (Review item : db.reviewDao().getModified()) {
                addPathsFromJson(keep, item.getJsonData());
            }
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Đọc review chờ gửi lỗi: " + ex.getMessage());
        }

        return keep;
    }

    /** Các khoá trong jsonData có thể chứa đường dẫn file trên đĩa. */
    private static final String[] PATH_KEYS = {
            "SignaturePath", "SellerSignaturePath", "PdfPath", "ImagePath",
            "AirlineSignaturePath", "UserSkypecSignaturePath"};

    private static void addPathsFromJson(Set<String> keep, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;

            com.google.gson.JsonObject obj = parsed.getAsJsonObject();
            for (String key : PATH_KEYS) {
                com.google.gson.JsonElement value = obj.get(key);
                if (value != null && value.isJsonPrimitive())
                    addPath(keep, value.getAsString());
            }
            com.google.gson.JsonElement number = obj.get("Number");
            if (number != null && number.isJsonPrimitive())
                addScreenshotOf(keep, number.getAsString());
        } catch (RuntimeException ignored) {
            // jsonData hỏng: không suy ra được đường dẫn nào, các file khác vẫn xét bình thường.
        }
    }

    private static void addScreenshotOf(Set<String> keep, String key) {
        if (key == null || key.trim().isEmpty()) return;
        keep.add("screenshot_" + key + ".jpg");
    }

    private static void addPath(Set<String> keep, String path) {
        if (path == null || path.trim().isEmpty()) return;
        keep.add(path);
        keep.add(new File(path).getName());
    }
}
