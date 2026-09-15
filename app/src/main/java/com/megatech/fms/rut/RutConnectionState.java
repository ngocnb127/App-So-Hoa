package com.megatech.fms.rut;

/** Kết luận về đường Internet mà điện thoại đang thực sự dùng. */
public enum RutConnectionState {
    /** Chưa có kết luận — đang đo. */
    CHECKING,
    /** Đã chứng minh: Internet đi qua đúng router RUT đã ghép cặp. */
    VERIFIED_THROUGH_RUT,
    /** Nói chuyện được với router nhưng router không ra được Internet. */
    CONNECTED_TO_RUT_NO_INTERNET,
    /** Wi-Fi router có đó nhưng hệ điều hành đang đi bằng 4G/5G của chính điện thoại. */
    PHONE_USING_CELLULAR,
    /** Không ở trong Wi-Fi của router. */
    NOT_CONNECTED_TO_RUT,
    /** Sai tài khoản quản trị router. */
    AUTHENTICATION_FAILED,
    /** Không gọi được router, hoặc router trả lời nhưng không phải router đã ghép cặp. */
    ROUTER_UNREACHABLE,
    UNKNOWN;

    /**
     * Chỉ được phép khởi động lại router khi CHẮC CHẮN đang nói chuyện với đúng router.
     * Reboot nhầm một thiết bị khác trong mạng là hỏng việc của người khác.
     */
    public boolean allowsReboot() {
        return this == VERIFIED_THROUGH_RUT || this == CONNECTED_TO_RUT_NO_INTERNET;
    }
}
