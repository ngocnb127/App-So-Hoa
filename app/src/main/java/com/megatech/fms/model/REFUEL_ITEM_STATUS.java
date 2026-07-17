package com.megatech.fms.model;

import com.google.gson.annotations.SerializedName;

public enum REFUEL_ITEM_STATUS {
    @SerializedName("0")    NONE(0),
    @SerializedName("1") PROCESSING(1),
    @SerializedName("2") PAUSED(2),
    @SerializedName("3") DONE(3),
    @SerializedName("2") ERROR(4);
    private final int value;
    REFUEL_ITEM_STATUS(int i) {
        value = i;
    }

    public  int getValue()
    {
        return value;
    }
}
