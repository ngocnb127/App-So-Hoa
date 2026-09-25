package com.megatech.fms.helpers;

/**
 * Xử lý kết quả một lần đẩy phiếu BM 75.01 lên server.
 *
 * <p>Bảng mã lỗi lấy từ phản hồi của đội API ngày 2026-09-23
 * (`docs/API-BM7501-PHAN-HOI-BACKEND.md` §2). Tách ra lớp thuần để test được trên JVM: quyết
 * định sai ở đây thì hoặc phiếu quay vòng retry vô hạn, hoặc phiếu đã in bị bỏ quên.
 */
public final class BM7501SyncPolicy {

    private BM7501SyncPolicy() {
    }

    public enum Action {
        /** Server đã lưu: đánh dấu đã đồng bộ. */
        SYNCED,
        /** Lỗi tạm (mất mạng, 5xx, endpoint chưa deploy): để outbox thử lại. */
        RETRY,
        /** Lỗi không tự hết: dừng retry, chờ nhân viên xử lý. */
        STOP,
        /** Token hỏng: đăng nhập lại rồi thử lại. */
        REAUTH
    }

    /**
     * @param httpCode mã HTTP; {@code 0} nghĩa là không gọi được (mất mạng, timeout)
     * @param success  cờ {@code Success} trong thân phản hồi
     */
    public static Action decide(int httpCode, boolean success) {
        if (httpCode == 200) {
            // Message khác null chỉ là cảnh báo đối soát — phiếu VẪN đã lưu, không gửi lại.
            return success ? Action.SYNCED : Action.STOP;
        }
        if (httpCode == 401) return Action.REAUTH;

        // 409: trùng số phiếu hoặc mẻ đã có phiếu khác. 400: dữ liệu phía app sai.
        // Cả hai đều không tự hết khi gửi lại.
        if (httpCode == 409 || httpCode == 400) return Action.STOP;

        // 404 khi endpoint chưa lên production — đội API dặn cứ giữ trong outbox và thử lại.
        return Action.RETRY;
    }

    /** Cảnh báo đối soát: phiếu đã lưu nhưng server thấy dữ liệu lệch. */
    public static boolean hasWarning(int httpCode, boolean success, String message) {
        return httpCode == 200 && success && message != null && !message.trim().isEmpty();
    }
}
