package com.megatech.fms.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

import com.megatech.fms.model.BM7501Model;

import java.util.Date;

/**
 * BM 75.01/NLHK — phiếu yêu cầu hút nhiên liệu.
 *
 * <p>Một mẻ hút có thể có nhiều <b>revision</b> chứng từ nhưng chỉ một revision đang hiệu lực.
 * Vì vậy {@code refuelItemUniqueId} KHÔNG unique một mình; cặp
 * ({@code refuelItemUniqueId}, {@code revisionNumber}) mới unique.
 *
 * <p>Trạng thái pháp lý nằm ở <b>cột phẳng</b> ({@code businessStatus}, {@code localNumber},
 * {@code revisionNumber}, audit timestamp), không nằm trong {@code jsonData}. {@code jsonData}
 * chỉ là snapshot nội dung nghiệp vụ mục A/B/C.
 *
 * <p>Xem docs/PLAN-BM7501-HUT-NHIEN-LIEU.md §2.
 */
@Entity(indices = {
        @Index(value = {"refuelItemUniqueId", "revisionNumber"}, unique = true),
        @Index(value = {"localNumber"}, unique = true),
        @Index(value = {"refuelItemUniqueId"})
})
public class BM7501 extends BaseEntity {

    /** Khóa của mẻ hút: {@code RefuelItem.uniqueId}. */
    private String refuelItemUniqueId;

    private int revisionNumber = 1;

    /** {@code uniqueId} của revision bị thay thế. */
    private String supersedesUniqueId;

    /** Số chứng từ pháp lý do app sinh, bất biến sau khi in. */
    private String localNumber;

    /** Số tham chiếu kỹ thuật do server cấp. */
    private String serverNumber;

    /** Bộ đếm CAS cho ghi cục bộ. */
    private int localRevision;

    /** Tên hằng của {@link BM7501Model.BusinessStatus}. */
    private String businessStatus;

    /** Tên hằng của {@link BM7501Model.SyncStatus}. */
    private String syncStatus;

    private int truckId;
    private int enteredByUserId;

    private Date dateCreated;
    private Date signedAt;
    private Date printedAt;
    private int reprintCount;

    private String signedSnapshotHash;

    public static BM7501 fromModel(BM7501Model model) {
        if (model == null) return null;

        BM7501 item = new BM7501();
        item.setId(model.getId());
        item.setLocalId(model.getLocalId());
        item.setUniqueId(model.getUniqueId());
        item.setJsonData(model.toJson());

        item.setRefuelItemUniqueId(model.getRefuelItemUniqueId());
        item.setRevisionNumber(model.getRevisionNumber());
        item.setSupersedesUniqueId(model.getSupersedesUniqueId());
        item.setLocalNumber(model.getLocalNumber());
        item.setServerNumber(model.getServerNumber());
        item.setLocalRevision(model.getLocalRevision());
        item.setBusinessStatus(model.getBusinessStatus() == null
                ? BM7501Model.BusinessStatus.DRAFT.name() : model.getBusinessStatus().name());
        item.setSyncStatus(model.getSyncStatus() == null
                ? BM7501Model.SyncStatus.NOT_READY.name() : model.getSyncStatus().name());
        item.setTruckId(model.getTruckId());
        item.setEnteredByUserId(model.getEnteredByUserId());
        item.setDateCreated(model.getDate());
        item.setSignedAt(model.getSignedAt());
        item.setPrintedAt(model.getPrintedAt());
        item.setReprintCount(model.getReprintCount());
        item.setSignedSnapshotHash(model.getSignedSnapshotHash());
        return item;
    }

    /**
     * Dựng lại model. Cột phẳng là nguồn chuẩn cho trạng thái pháp lý — ghi đè lên giá trị
     * có thể đã cũ trong {@code jsonData}.
     */
    public BM7501Model toModel() {
        BM7501Model model = getJsonData() == null
                ? new BM7501Model()
                : BM7501Model.fromJson(getJsonData());
        if (model == null) model = new BM7501Model();

        model.setId(getId());
        model.setLocalId(getLocalId());
        model.setUniqueId(getUniqueId());

        model.setRefuelItemUniqueId(getRefuelItemUniqueId());
        model.setRevisionNumber(getRevisionNumber());
        model.setSupersedesUniqueId(getSupersedesUniqueId());
        model.setLocalNumber(getLocalNumber());
        model.setServerNumber(getServerNumber());
        model.setLocalRevision(getLocalRevision());
        model.setBusinessStatus(parseBusinessStatus(getBusinessStatus()));
        model.setSyncStatus(parseSyncStatus(getSyncStatus()));
        model.setTruckId(getTruckId());
        model.setEnteredByUserId(getEnteredByUserId());
        model.setSignedAt(getSignedAt());
        model.setPrintedAt(getPrintedAt());
        model.setReprintCount(getReprintCount());
        model.setSignedSnapshotHash(getSignedSnapshotHash());
        return model;
    }

    private static BM7501Model.BusinessStatus parseBusinessStatus(String s) {
        if (s == null) return BM7501Model.BusinessStatus.DRAFT;
        try {
            return BM7501Model.BusinessStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            // Dữ liệu lạ từ bản cũ/bản sau: coi như bản nháp còn hơn mất phiếu.
            return BM7501Model.BusinessStatus.DRAFT;
        }
    }

    private static BM7501Model.SyncStatus parseSyncStatus(String s) {
        if (s == null) return BM7501Model.SyncStatus.NOT_READY;
        try {
            return BM7501Model.SyncStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return BM7501Model.SyncStatus.NOT_READY;
        }
    }

    public String getRefuelItemUniqueId() { return refuelItemUniqueId; }
    public void setRefuelItemUniqueId(String v) { this.refuelItemUniqueId = v; }

    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int v) { this.revisionNumber = v; }

    public String getSupersedesUniqueId() { return supersedesUniqueId; }
    public void setSupersedesUniqueId(String v) { this.supersedesUniqueId = v; }

    public String getLocalNumber() { return localNumber; }
    public void setLocalNumber(String v) { this.localNumber = v; }

    public String getServerNumber() { return serverNumber; }
    public void setServerNumber(String v) { this.serverNumber = v; }

    public int getLocalRevision() { return localRevision; }
    public void setLocalRevision(int v) { this.localRevision = v; }

    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String v) { this.businessStatus = v; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String v) { this.syncStatus = v; }

    public int getTruckId() { return truckId; }
    public void setTruckId(int v) { this.truckId = v; }

    public int getEnteredByUserId() { return enteredByUserId; }
    public void setEnteredByUserId(int v) { this.enteredByUserId = v; }

    public Date getDateCreated() { return dateCreated; }
    public void setDateCreated(Date v) { this.dateCreated = v; }

    public Date getSignedAt() { return signedAt; }
    public void setSignedAt(Date v) { this.signedAt = v; }

    public Date getPrintedAt() { return printedAt; }
    public void setPrintedAt(Date v) { this.printedAt = v; }

    public int getReprintCount() { return reprintCount; }
    public void setReprintCount(int v) { this.reprintCount = v; }

    public String getSignedSnapshotHash() { return signedSnapshotHash; }
    public void setSignedSnapshotHash(String v) { this.signedSnapshotHash = v; }
}
