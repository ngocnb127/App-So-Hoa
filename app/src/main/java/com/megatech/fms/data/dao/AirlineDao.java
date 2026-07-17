package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Airline;

import java.util.List;

@Dao
public interface AirlineDao {

    @Query("Select * from Airline")
    List<Airline> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Airline item);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Airline item);

    @Query("Select * from Airline where id = :id")
    Airline get(int id);

}
