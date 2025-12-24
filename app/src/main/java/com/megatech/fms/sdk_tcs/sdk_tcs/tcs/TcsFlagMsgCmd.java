package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

public enum TcsFlagMsgCmd {
    FLAG_TYPE20(0x20),
    FLAG_TYPE40(0x40);

    private final int value;

    TcsFlagMsgCmd(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
