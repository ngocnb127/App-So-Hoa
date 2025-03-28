package com.megatech.fms.data.dao;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.megatech.fms.data.entity.ParkingLot;

import java.util.List;

@Dao
public interface ParkingLotDao {
    @Query("Select * from ParkingLot")
    List<ParkingLot> getAll();

    @Query("Select * from ParkingLot where airportId = :airportId")
    List<ParkingLot> getAll(int airportId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ParkingLot> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ParkingLot item);
}
