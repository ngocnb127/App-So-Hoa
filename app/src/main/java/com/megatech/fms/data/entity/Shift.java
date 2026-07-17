package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.ShiftModel;

import java.util.Date;

@Entity
public class Shift extends BaseEntity {


    private Date startTime;
    private Date endTime;

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public static Shift fromShiftModel(ShiftModel model) {
        Shift item = new Shift();
        item.setId(model.getId());
        item.setLocalId(model.getLocalId());
        item.setJsonData(gson.toJson(model));
        return item;
    }
    public ShiftModel toShiftModel() {

        ShiftModel shiftModel = gson.fromJson(getJsonData(), ShiftModel.class);
        shiftModel.setLocalId(getLocalId());
        return shiftModel;
    }
}
