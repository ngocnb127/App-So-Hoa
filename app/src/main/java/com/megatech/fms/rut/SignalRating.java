package com.megatech.fms.rut;

import androidx.annotation.Nullable;

/** Xếp hạng chất lượng sóng LTE theo RSRP. */
public enum SignalRating {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK,
    VERY_WEAK,
    UNKNOWN;

    /**
     * Ưu tiên RSRP: đó là công suất thu trên mỗi tài nguyên tham chiếu, không bị nhiễu và
     * băng thông làm lệch như RSSI. Thiếu RSRP thì trả UNKNOWN chứ KHÔNG quy đổi từ RSSI —
     * hai chỉ số đo hai thứ khác nhau, đổi lẫn là bịa số liệu.
     */
    public static SignalRating rateLteSignal(@Nullable Integer rsrpDbm) {
        if (rsrpDbm == null) return UNKNOWN;
        if (rsrpDbm >= -80) return EXCELLENT;
        if (rsrpDbm >= -90) return GOOD;
        if (rsrpDbm >= -100) return FAIR;
        if (rsrpDbm >= -110) return WEAK;
        return VERY_WEAK;
    }

    /**
     * Xếp hạng theo RSSI, dùng KHI VÀ CHỈ KHI router không trả RSRP.
     *
     * <p>Ngưỡng riêng, không quy đổi: RSSI gộp cả nhiễu và trải trên toàn băng thông nên
     * cùng một chất lượng sóng sẽ cho con số cao hơn RSRP chừng 20 dB. Dùng ngưỡng của RSRP
     * cho RSSI là biến sóng tốt thành "rất yếu" trên màn hình.
     */
    public static SignalRating rateRssiSignal(@Nullable Integer rssiDbm) {
        if (rssiDbm == null) return UNKNOWN;
        if (rssiDbm >= -65) return EXCELLENT;
        if (rssiDbm >= -75) return GOOD;
        if (rssiDbm >= -85) return FAIR;
        if (rssiDbm >= -95) return WEAK;
        return VERY_WEAK;
    }

    /**
     * Dải dBm có thật của RSRP. Ngoài dải này là router KHÔNG ĐO ĐƯỢC, không phải sóng khoẻ.
     *
     * <p>Vì sao phải chặn: các số "không đo được" của modem đều là số dương hoặc số 0 —
     * {@code AT+CSQ} trả 99 nghĩa là "không xác định", vài bản firmware trả 0, có bản trả
     * phần trăm 0–100. Mọi giá trị đó đều lớn hơn mọi ngưỡng dBm, nên lọt thẳng vào bậc
     * "Rất tốt": màn hình hiện đủ vạch đúng lúc ngoài xe không có sóng. Chặn ở đây thì
     * popup nói "Chưa bắt được sóng" — đúng việc người dùng cần biết.
     */
    static final int RSRP_MIN_DBM = -145;
    static final int RSRP_MAX_DBM = -44;

    /** Dải dBm có thật của RSSI; -51 là mức trần của thang CSQ, nới thêm chút cho chắc. */
    static final int RSSI_MIN_DBM = -121;
    static final int RSSI_MAX_DBM = -30;

    /** @return chính giá trị đó nếu là RSRP đo được thật, null nếu vô lý. */
    @Nullable
    public static Integer plausibleRsrp(@Nullable Integer rsrpDbm) {
        return inRange(rsrpDbm, RSRP_MIN_DBM, RSRP_MAX_DBM);
    }

    /** @return chính giá trị đó nếu là RSSI đo được thật, null nếu vô lý. */
    @Nullable
    public static Integer plausibleRssi(@Nullable Integer rssiDbm) {
        return inRange(rssiDbm, RSSI_MIN_DBM, RSSI_MAX_DBM);
    }

    @Nullable
    private static Integer inRange(@Nullable Integer value, int min, int max) {
        if (value == null) return null;
        return value >= min && value <= max ? value : null;
    }

    /** Số vạch hiển thị, 0..4. */
    public int bars() {
        switch (this) {
            case EXCELLENT: return 4;
            case GOOD: return 3;
            case FAIR: return 2;
            case WEAK: return 1;
            default: return 0;
        }
    }
}
