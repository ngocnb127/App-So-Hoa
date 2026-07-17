package com.megatech.fms.sdk_tcs.sdk_tcs;


public enum EDeviceType {
    TCS(1),
    LCR(2);

    private final int value;

    EDeviceType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}