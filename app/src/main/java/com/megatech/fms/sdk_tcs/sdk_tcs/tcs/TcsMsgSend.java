package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

import java.util.ArrayList;
import java.util.List;

public class TcsMsgSend extends TcsMsgBase {
    private byte[] inputByte;
    private int cmd;

    public TcsMsgSend(int msgDest, int msgSrc, int msgFlag, int msgCmd) {
        this(msgDest, msgSrc, msgFlag, msgCmd, null, "");
    }

    public TcsMsgSend(int msgDest, int msgSrc, int msgFlag, int msgCmd, byte[] data) {
        this(msgDest, msgSrc, msgFlag, msgCmd, data, "");
    }

    public TcsMsgSend(int msgDest, int msgSrc, int msgFlag, int msgCmd, byte[] data, String strData) {
        super(msgDest, msgSrc, msgFlag);
        this.cmd = msgCmd;

        if (data != null && data.length > 0) {
            inputByte = tcsCls.setMessageArray(data);
        } else if (strData != null && !strData.isEmpty()) {
            inputByte = tcsCls.setMessageArray(strData);
        } else {
            inputByte = new byte[0];
        }

        this.updateMsg();
    }

    public void updateMsg() {
        setMsg(tcsCls.setMessageArray(
                TcsMsg(getMsgTo(), getMsgFrom(), getMsgStatus(), cmd, inputByte.length, inputByte)));
    }

    private byte[] TcsMsg(int dest, int src, int msgFlag, int cmd, int leng, byte[] msg) {
        List<Byte> msgBytes = new ArrayList<>();

        msgBytes.add((byte) 0x7E);
        msgBytes.add((byte) dest);
        msgBytes.add((byte) src);
        msgBytes.add((byte) msgFlag);
        msgBytes.add((byte) cmd);
        msgBytes.add((byte) leng);

        for (byte b : msg) {
            msgBytes.add(b);
        }

        byte[] temp = new byte[msgBytes.size()];
        for (int i = 0; i < msgBytes.size(); i++) {
            temp[i] = msgBytes.get(i);
        }

        byte crc = tcsCls.msgCrc(temp);
        msgBytes.add(crc);

        byte[] result = new byte[msgBytes.size()];
        for (int i = 0; i < msgBytes.size(); i++) {
            result[i] = msgBytes.get(i);
        }

        return result;
    }
}
