package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

public class TcsMsgBase {

    private int msgFrom;
    private int msgTo;
    private int msgStatus;
    protected byte[] msg;
    protected int msgSize = 0;
    protected TcsCls tcsCls = new TcsCls();

    public TcsMsgBase(int msgTo, int msgFrom, int msgStatus) {
        this.msgFrom = msgFrom;
        this.msgTo = msgTo;
        this.msgStatus = msgStatus;
    }

    public int getMsgFrom() {
        return msgFrom;
    }

    public int getMsgTo() {
        return msgTo;
    }

    public int getMsgStatus() {
        return msgStatus;
    }

    public byte[] getMsg() {
        return msg;
    }

    public void setMsg(byte[] msg) {
        this.msg = msg;
    }
}
