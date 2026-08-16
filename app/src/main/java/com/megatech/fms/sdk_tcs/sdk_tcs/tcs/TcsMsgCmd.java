package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

public enum TcsMsgCmd {
    CMD_SYS_TIME(0x04),
    CMD_SYS_DATE(0x05),
    CMD_DEL_CFGDIR(0x37),
    CMD_DEL_CFGPRS(0x38),
    CMD_DEL_BEGIN(0x3C),
    CMD_DEL_END(0x3D),
    CMD_DEL_PAUSE(0x39),
    CMD_DEL_RESUME(0x3A),
    CMD_PRN_ITPRN(0x3E),
    CMD_DEL_GROSSDSP(0x2B),
    CMD_DEL_STATE(0x1F),
    CMD_DEL_FRT(0x42),
    CMD_DEL_AVGTEMP(0x40),
    CMD_SYS_GROSSTOTAL(0x1E),
    /** Số ticket kế tiếp của thiết bị (SYS_TICKETNR) — dùng làm số bán hàng của mẻ. */
    CMD_SYS_TICKETNR(0x1D),
    CMD_DEL_PRODUCT(0x0C);

    private final int value;

    TcsMsgCmd(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
