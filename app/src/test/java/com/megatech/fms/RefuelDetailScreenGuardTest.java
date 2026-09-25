package com.megatech.fms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Ràng buộc UI của màn tra nạp mà không có unit test nào khác giữ hộ.
 *
 * <p>Các ràng buộc này nằm trong Activity nên không tách ra hàm thuần được; test đọc thẳng mã
 * nguồn để chống hồi quy. Thà canh thô còn hơn không canh: cả ba lỗi bên dưới đều đã từng
 * làm màn hình chết trên xe thật.
 */
public class RefuelDetailScreenGuardTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String refuelDetail() throws IOException {
        return source("src/main/java/com/megatech/fms/RefuelDetailActivity.java");
    }

    /** Nút Dừng khẩn phải LUÔN hiện, LUÔN bấm được — ở mọi trạng thái, kể cả ENDED. */
    @Test
    public void nutDungKhanKhongBaoGioBiTat() throws IOException {
        String src = refuelDetail();

        assertFalse("btnForceStop không được disable ở bất kỳ trạng thái nào",
                src.contains("btnForceStop.setEnabled(false)"));
        assertFalse("btnForceStop không được ẩn",
                src.contains("btnForceStop.setVisibility(View.GONE)"));
        assertFalse("btnForceStop không được ẩn",
                src.contains("btnForceStop.setVisibility(View.INVISIBLE)"));
    }

    /**
     * Chốt mẻ không vào được Room thì KHÔNG được chặn bằng hộp thoại — chỉ ghi nhật ký rồi
     * đi tiếp.
     *
     * <p>Chủ dự án chốt ngày 2026-09-05: không chặn gì, vẫn cho luồng chạy tiếp. Hộp thoại
     * "Thử lại / Tiếp tục" trước đây vẫn là một lần chặn giữa lúc người dùng đứng cạnh tàu
     * bay, mà "Thử lại" thì vô nghĩa khi nguyên nhân cố định (chuyến chưa phân công cho xe).
     */
    @Test
    public void chotMeThatBaiKhongChanMaDiTiep() throws IOException {
        String src = refuelDetail();

        assertFalse("không được còn hộp thoại chặn ở đường chốt mẻ",
                src.contains("private void showEndSaveFailed("));

        int at = src.indexOf("if (!RefuelItemData.isCommitted(itemData)) {");
        assertTrue("không tìm thấy nhánh chốt mẻ thất bại", at > 0);

        String body = src.substring(at, Math.min(src.length(), at + 900));
        assertTrue("phải ghi nguyên nhân vào nhật ký",
                body.contains("logEndSaveFailureCause(itemData)"));
        assertTrue("phải đi tiếp ngay, không chờ người dùng chọn",
                body.contains("continueWithoutSavedEnd(itemData)"));
    }

    /**
     * Nhật ký phải nói được VÌ SAO, không chỉ nói là hỏng.
     *
     * <p>Hai nguyên nhân hoàn toàn khác nhau — chuyến chưa phân công cho xe (lý do cố định,
     * thử lại vô ích) và bản gốc bị dịch (lý do nhất thời) — phải phân biệt được khi đọc lại
     * {@code fms.log} sau ca.
     */
    @Test
    public void nhatKyChotMeThatBaiPhaiNeuNguyenNhan() throws IOException {
        String src = refuelDetail();
        int at = src.indexOf("private void logEndSaveFailureCause(");
        assertTrue("không tìm thấy logEndSaveFailureCause", at > 0);

        String body = src.substring(at, Math.min(src.length(), at + 3500));
        assertTrue("phải phân biệt ca chuyến chưa phân công",
                body.contains("CHUYEN_CHUA_PHAN_CONG"));
        assertTrue("phải phân biệt ca mẻ mang dấu xe khác",
                body.contains("ME_MANG_DAU_XE_KHAC"));
        assertTrue("phải phân biệt ca bản gốc bị dịch",
                body.contains("BAN_GOC_BI_DICH"));
        assertTrue("phải ghi ra fms.log", body.contains("Logger.appendLog("));
        assertTrue("phải ghi cả nhật ký bất thường để lọc nhanh cuối ca",
                body.contains("Logger.appendRefuelAnomaly("));
    }

    /** Back mặc định phải được PHÉP; chỉ màn tra nạp override và chỉ chặn khi mẻ đang chạy. */
    @Test
    public void backMacDinhDuocPhep() throws IOException {
        String base = source("src/main/java/com/megatech/fms/BaseActivity.java");

        assertTrue("BaseActivity phải có hook isBackBlocked", base.contains("isBackBlocked()"));
        assertTrue("mặc định phải cho Back", base.contains("super.onBackPressed()"));
        assertTrue("màn tra nạp phải override hook",
                refuelDetail().contains("protected boolean isBackBlocked()"));
    }

    /** Hộp nhập tay bắt buộc phải luôn có lối ra. */
    @Test
    public void hopNhapTayLuonCoLoiRa() throws IOException {
        assertTrue("hộp nhập tay bắt buộc phải có nút \"Để sau\"",
                refuelDetail().contains("R.string.input_later"));
    }

    /** Hộp báo lỗi nhập tay phải chỉ đúng ô sai. */
    @Test
    public void baoLoiNhapTayChiDungOSai() {
        assertTrue(RefuelDetailActivity.manualInputProblems(3029, 56636400, 56639429).isEmpty());

        List<String> khongSanLuong =
                RefuelDetailActivity.manualInputProblems(0, 56636400, 56639429);
        assertEquals(1, khongSanLuong.size());
        assertEquals(RefuelDetailActivity.PROBLEM_AMOUNT, khongSanLuong.get(0));

        List<String> dongHoDauSai =
                RefuelDetailActivity.manualInputProblems(3029, 0, 56639429);
        assertTrue(dongHoDauSai.contains(RefuelDetailActivity.PROBLEM_START_METER));

        List<String> dongHoCuoiSai =
                RefuelDetailActivity.manualInputProblems(3029, 100, 500);
        assertTrue(dongHoCuoiSai.contains(RefuelDetailActivity.PROBLEM_END_METER));
    }
}
