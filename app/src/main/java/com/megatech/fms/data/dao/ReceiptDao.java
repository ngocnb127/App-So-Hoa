package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity. Receipt;

import java.util.List;

@Dao
public interface ReceiptDao {
    @Query("Select * from  Receipt")
    List< Receipt> getAll();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert( Receipt truck);

    @Query("Select * from  Receipt where number = :number ")
     Receipt get(String number);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    int update( Receipt item);

    @Query("DELETE from  Receipt WHERE id= :id")
    void delete(int id);

    @Query("Update  Receipt set isDeleted=1, isLocalModified=1 WHERE id in( :ids)")
    void delete(int[] ids);

    @Query("SELECT * from  Receipt where isLocalModified")
    List< Receipt> getModified();

    @Query("Select *  from  Receipt where not isDeleted and (date between :start  and :end ) order by date desc" )
    List< Receipt> getAll(long start, long end);

    @Query("Update Receipt set isCancelled = 1, cancelReason = :reason, isLocalModified = 1 where number in (:printedItems) ")
    void cancel(String[] printedItems, String reason);

    @Query("Select * from receipt where uniqueId  = :uniqueId ")
    Receipt getByUniqueId(String uniqueId);

}
