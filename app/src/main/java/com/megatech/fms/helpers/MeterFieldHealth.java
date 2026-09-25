package com.megatech.fms.helpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sức khoẻ ĐỌC SỐ CỦA TỪNG TRƯỜNG đồng hồ.
 *
 * <p>Đồng hồ LCR và TCS đều đọc theo từng trường riêng lẻ, nên một trường có thể chết trong
 * khi trường khác vẫn về đều. Vòng canh độ tươi sẵn có ở màn hình tra nạp chỉ giữ MỘT mốc
 * chung cho mọi trường ({@code lastMeterDataAt}), nên ca "Gross vẫn chạy mà Totalizer đã
 * chết" — đúng ca sinh ra số đồng hồ đầu âm — không bao giờ bị bắt.
 *
 * <p>Lớp này thuần Java, đồng hồ TIÊM ĐƯỢC qua tham số {@code nowMs} để kiểm thử tất định.
 * Phía gọi phải truyền {@code SystemClock.elapsedRealtime()} chứ KHÔNG phải
 * {@code System.currentTimeMillis()}: máy bảng đồng bộ NTP, một bước nhảy tiến sẽ làm mọi
 * phép tính theo thời gian trôi mất tác dụng đúng lúc cần nhất.
 *
 * <p>Lớp chỉ ĐO và KỂ. Nó không chặn, không dừng đo, không bỏ gói dữ liệu; việc hiển thị do
 * đúng một người viết là vòng canh của màn hình tra nạp quyết định.
 */
public final class MeterFieldHealth {

    /** Các trường được theo dõi; tên trùng với FIELD_CHANGE của LCR để đọc log dễ đối chiếu. */
    public enum Field { GROSSQTY, TOTALIZER, TEMPERATURE, DENSITY, TICKET }

    /**
     * Không có số mới quá lâu thì coi là trường đã chết.
     *
     * <p>Chu kỳ đọc LIVE là 1 giây và STATIC là 5 giây, nên 15 giây là đã lỡ ít nhất ba vòng
     * của trường chậm nhất — đủ chắc để không báo động vì một nhịp mạng, mà vẫn kịp cho người
     * vận hành biết trước khi kết thúc mẻ.
     */
    public static final long STALE_AFTER_MS = 15_000L;

    /** Vừa mở mẻ thì hàng đợi đăng ký trường còn đang chạy; đừng báo động trong lúc đó. */
    public static final long BATCH_GRACE_MS = 20_000L;

    /**
     * Chốt ghi vết: quyết định KHI NÀO nhật ký phải ghi một dòng mới.
     *
     * <p>Vòng canh chạy mỗi 2 giây; ghi lại y hệt mỗi nhịp chỉ làm phình tệp nhật ký mà
     * không thêm thông tin. Chỉ ghi khi trạng thái ĐỔI.
     *
     * <p>Lớp này KHÔNG điều khiển gì trên màn hình. Phép đo theo từng trường cố ý không còn
     * bật cảnh báo cho người vận hành — xem ghi chú ở vòng canh của màn hình tra nạp.
     */
    public static final class WarningLatch {
        private boolean warning;

        /** Trạng thái đang ghi nhận. */
        public boolean isWarning() {
            return warning;
        }

        /** @return true khi trạng thái vừa đổi — chỉ lúc đó mới cần ghi một dòng nhật ký. */
        public boolean update(boolean warn) {
            if (warn == warning) return false;
            warning = warn;
            return true;
        }

        /** Phiên đo mới (nối lại đồng hồ / vào lại màn hình): quên trạng thái cũ. */
        public void beginSession() {
            warning = false;
        }
    }

    private static final MeterFieldHealth SHARED = new MeterFieldHealth();

    /**
     * Bản dùng chung cho lớp đọc thiết bị và màn hình tra nạp.
     *
     * <p>{@code LCRReader} vốn đã là singleton tĩnh dùng chung giữa các màn hình, nên trạng
     * thái đo cũng phải sống cùng vòng đời đó thì mới ghép được điểm đo với điểm hiển thị.
     */
    public static MeterFieldHealth shared() {
        return SHARED;
    }

    private static final class State {
        long lastReadAt;        // lần cuối ĐỌC + PHÂN TÍCH thành công
        long lastAcceptedAt;    // lần cuối giá trị đọc được thực sự ĐƯỢC NHẬN vào mô hình
        long lastFilteredAt;    // lần cuối đọc được nhưng bị bộ lọc ngưỡng loại
        long lastFailAt;        // lần cuối đọc / đăng ký / phân tích hỏng
        long firstSignalAt;     // tín hiệu đầu tiên bất kỳ, để kết luận được cả khi chưa từng đọc nổi
        long filteredStreakAt;  // mốc bắt đầu chuỗi bị loại LIÊN TIẾP hiện tại, 0 nếu không có
        int reads;
        int accepted;
        int filtered;
        int fails;
        int consecutiveFails;
        int consecutiveFiltered;
        boolean retired;        // đã bị gỡ khỏi hàng đợi đọc: im lặng là ĐÚNG, không phải chết
    }

    private final Map<Field, State> states = new EnumMap<>(Field.class);
    private long sessionStartedAt;

    public MeterFieldHealth() {
        for (Field f : Field.values()) states.put(f, new State());
    }

    /**
     * Mở một phiên đo mới (nối thiết bị / vào lại màn hình tra nạp): xoá số đếm cũ và bật
     * khoảng ân hạn. Không đụng tới bất kỳ số liệu mẻ nào.
     */
    public synchronized void beginSession(long nowMs) {
        for (State s : states.values()) {
            s.lastReadAt = 0;
            s.lastAcceptedAt = 0;
            s.lastFilteredAt = 0;
            s.lastFailAt = 0;
            s.firstSignalAt = 0;
            s.filteredStreakAt = 0;
            s.reads = 0;
            s.accepted = 0;
            s.filtered = 0;
            s.fails = 0;
            s.consecutiveFails = 0;
            s.consecutiveFiltered = 0;
            s.retired = false;
        }
        sessionStartedAt = nowMs;
    }

    /**
     * Đọc và phân tích THÀNH CÔNG — gói dữ liệu về tới nơi và đọc được số.
     *
     * <p>CHÚ Ý: điểm đo này nằm TRƯỚC bộ lọc ngưỡng, nên "đọc được" KHÔNG đồng nghĩa "giá trị
     * được nhận". Chuỗi bị loại liên tiếp do đó KHÔNG được xoá ở đây — xem {@link #markAccepted}.
     */
    public synchronized void markRead(Field field, long nowMs) {
        State s = state(field);
        if (s == null) return;
        s.retired = false;
        s.lastReadAt = nowMs;
        s.reads++;
        s.consecutiveFails = 0;
        if (s.firstSignalAt == 0) s.firstSignalAt = nowMs;
    }

    /**
     * Giá trị đọc được đã QUA bộ lọc và được ghi vào mô hình.
     *
     * <p>Đây mới là tín hiệu chứng minh trường thực sự đang theo kịp thiết bị; chỉ nó mới xoá
     * chuỗi bị loại liên tiếp.
     */
    public synchronized void markAccepted(Field field, long nowMs) {
        State s = state(field);
        if (s == null) return;
        s.retired = false;
        s.lastAcceptedAt = nowMs;
        s.accepted++;
        s.consecutiveFiltered = 0;
        s.filteredStreakAt = 0;
        if (s.firstSignalAt == 0) s.firstSignalAt = nowMs;
    }

    /**
     * Đọc được nhưng giá trị bị bộ lọc ngưỡng loại.
     *
     * <p>TÁCH HẲN khỏi {@link #markRead}: nhánh GROSSQTY chỉ nhận giá trị {@code > 0}, nên
     * gắn "khoẻ" vào "giá trị được chấp nhận" cho MỌI trường sẽ báo động giả cho ca phổ biến
     * nhất — đã vào màn hình mà chưa mở vòi. Vì vậy chuỗi bị loại chỉ sinh cảnh báo ở những
     * trường mà {@link #filteringMeansStale} cho phép.
     */
    public synchronized void markFiltered(Field field, long nowMs) {
        State s = state(field);
        if (s == null) return;
        s.retired = false;
        s.lastFilteredAt = nowMs;
        s.filtered++;
        s.consecutiveFiltered++;
        if (s.filteredStreakAt == 0) s.filteredStreakAt = nowMs;
        if (s.firstSignalAt == 0) s.firstSignalAt = nowMs;
    }

    /**
     * Trường nào mà việc bị bộ lọc loại LIÊN TIẾP đủ lâu đã đủ để coi là hỏng.
     *
     * <p>Chỉ TOTALIZER. Đây đúng ca hiện trường: gói vẫn về đều nên {@code lastReadAt} luôn
     * tươi, nhưng mọi giá trị đều lệch quá ngưỡng {@code 1000} nên số tổng ĐÓNG BĂNG trong khi
     * Gross vẫn tăng — hệ quả là số đồng hồ đầu mẻ tụt dần. Trường này ở trạng thái nghỉ vẫn
     * được nhận giá trị (không có điều kiện {@code > 0}), nên không có nguy cơ báo động giả
     * lúc chưa mở vòi. GROSSQTY giữ nguyên luật cũ chính vì lý do ngược lại.
     */
    private static boolean filteringMeansStale(Field field) {
        return field == Field.TOTALIZER;
    }

    /** Đọc / đăng ký / phân tích hỏng. */
    public synchronized void markFail(Field field, long nowMs) {
        State s = state(field);
        if (s == null) return;
        s.retired = false;
        s.lastFailAt = nowMs;
        s.fails++;
        s.consecutiveFails++;
        if (s.firstSignalAt == 0) s.firstSignalAt = nowMs;
    }

    /**
     * Trường đã được GỠ khỏi hàng đợi đọc — từ giờ im tiếng là đúng, không phải hỏng.
     *
     * <p>Ca thật: {@code TICKETNUMBER} không nằm trong hàng đợi đọc định kỳ. Nó chỉ được
     * đăng ký lúc chốt mẻ, về đúng MỘT lần, rồi {@code LCRReader.onDataChanged} gỡ luôn
     * field khỏi SDK. Trước đây 15 giây sau lần đọc duy nhất đó trường bị kết luận là chết,
     * và cảnh báo bật lên trong khi Gross với Tổng vẫn chảy hoàn toàn bình thường.
     *
     * <p>Không xoá số đếm: nhật ký đối soát sau ca vẫn phải thấy trường đó đã đọc được mấy
     * lần. Một tín hiệu mới bất kỳ ({@code markRead} / {@code markFail} …) sẽ tự mở lại phép
     * đo, vì lúc đó trường đã được đăng ký lại.
     */
    public synchronized void markRetired(Field field) {
        State s = state(field);
        if (s == null) return;
        s.retired = true;
    }

    /**
     * Trường đã ngừng cho số hay chưa.
     *
     * <p>Trả về false khi chưa có tín hiệu nào: "chưa biết" không phải "đã hỏng", báo động
     * lúc đó chỉ làm người dùng quen với việc bỏ qua cảnh báo.
     */
    public synchronized boolean isStale(Field field, long nowMs) {
        State s = state(field);
        if (s == null) return false;
        if (s.retired) return false;
        if (sessionStartedAt > 0 && nowMs - sessionStartedAt < BATCH_GRACE_MS) return false;

        // Bị bộ lọc loại liên tiếp quá lâu: gói vẫn về nhưng giá trị hiển thị đã đứng im,
        // xét trước điều kiện theo lastReadAt vì trong ca này lastReadAt luôn tươi.
        if (filteringMeansStale(field) && s.filteredStreakAt > 0
                && nowMs - s.filteredStreakAt > STALE_AFTER_MS) return true;

        if (s.lastReadAt <= 0) {
            // Chưa từng đọc nổi: chỉ kết luận khi đã có ít nhất một tín hiệu hỏng / bị loại.
            if (s.firstSignalAt <= 0) return false;
            return nowMs - s.firstSignalAt > STALE_AFTER_MS;
        }
        return nowMs - s.lastReadAt > STALE_AFTER_MS;
    }

    /**
     * MỌI trường đang chết, theo thứ tự khai báo của {@link Field}.
     *
     * <p>Chỉ dùng để GHI VẾT và để {@link #resolveStartNumber} giữ số đồng hồ đầu mẻ. Không
     * dùng để bật cảnh báo trên màn hình.
     */
    public synchronized List<Field> staleFields(long nowMs) {
        List<Field> result = new ArrayList<>();
        for (Field f : Field.values())
            if (isStale(f, nowMs)) result.add(f);
        return result;
    }

    /** Một dòng tổng kết để ghi vào nhật ký đối soát sau ca. */
    public synchronized String snapshotForLog() {
        StringBuilder sb = new StringBuilder();
        for (Field f : Field.values()) {
            State s = states.get(f);
            if (s == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%s=r%d/a%d/f%d/x%d",
                    f, s.reads, s.accepted, s.filtered, s.fails));
            // Phân biệt "đã gỡ khỏi hàng đợi" với "chết": hai ca này nhìn số đếm y hệt nhau.
            if (s.retired) sb.append("/nghi");
        }
        return sb.toString();
    }

    private State state(Field field) {
        return field == null ? null : states.get(field);
    }

    /**
     * Số đồng hồ đầu mẻ được phép ghi xuống Room.
     *
     * <p>Màn hình tra nạp tính {@code StartNumber = EndMeter − GrossQty} mỗi nhịp. Khi
     * TOTALIZER chết mà GROSSQTY vẫn tăng, hiệu này giảm dần và có thể ÂM, rồi được ghi
     * thẳng xuống Room. Ở đây chỉ GIỮ NGUYÊN giá trị cũ trong hai ca đó — vẫn hiển thị, vẫn
     * lưu, vẫn cho kết thúc mẻ; chỉ không đạp một giá trị hỏng đè lên giá trị còn dùng được.
     *
     * @param previousStart  giá trị đang có trên phiếu
     * @param endMeter       số tổng đọc từ đồng hồ
     * @param gross          sản lượng mẻ
     * @param totalizerStale số tổng đang đứng hay không
     * @return giá trị nên ghi; không bao giờ âm
     */
    public static double resolveStartNumber(double previousStart, double endMeter,
                                            double gross, boolean totalizerStale) {
        double kept = previousStart < 0 ? 0 : previousStart;
        if (totalizerStale) return kept;

        double candidate = endMeter - gross;
        if (candidate < 0) return kept;
        return candidate;
    }

    /** Có phải lần tính này bị bỏ qua để giữ giá trị cũ hay không (dùng để ghi vết). */
    public static boolean startNumberHeld(double previousStart, double endMeter,
                                          double gross, boolean totalizerStale) {
        return totalizerStale || endMeter - gross < 0;
    }
}
