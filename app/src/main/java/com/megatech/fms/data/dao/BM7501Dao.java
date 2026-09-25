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
     * VÀ phiếu chưa bị huỷ/vô hiệu hoá. Trả về số dòng bị ảnh hưởng — 0 nghĩa là có xung đột,
     * tầng gọi phải đọc lại chứ không được ghi đè.
     *
     * <p>Phiếu đã in vẫn sửa được (chốt 2026-09-23): bản in không phải là chốt chặn, in lại
     * là chuyện bình thường ngoài hiện trường.
     */
    @Query("UPDATE BM7501 SET jsonData = :payload, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "isSynced = 0, "
            + "syncStatus = 'PENDING', "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND localRevision = :expectedRevision "
            + "AND businessStatus NOT IN ('CANCELLED','VOIDED','EXPORTED')")
    int updatePayloadIfUnchanged(String uniqueId, String payload, int expectedRevision, long now);

    /**
     * Điền số phiếu khi mẻ hút được cấp số muộn hơn lúc lập phiếu 75.01.
     *
     * <p>Chỉ ghi khi số hiện tại đang trống: số đã cấp thì không bao giờ được đổi.
     */
    @Query("UPDATE BM7501 SET localNumber = :localNumber, "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "isSynced = 0, "
            + "syncStatus = 'PENDING', "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND (localNumber IS NULL OR localNumber = '') "
            + "AND businessStatus NOT IN ('CANCELLED','VOIDED','EXPORTED')")
    int fillLocalNumberIfEmpty(String uniqueId, String localNumber, long now);

    /**
     * Xuất phiếu — chốt sổ. Sau lệnh này {@code updatePayloadIfUnchanged} không ăn nữa nên nội
     * dung khoá vĩnh viễn; phiếu vẫn in lại được vì in chỉ đọc.
     *
     * <p>Đặt lại {@code syncStatus = 'PENDING'} để trạng thái mới được đẩy lên server — server
     * dựa vào đó để chuyển phiếu sang Omega.
     */
    @Query("UPDATE BM7501 SET businessStatus = 'EXPORTED', "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "isSynced = 0, "
            + "syncStatus = 'PENDING', "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND localRevision = :expectedRevision "
            + "AND businessStatus NOT IN ('CANCELLED','VOIDED','EXPORTED')")
    int markExported(String uniqueId, int expectedRevision, long now);

    /** Trạng thái phiếu của nhiều mẻ hút cùng lúc — dùng cho danh sách mẻ hút. */
    @Query("SELECT * FROM BM7501 WHERE refuelItemUniqueId IN (:refuelItemUniqueIds) "
            + "AND businessStatus <> 'CANCELLED' "
            + "ORDER BY revisionNumber")
    List<BM7501> getActiveByRefuelItems(List<String> refuelItemUniqueIds);

    /** Huỷ phiếu. */
    @Query("UPDATE BM7501 SET businessStatus = 'CANCELLED', "
            + "localRevision = localRevision + 1, "
            + "isLocalModified = 1, "
            + "dateUpdated = :now "
            + "WHERE uniqueId = :uniqueId "
            + "AND businessStatus IN ('DRAFT','A_DONE','B_DONE','C_DONE')")
    int markCancelled(String uniqueId, long now);

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

    /**
     * Phiếu chờ đẩy lên server — dùng cho outbox và cho cảnh báo "chưa đồng bộ".
     *
     * <p>Bỏ qua {@code FAILED}: đó là phiếu bị server từ chối bằng 409/400, gửi lại bao nhiêu
     * lần cũng hỏng như nhau. Nhân viên sửa phiếu là nó quay về {@code PENDING} và được thử lại.
     */
    @Query("SELECT * FROM BM7501 WHERE syncStatus NOT IN ('SYNCED','FAILED') "
            + "AND businessStatus <> 'CANCELLED' "
            + "ORDER BY localId")
    List<BM7501> getPendingSync();

    @Query("SELECT COUNT(*) FROM BM7501 WHERE syncStatus NOT IN ('SYNCED','FAILED') "
            + "AND businessStatus <> 'CANCELLED'")
    int countPendingSync();

    /** Chỉ dọn phiếu đã đồng bộ xong; phiếu chờ gửi giữ lại bất kể tuổi. */
    @Query("DELETE FROM BM7501 WHERE syncStatus = 'SYNCED' AND dateCreated < :cutoff")
    int deleteOlderThan(long cutoff);
}
