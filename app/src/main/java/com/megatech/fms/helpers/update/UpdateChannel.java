package com.megatech.fms.helpers.update;

import com.megatech.fms.BuildConfig;

/**
 * Nguồn duy nhất quyết định: updater có chạy không, đọc metadata ở đâu, tải APK nào.
 *
 * Không Activity nào được tự ghép URL cập nhật. Mọi thay đổi về endpoint hoặc quy ước
 * đặt tên APK chỉ sửa ở đây.
 *
 * Kênh được xác định bởi BuildConfig.UPDATE_CHANNEL, KHÔNG phải THERMAL_PRINTER
 * (cờ đó mô tả loại máy in, không phải kênh phát hành). Mặc định của mọi build type
 * là null = updater tắt; chỉ release và thermal bật tường minh trong build.gradle.
 */
public final class UpdateChannel {

    public static final String RELEASE = "release";
    public static final String THERMAL = "thermal";

    private UpdateChannel() {
    }

    /** Updater chỉ hoạt động trên các kênh đã được duyệt. debug/demo/demo_thermal luôn tắt. */
    public static boolean isEnabled() {
        String channel = BuildConfig.UPDATE_CHANNEL;
        return RELEASE.equals(channel) || THERMAL.equals(channel);
    }

    public static String current() {
        return BuildConfig.UPDATE_CHANNEL;
    }

    /**
     * Metadata legacy dạng text: "versionCode-yyyyMMdd.patch".
     *
     * Bản 104 dùng chung một file cho cả badge lẫn dialog. Server vẫn tiếp tục publish
     * version.txt / thermal.txt cho các bản < 103 — client 104 không đọc chúng nữa.
     */
    public static String metadataUrl() {
        String file = THERMAL.equals(current())
                ? "files/thermalUpdate.txt"
                : "files/versionUpdate.txt";
        return joinUrl(BuildConfig.API_BASE_URL, file);
    }

    /**
     * Tên APK theo kênh.
     *
     * Kênh thermal dùng tiền tố "thermal-" chứ không phải "fms-thermal-" mà Gradle sinh ra:
     * đây là quy ước mà các bản đã phát hành ngoài hiện trường đang dùng, và server đang
     * publish sẵn cả hai tên. Giữ nguyên để không cắt đường nâng cấp của chúng.
     */
    public static String apkUrl(String rawVersion) {
        String prefix = THERMAL.equals(current()) ? "thermal-" : "fms-release-";
        return joinUrl(BuildConfig.API_BASE_URL, "files/" + prefix + rawVersion + ".apk");
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (path.startsWith("/")) path = path.substring(1);
        return base + "/" + path;
    }
}
