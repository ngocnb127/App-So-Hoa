package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2506;

import java.util.List;

@Dao
public interface BM2506Dao {

    @Query("Select * from BM2506 where not isDeleted and (time between :start and :end) order by time desc")
    List<BM2506> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2506 item);

    @Query("Select * from BM2506 where (id>0 and id = :id) or (id=0 and localId=:localId)")
    BM2506 get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2506 item);

    @Query("Update BM2506 set isDeleted=1, isLocalModified=1 WHERE localId in (:ids)")
    void delete(int[] ids);

    @Query("SELECT * from BM2506 where isLocalModified")
    List<BM2506> getModified();

    @Query("DELETE FROM BM2506 WHERE NOT isLocalModified AND id > 0 AND time < :cutoff")
    int deleteOlderThan(long cutoff);
}
