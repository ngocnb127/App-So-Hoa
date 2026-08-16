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
 * đủ ba mục A/B/C, đủ ba chữ ký, đủ các ghi chú pháp lý và mã biểu mẫu.
 *
 * <p>Test không thay được việc in thử trên ZQ511/ZQ520 thật — đó vẫn là điều kiện nghiệm thu.
 */
public class BM7501PrinterTest {

    // ------------------------------------------------------------------ cấu trúc

    @Test
    public void zpl_coDuBaMuc() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(zpl.contains("A. KHÁCH HÀNG / CUSTOMER"));
        assertTrue(zpl.contains("B. SKYPEC"));
        assertTrue(zpl.contains("C. XÁC NHẬN / CONFIRMATION"));
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
        String zpl = BM7501Printer.createZpl(full(), opts());
        int ll = Integer.parseInt(zpl.substring(zpl.indexOf("^LL") + 3, zpl.indexOf('\n')));
        // Phiếu đầy đủ dài hơn 2000 dots (~25cm) và phải nằm trong giới hạn hợp lý của cuộn giấy.
        assertTrue("^LL = " + ll, ll > 2000);
        assertTrue("^LL = " + ll, ll < 6000);
    }

    // ------------------------------------------------------------------ chữ ký

    @Test
    public void zpl_inDuBaChuKyKhiCoAnh() {
        String zpl = BM7501Printer.createZpl(full(), opts());
        assertTrue(zpl.contains("^XG" + BM7501Printer.GRF_CUSTOMER_SECTION_A));
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
    public void zpl_coCaHaiLanKyCuaKhachHang() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts()));
        assertTrue(zpl.contains("Customer Rep. - Section A"));
        assertTrue(zpl.contains("Customer Rep. (Ký, ghi rõ họ tên)"));
    }

    // ------------------------------------------------------------------ bản sao

    @Test
    public void zpl_banSao_khiInLai() {
        BM7501Model m = full();
        m.setReprintCount(1);

        String zpl = textOf(BM7501Printer.createZpl(m, opts()));
        assertTrue(zpl.contains("BẢN SAO / COPY"));
        assertTrue(zpl.contains("Lần in: 2"));
    }

    @Test
    public void zpl_banGoc_khongCoDauBanSao() {
        assertFalse(textOf(BM7501Printer.createZpl(full(), opts())).contains("BẢN SAO"));
    }

    @Test
    public void zpl_banSao_theoCoTuyChon() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts().copy(true)));
        assertTrue(zpl.contains("BẢN SAO / COPY"));
    }

    // ------------------------------------------------------------------ bản thử

    @Test
    public void zpl_banThu_dongDauMauOCaDauVaCuoiPhieu() {
        String zpl = textOf(BM7501Printer.createZpl(full(), opts().specimen(true)));

        assertTrue(zpl.contains("MẪU / SPECIMEN"));
        assertTrue(zpl.contains("KHÔNG CÓ GIÁ TRỊ PHÁP LÝ"));
        // In hai lần: đầu phiếu và chân phiếu, phòng khi tờ giấy bị xé rời.
        assertEquals(2, countOccurrences(zpl, "NOT A LEGAL DOCUMENT"));
    }

    @Test
    public void zpl_banThat_khongCoDauMau() {
        assertFalse(textOf(BM7501Printer.createZpl(full(), opts())).contains("SPECIMEN"));
    }

    @Test
    public void escp_banThu_dongDauMau() {
        String text = BM7501Printer.createEscpText(full(), opts().specimen(true));
        assertTrue(text.contains("MẪU / SPECIMEN"));
        assertTrue(text.contains("KHÔNG CÓ GIÁ TRỊ PHÁP LÝ"));
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
        assertTrue(textOf(BM7501Printer.createZpl(m, opts())).contains("-5.5"));
    }

    @Test
    public void zpl_khongCoViSinh_ghiKhongYeuCau() {
        BM7501Model m = full();
        m.setVac(QcCheck.SATISFY);
        m.setCwd(QcCheck.SATISFY);
        m.setContaminationSuspected(false);
        m.setCustomerRequestedMicrobial(false);
        m.setSkypecMicrobialKit(null);
        m.setSkypecMicrobialResult(null);

        assertTrue(textOf(BM7501Printer.createZpl(m, opts())).contains("Không yêu cầu / Not required"));
    }

    @Test
    public void zpl_lyDoKhacDai_khongLamVoNhan() {
        BM7501Model m = full();
        m.setReason(DefuelReason.OTHER);
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 40; i++) longReason.append("lý do rất dài ");
        m.setReasonOther(longReason.toString());

        String zpl = BM7501Printer.createZpl(m, opts());
        int ll = Integer.parseInt(zpl.substring(zpl.indexOf("^LL") + 3, zpl.indexOf('\n')));
        // Nội dung dài làm phiếu dài thêm — chứng tỏ chiều cao bám nội dung thật.
        int llShort = Integer.parseInt(shortLl(BM7501Printer.createZpl(full(), opts())));
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
        assertTrue(zpl.contains("Yêu cầu lưu trữ / Storage"));
        assertTrue(zpl.contains("Lưu trữ từ / From"));
        assertTrue(zpl.contains("Lưu trữ đến / To"));
    }

    @Test
    public void zpl_tenDai_khongLamMatChu() {
        BM7501Model m = full();
        m.setCustomerRepName("Nguyễn Trần Hoàng Long Khánh Đức Thịnh Vượng Phát Đạt");

        assertTrue(textOf(BM7501Printer.createZpl(m, opts())).contains("Nguyễn"));
    }

    // ------------------------------------------------------------------ ESC/P

    @Test
    public void escp_coDuBaMucVaBaChuKy() {
        String text = BM7501Printer.createEscpText(full(), opts());
        assertTrue(text.contains("A. KHÁCH HÀNG / CUSTOMER"));
        assertTrue(text.contains("B. SKYPEC"));
        assertTrue(text.contains("C. XÁC NHẬN / CONFIRMATION"));
        assertTrue(text.contains("Customer Rep. - Section A"));
        assertTrue(text.contains("ĐẠI DIỆN SKYPEC"));
    }

    @Test
    public void escp_khongInNull() {
        assertFalse(BM7501Printer.createEscpText(new BM7501Model(), opts()).contains("null"));
    }

    @Test
    public void escp_khongVuotKhoGiay() {
        for (String line : BM7501Printer.createEscpText(full(), opts()).split("\n")) {
            assertTrue("Dòng dài " + line.length() + ": " + line, line.length() <= 70);
        }
    }

    @Test
    public void escp_banSao_khiInLai() {
        BM7501Model m = full();
        m.setReprintCount(2);
        assertTrue(BM7501Printer.createEscpText(m, opts()).contains("Lần in: 3"));
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
    private static String textOf(String zpl) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\^FD(.*?)\\^FS", java.util.regex.Pattern.DOTALL)
                .matcher(zpl);
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

    private static String shortLl(String zpl) {
        return zpl.substring(zpl.indexOf("^LL") + 3, zpl.indexOf('\n'));
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
        m.setConductivityRequired(true);
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

        m.setCustomerSectionASignaturePath("/x/a.png");
        m.setSkypecSignaturePath("/x/s.png");
        m.setCustomerFinalSignaturePath("/x/c.png");
        m.setSkypecRepName("Trần Văn B");
        m.setCustomerRepFinalName("Nguyễn Văn A");
        return m;
    }
}
