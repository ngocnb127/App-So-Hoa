package com.megatech.fms.helpers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Bão CONFLICT trên xe HAN3-20-7006 ngày 25-08-2026 (bản 114).
 *
 * <p>Chuyến VN 263 hỏng autosave liên tục 9 phút — khoảng 430 lần lưu trượt, mỗi giây một
 * lần — và chỉ thoát được nhờ đường EndFieldsPatch lúc chốt mẻ:
 *
 * <pre>
 * 19:01:09  POST seq=7 baseRev=3 → ACK rev=4
 * 19:01:10  REBASED_ON_SERVER_METADATA oldBaseRev=3 storedRev=4
 * 19:01:29  [RFW] Lưu nền chưa thành công: CONFLICT
 * 19:02:00  ... CONFLICT (đã nén 25 lần giống hệt)   ← lặp tới 19:10:26
 * </pre>
 *
 * <p>Bộ test này ghim NGUYÊN NHÂN và ĐIỀU KIỆN AN TOÀN của bản vá
 * {@code DataHelper.rebaseScreenOnStored}. Bản thân hàm đó cần Room nên không test ở đây;
 * cái phải giữ đúng là luật quyết định mà nó dựa vào.
 */
public class RefuelSaveRebaseTest {

    /** Payload nghiệp vụ của mẻ, kèm metadata để chứng minh metadata không vào vân tay. */
    private static String json(double realAmount, long clientSeq, int serverRevision) {
        return "{"
                + "\"UniqueId\":\"743e7272-7053-45c3-b437-01ad6764ac50\","
                + "\"Id\":2119814,"
                + "\"ClientSeq\":" + clientSeq + ","
                + "\"ServerRevision\":" + serverRevision + ","
                + "\"RealAmount\":" + realAmount + ","
                + "\"Gallon\":" + realAmount + ","
                + "\"StartNumber\":78526647,"
                + "\"EndNumber\":78527984"
                + "}";
    }

    /**
     * Vân tay CHỈ băm số liệu nghiệp vụ. Đây là nền móng của bản vá: nếu metadata lọt vào
     * vân tay thì không còn cách nào phân biệt "row nhích biến đếm" với "ai đó sửa số liệu".
     */
    @Test
    public void vanTayBoQuaClientSeqVaServerRevision() {
        String truoc = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 7, 3));
        String sau = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 9, 4));

        assertNotNull(truoc);
        assertEquals("Seq/revision nhích không được làm đổi vân tay nghiệp vụ", truoc, sau);
    }

    /** Đổi sản lượng thì phải đổi vân tay — nếu không, xung đột thật sẽ lọt lưới. */
    @Test
    public void doiSanLuongThiDoiVanTay() {
        assertNotEquals(
                RefuelSyncGuard.businessFingerprintOfJson(json(1337, 7, 3)),
                RefuelSyncGuard.businessFingerprintOfJson(json(1400, 7, 3)));
    }

    /**
     * CÁI BẪY, tái hiện đúng ca VN 263: lượt ghi nền nhích ClientSeq của row trong khi màn
     * hình đang mở. Số liệu nghiệp vụ y nguyên, vậy mà lần lưu kế tiếp bị chặn.
     *
     * <p>Đây mới chỉ là một lần trượt. Thảm hoạ nằm ở chỗ trước bản vá màn hình KHÔNG học
     * lại baseline, nên nó trượt mãi mãi.
     */
    @Test
    public void seqNhichNhungSoLieuKhongDoi_vanBiChan() {
        String vanTay = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 9, 4));

        RefuelSyncGuard.SaveDecision quyetDinh = RefuelSyncGuard.decideSave(
                /* storedClientSeq */ 9, /* storedServerRevision */ 4, vanTay,
                /* baseClientSeq   */ 7, /* baseServerRevision   */ 3, vanTay);

        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_CLIENT_MOVED, quyetDinh);
    }

    /**
     * ĐIỀU KIỆN CHO PHÉP REBASE: vân tay row bằng vân tay màn hình đã chốt lần trước.
     * Bằng nhau nghĩa là chưa ai sửa số liệu, nên đứng lên phiên bản mới không cướp việc
     * của ai — đúng tinh thần ALLOW_REBASE_SERVER_METADATA, mở rộng cho cả seq.
     */
    @Test
    public void vanTayBangNhau_thiRebaseLaAnToan() {
        String vanTay = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 9, 4));

        // Sau khi rebase, màn hình đứng đúng trên row: seq và revision khớp.
        assertEquals(RefuelSyncGuard.SaveDecision.ALLOW_EXACT,
                RefuelSyncGuard.decideSave(9, 4, vanTay, 9, 4, vanTay));
    }

    /**
     * XUNG ĐỘT THẬT phải giữ nguyên chặn. Web sửa sản lượng trong khi màn hình đang mở:
     * vân tay khác nhau, bản vá từ chối rebase, số liệu của Web không bị ghi đè.
     */
    @Test
    public void soLieuDaDoi_thiVanChan() {
        String vanTayManHinh = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 7, 3));
        String vanTayRow = RefuelSyncGuard.businessFingerprintOfJson(json(1400, 7, 4));

        RefuelSyncGuard.SaveDecision quyetDinh = RefuelSyncGuard.decideSave(
                7, 4, vanTayRow,
                7, 3, vanTayManHinh);

        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_PAYLOAD_CHANGED, quyetDinh);
    }

    /** Row lùi phiên bản là dữ liệu không nhất quán — không rebase, không lưu. */
    @Test
    public void revisionLui_thiVanChan() {
        String vanTay = RefuelSyncGuard.businessFingerprintOfJson(json(1337, 7, 3));

        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_REVISION_REGRESSED,
                RefuelSyncGuard.decideSave(7, 2, vanTay, 7, 5, vanTay));
    }
}
