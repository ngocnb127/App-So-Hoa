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

    /** Trả rowId Room vừa sinh để lớp gọi ghi ngược localId vào object (chống đẻ hàng thứ hai). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Invoice truck);

    @Query("Select * from Invoice where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    Invoice get(int id, int localId);

    /** Tra theo rowId Room — đường chắc chắn nhất khi object đã biết localId của mình. */
    @Query("Select * from Invoice where localId = :localId")
    Invoice getByLocalId(int localId);

    /**
     * Tra theo uniqueId (định danh HÀNG trong Room). Điều kiện NOT NULL/rỗng là bắt buộc:
     * nếu thiếu, mọi hàng cũ có uniqueId rỗng sẽ gom về một nhóm và bị coi là cùng một hoá đơn.
     */
    @Query("Select * from Invoice where uniqueId IS NOT NULL AND uniqueId <> '' AND uniqueId = :uniqueId LIMIT 1")
    Invoice getByUniqueId(String uniqueId);

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

    @Query("DELETE FROM Invoice WHERE NOT isLocalModified AND id > 0 AND date < :cutoff")
    int deleteOlderThan(long cutoff);
}
