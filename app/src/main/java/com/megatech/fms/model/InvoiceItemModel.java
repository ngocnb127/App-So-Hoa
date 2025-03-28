package com.megatech.fms.model;

import static com.megatech.fms.model.RefuelItemData.GALLON_TO_LITTER;

import java.util.Date;

public class InvoiceItemModel {
    private int refuelItemId;
    private String refuelUniqueId;
    private int truckId;
    private String truckNo;
    private double startNumber;
    private double endNumber;
    private double realAmount;
    private double gallon;
    private double volume;
    private double weight;
    private double temperature;
    private double density;
    private int driverId;
    private int operatorId;
    private Date startTime;
    private Date endTime;
    private String qualityNo;

    public int getRefuelItemId() {
        return refuelItemId;
    }

    public void setRefuelItemId(int refuelItemId) {
        this.refuelItemId = refuelItemId;
    }

    public String getRefuelUniqueId() {
        return refuelUniqueId;
    }

    public void setRefuelUniqueId(String refuelUniqueId) {
        this.refuelUniqueId = refuelUniqueId;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    private boolean isReturn;

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

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
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


    public double getRealAmount() {
        return realAmount;
    }

    public void setRealAmount(double realAmount) {
        this.realAmount = realAmount;
        this.gallon = realAmount;
    }

    public double getGallon() {
        return gallon;
    }

    public void setGallon(double gallon) {
        this.gallon = gallon;
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
    }

    public boolean isReturn() {
        return isReturn;
    }

    public void setReturn(boolean aReturn) {
        isReturn = aReturn;
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

    public String getQualityNo() {
        return qualityNo;
    }

    public void setQualityNo(String qualityNo) {
        this.qualityNo = qualityNo;
    }
}
