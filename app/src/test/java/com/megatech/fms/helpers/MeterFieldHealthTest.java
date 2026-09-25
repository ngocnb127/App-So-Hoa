package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Ca kiểm thử của sức khoẻ đọc từng trường đồng hồ.
 *
 * <p>Đồng hồ được tiêm qua tham số nên toàn bộ ca chạy tất định, không cần Android runtime
 * và không phụ thuộc thời điểm chạy CI.
 */
public class MeterFieldHealthTest {

    private static final long T0 = 1_000_000L;

    private static MeterFieldHealth started() {
        MeterFieldHealth health = new MeterFieldHealth();
        health.beginSession(T0);
        return health;
    }

    /** Qua khỏi khoảng ân hạn mở mẻ. */
    private static long afterGrace(long extraMs) {
        return T0 + MeterFieldHealth.BATCH_GRACE_MS + extraMs;
    }

    @Test
    public void motTruongChetTruongKiaSongVanBaoStale() {
        MeterFieldHealth health = started();

        // Gross vẫn về đều suốt 40 giây; Totalizer chết sau giây thứ nhất.
        health.markRead(MeterFieldHealth.Field.TOTALIZER, T0 + 1000);
        for (long t = T0; t <= afterGrace(20_000); t += 1000)
            health.markRead(MeterFieldHealth.Field.GROSSQTY, t);

        long now = afterGrace(20_000);
        assertFalse(health.isStale(MeterFieldHealth.Field.GROSSQTY, now));
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, now));
        assertEquals(1, health.staleFields(now).size());
        assertEquals(MeterFieldHealth.Field.TOTALIZER, health.staleFields(now).get(0));
    }

    @Test
    public void chuaMoVoiKhongBaoDongGia() {
        MeterFieldHealth health = started();

        // Gross = 0 nên bị bộ lọc loại, nhưng thiết bị vẫn trả lời đều: trường CÒN SỐNG.
        for (long t = T0; t <= afterGrace(30_000); t += 1000) {
            health.markRead(MeterFieldHealth.Field.GROSSQTY, t);
            health.markFiltered(MeterFieldHealth.Field.GROSSQTY, t);
        }

        assertFalse(health.isStale(MeterFieldHealth.Field.GROSSQTY, afterGrace(30_000)));
    }

    @Test
    public void tuPhucHoiKhiCoSoTroLai() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.TOTALIZER, T0);

        long broken = afterGrace(10_000);
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, broken));

        // Không có cờ dính: một lần đọc được là hết cảnh báo ngay chu kỳ sau.
        health.markRead(MeterFieldHealth.Field.TOTALIZER, broken);
        assertFalse(health.isStale(MeterFieldHealth.Field.TOTALIZER, broken + 1000));
        assertTrue(health.staleFields(broken + 1000).isEmpty());
    }

    @Test
    public void anHanSauKhiMoMeKhongBaoDong() {
        MeterFieldHealth health = started();
        health.markFail(MeterFieldHealth.Field.TOTALIZER, T0);

        // Trong khoảng ân hạn, dù đã hỏng vẫn im lặng: hàng đợi đăng ký trường còn đang chạy.
        assertFalse(health.isStale(MeterFieldHealth.Field.TOTALIZER,
                T0 + MeterFieldHealth.BATCH_GRACE_MS - 1));
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, afterGrace(1)));
    }

    @Test
    public void chuaCoTinHieuNaoThiKhongKetLuan() {
        MeterFieldHealth health = started();

        // Chưa từng có tín hiệu: "chưa biết" không phải "đã hỏng".
        assertFalse(health.isStale(MeterFieldHealth.Field.TICKET, afterGrace(60_000)));
        assertTrue(health.staleFields(afterGrace(60_000)).isEmpty());
    }

    @Test
    public void dangKyHongLienTucBiBatDuDocChuaBaoGioThanhCong() {
        MeterFieldHealth health = started();
        health.markFail(MeterFieldHealth.Field.TEMPERATURE, T0 + 500);

        assertTrue(health.isStale(MeterFieldHealth.Field.TEMPERATURE, afterGrace(1000)));
    }

    @Test
    public void moPhienMoiXoaSoDemCu() {
        MeterFieldHealth health = started();
        health.markFail(MeterFieldHealth.Field.TOTALIZER, T0);
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, afterGrace(1)));

        health.beginSession(afterGrace(1));
        assertFalse(health.isStale(MeterFieldHealth.Field.TOTALIZER, afterGrace(1)));
    }

    @Test
    public void truongNullKhongNem() {
        MeterFieldHealth health = started();
        health.markRead(null, T0);
        health.markAccepted(null, T0);
        health.markFiltered(null, T0);
        health.markFail(null, T0);
        assertFalse(health.isStale(null, afterGrace(60_000)));
    }

    @Test
    public void tomTatChoLogCoDuSoDem() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.GROSSQTY, T0);
        health.markFiltered(MeterFieldHealth.Field.GROSSQTY, T0);
        health.markFail(MeterFieldHealth.Field.TOTALIZER, T0);

        String log = health.snapshotForLog();
        assertTrue(log, log.contains("GROSSQTY=r1/a0/f1/x0"));
        assertTrue(log, log.contains("TOTALIZER=r0/a0/f0/x1"));
    }

    /**
     * Ca gốc T1-01: đồng hồ TỔNG vẫn trả lời đều nên "đọc được" luôn tươi, nhưng mọi giá trị
     * đều lệch quá ngưỡng nên bị bộ lọc loại — số tổng đóng băng trong khi Gross vẫn tăng.
     * Trước đây trường này bị coi là KHOẺ: không cảnh báo, không giữ số đồng hồ đầu mẻ.
     */
    @Test
    public void tongBiBoLocLoaiLienTucThiCoiLaHong() {
        MeterFieldHealth health = started();

        // Tổng: đọc được mỗi giây nhưng lần nào cũng bị loại. Gross: nhận bình thường.
        for (long t = T0; t <= afterGrace(20_000); t += 1000) {
            health.markRead(MeterFieldHealth.Field.TOTALIZER, t);
            health.markFiltered(MeterFieldHealth.Field.TOTALIZER, t);
            health.markRead(MeterFieldHealth.Field.GROSSQTY, t);
            health.markAccepted(MeterFieldHealth.Field.GROSSQTY, t);
        }

        long now = afterGrace(20_000);
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, now));
        assertFalse(health.isStale(MeterFieldHealth.Field.GROSSQTY, now));
        assertEquals(1, health.staleFields(now).size());
        assertEquals(MeterFieldHealth.Field.TOTALIZER, health.staleFields(now).get(0));

        // Và số đồng hồ đầu mẻ được GIỮ, không bị hiệu (tổng đóng băng − gross) đạp đè.
        assertTrue(MeterFieldHealth.startNumberHeld(5000, 5200, 300, true));
        assertEquals(5000d,
                MeterFieldHealth.resolveStartNumber(5000, 5200, 300, true), 0.0001);
    }

    @Test
    public void tongBiLoaiNganHanChuaDuDeBaoDong() {
        MeterFieldHealth health = started();

        // Vài nhịp nhiễu rồi trở lại bình thường: không được kêu.
        for (long t = afterGrace(0); t <= afterGrace(5000); t += 1000) {
            health.markRead(MeterFieldHealth.Field.TOTALIZER, t);
            health.markFiltered(MeterFieldHealth.Field.TOTALIZER, t);
        }
        assertFalse(health.isStale(MeterFieldHealth.Field.TOTALIZER, afterGrace(5000)));
    }

    @Test
    public void tongDuocNhanTroLaiThiHetCanhBao() {
        MeterFieldHealth health = started();

        for (long t = T0; t <= afterGrace(20_000); t += 1000) {
            health.markRead(MeterFieldHealth.Field.TOTALIZER, t);
            health.markFiltered(MeterFieldHealth.Field.TOTALIZER, t);
        }
        long broken = afterGrace(20_000);
        assertTrue(health.isStale(MeterFieldHealth.Field.TOTALIZER, broken));

        // Không có cờ dính: một giá trị QUA được bộ lọc là hết cảnh báo.
        health.markRead(MeterFieldHealth.Field.TOTALIZER, broken);
        health.markAccepted(MeterFieldHealth.Field.TOTALIZER, broken);
        assertFalse(health.isStale(MeterFieldHealth.Field.TOTALIZER, broken + 1000));
    }

    // ---------------------------------------------------------------------------------
    // Trường "một phát rồi thôi": đọc xong là bị gỡ khỏi hàng đợi, im tiếng là ĐÚNG.
    // ---------------------------------------------------------------------------------

    /**
     * Ca hiện trường: TICKETNUMBER không nằm trong hàng đợi đọc định kỳ. Nó về đúng một lần
     * lúc chốt mẻ rồi {@code LCRReader.onDataChanged} gỡ luôn field khỏi SDK. Trước đây 15
     * giây sau đó màn hình bôi cam trong khi Gross và Tổng vẫn chảy bình thường.
     */
    @Test
    public void truongDaGoKhoiHangDoiThiKhongCoiLaChet() {
        MeterFieldHealth health = started();

        health.markRead(MeterFieldHealth.Field.TICKET, T0 + 1000);
        health.markRetired(MeterFieldHealth.Field.TICKET);

        assertFalse(health.isStale(MeterFieldHealth.Field.TICKET, afterGrace(120_000)));
        assertTrue(health.staleFields(afterGrace(120_000)).isEmpty());
    }

    /** Không gỡ thì vẫn phải kết luận là chết — chống việc "nghỉ" trở thành cửa thoát chung. */
    @Test
    public void chuaGoThiVanKetLuanLaChet() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.TICKET, T0 + 1000);

        assertTrue(health.isStale(MeterFieldHealth.Field.TICKET, afterGrace(1000)));
    }

    /** Đăng ký lại trường: một tín hiệu mới bất kỳ mở lại phép đo. */
    @Test
    public void dangKyLaiTruongThiDoTiep() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.TICKET, T0 + 1000);
        health.markRetired(MeterFieldHealth.Field.TICKET);

        long lai = afterGrace(60_000);
        health.markRead(MeterFieldHealth.Field.TICKET, lai);
        assertFalse(health.isStale(MeterFieldHealth.Field.TICKET, lai + 1000));
        assertTrue(health.isStale(MeterFieldHealth.Field.TICKET,
                lai + MeterFieldHealth.STALE_AFTER_MS + 1));
    }

    /** Gỡ trường KHÔNG được xoá số đếm: nhật ký đối soát sau ca vẫn phải thấy đã đọc mấy lần. */
    @Test
    public void goTruongVanGiuSoDemVaGhiRoTrongLog() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.TICKET, T0);
        health.markAccepted(MeterFieldHealth.Field.TICKET, T0);
        health.markRetired(MeterFieldHealth.Field.TICKET);

        String log = health.snapshotForLog();
        assertTrue(log, log.contains("TICKET=r1/a1/f0/x0/nghi"));
    }

    @Test
    public void moPhienMoiXoaCaTrangThaiNghi() {
        MeterFieldHealth health = started();
        health.markRead(MeterFieldHealth.Field.TICKET, T0);
        health.markRetired(MeterFieldHealth.Field.TICKET);

        health.beginSession(afterGrace(1));
        health.markFail(MeterFieldHealth.Field.TICKET, afterGrace(1));
        assertTrue(health.isStale(MeterFieldHealth.Field.TICKET,
                afterGrace(1) + MeterFieldHealth.BATCH_GRACE_MS
                        + MeterFieldHealth.STALE_AFTER_MS + 1));
    }

    // ---------------------------------------------------------------------------------
    // Chốt ghi vết: chỉ ghi nhật ký khi ĐỔI trạng thái.
    // ---------------------------------------------------------------------------------

    @Test
    public void chotChiBaoGhiKhiDoiTrangThai() {
        MeterFieldHealth.WarningLatch latch = new MeterFieldHealth.WarningLatch();

        assertFalse(latch.update(false));       // bình thường, không có gì để ghi
        assertTrue(latch.update(true));         // chuyển sang có trường chết
        assertFalse(latch.update(true));        // giữ nguyên: vòng canh 2 giây, đừng ghi lại
        assertTrue(latch.isWarning());
        assertTrue(latch.update(false));        // hồi phục
        assertFalse(latch.isWarning());
    }

    @Test
    public void moPhienMoiQuenTrangThaiCu() {
        MeterFieldHealth.WarningLatch latch = new MeterFieldHealth.WarningLatch();
        latch.update(true);

        latch.beginSession();
        assertFalse(latch.isWarning());
        // Và lần chuyển trạng thái tiếp theo vẫn được ghi.
        assertTrue(latch.update(true));
    }

    @Test
    public void grossBiLocLienTucVanKhongBaoDongGia() {
        MeterFieldHealth health = started();

        // CHỐNG HỒI QUY: vòi chưa mở thì Gross = 0 và bị loại liên tục hàng phút. Luật
        // "bị loại lâu = hỏng" CHỈ áp cho TOTALIZER, đúng vì lý do này.
        for (long t = T0; t <= afterGrace(120_000); t += 1000) {
            health.markRead(MeterFieldHealth.Field.GROSSQTY, t);
            health.markFiltered(MeterFieldHealth.Field.GROSSQTY, t);
        }

        assertFalse(health.isStale(MeterFieldHealth.Field.GROSSQTY, afterGrace(120_000)));
        assertTrue(health.staleFields(afterGrace(120_000)).isEmpty());
    }
}
