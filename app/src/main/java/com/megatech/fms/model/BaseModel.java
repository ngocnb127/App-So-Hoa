package com.megatech.fms.model;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.FMSApplication;

import java.util.Date;
import java.util.UUID;

public class BaseModel {

    protected int id;

    public Integer getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    protected static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    public int getLocalId() {
        return localId;
    }

    public void setLocalId(int localId) {
        this.localId = localId;
    }

    protected  boolean isLocalModified;

    public boolean isLocalModified() {
        return isLocalModified;
    }

    public void setLocalModified(boolean localModified) {
        isLocalModified = localModified;
    }

    protected boolean isDeleted;

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    protected int localId = 0;
    protected Date dateUpdated;

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    protected String tabletSerial;

    public String getTabletSerial() {


        return tabletSerial;
    }

    public void setTabletSerial(String tabletSerial) {
        this.tabletSerial = tabletSerial;
    }

    public String toJson() {
        return gson.toJson(this);
    }


    protected String uniqueId = UUID.randomUUID().toString();

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }
}
