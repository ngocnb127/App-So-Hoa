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

    /** Lệch dưới mức này giữa giờ tra nạp và mốc tiếp cận/rời đi coi như thao tác tay, bỏ qua. */
    public static final long APPROACH_TOLERANCE_MS = 5L * 60 * 1000;

    /**
     * Khoảng thời gian tra nạp toàn chuyến vượt mức này thì gần như chắc chắn có mẻ mang giờ
     * cũ: chuyến nhiều xe thực tế kéo dài vài giờ, không phải cả ca.
     */
    public static final long MAX_INVOICE_SPAN_MS = 6L * 60 * 60 * 1000;

    private static final String TIME_PATTERN = "HH:mm";

    /** Cùng nội dung nhưng kèm ngày, dùng khi các mốc của một mẻ rơi vào hai ngày khác nhau. */
    private static final String DATE_TIME_PATTERN = "dd/MM HH:mm";

    /**
     * Dung sai cho giờ tương lai.
     *
     * <p>{@code startTime}/{@code endTime} do chính máy bảng đóng dấu và cũng so với đồng hồ
     * của chính máy đó, nên giờ tương lai chỉ phát sinh từ nhập tay; 2 phút là thừa đủ để
     * không báo nhầm vì độ trễ thao tác.
     */
    public static final long FUTURE_TOLERANCE_MS = 2L * 60 * 1000;

    private RefuelTimeValidator() {
    }

    /**
     * @return danh sách mô tả lỗi, rỗng nếu giờ hợp lệ.
     */
    public static List<String> validate(RefuelItemData item) {
        return validate(item, System.currentTimeMillis());
    }

    /**
     * Bản nạp chồng nhận đồng hồ, để kiểm thử luật "giờ tương lai" tất định không phụ thuộc
     * ngày chạy CI.
     *
     * @param nowMs thời điểm coi là "bây giờ"
     */
    public static List<String> validate(RefuelItemData item, long nowMs) {
        List<String> errors = new ArrayList<>();
        if (item == null) return errors;

        Date start = item.getStartTime();
        Date end = item.getEndTime();
        Date approach = item.getApproachTime();
        Date leave = item.getLeaveTime();
        boolean withDate = spansMultipleDays(item);

        // Giờ tương lai: hoàn toàn không có ca hợp lệ nào, nhưng cũng chỉ cảnh báo — màn
        // hình gọi vẫn có nút đi tiếp, hiện trường có ca lệch đồng hồ máy bảng.
        addFutureError(errors, "Giờ tiếp cận", approach, nowMs, withDate);
        addFutureError(errors, "Giờ bắt đầu", start, nowMs, withDate);
        addFutureError(errors, "Giờ kết thúc", end, nowMs, withDate);
        addFutureError(errors, "Giờ rời đi", leave, nowMs, withDate);

        // Cặp tiếp cận / rời đi kiểm được ngay cả khi thiếu giờ tra nạp.
        if (approach != null && leave != null && leave.before(approach)) {
            errors.add("Giờ rời đi (" + time(leave, withDate) + ") sớm hơn giờ tiếp cận ("
                    + time(approach, withDate) + ").");
        }

        if (start == null || end == null) {
            errors.add("Chưa ghi nhận đủ giờ bắt đầu và giờ kết thúc tra nạp.");
            return errors;
        }

        long duration = end.getTime() - start.getTime();

        if (duration < 0) {
            errors.add("Giờ kết thúc (" + time(end, withDate) + ") sớm hơn giờ bắt đầu ("
                    + time(start, withDate) + ").");
        } else if (duration > MAX_DURATION_MS) {
            errors.add("Thời gian tra nạp " + minutes(duration)
                    + " phút, vượt quá " + minutes(MAX_DURATION_MS) + " phút cho phép.");
        }

        if (approach != null && start.before(approach)) {
            errors.add("Giờ bắt đầu (" + time(start, withDate) + ") trước giờ tiếp cận ("
                    + time(approach, withDate) + ").");
        }

        if (leave != null && end.after(leave)) {
            errors.add("Giờ kết thúc (" + time(end, withDate) + ") sau giờ rời đi ("
                    + time(leave, withDate) + ").");
        }

        return errors;
    }

    /**
     * Các điểm cần lưu ý nhưng KHÔNG phải lỗi nhập liệu.
     *
     * <p>Tách khỏi {@link #validate} có chủ ý: hai ca dưới đây xảy ra thường xuyên do chính
     * thiết kế hiện tại (phiếu cũ không có giờ tiếp cận; {@code finalizStop} cố ý đặt
     * {@code StartTime = EndTime} khi không bắt được sự kiện start). Trộn chúng vào danh
     * sách lỗi sẽ làm người dùng quen tay bấm bỏ qua, và cảnh báo thật mất tác dụng.
     */
    public static List<String> contextNotes(RefuelItemData item) {
        List<String> notes = new ArrayList<>();
        if (item == null) return notes;

        Date start = item.getStartTime();
        Date end = item.getEndTime();

        if (start != null && end != null && item.getApproachTime() == null) {
            notes.add("Chuyến chưa ghi nhận giờ tiếp cận nên không đối chiếu được"
                    + " với khoảng xe đỗ tại tàu bay.");
        }

        if (start != null && end != null && start.getTime() == end.getTime()) {
            notes.add("Giờ bắt đầu trùng giờ kết thúc (0 phút) — không bắt được sự kiện"
                    + " bắt đầu của đồng hồ.");
        }

        return notes;
    }

    private static void addFutureError(List<String> errors, String label, Date value,
                                       long nowMs, boolean withDate) {
        if (value == null) return;
        if (value.getTime() - nowMs <= FUTURE_TOLERANCE_MS) return;
        errors.add(label + " (" + time(value, withDate) + ") nằm ở tương lai.");
    }

    /** Các mốc giờ của mẻ có rơi vào nhiều ngày khác nhau hay không. */
    private static boolean spansMultipleDays(RefuelItemData item) {
        Date[] all = new Date[]{item.getApproachTime(), item.getStartTime(),
                item.getEndTime(), item.getLeaveTime()};
        String first = null;
        for (Date d : all) {
            if (d == null) continue;
            String day = DateUtils.formatDate(d, "yyyyMMdd");
            if (first == null) first = day;
            else if (!first.equals(day)) return true;
        }
        return false;
    }

    /**
     * Gộp các LỖI THẬT thành một khối văn bản để đổ thẳng vào TextView cảnh báo.
     *
     * <p>CỐ Ý KHÔNG gộp {@link #contextNotes}: chuỗi này là đầu vào của popup bắt buộc ở màn
     * xác nhận, mà hai ghi chú ngữ cảnh nổ trên rất nhiều mẻ do chính thiết kế hiện tại
     * ({@code finalizStop} đặt StartTime = EndTime khi không bắt được sự kiện bắt đầu). Trộn
     * chúng vào đây sẽ bật popup gần như mỗi mẻ, người dùng quen tay bấm bỏ qua rồi bỏ qua
     * luôn cảnh báo thật. Ghi chú ngữ cảnh đi đường {@link #describeContextNotes} — hiển thị
     * tại chỗ, không gián đoạn.
     *
     * @return chuỗi rỗng nếu không có lỗi.
     */
    public static String describe(RefuelItemData item) {
        return describe(item, System.currentTimeMillis());
    }

    /**
     * Bản nạp chồng nhận đồng hồ. Đây mới là hàm được cả hai màn hình gọi thật, nên luật
     * "giờ tương lai" chỉ kiểm thử được tất định khi có bản này.
     */
    public static String describe(RefuelItemData item, long nowMs) {
        List<String> errors = validate(item, nowMs);
        if (errors.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("⚠ Giờ tra nạp bất thường, kiểm tra lại:");
        for (String error : errors) {
            sb.append("\n• ").append(error);
        }
        return sb.toString();
    }

    /**
     * Gộp {@link #contextNotes} thành một khối văn bản để hiển thị TẠI CHỖ (nhãn trên màn
     * hình), KHÔNG dùng cho popup.
     *
     * @return chuỗi rỗng nếu không có ghi chú nào.
     */
    public static String describeContextNotes(RefuelItemData item) {
        List<String> notes = contextNotes(item);
        if (notes.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("ℹ Ghi chú giờ tra nạp:");
        for (String note : notes) {
            sb.append("\n• ").append(note);
        }
        return sb.toString();
    }

    /**
     * Chỉ các lỗi giờ tra nạp nằm ngoài khoảng tiếp cận – rời đi của mẻ.
     *
     * <p>Tách khỏi {@link #validate} vì đường xuất hoá đơn đã có cảnh báo riêng cho mẻ quá dài
     * và cho mẻ thiếu giờ; gọi thẳng {@code validate} ở đó sẽ hiện lại cùng một nội dung hai
     * lần trong hai hộp thoại liên tiếp.
     *
     * <p>Bỏ qua lệch dưới {@link #APPROACH_TOLERANCE_MS}: hiện trường có mẻ ghi tiếp cận sau
     * giờ bắt đầu vài phút do thao tác tay, cảnh báo những ca đó chỉ làm người dùng quen với
     * việc bấm bỏ qua.
     *
     * @return danh sách mô tả lỗi, rỗng nếu giờ nằm trong khoảng hoặc thiếu mốc để so.
     */
    public static List<String> outsideApproachWindow(RefuelItemData item) {
        List<String> errors = new ArrayList<>();
        if (item == null) return errors;

        Date start = item.getStartTime();
        Date end = item.getEndTime();
        Date approach = item.getApproachTime();
        Date leave = item.getLeaveTime();

        if (start != null && approach != null
                && approach.getTime() - start.getTime() > APPROACH_TOLERANCE_MS) {
            errors.add("Giờ bắt đầu (" + time(start) + ") trước giờ tiếp cận ("
                    + time(approach) + ").");
        }

        if (end != null && leave != null
                && end.getTime() - leave.getTime() > APPROACH_TOLERANCE_MS) {
            errors.add("Giờ kết thúc (" + time(end) + ") sau giờ rời đi ("
                    + time(leave) + ").");
        }

        return errors;
    }

    /**
     * Khoảng thời gian toàn chuyến in trên hoá đơn, tính từ mẻ sớm nhất tới mẻ muộn nhất.
     *
     * <p>Đây chính là cặp giá trị {@code InvoiceModel.fromRefuel} gộp ra và in lên hoá đơn.
     * Một mẻ mang giờ bắt đầu cũ sẽ kéo mốc đầu đi rất xa mà từng mẻ nhìn riêng vẫn hợp lệ,
     * nên phải soi ở mức tổng mới thấy.
     *
     * @return khoảng thời gian tính bằng mili giây, hoặc -1 khi không đủ dữ liệu để tính.
     */
    public static long invoiceSpanMs(List<RefuelItemData> items) {
        if (items == null || items.isEmpty()) return -1;

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (RefuelItemData item : items) {
            if (item == null) continue;
            Date start = item.getStartTime();
            Date end = item.getEndTime();
            if (start == null || end == null) continue;

            min = Math.min(min, start.getTime());
            max = Math.max(max, end.getTime());
        }

        if (min == Long.MAX_VALUE || max == Long.MIN_VALUE) return -1;

        return max - min;
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

    /**
     * Mô tả các mẻ có giờ kết thúc sớm hơn giờ bắt đầu trong một danh sách sắp in.
     *
     * <p>Tách riêng khỏi đường tạo phiếu (vốn CHẶN CỨNG qua {@link #endsBeforeStart}) để đường
     * xuất hoá đơn dùng lại đúng phép phát hiện đó ở mức CẢNH BÁO. Đường phiếu giữ nguyên
     * mức chặn cũ, không đụng tới.
     *
     * <p>Luôn kèm SỐ XE: mẻ lỗi có thể thuộc xe khác — máy này chỉ đọc, không sửa được — nên
     * người dùng cần biết phải gọi cho ai chứ không chỉ biết "có mẻ sai giờ".
     *
     * @return danh sách mô tả, rỗng khi không có mẻ nào đảo giờ.
     */
    public static List<String> reversedTimeItems(List<RefuelItemData> items) {
        List<String> result = new ArrayList<>();
        if (items == null) return result;

        for (RefuelItemData item : items) {
            if (!endsBeforeStart(item)) continue;

            String truckNo = item.getTruckNo() == null || item.getTruckNo().trim().isEmpty()
                    ? "(chưa rõ xe)" : item.getTruckNo().trim();
            String flightCode = item.getFlightCode() == null || item.getFlightCode().isEmpty()
                    ? "(chưa có số hiệu)" : item.getFlightCode();
            result.add(truckNo + " / " + flightCode + ": bắt đầu "
                    + time(item.getStartTime(), true) + ", kết thúc "
                    + time(item.getEndTime(), true));
        }
        return result;
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
        return time(date, false);
    }

    /**
     * Kèm ngày khi mẻ trải qua nửa đêm: chỉ in {@code HH:mm} thì "kết thúc 00:10 sớm hơn bắt
     * đầu 23:50" đọc như lỗi giả trong khi dữ liệu hoàn toàn đúng, và ngược lại.
     */
    private static String time(Date date, boolean withDate) {
        return DateUtils.formatDate(date, withDate ? DATE_TIME_PATTERN : TIME_PATTERN);
    }
}
