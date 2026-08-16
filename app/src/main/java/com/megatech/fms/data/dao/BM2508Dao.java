package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2508;

import java.util.List;

@Dao
public interface BM2508Dao {

    @Query("Select * from BM2508")
    List<BM2508> getAll();

    @Query("Select *  from BM2508 where isDeleted = 0 and (DateCreated between :start  and :end ) order by DateCreated desc" )
    List<BM2508> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2508 truck);

    @Query("Select * from BM2508 where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    BM2508 get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2508 item);

    @Query("SELECT * from BM2508 where isLocalModified")
    List<BM2508> getModified();

    @Query("SELECT * from BM2508 where isAttachmentPending = 1 and id > 0 and isDeleted = 0")
    List<BM2508> getPendingAttachments();

    @Query("DELETE from BM2508 WHERE id= :id")
    void delete(int id);

    @Query("Update BM2508 set isDeleted=1, isLocalModified=1 WHERE localId in( :ids)")
    void delete(int[] ids);


    /** Thêm điều kiện ảnh: phiếu còn ảnh chờ gửi thì giữ nguyên cả phiếu lẫn file. */
    @Query("DELETE FROM BM2508 WHERE NOT isLocalModified AND NOT isAttachmentPending "
            + "AND id > 0 AND DateCreated < :cutoff")
    int deleteOlderThan(long cutoff);
}
