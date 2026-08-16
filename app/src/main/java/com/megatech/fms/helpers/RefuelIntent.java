package com.megatech.fms.helpers;

import android.content.Intent;
import android.os.Bundle;

import com.megatech.fms.model.RefuelItemData;

/**
 * Truyền phiếu tra nạp qua Intent kèm BASELINE.
 *
 * <p>Baseline (version + vân tay payload nền) là {@code transient} nên không nằm trong JSON,
 * và cũng KHÔNG được suy ra lúc parse: vân tay tính tại thời điểm parse là vân tay của payload
 * người dùng đang sửa, không phải của bản nền đã đọc từ Room. Nếu để mất baseline thì
 * precondition khi lưu bị bỏ qua và snapshot cũ ghi đè được dữ liệu mới.
 *
 * <p>Baseline đi trong extras riêng, không bao giờ nằm trong payload gửi lên server.
 */
public final class RefuelIntent {

    public static final String EXTRA_REFUEL = "REFUEL";
    private static final String EXTRA_BASE_SEQ = "REFUEL_BASE_SEQ";
    private static final String EXTRA_BASE_REV = "REFUEL_BASE_REV";
    private static final String EXTRA_BASE_FINGERPRINT = "REFUEL_BASE_FP";

    private RefuelIntent() {
    }

    /** Đặt phiếu vào Intent kèm baseline hiện có của snapshot. */
    public static void putRefuel(Intent intent, RefuelItemData data) {
        if (intent == null || data == null) return;

        intent.putExtra(EXTRA_REFUEL, data.toJson());
        intent.putExtra(EXTRA_BASE_SEQ, data.getBaseClientSeq());
        intent.putExtra(EXTRA_BASE_REV, data.getBaseServerRevision());
        intent.putExtra(EXTRA_BASE_FINGERPRINT, data.getBaseBusinessFingerprint());
    }

    /** Đọc phiếu từ Intent và khôi phục đúng baseline đã gửi kèm. */
    public static RefuelItemData readRefuel(Bundle bundle) {
        if (bundle == null) return null;

        String json = bundle.getString(EXTRA_REFUEL, "");
        if (json == null || json.isEmpty()) return null;

        RefuelItemData data = RefuelItemData.fromJson(json);
        restoreBaseline(data, bundle);
        return data;
    }

    /** Khôi phục baseline cho một snapshot đã được parse sẵn từ extras của cùng Bundle. */
    public static void restoreBaseline(RefuelItemData data, Bundle bundle) {
        if (data == null || bundle == null) return;

        data.setBaseClientSeq(bundle.getLong(EXTRA_BASE_SEQ, RefuelItemData.VERSION_UNKNOWN));
        data.setBaseServerRevision(bundle.getInt(EXTRA_BASE_REV, (int) RefuelItemData.VERSION_UNKNOWN));
        data.setBaseBusinessFingerprint(bundle.getString(EXTRA_BASE_FINGERPRINT, null));
    }
}
