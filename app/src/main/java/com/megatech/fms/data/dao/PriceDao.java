package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.megatech.fms.data.entity.Price;

import java.util.List;

@Dao
public interface PriceDao {
    @Query("Select * from Price")
    List<Price> getAll();





}
