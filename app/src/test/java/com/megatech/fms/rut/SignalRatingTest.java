package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Biên của thang xếp hạng sóng.
 *
 * <p>VÌ SAO đáng test: người ngoài hiện trường quyết định "đứng đây bơm được hay phải dời
 * xe" dựa vào số vạch này. Lệch một biên (dùng {@code >} thay cho {@code >=}) là mỗi ca
 * đúng trên ranh giới bị tụt một bậc — đủ để một vị trí sóng tốt bị báo là yếu và ngược
 * lại. Vì vậy kiểm cả giá trị ngay trên và ngay dưới từng biên, chứ không chỉ giá trị
 * giữa khoảng.
 */
public class SignalRatingTest {

    @Test
    public void rates_minus80_as_excellent_and_one_below_the_boundary_as_good() {
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateLteSignal(-79));
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateLteSignal(-80));
        assertEquals(SignalRating.GOOD, SignalRating.rateLteSignal(-81));
    }

    @Test
    public void rates_minus90_as_good_and_one_below_the_boundary_as_fair() {
        assertEquals(SignalRating.GOOD, SignalRating.rateLteSignal(-89));
        assertEquals(SignalRating.GOOD, SignalRating.rateLteSignal(-90));
        assertEquals(SignalRating.FAIR, SignalRating.rateLteSignal(-91));
    }

    @Test
    public void rates_minus100_as_fair_and_one_below_the_boundary_as_weak() {
        assertEquals(SignalRating.FAIR, SignalRating.rateLteSignal(-99));
        assertEquals(SignalRating.FAIR, SignalRating.rateLteSignal(-100));
        assertEquals(SignalRating.WEAK, SignalRating.rateLteSignal(-101));
    }

    @Test
    public void rates_minus110_as_weak_and_one_below_the_boundary_as_very_weak() {
        assertEquals(SignalRating.WEAK, SignalRating.rateLteSignal(-109));
        assertEquals(SignalRating.WEAK, SignalRating.rateLteSignal(-110));
        assertEquals(SignalRating.VERY_WEAK, SignalRating.rateLteSignal(-111));
    }

    /**
     * Thiếu RSRP thì phải nói "không biết". Trả về một hạng cụ thể (kể cả VERY_WEAK) là
     * bịa ra một phép đo chưa từng có, và người dùng sẽ dời xe vì một con số không tồn tại.
     */
    @Test
    public void reports_unknown_when_rsrp_is_missing() {
        assertEquals(SignalRating.UNKNOWN, SignalRating.rateLteSignal(null));
        assertEquals(0, SignalRating.UNKNOWN.bars());
    }

    // ------------------------------------------------------------------ thang RSSI

    /**
     * VÌ SAO thang RSSI phải có biên riêng: RSSI gộp cả nhiễu và trải trên toàn băng thông
     * nên cùng một chất lượng sóng cho con số cao hơn RSRP chừng 20 dB. Nếu ai đó "gọn hoá"
     * bằng cách cho hàm này gọi lại {@code rateLteSignal}, mọi ngưỡng dưới đây lệch một bậc
     * hoặc hơn và người dùng nhìn nhầm chất lượng sóng đang có.
     */
    @Test
    public void rates_rssi_minus65_as_excellent_and_one_below_the_boundary_as_good() {
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateRssiSignal(-64));
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateRssiSignal(-65));
        assertEquals(SignalRating.GOOD, SignalRating.rateRssiSignal(-66));
    }

    @Test
    public void rates_rssi_minus75_as_good_and_one_below_the_boundary_as_fair() {
        assertEquals(SignalRating.GOOD, SignalRating.rateRssiSignal(-74));
        assertEquals(SignalRating.GOOD, SignalRating.rateRssiSignal(-75));
        assertEquals(SignalRating.FAIR, SignalRating.rateRssiSignal(-76));
    }

    @Test
    public void rates_rssi_minus85_as_fair_and_one_below_the_boundary_as_weak() {
        assertEquals(SignalRating.FAIR, SignalRating.rateRssiSignal(-84));
        assertEquals(SignalRating.FAIR, SignalRating.rateRssiSignal(-85));
        assertEquals(SignalRating.WEAK, SignalRating.rateRssiSignal(-86));
    }

    @Test
    public void rates_rssi_minus95_as_weak_and_one_below_the_boundary_as_very_weak() {
        assertEquals(SignalRating.WEAK, SignalRating.rateRssiSignal(-94));
        assertEquals(SignalRating.WEAK, SignalRating.rateRssiSignal(-95));
        assertEquals(SignalRating.VERY_WEAK, SignalRating.rateRssiSignal(-96));
    }

    /**
     * Đo thật trên RUT955 không cắm SIM: {@code signal} trả chuỗi "N/A", về tới đây là null.
     * Không có số đo thì phải nói "không biết", không được suy ra VERY_WEAK.
     */
    @Test
    public void reports_unknown_when_rssi_is_missing() {
        assertEquals(SignalRating.UNKNOWN, SignalRating.rateRssiSignal(null));
    }

    /**
     * Hai thang KHÔNG được trùng nhau. Cùng một dải giá trị mà cho cùng kết quả nghĩa là
     * một trong hai hàm đã bị nối vào hàm kia — đúng lỗi mà cặp ngưỡng riêng sinh ra để
     * tránh.
     */
    @Test
    public void keeps_the_rssi_scale_stricter_than_the_rsrp_scale() {
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateLteSignal(-80));
        assertEquals(SignalRating.FAIR, SignalRating.rateRssiSignal(-80));

        assertEquals(SignalRating.WEAK, SignalRating.rateLteSignal(-105));
        assertEquals(SignalRating.VERY_WEAK, SignalRating.rateRssiSignal(-105));
    }

    @Test
    public void maps_each_rating_to_a_distinct_bar_count() {
        assertEquals(4, SignalRating.EXCELLENT.bars());
        assertEquals(3, SignalRating.GOOD.bars());
        assertEquals(2, SignalRating.FAIR.bars());
        assertEquals(1, SignalRating.WEAK.bars());
        assertEquals(0, SignalRating.VERY_WEAK.bars());
    }
}
