package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.CheckTrucks;

import java.util.List;

@Dao
public interface CheckTrucksDao {

    @Query("Select * from CheckTrucks")
    List<CheckTrucks> getAll();

    @Query("Select *  from CheckTrucks where isDeleted = 0 and (DateCreated between :start  and :end ) order by DateCreated desc" )
    List<CheckTrucks> getAll(long start, long end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CheckTrucks truck);

    @Query("Select * from CheckTrucks where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    CheckTrucks get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(CheckTrucks item);

    @Query("SELECT * from CheckTrucks where isLocalModified")
    List<CheckTrucks> getModified();

    @Query("DELETE from CheckTrucks WHERE id= :id")
    void delete(int id);

    @Query("Update CheckTrucks set isDeleted=1, isLocalModified=1 WHERE localId in( :ids)")
    void delete(int[] ids);

}
