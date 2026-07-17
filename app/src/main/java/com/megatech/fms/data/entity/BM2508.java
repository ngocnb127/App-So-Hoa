package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2508Model;

import java.util.Date;

@Entity
public class BM2508 extends BaseEntity {
    public Date DateCreated;
    private Integer FlightId = 0;

    public Integer getFlightId() {
        return FlightId;
    }
    public void setFlightId(Integer FlightId) {
        this.FlightId = FlightId;
    }
    public Date getDateCreated() {
        return DateCreated;
    }
    public void setDateCreated(Date DateCreated) {
        this.DateCreated = DateCreated;
    }
    public static BM2508 fromModel(BM2508Model model) {
        if (model != null) {
            BM2508 item = gson.fromJson(model.toJson(), BM2508.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public BM2508Model toModel() {

        BM2508Model model = gson.fromJson(this.getJsonData(), BM2508Model.class);
        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());
        return model;

    }

}
