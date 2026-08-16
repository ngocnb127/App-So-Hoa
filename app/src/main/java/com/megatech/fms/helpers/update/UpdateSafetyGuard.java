package com.megatech.fms.helpers.update;

import android.util.Log;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.DataRepository;

import java.util.List;

/**
 * Quyết định thời điểm ĐƯỢC PHÉP CÀI đặt bản mới.
 *
 * Cài đặt làm hệ điều hành kết thúc tiến trình ứng dụng. Nếu điều đó xảy ra khi máy còn
 * dữ liệu nghiệp vụ chưa gửi lên server, dữ liệu đó nằm lại trong Room và chỉ được đồng bộ
 * ở lần mở app sau — trong khoảng đó FMS không nhìn thấy sản lượng đã tra nạp.
 *
 * Chỉ chặn bước CÀI. Việc kiểm tra phiên bản và tải file vẫn diễn ra bình thường.
 */
public final class UpdateSafetyGuard {

    private static final String LOG_TAG = "FMS_UPDATE";

    public static final class Decision {
        public final boolean safe;
        /** Lý do đã ở dạng hiển thị được cho người dùng; null khi safe. */
        public final String reason;

        private Decision(boolean safe, String reason) {
            this.safe = safe;
            this.reason = reason;
        }
    }

    private UpdateSafetyGuard() {
    }

    /**
     * Gọi TRÊN LUỒNG NỀN — truy vấn Room.
     *
     * "Chưa đồng bộ" xác định bằng cờ isLocalModified của các bảng nghiệp vụ.
     * Cố ý KHÔNG tính LogEntry: đó là dữ liệu telemetry, gần như luôn có bản ghi chờ,
     * tính vào sẽ chặn cập nhật vĩnh viễn.
     */
    public static Decision canInstallNow() {
        try {
            DataRepository repo = FMSApplication.getApplication().getRepository();

            int pending = 0;
            pending += size(repo.getAllModifiedRefuel());
            pending += size(repo.getModifiedReceipt());
            pending += size(repo.getModifiedInvoice());
            pending += size(repo.getModifiedTruckFuel());
            pending += size(repo.getModifiedBM2503());
            pending += size(repo.getModifiedBM2504());
            pending += size(repo.getModifiedBM2505());
            pending += size(repo.getModifiedBM2508());
            pending += size(repo.getModifiedCheckTrucks());
            pending += size(repo.getModifiedReview());

            if (pending > 0) {
                Log.w(LOG_TAG, "Chặn cài đặt: còn " + pending + " bản ghi chưa đồng bộ");
                return new Decision(false, "Còn " + pending +
                        " bản ghi chưa gửi lên máy chủ. Vui lòng đồng bộ xong rồi cập nhật.");
            }
            return new Decision(true, null);

        } catch (Throwable t) {
            // Không đọc được trạng thái dữ liệu thì không dám cho cài: nếu ở đây bỏ qua,
            // một lỗi Room sẽ âm thầm biến thành mất dữ liệu nghiệp vụ.
            Log.e(LOG_TAG, "Không kiểm tra được dữ liệu chưa đồng bộ", t);
            return new Decision(false, "Không kiểm tra được trạng thái dữ liệu. Thử lại sau.");
        }
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
