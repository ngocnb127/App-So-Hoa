package com.megatech.fms.helpers;


import com.megatech.tcpclient.TcpClient;
import com.megatech.tcpclient.TcpEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;

public class LCRWorker implements Observer {

    private final TcpClient mTcpClient;
    final int LCR_PORT = 10001;
    private final String ipAddress;
    public  LCRWorker(String ipAddress)
    {
        this.ipAddress = ipAddress;
        mTcpClient = new TcpClient(ipAddress, LCR_PORT);
        this.mTcpClient.addObserver(this);
    }

    public void connect()
    {
        mTcpClient.connect();
    }
    public void disconnect()
    {
        mTcpClient.disconnect();
    }
    public interface LCRWorkerListener{
        void onConnected();
        void onSent();
        void onReceived(LCR_FIELD field, Object data);
        void onCompleted();
    }
    private LCRWorkerListener lcrWorkerListener;

    public LCRWorkerListener getLcrWorkerListener() {
        return lcrWorkerListener;
    }

    public void setLcrWorkerListener(LCRWorkerListener lcrWorkerListener) {
        this.lcrWorkerListener = lcrWorkerListener;
    }

    private void onCompleted()
    {
        if(lcrWorkerListener!=null)
            lcrWorkerListener.onCompleted();
    }
    private void onConnected()
    {
        //processQueue();
        if (lcrWorkerListener!=null)
            lcrWorkerListener.onConnected();
    }
    public void requestField(LCR_FIELD field) {

        addToQueue(field);

    }

    Queue<LCR_FIELD> queue = new ArrayDeque<>();
    private boolean processing = false;
    private void addToQueue(LCR_FIELD field)
    {
        if (!queue.contains(field))
            queue.add(field);
        if (mTcpClient.isConnected())
            processQueue();
        else
            connect();
    }


    private void processQueue() {
        Logger.appendLog("LCW","Process Queue: " + queue.size());
        try {
            if (queue.isEmpty()) {
                disconnect();
                onCompleted();
            } else if (!processing) {
                processing = true;
                requestField = queue.poll();
                switch (requestField) {
                    case GROSS_QTY:
                        sendRequest(GROSS_QTY);
                        break;
                    case PREV_METER:
                        sendRequest(PREV_METER);
                        break;
                    case GROSS_METER:
                        sendRequest(GROSS_METER);
                        break;
                }
            }
        } catch (Exception ex) {
        }
    }
    @Override
    public void update(Observable observable, Object o) {

        TcpEvent event = (TcpEvent)o;
        char[] payload = null;
        if (event.getPayload() !=null && event.getPayload() instanceof char[])
            payload = (char[])event.getPayload();
        switch (event.getTcpEventType()) {
            case CONNECTION_FAILED:

                break;

            case CONNECTION_ESTABLISHED:
                // printer connected, start sending data line to print;
                onConnected();
                break;
            case MESSAGE_RECEIVED:
                if (payload!=null && requesting)
                {
                    requesting = false;
                    processData(payload);
                }

                break;


            case MESSAGE_SENT:


                break;

        }

    }

    private void processData(char[] payload) {
        try {


            if (payload.length > 5) {
                int len = payload[5];
                int data = 0;
                for (int i = 0; i < len - 2; i++) {
                    data = (data << 8) + payload[len + i + 2];
                }
                //disconnect();

                onReceived(requestField, (double) data);
            }
        } catch (Exception ex) {

        }


        processing = false;
        processQueue();

    }

    private void onReceived(LCR_FIELD requestField, Object data) {
        if (lcrWorkerListener!=null)
            lcrWorkerListener.onReceived(requestField, data);

    }

    private boolean requesting = false;
    private LCR_FIELD requestField;
    private void sendRequest(char[] bytes)
    {
        requesting = true;
        mTcpClient.sendMessage(BuildMessage(bytes));
    }
    public  enum LCR_FIELD
    {
        GROSS_QTY,
        GROSS_METER,
        PREV_METER
    }
    private final char[] GROSS_QTY = new char[]{0x20, 0x2C};
    private final char[] GROSS_METER = new char[]{0x20, 0x11};
    private final char[] PREV_METER = new char[]{0x20, 0x64};



    private  char[] BuildMessage(char[] Bytes) {
        ArrayList<Character> buff = new ArrayList<Character>();
        buff.add((char) 126);
        buff.add((char) 126);

        short crc = 32382;
        crc = AddByte(buff, (char) 250, crc);
        crc = AddByte(buff, (char) 255, crc);
        crc = AddByte(buff, (char) 0, crc);
        crc = AddByte(buff, ((char)(Bytes.length)), crc);
        for (char b : Bytes) {
            crc = AddByte(buff, b, crc);
        }

        AddByte(buff, ((char)((crc % 256))), crc);
        AddByte(buff, ((char)((crc / 256))), crc);
        char[] arr = new char[buff.size()];
        for (int i=0; i<buff.size(); i++){
            arr[i] = (char) (buff.get(i));
        }
        return arr;
    }

    private  short AddByte(List<Character> buff, char b, short crc) {
        buff.add(b);
        crc = UpdateCRC(crc, b);
        return crc;
    }

    private  short UpdateCRC(short crc, char b) {
        for (int i = 7; (i >= 0); i--) {
            boolean XORFlag = ((crc & 32768)!= 0);
            crc = ((short)((crc << 1)));
            crc = (short)(crc | ((short)((1 & (b >> i)))));
            if (XORFlag) {
                crc ^=    4129;
            }

        }

        return crc;
    }

}
