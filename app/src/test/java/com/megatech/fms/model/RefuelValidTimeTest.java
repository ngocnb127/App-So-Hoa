package com.megatech.fms.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

/**
 * Cổng giờ tra nạp của màn XÁC NHẬN ({@code RefuelItemData.validTime()}).
 *
 * <p>Ba việc khác nhau, không được gộp:
 * <ul>
 *   <li>Mẻ 0 phút CHO QUA — quyết định nghiệp vụ: có ca bơm rất ít, và chính
 *       {@code finalizStop()} tạo ra ca này khi không bắt được sự kiện bắt đầu.</li>
 *   <li>Giờ đảo ngược VẪN CHẶN CỨNG — đây là ca chống hồi quy quan trọng nhất.</li>
 *   <li>Thiếu giờ KHÔNG được ném NPE.</li>
 * </ul>
 */
public class RefuelValidTimeTest {

    private static final long T = 1_787_000_100_000L;

    private static RefuelItemData item(Date start, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setStartTime(start);
        item.setEndTime(end);
        return item;
    }

    @Test
    public void meBinhThuongHopLe() {
        assertTrue(item(new Date(T), new Date(T + 20 * 60 * 1000)).validTime());
    }

    /** FA-6: mẻ 0 phút cho qua. */
    @Test
    public void meKhongPhutChoQua() {
        assertTrue("mẻ 0 phút phải xuất được phiếu",
                item(new Date(T), new Date(T)).validTime());
    }

    /** CHỐNG HỒI QUY: giờ đảo ngược vẫn chặn cứng. */
    @Test
    public void gioDaoNguocVanChanCung() {
        assertFalse("end < start phải bị chặn",
                item(new Date(T + 1000), new Date(T)).validTime());
        assertFalse("lệch một giờ vẫn phải bị chặn",
                item(new Date(T + 3600_000), new Date(T)).validTime());
    }

    /** Thiếu giờ: trả false, KHÔNG được ném NPE. */
    @Test
    public void thieuGioKhongNemNPE() {
        assertFalse(item(null, new Date(T)).validTime());
        assertFalse(item(new Date(T), null).validTime());
        assertFalse(item(null, null).validTime());
    }

    /** Mẻ dài quá 2 giờ vẫn bị chặn như trước — FA-6 không được nới nhầm chỗ này. */
    @Test
    public void meDaiQuaHaiGioVanChan() {
        assertFalse(item(new Date(T), new Date(T + 121 * 60 * 1000)).validTime());
        assertTrue(item(new Date(T), new Date(T + 119 * 60 * 1000)).validTime());
    }
}
