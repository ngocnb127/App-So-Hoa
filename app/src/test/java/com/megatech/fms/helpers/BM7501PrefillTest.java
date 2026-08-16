package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

    @Test
    public void mucAVaMucB_deTrong_choNhanVienNhap() {
        BM7501Model m = BM7501Prefill.fromRefuelItem(new RefuelItemData());

        assertNull(m.getCustomerRepName());
        assertNull(m.getReason());
        assertNull(m.getVac());
        assertNull(m.getCwd());
        assertNull(m.getDensityKgM3());
        assertNull(m.getTankDrainSampled());
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
}
