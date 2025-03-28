package com.megatech.fms.data.entity;

import android.icu.lang.UProperty;

import androidx.room.Entity;

import com.megatech.fms.model.ReceiptModel;

import java.util.Date;

@Entity
public class Receipt extends  BaseEntity {
    private String number;
    private Date date;

    private int customerId;
    private String customerName;
    private String customerAddress;
    private String taxCode;

    private int flightId;
    private String flightCode;
    private String aircraftCode;
    private String aircraftTyppe;
    private String routeName;

    private String qcNo;
    private Date startTime;
    private Date endTime;


    private double gallon;
    private double volume;
    private double weight;

    private double returnAmount;
    private String defuelingNo;

    private boolean invoiceSplit;
    private double splitAmount;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
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

    public void setAircraftCode(String aircraftCode) {
        this.aircraftCode = aircraftCode;
    }

    public String getAircraftTyppe() {
        return aircraftTyppe;
    }

    public void setAircraftTyppe(String aircraftTyppe) {
        this.aircraftTyppe = aircraftTyppe;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getQcNo() {
        return qcNo;
    }

    public void setQcNo(String qcNo) {
        this.qcNo = qcNo;
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
        this.volume = volume;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getReturnAmount() {
        return returnAmount;
    }

    public void setReturnAmount(double returnAmount) {
        this.returnAmount = returnAmount;
    }

    public String getDefuelingNo() {
        return defuelingNo;
    }

    public void setDefuelingNo(String defuelingNo) {
        this.defuelingNo = defuelingNo;
    }

    public boolean isInvoiceSplit() {
        return invoiceSplit;
    }

    public void setInvoiceSplit(boolean invoiceSplit) {
        this.invoiceSplit = invoiceSplit;
    }

    public double getSplitAmount() {
        return splitAmount;
    }

    public void setSplitAmount(double splitAmount) {
        this.splitAmount = splitAmount;
    }

    private boolean isCancelled;

    private String cancelReason;

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }



    public ReceiptModel toModel()
    {
        ReceiptModel model = gson.fromJson(this.getJsonData(), ReceiptModel.class);
        model.setLocalId(this.getLocalId());
        model.setCancelled(isCancelled);
        model.setCancelReason(cancelReason);
        model.setLocalModified(this.isLocalModified());
        return model;

    }

    public  static Receipt fromModel(ReceiptModel model)
    {
        String json = model.toJson();
        Receipt entity = gson.fromJson(json, Receipt.class);
        entity.setJsonData(json);
        entity.setId(model.getId());
        return entity;
    }
}
