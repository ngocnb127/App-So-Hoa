package com.megatech.fms.data;

import androidx.room.Transaction;

import com.megatech.fms.data.dao.BM7501Dao;
import com.megatech.fms.data.entity.BM7501;
import com.megatech.fms.helpers.BM7501Canonical;
import com.megatech.fms.helpers.BM7501State;
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
     * được partial unique index), nên phải tự kiểm tra khi mở phiếu và trước khi ký.
     *
     * @return true nếu dữ liệu bất thường — tầng gọi phải KHOÁ ký/in và ghi log,
     *         tuyệt đối không tự chọn bừa một revision.
     */
    public boolean hasMultipleActive(String refuelItemUniqueId) {
        if (refuelItemUniqueId == null) return false;
        return dao.countActiveByRefuelItem(refuelItemUniqueId) > 1;
    }

    public int countPendingSync() {
        return dao.countPendingSync();
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

    /**
     * Tạo revision thay thế cho phiếu đã ký/in: vô hiệu hoá bản cũ rồi chèn bản mới,
     * trong cùng một giao dịch để không bao giờ tồn tại hai bản hiệu lực.
     */
    @Transaction
    public BM7501Model supersede(BM7501Model current, BM7501Model replacement) {
        if (current == null || replacement == null) {
            throw new IllegalArgumentException("Thiếu phiếu cũ hoặc phiếu thay thế");
        }
        BusinessStatus status = current.getBusinessStatus();
        if (status != BusinessStatus.SIGNED && status != BusinessStatus.PRINTED) {
            throw new IllegalStateException(
                    "Chỉ phiếu đã ký/in mới được thay thế, đang ở " + status);
        }

        if (dao.markVoided(current.getUniqueId(), System.currentTimeMillis()) == 0) {
            throw new IllegalStateException("Không vô hiệu hoá được phiếu cũ");
        }

        replacement.setRefuelItemUniqueId(current.getRefuelItemUniqueId());
        replacement.setSupersedesUniqueId(current.getUniqueId());
        replacement.setRevisionNumber(dao.getMaxRevision(current.getRefuelItemUniqueId()) + 1);
        replacement.setBusinessStatus(BusinessStatus.DRAFT);
        replacement.setSyncStatus(SyncStatus.NOT_READY);
        replacement.setLocalRevision(0);

        BM7501 entity = BM7501.fromModel(replacement);
        entity.setLocalModified(true);
        entity.setSynced(false);
        entity.setDateUpdated(new Date());

        replacement.setLocalId((int) dao.insert(entity));
        return replacement;
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

    /** Xác nhận hoàn tất một bước (A/B/C). Chỉ gọi khi validation của bước đó đã đạt. */
    public WriteResult advanceStep(BM7501Model model, BusinessStatus target) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (!BM7501State.canTransition(model.getBusinessStatus(), target)) {
            return WriteResult.INVALID_STATE;
        }
        if (!target.isEditable()) return WriteResult.INVALID_STATE;

        int affected = dao.updateStatusIfEditable(
                model.getUniqueId(), target.name(),
                model.getLocalRevision(), System.currentTimeMillis());

        if (affected == 0) return WriteResult.CONFLICT;

        model.setLocalRevision(model.getLocalRevision() + 1);
        model.setBusinessStatus(target);
        return WriteResult.OK;
    }

    /**
     * Ký phiếu: đóng băng nội dung và ghi băm bản đã ký.
     *
     * <p>Chạy trong một giao dịch cùng lần ghi nội dung cuối, để không có khe hở giữa
     * "lưu nội dung" và "đóng dấu đã ký".
     */
    @Transaction
    public WriteResult sign(BM7501Model model) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (model.getBusinessStatus() != BusinessStatus.C_DONE) return WriteResult.INVALID_STATE;
        if (hasMultipleActive(model.getRefuelItemUniqueId())) return WriteResult.INVALID_STATE;

        // Lưu nốt nội dung trước khi đóng băng.
        WriteResult saved = savePayload(model);
        if (saved == WriteResult.CONFLICT) return WriteResult.CONFLICT;

        Date signedAt = new Date();
        String payload = model.toJson();
        String manifest = BM7501Canonical.manifest(
                model.getSchemaVersion(),
                model.getUniqueId(),
                model.getRefuelItemUniqueId(),
                model.getRevisionNumber(),
                model.getLocalNumber(),
                BM7501Canonical.sha256OfString(payload),
                model.getCustomerSectionASignatureSha256(),
                model.getCustomerFinalSignatureSha256(),
                model.getSkypecSignatureSha256(),
                signedAt.getTime());
        String hash = BM7501Canonical.hashManifest(manifest);

        if (dao.markSigned(model.getUniqueId(), hash, signedAt.getTime()) == 0) {
            return WriteResult.CONFLICT;
        }

        model.setSignedAt(signedAt);
        model.setSignedSnapshotHash(hash);
        model.setBusinessStatus(BusinessStatus.SIGNED);
        model.setSyncStatus(SyncStatus.PENDING);
        dao.updateSyncStatus(model.getUniqueId(), SyncStatus.PENDING.name());
        return WriteResult.OK;
    }

    /**
     * Đánh dấu đã in.
     *
     * @param isReprint true nếu là bản sao — tăng {@code reprintCount} để bản in sau đóng
     *                  dấu BẢN SAO và không nhân bản bản gốc không kiểm soát.
     */
    public WriteResult markPrinted(BM7501Model model, boolean isReprint) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;

        BusinessStatus status = model.getBusinessStatus();
        if (status != BusinessStatus.SIGNED && status != BusinessStatus.PRINTED) {
            return WriteResult.INVALID_STATE;
        }

        Date now = new Date();
        if (dao.markPrinted(model.getUniqueId(), isReprint ? 1 : 0, now.getTime()) == 0) {
            return WriteResult.CONFLICT;
        }

        model.setPrintedAt(now);
        model.setBusinessStatus(BusinessStatus.PRINTED);
        if (isReprint) model.setReprintCount(model.getReprintCount() + 1);
        return WriteResult.OK;
    }

    /** Huỷ trước khi ký. */
    public WriteResult cancel(BM7501Model model, String reason) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (!BM7501State.canTransition(model.getBusinessStatus(), BusinessStatus.CANCELLED)) {
            return WriteResult.INVALID_STATE;
        }

        model.setCancelReason(reason);
        model.setCancelledAt(new Date());
        savePayload(model);

        if (dao.markCancelled(model.getUniqueId(), System.currentTimeMillis()) == 0) {
            return WriteResult.CONFLICT;
        }
        model.setBusinessStatus(BusinessStatus.CANCELLED);
        return WriteResult.OK;
    }

    /** Vô hiệu hoá sau khi đã ký/in. Bắt buộc có lý do và người thực hiện. */
    @Transaction
    public WriteResult voidDocument(BM7501Model model, String reason, int userId) {
        if (model == null || model.getUniqueId() == null) return WriteResult.INVALID_STATE;
        if (reason == null || reason.trim().isEmpty()) return WriteResult.INVALID_STATE;
        if (!BM7501State.canTransition(model.getBusinessStatus(), BusinessStatus.VOIDED)) {
            return WriteResult.INVALID_STATE;
        }

        if (dao.markVoided(model.getUniqueId(), System.currentTimeMillis()) == 0) {
            return WriteResult.CONFLICT;
        }

        model.setVoidReason(reason);
        model.setVoidedByUserId(userId);
        model.setVoidedAt(new Date());
        model.setBusinessStatus(BusinessStatus.VOIDED);
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
