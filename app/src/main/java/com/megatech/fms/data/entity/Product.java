package com.megatech.fms.data.entity;

import androidx.room.Entity;

import com.megatech.fms.model.ProductModel;

@Entity
public class Product extends BaseEntity {

    private String code;
    private String name;

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

    // Convert from model to entity
    public static Product fromModel(ProductModel model) {
        Product entity = new Product();
        entity.setId(model.getId());
        entity.setLocalId(model.getLocalId());
        entity.setName(model.getName());
        entity.setCode(model.getCode());
        entity.setJsonData(gson.toJson(model));
        return entity;
    }

    // Convert from entity to model
    public ProductModel toModel() {
        ProductModel model = gson.fromJson(this.getJsonData(), ProductModel.class);
        model.setLocalId(this.getLocalId());
        return model;
    }
}

