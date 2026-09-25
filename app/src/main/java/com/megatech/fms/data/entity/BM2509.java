package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2509Model;

import java.util.Date;

@Entity
public class BM2509 extends BaseEntity {
    private Date time;
    private int truckId;

    public static BM2509 fromModel(BM2509Model model) {
        if (model != null) {
            BM2509 item = gson.fromJson(model.toJson(), BM2509.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return item;
        }
        else
            return null;
    }

    public BM2509Model toModel() {
        BM2509Model model = gson.fromJson(this.getJsonData(), BM2509Model.class);
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
