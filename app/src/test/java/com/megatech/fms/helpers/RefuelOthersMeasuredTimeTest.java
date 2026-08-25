package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Sự cố chuyến CV 5781 ngày 24-08-2026: phiếu 2619EY0 in giờ bắt đầu 06:34 trong khi mẻ sớm
 * nhất của chuyến bắt đầu lúc 15:28.
 *
 * <p>Gói tin của xe chốt HAN3-20-7002 lúc 17:26:09 cho thấy bản sao mẻ của xe HAN3-20-7005
 * trên máy đó mang:
 *
 * <pre>
 * "RealAmount":6718, "StartNumber":32175897, "EndNumber":32182615,
 * "Status":3, "ReceiptNumber":"2619EY0", "ServerRevision":2   ← đều là giá trị sau 15:45
 * "StartTime":"06:34:16", "EndTime":"06:34:16"                ← riêng hai cột này là giá trị
 *                                                               lúc phân xe buổi sáng
 * </pre>
 *
 * <p>Máy đã nhận đủ bản cập nhật, chỉ trượt đúng hai mốc giờ — vì {@code mergeByOwnership}
 * loại {@code StartTime}/{@code EndTime} khỏi MỌI lượt trộn. Quy tắc đó đúng cho mẻ của chính
 * máy (server từng echo lại giờ sinh phản hồi), nhưng sai cho mẻ của xe khác: máy này không đo
 * mẻ đó nên không có gì để bảo vệ, còn giá trị local chỉ là giờ lúc phân xe.
 *
 * <p>Màn hình xuất hoá đơn gộp {@code MIN(StartTime)} trên tập mẻ, nên một bản sao trượt giờ là
 * đủ kéo cả hoá đơn đi sai 9 tiếng.
 */
public class RefuelOthersMeasuredTimeTest {

    private static Date at(int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.AUGUST, 24, hour, minute, second);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Bản trên máy xe chốt: giờ còn ở mốc phân xe 06:34, số liệu đã là bản chốt. */
    private static RefuelItem localCopyOnClosingTruck() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2118888);
        data.setUniqueId("64bfda9b-078e-4b3b-8732-6c87df9edd30");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setTruckNo("HAN3-20-7005");
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setRealAmount(6718);
        data.setStartNumber(32175897);
        data.setEndNumber(32182615);
        data.setClientSeq(8);
        data.setServerRevision(2);
        data.setStartTime(at(6, 34, 16));
        data.setEndTime(at(6, 34, 16));

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(false);
        return row;
    }

    /** Bản trên server sau khi xe 7005 chốt lúc 15:45:12. */
    private static RefuelItemData serverCopy() {
        RefuelItemData remote = new RefuelItemData();
        remote.setId(2118888);
        remote.setUniqueId("64bfda9b-078e-4b3b-8732-6c87df9edd30");
        remote.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        remote.setTruckNo("HAN3-20-7005");
        remote.setStatus(REFUEL_ITEM_STATUS.DONE);
        remote.setRealAmount(6718);
        remote.setStartNumber(32175897);
        remote.setEndNumber(32182615);
        remote.setClientSeq(8);
        remote.setServerRevision(2);
        remote.setStartTime(at(15, 28, 26));
        remote.setEndTime(at(15, 44, 50));
        return remote;
    }

    @Test
    public void meCuaXeKhac_nhanGioTuServer() {
        RefuelItem local = localCopyOnClosingTruck();

        RefuelItemData merged = RefuelSyncGuard.applyRemote(local, serverCopy(), true, true);

        assertNotNull(merged);
        assertEquals("giờ bắt đầu phải lấy bản server", at(15, 28, 26), merged.getStartTime());
        assertEquals("giờ kết thúc phải lấy bản server", at(15, 44, 50), merged.getEndTime());
    }

    @Test
    public void meCuaChinhMay_vanGiuGioDoDuoc() {
        RefuelItem local = localCopyOnClosingTruck();

        // Đường đồng bộ nền giữ nguyên hành vi cũ: không nhận hai mốc giờ từ server, vì với
        // mẻ của chính máy thì giá trị đo trên xe mới là nguồn chuẩn.
        RefuelItemData merged = RefuelSyncGuard.applyRemote(local, serverCopy(), true);

        assertNotNull(merged);
        assertEquals("giờ bắt đầu của chính máy không được server ghi đè",
                at(6, 34, 16), merged.getStartTime());
        assertEquals("giờ kết thúc của chính máy không được server ghi đè",
                at(6, 34, 16), merged.getEndTime());
    }

    @Test
    public void nhanGioVanKhongDungToiSoLieuChot() {
        RefuelItem local = localCopyOnClosingTruck();

        RefuelItemData merged = RefuelSyncGuard.applyRemote(local, serverCopy(), true, true);

        assertNotNull(merged);
        assertEquals(6718, merged.getRealAmount(), 0.001);
        assertEquals(32175897, merged.getStartNumber(), 0.001);
        assertEquals(32182615, merged.getEndNumber(), 0.001);
        assertEquals(REFUEL_ITEM_STATUS.DONE, merged.getStatus());
    }

    /**
     * Giờ in lên hoá đơn là {@code MIN}/{@code MAX} trên tập mẻ. Trước bản vá, tập của xe chốt
     * cho ra 06:34:16 — đúng con số đã in ra giấy.
     */
    @Test
    public void gioTrenHoaDon_theoTapMe() {
        java.util.List<RefuelItemData> items = new java.util.ArrayList<>();

        items.add(itemWithTimes(at(15, 28, 26), at(15, 44, 50)));   // 7005
        items.add(itemWithTimes(at(15, 45, 30), at(15, 55, 35)));   // 7009
        items.add(itemWithTimes(at(15, 50, 42), at(16, 5, 4)));     // 7012
        items.add(itemWithTimes(at(16, 1, 18), at(16, 1, 18)));     // 7010
        items.add(itemWithTimes(at(16, 6, 34), at(16, 23, 43)));    // 7004
        items.add(itemWithTimes(at(17, 10, 57), at(17, 26, 9)));    // 7002

        long span = RefuelTimeValidator.invoiceSpanMs(items);
        assertEquals("15:28:26 → 17:26:09", 117L, span / 60000);

        // Thêm đúng một mẻ trượt giờ như trên máy xe chốt là span nhảy lên gần 11 tiếng,
        // vượt ngưỡng cảnh báo của màn hình xuất hoá đơn.
        items.add(itemWithTimes(at(6, 34, 16), at(6, 34, 16)));
        long poisoned = RefuelTimeValidator.invoiceSpanMs(items);
        assertEquals("06:34:16 → 17:26:09", 651L, poisoned / 60000);
        org.junit.Assert.assertTrue("phải vượt ngưỡng cảnh báo",
                poisoned > RefuelTimeValidator.MAX_INVOICE_SPAN_MS);
    }

    /**
     * Thế kẹt đo trên xe HAN3-20-7005 ngày 25-08-2026: 90 lượt CONFLICT trên 5 phiếu, tất cả
     * chỉ lệch đúng cột endTime. Phải nhận ra để nhường cho server thay vì gửi lại vô hạn.
     */
    @Test
    public void chiLechGio_thiNhanRaDuoc() {
        RefuelItemData request = settled(at(6, 40, 12));
        RefuelItemData response = settled(at(17, 11, 57));

        org.junit.Assert.assertTrue(RefuelSyncGuard.isOnlyMeasuredTimeDiff(request, response));
    }

    @Test
    public void lechCaSoDongHo_thiKhongPhaiTheKet() {
        RefuelItemData request = settled(at(6, 40, 12));
        RefuelItemData response = settled(at(17, 11, 57));
        response.setEndNumber(70456999);   // số chốt cũng lệch ⇒ conflict thật, không được nhường

        org.junit.Assert.assertFalse(RefuelSyncGuard.isOnlyMeasuredTimeDiff(request, response));
    }

    @Test
    public void khopHoanToan_thiKhongCoGiDeNhuong() {
        RefuelItemData request = settled(at(17, 11, 57));
        RefuelItemData response = settled(at(17, 11, 57));

        org.junit.Assert.assertFalse(RefuelSyncGuard.isOnlyMeasuredTimeDiff(request, response));
    }

    /** Mẻ đã chốt, chỉ khác nhau ở giờ kết thúc. */
    private static RefuelItemData settled(Date endTime) {
        RefuelItemData item = new RefuelItemData();
        item.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(7042);
        item.setStartNumber(70449767);
        item.setEndNumber(70456809);
        item.setStartTime(at(16, 55, 0));
        item.setEndTime(endTime);
        return item;
    }

    private static RefuelItemData itemWithTimes(Date start, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setStartTime(start);
        item.setEndTime(end);
        return item;
    }
}
