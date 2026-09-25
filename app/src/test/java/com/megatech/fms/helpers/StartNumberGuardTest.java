package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Vị từ quyết định số đồng hồ đầu mẻ được phép ghi xuống Room.
 *
 * <p>Ca gốc: đồng hồ tổng đóng băng trong khi sản lượng vẫn tăng, khiến
 * {@code StartNumber = EndMeter − Gross} giảm dần rồi ÂM và được ghi thẳng xuống Room.
 */
public class StartNumberGuardTest {

    @Test
    public void tongKhoeThiTinhLai() {
        assertEquals(15_000_000d,
                MeterFieldHealth.resolveStartNumber(14_999_000d, 15_001_000d, 1_000d, false),
                0.001);
        assertFalse(MeterFieldHealth.startNumberHeld(
                14_999_000d, 15_001_000d, 1_000d, false));
    }

    @Test
    public void tongStaleThiGiuNguyenGiaTriCu() {
        // Tổng đóng băng ở 15.001.000 còn Gross đã lên 5.000: hiệu sẽ tụt 4.000 so với thật.
        assertEquals(15_000_000d,
                MeterFieldHealth.resolveStartNumber(15_000_000d, 15_001_000d, 5_000d, true),
                0.001);
        assertTrue(MeterFieldHealth.startNumberHeld(
                15_000_000d, 15_001_000d, 5_000d, true));
    }

    @Test
    public void khongBaoGioRaSoAm() {
        // Tổng vẫn được coi là khoẻ nhưng hiệu đã âm — vẫn không cho ghi số âm.
        assertEquals(1_000d,
                MeterFieldHealth.resolveStartNumber(1_000d, 500d, 3_000d, false),
                0.001);
        assertTrue(MeterFieldHealth.startNumberHeld(1_000d, 500d, 3_000d, false));

        // Giá trị cũ đã âm sẵn (dữ liệu hỏng từ trước) thì kẹp về 0.
        assertEquals(0d,
                MeterFieldHealth.resolveStartNumber(-500d, 500d, 3_000d, false),
                0.001);
        assertEquals(0d,
                MeterFieldHealth.resolveStartNumber(-500d, 15_000d, 3_000d, true),
                0.001);
    }

    @Test
    public void meMoiVoiGiaTriCuBangKhongVanTinhDuoc() {
        assertEquals(14_000d,
                MeterFieldHealth.resolveStartNumber(0d, 14_000d, 0d, false),
                0.001);
    }
}
