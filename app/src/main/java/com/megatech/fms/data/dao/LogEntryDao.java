package com.megatech.fms.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.megatech.fms.data.entity.LogEntry;

import java.util.List;

@Dao
public interface LogEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LogEntry entry);

    @Query("Select * from LogEntry order by logTime limit :limit")
    List<LogEntry> getList(int limit);


    @Query("Select * from LogEntry  where isLocalModified = 1 order by logTime limit :limit")
    List<LogEntry> getModified(int limit);


    @Query("Delete from LogEntry where isLocalModified = 0")
    void deleteSynced();

    @Query("Delete from LogEntry where localId In (:ids)")
    void deleteLogs(int[] ids);
}
