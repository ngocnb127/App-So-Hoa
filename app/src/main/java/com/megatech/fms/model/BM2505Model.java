package com.megatech.fms.model;

import androidx.databinding.InverseMethod;

import java.util.Date;

public class BM2505Model extends BaseModel {

    private int reportType = 0;

    private int containerId;


    private String containerName;

    private String depot;

    private double maxFlowRate;

    public int getReportType() {
        return reportType;
    }

    public void setReportType(int reportType) {
        this.reportType = reportType;
    }

    public int getContainerId() {
        return containerId;
    }

    public void setContainerId(int containerId) {
        this.containerId = containerId;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getDepot() {
        return depot;
    }

    public void setDepot(String depot) {
        this.depot = depot;
    }

    private Date time;
    private int truckId;
    private int flightId;
    public String flightCode;
    public String aircraftCode;
    private String tankNo;
    private String rTCNo;
    private double temperature;
    private double density;
    private double density15;
    private  double densityDiff;
    private boolean densityCheck = true;
    private String appearanceCheck = "C&B";
    private Boolean waterCheck = null;
    private String pressureDiff;
    private String HosePressure;
    private int operatorId;
    private String operatorName;

    private String note;


    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    public String getAircraftCode() {
        return aircraftCode;
    }

    public void setAircraftCode(String airCraftCode) {
        this.aircraftCode = airCraftCode;
    }

    public String getTankNo() {
        return tankNo;
    }

    public void setTankNo(String tankNo) {
        this.tankNo = tankNo;
    }

    public String getRTCNo() {
        return rTCNo;
    }

    public void setRTCNo(String rTCNo) {
        this.rTCNo = rTCNo;
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

    public double getDensity15() {
        return density15;
    }

    public void setDensity15(double density15) {
        this.density15 = density15;
    }

    public double getDensityDiff() {
        return densityDiff;
    }

    public void setDensityDiff(double densityDiff) {
        this.densityDiff = densityDiff;
    }

    public boolean isDensityCheck() {
        return densityCheck;
    }

    public void setDensityCheck(boolean densityCheck) {
        this.densityCheck = densityCheck;
    }

    public String getAppearanceCheck() {
        return appearanceCheck;
    }

    public void setAppearanceCheck(String appearanceCheck) {
        this.appearanceCheck = appearanceCheck;
    }
    public Boolean isWaterCheck() {
        return waterCheck;
    }

    public void setWaterCheck(Boolean waterCheck) {
        this.waterCheck = waterCheck;
    }

    public String getPressureDiff() {
        return pressureDiff;
    }

    public void setPressureDiff(String pressureDiff) {
        this.pressureDiff = pressureDiff;
    }

    public String getHosePressure() {
        return HosePressure;
    }

    public void setHosePressure(String hosePressure) {
        HosePressure = hosePressure;
    }

    public int getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(int operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public double getMaxFlowRate() {
        return maxFlowRate;
    }

    public void setMaxFlowRate(double maxFlowRate) {
        this.maxFlowRate = maxFlowRate;
    }
}
