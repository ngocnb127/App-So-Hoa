package com.megatech.fms.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Lỗi số lít sai ở gói tin chốt mẻ, báo ngày 24-08-2026, xác minh trên hai mẻ ở hai sân bay.
 *
 * <pre>
 * RefuelItem 2118503 — xe CXR-32006, chuyến EO3548
 *   gói 437  21:14:51  Gallon 4.541  Volume 17.190   ✓
 *   gói 438  21:17:17  Gallon 5.306  Volume 17.190   ✗ (đúng phải là 20.085)
 *
 * RefuelItem 2118695 — xe DAD-320023, chuyến VN1670
 *   gói 117  08:50:22  Gallon   445  Volume  1.685   ✓
 *   gói 118  09:00:14  Gallon 1.949  Volume  1.685   ✗ (đúng phải là 7.378)
 * </pre>
 *
 * <p>Gson serialize FIELD chứ không gọi getter, nên gói tin mang thẳng {@code volume}. Field đó
 * trước đây chỉ được tính lại trong nhánh không-FHS của {@code setRealAmount}, còn
 * {@code getVolume()} thì luôn tính lại — nên màn hình và phiếu in vẫn đúng, chỉ gói tin gửi lên
 * là sai. Đúng một cột, đúng một gói.
 */
public class RefuelVolumeConsistencyTest {

    /** Hằng số US gallon → lít, phải khớp server, phiếu in và luồng hoá đơn. */
    private static final double GAL_TO_L = 3.7854;

    @Test
    public void doiGallon_thiSoLitDiTheo() {
        RefuelItemData item = new RefuelItemData();

        item.setRealAmount(4541);
        assertEquals(Math.round(4541 * GAL_TO_L), item.getVolume(), 0.001);

        // Đúng bước nhảy của gói chốt mẻ ca A: 4.541 -> 5.306 gallon.
        item.setRealAmount(5306);
        assertEquals("số lít phải theo gallon mới, không giữ giá trị gói trước",
                Math.round(5306 * GAL_TO_L), item.getVolume(), 0.001);
        assertEquals(20085, item.getVolume(), 0.001);
    }

    @Test
    public void caB_1949Gallon() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(445);
        item.setRealAmount(1949);

        assertEquals(7378, item.getVolume(), 0.001);
    }

    @Test
    public void chotChan_batDuocLechVaSuaLai() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(5306);
        item.setVolume(17190);          // mô phỏng số lít cũ còn sót lại

        String fixed = item.reconcileVolume();

        assertNotNull("phải phát hiện lệch", fixed);
        assertTrue(fixed.contains("volume_in=17190"));
        assertTrue(fixed.contains("volume_calc=20085"));
        assertEquals(20085, item.getVolume(), 0.001);
    }

    @Test
    public void chotChan_boQuaChenhLechLamTron() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(4541);

        double rounded = item.getVolume();
        item.setVolume(rounded + 1);    // trong dung sai 2 lít

        assertNull("chênh lệch làm tròn không được coi là lỗi", item.reconcileVolume());
    }

    @Test
    public void chotChan_khongDungToiDuLieuDung() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(6718);       // dòng 1 phiếu giấy 2619EY0

        assertNull(item.reconcileVolume());
        assertEquals(25430, item.getVolume(), 0.001);
    }

    /** Số kg suy từ số lít ĐÃ làm tròn — đối chiếu dòng 2 phiếu giấy 2619EY0. */
    @Test
    public void soKgTinhTuSoLitDaLamTron() {
        RefuelItemData item = new RefuelItemData();
        item.setRealAmount(4311);
        item.setDensity(0.7910);

        assertEquals(16319, item.getVolume(), 0.001);
        assertEquals(12908, item.getWeight(), 0.001);
    }
}
