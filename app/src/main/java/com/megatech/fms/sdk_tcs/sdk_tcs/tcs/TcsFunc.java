package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

import com.megatech.fms.sdk_tcs.sdk_tcs.network.NetWorkCls;
import com.megatech.fms.sdk_tcs.sdk_tcs.network.TcsTcpSck;

public class TcsFunc {
    private NetWorkCls nwSck = new TcsTcpSck(); // Assuming TcsTcpSck extends NetWorkCls

    private int msgTo, msgFrom, msgStatus;

    public void setHaveLogWrite(int value) {
        nwSck.setHaveLogWrite(value);
    }

    public boolean isConnect() {
        if (nwSck == null) return false;
        return nwSck.isConnected();
    }

    public void setMsgTo(int msgTo) {
        this.msgTo = msgTo;
    }

    public void setMsgFrom(int msgFrom) {
        this.msgFrom = msgFrom;
    }

    public void setMsgStatus(int msgStatus) {
        this.msgStatus = msgStatus;
    }

    public void connectIp(String ip, int port) {
        nwSck.nwConnect(ip, port);
    }
    public void disConnect() {
        nwSck.nwDisconnect();
    }

    public byte[] setField(int cmd, byte[] data) {
        TcsMsgSend tempSend = new TcsMsgSend(msgTo, msgFrom, msgStatus, cmd, data);
        tempSend.updateMsg();
        return nwSck.sendData(tempSend.getMsg());
    }

    public byte[] setField(byte[] value) {
        byte[] tempData = value;
        TcsMsgSend tempSend = new TcsMsgSend(msgTo, msgFrom, msgStatus, 1, tempData);
        return nwSck.sendData(tempSend.getMsg());
    }

    public byte[] getField(int cmd) {
        TcsMsgSend tempSend = new TcsMsgSend(msgTo, msgFrom, msgStatus, cmd);
        tempSend.updateMsg();
        return nwSck.sendData(tempSend.getMsg());
    }

    // Uncomment and adapt if needed
    /*
    public byte[] sendCmd(int value1) {
        byte[] tempData = new byte[] { 0x24, (byte)value1 };
        TcsMsgSend tempSend = new TcsMsgSend(msgTo, msgFrom, msgStatus, 1, tempData);
        tempSend.updateMsg();
        return nwSck.sendData(tempSend.getMsg());
    }
    */
}
