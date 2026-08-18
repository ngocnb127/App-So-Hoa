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
 * <h3>Chặn tạm thời, KHÔNG chặn vĩnh viễn</h3>
 *
 * <p>Bản trước chặn không điều kiện khi còn bản ghi chưa gửi, và tạo ra một bế tắc thật:
 * dữ liệu kẹt vì LỖI thì chờ bao lâu cũng không đi, trong khi bản sửa đúng lỗi đó lại nằm
 * sau đúng cánh cửa đang khoá. Càng kẹt lâu càng không cập nhật được, càng không cập nhật
 * được thì càng kẹt.
 *
 * <p>Tiền đề của việc chặn cũng cần nói đúng: cài đè KHÔNG xoá dữ liệu — Room còn nguyên.
 * Cái mất là thời gian, không phải số liệu. Vì vậy:
 *
 * <ul>
 *   <li>Dữ liệu vừa mới xếp hàng và chưa có lỗi: chặn thật, bảo người dùng chờ đồng bộ —
 *       chờ vài phút là xong.</li>
 *   <li>Dữ liệu ĐANG KẸT (có bản ghi báo lỗi gửi, hoặc chờ quá lâu): vẫn cảnh báo nhưng
 *       CHO PHÉP người dùng cập nhật, vì bản mới thường chính là thứ chữa được nó.</li>
 * </ul>
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

        boolean waitedTooLong = pendingSince > 0 && now - pendingSince > STUCK_AFTER_MS;
        boolean stuck = failed > 0 || waitedTooLong;

        if (!stuck) {
            // Hàng đợi còn mới và chưa có lỗi: nhiều khả năng đang gửi, chờ là xong. Đây là
            // trường hợp duy nhất chặn thật.
            return new Decision(false, false, "Còn " + pending
                    + " bản ghi chưa gửi lên máy chủ. Vui lòng đồng bộ xong rồi cập nhật.");
        }

        String detail = failed > 0
                ? "trong đó " + failed + " bản ghi gửi lỗi"
                : "đã chờ quá 30 phút mà không gửi được";

        return new Decision(false, true, "Còn " + pending + " bản ghi chưa gửi lên máy chủ, "
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
