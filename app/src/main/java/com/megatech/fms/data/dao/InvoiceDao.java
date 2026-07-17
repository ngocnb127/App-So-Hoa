package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Invoice;

import java.util.List;

@Dao
public interface InvoiceDao {
    @Query("Select * from Invoice")
    List<Invoice> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Invoice truck);

    @Query("Select * from Invoice where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    Invoice get(int id, int localId);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Invoice item);

    @Query("DELETE from Invoice WHERE id= :id")
    void delete(int id);

    @Query("Update Invoice set isDeleted=1, isLocalModified=1 WHERE id in( :ids)")
    void delete(int[] ids);

    @Query("SELECT * from Invoice where isLocalModified")
    List<Invoice> getModified();

    @Query("Select *  from Invoice where not isDeleted and (date between :start  and :end ) order by date desc" )
    List<Invoice> getAll(long start, long end);
}
