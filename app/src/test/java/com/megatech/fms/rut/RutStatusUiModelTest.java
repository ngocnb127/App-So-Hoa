package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * VÌ SAO đáng test: RSRP và RSSI đo hai đại lượng khác nhau và có thang khác nhau. Popup
 * chỉ hiện MỘT con số lớn kèm một nhãn. Nếu số lấy từ RSSI mà nhãn ghi RSRP, người dùng
 * đọc được một phép đo hợp lệ về mặt hình thức nhưng sai về bản chất — và đó chính là con
 * số họ dùng để quyết định có dời xe hay không. Cặp {@code primarySignalDbm()} /
 * {@code primarySignalIsRsrp()} phải luôn nhất quán với nhau.
 */
public class RutStatusUiModelTest {

    @Test
    public void shows_rsrp_as_the_primary_signal_when_the_router_reports_it() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.rsrpDbm = -95;
        model.rssiDbm = -61;

        assertEquals(Integer.valueOf(-95), model.primarySignalDbm());
        assertTrue(model.primarySignalIsRsrp());
    }

    @Test
    public void falls_back_to_rssi_and_says_so_when_rsrp_is_missing() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.rsrpDbm = null;
        model.rssiDbm = -61;

        assertEquals(Integer.valueOf(-61), model.primarySignalDbm());
        assertFalse("Số hiện lên là RSSI thì nhãn không được ghi RSRP",
                model.primarySignalIsRsrp());
    }

    @Test
    public void reports_no_signal_value_at_all_when_neither_measurement_arrived() {
        RutStatusUiModel model = new RutStatusUiModel();

        assertNull(model.primarySignalDbm());
        assertFalse(model.primarySignalIsRsrp());
    }

    /**
     * Chỉ có RSSI thì VẪN xếp hạng được — nhưng bằng thang RIÊNG của RSSI.
     *
     * <p>VÌ SAO đáng test: hiện "Không xác định" ngay cạnh một số đo có thật là bỏ phí đúng
     * thông tin người dùng cần. Đổi lại, xếp hạng phải đi qua {@code rateRssiSignal}: RSSI
     * gộp nhiễu và trải trên toàn băng thông nên cao hơn RSRP chừng 20 dB.
     */
    @Test
    public void rates_the_signal_from_rssi_when_rsrp_is_missing() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.rssiDbm = -61;

        assertEquals(Integer.valueOf(-61), model.primarySignalDbm());
        assertFalse(model.primarySignalIsRsrp());
        assertEquals(SignalRating.EXCELLENT, model.signalRating());
    }

    /**
     * Khẳng định quan trọng nhất của cả file: RSSI KHÔNG BAO GIỜ được chấm theo ngưỡng của
     * RSRP. Hai thang lệch nhau chừng 20 dB, nên dùng nhầm thang là đẩy cả dải RSSI đời
     * thường (-70…-90 dBm) xuống đáy bảng và báo "sóng rất yếu" ở đúng chỗ sóng đang tốt.
     */
    @Test
    public void never_rates_rssi_on_the_rsrp_scale() {
        RutStatusUiModel rssiOnly = new RutStatusUiModel();
        rssiOnly.rssiDbm = -70;

        assertEquals(SignalRating.GOOD, rssiOnly.signalRating());
        assertNotEquals(SignalRating.VERY_WEAK, rssiOnly.signalRating());

        // Cùng dải đó chấm theo thang RSRP sẽ ra hạng khác hẳn — bằng chứng hai thang là
        // hai thứ riêng biệt, không quy đổi được cho nhau.
        assertNotEquals(SignalRating.GOOD, SignalRating.rateLteSignal(-70));
        assertEquals(SignalRating.EXCELLENT, SignalRating.rateLteSignal(-70));
    }

    /**
     * Có RSRP thì RSRP thắng, kể cả khi RSSI cùng có mặt và sẽ cho ra hạng đẹp hơn — nếu
     * không, một RSSI mạnh sẽ che mất một RSRP yếu, đúng lúc cần biết là sóng đang kém.
     */
    @Test
    public void prefers_the_rsrp_scale_whenever_rsrp_is_present() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.rsrpDbm = -105;
        model.rssiDbm = -61;

        assertTrue(model.primarySignalIsRsrp());
        assertEquals(SignalRating.WEAK, model.signalRating());
    }

    /**
     * Ca đo thật trên RUT955 không cắm SIM: rsrp/rssi đều là "N/A" nên về tới model là
     * null. Không có số đo nào thì hạng phải là UNKNOWN, không được rơi xuống VERY_WEAK.
     */
    @Test
    public void reports_unknown_rating_when_the_router_sent_no_measurement_at_all() {
        assertEquals(SignalRating.UNKNOWN, new RutStatusUiModel().signalRating());
    }

    /** Mặc định phải là "chưa biết" và "chưa xác minh", không phải trạng thái lạc quan. */
    @Test
    public void starts_out_unverified_so_nothing_is_claimed_before_it_is_measured() {
        RutStatusUiModel model = new RutStatusUiModel();

        assertEquals(RutConnectionState.UNKNOWN, model.connectionState);
        assertFalse(model.routerIdentityVerified);
        assertFalse(model.internetThroughRouter);
        assertFalse(model.wifiConnectedToRut);
        assertFalse(model.connectionState.allowsReboot());
    }
}
