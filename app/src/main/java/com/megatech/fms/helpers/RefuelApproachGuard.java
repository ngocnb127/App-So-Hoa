package com.megatech.fms.helpers;

import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Chặn tiếp cận chuyến mới khi chuyến đã tiếp cận trước đó chưa bấm rời đi.
 *
 * <p>Một xe chỉ đứng được ở một tàu bay tại một thời điểm, nên hai chuyến cùng ở trạng
 * thái "đã tiếp cận, chưa rời đi" là dữ liệu sai: mốc tiếp cận của chuyến sau đè lên
 * khoảng thời gian chuyến trước, và bảng theo dõi xe không còn dựng lại được thứ tự phục
 * vụ. Ngăn ngay lúc bấm rẻ hơn nhiều so với đi sửa hai phiếu sau ca.
 *
 * <p>Việc đọc dữ liệu chạm Room nên {@link #findBlocking} PHẢI gọi ở thread nền.
 */
public final class RefuelApproachGuard {

    /**
     * Chỉ xét các lần tiếp cận trong 24 giờ gần nhất.
     *
     * <p>Không có mốc này thì một phiếu cũ quên bấm rời đi sẽ khoá nút Tiếp cận vĩnh viễn
     * cho tới lượt dọn dữ liệu định kỳ — người dùng không còn đường nào đi tiếp.
     */
    public static final long LOOKBACK_MS = 24L * 60 * 60 * 1000;

    private RefuelApproachGuard() {
    }

    /**
     * Tìm các chuyến đang mở dở: đã có {@code approachTime} trong 24 giờ qua nhưng chưa có
     * {@code leaveTime}.
     *
     * @param excludeUniqueId phiếu đang thao tác, không tự chặn chính nó (có thể null).
     * @return danh sách chuyến chặn, mới nhất trước; rỗng nghĩa là được phép tiếp cận.
     */
    public static List<RefuelItemData> findBlocking(String excludeUniqueId) {
        List<RefuelItemData> blocking = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - LOOKBACK_MS;

        List<RefuelItemData> all;
        try {
            all = DataHelper.getLocalRefuelList();
        } catch (Exception ex) {
            // Không đọc được dữ liệu thì không chặn: thà lọt một ca hiếm còn hơn khoá nút
            // Tiếp cận của cả xe vì một lỗi đọc DB.
            Logger.appendLog("APPROACH", "Không đọc được danh sách phiếu: " + ex.getMessage());
            return blocking;
        }

        if (all == null) return blocking;

        for (RefuelItemData item : all) {
            if (item == null) continue;

            Date approach = item.getApproachTime();
            if (approach == null || item.getLeaveTime() != null) continue;
            if (approach.getTime() < cutoff) continue;

            if (excludeUniqueId != null && excludeUniqueId.equals(item.getUniqueId())) continue;

            blocking.add(item);
        }

        Collections.sort(blocking, new Comparator<RefuelItemData>() {
            @Override
            public int compare(RefuelItemData a, RefuelItemData b) {
                return b.getApproachTime().compareTo(a.getApproachTime());
            }
        });

        return blocking;
    }

    /**
     * Nội dung hộp thoại báo vì sao không bấm tiếp cận được.
     */
    public static String buildMessage(List<RefuelItemData> blocking) {
        StringBuilder sb = new StringBuilder(
                "Chưa bấm Rời đi cho chuyến đã tiếp cận trước đó. "
                        + "Hãy bấm Rời đi cho chuyến sau rồi mới tiếp cận chuyến mới:");

        for (RefuelItemData item : blocking) {
            sb.append("\n\n• ").append(describe(item));
        }

        return sb.toString();
    }

    private static String describe(RefuelItemData item) {
        StringBuilder sb = new StringBuilder();

        String flightCode = item.getFlightCode();
        sb.append(flightCode == null || flightCode.isEmpty() ? "(chưa có số hiệu)" : flightCode);

        String parkingLot = item.getParkingLot();
        if (parkingLot != null && !parkingLot.isEmpty()) {
            sb.append(" — bãi ").append(parkingLot);
        }

        sb.append("\n  Tiếp cận lúc ")
                .append(DateUtils.formatDate(item.getApproachTime(), "dd/MM HH:mm"));

        return sb.toString();
    }
}
