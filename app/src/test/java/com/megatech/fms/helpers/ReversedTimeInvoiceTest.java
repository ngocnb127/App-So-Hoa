package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * MỤC 10 — giờ đảo ngược khi XUẤT HOÁ ĐƠN.
 *
 * <p>Đường tạo phiếu chặn cứng bằng {@code endsBeforeStart}. Đường hoá đơn dùng lại đúng phép
 * phát hiện đó nhưng ở mức CẢNH BÁO hai nút, vì mẻ sai giờ có thể thuộc xe khác mà máy này
 * không sửa được. Các ca ở đây khoá cả hai điều: phát hiện đúng mẻ lỗi, thông báo có SỐ XE, và
 * mức chặn cũ của đường phiếu KHÔNG đổi.
 */
public class ReversedTimeInvoiceTest {

    private static Date at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.AUGUST, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static RefuelItemData item(String truckNo, String flight, Date start, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setTruckNo(truckNo);
        item.setFlightCode(flight);
        item.setStartTime(start);
        item.setEndTime(end);
        return item;
    }

    @Test
    public void meBinhThuongKhongBaoGi() {
        List<String> issues = RefuelTimeValidator.reversedTimeItems(Collections.singletonList(
                item("51C-123.45", "VN123", at(20, 10, 0), at(20, 10, 30))));

        assertTrue(issues.toString(), issues.isEmpty());
    }

    @Test
    public void phatHienDungMeDaoGioVaKemSoXe() {
        List<String> issues = RefuelTimeValidator.reversedTimeItems(Arrays.asList(
                item("51C-111.11", "VN100", at(20, 10, 0), at(20, 10, 30)),
                item("51C-222.22", "VN200", at(20, 11, 0), at(20, 10, 40))));

        assertEquals(1, issues.size());
        // Số xe là thông tin bắt buộc: mẻ lỗi có thể thuộc xe khác, người dùng phải biết
        // gọi cho ai chứ không chỉ biết "có mẻ sai giờ".
        assertTrue(issues.get(0), issues.get(0).contains("51C-222.22"));
        assertTrue(issues.get(0), issues.get(0).contains("VN200"));
    }

    @Test
    public void thieuSoXeVanHienDuocMoTa() {
        List<String> issues = RefuelTimeValidator.reversedTimeItems(Collections.singletonList(
                item(null, null, at(20, 11, 0), at(20, 10, 40))));

        assertEquals(1, issues.size());
        assertTrue(issues.get(0), issues.get(0).contains("(chưa rõ xe)"));
    }

    @Test
    public void meQuaNuaDemHienKemNgayDeKhongDocNhamThanhLoiGia() {
        // Bắt đầu 23:50 hôm trước, kết thúc 00:10 hôm sau: KHÔNG đảo giờ.
        List<String> ok = RefuelTimeValidator.reversedTimeItems(Collections.singletonList(
                item("51C-333.33", "VN300", at(20, 23, 50), at(21, 0, 10))));
        assertTrue(ok.toString(), ok.isEmpty());

        // Ngược lại mới là lỗi thật, và mô tả phải kèm ngày.
        List<String> issues = RefuelTimeValidator.reversedTimeItems(Collections.singletonList(
                item("51C-333.33", "VN300", at(21, 0, 10), at(20, 23, 50))));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0), issues.get(0).contains("21/08"));
        assertTrue(issues.get(0), issues.get(0).contains("20/08"));
    }

    @Test
    public void thieuGioThiKhongBaoNham() {
        List<String> issues = RefuelTimeValidator.reversedTimeItems(Arrays.asList(
                item("51C-444.44", "VN400", null, at(20, 10, 0)),
                item("51C-444.44", "VN401", at(20, 10, 0), null),
                null));

        assertTrue(issues.toString(), issues.isEmpty());
    }

    @Test
    public void danhSachNullHoacRongTraVeRong() {
        assertTrue(RefuelTimeValidator.reversedTimeItems(null).isEmpty());
        assertTrue(RefuelTimeValidator.reversedTimeItems(
                Collections.<RefuelItemData>emptyList()).isEmpty());
    }

    /**
     * CHỐNG HỒI QUY: đường tạo PHIẾU vẫn chặn cứng theo {@code endsBeforeStart}. Bản vá mục 10
     * chỉ thêm cảnh báo ở đường hoá đơn, tuyệt đối không nới mức chặn của đường phiếu.
     */
    @Test
    public void duongPhieuGiuNguyenMucChanCu() {
        assertTrue(RefuelTimeValidator.endsBeforeStart(
                item("51C-555.55", "VN500", at(20, 11, 0), at(20, 10, 40))));
        assertFalse(RefuelTimeValidator.endsBeforeStart(
                item("51C-555.55", "VN500", at(20, 10, 0), at(20, 10, 40))));
        assertFalse(RefuelTimeValidator.endsBeforeStart(
                item("51C-555.55", "VN500", null, at(20, 10, 40))));
        assertFalse(RefuelTimeValidator.endsBeforeStart(null));
    }
}
