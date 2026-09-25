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

    // ---- Luật bổ sung: chỉ CẢNH BÁO, không chỗ nào biến thành chặn ----

    private static Date at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.AUGUST, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Đồng hồ tiêm được nên ca này tất định, không phụ thuộc ngày chạy CI. */
    private static long now() {
        return at(20, 12, 0).getTime();
    }

    @Test
    public void gioTuongLaiBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(20, 14, 0), at(20, 14, 30), null), now());

        assertEquals(2, errors.size());
        assertTrue(errors.toString(), errors.get(0).contains("tương lai"));
    }

    @Test
    public void gioTrongDungSaiHaiPhutKhongBiCanhBao() {
        // Thao tác tay chậm một nhịp không được coi là lỗi.
        List<String> errors = RefuelTimeValidator.validate(
                item(null, at(20, 11, 30), at(20, 12, 1), null), now());

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void roiDiSomHonTiepCanBiCanhBao() {
        List<String> errors = RefuelTimeValidator.validate(
                item(at(20, 10, 30), at(20, 10, 35), at(20, 10, 50), at(20, 10, 0)), now());

        assertTrue(errors.toString(), errors.toString().contains("sớm hơn giờ tiếp cận"));
    }

    @Test
    public void thieuGioTiepCanChiLaGhiChuNguCanh() {
        // KHÔNG phải lỗi: phiếu cũ không có giờ tiếp cận là chuyện thường ngày.
        assertTrue(RefuelTimeValidator.validate(
                item(null, at(20, 10, 5), at(20, 10, 35), null), now()).isEmpty());

        List<String> notes = RefuelTimeValidator.contextNotes(
                item(null, at(20, 10, 5), at(20, 10, 35), null));
        assertEquals(1, notes.size());
        assertTrue(notes.get(0), notes.get(0).contains("giờ tiếp cận"));
    }

    @Test
    public void meKhongPhutDuocNeuRoNguyenNhan() {
        List<String> notes = RefuelTimeValidator.contextNotes(
                item(at(20, 10, 0), at(20, 10, 30), at(20, 10, 30), null));

        assertEquals(1, notes.size());
        assertTrue(notes.get(0), notes.get(0).contains("0 phút"));
        // Diễn đạt phải chỉ đúng nguyên nhân, không ngụ ý người dùng nhập sai.
        assertTrue(notes.get(0), notes.get(0).contains("sự kiện"));
    }

    @Test
    public void ghiChuNguCanhKhongDuocLotVaoDuongBatPopup() {
        // describe() là đầu vào của popup setCancelable(false) ở màn xác nhận. Hai ca dưới
        // đây xảy ra trên rất nhiều mẻ do chính thiết kế hiện tại, nên nếu chúng lọt vào
        // describe() thì gần như mẻ nào cũng bật popup và cảnh báo thật mất tác dụng.
        RefuelItemData thieuTiepCan = item(null, at(20, 10, 5), at(20, 10, 35), null);
        assertEquals("", RefuelTimeValidator.describe(thieuTiepCan, now()));

        RefuelItemData khongPhut = item(at(20, 10, 0), at(20, 10, 30), at(20, 10, 30), null);
        assertEquals("", RefuelTimeValidator.describe(khongPhut, now()));
    }

    @Test
    public void ghiChuNguCanhVanHienDuocTaiCho() {
        // Không gián đoạn nhưng cũng không im lặng: vẫn có chuỗi để đổ vào nhãn trên màn hình.
        String notes = RefuelTimeValidator.describeContextNotes(
                item(at(20, 10, 0), at(20, 10, 30), at(20, 10, 30), null));

        assertTrue(notes, notes.contains("0 phút"));
        assertFalse(notes, notes.startsWith("⚠"));

        assertEquals("", RefuelTimeValidator.describeContextNotes(
                item(at(20, 10, 0), at(20, 10, 5), at(20, 10, 35), at(20, 10, 40))));
        assertEquals("", RefuelTimeValidator.describeContextNotes(null));
    }

    @Test
    public void loiThatVanBatPopupDuKemGhiChuNguCanh() {
        // CHỐNG HỒI QUY: tách ghi chú ra không được làm mất cảnh báo lỗi thật; mẻ vừa 0 phút
        // vừa thiếu tiếp cận mà lại có giờ kết thúc sớm hơn giờ bắt đầu thì vẫn phải kêu.
        String message = RefuelTimeValidator.describe(
                item(null, at(20, 11, 0), at(20, 10, 30), null), now());

        assertTrue(message, message.startsWith("⚠"));
        assertTrue(message, message.contains("sớm hơn giờ bắt đầu"));
    }

    @Test
    public void meQuaNuaDemHienKemNgay() {
        // Mẻ bắt đầu 21/08 00:10 và kết thúc 20/08 23:50 là đảo giờ THẬT; chỉ in HH:mm thì
        // "00:10 → 23:50" lại đọc như một mẻ dài 23 tiếng hợp lệ.
        String message = RefuelTimeValidator.describe(
                item(null, at(21, 0, 10), at(20, 23, 50), null), at(22, 12, 0).getTime());

        assertTrue(message, message.contains("21/08"));
        assertTrue(message, message.contains("20/08"));
        assertTrue(message, message.contains("sớm hơn giờ bắt đầu"));
    }

    @Test
    public void meTrongMotNgayChiHienGioPhut() {
        String message = RefuelTimeValidator.describe(
                item(null, at(20, 11, 0), at(20, 10, 30), null), now());

        assertFalse(message, message.contains("20/08"));
    }

    @Test
    public void luatMoiKhongLamDoiMucChanCuaDuongTaoPhieu() {
        // CHỐNG HỒI QUY: đường tạo phiếu chặn cứng theo endsBeforeStart, không qua validate().
        assertTrue(RefuelTimeValidator.endsBeforeStart(
                item(null, at(20, 11, 0), at(20, 10, 30), null)));
        assertFalse(RefuelTimeValidator.endsBeforeStart(
                item(null, at(20, 10, 0), at(20, 10, 30), null)));
        // Giờ tương lai KHÔNG được biến thành chặn cứng ở đường tạo phiếu.
        assertFalse(RefuelTimeValidator.endsBeforeStart(
                item(null, at(20, 14, 0), at(20, 14, 30), null)));
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(
                item(null, at(20, 14, 0), at(20, 14, 30), null)));
    }

    @Test
    public void thieuGioThiKhongChanDuongInHoaDon() {
        // Thiếu giờ là việc của validate(); đường in không được vì thế mà dựng cảnh báo.
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(item(null, at(10, 0), null, null)));
        assertFalse(RefuelTimeValidator.exceedsMaxDuration(null));
        assertEquals(0, RefuelTimeValidator.durationMinutes(null));
    }
}
