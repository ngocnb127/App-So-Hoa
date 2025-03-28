package com.megatech.fms.model;

import androidx.annotation.NonNull;

public class FlightModel extends BaseModel {
    private String code;
    private String aircraftCode;
    private String aircraftType;

    public String getFlightCode() {
        return code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setFlightCode(String flightCode) {
        this.code = flightCode;
    }

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

    @NonNull
    @Override
    public String toString() {
        return code;
    }
}
