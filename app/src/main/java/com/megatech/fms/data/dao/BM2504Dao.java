package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2504;

import java.util.List;

@Dao
public interface BM2504Dao {

    /* =========================
       GET ALL
     ========================= */

    @Query("SELECT * FROM BM2504")
    List<BM2504> getAll();

    @Query("SELECT * FROM BM2504 WHERE NOT isDeleted AND (time BETWEEN :start AND :end) ORDER BY time DESC ")
    List<BM2504> getAll(long start, long end);

    /* =========================
       INSERT / UPDATE
     ========================= */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BM2504 item);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(BM2504 item);

    /* =========================
       GET ONE
     ========================= */

    @Query("SELECT * FROM BM2504 WHERE (id > 0 AND id = :id) OR (id = 0 AND localId = :localId) LIMIT 1 ")
    BM2504 get(int id, int localId);

    /* =========================
       DELETE
     ========================= */

    @Query("DELETE FROM BM2504 WHERE id = :id")
    void delete(int id);

    @Query(" UPDATE BM2504 SET isDeleted = 1, isLocalModified = 1 WHERE localId IN (:ids) ")
    void delete(int[] ids);

    /* =========================
       SYNC
     ========================= */

    @Query("SELECT * FROM BM2504 WHERE isLocalModified")
    List<BM2504> getModified();
}
