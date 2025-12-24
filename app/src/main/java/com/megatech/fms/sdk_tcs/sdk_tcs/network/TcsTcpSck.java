package com.megatech.fms.sdk_tcs.sdk_tcs.network;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TcsTcpSck extends NetWorkCls {
    private Socket sck;
    //private TextLog textLog = new TextLog();
    private boolean bConnected;
    private int timeOut;
    private int haveWriteLog = 0;

    public interface ResponseData {
        void onResponseData(int id, byte[] data);
    }

    public interface ExceptionData {
        void onExceptionData(int id, Exception ex);
    }

    private ResponseData responseDataListener;
    private ExceptionData exceptionDataListener;

    public void setResponseDataListener(ResponseData listener) {
        this.responseDataListener = listener;
    }

    public void setExceptionDataListener(ExceptionData listener) {
        this.exceptionDataListener = listener;
    }

    @Override
    public void setHaveLogWrite(int value) {
        haveWriteLog = value;
    }

    @Override
    public boolean isConnected() {
        return sck != null && sck.isConnected() && bConnected;
    }

    @Override
    public void nwConnect(String ip, int port) {
        try {
            sck = new Socket();
            timeOut = 1500;
            sck.connect(new InetSocketAddress(ip, port), timeOut);
            sck.setSoTimeout(timeOut);
            bConnected = true;
        } catch (Exception ex) {
            //textLog.writeToErrorLog("Error nwConnect " + ex.getMessage(), haveWriteLog);
            if (sck != null) {
                try {
                    sck.close();
                } catch (Exception e) {}
                sck = null;
            }
            bConnected = false;
        }
    }

    @Override
    public void nwDisconnect() {
        nwDispose();
    }

    @Override
    protected void nwDispose() {
        if (sck != null) {
            try {
                sck.close();
            } catch (Exception ignored) {}
            sck = null;
        }
    }

    @Override
    public byte[] sendData(byte[] writeData) {
        byte[] data = null;
        try {
            if (sck != null && sck.isConnected()) {
                OutputStream output = sck.getOutputStream();
                output.write(writeData);
                output.flush();

                Thread.sleep(50);
                //textLog.writeToErrorLog("Send:" + display(writeData), haveWriteLog);

                InputStream input = sck.getInputStream();
                byte[] buffer = new byte[1024];
                List<Byte> tempInput = new ArrayList<>();

                boolean bReceived = false;
                long startTime = System.currentTimeMillis();

                while (!bReceived) {
                    if (sck != null && sck.isConnected()) {
                        int bytesRead = input.read(buffer);
                        for (int i = 0; i < bytesRead; i++) {
                            tempInput.add(buffer[i]);
                        }

                        byte[] clientData = new byte[tempInput.size()];
                        for (int i = 0; i < tempInput.size(); i++) {
                            clientData[i] = tempInput.get(i);
                        }

                        int receiveByteLen = clientData.length;
                        if (receiveByteLen > 6) {
                            int tempLenFollow = clientData[5] + 7;
                            if (receiveByteLen >= tempLenFollow) {
                                bReceived = true;
                                data = clientData;
                                tempInput.clear();
                            }
                        }
                    } else {
                        bReceived = true;
                    }

                    if (System.currentTimeMillis() - startTime > 1500) {
                        bReceived = true;
                        tempInput.clear();
                    }
                }
            }
        } catch (Exception ex) {
            Log.d("TCS", "Data: "+ ex.toString() + "Message : " + ex.getMessage());
            //textLog.writeToErrorLog("Error SendData " + ex.getMessage(), haveWriteLog);
            if (sck != null) {
                try {
                    sck.close();
                } catch (Exception ignored) {

                }
                sck = null;
            }
            bConnected = false;
        }

        //textLog.writeToErrorLog("Rev:" + display(data), haveWriteLog);
        return data;
    }

    private String display(byte[] data) {
        if (data == null) return "";
        StringBuilder result = new StringBuilder();
        for (byte b : data) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}
