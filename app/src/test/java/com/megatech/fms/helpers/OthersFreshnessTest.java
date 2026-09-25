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
 * MỤC 9 — làm mới dữ liệu mẻ xe khác trước khi dựng chứng từ gộp.
 *
 * <p>Toàn bộ các ca ở đây bảo vệ đúng một lời hứa: việc làm mới KHÔNG được đẻ ra đường chặn
 * mới. Refresh lỗi vẫn phải in được; mẻ xe khác chưa đủ điều kiện thì bị loại kèm cảnh báo chứ
 * không khoá nút; mẻ mới xuất hiện thì phải hỏi chứ không tự tích.
 */
public class OthersFreshnessTest {

    private static final long T0 = 1_000_000L;

    private static Date at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.AUGUST, 20, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static RefuelItemData done(String uid, String truckNo) {
        RefuelItemData item = new RefuelItemData();
        item.setUniqueId(uid);
        item.setTruckNo(truckNo);
        item.setStartTime(at(10, 0));
        item.setEndTime(at(10, 30));
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(1200);
        // Đủ cả các trường BẮT BUỘC KHI IN: mẻ "đủ điều kiện" phải đủ cả hai vế.
        item.setManualTemperature(30);
        item.setDensity(0.79);
        item.setQualityNo("QC-001");
        return item;
    }

    /** Mẻ đã đo xong nhưng thiếu tỉ trọng / QC — máy này không sửa được nếu là xe khác. */
    private static RefuelItemData missingPrintFields(String uid, String truckNo) {
        RefuelItemData item = done(uid, truckNo);
        item.setDensity(0);
        item.setQualityNo("");
        return item;
    }

    private static RefuelItemData processing(String uid, String truckNo) {
        RefuelItemData item = done(uid, truckNo);
        item.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        return item;
    }

    // ------------------------------------------------------------------ độ tươi

    @Test
    public void chuaLamMoiLanNaoThiPhaiLamMoi() {
        assertTrue(OthersFreshness.needsRefresh(0, T0));
        assertTrue(OthersFreshness.needsRefresh(-5, T0));
    }

    @Test
    public void trongCuaSoBaPhutThiKhongGoiLai() {
        assertFalse(OthersFreshness.needsRefresh(T0, T0));
        assertFalse(OthersFreshness.needsRefresh(
                T0, T0 + OthersFreshness.FRESH_WINDOW_MS));
        assertTrue(OthersFreshness.needsRefresh(
                T0, T0 + OthersFreshness.FRESH_WINDOW_MS + 1));
    }

    @Test
    public void dongHoNhayLuiThiCoiNhuKhongBietGi() {
        assertTrue(OthersFreshness.needsRefresh(T0, T0 - 1000));
    }

    // ------------------------------------- không tạo đường chặn mới khi refresh

    @Test
    public void refreshLoiThiGiuNguyenTrangThaiCuNenVanInDuoc() {
        // Trạng thái đang tốt (không hasFailure) + refresh thất bại ⇒ KHÔNG nhận kết quả mới,
        // cờ "đã đủ dữ liệu" không bị hạ cấp ⇒ đường chặn phiếu gộp không kích hoạt.
        assertFalse(OthersFreshness.adoptRefreshResult(false, false, false));
        assertFalse(OthersFreshness.adoptRefreshResult(true, false, false));
    }

    @Test
    public void ketQuaLamMoiXauHonThiKhongNhan() {
        // Đang in được mà nhận một kết quả xấu hơn là biến màn hình thành không in được.
        assertFalse(OthersFreshness.adoptRefreshResult(false, true, true));
    }

    @Test
    public void ketQuaLamMoiTotHonHoacBangThiNhan() {
        assertTrue(OthersFreshness.adoptRefreshResult(false, true, false));
        assertTrue(OthersFreshness.adoptRefreshResult(true, true, false));
        assertTrue(OthersFreshness.adoptRefreshResult(true, true, true));
    }

    // ------------------------------------------------- sàng lọc mẻ của xe khác

    @Test
    public void meXeKhacChuaDuDieuKienThiBiLoaiChuKhongChan() {
        RefuelItemData mine = done("uid-mine", "51C-111.11");
        RefuelItemData otherOk = done("uid-other-ok", "51C-222.22");
        RefuelItemData otherBad = processing("uid-other-bad", "51C-333.33");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mine, otherOk, otherBad),
                Arrays.asList(false, true, true));

        assertEquals(Arrays.asList(mine, otherOk), partition.kept);
        assertEquals(Collections.singletonList(otherBad), partition.excluded);
        assertTrue(partition.hasExclusion());
        assertEquals(Collections.singletonList("51C-333.33"),
                partition.excludedTruckNumbers());
    }

    @Test
    public void meCuaChinhXeNayKhongBaoGioBiLoai() {
        // Người dùng sửa được mẻ của chính mình, nên các đường kiểm tra cũ phải tiếp tục
        // báo lỗi để họ sửa — loại im lặng ở đây là giấu lỗi.
        RefuelItemData mineBad = processing("uid-mine", "51C-111.11");
        RefuelItemData otherOk = done("uid-other", "51C-222.22");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mineBad, otherOk), Arrays.asList(false, true));

        assertFalse(partition.hasExclusion());
        assertEquals(Arrays.asList(mineBad, otherOk), partition.kept);
    }

    @Test
    public void chungTuDonKhongBiDongCham() {
        RefuelItemData otherBad = processing("uid-other-bad", "51C-333.33");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Collections.singletonList(otherBad), Collections.singletonList(true));

        assertFalse(partition.hasExclusion());
        assertEquals(Collections.singletonList(otherBad), partition.kept);
    }

    @Test
    public void loaiHetThiTraLaiNguyenDanhSachChoDuongKiemTraCu() {
        RefuelItemData a = processing("uid-a", "51C-222.22");
        RefuelItemData b = processing("uid-b", "51C-333.33");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(a, b), Arrays.asList(true, true));

        assertFalse(partition.hasExclusion());
        assertEquals(Arrays.asList(a, b), partition.kept);
    }

    @Test
    public void thieuGioHoacSanLuongCungLaChuaDuDieuKien() {
        RefuelItemData noEnd = done("uid-1", "51C-222.22");
        noEnd.setEndTime(null);
        RefuelItemData noAmount = done("uid-2", "51C-222.22");
        noAmount.setRealAmount(0);

        assertFalse(OthersFreshness.eligibleForDocument(noEnd));
        assertFalse(OthersFreshness.eligibleForDocument(noAmount));
        assertFalse(OthersFreshness.eligibleForDocument(null));
        assertTrue(OthersFreshness.eligibleForDocument(done("uid-3", "51C-222.22")));
    }

    @Test
    public void meXeKhacThieuTiTrongQcThiLoaiChuKhongChan() {
        // Đúng ca "hôm nay in được, mai không in được": mẻ xe khác đo xong nhưng chưa có
        // tỉ trọng / QC. Máy này chỉ đọc mẻ đó nên chặn là bắt người dùng chờ vô hạn.
        RefuelItemData mine = done("uid-mine", "51C-111.11");
        RefuelItemData otherBad = missingPrintFields("uid-other", "51C-444.44");

        assertTrue(OthersFreshness.eligibleForDocument(otherBad));
        assertFalse(OthersFreshness.hasRequiredPrintFields(otherBad));
        assertFalse(OthersFreshness.printableForDocument(otherBad));

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mine, otherBad), Arrays.asList(false, true));

        assertEquals(Collections.singletonList(mine), partition.kept);
        assertEquals(Collections.singletonList(otherBad), partition.excluded);
        assertEquals(Collections.singletonList("51C-444.44"),
                partition.excludedTruckNumbers());
    }

    @Test
    public void meThieuTiTrongQcCuaChinhXeNayVanGiuMucChanCu() {
        // CHỐNG HỒI QUY: người dùng sửa được mẻ của mình, nên phải để đường validate() cũ
        // chặn và bắt họ nhập, KHÔNG được im lặng loại khỏi chứng từ.
        RefuelItemData mineBad = missingPrintFields("uid-mine", "51C-111.11");
        RefuelItemData otherOk = done("uid-other", "51C-222.22");

        OthersFreshness.Partition partition = OthersFreshness.excludeIneligibleForeignItems(
                Arrays.asList(mineBad, otherOk), Arrays.asList(false, true));

        assertFalse(partition.hasExclusion());
        assertEquals(Arrays.asList(mineBad, otherOk), partition.kept);
    }

    @Test
    public void thieuTungTruongInDeuBiCoiLaChuaDu() {
        RefuelItemData noTemp = done("uid-1", "51C-2");
        noTemp.setManualTemperature(0);
        RefuelItemData noQc = done("uid-2", "51C-2");
        noQc.setQualityNo(null);

        assertFalse(OthersFreshness.hasRequiredPrintFields(noTemp));
        assertFalse(OthersFreshness.hasRequiredPrintFields(noQc));
        assertFalse(OthersFreshness.hasRequiredPrintFields(null));
        assertTrue(OthersFreshness.hasRequiredPrintFields(done("uid-3", "51C-2")));
    }

    @Test
    public void danhSachRongVaCoLechKichThuocDeuAnToan() {
        assertTrue(OthersFreshness.excludeIneligibleForeignItems(null, null).kept.isEmpty());
        List<RefuelItemData> items = Arrays.asList(
                done("uid-1", "51C-1"), processing("uid-2", "51C-2"));
        // Cờ foreign lệch kích thước: giữ nguyên danh sách, không đoán mò.
        assertEquals(items, OthersFreshness.excludeIneligibleForeignItems(
                items, Collections.singletonList(true)).kept);
    }

    // ------------------------------------------------------------- mẻ mới về

    @Test
    public void meMoiXuatHienDuocLietKeDeHoiChuKhongTuTich() {
        List<String> before = Arrays.asList("uid-1", "uid-2");
        List<String> after = Arrays.asList("uid-1", "uid-2", "uid-3", "uid-3", null, "");

        assertEquals(Collections.singletonList("uid-3"),
                OthersFreshness.newlyAppearedUniqueIds(before, after));
    }

    @Test
    public void khongCoMeMoiThiDanhSachRong() {
        assertTrue(OthersFreshness.newlyAppearedUniqueIds(
                Arrays.asList("uid-1", "uid-2"), Arrays.asList("uid-1")).isEmpty());
        assertTrue(OthersFreshness.newlyAppearedUniqueIds(
                new ArrayList<>(), null).isEmpty());
    }
}
