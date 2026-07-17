package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM2505Model;

import java.util.Date;

@Entity
public class BM2503 extends BaseEntity {
    private Date time;
    private int flightId;
    private int truckId;

    public static BM2503 fromModel(BM2503Model model) {
        if (model != null) {
            BM2503 item = gson.fromJson(model.toJson(), BM2503.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public  BM2503Model toModel() {

        BM2503Model model = gson.fromJson(this.getJsonData(), BM2503Model.class);
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

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }

    public int getTruckId() {
        return truckId;
    }

    public void setTruckId(int truckId) {
        this.truckId = truckId;
    }
}
