package com.megatech.fms.model;

import com.megatech.fms.FMSApplication;

import java.util.Date;

public class LogEntryModel extends BaseModel{

    public LogEntryModel(LOG_TYPE logType, String activityName, String logText)
    {
        this.logText = logText;
        this.logTime = new Date();
        this.logType = logType;
        this.activityName = activityName;
        this.userId = FMSApplication.getApplication().getUser().getUserId();
        this.truckId = FMSApplication.getApplication().getTruckId();
        this.tabletId = FMSApplication.getApplication().getTabletId();
    }

    private String activityName;
    private Date logTime;
    private LOG_TYPE logType;
    private String logText;

    private int userId;
    private int truckId;

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public Date getLogTime() {
        return logTime;
    }

    public void setLogTime(Date logTime) {
        this.logTime = logTime;
    }

    public LOG_TYPE getLogType() {
        return logType;
    }

    public void setLogType(LOG_TYPE logType) {
        this.logType = logType;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    private String tabletId;

    public String getTabletId() {
        return tabletId;
    }

    public void setTabletId(String tabletId) {
        this.tabletId = tabletId;
    }

    public  enum LOG_TYPE
    {
        APP_LOG,
        USER_ACTION,
        DATA_CHANGED,
        LCR_LOG,
        ERROR_LOG
    }
}
