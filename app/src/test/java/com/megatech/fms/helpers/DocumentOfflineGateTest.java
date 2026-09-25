package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * FB-1 / FB-2 — mất mạng CHỈ CẢNH BÁO, mẻ 0 lít CHẶN XUẤT PHIẾU.
 *
 * <p>Hai quyết định nghiệp vụ đối nghịch nhau nên phải khoá lại bằng test:
 * <ul>
 *   <li>Dữ liệu mẻ xe khác chưa về (mất sóng) KHÔNG BAO GIỜ được chặn xuất phiếu/hoá đơn —
 *       cùng lắm là hỏi một câu rồi đi tiếp.</li>
 *   <li>Mẻ sản lượng 0 thì KHÔNG được lên chứng từ, dù đã DONE và đủ mọi trường khác.</li>
 *   <li>Các trường bắt buộc khi in (tỉ trọng / nhiệt độ đo tay / số QC — nhóm C) GIỮ NGUYÊN
 *       mức chặn, bản vá mất mạng không được nới nhầm sang đây.</li>
 * </ul>
 */
public class DocumentOfflineGateTest {

    private static Date at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.SEPTEMBER, 5, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Mẻ hoàn chỉnh, đủ điều kiện lên chứng từ. */
    private static RefuelItemData done(String uid, String truckNo, double gallons) {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId(uid);
        item.setTruckNo(truckNo);
        item.setStartTime(at(10, 0));
        item.setEndTime(at(10, 30));
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(gallons);
        item.setManualTemperature(30);
        item.setDensity(0.8);
        item.setQualityNo("QC-001");
        return item;
    }

    // ------------------------------------------------- FB-1: mất mạng không bao giờ chặn

    @Test
    public void matMangThiVanXuatChungTuDuocChiCanhBao() {
        // Chưa lần nào lấy được dữ liệu xe khác (mất sóng từ lúc mở màn hình).
        assertEquals(OthersFreshness.IncompleteOthersGate.WARN,
                OthersFreshness.gateForIncompleteOthers(false, false));
        // Có kết quả nhưng thiếu mẻ.
        assertEquals(OthersFreshness.IncompleteOthersGate.WARN,
                OthersFreshness.gateForIncompleteOthers(true, true));
    }

    @Test
    public void duLieuXeKhacDayDuThiDiThangKhongHoi() {
        assertEquals(OthersFreshness.IncompleteOthersGate.CONTINUE,
                OthersFreshness.gateForIncompleteOthers(true, false));
    }

    /**
     * Chống hồi quy quan trọng nhất của FB-1: cổng này chỉ có hai lối, và KHÔNG lối nào là
     * chặn. Ai đó thêm một giá trị BLOCK vào enum là làm hỏng quyết định nghiệp vụ.
     */
    @Test
    public void congNayKhongCoLoiNaoLaChan() {
        assertEquals(2, OthersFreshness.IncompleteOthersGate.values().length);
        for (OthersFreshness.IncompleteOthersGate value
                : OthersFreshness.IncompleteOthersGate.values())
            assertFalse("Cổng mất mạng không được phép có nhánh chặn: " + value,
                    value.name().contains("BLOCK"));
    }

    // ------------------------------------ FB-1: hộp cảnh báo phải nói bằng con số cụ thể

    @Test
    public void tongLitKgCuaChungTuTinhDungTrenCacMeSeIn() {
        RefuelItemData a = done("A", "51C-001", 1000);
        RefuelItemData b = done("B", "51C-002", 500);

        OthersFreshness.DocumentTotals totals =
                OthersFreshness.documentTotals(Arrays.asList(a, b));

        assertEquals(2, totals.count);
        assertEquals(a.getVolume() + b.getVolume(), totals.litres, 0.001);
        assertEquals(a.getWeight() + b.getWeight(), totals.kilos, 0.001);
        // Con số phải thật, không phải 0 — đó là lý do tồn tại của hộp thoại.
        assertTrue(totals.litres > 0);
        assertTrue(totals.kilos > 0);
    }

    @Test
    public void tongChungTuKhongVoNuoiDanhSachRongHoacCoPhanTuNull() {
        OthersFreshness.DocumentTotals empty = OthersFreshness.documentTotals(null);
        assertEquals(0, empty.count);
        assertEquals(0, empty.litres, 0.001);

        List<RefuelItemData> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(done("A", "51C-001", 1000));
        OthersFreshness.DocumentTotals totals = OthersFreshness.documentTotals(withNull);
        assertEquals("Mẻ null bị bỏ qua, không được tính vào số mẻ", 1, totals.count);
    }

    // ------------------------------------------------- FB-2: mẻ 0 lít chặn ở đường chứng từ

    @Test
    public void me0LitKhongDuDieuKienLenChungTu() {
        RefuelItemData zero = done("Z", "51C-009", 0);
        assertFalse("Mẻ 0 lít phải bị chặn ở đường xuất phiếu",
                OthersFreshness.eligibleForDocument(zero));
        assertFalse(OthersFreshness.printableForDocument(zero));
    }

    @Test
    public void me0LitVanDuTruongBatBuocKhiIn() {
        // Việc chặn là do SẢN LƯỢNG, không phải do thiếu trường: thông báo cho người dùng
        // phải nói đúng lý do, và mẻ vẫn được ghi nhận bình thường ở màn tra nạp.
        RefuelItemData zero = done("Z", "51C-009", 0);
        assertTrue(OthersFreshness.hasRequiredPrintFields(zero));
    }

    @Test
    public void meCoSanLuongThiVanQua() {
        assertTrue(OthersFreshness.eligibleForDocument(done("A", "51C-001", 1)));
    }

    // ------------------------------ Nhóm C: mức chặn trường bắt buộc khi in GIỮ NGUYÊN

    @Test
    public void thieuTiTrongVanBiChan() {
        RefuelItemData item = done("A", "51C-001", 1000);
        item.setDensity(0);
        assertFalse(OthersFreshness.hasRequiredPrintFields(item));
    }

    @Test
    public void thieuNhietDoDoTayVanBiChan() {
        RefuelItemData item = done("A", "51C-001", 1000);
        item.setManualTemperature(0);
        assertFalse(OthersFreshness.hasRequiredPrintFields(item));
    }

    @Test
    public void thieuSoQCVanBiChan() {
        RefuelItemData item = done("A", "51C-001", 1000);
        item.setQualityNo("");
        assertFalse(OthersFreshness.hasRequiredPrintFields(item));
    }

    @Test
    public void meChuaDoneVanBiChan() {
        RefuelItemData item = done("A", "51C-001", 1000);
        item.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        assertFalse(OthersFreshness.eligibleForDocument(item));
    }

    /**
     * Mẻ của XE KHÁC chưa đủ điều kiện vẫn bị LOẠI khỏi chứng từ gộp kèm cảnh báo — hành vi
     * này có trước FB-1 và không được đổi.
     */
    @Test
    public void meXeKhacChuaDuDieuKienVanBiLoaiKhoiChungTuGop() {
        RefuelItemData mine = done("A", "51C-001", 1000);
        RefuelItemData foreignBad = done("B", "51C-002", 1000);
        foreignBad.setQualityNo("");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mine, foreignBad), Arrays.asList(false, true));

        assertTrue(partition.hasExclusion());
        assertEquals(Collections.singletonList("51C-002"), partition.excludedTruckNumbers());
        assertEquals(1, partition.kept.size());
    }
}
