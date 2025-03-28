package com.megatech.fms.model;

import androidx.annotation.NonNull;

import com.megatech.fms.model.RefuelItemData.CURRENCY;

public class AirportsModel extends BaseModel {

    private String name;
    private String taxCode;
    private String address;
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
