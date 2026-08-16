package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.RefuelItemData;

import java.util.Date;
import java.util.List;

@Dao
public interface RefuelItemDao {

    @Query("Select * from RefuelItem")
    List<RefuelItem> getAll();

    @Query("Select * from RefuelItem where id = :id")
    RefuelItem get(int id);

    @Query("Select * from RefuelItem where uniqueId = :uniqueId")
    RefuelItem get(String uniqueId);

    @Query("Select * from RefuelItem where localId != :id AND flightId = (SELECT flightId from RefuelItem where localId= :id)")
    List<RefuelItem> getOthers(int id);

    @Query("Select * from RefuelItem where flightId = :id")
    List<RefuelItem> getByFlightId(int id);

    @Query("Select * from RefuelItem where truckNo = :truckNo and refuelTime between :start and :end order by refuelTime")
    List<RefuelItem> getByTruckNo(String truckNo, long start, long end);

    @Query("Select * from RefuelItem where truckNo = :truckNo and refuelTime between :start and :end and refuelItemType = :type order by refuelTime")
    List<RefuelItem> getByTruckNo(String truckNo, long start, long end, int type);

    @Query("Select * from RefuelItem where truckNo = :truckNo order by refuelTime")
    List<RefuelItem> getByTruckNo(String truckNo);


    @Query("Select * from RefuelItem where truckNo != :truckNo and refuelTime between :start and :end order by refuelTime")
    List<RefuelItem> getOthers(String truckNo, long start, long end);

    @Query("Select * from RefuelItem where truckNo != :truckNo and refuelTime between :start and :end and refuelItemType = :type order by refuelTime")
    List<RefuelItem> getOthers(String truckNo, long start, long end, int type);

    @Query("Select * from RefuelItem where truckNo != :truckNo order by refuelTime")
    List<RefuelItem> getOthers(String truckNo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RefuelItem> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RefuelItem item);

    @Delete
    void delete(RefuelItem item);

    @Update
    void update(RefuelItem item);

    @Query("Select * from RefuelItem where localId = :id")
    RefuelItem getLocal(Integer id);

    @Query("Select id from refuelitem where isSynced")
    int[] getNotChanges();

    @Query("Delete from RefuelItem where id > 0 and id in (:ids) and NOT isLocalModified")
    void removeDeleted(int[] ids);

    @Query("Select * from RefuelItem where isLocalModified OR id = 0")
    List<RefuelItem> getModified();

    /**
     * Hàng đợi đồng bộ tự động: bỏ qua các row đang giữ conflict (postStatus = ERROR = 2).
     * Dữ liệu local của chúng vẫn nguyên vẹn và vẫn isLocalModified; chúng chỉ quay lại
     * hàng đợi khi người dùng sửa/lưu lại (xem DataHelper.resumeSync).
     */
    @Query("Select * from RefuelItem where (isLocalModified OR id = 0) AND postStatus <> 2")
    List<RefuelItem> getModifiedForSync();

    @Query("Select Max(dateUpdated) from RefuelItem ")
    Date getLastModifiedDate();

    @Query("delete from RefuelItem where NOT isLocalModified and refuelTime < :d")

    void deleteByDate(long d);

    @Query("Select * from RefuelItem where status = 1 and  truckNo = :truckNo and startTime > :timeLimit limit 1")
    RefuelItem getIncomplete(String truckNo, long timeLimit);

    @Query("Select * from RefuelItem where uniqueId != :uniqueId AND flightId = (SELECT flightId from RefuelItem where uniqueId= :uniqueId)")
    List<RefuelItem> getOtherItems(String uniqueId);

    @Query("Select * from RefuelItem where flightId = :flightId  AND truckId  = :truckId and status !=3")
    RefuelItem getByFlightAndTruck(Integer flightId, int truckId);

    @Query("SELECT * FROM RefuelItem WHERE jsonData LIKE '%\"Density\":%' ORDER BY EndTime DESC LIMIT 1")
    RefuelItem getLatestByEndTime();

    @Query(
            "SELECT jsonData " +
                    "FROM RefuelItem " +
                    "WHERE jsonData LIKE '%\"Density\"%' " +
                    "ORDER BY EndTime DESC " +
                    "LIMIT 1"
    )
    String getLatestRefuelItemJson();





    /**
     * Dọn theo hạn lưu. Không đụng bản ghi còn thay đổi chưa gửi hoặc chưa có id server:
     * đó là dữ liệu chỉ tồn tại trên máy này.
     */
    @Query("DELETE FROM RefuelItem WHERE NOT isLocalModified AND id > 0 AND refuelTime < :cutoff")
    int deleteOlderThan(long cutoff);

    /**
     * Đưa các row đang giữ conflict trở lại hàng đợi đồng bộ.
     *
     * <p>{@code postStatus = 2} (ERROR) loại row khỏi {@link #getModifiedForSync()} cho tới khi
     * người dùng mở ra sửa lại. Luật ACK cũ gắn cờ đó cho gần như mọi phiếu đã POST, nên sau khi
     * sửa luật phải có một lần dọn — nếu không, phiếu cũ vẫn phải gõ tay dù bản vá đã cài.
     *
     * @return số row được đưa trở lại hàng đợi
     */
    @Query("UPDATE RefuelItem SET postStatus = 0 WHERE postStatus = 2")
    int resumeConflictedRows();
}
