package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2505Model;

import java.util.Date;
@Entity
public class BM2505 extends BaseEntity {
    private Date time;
    private int flightId;
    private int truckId;

    public static BM2505 fromModel(BM2505Model model) {
        if (model != null) {
            BM2505 item = gson.fromJson(model.toJson(), BM2505.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public  BM2505Model toModel() {

        BM2505Model model = gson.fromJson(this.getJsonData(), BM2505Model.class);
        model.setLocalId(this.getLocalId());
        // id trong jsonData có thể còn là 0 (bản ghi tạo offline, sau đó mới được server cấp id),
        // lấy theo entity để lần lưu sau là cập nhật chứ không tạo bản ghi mới.
        model.setId(this.getId());
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
