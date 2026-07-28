package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.RefuelItemData;

import java.util.Objects;

/** Pure decisions used by Refuel synchronization; kept Android-free for unit tests. */
public final class RefuelSyncGuard {
    private RefuelSyncGuard() {
    }

    public static boolean shouldPreserveLocal(String jsonBeforeRequest,
                                              String newestLocalJson,
                                              boolean newestLocalModified) {
        return newestLocalModified && !Objects.equals(jsonBeforeRequest, newestLocalJson);
    }

    /**
     * Adopt the identity/version fields the server just confirmed while keeping the
     * refuel payload the user edited while the request was in flight.
     *
     * <p>{@link RefuelItem#toRefuelItemData()} rebuilds the model from {@code jsonData}
     * and only overrides localId/localModified/uniqueId, so writing the server values
     * to the entity columns alone would leave the next POST payload carrying the old
     * {@code Id} — for a brand new row that means the server creates a duplicate
     * refuel. Both the columns and the JSON must be updated together.
     *
     * <p>The row stays {@code localModified} so the next sync pushes the newer local
     * payload.
     */
    public static void mergeServerMetadata(RefuelItem localItem, RefuelItemData serverData) {
        if (localItem == null || serverData == null) return;

        // An abnormal response must never erase an identity the row already has.
        int mergedId = serverData.getId() != null && serverData.getId() > 0
                ? serverData.getId() : localItem.getId();
        String mergedUniqueId = isBlank(serverData.getUniqueId())
                ? localItem.getUniqueId() : serverData.getUniqueId();

        RefuelItemData preserved = localItem.toRefuelItemData();
        preserved.setId(mergedId);
        if (!isBlank(mergedUniqueId))
            preserved.setUniqueId(mergedUniqueId);
        preserved.setClientSeq(serverData.getClientSeq());
        preserved.setServerRevision(serverData.getServerRevision());
        if (serverData.getDateUpdated() != null)
            preserved.setDateUpdated(serverData.getDateUpdated());

        localItem.setId(mergedId);
        if (!isBlank(mergedUniqueId))
            localItem.setUniqueId(mergedUniqueId);
        localItem.setClientSeq(serverData.getClientSeq());
        localItem.setServerRevision(serverData.getServerRevision());
        if (serverData.getDateUpdated() != null)
            localItem.setDateUpdated(serverData.getDateUpdated());
        localItem.setJsonData(preserved.toJson());
        localItem.setLocalModified(true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
