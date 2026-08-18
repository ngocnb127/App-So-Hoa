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
     * Từ bản 110: dữ liệu chưa gửi KHÔNG còn chặn được cập nhật, kể cả khi hàng đợi vừa
     * mới có bản ghi và chưa có lỗi nào. Trên xe, cập nhật chính là cách đẩy nó đi.
     */
    @Test
    public void aFreshQueueStillLetsTheUserUpdate() {
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(3, 0, NOW - 60_000, NOW);

        assertFalse("vẫn phải báo cho người dùng biết", d.safe);
        assertTrue("nhưng không được chặn", d.overridable);
    }

    /** Có bản ghi gửi lỗi: nói rõ ra, vì đây đúng là ca bản mới thường chữa được. */
    @Test
    public void failedRecordsAreNamedInTheWarning() {
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(3, 1, NOW - 60_000, NOW);

        assertTrue(d.overridable);
        assertTrue(d.reason.contains("gửi lỗi"));
        assertTrue("phải nói rõ dữ liệu không mất", d.reason.contains("KHÔNG mất"));
    }

    /** Chờ quá lâu mà hàng đợi không vơi cũng phải được nói ra. */
    @Test
    public void waitingTooLongIsNamedInTheWarning() {
        long since = NOW - UpdateSafetyGuard.STUCK_AFTER_MS - 1000;
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(2, 0, since, NOW);

        assertTrue(d.overridable);
        assertTrue(d.reason.contains("30 phút"));
    }

    /** Chưa quá ngưỡng thì chỉ nói là đang gửi, không doạ người dùng bằng chữ "quá hạn". */
    @Test
    public void justUnderTheThresholdReadsAsStillSyncing() {
        long since = NOW - UpdateSafetyGuard.STUCK_AFTER_MS;
        UpdateSafetyGuard.Decision d = UpdateSafetyGuard.decide(2, 0, since, NOW);

        assertTrue(d.overridable);
        assertTrue(d.reason.contains("đang gửi"));
    }

    /** Không một tổ hợp nào được chặn cứng — đó chính là bế tắc mà bản 110 đi sửa. */
    @Test
    public void noCombinationOfPendingDataEverBlocksTheUpdate() {
        int[] counts = {1, 5, 500};
        int[] failures = {0, 1, 500};
        long[] ages = {0, NOW - 1000, NOW - UpdateSafetyGuard.STUCK_AFTER_MS * 10};

        for (int pending : counts)
            for (int failed : failures)
                for (long since : ages)
                    assertTrue("pending=" + pending + " failed=" + failed + " since=" + since,
                            UpdateSafetyGuard.decide(pending, failed, since, NOW).overridable);
    }

    /** Lý do luôn phải nói được số bản ghi — người dùng cần biết mình đang bỏ lại cái gì. */
    @Test
    public void reasonAlwaysNamesTheCount() {
        assertTrue(UpdateSafetyGuard.decide(7, 0, NOW, NOW).reason.contains("7"));
        assertNull(UpdateSafetyGuard.decide(0, 0, 0, NOW).reason);
        assertTrue(UpdateSafetyGuard.decide(7, 2, 0, NOW).reason.contains("7"));
        assertTrue(UpdateSafetyGuard.decide(7, 2, 0, NOW).reason.contains("2"));
    }
}
