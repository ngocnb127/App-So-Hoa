package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;


import com.megatech.fms.data.entity.Review;

import java.util.List;

@Dao
public interface ReviewDao {

    @Query("Select * from Review where id = :id")
    Review get(int id);

    @Query("Select * from Review where uniqueId = :uniqueid")
    Review get(String uniqueid);

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    void insert(Review model);


    @Update (onConflict = OnConflictStrategy.REPLACE)
    void update(Review model);

    @Query("Select * from Review where flightId = :flightId order by dateUpdated desc limit 1")
    Review getByFlight(int flightId);

    @Query("Select * from Review where flightUniqueId = :flightId order by dateUpdated desc limit 1")
    Review getByFlight(String flightId);

    @Query("Select count(*) from Review where (flightId >0 AND flightId = :flightId)  OR flightUniqueId = :flightUniqueId ")
    boolean checkReview(int flightId,String flightUniqueId);

    @Query("Select * from Review where isLocalModified  = 1")
    List<Review> getModified();
}
