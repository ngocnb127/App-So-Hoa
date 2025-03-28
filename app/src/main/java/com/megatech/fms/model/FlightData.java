package com.megatech.fms.model;


import java.util.Date;

public class FlightData {
    private int id;
    private String  code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    private String aircraftType;
    private String aircraftCode;
    private String parkingLot;
    private Date refuelTime;
    private double estimateAmount;

    public double getEstimateAmount() {
        return estimateAmount;
    }

    public void setEstimateAmount(double estimateAmount) {
        estimateAmount = estimateAmount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getParkingLot() {
        return parkingLot;
    }

    public void setParkingLot(String parkingLot) {
        this.parkingLot = parkingLot;
    }

    public Date getRefuelTime() {
        return refuelTime;
    }

    public void setRefuelTime(Date refuelTime) {
        this.refuelTime = refuelTime;
    }
}
