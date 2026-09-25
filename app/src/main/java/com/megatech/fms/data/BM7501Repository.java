package com.megatech.fms.data;

import androidx.room.Transaction;

import com.megatech.fms.data.dao.BM7501Dao;
import com.megatech.fms.data.entity.BM7501;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.BusinessStatus;
import com.megatech.fms.model.BM7501Model.SyncStatus;

import java.util.Date;

/**
 * Tầng ghi DUY NHẤT của bảng BM7501.
 *
 * <p>Không Activity/Fragment/worker nào được gọi thẳng {@link BM7501Dao} để ghi. Lý do:
 * chứng từ này bị nhiều nguồn đụng vào cùng lúc (autosave từng ô, callback thiết bị đến trễ,
 * đồng bộ) nên mọi lần ghi phải đi qua so-sánh-rồi-đổi theo {@code localRevision}.
 *
 * <p>Xem docs/PLAN-BM7501-HUT-NHIEN-LIEU.md §4.
 */
public class BM7501Repository {

    /** Kết quả một lần ghi: thành công, xung đột (phải đọc lại), hay sai trạng thái. */
    public enum WriteResult {
        OK,
        CONFLICT,
        INVALID_STATE
    }

    private final BM7501Dao dao;

    public BM7501Repository(BM7501Dao dao) {
        this.dao = dao;
    }

    // ------------------------------------------------------------------ đọc

    /** Phiếu đang hiệu lực của một mẻ hút, null nếu chưa có. */
    public BM7501Model getActive(String refuelItemUniqueId) {
        if (refuelItemUniqueId == null) return null;
        BM7501 entity = dao.getActiveByRefuelItem(refuelItemUniqueId);
        return entity == null ? null : entity.toModel();
    }

    /**
     * Bất biến "mỗi mẻ chỉ một phiếu hiệu lực" không được DB ép buộc (Room không biểu diễn
     * được partial unique index), nên phải tự kiểm tra mỗi lần mở phiếu.
     *
     * @return true nếu dữ liệu bất thường — tầng gọi phải KHOÁ nhập/in và ghi log,
     *         tuyệt đối không tự chọn bừa một revision.
     */
    public boolean hasMultipleActive(String refuelItemUniqueId) {
        if (refuelItemUniqueId == null) return false;
        return dao.countActiveByRefuelItem(refuelItemUniqueId) > 1;
    }

    public int countPendingSync() {
        return dao.countPendingSync();
    }

    /** Phiếu chờ đẩy lên server, mới nhất theo thứ tự lập. */
    public java.util.List<BM7501Model> getPendingSync() {
        java.util.List<BM7501Model> out = new java.util.ArrayList<>();
        for (BM7501 entity : dao.getPendingSync()) {
            if (entity != null) out.add(entity.toModel());
        }
        return out;
    }

    // ------------------------------------------------------------------ tạo

    /**
     * Tạo phiếu mới cho một mẻ hút. Nếu mẻ đã có phiếu hiệu lực thì TRẢ VỀ phiếu đó,
     * không tạo thêm — đúng quan hệ một mẻ một phiếu.
     */
    @Transaction
    public BM7501Model createOrGetActive(BM7501Model model) {
        if (model == null || model.getRefuelItemUniqueId() == null) {
            throw new IllegalArgumentException("Thiếu refuelItemUniqueId");
        }

        BM7501 existing = dao.getActiveByRefuelItem(model.getRefuelItemUniqueId());
        if (existing != null) {
            return existing.toModel();
        }

        model.setRevisionNumber(dao.getMaxRevision(model.getRefuelItemUniqueId()) + 1);
        model.setBusinessStatus(BusinessStatus.DRAFT);
        model.setSyncStatus(SyncStatus.NOT_READY);
        model.setLocalRevision(0);

        BM7501 entity = BM7501.fromModel(model);
        entity.setLocalModified(true);
        entity.setSynced(false);
        entity.setDateUpdated(new Date());

        long localId = dao.insert(entity);
        model.setLocalId((int) localId);
        return model;
    }

    // ------------------------------------------------------------------ ghi

    /**
     * Autosave nội dung. Chỉ ghi khi phiên bản đang cầm còn đúng và phiếu còn sửa được.
     *
     * <p>{@link WriteResult#CONFLICT} nghĩa là có bản ghi mới hơn — tầng gọi phải đọc lại
     * và xử lý, KHÔNG được ghi đè và không retry mù.
     */
    public WriteResult savePayload(BM7501Model model) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (!model.isEditable()) return WriteResult.INVALID_STATE;

        int affected = dao.updatePayloadIfUnchanged(
                model.getUniqueId(),
                model.toJson(),
                model.getLocalRevision(),
                System.currentTimeMillis());

        if (affected == 0) return WriteResult.CONFLICT;

        model.setLocalRevision(model.getLocalRevision() + 1);
        return WriteResult.OK;
    }

    /**
     * Điền số phiếu khi mẻ hút được cấp số sau lúc lập phiếu.
     *
     * <p>Số chứng từ nằm ở cột phẳng nên {@link #savePayload} không chạm tới được; không có
     * đường này thì phiếu lập sớm sẽ mãi không có số và không ký được.
     *
     * @return {@link WriteResult#OK} nếu vừa điền được; {@link WriteResult#INVALID_STATE}
     *         nếu phiếu đã có số hoặc đã khoá — đều không phải lỗi cần báo người dùng.
     */
    public WriteResult fillLocalNumber(BM7501Model model, String localNumber) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (localNumber == null || localNumber.trim().isEmpty()) return WriteResult.INVALID_STATE;
        if (model.getLocalNumber() != null && !model.getLocalNumber().trim().isEmpty()) {
            return WriteResult.INVALID_STATE;
        }

        int affected = dao.fillLocalNumberIfEmpty(
                model.getUniqueId(), localNumber.trim(), System.currentTimeMillis());
        if (affected == 0) return WriteResult.INVALID_STATE;

        model.setLocalNumber(localNumber.trim());
        model.setLocalRevision(model.getLocalRevision() + 1);
        return WriteResult.OK;
    }

    /**
     * Xuất phiếu: chốt sổ. Ghi nốt nội dung rồi khoá — sau lệnh này chỉ in lại được.
     *
     * <p>Tầng gọi phải kiểm tra đủ thông tin và có số phiếu TRƯỚC khi gọi; ở đây chỉ lo phần
     * ghi cho đúng thứ tự (nội dung trước, khoá sau) để không xuất nhầm một bản còn thiếu.
     */
    @Transaction
    public WriteResult export(BM7501Model model, int userId) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (!model.isEditable()) return WriteResult.INVALID_STATE;

        Date exportedAt = new Date();
        model.setExportedAt(exportedAt);
        model.setExportedByUserId(userId);

        WriteResult saved = savePayload(model);
        if (saved != WriteResult.OK) return saved;

        if (dao.markExported(model.getUniqueId(), model.getLocalRevision(),
                exportedAt.getTime()) == 0) {
            return WriteResult.CONFLICT;
        }

        model.setLocalRevision(model.getLocalRevision() + 1);
        model.setBusinessStatus(BusinessStatus.EXPORTED);
        model.setSyncStatus(SyncStatus.PENDING);
        return WriteResult.OK;
    }

    /**
     * Trạng thái phiếu của nhiều mẻ hút, tra một lần cho cả danh sách.
     *
     * @return map theo {@code refuelItemUniqueId}; mẻ chưa có phiếu thì không có khoá
     */
    public java.util.Map<String, BM7501Model> getActiveByRefuelItems(java.util.List<String> refuelItemUniqueIds) {
        java.util.Map<String, BM7501Model> out = new java.util.HashMap<>();
        if (refuelItemUniqueIds == null || refuelItemUniqueIds.isEmpty()) return out;

        for (BM7501 entity : dao.getActiveByRefuelItems(refuelItemUniqueIds)) {
            if (entity == null || entity.getRefuelItemUniqueId() == null) continue;
            // Query sắp theo revisionNumber tăng dần nên bản sau ghi đè bản trước: lấy revision mới nhất.
            out.put(entity.getRefuelItemUniqueId(), entity.toModel());
        }
        return out;
    }

    /** Huỷ phiếu. Phiếu đã huỷ là phiếu duy nhất không sửa được nữa. */
    public WriteResult cancel(BM7501Model model, String reason) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (!model.isEditable()) return WriteResult.INVALID_STATE;

        model.setCancelReason(reason);
        model.setCancelledAt(new Date());
        savePayload(model);

        if (dao.markCancelled(model.getUniqueId(), System.currentTimeMillis()) == 0) {
            return WriteResult.CONFLICT;
        }
        model.setBusinessStatus(BusinessStatus.CANCELLED);
        return WriteResult.OK;
    }

    /**
     * Ghi nhận kết quả đồng bộ. CHỈ chạm danh tính phía server — không đụng nội dung,
     * không đụng trạng thái nghiệp vụ, vì bản cục bộ là bản đã ký và đã in ra giấy.
     */
    public WriteResult applySyncResult(String uniqueId, int serverId, String serverNumber) {
        if (uniqueId == null) return WriteResult.INVALID_STATE;
        int affected = dao.applySyncResult(uniqueId, serverId, serverNumber, SyncStatus.SYNCED.name());
        return affected == 0 ? WriteResult.CONFLICT : WriteResult.OK;
    }

    public void markSyncFailed(String uniqueId) {
        if (uniqueId == null) return;
        dao.updateSyncStatus(uniqueId, SyncStatus.FAILED.name());
    }
}
