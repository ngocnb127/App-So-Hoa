package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Shift;

import java.util.List;

@Dao
public interface ShiftDao {

    @Query("Select * from Shift")
    List<Shift> getAll();


    @Query("Select * from Shift where id = :id")
    Shift get(Integer id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Shift shift);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    int update(Shift shift);

    @Query("DELETE from Shift WHERE id NOT IN(:shiftIds)")
    void deleteNotIds(int[] shiftIds);
}
