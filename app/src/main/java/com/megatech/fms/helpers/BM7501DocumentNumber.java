package com.megatech.fms.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sinh và kiểm tra số chứng từ BM 75.01 — {@code localNumber}.
 *
 * <p>Đây là <b>số pháp lý chính thức</b> do app sinh offline, bất biến sau khi in.
 * {@code serverNumber} chỉ là tham chiếu kỹ thuật, không thay thế số này.
 *
 * <p>Định dạng: {@code 75-<chi nhánh>-<số xe>-<mã thiết bị>-<yyMMdd>-<seq>}
 *
 * <p>Mã thiết bị được giữ trong số phiếu vì chưa bảo đảm "một xe chỉ dùng một tablet"
 * (quyết định §14 mục 4). Mã lấy từ {@code TruckModel.tabletSerial} — giá trị do app tự
 * quản lý và lưu trong setting — rồi rút thành 4 ký tự hex bằng SHA-256 để số phiếu không
 * quá dài và không lộ serial thiết bị.
 *
 * <p>Lớp này KHÔNG cấp phát {@code seq}. Việc cấp số chạy trong transaction ở tầng lưu trữ
 * (bảng đếm riêng + unique index trên {@code localNumber}) — thuộc bước 2 đang bị chặn.
 */
public final class BM7501DocumentNumber {

    private BM7501DocumentNumber() {
    }

    public static final String PREFIX = "75";

    /** Độ dài phần số thứ tự trong ngày. */
    private static final int SEQ_WIDTH = 3;

    /** Độ dài mã thiết bị rút gọn. */
    private static final int DEVICE_CODE_LENGTH = 4;

    /**
     * Dựng số chứng từ.
     *
     * @param branchCode   mã chi nhánh, ví dụ "NBA"
     * @param truckNo      số xe, ví dụ "51F-123.45"
     * @param tabletSerial serial tablet do app quản lý (có thể null → dùng "0000")
     * @param date         ngày lập phiếu
     * @param seq          số thứ tự trong ngày của phạm vi cấp số, bắt đầu từ 1
     */
    public static String build(String branchCode, String truckNo, String tabletSerial,
                               Date date, int seq) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq phải >= 1, nhận được " + seq);
        }
        if (date == null) {
            throw new IllegalArgumentException("date không được null");
        }
        if (sanitize(branchCode).isEmpty()) {
            throw new IllegalArgumentException("Thiếu mã chi nhánh cho số phiếu");
        }
        if (sanitize(truckNo).isEmpty()) {
            throw new IllegalArgumentException("Thiếu số xe cho số phiếu");
        }
        return PREFIX
                + "-" + sanitize(branchCode)
                + "-" + sanitize(truckNo)
                + "-" + deviceCode(tabletSerial)
                + "-" + new SimpleDateFormat("yyMMdd", Locale.US).format(date)
                + "-" + pad(seq);
    }

    /**
     * Khóa phạm vi cấp số cho bảng đếm ở tầng lưu trữ: mỗi thiết bị + xe + ngày một dãy.
     * Nhờ mã thiết bị nằm trong khóa, hai tablet trên cùng một xe không tranh nhau số.
     */
    public static String counterScopeKey(String truckNo, String tabletSerial, Date date) {
        if (date == null) {
            throw new IllegalArgumentException("date không được null");
        }
        return sanitize(truckNo)
                + "|" + deviceCode(tabletSerial)
                + "|" + new SimpleDateFormat("yyMMdd", Locale.US).format(date);
    }

    /**
     * Mã thiết bị 4 ký tự hex, ổn định theo {@code tabletSerial}.
     * Serial rỗng → "0000" để vẫn sinh được số, nhưng tầng gọi nên cảnh báo.
     */
    public static String deviceCode(String tabletSerial) {
        if (tabletSerial == null || tabletSerial.trim().isEmpty()) {
            return "0000";
        }
        String hex = BM7501Canonical.sha256OfString(tabletSerial.trim());
        return hex.substring(0, DEVICE_CODE_LENGTH).toUpperCase(Locale.US);
    }

    /** Số phiếu có đúng cấu trúc hay không — dùng cho test và cho kiểm tra dữ liệu nhập lại. */
    public static boolean isValid(String localNumber) {
        if (localNumber == null) return false;
        return localNumber.matches("^75-[A-Z0-9]+-[A-Z0-9]+-[A-F0-9]{4}-\\d{6}-\\d{" + SEQ_WIDTH + ",}$");
    }

    private static String pad(int seq) {
        String s = String.valueOf(seq);
        while (s.length() < SEQ_WIDTH) {
            s = "0" + s;
        }
        return s;
    }

    /** Bỏ mọi ký tự không phải chữ/số và viết hoa, để số phiếu không chứa dấu phân tách lạ. */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }
}
