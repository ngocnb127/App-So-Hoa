package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.TruckFuelModel;

import java.util.Date;
@Entity
public class TruckFuel extends BaseEntity {
    private int truckId;
    private String ticketNo;
    private String tankNo;
    private String maintenanceStaff;
    private String qcNo;
    private Date time;
    private int operatorId;

    private double amount; //litter
    private double accumulatedRefuelAmount;//litter
    private double truckCapacity;

    public static TruckFuel fromTruckFuelModel(TruckFuelModel model) {
        if (model != null) {
            TruckFuel item = gson.fromJson(model.toJson(), TruckFuel.class);
            item.setJsonData(model.toJson());

            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public  TruckFuelModel toTruckFuelModel() {

        TruckFuelModel model = gson.fromJson(this.getJsonData(), TruckFuelModel.class);
        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());
        return model;

    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
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

    public double getAccumulatedRefuelAmount() {
        return accumulatedRefuelAmount;
    }

    public void setAccumulatedRefuelAmount(double accumulatedRefuelAmount) {
        this.accumulatedRefuelAmount = accumulatedRefuelAmount;
    }

    public double getTruckCapacity() {
        return truckCapacity;
    }

    public void setTruckCapacity(double truckCapacity) {
        this.truckCapacity = truckCapacity;
    }
}
