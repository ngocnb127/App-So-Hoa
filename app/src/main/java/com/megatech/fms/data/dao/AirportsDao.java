package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;

import java.util.List;

@Dao
public interface AirportsDao {

    @Query("Select * from Airports")
    List<Airports> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Airports item);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Airports item);

    @Query("Select * from Airports where id = :id")
    Airports get(int id);

    @Query("DELETE from Airports WHERE id NOT IN(:airportsIds)")
    void deleteNotIds(int[] airportsIds);

}
