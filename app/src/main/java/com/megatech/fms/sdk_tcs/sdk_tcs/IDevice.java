package com.megatech.fms.sdk_tcs.sdk_tcs;

import com.megatech.fms.sdk_tcs.sdk_tcs.model.DeviceDataView;

public interface IDevice {
    void connect();

    void disConnect();

    boolean isConnect();

    void runTask();

    void runCommand();

    void readField();

    void writeField();

    void setPreset(int value);

    void setCommand(int value);

    int getFlagCommand();

    DeviceDataView getDeviceDataView();
    //LcrTransRecord getLcrTransRecord();
    //void setHaveWriteLog(int flag);
}
