package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2509;

import java.util.List;

@Dao
public interface BM2509Dao {

    @Query("Select * from BM2509 where not isDeleted and (time between :start and :end) order by time desc")
    List<BM2509> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2509 item);

    @Query("Select * from BM2509 where (id>0 and id = :id) or (id=0 and localId=:localId)")
    BM2509 get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2509 item);

    @Query("Update BM2509 set isDeleted=1, isLocalModified=1 WHERE localId in (:ids)")
    void delete(int[] ids);

    @Query("SELECT * from BM2509 where isLocalModified")
    List<BM2509> getModified();

    @Query("DELETE FROM BM2509 WHERE NOT isLocalModified AND id > 0 AND time < :cutoff")
    int deleteOlderThan(long cutoff);
}
