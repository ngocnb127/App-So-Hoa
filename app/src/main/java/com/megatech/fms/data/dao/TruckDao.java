package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.Truck;

import java.util.List;
import java.util.Set;

@Dao
public interface TruckDao {

    @Query("Select * from truck")
    List<Truck> getAll();

    @Query("Select * from truck where isFHS")
    List<Truck> getFHS();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Truck truck);

    @Query("Select * from truck where id=:id")
    Truck get(int id);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Truck item);

    @Query("DELETE from Truck WHERE id NOT IN(:truckIds)")
    void deleteNotIds(int[] truckIds);
}
