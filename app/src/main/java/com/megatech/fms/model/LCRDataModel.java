package com.megatech.fms.model;

import android.util.Log;

import java.util.Date;

public class LCRDataModel {

    private final boolean volFlag = false;
    private final boolean totalFlag = false;
    private int flightId;

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    private int userId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    private double grossQty;


    public double getGrossQty() {

        return grossQty;
    }
    public void setGrossQty(double qty) {
        this.grossQty = qty;

        //if ( !(volFlag )){
        Log.e("FMS", endMeterNumber + " - " + this.grossQty);
            this.setStartMeterNumber(this.endMeterNumber - this.grossQty);
        //}

        //volFlag = true;
    }
    private double netQty;

    public double getNetQty() {
        return netQty;
    }

    public void setNetQty(double netQty) {
        this.netQty = netQty;
    }

    private double temperature;

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    private Date startTime;

    private Date endTime;

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

    private double meterNumber;
    private  double startMeterNumber;
    private  double endMeterNumber;

    public double getMeterNumber() {
        return meterNumber;
    }

    public void setMeterNumber(double meterNumber) {
        this.meterNumber = meterNumber;
    }

    public double getStartMeterNumber() {
        return startMeterNumber;
    }

    public void setStartMeterNumber(double startMeterNumber) {
        this.startMeterNumber = startMeterNumber;
    }

    public double getEndMeterNumber() {
        return endMeterNumber;
    }

    public void setEndMeterNumber(double endMeterNumber) {
        this.endMeterNumber = endMeterNumber;
        //if ( !(totalFlag)){
        Log.e("FMS", endMeterNumber + " - " + this.grossQty);
            this.setStartMeterNumber(this.endMeterNumber - this.grossQty);
        //}

        //totalFlag = true;
    }

    private String serialId;

    public String getSerialId() {
        return serialId;
    }

    public void setSerialId(String serialId) {
        this.serialId = serialId;
    }

    private double analogPortValueLive;

    public double getAnalogPortValueLive() {
        return analogPortValueLive;
    }

    public void setAnalogPortValueLive(double doubleValue) {
        analogPortValueLive = doubleValue;
    }
}
