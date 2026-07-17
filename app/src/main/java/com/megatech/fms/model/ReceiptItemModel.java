package com.megatech.fms.model;

import java.util.Date;

public class ReceiptItemModel {

    private int truckId;
    private String truckNo;

    private double startNumber;
    private double endNumber;

    private Date startTime;
    private Date endTime;

    private double temperature;
    private double density;

    private double gallon;
    private double volume;
    private double weight;

    private String qualityNo;

    private int refuelId;
    private String refuelItemId;


    private int driverId;
    private int operatorId;

    public int getRefuelId() {
        return refuelId;
    }

    public void setRefuelId(int refuelId) {
        this.refuelId = refuelId;
    }

    public String getRefuelItemId() {
        return refuelItemId;
    }

    public void setRefuelItemId(String refuelItemId) {
        this.refuelItemId = refuelItemId;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    public String getTruckNo() {
        return truckNo;
    }

    public void setTruckNo(String truckNo) {
        this.truckNo = truckNo;
    }

    public double getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(double startNumber) {
        this.startNumber = startNumber;
    }

    public double getEndNumber() {
        return endNumber;
    }

    public void setEndNumber(double endNumber) {
        this.endNumber = endNumber;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getDensity() {
        return density;
    }

    public void setDensity(double density) {
        this.density = density;
        this.weight = Math.round(this.volume * this.density);
    }

    public double getGallon() {
        return gallon;
    }

    public void setGallon(double gallon) {
        this.gallon = gallon;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.round(volume);
        this.weight = Math.round(this.volume * this.density);

    }

    public double getWeight() {
        return this.weight;
    }

    public void setWeight(double weight) {
        this.weight = Math.round(weight);
    }

    public String getQualityNo() {
        return qualityNo;
    }

    public void setQualityNo(String qualityNo) {
        this.qualityNo = qualityNo;
    }

    public int getDriverId() {
        return driverId;
    }

    public void setDriverId(int driverId) {
        this.driverId = driverId;
    }

    public int getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(int operatorId) {
        this.operatorId = operatorId;
    }
}
