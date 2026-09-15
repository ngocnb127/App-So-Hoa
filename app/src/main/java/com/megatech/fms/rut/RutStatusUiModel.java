package com.megatech.fms.rut;

import androidx.annotation.Nullable;

/**
 * Dữ liệu đã chuẩn hoá cho popup. Response thô của router KHÔNG bao giờ đi thẳng lên UI:
 * tên field đổi theo model và firmware, để UI đọc thẳng là mỗi lần nâng cấp firmware lại
 * hỏng một chỗ khác nhau.
 */
public final class RutStatusUiModel {

    public RutConnectionState connectionState = RutConnectionState.UNKNOWN;

    @Nullable public String routerModel;
    @Nullable public String routerSerial;

    /**
     * Modem có đang kết nối hay không. Cố ý dùng {@code Boolean}: null nghĩa là ROUTER
     * KHÔNG NÓI, khác hẳn với false nghĩa là router bảo "đang mất kết nối". Ép null thành
     * false là biến một chỗ chưa đo được thành một khẳng định sai trên màn hình.
     */
    @Nullable public Boolean mobileConnected;
    @Nullable public String networkType;
    @Nullable public String operatorName;
    @Nullable public Integer activeSimSlot;

    @Nullable public Integer rssiDbm;
    @Nullable public Integer rsrpDbm;
    @Nullable public Integer rsrqDb;
    @Nullable public Integer sinrDb;

    /** Tên Wi-Fi đang nối — chỉ để hiển thị, KHÔNG dùng làm bằng chứng nhận dạng router. */
    @Nullable public String wifiSsid;

    /**
     * Có SIM trong router hay không. null = router không nói.
     *
     * <p>Tách khỏi {@link #mobileConnected}: "không có SIM" và "có SIM nhưng chưa bắt được
     * mạng" là hai sự cố khác nhau, cần hai cách xử lý khác nhau ngoài hiện trường.
     */
    @Nullable public Boolean simPresent;

    /**
     * Kết luận đầy đủ về SIM: không nhận thẻ / khoá PIN / có thẻ mà chưa vào được mạng /
     * bình thường. Xem {@link RutSimState} — ba sự cố đầu cần ba cách xử lý khác nhau ngoài
     * hiện trường, nên chúng phải là ba trạng thái chứ không phải một cờ nhị phân.
     */
    public RutSimState simState = RutSimState.UNKNOWN;

    /** Router trả lời và đúng là router mong đợi. */
    public boolean wifiConnectedToRut;
    /** Router trả lời nhưng KHÔNG phải router đã ghép cặp — ca nguy hiểm nhất. */
    public boolean wrongRouter;
    public boolean routerIdentityVerified;
    public boolean internetThroughRouter;

    @Nullable public Long lastUpdatedEpochMs;
    @Nullable public String errorMessage;

    /** modemId đọc được từ router — cần cho lệnh reboot modem, không được đoán. */
    @Nullable public String modemId;

    /**
     * Vì sao router không đưa được máy ra Internet. Chỉ có khi trạng thái là
     * {@link RutConnectionState#CONNECTED_TO_RUT_NO_INTERNET}.
     */
    @Nullable public RutInternetDiagnosis diagnosis;

    /**
     * Hạng sóng để hiển thị. RSRP là nguồn chuẩn; router chỉ trả RSSI thì xếp hạng theo
     * ngưỡng RIÊNG của RSSI — hiện "Không xác định" ngay cạnh một số đo có thật là bỏ phí
     * đúng thông tin người dùng cần.
     */
    public SignalRating signalRating() {
        if (rsrpDbm != null) return SignalRating.rateLteSignal(rsrpDbm);
        return SignalRating.rateRssiSignal(rssiDbm);
    }

    /**
     * Giá trị lớn hiển thị ở khối cường độ sóng. RSRP là số chính; thiếu nó thì hiện RSSI
     * NHƯNG phải ghi đúng tên chỉ số ở nhãn đi kèm.
     */
    @Nullable
    public Integer primarySignalDbm() {
        return rsrpDbm != null ? rsrpDbm : rssiDbm;
    }

    public boolean primarySignalIsRsrp() {
        return rsrpDbm != null;
    }
}
