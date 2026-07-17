package com.megatech.fms.sdk_tcs.sdk_tcs.network;

public abstract class NetWorkCls {

    public abstract boolean isConnected();

    public abstract void nwConnect(String ip, int port);

    public abstract void nwDisconnect();

    protected abstract void nwDispose();

    public abstract byte[] sendData(byte[] writeData);

    public abstract void setHaveLogWrite(int value);
}
