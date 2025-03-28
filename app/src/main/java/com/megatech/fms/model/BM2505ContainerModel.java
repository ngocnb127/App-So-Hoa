package com.megatech.fms.model;

public class BM2505ContainerModel extends BaseModel {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString()
    {
        return this.name;
    }

}
