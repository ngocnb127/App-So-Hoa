package com.megatech.fms.helpers;

import androidx.annotation.Nullable;

/**
 * Ghi nhận NGUYÊN NHÂN của mỗi lần in hỏng.
 *
 * <p>Vì sao cần: cả hai đường in đều báo lỗi bằng đúng một câu — "Lỗi kết nối máy in" —
 * cho hàng chục nguyên nhân khác nhau: Bluetooth tắt, chưa ghép đôi, sai địa chỉ MAC, máy
 * in hết giấy, đầu in mở, máy còn ở chế độ CPCL, hết pin, hoặc dây mạng của máy in kim bị
 * rút. Người dùng báo "máy in lỗi", kỹ thuật không có gì để lần, và mỗi lần lại đi đoán
 * từ đầu. Không đếm được nguyên nhân nào hay xảy ra thì không thể sửa dứt điểm.
 *
 * <p>Mỗi bản ghi trả lời bốn câu: máy in loại nào, hỏng ở BƯỚC nào, địa chỉ nào, và nguyên
 * nhân kỹ thuật là gì. Tất cả ghi vào {@code fms.log} — cùng chỗ với mọi việc khác, vì sự
 * cố in luôn phải đọc cùng những gì xảy ra ngay trước nó, và file này đã có sẵn đường gửi
 * về máy chủ.
 */
public final class PrintDiagnostics {

    /** Loại máy in — hai đường mã hoàn toàn khác nhau, hỏng vì những lý do khác nhau. */
    public enum PrinterKind {
        /** Máy in kim, nối bằng TCP qua Wi-Fi/LAN. */
        DOT_MATRIX,
        /** Máy in nhiệt Zebra, nối bằng Bluetooth. */
        THERMAL
    }

    /**
     * Bước trong quy trình in. Biết hỏng ở bước nào là biết phải sửa cái gì: hỏng ở
     * {@link #CONNECT} là chuyện thiết bị/ghép đôi, hỏng ở {@link #SEND} là chuyện giấy và
     * đầu in, hỏng ở {@link #PREPARE} là chuyện cấu hình nằm trong máy in.
     */
    public enum Stage {
        /** Tìm địa chỉ máy in (MAC đã lưu, danh sách đã ghép đôi, dò mạng). */
        DISCOVER,
        /** Mở kết nối tới máy in. */
        CONNECT,
        /** Hỏi máy in còn sống và đúng chế độ không. */
        CHECK,
        /** Nạp font, đặt ngôn ngữ ZPL, đặt loại giấy. */
        PREPARE,
        /** Gửi nội dung phiếu. */
        SEND,
        /** Đóng kết nối, dọn dẹp. */
        FINISH
    }

    private PrintDiagnostics() {
    }

    /**
     * @param kind    loại máy in
     * @param stage   bước đang chạy khi hỏng
     * @param address địa chỉ máy in (MAC hoặc IP:port); null nếu chưa có
     * @param reason  nguyên nhân, viết cho KỸ THUẬT đọc chứ không phải cho người dùng
     * @param cause   ngoại lệ gốc nếu có
     */
    public static void recordFailure(PrinterKind kind, Stage stage,
                                     @Nullable String address, String reason,
                                     @Nullable Throwable cause) {
        recordFailure(kind, stage, address, null, reason, cause);
    }

    /**
     * @param document phiếu đang in (số phiếu / số hoá đơn / tên biểu mẫu), null nếu chưa
     *                 biết. Không có nó thì nhật ký nói được "lúc 9:14 in hỏng" nhưng không
     *                 nói được HỎNG PHIẾU NÀO — mà đó lại là câu hỏi đầu tiên khi đối soát
     *                 sau ca: phiếu đó cuối cùng có ra giấy hay không.
     */
    public static void recordFailure(PrinterKind kind, Stage stage,
                                     @Nullable String address, @Nullable String document,
                                     String reason, @Nullable Throwable cause) {
        String detail = cause == null ? reason : reason + " | " + Logger.describe(cause);

        // Ghi vào ĐÚNG nhật ký chung fms.log, không tách file riêng: sự cố in gần như luôn
        // phải đọc cùng những việc xảy ra ngay trước nó — chốt mẻ, đồng bộ, mất mạng — và
        // hai file song song thì phải ghép tay theo mốc giờ mới dựng lại được câu chuyện.
        Logger.appendLog("PRNT", String.format(java.util.Locale.US,
                "event=PRINT_FAILED printer=%s stage=%s address=%s document=%s reason=%s thread=%s",
                kind, stage, address == null ? "null" : address,
                document == null ? "null" : document, detail,
                Thread.currentThread().getName()));
    }

    /** Lần in thành công cũng ghi một dòng: không có mẫu số thì tỉ lệ hỏng không có nghĩa. */
    public static void recordSuccess(PrinterKind kind, @Nullable String address) {
        Logger.appendLog("PRNT", String.format(java.util.Locale.US,
                "event=PRINT_OK printer=%s address=%s",
                kind, address == null ? "null" : address));
    }
}
