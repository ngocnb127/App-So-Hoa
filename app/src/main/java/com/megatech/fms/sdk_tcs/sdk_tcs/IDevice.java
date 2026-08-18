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

    /**
     * Thời điểm nhận được gói dữ liệu gần nhất (millis), 0 nếu chưa từng nhận.
     *
     * <p>Kết nối còn sống KHÔNG có nghĩa là số còn chảy. Không có mốc này thì màn hình
     * không phân biệt được "đồng hồ đang đứng yên" với "app đã ngừng nhận số" — đo trên
     * máy thật 18-08: app đứng ở 1832 trong khi đồng hồ đã lên 2155, dấu kết nối vẫn xanh.
     */
    long getLastDataAt();
    //LcrTransRecord getLcrTransRecord();
    //void setHaveWriteLog(int flag);
}
