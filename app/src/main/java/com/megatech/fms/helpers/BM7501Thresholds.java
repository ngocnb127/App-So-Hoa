package com.megatech.fms.helpers;

/**
 * Ngưỡng tham chiếu cho BM 75.01 — <b>chỉ dùng để CẢNH BÁO</b>.
 *
 * <p>Theo quyết định đã chốt (§14 mục 2): nhiệt độ và KLR bắt buộc là số hợp lệ, nhưng
 * khoảng giá trị chỉ cảnh báo và phải cấu hình được. Chỉ được chuyển thành lỗi chặn sau khi
 * bộ phận nghiệp vụ/KTCL phê duyệt bằng văn bản — khi đó sửa {@link #blocking} thành true.
 *
 * <p>Các số mặc định dưới đây là <b>tham chiếu chưa được nghiệp vụ duyệt</b>, cố ý để rộng.
 */
public final class BM7501Thresholds {

    private BM7501Thresholds() {
    }

    /** Bật lên khi nghiệp vụ đã duyệt ngưỡng bằng văn bản. */
    private static boolean blocking = false;

    private static double minTempC = -40d;
    private static double maxTempC = 60d;

    /** Jet A-1 tham chiếu; chưa được KTCL duyệt nên chỉ cảnh báo. */
    private static double minDensityKgM3 = 775d;
    private static double maxDensityKgM3 = 840d;

    public static boolean isBlocking() {
        return blocking;
    }

    public static void setBlocking(boolean value) {
        blocking = value;
    }

    public static boolean isTempInRange(double tempC) {
        return tempC >= minTempC && tempC <= maxTempC;
    }

    public static boolean isDensityInRange(double densityKgM3) {
        return densityKgM3 >= minDensityKgM3 && densityKgM3 <= maxDensityKgM3;
    }

    public static void setTempRange(double min, double max) {
        minTempC = min;
        maxTempC = max;
    }

    public static void setDensityRange(double min, double max) {
        minDensityKgM3 = min;
        maxDensityKgM3 = max;
    }

    public static double getMinTempC() { return minTempC; }
    public static double getMaxTempC() { return maxTempC; }
    public static double getMinDensityKgM3() { return minDensityKgM3; }
    public static double getMaxDensityKgM3() { return maxDensityKgM3; }

    /** Khôi phục mặc định — dùng cho test. */
    public static void resetDefaults() {
        blocking = false;
        minTempC = -40d;
        maxTempC = 60d;
        minDensityKgM3 = 775d;
        maxDensityKgM3 = 840d;
    }
}
