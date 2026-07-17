package com.megatech.fms.enums;

import com.google.gson.annotations.SerializedName;

public enum REVIEW_RATE
{
    @SerializedName("1")   WORST (1),
    @SerializedName("2")  BAD(2),
    @SerializedName("3")  NEUTRAL(3),
    @SerializedName("4")  GOOD(4),
    @SerializedName("5")  BEST (5);

    private int val;
    REVIEW_RATE(int i) {
        val = i;
    }

    public int getVal() {
        return val;
    }

    public void setVal(int val) {
        this.val = val;
    }
}