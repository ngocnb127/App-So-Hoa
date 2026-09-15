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
