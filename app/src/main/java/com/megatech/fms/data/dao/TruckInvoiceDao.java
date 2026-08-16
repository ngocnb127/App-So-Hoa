package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.megatech.fms.data.entity.TruckInvoice;
import java.util.List;

@Dao
public interface TruckInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] insertOnly(List<TruckInvoice> invoices);

    @Query("SELECT * FROM TruckInvoice WHERE billDate >= :from AND billDate < :to ORDER BY billDate DESC")
    List<TruckInvoice> getBetween(long from, long to);

    @Query("DELETE FROM TruckInvoice WHERE billDate IS NULL OR billDate < :cutoff")
    int deleteOlderThan(long cutoff);
}
