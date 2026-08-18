package com.megatech.fms.helpers.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Cổng chặn cài đặt: chặn TẠM THỜI, không bao giờ chặn vĩnh viễn.
 *
 * <p>Bản trước chặn không điều kiện khi còn bản ghi chưa gửi, tạo ra bế tắc thật: dữ liệu
 * kẹt vì lỗi thì chờ bao lâu cũng không đi, mà bản sửa đúng lỗi đó lại nằm sau đúng cánh
 * cửa đang khoá.
 */
public class UpdateSafetyGuardTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void nothingPendingIsAlwaysSafe() {
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(0, 0, 0, NOW);

        assertTrue(d.safe);
        assertNull(d.reason);
    }

    /**
     * Hàng đợi vừa mới có bản ghi và chưa có lỗi: nhiều khả năng đang gửi. Chặn thật, không
     * cho vượt — chờ vài phút là xong, cho vượt ở đây chỉ làm chậm đồng bộ không cần thiết.
     */
    @Test
    public void freshQueueBlocksWithoutAnEscapeHatch() {
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(3, 0, NOW - 60_000, NOW);

        assertFalse(d.safe);
        assertFalse("hàng đợi mới thì không mở lối vượt", d.overridable);
    }

    /**
     * Có bản ghi đã gửi và bị từ chối: biết chắc nó không tự đi được. Đây đúng là ca mà bản
     * mới thường là thứ chữa được, nên phải cho người dùng cập nhật.
     */
    @Test
    public void failedRecordsOpenTheEscapeHatchImmediately() {
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(3, 1, NOW - 60_000, NOW);

        assertFalse(d.safe);
        assertTrue("dữ liệu kẹt vì lỗi phải cho phép cập nhật", d.overridable);
        assertTrue("phải nói rõ dữ liệu không mất", d.reason.contains("KHÔNG mất"));
    }

    /**
     * Không có bản ghi báo lỗi nhưng hàng đợi không hề vơi sau nửa giờ: cũng là kẹt, chỉ là
     * kẹt kiểu khác (mất mạng kéo dài, server từ chối im lặng).
     */
    @Test
    public void waitingTooLongAlsoOpensTheEscapeHatch() {
        long since = NOW - UpdateSafetyGuard.STUCK_AFTER_MS - 1000;
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(2, 0, since, NOW);

        assertFalse(d.safe);
        assertTrue(d.overridable);
    }

    /** Ngay sát ngưỡng vẫn coi là đang gửi — ngưỡng phải là "quá", không phải "bằng". */
    @Test
    public void justUnderTheThresholdIsStillTreatedAsSyncing() {
        long since = NOW - UpdateSafetyGuard.STUCK_AFTER_MS;
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(2, 0, since, NOW);

        assertFalse(d.safe);
        assertFalse(d.overridable);
    }

    /**
     * Không biết hàng đợi có từ bao giờ (mốc chưa ghi được) thì không được suy ra là đã kẹt
     * — nhưng cờ lỗi vẫn phải quyết định được.
     */
    @Test
    public void unknownQueueAgeFallsBackToTheErrorFlag() {
        assertFalse(UpdateSafetyGuard.decide(2, 0, 0, NOW).overridable);
        assertTrue(UpdateSafetyGuard.decide(2, 1, 0, NOW).overridable);
    }

    /** Lý do luôn phải nói được số bản ghi — người dùng cần biết mình đang bỏ lại cái gì. */
    @Test
    public void reasonAlwaysNamesTheCount() {
        assertTrue(UpdateSafetyGuard.decide(7, 0, NOW, NOW).reason.contains("7"));
        assertTrue(UpdateSafetyGuard.decide(7, 2, 0, NOW).reason.contains("7"));
        assertTrue(UpdateSafetyGuard.decide(7, 2, 0, NOW).reason.contains("2"));
    }
}
