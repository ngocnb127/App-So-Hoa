package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2506Model;

import java.util.Date;

@Entity
public class BM2506 extends BaseEntity {
    private Date time;
    private int truckId;

    public static BM2506 fromModel(BM2506Model model) {
        if (model != null) {
            BM2506 item = gson.fromJson(model.toJson(), BM2506.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return item;
        }
        else
            return null;
    }

    public BM2506Model toModel() {
        BM2506Model model = gson.fromJson(this.getJsonData(), BM2506Model.class);
        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());
        return model;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }
}
