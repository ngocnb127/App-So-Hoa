package com.megatech.fms.helpers;

import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Kiểm tra tính hợp lý của giờ bắt đầu / kết thúc tra nạp so với mốc tiếp cận và rời đi.
 *
 * <p>Giờ trên phiếu đến từ nhiều nguồn và có thể bị sửa tay ở màn xác nhận, nên vẫn có
 * những cặp giá trị vô lý lọt tới lúc in: mẻ dài mấy tiếng vì mốc bắt đầu bị suy ngược
 * sai, hoặc giờ tra nạp nằm ngoài hẳn khoảng xe đỗ tại tàu bay. Lớp này chỉ trả về mô tả
 * lỗi; hiển thị và mức độ chặn do màn hình gọi quyết định — hiện tại là cảnh báo, người
 * dùng vẫn đi tiếp được vì hiện trường có ca ngoại lệ thật.
 */
public final class RefuelTimeValidator {

    /** Một mẻ tra nạp thực tế không quá 60 phút; dài hơn gần như chắc chắn là mốc giờ sai. */
    public static final long MAX_DURATION_MS = 60L * 60 * 1000;

    private static final String TIME_PATTERN = "HH:mm";

    private RefuelTimeValidator() {
    }

    /**
     * @return danh sách mô tả lỗi, rỗng nếu giờ hợp lệ.
     */
    public static List<String> validate(RefuelItemData item) {
        List<String> errors = new ArrayList<>();
        if (item == null) return errors;

        Date start = item.getStartTime();
        Date end = item.getEndTime();

        if (start == null || end == null) {
            errors.add("Chưa ghi nhận đủ giờ bắt đầu và giờ kết thúc tra nạp.");
            return errors;
        }

        long duration = end.getTime() - start.getTime();

        if (duration < 0) {
            errors.add("Giờ kết thúc (" + time(end) + ") sớm hơn giờ bắt đầu (" + time(start) + ").");
        } else if (duration > MAX_DURATION_MS) {
            errors.add("Thời gian tra nạp " + minutes(duration)
                    + " phút, vượt quá " + minutes(MAX_DURATION_MS) + " phút cho phép.");
        }

        Date approach = item.getApproachTime();
        Date leave = item.getLeaveTime();

        if (approach != null && start.before(approach)) {
            errors.add("Giờ bắt đầu (" + time(start) + ") trước giờ tiếp cận ("
                    + time(approach) + ").");
        }

        if (leave != null && end.after(leave)) {
            errors.add("Giờ kết thúc (" + time(end) + ") sau giờ rời đi ("
                    + time(leave) + ").");
        }

        return errors;
    }

    /**
     * Gộp các lỗi thành một khối văn bản để đổ thẳng vào TextView cảnh báo.
     *
     * @return chuỗi rỗng nếu không có lỗi.
     */
    public static String describe(RefuelItemData item) {
        List<String> errors = validate(item);
        if (errors.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("⚠ Giờ tra nạp bất thường, kiểm tra lại:");
        for (String error : errors) {
            sb.append("\n• ").append(error);
        }
        return sb.toString();
    }

    /**
     * Mẻ có thời gian tra nạp vượt {@link #MAX_DURATION_MS} hay không.
     *
     * <p>Tách riêng khỏi {@link #validate} vì màn hình in hoá đơn chỉ quan tâm đúng dấu
     * hiệu này, không quan tâm mốc tiếp cận / rời đi.
     *
     * @return false khi thiếu giờ — thiếu dữ liệu là việc của {@link #validate}, không phải
     *         cái để chặn đường in.
     */
    public static boolean exceedsMaxDuration(RefuelItemData item) {
        if (item == null) return false;

        Date start = item.getStartTime();
        Date end = item.getEndTime();
        if (start == null || end == null) return false;

        return end.getTime() - start.getTime() > MAX_DURATION_MS;
    }

    /**
     * Giờ kết thúc sớm hơn giờ bắt đầu — dữ liệu sai chắc chắn, không có ca hợp lệ nào
     * như vậy. Đường tạo phiếu chặn cứng theo hàm này.
     *
     * @return false khi thiếu giờ, để việc thiếu dữ liệu không bị báo nhầm thành đảo giờ.
     */
    public static boolean endsBeforeStart(RefuelItemData item) {
        if (item == null) return false;

        Date start = item.getStartTime();
        Date end = item.getEndTime();
        if (start == null || end == null) return false;

        return end.before(start);
    }

    /** Thời gian tra nạp tính bằng phút; 0 nếu thiếu giờ. */
    public static long durationMinutes(RefuelItemData item) {
        if (item == null) return 0;

        Date start = item.getStartTime();
        Date end = item.getEndTime();
        if (start == null || end == null) return 0;

        return minutes(end.getTime() - start.getTime());
    }

    private static long minutes(long millis) {
        return Math.round(millis / 60000d);
    }

    private static String time(Date date) {
        return DateUtils.formatDate(date, TIME_PATTERN);
    }
}
