package com.megatech.fms.rut;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Thông tin để nói chuyện với một router Teltonika RUT cụ thể.
 *
 * <p>KHÔNG hard-code ở bất kỳ đâu khác: địa chỉ, tài khoản và serial đều là dữ liệu triển
 * khai, mỗi xe một khác. Giá trị mặc định chỉ nằm ở {@link RutConfigStore} và người dùng
 * sửa được trong màn hình cài đặt.
 */
public final class RutConnectionConfig {

    /** Model hợp lệ phải bắt đầu bằng tiền tố này — RUTX50, RUT956, RUT241... */
    public static final String EXPECTED_MODEL_PREFIX = "RUT";

    public final String baseUrl;
    public final String username;
    public final String password;

    /** Serial của router đã ghép cặp. Null nghĩa là chưa ghép — xem {@link #isPaired()}. */
    @Nullable
    public final String expectedSerial;

    /**
     * Vân tay SHA-256 của chứng thư TLS router đang dùng, dạng hex.
     *
     * <p>Router RUT xuất xưởng với chứng chỉ TỰ KÝ nên hệ thống tin cậy của Android từ chối
     * nó. Cách xử lý đúng là ghim đúng vân tay của chính router đã ghép cặp; tuyệt đối không
     * dùng trust manager chấp nhận mọi chứng chỉ, vì như vậy là mở cửa cho mọi máy chủ khác
     * trong cùng mạng giả danh router.
     *
     * <p>Null thì chỉ tin hệ thống. Với router tự ký, lần kết nối sẽ báo lỗi bắt tay TLS và
     * thông báo hướng dẫn quản trị ghim chứng thư hoặc dùng http trong LAN.
     */
    @Nullable
    public final String pinnedCertificateSha256;

    public RutConnectionConfig(String baseUrl, String username, String password,
                               @Nullable String expectedSerial,
                               @Nullable String pinnedCertificateSha256) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.expectedSerial = emptyToNull(expectedSerial);
        this.pinnedCertificateSha256 = emptyToNull(pinnedCertificateSha256);
    }

    /** Đã ghép cặp với một router xác định hay chưa. */
    public boolean isPaired() {
        return expectedSerial != null;
    }

    public boolean isUsable() {
        return !baseUrl.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

    /** Ghép đường dẫn API, không để sinh ra hai dấu gạch chéo liền nhau. */
    @NonNull
    public String url(String path) {
        if (path.startsWith("/")) return baseUrl + path;
        return baseUrl + "/" + path;
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String url = value.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
