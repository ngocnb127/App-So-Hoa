package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2505;


import java.util.Date;
import java.util.List;

@Dao
public interface BM2503Dao {



    @Query("Select * from BM2503")
    List<BM2503> getAll();

    @Query("Select *  from BM2503 where not isDeleted and (time between :start  and :end ) order by time desc" )
    List<BM2503> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2503 truck);

    @Query("Select * from BM2503 where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    BM2503 get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2503 item);

    @Query("DELETE from BM2503 WHERE id= :id")
    void delete(int id);

    @Query("Update BM2503 set isDeleted=1, isLocalModified=1 WHERE localId in( :ids)")
    void delete(int[] ids);

    @Query("SELECT * from BM2503 where isLocalModified")
    List<BM2503> getModified();


}
