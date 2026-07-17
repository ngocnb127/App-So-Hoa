package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.google.type.DateTime;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.CheckTrucksModel;

import java.util.Date;

@Entity
public class CheckTrucks extends BaseEntity {
    public Date DateCreated;

    public Date getDateCreated() {
        return DateCreated;
    }
    public void setDateCreated(Date DateCreated) {
        this.DateCreated = DateCreated;
    }
    public static CheckTrucks fromModel(CheckTrucksModel model) {
        if (model != null) {
            CheckTrucks item = gson.fromJson(model.toJson(), CheckTrucks.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public  CheckTrucksModel toModel() {

        CheckTrucksModel model = gson.fromJson(this.getJsonData(), CheckTrucksModel.class);
        model.setLocalId(this.getLocalId());
        model.setDeleted(this.isDeleted());
        return model;

    }
}
