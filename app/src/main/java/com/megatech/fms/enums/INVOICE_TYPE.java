package com.megatech.fms.enums;

import androidx.room.TypeConverter;

import com.google.gson.annotations.SerializedName;

public enum INVOICE_TYPE {
    @SerializedName("0")    INVOICE(0),
    @SerializedName("1") BILL(1);

    private final int value;

    INVOICE_TYPE(int i) {
        value = i;
    }

    @TypeConverter
    public static Integer getInt(INVOICE_TYPE status) {
        return status.value;
    }
}