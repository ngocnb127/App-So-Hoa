package com.megatech.fms.helpers;

/**
 * Bộ lọc nhiễu cho SỐ TỔNG (totalizer) của đồng hồ.
 *
 * <p>Luật gốc: bỏ giá trị lệch quá {@link #JUMP_LIMIT} so với giá trị đang có. Ý định đúng —
 * chặn một nhịp nhiễu của đường truyền đạp lên số tổng. Nhưng viết thẳng thành một câu
 * {@code if} thì nó TỰ KHOÁ: mốc so sánh chỉ đổi khi có giá trị được nhận, nên một khi số
 * thật đã ra xa mốc quá 1000 thì MỌI giá trị sau đó đều bị loại và không còn đường nào quay
 * lại. Số tổng trên app đóng băng vĩnh viễn trong khi đồng hồ vẫn chạy đúng.
 *
 * <p>Ca sinh ra chuyện đó rất đời thường: app rời đồng hồ một lúc (đổi màn hình, màn in ngắt
 * kết nối sau khi lấy số, rớt Bluetooth/WiFi) và trong lúc đó xe bơm hơn 1000 đơn vị. Nối
 * lại là khoá.
 *
 * <p>Cách thoát ở đây: phân biệt NHIỄU với MỐC MỚI bằng tính nhất quán. Một nhịp nhiễu là
 * đơn lẻ và ngẫu nhiên; một mốc mới thì lần đọc nào cũng khẳng định lại, và các lần đọc liên
 * tiếp bám sát nhau. Đủ {@link #REBASE_AFTER} nhịp liên tiếp cùng chỉ về một vùng giá trị
 * thì nhận vùng đó làm mốc mới.
 *
 * <p>Chu kỳ đọc là 1 giây nên việc lấy lại mốc mất khoảng {@link #REBASE_AFTER} giây — thấp
 * hơn hẳn ngưỡng kết luận trường đã chết của {@link MeterFieldHealth}, nên trạng thái khoá
 * không còn kịp trở thành một cảnh báo.
 *
 * <p>Thuần Java, không giữ tham chiếu nào tới thiết bị, để kiểm thử tất định.
 */
public final class TotalizerFilter {

    /** Lệch quá mức này so với giá trị đang có thì chưa nhận ngay. */
    public static final double JUMP_LIMIT = 1000d;

    /**
     * Bao nhiêu nhịp liên tiếp cùng khẳng định một mốc mới thì nhận.
     *
     * <p>5 nhịp ở chu kỳ 1 giây là 5 giây. Đủ dài để một nhịp nhiễu đơn lẻ không vượt qua,
     * đủ ngắn để người vận hành không kịp thấy số tổng đứng im.
     */
    public static final int REBASE_AFTER = 5;

    /**
     * Các nhịp bị loại liên tiếp phải bám sát nhau tới mức này mới coi là cùng một mốc.
     *
     * <p>Số tổng vẫn tăng trong lúc đang bơm nên không thể đòi bằng nhau tuyệt đối; 50 đơn
     * vị mỗi nhịp là rộng hơn tốc độ bơm thực tế mà vẫn hẹp hơn nhiều so với biên độ của một
     * giá trị nhiễu.
     */
    public static final double REBASE_TOLERANCE = 50d;

    private double lastRejected;
    private int rejectedStreak;

    /**
     * Có nên nhận giá trị vừa đọc hay không.
     *
     * @param current   giá trị số tổng app đang giữ; 0 nghĩa là chưa có gì để so
     * @param candidate giá trị vừa đọc từ đồng hồ
     * @return true thì phía gọi ghi {@code candidate} vào mô hình
     */
    public boolean accept(double current, double candidate) {
        if (current == 0 || Math.abs(candidate - current) < JUMP_LIMIT) {
            rejectedStreak = 0;
            return true;
        }

        // Lệch lớn. Nhịp này có nối tiếp chuỗi lệch trước đó, hay là một giá trị rời rạc?
        boolean tiepNoi = rejectedStreak > 0
                && Math.abs(candidate - lastRejected) <= REBASE_TOLERANCE;
        rejectedStreak = tiepNoi ? rejectedStreak + 1 : 1;
        lastRejected = candidate;

        if (rejectedStreak >= REBASE_AFTER) {
            // Đồng hồ đã khẳng định đủ nhiều lần: đây là mốc mới, không phải nhiễu.
            rejectedStreak = 0;
            return true;
        }
        return false;
    }

    /**
     * Lần nhận gần nhất có phải là LẤY LẠI MỐC hay không — dùng để ghi vết.
     *
     * <p>Chỉ đúng ngay sau một lần {@link #accept} trả về true. Lấy lại mốc nghĩa là số tổng
     * trên app vừa nhảy hơn 1000 đơn vị một lúc, và ca đối soát sau này cần biết điều đó.
     */
    public boolean justRebased(double current, double candidate) {
        return current != 0 && Math.abs(candidate - current) >= JUMP_LIMIT;
    }

    /** Phiên đo mới (nối lại thiết bị): quên chuỗi đang dở. */
    public void reset() {
        rejectedStreak = 0;
        lastRejected = 0;
    }
}
