package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.AirlineModel;

@Entity
public class Airline extends BaseEntity {



    private String name;
    private String address;
    private String taxCode;
    private String code;
    private String invoiceTaxCode;
    private String invoiceName;
    private String invoiceAddress;

    public String getInvoiceTaxCode() {
        return invoiceTaxCode;
    }

    public void setInvoiceTaxCode(String invoiceTaxCode) {
        this.invoiceTaxCode = invoiceTaxCode;
    }

    public String getInvoiceName() {
        return invoiceName;
    }

    public void setInvoiceName(String invoiceName) {
        this.invoiceName = invoiceName;
    }

    public String getInvoiceAddress() {
        return invoiceAddress;
    }

    public void setInvoiceAddress(String invoiceAddress) {
        this.invoiceAddress = invoiceAddress;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public static Airline fromAirlineModel(AirlineModel model) {
        Airline item = new Airline();
        item.setId(model.getId());
        item.setLocalId(model.getLocalId());
        item.setJsonData(gson.toJson(model));
        return item;
    }

    public AirlineModel toAirlineModel() {
        AirlineModel model = gson.fromJson(this.getJsonData(), AirlineModel.class);
        model.setLocalId(this.getLocalId());
        return model;
    }
}

