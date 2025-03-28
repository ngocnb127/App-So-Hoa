package com.megatech.fms.enums;

import com.google.gson.annotations.SerializedName;

public enum RETURN_UNIT {
    @SerializedName("0") KG(0),
    @SerializedName("1") GALLON(1);

    private final int value;
    RETURN_UNIT(int i) {
        value = i;
    }

    public  int getValue()
    {
        return value;
    }
}
