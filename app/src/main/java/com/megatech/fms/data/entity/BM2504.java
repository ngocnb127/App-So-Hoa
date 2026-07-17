package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2504Model;

import java.util.Date;

@Entity
public class BM2504 extends BaseEntity {

    private Date time;
    private int flightId;
    private int truckId;

    /* ================= CONVERT ================= */

    public static BM2504 fromModel(BM2504Model model) {
        if (model != null) {
            BM2504 item = gson.fromJson(model.toJson(), BM2504.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return item;
        }
        return null;
    }

    public BM2504Model toModel() {
        BM2504Model model =
                gson.fromJson(this.getJsonData(), BM2504Model.class);

        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());

        return model;
    }

    /* ================= GET / SET ================= */

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
