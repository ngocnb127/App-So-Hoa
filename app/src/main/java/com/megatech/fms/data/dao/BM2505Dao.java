package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.TruckFuel;

import java.util.List;

@Dao
public interface BM2505Dao {

    @Query("Select * from BM2505")
    List<BM2505> getAll();

    @Query("Select *  from BM2505 where not isDeleted and (time between :start  and :end ) order by time desc" )
    List<BM2505> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2505 truck);

    @Query("Select * from BM2505 where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    BM2505 get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2505 item);

    @Query("DELETE from BM2505 WHERE id= :id")
    void delete(int id);

    @Query("Update BM2505 set isDeleted=1, isLocalModified=1 WHERE localId in( :ids)")
    void delete(int[] ids);

    @Query("SELECT * from BM2505 where isLocalModified")
    List<BM2505> getModified();

    @Query("SELECT * from BM2505Container ")
    List<BM2505Container> getContainers();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContainer(BM2505Container container);

    @Query("Select * from BM2505Container where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    BM2505Container getContainer(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateContainer(BM2505Container item);
}
