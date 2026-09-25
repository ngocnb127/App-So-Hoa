package com.megatech.fms.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liquidcontrols.lcr.iq.sdk.ConnectionOptions;
import com.liquidcontrols.lcr.iq.sdk.DeviceInfo;
import com.liquidcontrols.lcr.iq.sdk.FieldItem;
import com.liquidcontrols.lcr.iq.sdk.LCRCommunicationException;
import com.liquidcontrols.lcr.iq.sdk.LcrSdk;
import com.liquidcontrols.lcr.iq.sdk.RequestField;
import com.liquidcontrols.lcr.iq.sdk.ResponseField;
import com.liquidcontrols.lcr.iq.sdk.SDKDeviceException;
import com.liquidcontrols.lcr.iq.sdk.WiFiConnectionOptions;
import com.liquidcontrols.lcr.iq.sdk.interfaces.CommandListener;
import com.liquidcontrols.lcr.iq.sdk.lc.api.device.InternalEvent;
import com.liquidcontrols.lcr.iq.sdk.interfaces.DeviceCommunicationListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.DeviceConnectionListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.DeviceListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.DeviceStatusListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.FieldListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.NetworkConnectionListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.PrinterStatusListener;
import com.liquidcontrols.lcr.iq.sdk.interfaces.SwitchStateListener;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.FIELDS.FIELD_REQUEST_STATES;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.COMMAND_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.FIELD_WRITE_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMUNICATION_STATUS;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DELIVERY_CODE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DELIVERY_STATUS;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_MESSAGE_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_PRINTER_STATUS;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_SECURITY_LEVEL;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_SWITCH_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_THREAD_CONNECTION_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.PRINTING_STATE;
import com.liquidcontrols.lcr.iq.sdk.lc.api.network.NETWORK_TYPE;
import com.liquidcontrols.lcr.iq.sdk.utils.AsyncCallback;
import com.liquidcontrols.lcr.iq.sdk.utils.TimeSet;
import com.liquidcontrols.lcr.iq.sdk.utils.AIPLogger;
import com.megatech.fms.model.LCRDataModel;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;


public class LCRReader {


    private final boolean deviceError = true;
    private static boolean _dateFormatRequested = false;
    //private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
    private static String dateFormat = "dd/MM/yy HH:mm:ss";
    private static boolean isLCR600 = false;
    private boolean isRefuel = false;
    public boolean getConnected() {
        if (lcrSdk !=null)
        {
            return  lcrSdk.getDeviceConnectionState(getDeviceId()) == LCR_DEVICE_CONNECTION_STATE.CONNECTED;
        }
        else
            return false;
    }




    public LCRReader(Context ctx)
    {
        this(ctx, "192.168.1.30", 10001);

    }
    public LCRReader(Context ctx,String ipAddress)
    {
        this(ctx, ipAddress, 10001);


    }
    public LCRReader(Context ctx,String ipAddress, int port)
    {
        this.dataListener = null;
        // Đồng hồ dùng chung cho cả ứng dụng (xem create()), nên SDK phải gắn với Context của
        // ứng dụng chứ không phải của màn hình đầu tiên tạo ra nó — màn hình đó sẽ chết trước.
        Context app = ctx.getApplicationContext();
        this.context = app != null ? app : ctx;
        this.wifiIpAddress = ipAddress;
        this.wifiPort = port;
        initLCR();

    }

    private static LCRDataModel lastData;
    private static LCRReader _reader;
    private final String serialFieldName = "METERID";

    /**
     * Các trường đang CHỜ gửi yêu cầu đọc tới SDK. Chỉ đọc/ghi trên luồng giao diện — luồng
     * SDK dùng để gọi mọi listener — xem {@link #onMainThread}.
     */
    private final Set<String> pendingFields = new LinkedHashSet<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Chạy việc đăng ký trường trên luồng giao diện.
     *
     * <p>Callback của SDK được đẩy về main looper, còn các màn hình gọi {@code requestData()}
     * cả từ luồng Timer lẫn luồng nền. Hai phía cùng sửa một tập hợp không an toàn luồng thì
     * có lúc ném exception — và exception trong {@code TimerTask} giết luôn Timer, tức vòng
     * tự hỏi lại dữ liệu của màn tra nạp tắt im lặng.
     */
    private void onMainThread(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else mainHandler.post(task);
    }

    /** SDK đã khởi tạo xong (init trả về không lỗi). */
    private volatile boolean sdkReady = false;

    private boolean isSameAddress(String ip, int port) {
        return Objects.equals(ip == null ? null : ip.trim(),
                wifiIpAddress == null ? null : wifiIpAddress.trim())
                && wifiPort != null && wifiPort == port;
    }
    /**
     * Device connection listener
     */
    public DeviceConnectionListener deviceConnectionListener = new DeviceConnectionListener() {
        /**
         * Called when connection to LCR device is made with all relevant information.
         * @param deviceId        Device identification string
         * @param deviceInfo    Device information
         */
        @Override
        public void deviceOnConnect(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo) {

            // Set user interface for connected state
            //doUIActionsForDeviceConnected();

            String logText = "Device on CONNECTED : "
                    + deviceId
                    + " LCP SDK Address : "
                    + deviceInfo.getSdkAddress().toString()
                    + " LCP Device Address : "
                    + deviceInfo.getDeviceAddress().toString();

            logConnection(logText);
            onConnected();
            refreshStatusListeners();
        }

        /**
         * Called when device lost connection
         * @param deviceId        Device identification string
         * @param deviceInfo    Device information
         * @param cause            Reason for connection lost
         */
        @Override
        public void deviceOnDisconnect(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable Throwable cause) {

            // Set user interface for disconnected state
            //doUIActionsForDeviceDisconnected();

            // Log text about disconnecting
            String causeString = "unknown";
            if (cause != null) {
                causeString = cause.getLocalizedMessage();
            }
            logConnection("Device on DISCONNECTED : " + deviceId + " Cause : " + causeString);
            LCRConnectionListener connection = connectionListener;
            if (connection != null)
                connection.onDisconnected();
        }

        /**
         * Called when device connection enter in error state
         * @param deviceId        Device identification string
         * @param deviceInfo    Device information
         * @param cause            Cause of error
         */
        @Override
        public void deviceOnError(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable Throwable cause) {

            // Set user interface for error connected state
            //doUIActionsForDeviceError();

            String errorMsg = "";
            if (cause != null) {
                errorMsg = cause.getLocalizedMessage();
            }
            logConnection("Device on ERROR : " + deviceId + " Cause : " + errorMsg);
            LCRConnectionListener connection = connectionListener;
            if (connection != null)
                connection.onError();
        }


        /**
         * Notify any status change events
         * @param deviceId        Device identification string
         * @param deviceInfo    Device information
         * @param newValue        New State
         * @param oldValue        Old State
         */
        @Override
        public void deviceConnectionStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_DEVICE_CONNECTION_STATE newValue,
                @Nullable LCR_DEVICE_CONNECTION_STATE oldValue) {

            //textViewDeviceConnectionStateData.setText(objToStrWithNullCheck(newValue));

            onConnectionStateChanged(newValue);
            logConnection("Device connection state changed : " + oldValue + " -> " + newValue);
        }

        /**
         * Device network status changed
         * @param deviceId            Device identification string
         * @param deviceInfo        Device info
         * @param connectionOptions    Connection info
         * @param newValue            Network new State
         * @param oldValue            Network old state
         */
        @Override
        public void deviceNetworkStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable ConnectionOptions connectionOptions,
                @NonNull LCR_THREAD_CONNECTION_STATE newValue,
                @NonNull LCR_THREAD_CONNECTION_STATE oldValue) {

//            if(connectionOptions != null) {
//                if(connectionOptions instanceof BlueToothConnectionOptions) {
//                    //textViewNetworkTypeData.setText(R.string.text_network_type_bluetooth);
//                } else if(connectionOptions instanceof WiFiConnectionOptions) {
//                    //textViewNetworkTypeData.setText(R.string.text_network_type_wifi);
//                } else {
//                    //textViewNetworkTypeData.setText(R.string.text_network_type_unknown);
//                }
//            } else {
//                //textViewNetworkTypeData.setText(R.string.text_network_type_unknown);
//            }
            //textViewNetworkConnectionStateData.setText(newValue.toString());

            // CHỈ ghi vết, như demo chính hãng. Mạng rớt là việc SDK tự nối lại (5 rồi 10
            // giây); báo lỗi ra màn hình ở đây là bật đỏ cho một nhịp SDK đang tự lo được.
            // Mất kết nối THẬT đến qua deviceOnDisconnect / deviceOnError ở trên.
            logConnection("Network connection state changed : " + oldValue + " -> " + newValue);
        }
    };
    /**
     * Printer status monitoring
     */
    PrinterStatusListener printerStatusListener = new PrinterStatusListener() {
        /**
         * Event is activated when printer status is changed
         * @param DeviceID        Device Id
         * @param deviceInfo    Device info
         * @param statusCode    Printer status code
         * @param newValue        New state for current status code
         * @param oldValue        Old state for current status code
         */
        @Override
        public void onPrinterStatusChanged(
                @NonNull String DeviceID,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_PRINTER_STATUS statusCode,
                @Nullable Boolean newValue,
                @Nullable Boolean oldValue) {

            // Logging printer status codes and values
            //raiseError("Printer Status : " + statusCode + " " + oldValue + " -> " + newValue);
        }

        /**
         * onPrintStatusChanged Listener is activated when print status for print item is changed.
         * Sta
         *
         * @param DeviceId   Device identification
         * @param deviceInfo Device information
         * @param workId     Print work identification
         * @param newValue   Print item new status
         * @param oldValue   Print item old status
         */
        @Override
        public void onPrintStatusChanged(
                @NonNull String DeviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull String workId,
                @Nullable PRINTING_STATE newValue,
                @Nullable PRINTING_STATE oldValue) {

            // Logging printing status change
            //raiseError("Printing status : " + oldValue + " -> " + newValue);
        }

        /**
         * onPrintSuccess event is activated when print data is successfully send to LCR device.
         *
         * @param deviceId   Device identification
         * @param deviceInfo Device information
         * @param workId     Print work identification
         */
        @Override
        public void onPrintSuccess(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull String workId) {

            // Logging print success
            //raiseError("Printing success");
        }

        /**
         * onPrintFailed event is activated when sending print data to LCR device has failed
         *
         * @param deviceId   Device identification
         * @param deviceInfo Device information
         * @param workId     Print work identification
         * @param cause      Error cause (note! can be <code>null</code>)
         */
        @Override
        public void onPrintFailed(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull String workId,
                @Nullable Throwable cause) {

//            String strCause = "(null)";
//            if(cause != null && cause.getMessage() != null) {
//                strCause = String.format(
//                        Locale.getDefault(),
//                        "LCP Error : %s",
//                        cause.getMessage());
//            }
            // Logging print work failed
            //raiseError("Print failed : " + strCause);
        }
    };
    private LCR_SWITCH_STATE old_switch = null;
    /**
     * Device operation switch state listener
     */
    public SwitchStateListener switchStateListener = new SwitchStateListener() {
        /**
         * Event of Switch State changed
         *
         * @param deviceId   Device identification
         * @param deviceInfo Device info
         * @param newValue   New switch state
         * @param oldValue   Old switch state
         */
        @Override
        public void onSwitchStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_SWITCH_STATE newValue,
                @Nullable LCR_SWITCH_STATE oldValue) {

            // Make variables for show status (getString formatting don't allow null)
//            String newValueText = "(null)";
//            if(newValue != null) {
//                newValueText = newValue.toString();
//            }

            // Set switch state text

            raiseError("Switch state : " + oldValue + " -> " + newValue);

            if (newValue == LCR_SWITCH_STATE.SWITCH_RUN || newValue == LCR_SWITCH_STATE.SWITCH_BETWEEN) {
                if (oldValue == LCR_SWITCH_STATE.SWITCH_PRINT
                        || oldValue == LCR_SWITCH_STATE.SWITCH_SHIFT_PRINT
                        || old_switch == LCR_SWITCH_STATE.SWITCH_PRINT
                        || old_switch == LCR_SWITCH_STATE.SWITCH_SHIFT_PRINT)
                    if (!alreadyStarted && isRefuel)
                        sendCommand(LCR_COMMAND.RUN);
            }
            if (newValue != LCR_SWITCH_STATE.SWITCH_STOP)
                old_switch = newValue;
        }

    };
    private volatile boolean fieldAvail = false;
    /**
     * Listener to handle all Field operation events
     */
    public FieldListener fieldListener = new FieldListener() {

        /**
         * onFieldInfoChanged event is activated when device field list is available or
         * field list status is changed.
         * Field Data read request can be done only for items what is in field list.
         *
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device info
         * @param fields        List of available fields
         */
        @Override
        public void onFieldInfoChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable List<FieldItem> fields) {
            // Clear local list
            //availableLCRFields.clear();
            if (fields != null) {
                String logText = "Field info arrived : " + fields.size() + " fields";
                raiseError(logText);
                // Add all list items to local list
                //availableLCRFields.addAll(fields);
            }
            fieldAvail = true;
            lcrSdk.fieldToolsFindField(getDeviceId(), "DBMNODE", new AsyncCallback() {
                @Override
                public void onAsyncReturn(@Nullable Throwable throwable) {
                    isLCR600 = throwable == null;
                }
            });
            processFieldQueue();
        }

        /**
         * onFieldReadDataChanged event is activated when new data arrived in requested field
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device info
         * @param responseField    Reply/response field with data
         * @param requestField    Requested field info
         */
        @Override
        public void onFieldReadDataChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull ResponseField responseField,
                @NonNull RequestField requestField) {

            applyFieldValue(responseField, requestField, true);
        }

        /**
         * Xử lý một giá trị trường nhận từ thiết bị, dùng chung cho hai callback của SDK.
         *
         * <p>SDK phân biệt rõ hai sự kiện:
         * <ul>
         *   <li>{@code onFieldReadDataChanged} — chỉ khi giá trị THAY ĐỔI</li>
         *   <li>{@code onFieldDataRequestSuccess} — mỗi lần đọc thành công, KỂ CẢ khi không đổi</li>
         * </ul>
         *
         * <p>Trước đây app chỉ nghe sự kiện đầu. Điều đó đúng cho màn hình tra nạp (số nhảy
         * liên tục), nhưng sai cho màn hình in: lúc đó mẻ đã kết thúc, {@code GROSSQTY} đứng
         * yên nên sự kiện "thay đổi" không bao giờ phát, và màn hình in luôn nhận Gross = 0.
         *
         * @param fromChangeEvent true nếu đến từ sự kiện thay đổi. Chỉ nhánh này mới ghi log:
         *                        nhánh đọc định kỳ chạy mỗi giây cho từng trường, ghi log ở đó
         *                        sẽ làm phình tệp log mà không thêm thông tin gì.
         */
        private void applyFieldValue(
                @NonNull ResponseField responseField,
                @NonNull RequestField requestField,
                boolean fromChangeEvent) {

            // Temporary variables for unit information
            FieldItem responseFieldItem = responseField.getFieldItem();

            String responseFieldName = responseFieldItem.getFieldName();

            Locale locale = Locale.getDefault();
            NumberFormat numberFormat = NumberFormat.getInstance(locale);

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);


            // For not log items what is displayed in separated fields
            boolean showInLog = true;

            if (responseFieldName.equals(FIELD_CHANGE.SERIAL.toString())) {
                model.setSerialId(responseField.getNewValue());
                onDataChanged(model, FIELD_CHANGE.SERIAL);
            }
            if (responseFieldName.equals(FIELD_CHANGE.DATE_FORMAT.toString())) {
                try {
                    int dateF = numberFormat.parse(responseField.getNewValue()).intValue();
                    if (dateF == 1) {
                        dateFormat = "dd/MM/yy HH:mm:ss";
                    } else
                        dateFormat = "MM/dd/yy HH:mm:ss";
                } catch (Exception ex) {

                }
                onDataChanged(model, FIELD_CHANGE.DATE_FORMAT);
            }


            if (responseFieldName.equals(FIELD_CHANGE.GROSSQTY.toString())) {
                // Set logger off for this field
                //showInLog = true;
                // Format setText string
                if (dataListener != null) {
                    try {
                        double qty = numberFormat.parse(responseField.getNewValue()).doubleValue();
                        // Đọc + phân tích được là trường CÒN SỐNG, kể cả khi giá trị bị bộ lọc
                        // loại ngay sau đó: đầu mẻ Gross = 0 (chưa mở vòi) là bình thường.
                        markMeterRead(FIELD_CHANGE.GROSSQTY);
                        if (qty > 0  &&  Math.abs(qty-model.getGrossQty())<10000) {
                            model.setGrossQty(qty);
                            markMeterAccepted(FIELD_CHANGE.GROSSQTY);
                            onDataChanged(model, FIELD_CHANGE.GROSSQTY);
                        } else {
                            markMeterFiltered(FIELD_CHANGE.GROSSQTY);
                        }
                    } catch (ParseException e) {
                        markMeterFail(FIELD_CHANGE.GROSSQTY);
                    }
                }
            }

            if (responseFieldName.equals(FIELD_CHANGE.PREVIOUSGROSS.toString())) {
                // Set logger off for this field
                //showInLog = false;
                // Format setText string
                if (dataListener != null) {
                    try {
                        model.setStartMeterNumber(numberFormat.parse(responseField.getNewValue()).doubleValue());
                        onDataChanged(model, FIELD_CHANGE.PREVIOUSGROSS);
                    } catch (ParseException e) {
                    }
                }
            }
            if (responseFieldName.equals(FIELD_CHANGE.STARTTIME.toString())) {

                if (dataListener != null) {
                    try {
                        model.setStartTime(simpleDateFormat.parse(responseField.getNewValue()));
                        onDataChanged(model, FIELD_CHANGE.STARTTIME);
                    } catch (ParseException e) {
                    }
                }
            }
            if (responseFieldName.equals(FIELD_CHANGE.ENDTIME.toString())) {
                // Set logger off for this field
                //showInLog = false;
                if (dataListener != null) {
                    try {
                        model.setEndTime(simpleDateFormat.parse(responseField.getNewValue()));
                        onDataChanged(model, FIELD_CHANGE.ENDTIME);
                        // if state changed to END_DELIVERY before, call onStopped

                    } catch (ParseException e) {
                    }
                }
            }

            if (responseFieldName.equals(FIELD_CHANGE.TOTALIZER.toString())) {
                // Set logger off for this field
                //showInLog = false;
                // Format setText string
                if (dataListener != null) {
                    try {
                        double value = numberFormat.parse(responseField.getNewValue()).doubleValue();

                        markMeterRead(FIELD_CHANGE.TOTALIZER);
                        double dangGiu = model.getEndMeterNumber();
                        if (totalizerFilter.accept(dangGiu, value)) {
                            // Nhảy hơn 1000 mà vẫn được nhận nghĩa là vừa LẤY LẠI MỐC; ca đối
                            // soát sau này phải thấy được chỗ số tổng nhảy một phát.
                            if (totalizerFilter.justRebased(dangGiu, value))
                                raiseError(String.format(Locale.US,
                                        "Totalizer lay lai moc: %.0f -> %.0f", dangGiu, value));
                            model.setEndMeterNumber(value);
                            // Chỉ giá trị QUA được bộ lọc mới chứng minh số tổng còn theo kịp
                            // thiết bị; "đọc được" ở trên không đủ (xem MeterFieldHealth).
                            markMeterAccepted(FIELD_CHANGE.TOTALIZER);
                            onDataChanged(model, FIELD_CHANGE.TOTALIZER);
                        } else {
                            markMeterFiltered(FIELD_CHANGE.TOTALIZER);
                        }
                    } catch (ParseException e) {
                        markMeterFail(FIELD_CHANGE.TOTALIZER);
                    }

                }
            }
            if (responseFieldName.equals(FIELD_CHANGE.TEMPERATURE.toString())) {
                // Set logger off for this field
                //showInLog = false;
                // Format setText string
                if (dataListener != null) {
                    try {
                        model.setTemperature(numberFormat.parse(responseField.getNewValue()).doubleValue());
                        markMeterRead(FIELD_CHANGE.TEMPERATURE);
                        onDataChanged(model, FIELD_CHANGE.TEMPERATURE);
                    } catch (ParseException e) {
                        markMeterFail(FIELD_CHANGE.TEMPERATURE);
                    }
                }
            }
            if (responseFieldName.equals(FIELD_CHANGE.ANALOGPORTVALUELIVE.toString())) {

                if (dataListener != null) {
                    try {
                        model.setAnalogPortValueLive(numberFormat.parse(responseField.getNewValue()).doubleValue());
                        onDataChanged(model, FIELD_CHANGE.ANALOGPORTVALUELIVE);
                    } catch (ParseException e) {
                    }
                }
            }

            if (responseFieldName.equals(FIELD_CHANGE.SALENUMBER.toString())) {
                // Set logger off for this field
                //showInLog = false;
                // Format setText string
                if (dataListener != null) {
                    try {
                        String saleNum = responseField.getNewValue();
                        if (saleNum != null && !saleNum.isEmpty()) {
                            model.setSaleNumber(saleNum);
                            onDataChanged(model, FIELD_CHANGE.SALENUMBER);
                        }
                    } catch (Exception e) {
                        raiseError("Error parsing SALENUMBER: " + e.getLocalizedMessage());
                    }
                }
            }

            if (responseFieldName.equals(FIELD_CHANGE.TICKETNUMBER.toString())) {
                // Set logger off for this field
                //showInLog = false;
                // Format setText string
                if (dataListener != null) {
                    try {
                        String ticketNum = responseField.getNewValue();
                        markMeterRead(FIELD_CHANGE.TICKETNUMBER);
                        if (ticketNum != null && !ticketNum.isEmpty()) {
                            model.setTicketNumber(ticketNum);
                            onDataChanged(model, FIELD_CHANGE.TICKETNUMBER);
                        } else {
                            markMeterFiltered(FIELD_CHANGE.TICKETNUMBER);
                        }
                    } catch (Exception e) {
                        markMeterFail(FIELD_CHANGE.TICKETNUMBER);
                        raiseError("Error parsing TICKETNUMBER: " + e.getLocalizedMessage());
                    }
                }
            }

            if (responseFieldName.equals("DBMNODE")) {
                isLCR600 = true;
                // Chỉ gỡ một lần, ở nhánh thay đổi — nhánh đọc định kỳ chạy lại mỗi giây.
                if (fromChangeEvent)
                    removeFieldData(requestField.getItemToRequest());
            }
            if (responseFieldName.equals(FIELD_CHANGE.SALENUMBER.toString())) {
                showInLog = true; // Cho phép log field này
            }

            if (responseFieldName.equals(FIELD_CHANGE.TICKETNUMBER.toString())) {
                showInLog = true; // Cho phép log field này
            }
            if (showInLog && fromChangeEvent) {
                String logText = "Field data arrive : "
                        + responseField.getFieldItem().getFieldName()
                        + " - " + responseField.getOldValue()
                        + " -> "
                        + responseField.getNewValue();

                // Logging field data change event
                raiseError(logText);
            }
        }


        /**
         * Called when field data request run is success (data has received from LCR device)
         * (activated every time when data request has success, even received data values are same)
         * @param deviceId            Device Id
         * @param deviceInfo        Device info
         * @param responseField        Reply field with data
         * @param requestField        Requested field info
         */
        @Override
        public void onFieldDataRequestSuccess(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull ResponseField responseField,
                @NonNull RequestField requestField) {

            /*
             * NOTE!
             * This event return field data request values (ResponseField), even data has not change
             *
             * Đây là đường DUY NHẤT lấy được giá trị hiện tại của một trường đứng yên. Màn hình
             * in đọc số sau khi mẻ đã kết thúc nên phụ thuộc hoàn toàn vào nhánh này.
             */
            applyFieldValue(responseField, requestField, false);
        }

        /**
         * Called when field item data request is failed (Request data from LCR device is failed)
         * @param deviceId        Device Id
         * @param deviceInfo    Device info
         * @param requestField    Requested field info
         * @param cause            Error cause / message
         */
        @Override
        public void onFieldDataRequestFailed(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull RequestField requestField,
                @NonNull Throwable cause) {

            String logString;

            logString = String.format(Locale.getDefault(),
                    "Field data request failed %s\nCause : %s"
                    , requestField.getItemToRequest().getFieldName()
                    , cause.getLocalizedMessage());

            markMeterFail(requestField.getItemToRequest().getFieldName());

            // Write log text
            raiseError(logString);
            //request field again
            //requestFieldData(requestField.getItemToRequest());
        }

        /**
         * Called when field item data request {@link FIELD_REQUEST_STATES state} has changed
         * @param deviceId        Device Id
         * @param deviceInfo        Device info
         * @param requestField    Requested field info
         * @param newValue        New value of {@link FIELD_REQUEST_STATES}
         * @param oldValue        Old value of {@link FIELD_REQUEST_STATES}
         */
        @Override
        public void onFieldDataRequestStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull RequestField requestField,
                @NonNull FIELD_REQUEST_STATES newValue,
                @NonNull FIELD_REQUEST_STATES oldValue) {

            //raiseError( "Field data request state changed " + requestField.getItemToRequest().getFieldName() + " " + oldValue + " -> " + newValue) ;

        }

        /**
         * onFieldDataRequestAddSuccess event activated when new field data request add success
         * (activated only one time for each new field data request)
         * @param deviceId            Device Id
         * @param deviceInfo            Device info
         * @param requestField        Original requested field information
         * @param overWriteRequest    <code>true</code> if previous request from same field was replaced
         */
        @Override
        public void onFieldDataRequestAddSuccess(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull RequestField requestField,
                @NonNull Boolean overWriteRequest) {

            onFieldAddSucess(requestField);

            // Logging data request add success event
            raiseError("Field data request add success : " + requestField.getItemToRequest().getFieldName());
        }

        /**
         * Called when new field data request task add failed
         * (activated only one time for each new field data request)
         * @param deviceId        Device Id
         * @param deviceInfo        Device info
         * @param requestField    Original requested field information
         * @param cause            Cause of error as throwable
         */
        @Override
        public void onFieldDataRequestAddFailed(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull RequestField requestField,
                @NonNull Throwable cause) {

            markMeterFail(requestField.getItemToRequest().getFieldName());

            // Logging data request add failed
            raiseError("Field data request add failed : "
                    + requestField.getItemToRequest().getFieldName()
                    + "\nCause :"
                    + cause.getLocalizedMessage());

            // Xếp lại để thử ở lần nối/nhận danh sách trường kế tiếp — KHÔNG thử lại ngay (một
            // trường lỗi cố định sẽ thành vòng lặp) và KHÔNG chặn trường nào khác.
            pendingFields.add(requestField.getItemToRequest().getFieldName());
        }

        /**
         * onFieldDataRequestRemoved event activated when field data request is removed
         * (activate only one time)
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device info
         * @param requestField    Original requested field information
         * @param info            Information of remove (why removed)
         */
        @Override
        public void onFieldDataRequestRemoved(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull RequestField requestField,
                @NonNull String info) {

            raiseError("Field read data request removed : " + requestField.getItemToRequest().getFieldName() + " - " + info);
        }

        /**
         * onFieldWriteStatusChanged event is activated when write process status is changed
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device info
         * @param fieldItem        Field information
         * @param data            Data to write into field
         * @param newValue        New Status
         * @param oldValue        Old Status
         */
        @Override
        public void onFieldWriteStatusChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull FieldItem fieldItem,
                @NonNull String data,
                @Nullable FIELD_WRITE_STATE newValue,
                @Nullable FIELD_WRITE_STATE oldValue) {

            raiseError("Write field state : " + oldValue + " -> " + newValue);
        }

        /**
         * onFieldWriteSuccess activated when write process is success to LCR device.
         * (activate only one time for each write process)
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device Info
         * @param fieldItem        Field information
         * @param data            Data what is written to field
         */
        @Override
        public void onFieldWriteSuccess(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull FieldItem fieldItem,
                @NonNull String data) {

            raiseError("Field data write success : " + fieldItem.getFieldName());
        }

        /**
         * onFieldWriteFailed activated when field write process has failed
         * (activate only one time for each write process)
         * @param deviceId        Device Id for using multiple devices same time
         * @param deviceInfo    Device Info
         * @param fieldItem        Field info
         * @param data            Data what tried to write to field
         * @param cause            Cause of fail
         */
        @Override
        public void onFieldWriteFailed(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull FieldItem fieldItem,
                @NonNull String data,
                @Nullable Throwable cause) {

            String errorMsg = "(unknown)";
            if (cause != null) {
                errorMsg = cause.getLocalizedMessage();
            }
            raiseError("Field data write failed : " + fieldItem.getFieldName() + " Cause : " + errorMsg);
        }
    };
    /**
     * DeviceCommunicationListener report SDK communication information to LCR device
     */
    private final DeviceCommunicationListener deviceCommunicationListener = new DeviceCommunicationListener() {

        /**
         * Notify when message state is changed between SDK and LCR device
         * NOTE! This listener update very high frequency
         *
         * @param deviceId   Device identification string
         * @param deviceInfo Device info
         * @param newValue   New value
         * @param oldValue   Old value
         */
        @Override
        public void onMessageStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_MESSAGE_STATE newValue,
                @Nullable LCR_MESSAGE_STATE oldValue) {

        }

        /**
         * Notify when SDK has detect communication error with LCR device.
         * <p>
         * Also notify some important internal SDK errors (example. generated output message size errors).
         * <p>
         * About event trace :
         * - Event trace start when device object get run turn from SDK DeviceRunner Thread
         * - Event list actions is add in key points of SDK and LCR device communicating
         * - Event list is cleared when device object finnish run or onCommunicationStatus error listeners is notified
         * <p>
         * Event trace is not yet fully implemented inside SDK. More events will add recording later on.
         *
         * @param deviceId   Device identification string
         * @param deviceInfo Device Info
         * @param cause      Special type of exception
         */
        @Override
        public void onCommunicationStatusError(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCRCommunicationException cause) {
            // Như demo chính hãng: in chuỗi sự kiện dẫn tới lỗi. Đây là dấu vết DUY NHẤT nói
            // được vì sao đồng hồ không trả lời (timeout, retry, mã trả về LCP) — thiếu nó
            // thì "mất kết nối" ngoài hiện trường không có cách nào lần ra nguyên nhân.
            StringBuilder trace = new StringBuilder("Communication error from ")
                    .append(deviceId).append(" : ").append(cause.getLocalizedMessage());
            List<InternalEvent> events = cause.getEvents();
            if (events != null) {
                int lineNumber = 1;
                for (InternalEvent event : events)
                    trace.append("\n  ").append(lineNumber++).append(" - ")
                            .append(objToStrWithNullCheck(event == null ? null : event.toShortFormat()));
            }
            logConnection(trace.toString());
        }

        /**
         * Notify when SDK communication status with LCR device is changed
         * !! NOTE !!
         * Not full implemented inside SDK
         *
         * @param deviceId   Device identification string
         * @param deviceInfo Device info
         * @param newValue   New Value
         * @param oldValue   Old Value
         */
        @Override
        public void onCommunicationStatusChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_COMMUNICATION_STATUS newValue,
                @Nullable LCR_COMMUNICATION_STATUS oldValue) {

            // CHỈ ghi vết, như demo chính hãng. Một bản tin lỗi/timeout là việc SDK tự thử lại
            // (trạng thái RETRY); trước đây app bật đỏ "mất kết nối" ngay tại đây, rồi lại
            // "đã kết nối" khi bản tin kế tiếp thành công — dấu kết nối nhấp nháy trong khi
            // thiết bị chưa hề rời mạng. Mất kết nối THẬT đến qua deviceOnDisconnect /
            // deviceOnError. Chủ dự án chốt ngày 2026-09-14.
            logConnection("Communication status changed: " + oldValue + " -> " + newValue);
        }
    };

    private void onDeviceAdded(boolean failed) {
        LCRConnectionListener connection = connectionListener;
        if (connection != null)
            connection.onDeviceAdded(false);
        if (!failed)
            doConnectDevice();
    }
    private LCR_DEVICE_STATE current_device_state;
    private void onCommandError(LCR_COMMAND command) {
        LCRConnectionListener connection = connectionListener;
        if (connection != null)
            connection.onCommandError(command);
    }
    /**
     * Listener for most of Device status and state information
     */
    public DeviceStatusListener deviceStatusListener = new DeviceStatusListener() {

        /**
         * Event is activated when delivery active state is changed
         * @param deviceId                Device identification
         * @param deviceInfo            Device information
         * @param deliveryActiveState    Delivery active <code>true</code> delivery is active
         */
        @Override
        public void onDeliveryActiveStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull Boolean deliveryActiveState) {

            // Logging delivery active state
            raiseError("Delivery active state changed : " + deliveryActiveState);
        }

        /**
         * Event is activated when LCR Device {@link LCR_DEVICE_STATE state} is changed
         * @param deviceId        Device identification
         * @param deviceInfo    Device info
         * @param newValue        New device {@link LCR_DEVICE_STATE state}
         * @param oldValue        Old device {@link LCR_DEVICE_STATE state}
         */
        @Override
        public void onDeviceStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_DEVICE_STATE newValue,
                @Nullable LCR_DEVICE_STATE oldValue) {

            // Set device state text

            // Logging device state
            raiseError("Device state changed : " + oldValue + " -> " + newValue);

            //state changed from STATE_STOP to STATE_RUN
            current_device_state = newValue;
            if (newValue == LCR_DEVICE_STATE.STATE_RUN )
                onStarted();

            //State change to STATE_END_DELIVERY
            if (newValue == LCR_DEVICE_STATE.STATE_END_DELIVERY                   ) {
                isStopped = alreadyStarted;
                if (alreadyStarted)
                    onStopped();
            }

        }


        /**
         * Event is activated when one of {@link LCR_DELIVERY_CODE LCR_DELIVERY_CODE} status is changed.
         * Each delivery code has values of <code>null</code>, <code>true</code> or <code>false</code>
         * @param deviceId    Device identification
         * @param deviceInfo    Device info
         * @param code        Delivery code {@link LCR_DELIVERY_CODE}
         * @param newValue    New value
         * @param oldValue    Old value
         */
        @Override
        public void onDeliveryCodeChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_DELIVERY_CODE code,
                @Nullable Boolean newValue,
                @Nullable Boolean oldValue) {

            raiseError("Delivery code changed : " + code + " " + oldValue + " -> " + newValue);
            //Check out FLOW_ACTIVE event
            if (code.equals(LCR_DELIVERY_CODE.FLOW_ACTIVE)) {

            }
//Logging delivery code status


        }

        /**
         * Event is activated when one of {@link LCR_DELIVERY_STATUS LCR_DELIVERY_STATUS} code is changed.
         * Each delivery status code has values of <code>null</code>, <code>true</code> or <code>false</code>
         * @param deviceId        Device identification
         * @param deviceInfo    Device info
         * @param code            {@link LCR_DELIVERY_STATUS LCR_DELIVERY_STATUS} code
         * @param newValue        New Value
         * @param oldValue        Old Value
         */
        public void onDeliveryStatusChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_DELIVERY_STATUS code,
                @Nullable Boolean newValue,
                @Nullable Boolean oldValue) {

            // Logging delivery status codes
            raiseError("Delivery Status changed : " + code + " " + objToStrWithNullCheck(oldValue) + " -> " + objToStrWithNullCheck(newValue));
        }

        /**
         * Event is activated when one of security level {@link LCR_SECURITY_LEVEL code} value is changed.
         * @param deviceId        Device identification
         * @param deviceInfo    Device info
         * @param newValue        New value of current security level code
         * @param oldValue        Old value of current security level code
         */
        public void onSecurityLevelChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @Nullable LCR_SECURITY_LEVEL newValue,
                @Nullable LCR_SECURITY_LEVEL oldValue) {

            // Logging security level (old security level -> new security level)
            raiseError("Security level changed : " + " " + oldValue + " -> " + newValue);
        }
    };
    private final Context context;
    /**
     * Setup for WiFi connection
     */
    private final String wifiIpAddress;
    private final Integer wifiPort;

    public void requestSerial() {

        //FieldItem serial = findUserFieldByName(serialFieldName);
        requestFieldData(serialFieldName);
    }

    private void reset() {
        //initLCR();
        alreadyStarted = false;
        model = new LCRDataModel();
    }

    /**
     * Lấy đồng hồ dùng chung cho một màn hình tra nạp / in, và bắt đầu phiên đọc mới.
     *
     * <p>Cả ứng dụng chỉ có MỘT {@link LcrSdk}. SDK chạy trên một service dùng chung: mọi
     * {@code LcrSdk} cùng trỏ vào một thiết bị {@code "LCR.iQ"}, và {@code removeAllListeners}
     * xoá listener của CẢ service. Trước đây mỗi {@code renew = true} dựng thêm một SDK mà
     * không huỷ cái cũ (listener cũ vẫn chạy, {@code addDevice} lần hai bị SDK từ chối), còn
     * màn Cài đặt dựng riêng một cái rồi {@code destroy()} — gỡ luôn thiết bị và listener mà
     * màn tra nạp đang dùng. Demo chính hãng chỉ có một SDK, init một lần, huỷ một lần.
     *
     * @param renew true để thử khởi tạo lại SDK nếu lần trước thất bại (chỉ gọi từ chỗ chắc
     *              chắn đang foreground — xem {@link #init()}). KHÔNG còn dựng SDK mới.
     */
    public static LCRReader create(Context ctx, String ip, int port, boolean renew) {
        LCRReader reader = obtain(ctx, ip, port, renew);

        reader.reset();

        if (reader.getConnected())
            reader.onConnected();


        if (reader.isDeviceError()) {
            reader.doConnectDevice();
        }


        return reader;

    }

    /**
     * Đồng hồ dùng chung, KHÔNG mở phiên đọc mới — cho màn Cài đặt kiểm tra IP.
     *
     * <p>Khác {@link #create}: không gọi {@code reset()}, nên bấm "Kiểm tra" giữa lúc một mẻ
     * đang chạy không xoá trạng thái của mẻ đó.
     */
    public static LCRReader shared(Context ctx, String ip, int port) {
        return obtain(ctx, ip, port, true);
    }

    private static synchronized LCRReader obtain(Context ctx, String ip, int port, boolean retryInit) {
        // Đổi địa chỉ (người dùng vừa đổi IP ở màn Cài đặt) là lúc DUY NHẤT dựng lại SDK: thiết
        // bị đã thêm vào SDK mang theo địa chỉ cũ. Huỷ hẳn cái cũ trước, không để hai SDK sống.
        if (_reader != null && !_reader.isSameAddress(ip, port)) {
            Logger.appendLog("LCR", "Đổi địa chỉ đồng hồ " + _reader.wifiIpAddress + ":"
                    + _reader.wifiPort + " -> " + ip + ":" + port + " — dựng lại kết nối");
            _reader.destroy();
            _reader = null;
        }
        if (_reader == null) {
            _reader = new LCRReader(ctx, ip, port);
        } else if (retryInit && !_reader.sdkReady) {
            // Lần khởi tạo trước bị Android từ chối (service lúc tiến trình ở background):
            // thử lại trên CHÍNH SDK đó, không dựng SDK thứ hai.
            _reader.init();
        }
        return _reader;
    }

    public boolean isDeviceError() {
        LCR_DEVICE_CONNECTION_STATE state = lcrSdk.getDeviceConnectionState(getDeviceId());
        return state == LCR_DEVICE_CONNECTION_STATE.ERROR
                || state == LCR_DEVICE_CONNECTION_STATE.DISCONNECTED;
    }

    /** Gửi yêu cầu đọc cho mọi trường đang chờ. Xem {@link #drainPendingFields()}. */
    private void processFieldQueue() {
        onMainThread(this::drainPendingFields);
    }

    /**
     * Gửi yêu cầu đọc cho MỌI trường đang chờ, mỗi trường độc lập — đúng cách demo chính hãng
     * làm; SDK tự xếp hàng các yêu cầu bên trong.
     *
     * <p>Trước đây app tự dựng một hàng đợi NỐI TIẾP: trường sau chỉ được gửi khi trường trước
     * báo {@code onFieldDataRequestAddSuccess}. Một trường đăng ký thất bại thì nằm lì ở đầu
     * hàng và mọi trường phía sau không bao giờ được gửi — đúng cái ca "SALENUMBER trống suốt
     * mẻ" mà màn tra nạp phải vá bằng cách hỏi lại mỗi phút (SALENUMBER xếp SAU sáu trường của
     * {@code requestData()}). Hàng đợi đó còn gửi lại trường đầu hàng mỗi lần được gọi, và SDK
     * hiểu mỗi lần như một yêu cầu ghi đè: xoá yêu cầu cũ, đếm lại chu kỳ từ đầu.
     */
    private void drainPendingFields() {
        if (!fieldAvail || lcrSdk == null || pendingFields.isEmpty()) return;
        List<String> names = new ArrayList<>(pendingFields);
        pendingFields.clear();
        for (String fieldName : names)
            requestFieldByName(fieldName);
    }

    private void requestFieldByName(final String fieldName) {
        FieldItem field;
        try {
            field = lcrSdk.fieldToolsFindField(getDeviceId(), fieldName, new AsyncCallback() {
                @Override
                public void onAsyncReturn(@Nullable Throwable throwable) {
                    if (throwable != null)
                        Logger.appendLog("LCR", "fieldToolsFindField " + fieldName + " failed: " + throwable.getLocalizedMessage());
                }
            });
        } catch (Exception ex) {
            // SDK chưa sẵn sàng: giữ lại, lần nối / nhận danh sách trường kế tiếp gửi lại.
            Logger.appendLog("LCR", "fieldToolsFindField " + fieldName + " lỗi: " + Logger.describe(ex));
            pendingFields.add(fieldName);
            return;
        }
        // Thiết bị không có trường này (đời máy khác): bỏ, như trước đây.
        if (field == null) return;

        if ("DBMNODE".equals(fieldName))
            isLCR600 = true;
        requestFieldData(field);
    }

    public boolean isLCR600() {
        return  isLCR600;
    }

    public interface LCRConnectionListener {
        void onConnected();

        void onError();

        void onDeviceAdded(boolean failed);

        void onDisconnected();

        void onCommandError(LCR_COMMAND command);

        void onConnectionStateChange(LCR_DEVICE_CONNECTION_STATE state);
    }

    public interface LCRStateListener {
        void onEndDelivery();

        void onStart();

        void onStop();
    }

    // volatile: màn hình gắn listener từ cả luồng nền, SDK gọi chúng trên luồng giao diện.
    private volatile LCRDataListener dataListener;

    public void setFieldDataListener(LCRDataListener listener) {
        this.dataListener = listener;
    }

    private volatile LCRConnectionListener connectionListener;

    public void setConnectionListener(LCRConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    private volatile LCRStateListener stateListener;

    public void setStateListener(LCRStateListener stateListener) {
        this.stateListener = stateListener;
    }

    /**
     * Gỡ listener CỦA CHÍNH màn hình gọi — chỉ khi nó còn là listener đang gắn.
     *
     * <p>Đồng hồ dùng chung giữa các màn hình, và Android gọi {@code onDestroy} của màn cũ
     * MUỘN, thường sau khi màn mới đã gắn listener của nó. {@code setXxxListener(null)} lúc đó
     * gỡ nhầm listener của màn mới: màn tra nạp đứng số mà dấu kết nối vẫn xanh.
     */
    public void removeListeners(LCRDataListener data, LCRConnectionListener connection,
                                LCRStateListener state) {
        synchronized (this) {
            if (data != null && dataListener == data) dataListener = null;
            if (connection != null && connectionListener == connection) connectionListener = null;
            if (state != null && stateListener == state) stateListener = null;
        }
    }

    public LCRDataListener getFieldDataListener() {
        return dataListener;
    }

    public LCRConnectionListener getConnectionListener() {
        return connectionListener;
    }

    /**
     * Trả listener về như trước khi một màn hình MƯỢN tạm — chỉ khi listener đang gắn vẫn là
     * cái đã mượn. Dùng cho nút Kiểm tra ở màn Cài đặt: màn tra nạp có thể đang nằm bên dưới,
     * mượn xong không trả là màn tra nạp mất số cho tới hết mẻ.
     */
    public void restoreListeners(LCRDataListener borrowedData, LCRDataListener previousData,
                                 LCRConnectionListener borrowedConnection,
                                 LCRConnectionListener previousConnection) {
        synchronized (this) {
            if (borrowedData != null && dataListener == borrowedData) dataListener = previousData;
            if (borrowedConnection != null && connectionListener == borrowedConnection)
                connectionListener = previousConnection;
        }
    }

    private LCRDataModel model = new LCRDataModel();

    /**
     * Bộ lọc nhiễu của số tổng.
     *
     * <p>Sống cùng {@code model} vì nó lọc theo mốc của chính {@code model}: hai thứ này mà
     * lệch vòng đời nhau thì bộ lọc so với một mốc không còn tồn tại.
     */
    private final TotalizerFilter totalizerFilter = new TotalizerFilter();

    private void onFieldAddSucess(RequestField requestField) {

        LCRDataListener data = dataListener;
        if (data != null)
            data.onFieldAddSucess(requestField.getItemToRequest().getFieldName());

    }

    public void stopRequestData() {
        /*
        grossQty = findUserFieldByName("GROSSQTY");
        grossMeter = findUserFieldByName("GROSSMETERQTY");
        endTime = findUserFieldByName("DELIVERYFINISH");
        startTime = findUserFieldByName("DELIVERYSTART");
        temp = findUserFieldByName("AVGTEMP");

        removeFieldData(grossQty);
        removeFieldData(grossMeter);
        removeFieldData(endTime);
        removeFieldData(startTime);
        removeFieldData(temp);
        */

    }

    private void onConnectionStateChanged(LCR_DEVICE_CONNECTION_STATE state) {
        raiseError("onConnectionStateChanged " + state);
        // Đổi trạng thái kết nối là ranh giới của một phiên đọc: chuỗi lệch đang dở dang
        // thuộc về phiên cũ, cộng dồn sang phiên mới sẽ lấy mốc sớm hơn thiết kế.
        totalizerFilter.reset();
        LCRConnectionListener connection = connectionListener;
        if (connection != null)
            connection.onConnectionStateChange( state);

    }

    private void initLCR(){
        if (lcrSdk!=null) {
            lcrSdk.disconnect(getDeviceId());
            try {
                lcrSdk.removeDevice(getDeviceId());
            }
            catch ( Exception ex)
            {

            }

        }
        lcrSdk = new LcrSdk(context);
        init();
    }

    /**
     * Huỷ hẳn SDK. Chỉ {@link #obtain} gọi, khi đổi địa chỉ đồng hồ — màn hình KHÔNG được tự
     * gọi: SDK và thiết bị là của chung cả ứng dụng, {@code removeAllListeners} xoá listener
     * của cả service.
     */
    void destroy() {
        sdkReady = false;

        if (lcrSdk != null) {
            doDisconnectDevice();
            try {
                lcrSdk.removeDevice(getDeviceId());
            } catch (Exception ex) {
            }
            // Remove device

            // Remove used listeners
            lcrSdk.removeAllListeners();

            // Request SDK perform quit actions
            lcrSdk.quit();

        }
    }

    public void restart() {

        destroy();
        lcrSdk = null;
        Timer tmrRestart = new Timer();
        tmrRestart.schedule(new TimerTask() {
            @Override
            public void run() {
                initLCR();
            }
        }, 1000*5);

    }

    public void requestDateFormat() {

        if (!_dateFormatRequested) {
            raiseError("Request DATEFORMAT & DBMNODE");
            //dateFormat = new SimpleDateFormat("DD/MM/YYYY HH:mm:ss");
            addRequestQueue("DATEFORMAT");
            //addRequestQueue("DBMNODE");
            processFieldQueue();
            _dateFormatRequested = true;
        }
    }

    // command methods
    public void start() {
        alreadyStarted = false;
        isStopped = false;

        sendCommand(LCR_COMMAND.RUN);
    }

    private void onConnected() {
        if (!isLCR600 && fieldAvail) {
            lcrSdk.fieldToolsFindField(getDeviceId(), "DBMNODE", new AsyncCallback() {
                @Override
                public void onAsyncReturn(@Nullable Throwable throwable) {
                    isLCR600 = throwable == null;
                }
            });
        }

        raiseError("onConnected");
        LCRConnectionListener connection = connectionListener;
        if (connection != null)
            connection.onConnected();
        if (current_device_state == LCR_DEVICE_STATE.STATE_RUN) {
            onStarted();
        }

        processFieldQueue();
    }

    /**
     * Lấy lại trạng thái thiết bị SDK đang giữ, ngay sau khi nối — như
     * {@code refreshStatusListeners()} của demo chính hãng.
     *
     * <p>SDK không nhân đôi listener đã gắn: gắn lại chỉ PHÁT LẠI giá trị mới nhất. Đồng hồ
     * đổi trạng thái trong lúc mất kết nối (ví dụ đã về END_DELIVERY) thì đây là đường để app
     * biết; không có nó app kẹt ở trạng thái cũ cho tới lần đổi kế tiếp.
     *
     * <p>CỐ Ý không phát lại {@code switchStateListener}: listener đó tự gửi lệnh RUN khi
     * công tắc rời vị trí PRINT. Phát lại một lần chuyển công tắc đã cũ là tự khởi động một
     * mẻ mới trên đồng hồ.
     */
    private void refreshStatusListeners() {
        if (lcrSdk == null) return;
        try {
            lcrSdk.addListener(deviceStatusListener);
        } catch (Exception ex) {
            logConnection("Không làm mới được trạng thái thiết bị: " + Logger.describe(ex));
        }
    }

    private boolean isStopped = false;
    private boolean isRestart = false;

    public void end() {
        end(false);
    }

    private void sendCommand(LCR_COMMAND command) {
        if (lcrSdk!=null) {
            //LCR_DEVICE_CONNECTION_STATE state = lcrSdk.getDeviceConnectionState(getDeviceId());
            //if (state == LCR_DEVICE_CONNECTION_STATE.CONNECTED)
            lcrSdk.addDeviceCommand(getDeviceId(), command);

            //else
            //{
            //    raiseError(context.getString(R.string.lcr_connection_error));
            //}
        }

    }

    public void requestData() {

       /* grossMeter = findUserFieldByName("GROSSMETERQTY");
        grossQty = findUserFieldByName("GROSSQTY");
        endTime = findUserFieldByName("DELIVERYFINISH");
        startTime = findUserFieldByName("DELIVERYSTART");
        temp = findUserFieldByName("AVGTEMP");

        requestFieldData(startTime);
        requestFieldData(grossMeter);
        requestFieldData(grossQty);
        requestFieldData(endTime);
        requestFieldData(temp);
        */
        raiseError("Request data");
        addRequestQueue("GROSSMETERQTY");
        addRequestQueue("GROSSQTY");
        addRequestQueue("DELIVERYFINISH");
        addRequestQueue("DELIVERYSTART");
        addRequestQueue("AVGTEMP");
        addRequestQueue("ANALOGPORTVALUELIVE");
        //addRequestQueue("PREVIOUSGROSS");

        if (fieldAvail)
            processFieldQueue();
    }

    private void removeFieldData(final FieldItem field) {
        if (field != null)
            lcrSdk.removeFieldDataRequest(getDeviceId(), field);

    }

    private void addRequestQueue(String fieldName) {
        onMainThread(() -> pendingFields.add(fieldName));
    }

    private void requestFieldData(String fieldName) {
        addRequestQueue(fieldName);
        processFieldQueue();
    }
    public void requestSaleNumberAndTicket() {
        raiseError("Request SALENUMBER and TICKETNUMBER");
        addRequestQueue("SALENUMBER");
        addRequestQueue("TICKETNUMBER");

        if (fieldAvail) {
            processFieldQueue();
        }
    }

    private enum CONNECTION_TYPE {
        BLUETOOTH,
        WIFI
    }

    private final String deviceId = "LCR.iQ";

    /** LCR device LCP protocol address */
    private final Integer lcpLCRAddress = 250;

    /** SDK LCP protocol address */
    private final Integer lcpSDKAddress = 20;

    /**
     * Chu kỳ hỏi các trường thay đổi liên tục trong lúc bơm.
     *
     * <p>Demo chính hãng của SDK dùng 1–5 giây. Trước đây chỗ này đặt
     * {@code TimeSet(2, MILLISECONDS)} — nhanh gấp 500 lần so với thiết kế, gần như chắc chắn
     * là nhầm đơn vị (ý định ban đầu là 2 giây). Với 9 trường, đó là ~4.500 yêu cầu mỗi giây
     * trên một kênh LCP: hàng đợi bão hoà, trường nào cũng phải chờ lượt, và mỗi lần giá trị
     * đổi lại kéo theo một lần ghi Room.
     */
    private static final TimeSet LIVE_FIELD_INTERVAL = new TimeSet(1, TimeUnit.SECONDS);

    /** Chu kỳ cho các trường định danh, không nằm trong vòng đời một mẻ. */
    private static final TimeSet STATIC_FIELD_INTERVAL = new TimeSet(5, TimeUnit.SECONDS);

    /**
     * Các trường thuộc VÒNG ĐỜI CỦA MỘT MẺ — đều phải ở chu kỳ nhanh.
     *
     * <p>Không chỉ gồm số liệu đang chạy. {@code DELIVERYFINISH} nhìn thì "tĩnh" nhưng
     * {@code RefuelDetailActivity.onStopped()} chờ đúng trường này để chốt mẻ: nó kiểm tra ở
     * giây thứ 2, 5, 8 rồi bỏ cuộc và bắt người dùng nhập tay. Đặt trường này ở chu kỳ 5 giây
     * là tự đẩy mình vào nhánh nhập tay. {@code DELIVERYSTART}, {@code SALENUMBER},
     * {@code TICKETNUMBER} cũng nằm trong đường chốt mẻ nên đi cùng nhóm.
     */
    private static final java.util.Set<String> LIVE_FIELDS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "GROSSQTY", "GROSSMETERQTY", "AVGTEMP", "ANALOGPORTVALUELIVE",
                    "DELIVERYSTART", "DELIVERYFINISH", "SALENUMBER", "TICKETNUMBER"));

    private static TimeSet intervalOf(FieldItem field) {
        return LIVE_FIELDS.contains(field.getFieldName())
                ? LIVE_FIELD_INTERVAL : STATIC_FIELD_INTERVAL;
    }

    private void requestFieldData(final FieldItem field) {
        if (field != null) {
            raiseError("Send request field " + field.getFieldName());
            lcrSdk.requestFieldData(
                    getDeviceId(),
                    new RequestField(
                            field,
                            intervalOf(field)),
                    new AsyncCallback() {
                        @Override
                        public void onAsyncReturn(@Nullable Throwable throwable) {
                            if (throwable != null) {
                                raiseError("SDK : Request command for field " + field.getFieldName() + " failed : " + throwable.getLocalizedMessage());
                                // SDK không nhận được yêu cầu: giữ lại cho lần nối kế tiếp,
                                // không chặn trường nào khác.
                                addRequestQueue(field.getFieldName());
                            } else {
                                raiseError("SDK : Request command for field " + field.getFieldName() + " success");
                            }
                        }
                    });
        }
    }

    public void pause()
    {
        //sendCommand(LCR_COMMAND.PAUSE);

    }

    private final CONNECTION_TYPE connectionToUse = CONNECTION_TYPE.WIFI;

    private  LcrSdk lcrSdk;

    private final List<FieldItem> availableLCRFields = new ArrayList<>();

    private void end(boolean restart) {
        isStopped = false;
        this.isRestart = restart;
        sendCommand(LCR_COMMAND.END_DELIVERY);
    }
    private void doAddDevice(String ipAddress){
        if(lcrSdk == null) {
            return;

        }

        ConnectionOptions connectionOptions = null;

        // Check what type of connection to use
        if(connectionToUse.equals(CONNECTION_TYPE.WIFI)) {
            connectionOptions = getWifiConnectionOptions(ipAddress);
        }
        // Set default text for device and network connection state

        try {

            lcrSdk.addDevice(
                    getDeviceInfo(),
                    connectionOptions);

            /* !! NOTE !! Device add will be confirmed in DeviceListener */

        } catch (Exception e) {
            // Device add request fail
            String strError = "Device add request failed : " + e.getLocalizedMessage();
            raiseError(strError);
        }
    }
    private void doAddDevice() {
        doAddDevice(wifiIpAddress);
    }

    private DeviceInfo getDeviceInfo() {
        // Making device info with device id
        DeviceInfo returnDeviceInfo = new DeviceInfo(getDeviceId());

        // Set Device LCP address
        returnDeviceInfo.setDeviceAddress(lcpLCRAddress);

        // Set SDK LCP address
        returnDeviceInfo.setSdkAddress(lcpSDKAddress);

        return returnDeviceInfo;
    }
    private WiFiConnectionOptions getWifiConnectionOptions(String lcrIpAddress) {
        return new WiFiConnectionOptions(
            // IP Address
            lcrIpAddress,
            // Port
            wifiPort);


    }
    private WiFiConnectionOptions getWifiConnectionOptions() {

        return new WiFiConnectionOptions(
                // IP Address
                wifiIpAddress,
                // Port
                wifiPort);
    }
    public void doDisconnectDevice() {
        // Check SDK object (if call this method after close object)
        if (lcrSdk == null) {
            return;
        }
        // Call SDK to make disconnect command for device
        lcrSdk.disconnect(getDeviceId());

    }

    /**
     * Request SDK to connect to device
     */
    public void doConnectDevice() {
        // Check SDK object (if call this method after close object)
        if(lcrSdk == null) {
            return;
        }
        // Call SDK to make connect
        lcrSdk.connect(getDeviceId());

    }

    private String getDeviceId() {
        return this.deviceId;
    }

    public LCR_DEVICE_STATE getDeviceState()
    {
        return  current_device_state;
    }

    private void onStarted()
    {
        raiseError("onStarted");
        //requestData();
        alreadyStarted = true;
        lastData = null;
        LCRStateListener state = stateListener;
        if (state != null)
            state.onStart();

    }

    private String objToStrWithNullCheck(@Nullable Object valueToCheck, @NonNull String valueIfNull) {
        if(valueToCheck != null && valueToCheck.toString() != null) {
            return valueToCheck.toString();
        }
        return valueIfNull;
    }
    private String objToStrWithNullCheck(@Nullable Object valueToCheck) {
        return this.objToStrWithNullCheck(valueToCheck, "(null)");
    }
    /** Device add / remove listener */
    public DeviceListener deviceListener = new DeviceListener() {

        /**
         * Called when device add operation success
         * @param deviceId	Device identification
         */
        @Override
        public void onDeviceAddSuccess(@NonNull String deviceId) {

            // Logging success
            logConnection("Add device success : " + deviceId);

            // Set user interface objects for device add success
            //doUIActionsForDeviceAddSuccess();
            onDeviceAdded(false);
        }

        /**
         * Called when device add operation failed
         * @param deviceId	Device identification
         * @param cause		Cause of error
         */
        @Override
        public void onDeviceAddFailed(@NonNull String deviceId, SDKDeviceException cause) {
            String strCause = "(null)";
            if(cause != null) {
                strCause = cause.getMessage();
            }
            // Logging add device error
            logConnection("Add device failed : " + strCause);
            onDeviceAdded(true);
        }

        /**
         * Called when device remove operation is success
         * @param deviceId	Device identification
         */
        @Override
        public void onDeviceRemoveSuccess(@NonNull String deviceId) {
            // Logging actions
            logConnection("Remove device success");

        }

        /**
         * Called when device remove operation is failed
         *
         * @param deviceId Device identification
         * @param cause    Cause of error
         */
        @Override
        public void onDeviceRemoveFailed(@NonNull String deviceId, SDKDeviceException cause) {
            String strCause = "(null)";
            if (cause != null) {
                strCause = cause.getMessage();
            }
            // Logging remove device error
            logConnection("Remove device failed : " + strCause);

        }
    };

    /**
     * Khởi tạo SDK đồng hồ.
     *
     * <p>Cái bẫy ở đây: {@code lcrSdk.init(...)} NHÌN như bất đồng bộ — nó nhận
     * {@link AsyncCallback} và callback có tham số {@code Throwable error}, nên ai đọc cũng
     * tưởng lỗi sẽ về theo đường đó. Không phải. Bên trong SDK, {@code init} chạy
     * {@code checkBound → ensureBound → bindService → startService} NGAY LẬP TỨC và ĐỒNG BỘ,
     * trước khi có bất kỳ callback nào.
     *
     * <p>Từ Android 12, khởi động service thường khi tiến trình đang ở background bị hệ thống
     * từ chối bằng {@code BackgroundServiceStartNotAllowedException}. Đo trên máy thật
     * 06-09-2026 (bản 119): exception bay thẳng ra khỏi lời gọi này, xuyên qua constructor
     * {@code LCRReader} tới {@code MainActivity.onCreate}, không một khối catch nào trên
     * đường đi. {@code ActivityThread} bọc lại thành "Unable to start activity" và giết tiến
     * trình — người dùng thấy app mở lên rồi tắt ngay.
     *
     * <p>Chưa nối được đồng hồ là chuyện chấp nhận được: lần mở màn hình sau
     * {@code LCRReader.create(..., renew = true)} gọi lại hàm này trên cùng SDK (cờ
     * {@link #sdkReady} còn false) nên tự có cơ hội thử lại.
     * Chết app thì không chấp nhận được.
     */
    private void init() {
        Log.e("D", "call init()");
        try {
            initSdk();
        } catch (Exception ex) {
            Logger.appendLog("LCR",
                    "Không khởi tạo được SDK đồng hồ: " + ex.getClass().getSimpleName()
                            + " - " + ex.getMessage());
            raiseError("Không khởi tạo được SDK đồng hồ: " + ex.getMessage());
        }
    }

    private void initSdk() {
        lcrSdk.init(new AsyncCallback() {
            @Override
            public void onAsyncReturn(@Nullable Throwable error) {
                // Throwable only has data if error occurred
                if (error != null) {
                    // Error at init
                    String strError = "ERROR INIT SDK : " + error.getLocalizedMessage();
                    logConnection(strError);

                } else {
                    sdkReady = true;
                    // Add listeners to receive data from SDK
                    addSDKListeners();
                    // Add device to communicate with
                    doAddDevice();
                }
            }
        });
    }

    private void addSDKListeners() {
        if (lcrSdk == null) {
            return;
        }
        // Device connection listener
        lcrSdk.addListener(deviceConnectionListener);
        // Field listener
        lcrSdk.addListener(fieldListener);
        // Command listener
        lcrSdk.addListener(commandListener);
        // Device status / state
        lcrSdk.addListener(deviceStatusListener);
        // Switch state listener
        lcrSdk.addListener(switchStateListener);
        // Printer status listener
        //lcrSdk.addListener(printerStatusListener);

        // ** New listeners **
        // Add device communication listener
        lcrSdk.addListener(deviceCommunicationListener);
        // Device add/remove listener
        lcrSdk.addListener(deviceListener);
        // Network status listener (for logging purposes)
        lcrSdk.addListener(networkConnectionListener);

    }

    private FieldItem findUserFieldByName(@NonNull String name) {
        for(FieldItem item : availableLCRFields) {
            //Log.d("FIELD",item.getFieldName());
            if(item.getFieldName().equals(name)) {
                return item;
            }
        }
        return null;
    }

    //END DELIVERY success and ENDTIME received
    private void onStopped() {
        raiseError("onStopped");
        isStopped = false;
        alreadyStarted = false;
        stopRequestData();
        LCRStateListener state = stateListener;
        if (state != null)
            state.onStop();


    }

    /** Device command listener */
    public CommandListener commandListener = new CommandListener() {
        /**
         * onCommandStateChanged listener is activated when ever command state is changed
         * @param deviceId		Device Id for using multiple devices same time
         * @param deviceInfo	Information about device
         * @param command		Command
         * @param newValue		New Value
         * @param oldValue		Old Value
         */
        @Override
        public void onCommandStateChanged(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_COMMAND command,
                @Nullable COMMAND_STATE newValue,
                @Nullable COMMAND_STATE oldValue) {

            raiseError("Command state : " + oldValue + " -> " + newValue);
        }

        /**
         * onCommandSuccess listener is activated when command is run SUCCESSFULLY
         * @param deviceId		Device Id for using multiple devices same time
         * @param deviceInfo	Information about device
         * @param command		Command
         */
        @Override
        public void onCommandSuccess(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_COMMAND command) {

            raiseError("Command success : " + command);

        }

        /**
         * onCommandFailed listener is activated when command is FAILED to run
         * @param deviceId		Device Id for using multiple devices same time
         * @param deviceInfo	Information about device
         * @param command		Command
         * @param cause			Cause for command fail (can be <code>null</code>)
         */
        @Override
        public void onCommandFailed(
                @NonNull String deviceId,
                @NonNull DeviceInfo deviceInfo,
                @NonNull LCR_COMMAND command,
                @Nullable Throwable cause) {

            String errorMsg = "(unknown)";
            if (cause != null) {
                errorMsg = cause.getLocalizedMessage();
            }
            raiseError("Command failed : " + command + " Cause : " + errorMsg);

            if (command == LCR_COMMAND.RUN || command == LCR_COMMAND.END_DELIVERY)
                onCommandError(command);
        }
    };

    private void onDataChanged(LCRDataModel model, FIELD_CHANGE field_change) {

        lastData = model;
        if (field_change == FIELD_CHANGE.DATE_FORMAT
                || field_change == FIELD_CHANGE.STARTTIME
                || field_change == FIELD_CHANGE.PREVIOUSGROSS
                || field_change == FIELD_CHANGE.SERIAL
                || field_change == FIELD_CHANGE.SALENUMBER        // ← THÊM DÒNG NÀY
                || field_change == FIELD_CHANGE.TICKETNUMBER      // ← THÊM DÒNG NÀY
        ) {
            removeFieldData(lcrSdk.fieldToolsFindField(getDeviceId(), field_change.toString()));
            // Trường vừa bị gỡ thì từ giờ KHÔNG còn số mới, và đó là đúng thiết kế. Không
            // báo cho phép đo biết thì 15 giây sau nó kết luận trường đã chết — đúng ca
            // TICKETNUMBER làm màn hình bôi cam giữa lúc đồng hồ vẫn chạy bình thường.
            markMeterRetired(field_change);
        }


        LCRDataListener data = dataListener;
        if (data != null)
            data.onDataChanged(model, field_change);
    }

    private boolean alreadyStarted = false;

    public boolean isAlreadyStarted() {
        return alreadyStarted;
    }
    public boolean isRunning() {
        return current_device_state == LCR_DEVICE_STATE.STATE_RUN;
    }
    public void setRefuel(boolean refuel)
    {
        isRefuel = refuel;
    }


    private void raiseError(String errorMsg) {
        Log.e("FMS", errorMsg);
        LCRDataListener listener = dataListener;
        if (listener != null)
            listener.onErrorMessage(errorMsg);

    }

    /**
     * Ghi vết KẾT NỐI thẳng vào nhật ký, không qua listener của màn hình.
     *
     * <p>{@link #raiseError} chỉ tới được nhật ký khi có màn hình đang nghe và màn hình đó chịu
     * ghi — màn chính và màn Cài đặt bỏ trống, nên mất kết nối lúc đứng ở đó không để lại dấu
     * vết nào. Kết nối là chuyện của cả ứng dụng, không của riêng màn hình nào.
     */
    private void logConnection(String message) {
        Log.e("FMS", message);
        Logger.appendLog("LCR", message);
    }

    /**
     * Ánh xạ trường của SDK sang trường được theo dõi sức khoẻ.
     *
     * @return null khi trường không nằm trong diện theo dõi — chỉ đo những trường mà người
     *         vận hành thực sự nhìn trên màn hình tra nạp.
     */
    private static MeterFieldHealth.Field healthField(FIELD_CHANGE field) {
        if (field == null) return null;
        switch (field) {
            case GROSSQTY: return MeterFieldHealth.Field.GROSSQTY;
            case TOTALIZER: return MeterFieldHealth.Field.TOTALIZER;
            case TEMPERATURE: return MeterFieldHealth.Field.TEMPERATURE;
            case TICKETNUMBER: return MeterFieldHealth.Field.TICKET;
            default: return null;
        }
    }

    private static MeterFieldHealth.Field healthField(String sdkFieldName) {
        if (sdkFieldName == null) return null;
        for (FIELD_CHANGE f : FIELD_CHANGE.values())
            if (sdkFieldName.equals(f.toString())) return healthField(f);
        return null;
    }

    /**
     * Mốc thời gian ĐƠN ĐIỆU. Đồng bộ NTP nhảy tiến vài phút sẽ làm mọi phép so theo thời
     * gian trôi mất tác dụng, nên tuyệt đối không dùng {@code System.currentTimeMillis()}.
     */
    private static long healthNow() {
        return SystemClock.elapsedRealtime();
    }

    private static void markMeterRead(FIELD_CHANGE field) {
        MeterFieldHealth.Field f = healthField(field);
        if (f != null) MeterFieldHealth.shared().markRead(f, healthNow());
    }

    /** Giá trị đã qua bộ lọc và được ghi vào mô hình. */
    private static void markMeterAccepted(FIELD_CHANGE field) {
        MeterFieldHealth.Field f = healthField(field);
        if (f != null) MeterFieldHealth.shared().markAccepted(f, healthNow());
    }

    /** Trường đã bị gỡ khỏi hàng đợi đọc: dừng đếm độ tươi, không kết luận là chết. */
    private static void markMeterRetired(FIELD_CHANGE field) {
        MeterFieldHealth.Field f = healthField(field);
        if (f != null) MeterFieldHealth.shared().markRetired(f);
    }

    private static void markMeterFiltered(FIELD_CHANGE field) {
        MeterFieldHealth.Field f = healthField(field);
        if (f != null) MeterFieldHealth.shared().markFiltered(f, healthNow());
    }

    private static void markMeterFail(FIELD_CHANGE field) {
        MeterFieldHealth.Field f = healthField(field);
        if (f != null) MeterFieldHealth.shared().markFail(f, healthNow());
    }

    private static void markMeterFail(String sdkFieldName) {
        MeterFieldHealth.Field f = healthField(sdkFieldName);
        if (f != null) MeterFieldHealth.shared().markFail(f, healthNow());
    }

    public  enum FIELD_CHANGE {

        TOTALIZER("GROSSMETERQTY"),
        PREVIOUSGROSS("PREVIOUSGROSS"),
        GROSSQTY("GROSSQTY"),
        TEMPERATURE("AVGTEMP"),
        STARTTIME("DELIVERYSTART"),
        ENDTIME("DELIVERYFINISH"),
        DATE_FORMAT("DATEFORMAT"),
        TIME_FORMAT("TIMEFORMAT"),
        SERIAL("METERID"),
        SALENUMBER("SALENUMBER"),
        TICKETNUMBER("TICKETNUMBER"),

        ANALOGPORTVALUELIVE ("ANALOGPORTVALUELIVE");
        private final String text;

        /**
         * @param text
         */
        FIELD_CHANGE(final String text) {
            this.text = text;
        }

        /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return text;
        }

        public boolean equals(String text)
        {
            return Objects.equals(this.text, text);
        }
    }

    public interface LCRDataListener {
        void onDataChanged(LCRDataModel dataModel, FIELD_CHANGE field_change);

        void onErrorMessage(String errorMsg);

        void onFieldAddSucess(String field_name);
        //void onFieldAvailable();
    }

    /** Monitor network connection states (for all devices) */
    public NetworkConnectionListener networkConnectionListener = new NetworkConnectionListener() {

        /**
         * Called when network connection state changed
         * @param networkType		Network type
         * @param connectionOptions	Connection options
         * @param attachedDevices	List of devices what is attach to network
         * @param newValue			New value
         * @param oldValue			Old Value
         */
        @Override
        public void onNetworkConnectionStateChange(
                @NonNull NETWORK_TYPE networkType,
                @NonNull ConnectionOptions connectionOptions,
                @NonNull List<DeviceInfo> attachedDevices,
                @Nullable LCR_THREAD_CONNECTION_STATE newValue,
                @Nullable LCR_THREAD_CONNECTION_STATE oldValue) {

            // Device level network status change is reported also in DeviceConnectionListener#deviceNetworkStateChanged

            logConnection("Network connection state change : " + oldValue + " -> " + newValue);
        }

        /**
         * Called when network connection is success
         * @param networkType		Network type
         * @param connectionOptions	Connection options
         * @param attachedDevices	Devices
         */
        @Override
        public void onNetworkConnected(
                @NonNull NETWORK_TYPE networkType,
                @NonNull ConnectionOptions connectionOptions,
                @NonNull List<DeviceInfo> attachedDevices) {

            // Logging network connect
            logConnection("Network connected : " + networkType.name());
        }

        /**
         * Called when network is disconnected
         * @param networkType		Network type
         * @param connectionOptions	Connection options
         * @param attachedDevices	Devices
         * @param cause				Cause of error
         */
        @Override
        public void onNetworkDisconnected(
                @NonNull NETWORK_TYPE networkType,
                @NonNull ConnectionOptions connectionOptions,
                @NonNull List<DeviceInfo> attachedDevices,
                @Nullable Throwable cause) {

            String strCause = "(null)";
            if(cause != null) {
                strCause = cause.getMessage();
            }
            // Logging network disconnecting
            logConnection("Network disconnected : " + networkType.name() + " : " + strCause);
        }

        /**
         * Called when network is on error (need user operations for recover)
         * @param networkType		Network type
         * @param connectionOptions	Connection options
         * @param attachedDevices	Devices
         * @param cause				Cause of error
         */
        @Override
        public void onNetworkError(
                @NonNull NETWORK_TYPE networkType,
                @NonNull ConnectionOptions connectionOptions,
                @NonNull List<DeviceInfo> attachedDevices,
                @Nullable Throwable cause) {

            String strCause = "(null)";
            if(cause != null) {
                strCause = cause.getMessage();
            }
            // Logging network error
            // CHỈ ghi vết, như demo chính hãng. Thiết bị hỏng theo mạng thì SDK báo tiếp qua
            // deviceOnError — đó mới là chỗ bật đỏ.
            logConnection("Network error : " + networkType.name() + " : " + strCause);
        }
    };
}
