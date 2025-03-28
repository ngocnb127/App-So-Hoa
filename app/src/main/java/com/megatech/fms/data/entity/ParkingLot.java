package com.megatech.fms.data.entity;

import androidx.room.Entity;

@Entity
public class ParkingLot extends BaseEntity {

    private String code;
    private String name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
