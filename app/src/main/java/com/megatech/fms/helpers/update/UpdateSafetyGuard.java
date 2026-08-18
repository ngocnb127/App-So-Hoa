package com.megatech.fms.helpers.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.RefuelItem;

import java.util.List;

/**
 * Quyết định thời điểm ĐƯỢC PHÉP CÀI đặt bản mới.
 *
 * <p>Cài đặt làm hệ điều hành kết thúc tiến trình ứng dụng. Nếu điều đó xảy ra khi máy còn
 * dữ liệu nghiệp vụ chưa gửi lên server, dữ liệu đó nằm lại trong Room và chỉ được đồng bộ
 * ở lần mở app sau — trong khoảng đó FMS không nhìn thấy sản lượng đã tra nạp.
 *
 * <h3>Báo, KHÔNG chặn</h3>
 *
 * <p>Từ bản 110, dữ liệu chưa gửi KHÔNG còn chặn được việc cập nhật. Lý do là thực tế vận
 * hành: dữ liệu kẹt thường kẹt vì LỖI, mà bản sửa đúng lỗi đó lại nằm sau đúng cánh cửa
 * đang khoá — càng kẹt lâu càng không cập nhật được, càng không cập nhật được thì càng kẹt.
 * Trên xe, cập nhật chính là cách đẩy được đống dữ liệu đó đi.
 *
 * <p>Tiền đề của việc chặn vốn cũng đã sai: cài đè KHÔNG xoá dữ liệu — Room còn nguyên,
 * hàng đợi được gửi tiếp ngay khi app mở lại. Cái mất là vài phút đồng bộ, không phải số
 * liệu; cái giá của việc khoá thiết bị ở bản cũ lớn hơn hẳn.
 *
 * <p>Việc còn lại của lớp này là NÓI ĐÚNG tình trạng: còn bao nhiêu bản ghi, có bản nào
 * gửi lỗi không, đã chờ bao lâu. Người dùng xác nhận rồi cập nhật.
 *
 * <p>Chỉ chi phối bước CÀI. Việc kiểm tra phiên bản và tải file vẫn diễn ra bình thường.
 */
public final class UpdateSafetyGuard {

    private static final String LOG_TAG = "FMS_UPDATE";

    private static final String PREF_FILE = "fms_update";
    private static final String PREF_PENDING_SINCE = "pending_since";

    /**
     * Chờ quá lâu mà số bản ghi chờ không về 0 thì coi là kẹt, không phải là đang gửi.
     *
     * <p>30 phút: một lượt đồng bộ chạy 30 giây một lần, nên hàng đợi lành mạnh phải trống
     * trong vài phút. Còn đọng sau nửa giờ nghĩa là có thứ không tự đi được.
     */
    static final long STUCK_AFTER_MS = 30 * 60 * 1000L;

    public static final class Decision {
        public final boolean safe;
        /**
         * Chặn nhưng người dùng ĐƯỢC PHÉP vượt sau khi xác nhận.
         *
         * <p>Đây là lối thoát của bế tắc "dữ liệu kẹt vì lỗi mà bản vá bị chính nó chặn".
         */
        public final boolean overridable;
        /** Lý do đã ở dạng hiển thị được cho người dùng; null khi safe. */
        public final String reason;

        Decision(boolean safe, boolean overridable, String reason) {
            this.safe = safe;
            this.overridable = overridable;
            this.reason = reason;
        }
    }

    private UpdateSafetyGuard() {
    }

    /**
     * Gọi TRÊN LUỒNG NỀN — truy vấn Room.
     *
     * <p>"Chưa đồng bộ" xác định bằng cờ isLocalModified của các bảng nghiệp vụ.
     * Cố ý KHÔNG tính LogEntry: đó là dữ liệu telemetry, gần như luôn có bản ghi chờ,
     * tính vào sẽ chặn cập nhật vĩnh viễn.
     */
    public static Decision canInstallNow() {
        try {
            DataRepository repo = FMSApplication.getApplication().getRepository();

            List<RefuelItem> refuels = repo.getAllModifiedRefuel();

            int pending = 0;
            pending += size(refuels);
            pending += size(repo.getModifiedReceipt());
            pending += size(repo.getModifiedInvoice());
            pending += size(repo.getModifiedTruckFuel());
            pending += size(repo.getModifiedBM2503());
            pending += size(repo.getModifiedBM2504());
            pending += size(repo.getModifiedBM2505());
            pending += size(repo.getModifiedBM2508());
            pending += size(repo.getModifiedCheckTrucks());
            pending += size(repo.getModifiedReview());

            int failed = countFailed(refuels);
            Decision decision = decide(pending, failed,
                    pendingSince(pending > 0), System.currentTimeMillis());

            if (!decision.safe)
                Log.w(LOG_TAG, "Chặn cài đặt: pending=" + pending + " lỗi=" + failed
                        + " chovuot=" + decision.overridable);
            return decision;

        } catch (Throwable t) {
            // Không đọc được trạng thái dữ liệu thì không khẳng định là an toàn — nhưng cũng
            // KHÔNG khoá chết: một lỗi Room không được phép giam thiết bị ở bản cũ vĩnh viễn,
            // nhất là khi bản mới có thể chính là bản sửa lỗi Room đó.
            Log.e(LOG_TAG, "Không kiểm tra được dữ liệu chưa đồng bộ", t);
            return new Decision(false, true,
                    "Không kiểm tra được trạng thái dữ liệu chưa gửi.\n\n"
                            + "Cập nhật KHÔNG làm mất dữ liệu đã lưu trên máy. Bạn có muốn "
                            + "cập nhật không?");
        }
    }

    /**
     * Phần quyết định THUẦN TUÝ: không chạm Room, không ghi log, không đọc đồng hồ hệ thống.
     *
     * <p>Tách ra để kiểm chứng được bằng unit test thường — chính luật ở đây là thứ từng
     * khoá chết thiết bị ở bản cũ, nên nó phải có test.
     *
     * @param pending      tổng số bản ghi chưa gửi
     * @param failed       số bản ghi đã gửi lỗi (biết chắc là không tự đi được)
     * @param pendingSince thời điểm hàng đợi bắt đầu có bản ghi, 0 nếu đang trống
     */
    static Decision decide(int pending, int failed, long pendingSince, long now) {
        if (pending <= 0) return new Decision(true, false, null);

        // KHÔNG có nhánh chặn cứng nào ở đây. Mọi trường hợp còn dữ liệu chưa gửi đều cho
        // phép cập nhật sau khi người dùng xác nhận — xem phần đầu lớp về lý do.
        boolean waitedTooLong = pendingSince > 0 && now - pendingSince > STUCK_AFTER_MS;

        String detail;
        if (failed > 0) detail = ", trong đó " + failed + " bản ghi gửi lỗi";
        else if (waitedTooLong) detail = " và đã chờ quá 30 phút mà chưa gửi được";
        else detail = " (đang gửi)";

        return new Decision(false, true, "Còn " + pending + " bản ghi chưa gửi lên máy chủ"
                + detail + ".\n\n"
                + "Dữ liệu này KHÔNG mất khi cập nhật — vẫn nằm trong máy và sẽ được gửi "
                + "tiếp sau khi cài xong. Bản mới thường chính là bản sửa lỗi gửi dữ liệu.\n\n"
                + "Vẫn cập nhật?");
    }

    /** Bản ghi đã gửi và bị từ chối: biết chắc nó không tự đi được nếu không sửa gì. */
    private static int countFailed(List<RefuelItem> refuels) {
        if (refuels == null) return 0;
        int failed = 0;
        for (RefuelItem item : refuels) {
            if (item != null && item.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR) failed++;
        }
        return failed;
    }

    /**
     * Thời điểm hàng đợi bắt đầu có bản ghi chờ.
     *
     * <p>Ghi lại vì không bảng nào giữ được "đã chờ bao lâu": {@code dateUpdated} bị mỗi lần
     * thử gửi lại làm mới, nên một bản ghi kẹt cả ngày vẫn trông như vừa mới tạo.
     */
    private static long pendingSince(boolean hasPending) {
        try {
            SharedPreferences prefs = FMSApplication.getApplication()
                    .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);

            if (!hasPending) {
                prefs.edit().remove(PREF_PENDING_SINCE).apply();
                return 0;
            }

            long since = prefs.getLong(PREF_PENDING_SINCE, 0);
            if (since <= 0) {
                since = System.currentTimeMillis();
                prefs.edit().putLong(PREF_PENDING_SINCE, since).apply();
            }
            return since;
        } catch (Throwable t) {
            // Không đọc được mốc thì coi như chưa biết, để cờ lỗi tự quyết.
            return 0;
        }
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
