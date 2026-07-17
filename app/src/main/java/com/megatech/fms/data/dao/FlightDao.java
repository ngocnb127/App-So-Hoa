package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.data.entity.Shift;

import java.util.List;

@Dao
public interface FlightDao {

    @Query("Select * from Flight")
    List<Flight> getAll();
    @Query("Select * from Flight where refuelScheduledTime between :start and :end")
    List<Flight> getAll(long start, long end );

    @Query("Select * from Flight where (id >0 and id = :id) or (id =0 and localId= :localId)")
    Flight get(Integer id, Integer localId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Flight flight);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    int update(Flight flight);


}
