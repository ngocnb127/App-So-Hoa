package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;

@Entity
public class BM2505Container extends BaseEntity {
    private String name;

    public static BM2505Container fromModel(BM2505ContainerModel model) {
        if (model != null) {
            BM2505Container item = gson.fromJson(model.toJson(), BM2505Container.class);
            item.setJsonData(model.toJson());
            item.setId(model.getId());
            return  item;
        }

        else
            return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BM2505ContainerModel toModel() {

        return gson.fromJson(this.jsonData, BM2505ContainerModel.class);
    }
}
