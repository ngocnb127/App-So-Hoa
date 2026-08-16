package com.megatech.fms.helpers.update;

import com.megatech.fms.helpers.AppVersionInfo;

/**
 * Trạng thái kiểm tra phiên bản. Bất biến — mỗi lần kiểm tra tạo một instance mới.
 *
 * Bất biến quan trọng nhất: metadata CHỈ tồn tại ở phase UPDATE_AVAILABLE.
 * Không có trạng thái nào khác mang theo thông tin phiên bản, nên không thể
 * vô tình tải/cài từ kết quả của một lần kiểm tra đã thất bại.
 *
 * CHECK_FAILED không bao giờ được coi là UP_TO_DATE: lỗi mạng không phải bằng
 * chứng rằng máy đang chạy bản mới nhất.
 */
public final class UpdateStatus {

    public enum Phase {
        /** Chưa kiểm tra lần nào trong process này. */
        IDLE,
        /** Đang gọi server. */
        CHECKING,
        /** Đã kiểm tra thành công, không có bản mới. */
        UP_TO_DATE,
        /** Đã kiểm tra thành công, có bản mới. */
        UPDATE_AVAILABLE,
        /** Không kiểm tra được (mạng/HTTP/định dạng). KHÔNG phải "không có bản mới". */
        CHECK_FAILED
    }

    public final Phase phase;
    /** Chỉ khác null khi phase == UPDATE_AVAILABLE. */
    public final AppVersionInfo metadata;
    /** 0 nếu chưa từng kiểm tra thành công. */
    public final long lastSuccessfulCheckAt;
    public final String error;

    private UpdateStatus(Phase phase, AppVersionInfo metadata, long lastSuccessfulCheckAt, String error) {
        this.phase = phase;
        this.metadata = metadata;
        this.lastSuccessfulCheckAt = lastSuccessfulCheckAt;
        this.error = error;
    }

    public static UpdateStatus idle() {
        return new UpdateStatus(Phase.IDLE, null, 0, null);
    }

    public UpdateStatus checking() {
        return new UpdateStatus(Phase.CHECKING, null, lastSuccessfulCheckAt, null);
    }

    public UpdateStatus upToDate(long now) {
        return new UpdateStatus(Phase.UP_TO_DATE, null, now, null);
    }

    public UpdateStatus updateAvailable(AppVersionInfo info, long now) {
        return new UpdateStatus(Phase.UPDATE_AVAILABLE, info, now, null);
    }

    /**
     * Giữ lại mốc kiểm tra thành công gần nhất để audit, nhưng KHÔNG giữ metadata:
     * UI không được hiển thị một bản cập nhật cũ như thể nó đang sẵn sàng cài.
     */
    public UpdateStatus checkFailed(String reason) {
        return new UpdateStatus(Phase.CHECK_FAILED, null, lastSuccessfulCheckAt, reason);
    }

    public boolean hasUpdate() {
        return phase == Phase.UPDATE_AVAILABLE && metadata != null;
    }
}
