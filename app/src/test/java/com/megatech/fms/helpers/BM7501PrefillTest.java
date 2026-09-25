package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * Điền sẵn phiếu BM 75.01 từ mẻ hút.
 *
 * <p>Dùng Robolectric vì {@code RefuelItemData} nằm trên nhánh có phụ thuộc Android,
 * giống các test đồng bộ mẻ hút hiện có.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BM7501PrefillTest {

    @Test
    public void lienKetBangUniqueIdCuaMeHut_khongPhaiFlightUniqueId() {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId("me-hut-1");
        item.setFlightUniqueId("chuyen-bay-1");

        BM7501Model m = BM7501Prefill.fromRefuelItem(item);

        assertEquals("me-hut-1", m.getRefuelItemUniqueId());
    }

    @Test
    public void soPhieu_layTuReceiptNumberCuaMeHut() {
        RefuelItemData item = new RefuelItemData();
        item.setReceiptNumber("  HT250810-001  ");

        assertEquals("HT250810-001", BM7501Prefill.fromRefuelItem(item).getLocalNumber());
    }

    @Test
    public void soPhieu_deTrongKhiMeHutChuaXuatPhieu() {
        RefuelItemData item = new RefuelItemData();
        item.setReceiptNumber(null);
        assertNull(BM7501Prefill.fromRefuelItem(item).getLocalNumber());

        item.setReceiptNumber("   ");
        assertNull(BM7501Prefill.fromRefuelItem(item).getLocalNumber());
    }

    @Test
    public void quyDoiTyTrong_kgPerL_sangKgPerM3() {
        assertEquals(795.2d, BM7501Prefill.densityToKgM3(0.7952d), 0.0001d);
        assertNull("Chưa có tỷ trọng thì để trống, không điền 0",
                BM7501Prefill.densityToKgM3(0d));
        assertNull(BM7501Prefill.densityToKgM3(-1d));
    }

    @Test
    public void dienSanSoLieuDoDuocCuaMeHut() {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId("me-hut-2");
        item.setAircraftType("A321");
        item.setAircraftCode("VN-A123");
        item.setTruckNo("51F-123.45");
        item.setDensity(0.7952d);
        item.setManualTemperature(28.5d);

        BM7501Model m = BM7501Prefill.fromRefuelItem(item);

        assertEquals("A321", m.getAircraftType());
        assertEquals("VN-A123", m.getAircraftReg());
        assertEquals("51F-123.45", m.getDefuellerTruckNo());
        assertEquals(28.5d, m.getActualTempC(), 0.0001d);
        assertEquals(795.2d, m.getActualDensityKgM3(), 0.0001d);
    }

    @Test
    public void macDinhPhuongThucHut_laBomTauBay() {
        // Biểu mẫu ghi rõ ưu tiên sử dụng bơm của tàu bay.
        assertEquals(BM7501Model.DefuelMethod.AIRCRAFT_PUMP,
                BM7501Prefill.fromRefuelItem(new RefuelItemData()).getMethod());
    }

    @Test
    public void luongDuKien_chiQuyDoiKhiDaCoTyTrong() {
        RefuelItemData item = new RefuelItemData();
        item.setEstimateAmount(1000d);
        item.setDensity(0d);

        assertNull("Chưa có tỷ trọng thì không được đoán số kg",
                BM7501Prefill.fromRefuelItem(item).getExpectedKg());

        item.setDensity(0.8d);
        Double kg = BM7501Prefill.fromRefuelItem(item).getExpectedKg();
        assertNotNull(kg);
        // 1000 USG * 3.7854 l/USG * 0.8 kg/l = 3028.32 kg
        assertEquals(3028.32d, kg, 0.5d);
    }

    /**
     * Chốt 2026-09-23: các mục "đạt" và loại nhiên liệu JET A-1 được tick/điền sẵn vì gần như
     * mọi phiếu đều như vậy; phần khách hàng khai thì vẫn để trống.
     */
    @Test
    public void mucDat_vaLoaiNhienLieu_dienSan() {
        BM7501Model m = BM7501Prefill.fromRefuelItem(new RefuelItemData());

        assertEquals(Boolean.TRUE, m.getTankDrainSampled());
        assertEquals(BM7501Model.QcCheck.SATISFY, m.getVac());
        assertEquals(BM7501Model.QcCheck.SATISFY, m.getCwd());
        assertEquals("JET A-1", m.getPrevGrade1());
        assertEquals("JET A-1", m.getPrevGrade2());
    }

    @Test
    public void mucAVaMucB_deTrong_choNhanVienNhap() {
        BM7501Model m = BM7501Prefill.fromRefuelItem(new RefuelItemData());

        assertNull(m.getCustomerRepName());
        assertNull(m.getReason());
        assertNull(m.getDensityKgM3());
        assertNull(m.getAdditivePresence());
        assertNull(m.getPrevLocation1());
    }

    @Test
    public void itemNull_khongNo() {
        assertNotNull(BM7501Prefill.fromRefuelItem(null));
    }

    @Test
    public void thoiGian_layTuMeHut() {
        Date start = new Date(1_700_000_000_000L);
        Date end = new Date(1_700_001_000_000L);
        RefuelItemData item = new RefuelItemData();
        item.setStartTime(start);
        item.setEndTime(end);

        BM7501Model m = BM7501Prefill.fromRefuelItem(item);
        assertEquals(start, m.getStartTime());
        assertEquals(end, m.getEndTime());
        assertEquals(start, m.getDate());
    }

    // ------------------------------------------------------- điền bù khi mở lại

    /**
     * Phiếu thường được lập lúc đang hút, khi mẻ chưa có giờ kết thúc và số lượng.
     * Mở lại phiếu thì những số đó phải tự vào, không bắt nhân viên gõ lại.
     */
    @Test
    public void moLaiPhieu_dienBuSoLieuMeHutVuaCo() {
        BM7501Model m = new BM7501Model();

        RefuelItemData item = new RefuelItemData();
        item.setEndTime(new Date(1_700_001_000_000L));
        // Mẻ hút chốt số: đồng hồ ra 990 USG, tỷ trọng 0,7952 kg/l.
        item.setRealAmount(990d);
        item.setDensity(0.7952d);

        assertTrue("Có dữ liệu mới thì phải báo là đã điền thêm",
                BM7501Prefill.fillMissing(m, item));
        assertEquals(990d, m.getGallon(), 0.0001d);
        assertEquals(3748d, m.getLiter(), 0.0001d);
        assertEquals(2980d, m.getActualKg(), 0.0001d);
        assertNotNull(m.getEndTime());

        assertFalse("Không còn gì để điền thì không được báo là có thay đổi",
                BM7501Prefill.fillMissing(m, item));
    }

    @Test
    public void dienBu_khongDeLenODaNhap() {
        BM7501Model m = new BM7501Model();
        m.setActualKg(2000d);
        m.setDefuellerTruckNo("51F-999.99");

        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(990d);
        item.setDensity(0.7952d);
        item.setTruckNo("51F-123.45");

        BM7501Prefill.fillMissing(m, item);
        assertEquals("Số nhân viên đã sửa là số chuẩn", 2000d, m.getActualKg(), 0.0001d);
        assertEquals("51F-999.99", m.getDefuellerTruckNo());
    }

    @Test
    public void nhietDo_laySoNhapTayTruoc_roiMoiDenSoDongHo() {
        RefuelItemData meterOnly = new RefuelItemData();
        meterOnly.setTemperature(30.5d);
        assertEquals(30.5d, BM7501Prefill.fromRefuelItem(meterOnly).getActualTempC(), 0.0001d);

        RefuelItemData both = new RefuelItemData();
        both.setTemperature(30.5d);
        both.setManualTemperature(28.5d);
        assertEquals(28.5d, BM7501Prefill.fromRefuelItem(both).getActualTempC(), 0.0001d);
    }

    // ------------------------------------------------------- phiên và hãng

    @Test
    public void phienDangNhap_dienSanSanBayVaDaiDienSkypec() {
        BM7501Model m = new BM7501Model();
        assertTrue(BM7501Prefill.fillSession(m, 3, "NỘI BÀI", "Trần Văn B"));

        assertEquals(3, m.getAirportId());
        assertEquals("NỘI BÀI", m.getAirportName());
        assertEquals("Trần Văn B", m.getSkypecRepName());
    }

    /**
     * Chốt 2026-09-23: mỗi chuyến là một người của hãng ra ký, nên KHÔNG điền sẵn đại diện,
     * chức danh, điện thoại — điền sẵn thì nhân viên dễ để nguyên tên người của phiếu trước.
     */
    /**
     * Phiếu phải tự mang khoá của mẻ hút và của chuyến bay: trên server, phiếu không có mấy
     * khoá này chỉ là một mảnh rời, không biết thuộc chuyến nào.
     */
    @Test
    public void mangDuKhoaCuaMeHutVaChuyenBay() {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId("me-hut-9");
        item.setId(4455);
        item.setFlightId(778);
        item.setFlightUniqueId("chuyen-abc");
        item.setFlightCode("VN 7561");

        BM7501Model m = BM7501Prefill.fromRefuelItem(item);

        assertEquals("me-hut-9", m.getRefuelItemUniqueId());
        assertEquals(4455, m.getRefuelItemId());
        assertEquals(778, m.getFlightId());
        assertEquals("chuyen-abc", m.getFlightUniqueId());
        assertEquals("VN 7561", m.getFlightCode());
    }

    @Test
    public void khongDienSanThongTinDaiDienHang() {
        RefuelItemData item = new RefuelItemData();
        item.setAircraftType("A321");

        BM7501Model m = BM7501Prefill.fromRefuelItem(item);
        assertNull(m.getCustomerRepName());
        assertNull(m.getCustomerTitle());
        assertNull(m.getCustomerTel());
        assertNull(m.getCustomerFax());
    }

    @Test
    public void tinHieuChuan_dienSanChoPhieuMoi() {
        BM7501Model m = BM7501Prefill.fromRefuelItem(new RefuelItemData());
        assertTrue(m.isSignalThumbUp());
        assertTrue(m.isSignalCrossArms());
    }
}
