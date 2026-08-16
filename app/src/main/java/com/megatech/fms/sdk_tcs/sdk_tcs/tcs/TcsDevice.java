package com.megatech.fms.sdk_tcs.sdk_tcs.tcs;

import com.megatech.fms.sdk_tcs.sdk_tcs.DeviceConnectState;
import com.megatech.fms.sdk_tcs.sdk_tcs.IDevice;
import com.megatech.fms.sdk_tcs.sdk_tcs.model.DeviceDataView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class TcsDevice implements IDevice {
    private DeviceConnectState tcsState;
    private TCS_DELIVERY_STATE tcsDeliveryState;
    private TCS_SYSTEM_STATE tcsSystemState;
    //private TextLog textLog = new TextLog();
    private TcsFunc tcsFunc = new TcsFunc();

    // Local values
    private int rc, devStatus;
    private int productId, GrossPreset;
    private double GrossQty, FlowRate, FlowState, GrossTotal_WM;
    private int deliveryCode, deliveryStatus, prevDeliveryStatus;
    private String name, dateUL, timeUL;
    private double avgTemp;

    private int flagDirect;
    private int flagPreset;
    private int flagIssueCommand;
    private int flagReadTransRecord;
    private int flagInternalCommand = 3;
    private int version;
    private int countDisconnect = 0;
    private int haveWriteLog = 0;
    private int commandType;
    private int fieldSet;
    private int valueFieldSet;

    private TCS_DLV_TYPE DlvType;
    private short ProdId;

    private DeviceDataView tcsDataView = new DeviceDataView();
    private long ticketNumber = 0;

    private final String ip;
    private final int port;

    private final Runnable onConnected;
    private final Runnable onDisconnected;
    private final Runnable onReceivedData;
    private final Runnable onStartedDelivery;
    private final Runnable onEndingDelivery;
    private final Runnable onStoppedDelivery;

    /**
     * Số vòng đọc tối đa còn chờ ở pha ENDING trước khi coi như mẻ đã kết thúc.
     * Mỗi vòng cách nhau {@link #POLL_INTERVAL_MS}, tương đương 30 giây.
     */
    private static final int ENDING_TIMEOUT_LOOPS = 60;
    private static final long POLL_INTERVAL_MS = 500;

    // Giá trị cuối của mẻ, giữ lại từ pha ENDING phòng khi thiết bị xoá màn hình khi về IDLE.
    private double lastDeliveryGrossQty, lastDeliveryGrossTotal, lastDeliveryAvgTemp;
    private long lastDeliveryTicketNumber;

    public TcsDevice(
            String ip,
            int port,
            Runnable onConnected,
            Runnable onDisconnected,
            Runnable onReceivedData,
            Runnable onStartedDelivery,
            Runnable onStoppedDelivery
    ) {
        this(ip, port, onConnected, onDisconnected, onReceivedData, onStartedDelivery, null, onStoppedDelivery);
    }

    public TcsDevice(
            String ip,
            int port,
            Runnable onConnected,
            Runnable onDisconnected,
            Runnable onReceivedData,
            Runnable onStartedDelivery,
            Runnable onEndingDelivery,
            Runnable onStoppedDelivery
    ) {
        this.ip = ip;
        this.port = port;
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.onReceivedData = onReceivedData;
        this.onStartedDelivery = onStartedDelivery;
        this.onEndingDelivery = onEndingDelivery;
        this.onStoppedDelivery = onStoppedDelivery;
    }

    public void connect() {
        countDisconnect++;
        tcsState = DeviceConnectState.DISCONNECTED;

        if (countDisconnect == 5) {
            productId = 0;
            version = 0;
            countDisconnect = 0;
        }

        tcsFunc.connectIp(ip, port);
        tcsFunc.setMsgTo(1);
        tcsFunc.setMsgFrom(0);
        tcsFunc.setMsgStatus(0);
    }

    @Override
    public void disConnect() {
        tcsFunc.disConnect();
    }

    public void setPreset(int preset) {
        if (preset > 0) {
            valueFieldSet = preset;
            flagPreset = 1;
        } else {
            flagDirect = 1;
        }
    }

    public boolean isConnect() {
        return tcsFunc.isConnect();
    }

    public void setCommand(int value) {
        commandType = value;
        flagIssueCommand = 1;
    }

    public int getFlagCommand() {
        return flagIssueCommand;
    }

    public DeviceDataView getDeviceDataView() {
        return tcsDataView;
    }

    public void runTask() {
        new Thread(() -> {
            TCS_DELIVERY_STATE oldDeliveryState = TCS_DELIVERY_STATE.ERROR;
            boolean connectedFlag = false;
            boolean deliveryStarted = false;
            boolean endingNotified = false;
            int endingLoops = 0;

            while (tcsFunc.isConnect()) {
                tcsState = DeviceConnectState.CONNECTED;
                oldDeliveryState = tcsDeliveryState;

                if (!connectedFlag && onConnected != null) {
                    onConnected.run();
                    connectedFlag = true;
                }

                readField();
                updateDataView();

                if (oldDeliveryState != tcsDeliveryState
                        && tcsDeliveryState == TCS_DELIVERY_STATE.ACTIVE) {
                    deliveryStarted = true;
                    endingNotified = false;
                    endingLoops = 0;
                    if (onStartedDelivery != null) onStartedDelivery.run();
                }

                if (deliveryStarted) {
                    // Pha ENDING: thiết bị đã ngưng bơm nhưng còn chốt số/in vé, số liệu chưa chốt.
                    if (isEndingState(tcsDeliveryState)) {
                        holdDeliveryValues();
                        endingLoops++;
                        if (!endingNotified) {
                            endingNotified = true;
                            if (onEndingDelivery != null) onEndingDelivery.run();
                        }
                    }

                    // Pha END: chỉ khi thiết bị về IDLE (hoặc quá thời gian chờ) mới lấy số liệu cuối.
                    boolean ended = endingNotified
                            && (tcsDeliveryState == TCS_DELIVERY_STATE.IDLE
                                || endingLoops >= ENDING_TIMEOUT_LOOPS);
                    if (ended) {
                        restoreDeliveryValues();
                        updateDataView();
                        deliveryStarted = false;
                        endingNotified = false;
                        endingLoops = 0;
                        if (onStoppedDelivery != null) onStoppedDelivery.run();
                    }
                }

                if (onReceivedData != null) {
                    onReceivedData.run();
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag
                    break;
                }
            }

            tcsState = DeviceConnectState.DISCONNECTED;
            if (onDisconnected != null) {
                onDisconnected.run();
            }
        }).start();
    }

    /** Các trạng thái thuộc pha kết thúc (ENDING): đã ngưng bơm nhưng chưa chốt xong mẻ. */
    private boolean isEndingState(TCS_DELIVERY_STATE state) {
        return state == TCS_DELIVERY_STATE.STOPPED
                || state == TCS_DELIVERY_STATE.TCKT_PENDING
                || state == TCS_DELIVERY_STATE.PRINTING;
    }

    /** Giữ lại số liệu cuối trong pha ENDING (thiết bị có thể xoá màn hình khi về IDLE). */
    private void holdDeliveryValues() {
        if (GrossQty > 0) lastDeliveryGrossQty = GrossQty;
        if (GrossTotal_WM > 0) lastDeliveryGrossTotal = GrossTotal_WM;
        if (avgTemp != 0) lastDeliveryAvgTemp = avgTemp;
        if (ticketNumber > 0) lastDeliveryTicketNumber = ticketNumber;
    }

    private void restoreDeliveryValues() {
        if (GrossQty <= 0 && lastDeliveryGrossQty > 0) GrossQty = lastDeliveryGrossQty;
        if (GrossTotal_WM <= 0 && lastDeliveryGrossTotal > 0) GrossTotal_WM = lastDeliveryGrossTotal;
        if (avgTemp == 0 && lastDeliveryAvgTemp != 0) avgTemp = lastDeliveryAvgTemp;
        if (ticketNumber <= 0 && lastDeliveryTicketNumber > 0) ticketNumber = lastDeliveryTicketNumber;
    }

    public void readField() {
        readGrossDisplay();
        readGrossTotalWM();
        readAvgTemp();
        readDeliveryStatus();
        readTicketNumber();

        //readFlowRate();
        //readDateUL();
        //readTimeUL();
    }

    public void runCommand() {
        if (flagIssueCommand == 1) {
            flagIssueCommand = 2;

            switch (commandType) {
                case 0:
                    cmdBeginDel();
                    break;
                case 1:
                    cmdEndDel();
                    break;
                case 2:
                    cmdPauseDel();
                    break;
                case 3:
                    cmdResumeDel();
                    break;
            }

            flagIssueCommand = 3;
        }

        if (flagInternalCommand == 1) {
            flagInternalCommand = 2;
            cmdPrintInternalTicket();
            flagInternalCommand = 3;
        }
    }

    public void writeField() {
        if (valueFieldSet > 0 && flagPreset == 1) {
            flagPreset = 2;
            cmdCfgPresetDel(valueFieldSet); // set preset
            flagPreset = 3;
        } else {
            if (flagDirect == 1) {
                flagDirect = 2;
                cmdCfgDirectDel(); // set direct
                flagPreset = 3;
            }
        }
    }

    /**
     * Đọc số ticket của thiết bị (SYS_TICKETNR = 0x1D).
     *
     * <p>Giá trị là số nguyên 64 bit theo thứ tự byte lớn trước, khác với các trường số thực
     * (double) của những lệnh còn lại — xem {@link #extractULong(byte[])}.
     */
    public void readTicketNumber() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE40.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_SYS_TICKETNR;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    public void readGrossTotalWM() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE40.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_SYS_GROSSTOTAL;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());

        // Optionally log the data
        // textLog.writeToErrorLog("Received: " + display(dataRev), 1);

        processValue(cmd, 1, dataRev);
        // processValue(Lcp02Msg.GET_FIELD_DATA, 17, dataRev);
    }

    //------read--------
    private void cmdCfgDirectDel() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_CFGDIR;
        DlvType = TCS_DLV_TYPE.DIRECT;
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(ProdId);
        byte[] dataRev = tcsFunc.setField(cmd.getValue(), buffer.array());
        processValue(cmd, 1, dataRev);
    }

    private void cmdCfgPresetDel(double valuePreset) {
        byte[] tempValuePreset = ByteBuffer.allocate(8).putDouble(valuePreset).array();
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_CFGPRS;
        DlvType = TCS_DLV_TYPE.PRESET_GRS;

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8);
        buffer.put((byte) DlvType.ordinal());
        buffer.putInt(ProdId);
        buffer.put(tempValuePreset);

        byte[] dataRev = tcsFunc.setField(cmd.getValue(), buffer.array());
        processValue(cmd, 1, dataRev);
    }

    private void cmdBeginDel() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_BEGIN;
        byte[] dataRev = tcsFunc.setField(cmd.getValue(), new byte[] { (byte) DlvType.ordinal() });
        processValue(cmd, 1, dataRev);
    }

    private void cmdEndDel() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_END;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    private void cmdPrintInternalTicket() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_PRN_ITPRN;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    private void cmdPauseDel() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_PAUSE;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    private void cmdResumeDel() {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE20.getValue());
        TcsMsgCmd cmd = TcsMsgCmd.CMD_DEL_RESUME;
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    private void readGrossDisplay() {
        readFieldTemplate(TcsMsgCmd.CMD_DEL_GROSSDSP);
    }

    private void readFlowRate() {
        readFieldTemplate(TcsMsgCmd.CMD_DEL_FRT);
    }

    private void readAvgTemp() {
        readFieldTemplate(TcsMsgCmd.CMD_DEL_AVGTEMP);
    }

    private void readDeliveryStatus() {
        readFieldTemplate(TcsMsgCmd.CMD_DEL_STATE);
    }

    private void readProductId() {
        readFieldTemplate(TcsMsgCmd.CMD_DEL_PRODUCT);
    }

    private void readDateUL() {
        readFieldTemplate(TcsMsgCmd.CMD_SYS_DATE);
    }

    private void readTimeUL() {
        readFieldTemplate(TcsMsgCmd.CMD_SYS_TIME);
    }
    //-----end read----

    //------Process------
    private void readFieldTemplate(TcsMsgCmd cmd) {
        tcsFunc.setMsgStatus(TcsFlagMsgCmd.FLAG_TYPE40.getValue());
        byte[] dataRev = tcsFunc.getField(cmd.getValue());
        processValue(cmd, 1, dataRev);
    }

    private void processValue(TcsMsgCmd command, int type, byte[] dataRev) {
        if (dataRev != null && dataRev.length > 6) {
            countDisconnect = 0;
            int returnCode = dataRev[6];
            TcsMsgCmd tempCommand = command;
            //textLog.WriteToErrorLog("Cmd " + command, haveWriteLog);

            if (returnCode == 0) {
                double retvalue = 0;
                int err = 0, stt = 0;
                String strValue;
                short uValue;

                switch (tempCommand) {
                    case CMD_DEL_GROSSDSP:
                        retvalue = extractDouble(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        GrossQty = retvalue;
                        break;

                    case CMD_DEL_FRT:
                        retvalue = extractDouble(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        FlowRate = retvalue;
                        break;

                    case CMD_DEL_AVGTEMP:
                        retvalue = extractDouble(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        avgTemp = retvalue;
                        break;

                    case CMD_SYS_GROSSTOTAL:
                        retvalue = extractDouble(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        GrossTotal_WM = retvalue;
                        break;

                    case CMD_SYS_TICKETNR:
                        rc = dataRev[6];
                        stt = dataRev[7];
                        ticketNumber = extractULong(dataRev);
                        break;

                    case CMD_SYS_TIME:
                        strValue = extractString(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        timeUL = strValue;
                        break;

                    case CMD_SYS_DATE:
                        strValue = extractString(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        dateUL = strValue;
                        break;

                    case CMD_DEL_PRODUCT:
                        uValue = extractUShort(dataRev);
                        rc = dataRev[6];
                        stt = dataRev[7];
                        ProdId = uValue;
                        productId = uValue;
                        break;

                    default:
                        rc = dataRev[6];
                        stt = dataRev[7];
                        break;
                }

                this.rc = rc;
                this.devStatus = stt;
            }
        }
    }

    private double extractDouble(byte[] input) {
        byte[] bytes = new byte[8];
        System.arraycopy(input, 8, bytes, 0, 8);

        bytes = reversed(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getDouble();
    }

    /** Số nguyên 64 bit không dấu, byte lớn trước (giống Decode.U64 của bộ giám sát TCS). */
    private long extractULong(byte[] input) {
        if (input == null || input.length < 16) return 0;
        long value = 0;
        for (int i = 8; i < 16; i++) {
            value = (value << 8) | (input[i] & 0xFF);
        }
        return value;
    }

    private byte[] reversed(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[input.length - 1 - i];
        }
        return output;
    }

    private String extractString(byte[] input) {
        return new String(input, 8, input.length - 9, StandardCharsets.US_ASCII);
    }

    private short extractUShort(byte[] input) {
        return ByteBuffer.wrap(input, 8, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private void ProcessRcStatus(int rc, int[] out) {
        out[0] = (rc & 0xF0) >> 4;
        out[1] = rc & 0x0F;
    }

    private void updateDataView() {
        try {
            tcsDataView.setProductId(productId);
            tcsDataView.setConnectState(tcsState);
            tcsDataView.setRc(rc);
            tcsDataView.setDevStatus(devStatus);

            tcsDataView.setTicketNumber(ticketNumber);
            tcsDataView.setName(name);
            tcsDataView.setVersion(String.valueOf(version));

            tcsDataView.setGrossPreset(GrossPreset);
            tcsDataView.setGrossQty(GrossQty);
            tcsDataView.setGrossQtyRoundFromDouble(GrossQty);
            tcsDataView.setFlowRate(FlowRate);
            tcsDataView.setDelStatus(deliveryStatus);
            tcsDataView.setDelCode(deliveryCode);

            tcsDataView.setGrossTotal(GrossTotal_WM);
            tcsDataView.setGrossTotalRoundFromDouble(GrossTotal_WM);

            int[] out = new int[2];
            ProcessRcStatus(devStatus, out);
            SetSystemState(out[1]);
            SetDeliveryState(out[0]);

            tcsDataView.setDateUL(dateUL);
            tcsDataView.setTimeUL(timeUL);
            tcsDataView.setTemperature(avgTemp);

            tcsDataView.setTcsDeliveryState(tcsDeliveryState);
            tcsDataView.setTcsSystemState(tcsSystemState);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void SetSystemState(int tcsData) {
        tcsSystemState = TCS_SYSTEM_STATE.values()[tcsData];
    }

    private void SetDeliveryState(int tcsData) {
        tcsDeliveryState = TCS_DELIVERY_STATE.values()[tcsData];
        if (tcsDeliveryState == TCS_DELIVERY_STATE.TCKT_PENDING && flagInternalCommand >= 3) {
            flagInternalCommand = 1;
        }
    }
    //------End Process------
}