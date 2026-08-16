package com.megatech.fms.sdk_tcs.sdk_tcs.model;

import com.megatech.fms.sdk_tcs.sdk_tcs.DeviceConnectState;
import com.megatech.fms.sdk_tcs.sdk_tcs.tcs.TCS_DELIVERY_STATE;
import com.megatech.fms.sdk_tcs.sdk_tcs.tcs.TCS_SYSTEM_STATE;

public class DeviceDataView {

    private double grossPreset;
    private double grossQty;
    private double flowRate;
    private double grossTotal;
    private double temperature;
    private double grossTotalRound;
    private double grossQtyRound;
    private int rc;
    private int devStatus;

    private int productId;
    private String name;
    private String version;

    private int delStatus;
    private int delCode;

    /** Số ticket đọc từ thiết bị (SYS_TICKETNR). 0 = chưa đọc được. */
    private long ticketNumber;

    // Network
    private String dateUL;
    private String timeUL;

    private DeviceConnectState connectState;


    // TCS
    private TCS_DELIVERY_STATE tcsDeliveryState;
    private TCS_SYSTEM_STATE tcsSystemState;

    public long getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(long ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    // Getters and Setters
    public double getGrossPreset() {
        return grossPreset;
    }


    public double getGrossTotalRound() {
        return grossTotalRound;
    }

    public void setGrossTotalRoundFromDouble(double value) {
        this.grossTotalRound = (double) Math.round(value);
    }

    public double getGrossQtyRound() {
        return grossQtyRound;
    }

    public void setGrossQtyRoundFromDouble(double value) {
        this.grossQtyRound = (double) Math.round(value);
    }
    public void setGrossPreset(double grossPreset) {
        this.grossPreset = grossPreset;
    }

    public double getGrossQty() {
        return grossQty;
    }

    public void setGrossQty(double grossQty) {
        this.grossQty = grossQty;
    }

    public double getFlowRate() {
        return flowRate;
    }

    public void setFlowRate(double flowRate) {
        this.flowRate = flowRate;
    }

    public double getGrossTotal() {
        return grossTotal;
    }

    public void setGrossTotal(double grossTotal) {
        this.grossTotal = grossTotal;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getRc() {
        return rc;
    }

    public void setRc(int rc) {
        this.rc = rc;
    }

    public int getDevStatus() {
        return devStatus;
    }

    public void setDevStatus(int devStatus) {
        this.devStatus = devStatus;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getDelStatus() {
        return delStatus;
    }

    public void setDelStatus(int delStatus) {
        this.delStatus = delStatus;
    }

    public int getDelCode() {
        return delCode;
    }

    public void setDelCode(int delCode) {
        this.delCode = delCode;
    }

    public String getDateUL() {
        return dateUL;
    }

    public void setDateUL(String dateUL) {
        this.dateUL = dateUL;
    }

    public String getTimeUL() {
        return timeUL;
    }

    public void setTimeUL(String timeUL) {
        this.timeUL = timeUL;
    }

    public DeviceConnectState getConnectState() {
        return connectState;
    }

    public void setConnectState(DeviceConnectState connectState) {
        this.connectState = connectState;
    }


    public TCS_DELIVERY_STATE getTcsDeliveryState() {
        return tcsDeliveryState;
    }

    public void setTcsDeliveryState(TCS_DELIVERY_STATE tcsDeliveryState) {
        this.tcsDeliveryState = tcsDeliveryState;
    }

    public TCS_SYSTEM_STATE getTcsSystemState() {
        return tcsSystemState;
    }

    public void setTcsSystemState(TCS_SYSTEM_STATE tcsSystemState) {
        this.tcsSystemState = tcsSystemState;
    }
}
