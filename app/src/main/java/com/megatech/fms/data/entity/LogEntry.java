package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.LogEntryModel;

import java.util.Date;

@Entity
public class LogEntry extends BaseEntity{

    public LogEntry()
    {

        this.logTime = new Date();

        this.isLocalModified = true;
    }

    public static LogEntry fromModel(LogEntryModel model)
    {
        LogEntry entry = new LogEntry();
        entry.logTime = model.getLogTime();
        entry.jsonData = model.toJson();
        return  entry;
    }
    public LogEntryModel toModel()
    {
        LogEntryModel model = gson.fromJson(this.jsonData, LogEntryModel.class);
        model.setLocalId(this.localId);

        return model;
    }

    private Date logTime;


    public Date getLogTime() {
        return logTime;
    }

    public void setLogTime(Date logTime) {
        this.logTime = logTime;
    }


}
