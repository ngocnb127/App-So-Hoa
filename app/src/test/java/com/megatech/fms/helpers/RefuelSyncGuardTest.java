package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RefuelSyncGuardTest {

    @Test
    public void preservesModifiedLocalWhenJsonChangedDuringRequest() {
        assertTrue(RefuelSyncGuard.shouldPreserveLocal("before", "new edit", true));
    }

    @Test
    public void appliesServerResponseWhenLocalDidNotChange() {
        assertFalse(RefuelSyncGuard.shouldPreserveLocal("same", "same", true));
    }

    @Test
    public void doesNotPreserveUnmodifiedServerRefresh() {
        assertFalse(RefuelSyncGuard.shouldPreserveLocal("before", "remote refresh", false));
    }

    @Test
    public void detectsNewLocalRowCreatedDuringRequest() {
        assertTrue(RefuelSyncGuard.shouldPreserveLocal(null, "new local row", true));
    }

    /**
     * The incident case: a brand new row whose JSON still carries Id=0. If the merge
     * only wrote the entity column, the next payload built by toRefuelItemData()
     * would post Id=0 again and the server would create a duplicate refuel.
     */
    @Test
    public void mergeWritesServerIdIntoJsonPayload() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        assertEquals(2087328, local.getId());
        assertEquals(Integer.valueOf(2087328), local.toRefuelItemData().getId());
    }

    @Test
    public void mergeKeepsLocalPayloadAndAdoptsServerVersion() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        RefuelItemData merged = local.toRefuelItemData();
        assertEquals(1110d, merged.getRealAmount(), 0d);
        assertEquals(60166240d, merged.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, merged.getStatus());
        assertEquals(6, merged.getServerRevision());
        assertEquals(6, local.getServerRevision());
    }

    @Test
    public void mergeKeepsRowQueuedForTheNextSync() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        assertTrue(local.isLocalModified());
        assertTrue(local.toRefuelItemData().isLocalModified());
    }

    @Test
    public void mergeKeepsLocalUniqueIdWhenServerReturnsNull() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, null, 862, 6));

        assertEquals("uid-local", local.getUniqueId());
        assertEquals("uid-local", local.toRefuelItemData().getUniqueId());
    }

    @Test
    public void mergeKeepsLocalIdWhenServerReturnsZero() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(0, "uid-local", 862, 6));

        assertEquals(2087328, local.getId());
        assertEquals(Integer.valueOf(2087328), local.toRefuelItemData().getId());
    }

    private static RefuelItem localRow(int id, String uniqueId, double realAmount,
                                       REFUEL_ITEM_STATUS status) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uniqueId);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(status);
        data.setRealAmount(realAmount);
        data.setStartNumber(60165130);
        data.setEndNumber(60166240);
        data.setEndTime(new Date(1_769_000_000_000L));
        data.setServerRevision(5);

        RefuelItem item = RefuelItem.fromRefuelItemData(data);
        item.setLocalModified(true);
        return item;
    }

    private static RefuelItemData serverResponse(int id, String uniqueId, double realAmount,
                                                 int serverRevision) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uniqueId);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setRealAmount(realAmount);
        data.setStartNumber(60165130);
        data.setEndNumber(60165992);
        data.setServerRevision(serverRevision);
        return data;
    }
}
