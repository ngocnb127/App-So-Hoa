package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.Additive;
import com.megatech.fms.model.BM7501Model.AdditivePresence;
import com.megatech.fms.model.BM7501Model.DefuelMethod;
import com.megatech.fms.model.BM7501Model.DefuelReason;
import com.megatech.fms.model.BM7501Model.HandlingOption;
import com.megatech.fms.model.BM7501Model.MicrobialKit;
import com.megatech.fms.model.BM7501Model.MicrobialResult;
import com.megatech.fms.model.BM7501Model.QcCheck;
import com.megatech.fms.model.BM7501Model.TriState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

/**
 * Golden test bản in BM 75.01.
 *
 * <p>Bản in nhiệt là bản gốc của biểu mẫu nên test bám vào yêu cầu "in đủ, không rút gọn":
 * đủ ba mục A/B/C, đủ hai chữ ký cuối phiếu, đủ các ghi chú pháp lý và mã biểu mẫu.
 *
 * <p>Không còn dấu MẪU/SPECIMEN, không đánh số bản sao và không có chữ ký mục A — chốt với
 * chủ dự án ngày 2026-09-23 sau khi đối chiếu phiếu thật.
 *
 * <p>Test không thay được việc in thử trên ZQ511/ZQ520 thật — đó vẫn là điều kiện nghiệm thu.
 */
public class BM7501PrinterTest {

    // ------------------------------------------------------------------ cấu trúc

    @Test
    public void zpl_coDuBaMuc() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(zpl.contains("A. KHÁCH HÀNG ĐIỀN"));
        assertTrue(zpl.contains("(To be completed by customer)"));
        assertTrue(zpl.contains("B. SKYPEC ĐIỀN"));
        assertTrue(zpl.contains("C. CÁC BÊN XÁC NHẬN THÔNG TIN"));
    }

    @Test
    public void zpl_coTenBieuMauSongNgu_vaMaBieuMau() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(zpl.contains("JET FUEL DEFUEL REQUEST FORM"));
        assertTrue(zpl.contains("PHIẾU YÊU CẦU HÚT NHIÊN LIỆU TỪ TÀU BAY"));
        assertTrue(zpl.contains("BM 75.01/NLHK"));
        assertTrue(zpl.contains("Ban hành/sửa đổi: 01/03"));
    }

    @Test
    public void zpl_giuDuCacGhiChuPhapLyCuaBanGiay() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue("Thiếu ghi chú ưu tiên bơm tàu bay",
                zpl.contains("Ưu tiên sử dụng bơm của tàu bay"));
        assertTrue("Thiếu mô tả tín hiệu phối hợp", zpl.contains("ngón cái"));
        assertTrue("Thiếu ghi chú giao phiếu cho cán bộ đội tra nạp",
                zpl.contains("cán bộ đội tra nạp"));
        assertTrue("Thiếu ghi chú Không xác định được của mục A14",
                zpl.contains("Không xác định được"));
    }

    @Test
    public void zpl_llKhopVoiNoiDung() {
        int ll = llOf(BM7501Printer.createZpl(full(), opts()));
        // Phiếu đầy đủ dài hơn 2000 dots (~25cm) và phải nằm trong giới hạn hợp lý của cuộn giấy.
        assertTrue("^LL = " + ll, ll > 2000);
        assertTrue("^LL = " + ll, ll < 9000);
    }

    // ------------------------------------------------------------------ chữ ký

    @Test
    public void zpl_inDuHaiChuKyKhiCoAnh() {
        String zpl = BM7501Printer.createZpl(full(), opts());
        assertTrue(zpl.contains("^XG" + BM7501Printer.GRF_SKYPEC));
        assertTrue(zpl.contains("^XG" + BM7501Printer.GRF_CUSTOMER_FINAL));
    }

    @Test
    public void zpl_thieuAnhChuKy_vanChuaChoKyTay_khongInAnh() {
        BM7501Model m = full();
        m.setSkypecSignaturePath(null);

        String zpl = BM7501Printer.createZpl(m, opts());
        assertFalse(zpl.contains("^XG" + BM7501Printer.GRF_SKYPEC));
        // Vẫn còn tiêu đề khối chữ ký để ký tay.
        assertTrue(zpl.contains("ĐẠI DIỆN SKYPEC"));
    }

    @Test
    public void zpl_coDuHaiKhoiChuKyCuoiPhieu() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(zpl.contains("Skypec Rep. Name (print) and Signature"));
        assertTrue(zpl.contains("Customer Rep. Name (print) and Signature"));
    }

    // ------------------------------------------------------------------ ô đánh dấu

    /**
     * Bản in phải giữ nguyên các lựa chọn của biểu mẫu giấy dưới dạng ô đánh dấu, kể cả ô
     * KHÔNG được chọn — người đọc phiếu cần thấy hãng đã bỏ qua lựa chọn nào.
     */
    @Test
    public void zpl_inDuMoiLuaChon_keCaOKhongDuocChon() {
        String zpl = BM7501Printer.createZpl(full(), opts());

        assertTrue("Lý do đã chọn phải có tick", isChecked(zpl, "Điều chỉnh tải trọng"));
        assertFalse("Lựa chọn không chọn vẫn in ra nhưng để trống",
                isChecked(zpl, "Bảo dưỡng, sửa chữa"));
        assertTrue("Lựa chọn không chọn vẫn phải in ra",
                textOf(zpl).contains("Bảo dưỡng, sửa chữa tàu bay/Aircraft maintenance"));
        assertFalse(isChecked(zpl, "Bơm của xe tra nạp"));
    }

    @Test
    public void zpl_khongKiemTraViSinh_moiOThietBiDeTrong() {
        BM7501Model m = full();
        m.setCustomerMicrobialKit(null);
        m.setCustomerMicrobialResult(null);
        m.setSkypecMicrobialKit(null);
        m.setSkypecMicrobialResult(null);

        String zpl = BM7501Printer.createZpl(m, opts());
        // Hai mục A và B đều in dải thiết bị/kết quả, và không ô nào được đánh dấu.
        assertEquals("Cả hai mục đều phải in dải thiết bị",
                2, countOccurrences(textOf(zpl), "Hy-lite"));
        assertEquals("Chưa kiểm tra thì không ô nào được tick", 0, countChecked(zpl, "Hy-lite"));
        assertEquals(0, countChecked(zpl, "Mức độ được chấp nhận"));
    }

    @Test
    public void zpl_viSinhDaKiemTra_danhDauDungThietBiVaKetQua() {
        BM7501Model m = full();
        m.setSkypecMicrobialKit(MicrobialKit.FUELSTAT);
        m.setSkypecMicrobialResult(MicrobialResult.WARNING);

        String zpl = BM7501Printer.createZpl(m, opts());
        assertEquals("Cả mục A và mục B cùng chọn Fuelstat", 2, countChecked(zpl, "Fuelstat"));
        // Mục A để kết quả "được chấp nhận", mục B mới là "cảnh báo" -> đúng một ô được tick.
        assertEquals(1, countChecked(zpl, "Mức độ cảnh báo"));
    }

    /** Phiếu cũ chỉ có một cờ chung "đã phổ biến tín hiệu" — in ra là đã thống nhất cả hai. */
    @Test
    public void zpl_phieuCu_coCoPhoBienTinHieu_tickCaHaiTinHieuChuan() {
        BM7501Model m = full();
        m.setSignalsBriefed(true);
        m.setSignalThumbUp(false);
        m.setSignalCrossArms(false);

        String zpl = BM7501Printer.createZpl(m, opts());
        assertTrue(isChecked(zpl, "Giơ ngón cái"));
        assertTrue(isChecked(zpl, "Giơ chéo hai tay"));
    }

    // ------------------------------------------------------------------ dữ liệu

    @Test
    public void zpl_khongInNull_ngayCaKhiPhieuTrong() {
        BM7501Model m = new BM7501Model();
        String zpl = BM7501Printer.createZpl(m, opts());

        assertFalse("Bản in không được chứa chữ null", zpl.contains("null"));
        assertTrue(zpl.contains("....."));
    }

    @Test
    public void zpl_phieuTrong_vanDungCauTruc() {
        String zpl = BM7501Printer.createZpl(new BM7501Model(), opts());
        assertTrue(zpl.startsWith("^XA"));
        assertTrue(zpl.endsWith("^XZ"));
        assertTrue(textOf(zpl).contains("A. KHÁCH HÀNG"));
    }

    @Test
    public void zpl_nhietDoAm_inDung() {
        BM7501Model m = full();
        m.setActualTempC(-5.5d);
        assertTrue(textOf(BM7501Printer.createZpl(m, opts())).contains("-5,5"));
    }

    @Test
    public void zpl_lyDoKhacDai_khongLamVoNhan() {
        BM7501Model m = full();
        m.setReason(DefuelReason.OTHER);
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 40; i++) longReason.append("lý do rất dài ");
        m.setReasonOther(longReason.toString());

        // Nội dung dài làm phiếu dài thêm — chứng tỏ chiều cao bám nội dung thật.
        int ll = llOf(BM7501Printer.createZpl(m, opts()));
        int llShort = llOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(ll > llShort);
    }

    @Test
    public void zpl_phuGia_inDungDanhSach() {
        BM7501Model m = full();
        m.setAdditivePresence(AdditivePresence.PRESENT);
        m.setAdditives(Arrays.asList(Additive.FSII, Additive.AQUARIUS_WMA));

        String zpl = textOf(BM7501Printer.createZpl(m, opts()));
        assertTrue(zpl.contains("FSII"));
        assertTrue(zpl.contains("Aquarius WMA"));
    }

    @Test
    public void zpl_phuongAnXuLy_inDayDuKhiLuuTru() {
        BM7501Model m = full();
        m.setRefuellableWithoutTest(Boolean.FALSE);
        m.setHandling(HandlingOption.STORAGE);
        m.setStorageFrom(new Date(1_000_000L));
        m.setStorageTo(new Date(9_000_000L));

        String zpl = textOf(BM7501Printer.createZpl(m, opts()));
        assertTrue(zpl.contains("Yêu cầu lưu trữ/Storage"));
        assertTrue(zpl.contains("Từ/From"));
        assertTrue(zpl.contains("Đến/To"));
    }

    @Test
    public void zpl_tenDai_khongLamMatChu() {
        BM7501Model m = full();
        m.setCustomerRepName("Nguyễn Trần Hoàng Long Khánh Đức Thịnh Vượng Phát Đạt");

        assertTrue(textOf(BM7501Printer.createZpl(m, opts())).contains("Nguyễn"));
    }

    // ------------------------------------------------------------------ chuyển ngữ

    @Test
    public void chuyenNgu_dungTheoBieuMau() {
        assertEquals("Có / Yes", BM7501Printer.yesNo(Boolean.TRUE));
        assertEquals("Không / No", BM7501Printer.yesNo(Boolean.FALSE));
        assertEquals(".....", BM7501Printer.yesNo(null));
        assertEquals("Đạt / Satisfy", BM7501Printer.qcText(QcCheck.SATISFY));
        assertEquals("Không đạt / Not satisfy", BM7501Printer.qcText(QcCheck.NOT_SATISFY));
    }

    // ------------------------------------------------------------------ dữ liệu mẫu

    /**
     * Gom nội dung chữ của nhãn ZPL: lấy mọi payload ^FD...^FS rồi nối bằng dấu cách.
     * Builder wrap chủ động nên một câu dài nằm trên nhiều trường — tìm chuỗi liền mạch
     * trong ZPL thô sẽ trượt, còn kiểm tra trên chuỗi đã gom mới đúng ý "phiếu có in nội dung này".
     */
    /**
     * Bỏ các trường đắp chồng của cơ chế in đậm (bản sao lệch đúng 1 dot theo trục X), để
     * test đếm được số lần một nội dung THỰC SỰ xuất hiện trên phiếu.
     */
    private static String plain(String zpl) {
        String[] parts = zpl.split("\\^FS");
        StringBuilder out = new StringBuilder();
        String prevRest = null;
        int prevX = Integer.MIN_VALUE;
        for (String part : parts) {
            int fo = part.lastIndexOf("^FO");
            int comma = fo < 0 ? -1 : part.indexOf(',', fo);
            if (comma > 0) {
                try {
                    int x = Integer.parseInt(part.substring(fo + 3, comma).trim());
                    String rest = part.substring(comma);
                    if (rest.equals(prevRest) && x == prevX + 1) continue;
                    prevRest = rest;
                    prevX = x;
                } catch (NumberFormatException ignored) {
                    prevRest = null;
                }
            }
            out.append(part).append("^FS");
        }
        return out.toString();
    }

    private static String textOf(String zpl) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\^FD(.*?)\\^FS", java.util.regex.Pattern.DOTALL)
                .matcher(plain(zpl));
        while (m.find()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(m.group(1));
        }
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static BM7501Printer.Options opts() {
        return new BM7501Printer.Options().branchName("Nội Bài").zq520(false);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static int llOf(String zpl) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\^LL(\\d+)").matcher(zpl);
        assertTrue("Nhãn không có ^LL", m.find());
        return Integer.parseInt(m.group(1));
    }

    /**
     * Ô đánh dấu nay là hình vuông vẽ thật: ^GB là khung, hai ^GD là nét tick. Một lựa chọn
     * được chọn khi giữa khung và chữ của nó có ^GD.
     */
    private static boolean isChecked(String rawZpl, String label) {
        String zpl = plain(rawZpl);
        int text = zpl.indexOf("^FD" + label);
        assertTrue("Bản in không có lựa chọn: " + label, text > 0);
        int box = zpl.lastIndexOf("^GB", text);
        return box > 0 && zpl.substring(box, text).contains("^GD");
    }

    private static int countChecked(String rawZpl, String label) {
        String zpl = plain(rawZpl);
        int count = 0;
        int from = 0;
        while (true) {
            int text = zpl.indexOf("^FD" + label, from);
            if (text < 0) return count;
            int box = zpl.lastIndexOf("^GB", text);
            if (box > 0 && zpl.substring(box, text).contains("^GD")) count++;
            from = text + 3;
        }
    }

    private static BM7501Model full() {
        BM7501Model m = new BM7501Model();
        m.setLocalNumber("75-NBA-51F12345-A3F1-260810-001");
        m.setDate(new Date(1_700_000_000_000L));
        m.setAirlineName("VIETNAM AIRLINES");
        m.setAirportName("NỘI BÀI");

        m.setCustomerRepName("Nguyễn Văn A");
        m.setCustomerTitle("Cơ trưởng");
        m.setCustomerTel("0900000000");
        m.setCustomerFax("0240000000");
        m.setAircraftType("A321");
        m.setAircraftReg("VN-A123");
        m.setReason(DefuelReason.LOAD_ADJUSTMENT);
        m.setTankDrainSampled(Boolean.TRUE);
        m.setCustomerMicrobialTestPerformed(TriState.YES);
        m.setCustomerMicrobialKit(MicrobialKit.FUELSTAT);
        m.setCustomerMicrobialResult(MicrobialResult.NORMAL);
        m.setAdditivePresence(AdditivePresence.NONE);
        m.setPrevLocation1("SGN");
        m.setPrevGrade1("JET A-1");
        m.setPrevLocation2("DAD");
        m.setPrevGrade2("Không xác định được");

        m.setVac(QcCheck.SATISFY);
        m.setCwd(QcCheck.SATISFY);
        m.setDensityKgM3(795.2d);
        m.setConductivityPsM(150d);

        m.setDefuellerTruckNo("51F-123.45");
        m.setStartTime(new Date(1_700_000_100_000L));
        m.setEndTime(new Date(1_700_001_500_000L));
        m.setMethod(DefuelMethod.AIRCRAFT_PUMP);
        m.setSignalsBriefed(true);
        m.setExpectedKg(3000d);
        m.setActualKg(2980d);
        m.setActualTempC(28.5d);
        m.setActualDensityKgM3(795.2d);
        m.setGallon(990d);
        m.setLiter(3748d);
        m.setRefuellableWithoutTest(Boolean.TRUE);

        m.setSkypecSignaturePath("/x/s.png");
        m.setCustomerFinalSignaturePath("/x/c.png");
        m.setSkypecRepName("Trần Văn B");
        return m;
    }
}
