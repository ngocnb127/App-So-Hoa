package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class RefuelTimeValidatorTest {

    private static Date at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.AUGUST, 20, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static RefuelItemData item(Date approach, Date start, Date end, Date leave) {
        RefuelItemData item = new RefuelItemData();
        item.setApproachTime(approach);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setLeaveTime(leave);
        return item;
    }

    @Test
    public void meBinhThuongKhongCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(at(10, 0), at(10, 5), at(10, 35), at(10, 40)));

        assertTrue(errors.toString(), errors.isEmpty());
        assertEquals("", RefuelTimeValidator.describe(
                item(at(10, 0), at(10, 5), at(10, 35), at(10, 40))));
    }

    @Test
    public void mePhutDungBangNguongVanHopLe() {
        // 60 phút chẵn là ngưỡng cho phép, chỉ dài HƠN mới cảnh báo.
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(10, 0), at(11, 0), null));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void meDaiHonMotGioBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(10, 0), at(11, 30), null));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("90 phút"));
    }

    @Test
    public void batDauTruocGioTiepCanBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(at(10, 0), at(9, 50), at(10, 20), null));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("trước giờ tiếp cận"));
    }

    @Test
    public void ketThucSauGioRoiDiBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(at(10, 0), at(10, 5), at(10, 50), at(10, 40)));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("sau giờ rời đi"));
    }

    @Test
    public void ketThucSomHonBatDauBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(10, 30), at(10, 5), null));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("sớm hơn giờ bắt đầu"));
    }

    @Test
    public void nhieuLoiDuocGopHet() {
        // Vừa dài quá 1 giờ, vừa bắt đầu trước tiếp cận, vừa kết thúc sau rời đi.
        List<String> errors = RefuelTimeValidator.validate(
                item(at(10, 0), at(9, 30), at(11, 30), at(11, 0)));

        assertEquals(3, errors.size());

        String message = RefuelTimeValidator.describe(
                item(at(10, 0), at(9, 30), at(11, 30), at(11, 0)));
        assertTrue(message, message.startsWith("⚠"));
        assertEquals(3, message.split("•", -1).length - 1);
    }

    @Test
    public void thieuGioThiBaoThieu() {
        List<String> errors = RefuelTimeValidator.validate(
                item(at(10, 0), at(10, 5), null, null));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("Chưa ghi nhận đủ giờ"));
    }

    @Test
    public void chuaTiepCanThiKhongKiemTraKhoangThoiGian() {
        // approachTime null là chuyện bình thường với phiếu cũ; chỉ còn luật độ dài mẻ.
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(10, 5), at(10, 35), null));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void itemNullKhongNem() {
        assertTrue(RefuelTimeValidator.validate(null).isEmpty());
        assertEquals("", RefuelTimeValidator.describe(null));
    }

    @Test
    public void meQuaDaiBiBatODuongInHoaDon() {
        assertTrue(RefuelTimeValidator.exceedsMaxDuration(
                item(null, at(10, 0), at(11, 30), null)));
        assertEquals(90, RefuelTimeValidator.durationMinutes(
                item(null, at(10, 0), at(11, 30), null)));
    }

    @Test
    public void meDuoiNguongKhongChanDuongInHoaDon() {
        // Đúng 60 phút vẫn được in thẳng, không hiện cảnh báo.
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(
                item(null, at(10, 0), at(11, 0), null)));
    }

    @Test
    public void gioDaoNguocBiChanODuongTaoPhieu() {
        assertTrue(RefuelTimeValidator.endsBeforeStart(
                item(null, at(11, 0), at(10, 30), null)));
        assertFalse(RefuelTimeValidator.endsBeforeStart(
                item(null, at(10, 0), at(10, 30), null)));

        // Thiếu giờ không phải đảo giờ: để validate() báo, không chặn nhầm đường tạo phiếu.
        assertFalse(RefuelTimeValidator.endsBeforeStart(item(null, at(10, 0), null, null)));
        assertFalse(RefuelTimeValidator.endsBeforeStart(null));
    }

    @Test
    public void thieuGioThiKhongChanDuongInHoaDon() {
        // Thiếu giờ là việc của validate(); đường in không được vì thế mà dựng cảnh báo.
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(item(null, at(10, 0), null, null)));
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(null));
        assertEquals(0, RefuelTimeValidator.durationMinutes(null));
    }
}
