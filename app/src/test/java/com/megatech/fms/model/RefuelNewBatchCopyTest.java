package com.megatech.fms.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.FMSApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Date;

/**
 * "Nạp thêm" từ một mẻ của XE KHÁC phải ra một mẻ mới sạch định danh.
 *
 * <p>Nghiệp vụ cho phép nhìn phiếu xe bạn trên chuyến rồi nạp tiếp phần còn lại. Bản vá toàn
 * vẹn dữ liệu trước đó chặn thẳng thao tác này, làm chết tính năng. Thứ thật sự cần chặn là
 * bản sao mang theo định danh của phiếu nguồn — đó là điều các test dưới đây khoá lại.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class RefuelNewBatchCopyTest {

    private static final String SOURCE_UID = "6f1c0f5e-2d43-4a91-8bb0-9d2f7e5a41c7";

    /** Mẻ ĐÃ CHỐT của xe khác, đúng hình dạng một replica server trong Room. */
    private RefuelItemData foreignDoneBatch() {
        RefuelItemData item = new RefuelItemData();
        item.setId(2122137);
        item.setLocalId(2);
        item.setUniqueId(SOURCE_UID);
        item.setTruckId(134);
        item.setTruckNo("DEMO 02");
        item.setFlightId(998877);
        item.setFlightCode("VN 7561");
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(1061);
        item.setStartNumber(44685105);
        item.setEndNumber(44686166);
        item.setStartTime(new Date(1_787_000_100_000L));
        item.setEndTime(new Date(1_787_000_900_000L));
        item.setReceiptNumber("2619HG9");
        item.setInvoiceNumber("INV-123");
        item.setClientSeq(10);
        item.setServerRevision(8);
        item.setPostStatus(RefuelItemData.ITEM_POST_STATUS.SUCCESS);
        item.setRawJson("{\"UniqueId\":\"" + SOURCE_UID + "\",\"Id\":2122137,"
                + "\"TruckNo\":\"DEMO 02\",\"TruckId\":134}");
        item.setBaseJson(item.getRawJson());
        item.setBaseClientSeq(10);
        item.setBaseServerRevision(8);
        item.setBaseBusinessFingerprint("fingerprint-cua-xe-khac");
        item.setOthers(Collections.singletonList(new RefuelItemData()));
        item.setCompleteOthersSnapshot(true);
        return item;
    }

    @Test
    public void newBatchBelongsToThisTruckAndKeepsTheFlight() {
        RefuelItemData source = foreignDoneBatch();

        RefuelItemData fresh = source.copyForNewBatch(34, "DEMO-03");

        assertNotNull(fresh);
        assertEquals(34, (int) fresh.getTruckId());
        assertEquals("DEMO-03", fresh.getTruckNo());
        // Cùng chuyến — đó chính là mục đích của "nạp thêm".
        assertEquals(998877, (int) fresh.getFlightId());
        assertEquals("VN 7561", fresh.getFlightCode());
    }

    @Test
    public void newBatchCarriesNoIdentityOfTheSourceRow() {
        RefuelItemData source = foreignDoneBatch();

        RefuelItemData fresh = source.copyForNewBatch(34, "DEMO-03");

        assertEquals(0, fresh.getId() == null ? 0 : fresh.getId().intValue());
        assertEquals(0, fresh.getLocalId());
        assertNotEquals(SOURCE_UID, fresh.getUniqueId());
        assertNotNull(fresh.getUniqueId());
        assertFalse(fresh.getUniqueId().trim().isEmpty());
    }

    /**
     * Trọng tâm: {@code copy()} dùng {@code clone()} nên raw/base JSON của phiếu nguồn sống
     * sót. Để nguyên thì dữ liệu xe khác có đường lên server dưới danh nghĩa xe này.
     */
    @Test
    public void newBatchDropsForeignRawAndBaselineJson() {
        RefuelItemData source = foreignDoneBatch();

        RefuelItemData fresh = source.copyForNewBatch(34, "DEMO-03");

        assertNull(fresh.getRawJson());
        assertNull(fresh.getBaseJson());
        assertNull(fresh.getBaseBusinessFingerprint());
        assertEquals(0, fresh.getBaseClientSeq());
        assertEquals(0, fresh.getBaseServerRevision());
    }

    /** Mẻ mới chưa nạp gì: không được vào màn tra nạp ở trạng thái đã chốt. */
    @Test
    public void newBatchStartsEmptyAndNotDone() {
        RefuelItemData source = foreignDoneBatch();

        RefuelItemData fresh = source.copyForNewBatch(34, "DEMO-03");

        assertEquals(REFUEL_ITEM_STATUS.NONE, fresh.getStatus());
        assertEquals(0d, fresh.getRealAmount(), 0d);
        assertEquals(0d, fresh.getStartNumber(), 0d);
        assertEquals(0d, fresh.getEndNumber(), 0d);
        assertNull(fresh.getReceiptNumber());
        assertNull(fresh.getInvoiceNumber());
    }

    /** Collection Others thuộc phiếu gốc; mang sang sẽ làm hỏng phiếu gộp của mẻ mới. */
    @Test
    public void newBatchDoesNotInheritOthersCollection() {
        RefuelItemData source = foreignDoneBatch();

        RefuelItemData fresh = source.copyForNewBatch(34, "DEMO-03");

        assertFalse(fresh.hasCompleteOthersSnapshot());
        assertTrue(fresh.getOthers() == null || fresh.getOthers().isEmpty());
    }

    /** Phiếu nguồn là replica xe khác: tuyệt đối không được đụng vào. */
    @Test
    public void sourceRowIsNotMutated() {
        RefuelItemData source = foreignDoneBatch();
        String before = source.toJson();

        source.copyForNewBatch(34, "DEMO-03");

        assertEquals(before, source.toJson());
        assertEquals("DEMO 02", source.getTruckNo());
        assertEquals(SOURCE_UID, source.getUniqueId());
    }
}
