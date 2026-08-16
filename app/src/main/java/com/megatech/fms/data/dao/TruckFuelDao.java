package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;


import com.megatech.fms.data.entity.TruckFuel;

import java.util.List;
@Dao
public interface TruckFuelDao {

    @Query("Select * from TruckFuel")
    List<TruckFuel> getAll();

    @Query("Select *  from TruckFuel where not isDeleted and (time between :start  and :end ) order by time desc" )
    List<TruckFuel> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TruckFuel truck);

    @Query("SELECT * FROM TruckFuel " +
            "WHERE (id > 0 AND id = :id) " +
            "OR (:localId > 0 AND localId = :localId) " +
            "LIMIT 1")
    TruckFuel get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(TruckFuel item);

    @Query("DELETE from TruckFuel WHERE id= :id")
    void delete(int id);

    @Query("Update TruckFuel set isDeleted=1, isLocalModified=1 WHERE localId in( :ids)")
    void delete(int[] ids);

    @Query("SELECT * from truckfuel where isLocalModified")
    List<TruckFuel> getModified();

    @Query("DELETE FROM TruckFuel WHERE NOT isLocalModified AND id > 0 AND time < :cutoff")
    int deleteOlderThan(long cutoff);
}
