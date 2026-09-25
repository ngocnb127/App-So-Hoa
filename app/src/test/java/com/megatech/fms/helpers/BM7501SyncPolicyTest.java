package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.helpers.BM7501SyncPolicy.Action;

import org.junit.Test;

/**
 * Chính sách đẩy phiếu BM 75.01 lên server.
 *
 * <p>Theo bảng mã lỗi đội API chốt ngày 2026-09-23. Sai ở đây hỏng theo hai kiểu ngược nhau:
 * hoặc phiếu quay vòng retry vô hạn (409 gửi lại cũng hỏng như cũ), hoặc phiếu đã in bị bỏ
 * quên vì tưởng là lỗi vĩnh viễn (404 lúc API chưa deploy).
 */
public class BM7501SyncPolicyTest {

    @Test
    public void thanhCong_thiDanhDauDaDongBo() {
        assertEquals(Action.SYNCED, BM7501SyncPolicy.decide(200, true));
    }

    @Test
    public void canhBaoDoiSoat_vanLaDaLuu_khongGuiLai() {
        assertTrue(BM7501SyncPolicy.hasWarning(200, true, "Trung du lieu voi phieu khac"));
        assertEquals("Có cảnh báo vẫn là đã lưu trên server",
                Action.SYNCED, BM7501SyncPolicy.decide(200, true));
    }

    @Test
    public void khongCoCanhBao_khiMessageRong() {
        assertFalse(BM7501SyncPolicy.hasWarning(200, true, null));
        assertFalse(BM7501SyncPolicy.hasWarning(200, true, "   "));
        assertFalse("409 không phải cảnh báo mà là từ chối",
                BM7501SyncPolicy.hasWarning(409, false, "LocalNumber da thuoc ve phieu khac"));
    }

    @Test
    public void xungDot409_dungGui_choNhanVienXuLy() {
        assertEquals(Action.STOP, BM7501SyncPolicy.decide(409, false));
    }

    @Test
    public void duLieuSai400_dungGui() {
        assertEquals(Action.STOP, BM7501SyncPolicy.decide(400, false));
    }

    @Test
    public void tokenHong401_thiDangNhapLai() {
        assertEquals(Action.REAUTH, BM7501SyncPolicy.decide(401, false));
    }

    /** API chưa lên production: đội API dặn giữ phiếu trong outbox, đừng coi là hỏng. */
    @Test
    public void chuaDeploy404_vanGiuPhieuDeGuiLai() {
        assertEquals(Action.RETRY, BM7501SyncPolicy.decide(404, false));
    }

    @Test
    public void loiServer5xx_vaMatMang_thiGuiLai() {
        assertEquals(Action.RETRY, BM7501SyncPolicy.decide(500, false));
        assertEquals(Action.RETRY, BM7501SyncPolicy.decide(503, false));
        assertEquals("Mã 0 = không gọi được server", Action.RETRY, BM7501SyncPolicy.decide(0, false));
    }

    /** 200 nhưng server báo Success=false: gửi lại cũng vậy, đừng quay vòng. */
    @Test
    public void http200NhungSuccessFalse_thiDungGui() {
        assertEquals(Action.STOP, BM7501SyncPolicy.decide(200, false));
    }
}
