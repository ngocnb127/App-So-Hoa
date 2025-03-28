package com.megatech.fms.data.entity;

import androidx.room.Entity;

@Entity
public class Price extends BaseEntity {
    private int flightTye;
    private int customerId;
    private int unit;

    public int getFlightTye() {
        return flightTye;
    }

    public void setFlightTye(int flightTye) {
        this.flightTye = flightTye;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getUnit() {
        return unit;
    }

    public void setUnit(int unit) {
        this.unit = unit;
    }

    public int getCurrency() {
        return currency;
    }

    public void setCurrency(int currency) {
        this.currency = currency;
    }

    public int getStartDate() {
        return startDate;
    }

    public void setStartDate(int startDate) {
        this.startDate = startDate;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    private int currency;
    private int startDate;
    private double price;

}
