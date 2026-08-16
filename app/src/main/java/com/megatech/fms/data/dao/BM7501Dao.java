package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.megatech.fms.data.entity.BM7501;

import java.util.List;

/**
 * DAO cho BM 75.01.
 *
 * <p>Cố ý KHÔNG có {@code @Update} tự do và KHÔNG có {@code onConflict = REPLACE}:
 * {@code REPLACE} sẽ âm thầm xoá phiếu đã ký khi có đua luồng giữa autosave và đồng bộ.
 * Mọi cập nhật đi qua các câu lệnh CAS bên dưới, và chỉ {@code BM7501Repository} được gọi.
 */
@Dao
public interface BM7501Dao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(BM7501 item);

    @Query("SELECT * FROM BM7501 WHERE uniqueId = :uniqueId")
    BM7501 getByUniqueId(String uniqueId);

    @Query("SELECT * FROM BM7501 WHERE localId = :localId")
    BM7501 getByLocalId(int localId);

    /** Phiếu đang hiệu lực của một mẻ hút: chưa bị huỷ và chưa bị vô hiệu hoá. */
    @Query("SELECT * FROM BM7501 WHERE refuelItemUniqueId = :refuelItemUniqueId "
            + "AND businessStatus NOT IN ('VOIDED','CANCELLED') "
            + "ORDER BY revisionNumber DESC LIMIT 1")
    BM7501 getActiveByRefuelItem(String refuelItemUniqueId);

    /**
     * Kiểm tra bất biến "mỗi mẻ chỉ một phiếu hiệu lực".
     * DB không ép được (Room không biểu diễn partial unique index) nên phải tự soi:
     * gọi khi mở phiếu và trước khi ký.
     */
    @Query("SELECT COUNT(*) FROM BM7501 WHERE refuelItemUniqueId = :refuelItemUniqueId "
            + "AND businessStatus NOT IN ('VOIDED','CANCELLED')")
    int countActiveByRefuelItem(String refuelItemUniqueId);

    @Query("SELECT * FROM BM7501 WHERE refuelItemUniqueId = :refuelItemUniqueId "
            + "ORDER BY revisionNumber DESC")
    List<BM7501> getAllRevisions(String refuelItemUniqueId);

    @Query("SELECT IFNULL(MAX(revisionNumber), 0) FROM BM7501 "
            + "WHERE refuelItemUniqueId = :refuelItemUniqueId")
    int getMaxRevision(String refuelItemUniqueId);

    /**
     * Ghi nội dung theo kiểu so-sánh-rồi-đổi: chỉ ghi khi phiên bản cục bộ đúng như đang cầm
     * VÀ phiếu còn ở nhóm sửa được. Trả về số dòng bị ảnh hưởng — 0 nghĩa là có xung đột,
     * tầng gọi phải đọc lại chứ không được ghi đè.
     */
    @Query("UPDATE BM7501 SET jsonData = :payload, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND localRevision = :expectedRevision "
            + "AND businessStatus IN ('DRAFT','A_DONE','B_DONE','C_DONE')")
    int updatePayloadIfUnchanged(String uniqueId, String payload, int expectedRevision, long now);

    /** Đổi trạng thái trong nhóm còn sửa được (xác nhận hoàn tất từng bước). */
    @Query("UPDATE BM7501 SET businessStatus = :newStatus, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND localRevision = :expectedRevision "
            + "AND businessStatus IN ('DRAFT','A_DONE','B_DONE','C_DONE')")
    int updateStatusIfEditable(String uniqueId, String newStatus, int expectedRevision, long now);

    /** Ký: chỉ đi được từ C_DONE, và ghi kèm băm bản đã ký. */
    @Query("UPDATE BM7501 SET businessStatus = 'SIGNED', "
            + "signedAt = :signedAt, "
            + "signedSnapshotHash = :snapshotHash, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :signedAt "
            + "WHERE uniqueId = :uniqueId AND businessStatus = 'C_DONE'")
    int markSigned(String uniqueId, String snapshotHash, long signedAt);

    /** In: đi từ SIGNED (bản gốc) hoặc PRINTED (bản sao, tăng số lần in). */
    @Query("UPDATE BM7501 SET businessStatus = 'PRINTED', "
            + "printedAt = :printedAt, "
            + "reprintCount = reprintCount + :increment, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :printedAt "
            + "WHERE uniqueId = :uniqueId AND businessStatus IN ('SIGNED','PRINTED')")
    int markPrinted(String uniqueId, int increment, long printedAt);

    /** Huỷ TRƯỚC khi ký. */
    @Query("UPDATE BM7501 SET businessStatus = 'CANCELLED', "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND businessStatus IN ('DRAFT','A_DONE','B_DONE','C_DONE')")
    int markCancelled(String uniqueId, long now);

    /** Vô hiệu hoá SAU khi đã ký/in. */
    @Query("UPDATE BM7501 SET businessStatus = 'VOIDED', "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId AND businessStatus IN ('SIGNED','PRINTED')")
    int markVoided(String uniqueId, long now);

    /**
     * Đồng bộ chỉ được chạm tới danh tính phía server. KHÔNG đụng {@code jsonData},
     * KHÔNG đụng {@code businessStatus} — bản cục bộ là bản đã ký, đã in.
     */
    @Query("UPDATE BM7501 SET id = :serverId, serverNumber = :serverNumber, "
            + "syncStatus = :syncStatus, isSynced = 1, isLocalModified = 0 "
            + "WHERE uniqueId = :uniqueId")
    int applySyncResult(String uniqueId, int serverId, String serverNumber, String syncStatus);

    @Query("UPDATE BM7501 SET syncStatus = :syncStatus WHERE uniqueId = :uniqueId")
    int updateSyncStatus(String uniqueId, String syncStatus);

    /** Phiếu chờ đẩy lên server — dùng cho outbox và cho cảnh báo "chưa đồng bộ". */
    @Query("SELECT * FROM BM7501 WHERE syncStatus <> 'SYNCED' "
            + "AND businessStatus IN ('SIGNED','PRINTED','VOIDED') "
            + "ORDER BY localId")
    List<BM7501> getPendingSync();

    @Query("SELECT COUNT(*) FROM BM7501 WHERE syncStatus <> 'SYNCED' "
            + "AND businessStatus IN ('SIGNED','PRINTED','VOIDED')")
    int countPendingSync();

    /** Chỉ dọn phiếu đã đồng bộ xong; phiếu chờ gửi giữ lại bất kể tuổi. */
    @Query("DELETE FROM BM7501 WHERE syncStatus = 'SYNCED' AND dateCreated < :cutoff")
    int deleteOlderThan(long cutoff);
}
