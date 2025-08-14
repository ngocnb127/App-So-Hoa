package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.Product;

import java.util.List;

@Dao
public interface ProductDao {

    @Query("SELECT * from Product ")
    List<Product> getAll();
    @Query("Select * from Product where  (id>0 and id = :id) or (id=0 and localId=:localId) ")
    Product get(int id, int localId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Product item);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Product item);


}
