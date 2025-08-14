package com.megatech.fms.model;

import android.database.DatabaseErrorHandler;

import com.megatech.fms.FMSApplication;

import java.util.Date;

public class TruckFuelModel extends BaseModel {

    private int truckId;
    private String truckNo;
    private String unit;

    private String ticketNo;
    private String tankNo;
    private String maintenanceStaff;
    private String qcNo;
    private Date time;
    private Date startTime;
    private Date endTime;
    private int operatorId;
    private String operatorName;
    private double amount; //litter
    private double AccumulateRefuelAmount;//litter
    private double truckCapacity;

    private Date testStartTime;
    private Date testEndTime;
    private boolean testResult = true;
    private boolean FullVolumn = true;

    private String appearanceCheck = "C&B";
    private Boolean waterCheck = null;

    private double flowRate = 0;
    public TruckFuelModel() {
        TruckModel setting = FMSApplication.getApplication().getSetting();
        this.truckId = setting.getTruckId();
        this.truckNo = setting.getTruckNo();
        this.qcNo = FMSApplication.getApplication().getQCNo();
        this.startTime = this.endTime=this.testStartTime= this.testEndTime  = this.time = new Date();


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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getTankNo() {
        return tankNo;
    }

    public void setTankNo(String tankNo) {
        this.tankNo = tankNo;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getMaintenanceStaff() {
        return maintenanceStaff;
    }

    public void setMaintenanceStaff(String maintenanceStaff) {
        this.maintenanceStaff = maintenanceStaff;
    }

    public String getQcNo() {
        return qcNo;
    }

    public void setQcNo(String qcNo) {
        this.qcNo = qcNo;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public int getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(int operatorId) {
        this.operatorId = operatorId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getAccumulateRefuelAmount() {
        return AccumulateRefuelAmount;
    }

    public void setAccumulateRefuelAmount(double AccumulateRefuelAmount) {
        this.AccumulateRefuelAmount = AccumulateRefuelAmount;
    }

    public double getTruckCapacity() {
        return truckCapacity;
    }

    public void setTruckCapacity(double truckCapacity) {
        this.truckCapacity = truckCapacity;
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

    public Date getTestStartTime() {
        return testStartTime;
    }

    public void setTestStartTime(Date testStartTime) {
        this.testStartTime = testStartTime;
    }

    public Date getTestEndTime() {
        return testEndTime;
    }

    public void setTestEndTime(Date testEndTime) {
        this.testEndTime = testEndTime;
    }

    public boolean isTestResult() {
        return testResult;
    }

    public void setTestResult(boolean testResult) {
        this.testResult = testResult;
    }

    public double getFlowRate() {
        return flowRate;
    }

    public void setFlowRate(double flowRate) {
        this.flowRate = flowRate;
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

    public Boolean getFullVolumn() {
        return FullVolumn;
    }

    public void setFullVolumn(Boolean FullVolumn) {
        this.FullVolumn = FullVolumn;
    }


}
