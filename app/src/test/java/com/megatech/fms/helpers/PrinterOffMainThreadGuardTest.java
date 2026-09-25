package com.megatech.fms.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Canh một HỌ LỖI: nói chuyện với máy in Bluetooth trên luồng giao diện.
 *
 * <p>Không có chốt nào của hệ thống bắt được lỗi này lúc chạy — nó không ném exception, nó
 * chỉ làm giao diện đứng. Hậu quả tới muộn và tới dưới dạng ANR, tức là app biến mất giữa
 * lúc đang tra nạp cho chuyến bay.
 *
 * <p>Đo trên xe thật 09-09-2026 (bản 119, gói {@code .fhs}). Ba lượt in liên tiếp, đọc từ
 * nhật ký giữa lúc bấm nút và lúc {@code PRINT_OK}: 21:56:33→45, 21:56:45→57, 21:56:57→
 * 21:57:05 — khoá giao diện 12, 12 và 8 giây, trong khi ngưỡng ANR cho thao tác chạm là 5.
 *
 * <p>Chi tiết quan trọng nhất: cả ba lượt đều IN THÀNH CÔNG. Lượt thứ ba vẫn bị hệ thống
 * tuyên ANR giữa chừng, hộp "FMS Delivery THERMAL không phản hồi" hiện lên và KHÔNG tự tắt;
 * người dùng lưu phiếu, rời chuyến, quay về danh sách, rồi 21:58:45 bấm "Đóng ứng dụng" —
 * tiến trình chết. Nghĩa là lỗi này dính ở MỌI lượt in, không cần máy in hỏng, và hậu quả
 * tới muộn tới mức nhìn vào nhật ký không thấy liên hệ gì với việc in.
 *
 * <p>Khuôn đúng: cửa vào in xếp việc sang luồng in rồi trả về ngay; kết quả về giao diện qua
 * {@code ZebraStateListener}, và listener được gọi sẵn trên luồng giao diện.
 */
public class PrinterOffMainThreadGuardTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String zebraWorker() throws IOException {
        return source("src/main/java/com/megatech/fms/helpers/ZebraWorker.java");
    }

    /** Thân hàm {@code name}, cắt từ chữ ký tới dấu đóng ngoặc cùng cấp. */
    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        assertTrue("không tìm thấy hàm: " + signature, at > 0);
        int open = src.indexOf('{', at);
        assertTrue("không tìm thấy thân hàm: " + signature, open > at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("thân hàm không đóng: " + signature);
    }

    /**
     * Cửa vào in phiếu tra nạp — chính lượt gọi đã gây ANR — phải xếp việc sang luồng in,
     * không được tự mình đụng tới máy in.
     */
    @Test
    public void inPhieuTraNapPhaiChayTrenLuongIn() throws IOException {
        String printReceipt = body(zebraWorker(), "public void printReceipt(ReceiptModel");

        assertTrue("printReceipt phải xếp việc sang luồng in, không tự chạy trên luồng gọi",
                printReceipt.contains("runOnPrinterThread"));
        assertFalse("printReceipt không được tự dò máy in trên luồng gọi",
                printReceipt.contains("findPrinter("));
        assertFalse("printReceipt không được tự gửi phiếu trên luồng gọi",
                printReceipt.contains("print(receiptModel)"));
    }

    /** BM 25.03 đi cùng một đường; bỏ sót nó là để lại đúng cái bẫy cũ ở màn hình khác. */
    @Test
    public void inBM2503PhaiChayTrenLuongIn() throws IOException {
        String print2503 = body(zebraWorker(), "public void print2503(BM2503Model");

        assertTrue("print2503 phải xếp việc sang luồng in",
                print2503.contains("runOnPrinterThread"));
        assertFalse("print2503 không được tự dò máy in trên luồng gọi",
                print2503.contains("find2503Printer("));
    }

    /**
     * BM 75.01 CHỜ in xong mới trả về, vì lối gọi còn phải đóng dấu ĐÃ IN sau khi giấy ra.
     * Nhưng nó vẫn phải đi qua luồng in chung — hai luồng cùng ghi vào một máy in là hỏng.
     */
    @Test
    public void inBM7501PhaiChoXongNhungVanQuaLuongIn() throws IOException {
        String print7501 = body(zebraWorker(), "public void print7501(BM7501Model");

        assertTrue("print7501 phải đi qua luồng in chung và chờ xong",
                print7501.contains("runOnPrinterThreadAndWait"));
    }

    /**
     * Mở kết nối phải có hạn chờ. Không có nó thì máy in tắt là giao diện chờ vô hạn —
     * chạy nền nên không còn văng app, nhưng người dùng vẫn kẹt ở vòng xoay không lối ra.
     */
    @Test
    public void moKetNoiPhaiCoHanCho() throws IOException {
        String src = zebraWorker();

        assertTrue("phải có hạn chờ mở kết nối", src.contains("CONNECT_TIMEOUT_MS"));
        assertTrue("phải có luồng canh hạn để cắt được lời gọi đang nằm chết",
                src.contains("connectWatchdog"));

        // Mọi lượt mở kết nối phải đi qua openConnection(); một chỗ gọi thẳng con.open() là
        // một chỗ chờ vô hạn.
        int direct = 0;
        for (String line : src.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
            if (trimmed.contains("con.open()")) direct++;
        }
        assertTrue("còn " + direct + " chỗ gọi thẳng con.open() — phải dùng openConnection()",
                direct == 0);
    }

    /**
     * Callback phải về luồng giao diện. Lối gọi đụng thẳng vào view — đóng hộp tiến trình,
     * hiện thông báo, {@code binding.invalidateAll()} — nên gọi chúng từ luồng in là văng.
     */
    @Test
    public void callbackPhaiVeLuongGiaoDien() throws IOException {
        String src = zebraWorker();

        assertTrue("phải có chỗ đưa callback về luồng giao diện",
                src.contains("mainHandler.post"));

        // Không còn chỗ nào gọi thẳng listener từ luồng in.
        assertFalse("không được gọi thẳng stateListener.onSuccess()",
                src.contains("stateListener.onSuccess()"));
        assertFalse("không được gọi thẳng stateListener.onError()",
                src.contains("stateListener.onError()"));
        assertFalse("không được gọi thẳng stateListener.onConnectionError()",
                src.contains("stateListener.onConnectionError()"));
    }

    /**
     * Factory máy in cũng là Bluetooth: đọc thông tin và gửi lệnh đều phải qua luồng in.
     * Gọi từ menu trên luồng giao diện, nên chạy thẳng là đúng cái ANR ở trên.
     */
    @Test
    public void factoryMayInPhaiChayTrenLuongIn() throws IOException {
        String src = zebraWorker();

        assertTrue("readPrinterIdentity phải xếp việc sang luồng in",
                body(src, "public void readPrinterIdentity(").contains("runOnPrinterThread"));
        assertTrue("factoryReset phải xếp việc sang luồng in",
                body(src, "public void factoryReset(").contains("runOnPrinterThread"));
    }

    /**
     * Factory xong PHẢI quên máy in. {@code prepare()} đọc cờ nhớ trước mọi kiểm tra; còn
     * cờ thì lượt in sau gửi ZPL vào máy vừa về CPCL — ra giấy trắng mà app báo in xong.
     *
     * <p>Chủ dự án chốt ngày 2026-09-13: sau factory, app tự đưa máy in về ZPL ở lượt kết
     * nối kế tiếp. Điều đó chỉ đúng khi cờ nhớ đã bị xoá.
     */
    @Test
    public void factoryXongPhaiQuenMayIn() throws IOException {
        String run = body(zebraWorker(), "private void runFactoryReset(");

        assertTrue("runFactoryReset phải gọi PrinterProvisioner.forget",
                run.contains("PrinterProvisioner.forget("));
        assertTrue("runFactoryReset phải gửi lệnh qua PrinterProvisioner.factoryReset",
                run.contains("PrinterProvisioner.factoryReset("));
    }

    /**
     * Chuyển máy in sang ZPL KHÔNG được xoá địa chỉ MAC: app vừa nói chuyện được với đúng
     * máy đó. Xoá đi thì lượt sau tự chọn "thiết bị ghép đôi đầu tiên" — có thể là tai nghe
     * hay máy in khác.
     *
     * <p>Chủ dự án chốt ngày 2026-09-14. Sau Factory máy in, máy về CPCL nên nhánh này chạy
     * ngay lượt đầu — đúng lúc người dùng vừa phải ghép lại Bluetooth.
     */
    @Test
    public void chuyenSangZplKhongDuocXoaMac() throws IOException {
        String src = zebraWorker();

        // Bỏ chú thích: chính chú thích giải thích "không qua onError()" không được tính.
        String prepare = body(src, "private boolean preparePrinter(")
                .replaceAll("//[^\n]*", "");
        assertFalse("preparePrinter không được xoá MAC khi vừa chuyển ZPL",
                prepare.contains("clearAddress("));

        String cpclBranch = body(body(src, "private void runPrintTest("),
                "if (report.isCpclMode())").replaceAll("//[^\n]*", "");
        assertFalse("In thử chuyển ZPL không được xoá MAC",
                cpclBranch.contains("clearAddress("));
        assertFalse("In thử chuyển ZPL không được đi qua onError() — hàm đó xoá MAC",
                cpclBranch.contains("onError("));
        assertTrue("In thử chuyển ZPL vẫn phải báo cho màn hình",
                cpclBranch.contains("notifyListener(ZebraStateListener::onError)"));
    }

    /**
     * Việc in chạy nền rồi thì MỌI nhánh kết thúc phải đóng hộp tiến trình. Thiếu một nhánh
     * là để lại một vòng xoay quay mãi trên màn hình phiếu — người dùng không biết in xong
     * hay chưa, và không bấm được gì nữa.
     */
    @Test
    public void moiNhanhKetThucPhaiDongHopTienTrinh() throws IOException {
        String src = source("src/main/java/com/megatech/fms/PrintReceiptActivity.java");

        int at = 0;
        int checked = 0;
        while (true) {
            at = src.indexOf("new ZebraWorker.ZebraStateListener()", at);
            if (at < 0) break;
            String listener = body(src, "new ZebraWorker.ZebraStateListener()");
            // Cắt đúng khối listener đang xét.
            int open = src.indexOf('{', at);
            int depth = 0;
            int end = -1;
            for (int i = open; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) { end = i; break; }
            }
            listener = src.substring(open, end + 1);

            for (String method : new String[]{"onConnectionError", "onError", "onSuccess"}) {
                String methodBody = body(listener, "public void " + method + "()");
                assertTrue("nhánh " + method + " của ZebraStateListener phải đóng hộp tiến trình",
                        methodBody.contains("closeProgressDialog()"));
            }
            checked++;
            at = end;
        }
        assertTrue("không tìm thấy ZebraStateListener nào trong PrintReceiptActivity",
                checked > 0);
    }
}
