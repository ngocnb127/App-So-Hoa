package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.helpers.BM7501Validator.Finding;
import com.megatech.fms.helpers.BM7501Validator.Severity;
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

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Test validation BM 75.01, tập trung vào đúng những chỗ bản phương án v1 làm sai
 * và đã bị review bác.
 */
public class BM7501ValidatorTest {

    @After
    public void tearDown() {
        BM7501Thresholds.resetDefaults();
    }

    // ------------------------------------------------------------------ MỤC A

    @Test
    public void sectionA_khongBatBuocViSinh_khiHangChuaKiemTra() {
        BM7501Model m = validSectionA();
        m.setCustomerMicrobialTestPerformed(TriState.NO);
        m.setCustomerMicrobialKit(null);
        m.setCustomerMicrobialResult(null);

        assertFalse(hasFieldError(BM7501Validator.validateSectionA(m), "customerMicrobialKit"));
        assertFalse(hasFieldError(BM7501Validator.validateSectionA(m), "customerMicrobialResult"));
    }

    @Test
    public void sectionA_batBuocViSinh_khiHangDaKiemTra() {
        BM7501Model m = validSectionA();
        m.setCustomerMicrobialTestPerformed(TriState.YES);
        m.setCustomerMicrobialKit(null);
        m.setCustomerMicrobialResult(null);

        List<Finding> f = BM7501Validator.validateSectionA(m);
        assertTrue(hasFieldError(f, "customerMicrobialKit"));
        assertTrue(hasFieldError(f, "customerMicrobialResult"));
    }

    @Test
    public void sectionA_thietBiKhac_phaiGhiRo() {
        BM7501Model m = validSectionA();
        m.setCustomerMicrobialTestPerformed(TriState.YES);
        m.setCustomerMicrobialKit(MicrobialKit.OTHER);
        m.setCustomerMicrobialKitOther("  ");
        m.setCustomerMicrobialResult(MicrobialResult.NORMAL);

        assertTrue(hasFieldError(BM7501Validator.validateSectionA(m), "customerMicrobialKitOther"));
    }

    @Test
    public void sectionA_phuGia_none_loaiTruPhuGiaCuThe() {
        BM7501Model m = validSectionA();
        m.setAdditivePresence(AdditivePresence.NONE);
        m.setAdditives(Arrays.asList(Additive.FSII));

        assertTrue(hasFieldError(BM7501Validator.validateSectionA(m), "additives"));
    }

    @Test
    public void sectionA_phuGia_undetermined_loaiTruPhuGiaCuThe() {
        BM7501Model m = validSectionA();
        m.setAdditivePresence(AdditivePresence.UNDETERMINED);
        m.setAdditives(Arrays.asList(Additive.BIOCIDE, Additive.AQUARIUS_WMA));

        assertTrue(hasFieldError(BM7501Validator.validateSectionA(m), "additives"));
    }

    @Test
    public void sectionA_phuGia_present_phaiChonItNhatMot() {
        BM7501Model m = validSectionA();
        m.setAdditivePresence(AdditivePresence.PRESENT);
        m.setAdditives(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionA(m), "additives"));
    }

    @Test
    public void sectionA_lyDoKhac_phaiGhiRo() {
        BM7501Model m = validSectionA();
        m.setReason(DefuelReason.OTHER);
        m.setReasonOther(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionA(m), "reasonOther"));
    }

    @Test
    public void sectionA_khongXacDinhDuoc_vanHopLe() {
        BM7501Model m = validSectionA();
        m.setPrevGrade1("Không xác định được");
        m.setPrevGrade2("Undetermined");

        assertFalse(BM7501Validator.hasError(BM7501Validator.validateSectionA(m)));
    }

    @Test
    public void sectionA_hopLeDayDu_khongLoi() {
        assertFalse(BM7501Validator.hasError(BM7501Validator.validateSectionA(validSectionA())));
    }

    // ------------------------------------------------------------------ MỤC B

    @Test
    public void sectionB_viSinh_khongSuyTuA10() {
        // A10 = Không xả mẫu, nhưng VAC/CWD đạt và không nghi ngờ, không ai yêu cầu
        // → KHÔNG bắt buộc vi sinh. Đây chính là lỗi của bản v1.
        BM7501Model m = validSectionB();
        m.setTankDrainSampled(Boolean.FALSE);

        List<Finding> f = BM7501Validator.validateSectionB(m);
        assertFalse(hasFieldError(f, "skypecMicrobialKit"));
        assertFalse(hasFieldError(f, "microbialReason"));
    }

    @Test
    public void sectionB_viSinh_batBuoc_khiVacKhongDat() {
        BM7501Model m = validSectionB();
        m.setVac(QcCheck.NOT_SATISFY);

        List<Finding> f = BM7501Validator.validateSectionB(m);
        assertTrue(hasFieldError(f, "skypecMicrobialKit"));
        assertTrue(hasFieldError(f, "skypecMicrobialResult"));
        assertTrue(hasFieldError(f, "microbialReason"));
    }

    @Test
    public void sectionB_viSinh_batBuoc_khiNghiNgoNhiemViSinh() {
        BM7501Model m = validSectionB();
        m.setContaminationSuspected(true);

        assertTrue(hasFieldError(BM7501Validator.validateSectionB(m), "skypecMicrobialKit"));
    }

    @Test
    public void sectionB_viSinh_batBuoc_khiKhachYeuCau() {
        BM7501Model m = validSectionB();
        m.setCustomerRequestedMicrobial(true);

        assertTrue(hasFieldError(BM7501Validator.validateSectionB(m), "skypecMicrobialKit"));
    }

    @Test
    public void sectionB_doDanDien_chiBatBuocKhiCoYeuCau() {
        BM7501Model m = validSectionB();
        m.setConductivityRequired(false);
        m.setConductivityPsM(null);
        assertFalse(hasFieldError(BM7501Validator.validateSectionB(m), "conductivityPsM"));

        m.setConductivityRequired(true);
        assertTrue(hasFieldError(BM7501Validator.validateSectionB(m), "conductivityPsM"));
    }

    // ------------------------------------------------------------------ MỤC C

    @Test
    public void sectionC_nhietDoAmKhongPhaiLoi_chiLaCanhBaoKhiNgoaiKhoang() {
        BM7501Model m = validSectionC();
        m.setActualTempC(-5d);

        List<Finding> f = BM7501Validator.validateSectionC(m);
        assertFalse("Nhiệt độ âm phải hợp lệ", BM7501Validator.hasError(f));
    }

    @Test
    public void sectionC_nhietDoNgoaiKhoang_laCanhBao_khongChan() {
        BM7501Model m = validSectionC();
        m.setActualTempC(120d);

        List<Finding> f = BM7501Validator.validateSectionC(m);
        assertFalse(BM7501Validator.hasError(f));
        assertTrue(hasFinding(f, "actualTempC", Severity.WARNING));
    }

    @Test
    public void sectionC_nhietDoNgoaiKhoang_thanhLoi_khiNghiepVuDaDuyet() {
        BM7501Thresholds.setBlocking(true);
        BM7501Model m = validSectionC();
        m.setActualTempC(120d);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "actualTempC"));
    }

    @Test
    public void sectionC_klrNgoaiKhoang_laCanhBao() {
        BM7501Model m = validSectionC();
        m.setActualDensityKgM3(900d);

        List<Finding> f = BM7501Validator.validateSectionC(m);
        assertFalse(BM7501Validator.hasError(f));
        assertTrue(hasFinding(f, "actualDensityKgM3", Severity.WARNING));
    }

    @Test
    public void sectionC_klrAmLaLoi() {
        BM7501Model m = validSectionC();
        m.setActualDensityKgM3(-1d);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "actualDensityKgM3"));
    }

    @Test
    public void sectionC_c10_batBuoc_khiCoVanDeChatLuong_duC9DongY() {
        // C9 = Đồng ý nạp lại, nhưng CWD không đạt → vẫn phải chọn phương án xử lý.
        // Bản v1 sai chỗ này vì chỉ ràng buộc theo C9 = NO.
        BM7501Model m = validSectionC();
        m.setRefuellableWithoutTest(Boolean.TRUE);
        m.setCwd(QcCheck.NOT_SATISFY);
        m.setHandling(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "handling"));
    }

    @Test
    public void sectionC_c10_batBuoc_khiKhongNapLaiNgay() {
        BM7501Model m = validSectionC();
        m.setRefuellableWithoutTest(Boolean.FALSE);
        m.setHandling(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "handling"));
    }

    @Test
    public void sectionC_c10_khongBatBuoc_khiNapLaiDuocVaKhongCoVanDe() {
        BM7501Model m = validSectionC();
        m.setRefuellableWithoutTest(Boolean.TRUE);
        m.setHandling(null);

        assertFalse(hasFieldError(BM7501Validator.validateSectionC(m), "handling"));
    }

    @Test
    public void sectionC_luuTru_batBuocTuDen_vaDenPhaiSauTu() {
        BM7501Model m = validSectionC();
        m.setRefuellableWithoutTest(Boolean.FALSE);
        m.setHandling(HandlingOption.STORAGE);
        m.setStorageFrom(new Date(2_000_000L));
        m.setStorageTo(new Date(1_000_000L));

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "storageTo"));
    }

    @Test
    public void sectionC_napLaiDuVanDe_phaiGhiRo() {
        BM7501Model m = validSectionC();
        m.setRefuellableWithoutTest(Boolean.FALSE);
        m.setHandling(HandlingOption.REFUEL_DESPITE_ISSUE);
        m.setHandlingNote(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "handlingNote"));
    }

    @Test
    public void sectionC_tinHieu_chapNhanKhiDaPhoBien() {
        BM7501Model m = validSectionC();
        m.setSignalsBriefed(true);
        m.setOtherSignal(null);

        assertFalse(hasFieldError(BM7501Validator.validateSectionC(m), "signalsBriefed"));
    }

    @Test
    public void sectionC_tinHieu_chapNhanKhiCoPhuongThucKhac() {
        BM7501Model m = validSectionC();
        m.setSignalsBriefed(false);
        m.setOtherSignal("Dùng bộ đàm kênh 3");

        assertFalse(hasFieldError(BM7501Validator.validateSectionC(m), "signalsBriefed"));
    }

    @Test
    public void sectionC_tinHieu_loiKhiKhongPhoBienVaKhongCoPhuongThucKhac() {
        BM7501Model m = validSectionC();
        m.setSignalsBriefed(false);
        m.setOtherSignal(null);

        assertTrue(hasFieldError(BM7501Validator.validateSectionC(m), "signalsBriefed"));
    }

    @Test
    public void sectionC_khongApRangBuoc3den180PhutCuaMeHut() {
        // Rule 3–180 phút thuộc nghiệp vụ mẻ hút, không sao chép vào validator chứng từ.
        BM7501Model m = validSectionC();
        m.setStartTime(new Date(0L));
        m.setEndTime(new Date(60_000L)); // 1 phút

        assertFalse(BM7501Validator.hasError(BM7501Validator.validateSectionC(m)));
    }

    // -------------------------------------------------------------- trước khi ký

    @Test
    public void truocKhiKy_batBuocDuBaChuKy() {
        BM7501Model m = fullyValid();
        m.setCustomerSectionASignaturePath(null);

        List<Finding> f = BM7501Validator.validateForSigning(m);
        assertTrue(hasFieldError(f, "customerSectionASignaturePath"));

        m.setCustomerSectionASignaturePath("/x/a.png");
        m.setSkypecSignaturePath(null);
        assertTrue(hasFieldError(BM7501Validator.validateForSigning(m), "skypecSignaturePath"));

        m.setSkypecSignaturePath("/x/s.png");
        m.setCustomerFinalSignaturePath(null);
        assertTrue(hasFieldError(BM7501Validator.validateForSigning(m), "customerFinalSignaturePath"));
    }

    @Test
    public void truocKhiKy_phieuDayDu_khongLoi() {
        List<Finding> f = BM7501Validator.validateForSigning(fullyValid());
        assertFalse(describe(f), BM7501Validator.hasError(f));
    }

    @Test
    public void errorsOnly_locBoCanhBao() {
        BM7501Model m = validSectionC();
        m.setActualTempC(120d);
        m.setMethod(null);

        List<Finding> all = BM7501Validator.validateSectionC(m);
        List<Finding> errors = BM7501Validator.errorsOnly(all);

        assertTrue(all.size() > errors.size());
        for (Finding f : errors) {
            assertEquals(Severity.ERROR, f.getSeverity());
        }
    }

    // ------------------------------------------------------------------- dựng dữ liệu

    private static BM7501Model validSectionA() {
        BM7501Model m = new BM7501Model();
        m.setCustomerRepName("Nguyễn Văn A");
        m.setAircraftType("A321");
        m.setAircraftReg("VN-A123");
        m.setReason(DefuelReason.LOAD_ADJUSTMENT);
        m.setTankDrainSampled(Boolean.TRUE);
        m.setCustomerMicrobialTestPerformed(TriState.NO);
        m.setAdditivePresence(AdditivePresence.NONE);
        m.setPrevLocation1("SGN");
        m.setPrevGrade1("JET A-1");
        m.setPrevLocation2("HAN");
        m.setPrevGrade2("JET A-1");
        return m;
    }

    private static BM7501Model validSectionB() {
        BM7501Model m = validSectionA();
        m.setVac(QcCheck.SATISFY);
        m.setCwd(QcCheck.SATISFY);
        m.setDensityKgM3(795d);
        m.setConductivityRequired(false);
        return m;
    }

    private static BM7501Model validSectionC() {
        BM7501Model m = validSectionB();
        m.setDefuellerTruckNo("51F-123.45");
        m.setStartTime(new Date(1_000_000L));
        m.setEndTime(new Date(2_000_000L));
        m.setMethod(DefuelMethod.AIRCRAFT_PUMP);
        m.setSignalsBriefed(true);
        m.setExpectedKg(3000d);
        m.setActualKg(2980d);
        m.setActualTempC(28.5d);
        m.setActualDensityKgM3(795.2d);
        m.setGallon(990d);
        m.setLiter(3748d);
        m.setRefuellableWithoutTest(Boolean.TRUE);
        return m;
    }

    private static BM7501Model fullyValid() {
        BM7501Model m = validSectionC();
        m.setCustomerSectionASignaturePath("/x/a.png");
        m.setSkypecSignaturePath("/x/s.png");
        m.setCustomerFinalSignaturePath("/x/c.png");
        m.setSkypecRepName("Trần Văn B");
        m.setCustomerRepFinalName("Nguyễn Văn A");
        return m;
    }

    private static boolean hasFieldError(List<Finding> findings, String field) {
        return hasFinding(findings, field, Severity.ERROR);
    }

    private static boolean hasFinding(List<Finding> findings, String field, Severity severity) {
        for (Finding f : findings) {
            if (field.equals(f.getField()) && f.getSeverity() == severity) return true;
        }
        return false;
    }

    private static String describe(List<Finding> findings) {
        StringBuilder b = new StringBuilder();
        for (Finding f : findings) {
            b.append(f).append("; ");
        }
        return b.toString();
    }
}
