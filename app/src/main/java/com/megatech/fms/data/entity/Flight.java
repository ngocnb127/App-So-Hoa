package com.megatech.fms.data.entity;

import androidx.room.Embedded;
import androidx.room.Entity;

import com.megatech.fms.model.FlightModel;

import java.util.Date;
import java.util.UUID;

@Entity
public class Flight extends BaseEntity {
    private String code;
    private int airportId;

    public int getAirportId() {
        return airportId;
    }

    public void setAirportId(int airportId) {
        this.airportId = airportId;
    }


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    private String aircraftCode;
    private String aircraftType;

    private Date refuelScheduledTime;



    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public Date getRefuelScheduledTime() {
        return refuelScheduledTime;
    }

    public void setRefuelScheduledTime(Date refuelScheduledTime) {
        this.refuelScheduledTime = refuelScheduledTime;
    }

    public FlightModel toModel()
    {
        FlightModel model = gson.fromJson(this.toJson(), FlightModel.class);
        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());
        return model;
    }
}
