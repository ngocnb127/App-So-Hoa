package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;

@Entity
public class Airports extends BaseEntity {
    private String code;
    private String name;
    private String taxCode;
    private String address;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
    public static Airports fromAirportsModel(AirportsModel model) {
        Airports item = new Airports();
        item.setId(model.getId());
        item.setLocalId(model.getLocalId());
        item.setJsonData(gson.toJson(model));
        return item;
    }

    public AirportsModel toAirportsModel() {
        AirportsModel model = gson.fromJson(this.getJsonData(), AirportsModel.class);
        model.setLocalId(this.getLocalId());
        return model;
    }
}

