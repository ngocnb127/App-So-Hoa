package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import com.liquidcontrols.lcr.iq.sdk.RequestField;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.FIELDS.FIELD_ITEMS;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;

import com.megatech.fms.databinding.ActivityRefuelDetailBinding;
import com.megatech.fms.databinding.EditRefuelDialogBinding;
import com.megatech.fms.databinding.SelectUserBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.LCRReader;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.LogEntryModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.sdk_tcs.sdk_tcs.IDevice;
import com.megatech.fms.sdk_tcs.sdk_tcs.model.DeviceDataView;
import com.megatech.fms.sdk_tcs.sdk_tcs.tcs.TcsDevice;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RefuelDetailActivity extends UserBaseActivity implements View.OnClickListener, OnBM2505SavedListener, UpdateSensitiveScreen {

    private List<AirlineModel> airlines = null;

    private List<TruckModel> trucks = null;

    private Button btnReconnect;
    private Button btnRestart;
    AlertDialog inputDlg;
    private Button btnBack;
    private final String LOG_TAG = "RFW";

    private enum CONNECTION_STATUS {
        /** Đang nhận số từ đồng hồ. */
        OK,
        ERROR,
        CONNECTING,
        /**
         * Đường truyền còn sống nhưng KHÔNG có số mới về.
         *
         * <p>Trước đây trạng thái này hiện dấu xanh y như đang chạy: đo trên máy thật 18-08,
         * app đứng ở 1832 trong khi đồng hồ đã lên 2155, mà người vận hành nhìn dấu xanh nên
         * tin là app đang bám đồng hồ. Dấu xanh nay chỉ có một nghĩa: ĐANG NHẬN SỐ.
         */
        STALE
    }
    IDevice tcsDevice;
    boolean checkTCS = true;

    private RefuelItemData mItem;

    /**
     * MỌI lần ghi phiếu của màn hình này đi qua đúng một luồng, theo thứ tự gọi.
     *
     * <p>Ca phải bảo đảm: đồng hồ chạy 398 → 399 → 400 rồi HỒI LƯU về 399, người dùng bấm
     * End. Giá trị chốt hợp lệ là 399 — số cuối, không phải số lớn nhất. Điều đó chỉ đúng
     * nếu lần ghi của End diễn ra SAU lần ghi 400. Trước đây các lần lưu nằm rải trên
     * {@code AsyncTask} (có thứ tự) lẫn {@code new Thread} thô (không có thứ tự), nên hai
     * nhóm đó có thể đảo nhau và một số đo trung gian ghi đè số chốt.
     *
     * <p>Hàng đợi một luồng cho toàn bộ vòng đời mẻ là cách hẹp nhất để có thứ tự đó. Tuyệt
     * đối KHÔNG giải quyết bằng luật "chỉ nhận số lớn nhất": hồi lưu là nghiệp vụ hợp lệ.
     */
    private final java.util.concurrent.ExecutorService saveExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "Refuel-Save"));
    private Activity activity;
    private final boolean isEditing = false;
    private final boolean restartRequest = false;
    ActivityRefuelDetailBinding binding;
    Timer tmrCheckData = new Timer();
    private Button btnForceStop;
    private Date lastErrorTime = null;
    private int endRetry = 0;

    private int checkDataRetryCount = 0;
    private static final int MAX_CHECK_DATA_RETRY = 20; // ~20 phút theo dõi, đủ cho phiên bơm dài
    /** Thời gian chờ tối đa để TCS chuyển từ ENDING sang END sau khi người dùng bấm dừng. */
    private static final long TCS_END_WAIT_TIMEOUT_MS = 35_000L;

    private String deviceSerial;
    private boolean askedApproachConfirm = false;
    private boolean approachPopupShown = false;
    private final Object startEventLock = new Object();
    private boolean startEventRecorded = false;

    private List<AirportsModel> airportslist;
    private List<TruckModel> truckList;


    private void setTextBoxValue(int id, Object value) {
        setTextBoxValue(id, value, "%s");
    }

    private void setTextBoxValue(int id, Object value, String pattern) {
        if (value != null)
            ((TextView) findViewById(id)).setText(String.format(pattern, value));
    }

    /**
     * Nối lại đồng hồ theo yêu cầu người dùng: ĐÓNG HẲN kết nối cũ rồi mới mở kết nối mới.
     *
     * <p>Bản trước chỉ gọi thẳng connect() lên đối tượng cũ. Với TCS, socket cũ còn treo nên
     * mỗi lần bấm lại sinh thêm một luồng đọc cùng bắn callback trên một thiết bị. Với LCR,
     * SDK vẫn giữ phiên hỏng nên lệnh nối chỉ lặp lại đúng cái hỏng đó.
     *
     * <p>Chạy nền: đóng socket và chờ luồng đọc thoát là việc chặn, làm trên luồng giao diện
     * sẽ treo màn hình đúng lúc người dùng đang cần nó nhất.
     *
     * <p>Hàm này KHÔNG ghi log trước đây — mà đúng nút này là thứ người dùng bấm khi đang
     * mất số liệu, nên không ai lần ra được nó đã chạy hay chưa.
     */
    private void reconnect() {
        setConnectionCheckmark(CONNECTION_STATUS.CONNECTING);
        TruckModel settingModel = currentApp.getSetting();
        if (settingModel == null) {
            Logger.appendLog(LOG_TAG, "Kết nối lại BỎ QUA: chưa có cấu hình xe");
            setConnectionCheckmark(CONNECTION_STATUS.ERROR);
            return;
        }

        final TruckModel.DEVICE_TYPE deviceType = settingModel.getDeviceType();
        Logger.appendLog(LOG_TAG, "Người dùng bấm kết nối lại - thiết bị " + deviceType);

        new Thread(() -> {
            try {
                if (deviceType == TruckModel.DEVICE_TYPE.TCS) {
                    if (tcsDevice == null) {
                        // Màn hình mở mà thiết bị chưa dựng: nút này không cứu được, phải đi
                        // lại đường khởi tạo. Im lặng ở đây là để người dùng bấm mãi không
                        // hiểu vì sao không có gì xảy ra.
                        Logger.appendLog(LOG_TAG, "Chưa có đối tượng TCS - khởi tạo lại đồng hồ");
                        runOnUiThread(this::initReaderSafely);
                        return;
                    }
                    Logger.appendLog(LOG_TAG, "Đóng kết nối TCS cũ");
                    tcsDevice.disConnect();
                    waitForDeviceToClose();
                    Logger.appendLog(LOG_TAG, "Mở kết nối TCS mới");
                    tcsDevice.connect();
                    // runTask tự chặn luồng thứ hai; luồng cũ đã thoát khi socket đóng.
                    tcsDevice.runTask();

                } else if (deviceType == TruckModel.DEVICE_TYPE.LCR) {
                    if (reader == null) {
                        Logger.appendLog(LOG_TAG, "Chưa có đối tượng LCR - khởi tạo lại đồng hồ");
                        runOnUiThread(this::initReaderSafely);
                        return;
                    }
                    Logger.appendLog(LOG_TAG, "Đóng kết nối LCR cũ");
                    reader.doDisconnectDevice();
                    waitForDeviceToClose();
                    Logger.appendLog(LOG_TAG, "Mở kết nối LCR mới");
                    reader.doConnectDevice();
                }
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Kết nối lại lỗi: " + describeException(ex));
                setConnectionCheckmark(CONNECTION_STATUS.ERROR);
            }
        }, "FMS-Meter-Reconnect").start();
    }

    /**
     * Chờ thiết bị đóng hẳn trước khi mở lại.
     *
     * <p>Mở kết nối mới khi socket cũ chưa đóng thì thiết bị từ chối, và người dùng nhận
     * đúng cái lỗi mà họ vừa bấm để chữa.
     */
    private static final long DEVICE_CLOSE_WAIT_MS = 1500L;

    private void waitForDeviceToClose() {
        try {
            Thread.sleep(DEVICE_CLOSE_WAIT_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void initReaderSafely() {
        try {
            initReader();
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "initReader lỗi: " + describeException(ex));
            setConnectionCheckmark(CONNECTION_STATUS.ERROR);
        }
    }

    private String m_Text = "";


    ///Reader define

    private LCRReader reader = null;
    private LCRDataModel model;

    // Field lưu SALENUMBER & TICKETNUMBER
    private String lcrSaleNumber = "";
    private String lcrTicketNumber = "";
    private boolean ticketNumberReceived = false;
    private boolean saleNumberReceived = false;

    static DeviceDataView tcsData;
    private boolean deviceIsReady = false;
    private boolean deviceIsError = false;
    private boolean conditionIsReady = false;
    private boolean inventoryIsReady = false;
    private boolean startButtonPress = false;
    private boolean started = false;

    private Button btnStart;
    private int field_data_flag = FIELD_DATA_FLAG.FIELD_NOT_DEFINED;
    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    String m_Title = "";
    private List<UserModel> userList;
    String storedIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.appendLog("RFW", "oncreatee");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refuel_detail);
        this.activity = this;
        started = false;
        isActive = true;
        Drawable drawable = getResources().getDrawable(R.drawable.ic_edit);
        drawable.setAlpha(90);

        btnReconnect = findViewById(R.id.btnReconnect);
        btnRestart = findViewById(R.id.btnRestart);
        btnForceStop = findViewById(R.id.btnForceStop);
        btnBack = findViewById(R.id.btnBack);
        btnStart = findViewById(R.id.btnStart);
        //btnTest = findViewById(R.id.btnTest);
        //btnTest.setVisibility(View.VISIBLE);
        storedIP = currentApp.getDeviceIP();
        //deviceType = currentApp.setThermalDeviceType();

        deviceSerial = currentApp.getSetting().getDeviceSerial();

        if (BuildConfig.FHS && btnForceStop != null)
            btnForceStop.setText(R.string.fhs_input_values);
        Logger.appendLog("Start refueling");


        loaddata();


    }

    @Override
    protected void onStart() {
        super.onStart();
       /* if (!NetworkHelper.isWifi(this))
            showWarningMessage(R.string.wifi_not_connected);*/
    }

    private void setConnectionCheckmark(CONNECTION_STATUS status) {
        TruckModel settingModel = currentApp.getSetting();
        runOnUiThread(() -> {
            switch (status) {
                case OK:
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr)).setChecked(true);
                    ((TextView) findViewById(R.id.lbl_connection_status))
                            .setText(getString(R.string.lcr_connection_ok));
                    ((TextView) findViewById(R.id.lbl_connection_status))
                            .setTextColor(getResources().getColor(R.color.colorDarkGreen, getTheme()));
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr))
                            .setCheckMarkDrawable(R.drawable.ic_checked_circle);
                    btnReconnect.setVisibility(View.INVISIBLE);
                    break;

                case CONNECTING:
                    CheckedTextView chkTxt = findViewById(R.id.refuel_detail_chk_connect_lcr);
                    if (chkTxt != null) chkTxt.setCheckMarkDrawable(null);
                    findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

                    if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                        ((TextView) findViewById(R.id.lbl_connection_status))
                                .setText("Đang kết nối thiết bị TCS");
                    } else {
                        ((TextView) findViewById(R.id.lbl_connection_status))
                                .setText(getString(R.string.lcr_connection_connecting));
                    }

                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(Color.BLACK);
                    btnReconnect.setVisibility(View.VISIBLE);
                    btnRestart.setVisibility(View.VISIBLE);
                    btnReconnect.setEnabled(false);
                    break;

                case STALE:
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr))
                            .setChecked(false);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr))
                            .setCheckMarkDrawable(R.drawable.ic_error);
                    ((TextView) findViewById(R.id.lbl_connection_status))
                            .setText(R.string.meter_data_stale);
                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(Color.RED);
                    btnReconnect.setVisibility(View.VISIBLE);
                    btnReconnect.setEnabled(true);
                    btnRestart.setVisibility(View.VISIBLE);
                    btnRestart.setEnabled(true);
                    break;

                case ERROR:
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                        ((TextView) findViewById(R.id.lbl_connection_status))
                                .setText("Lỗi kết nối thiết bị TCS");
                    } else {
                        ((TextView) findViewById(R.id.lbl_connection_status))
                                .setText(getString(R.string.lcr_connection_error));
                    }

                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(Color.RED);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr))
                            .setCheckMarkDrawable(R.drawable.ic_error);
                    btnReconnect.setVisibility(View.VISIBLE);
                    btnRestart.setVisibility(View.VISIBLE);
                    btnForceStop.setVisibility(View.VISIBLE);
                    btnReconnect.setEnabled(true);
                    btnRestart.setEnabled(true);
                    btnForceStop.setEnabled(true);
                    setEnableButton(started);
                    break;
            }
        });
    }


    private void showConfirmDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(id == R.id.btnStart ? R.string.start_confirm : R.string.stop_confirm);
        TruckModel settingModel = currentApp.getSetting();

        builder.setPositiveButton(getString(id == R.id.btnStart ? R.string.start : R.string.stop), (dialog, id12) -> {
            Logger.appendLog(id == R.id.btnStart ? "Confirm start " : "Confirm stop");
            if (id == R.id.btnStop) {
                if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                    stopTCS();
                } else {
                    stop();
                }
            } else if (id == R.id.btnStart) {
                start();
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.back), (dialog, id1) -> dialog.dismiss());
        builder.create().show();
    }


    private void loaddata() {

        setProgressDialog();
        new Thread(() -> {
            if (BuildConfig.FHS)
                trucks = DataHelper.getFHSTrucks();

            airlines = DataHelper.getAirlines();
            if (userList == null)
                userList = DataHelper.getUsers();

            // THÊM
            if (airportslist == null)
                airportslist = DataHelper.getAirports();

            if (truckList == null) {
                if (BuildConfig.FHS) {
                    truckList = DataHelper.getFHSTrucks();
                } else {
                    truckList = DataHelper.getTrucks();
                }
            }

            Bundle b = getIntent().getExtras();
            Integer id = b.getInt("REFUEL_ID", 0);
            Integer localId = b.getInt("REFUEL_LOCAL_ID", 0);
            Integer flightId = b.getInt("FLIGHT_ID", 0);
            String unique_id = b.getString("REFUEL_UNIQUE_ID", "");
            String mData = b.getString("REFUEL", "");
            Logger.appendLog("RFW", "Start loading Item : " + id + " - " + localId);
            RefuelItemData itemData = null;
            if (mData != null && !mData.isEmpty()) {
                itemData = RefuelItemData.fromJson(mData);
                com.megatech.fms.helpers.RefuelIntent.restoreBaseline(itemData, b);
            }
            if (itemData == null)
                itemData = DataHelper.getItemToRefuel(flightId);

            if (itemData == null)
                itemData = DataHelper.getRefuelItem(id, localId);


            mItem = itemData;


            if (mItem == null) {
                Logger.appendLog("RFW", "Loading data error, id: " + id + " unique id: " + unique_id);
                runOnUiThread(() -> {
                    showMessage(R.string.error_data, R.string.error_loading_item, R.drawable.ic_error, () -> {
                        finish();
                        return null;
                    });
                });
            } else {
                if ((mItem.getQualityNo() == null || mItem.getQualityNo().isEmpty()))
                    mItem.setQualityNo(currentApp.getQCNo());
                Logger.appendLog(LOG_TAG, "Flight Code: " + mItem.getFlightCode());
                runOnUiThread(() -> {
                    if (isFinishing()) return;

                    // Vẽ màn hình TRƯỚC, và tách hẳn khỏi việc kết nối đồng hồ.
                    //
                    // Trước đây hai việc nằm chung một lambda không có try/catch: bất kỳ lỗi nào
                    // trong initReader() cũng làm showData() không bao giờ chạy, và người dùng
                    // nhận một màn hình trắng không thao tác được — mất đồng hồ kéo theo mất
                    // luôn cả giao diện. Nay đồng hồ hỏng chỉ còn là hỏng phần đồng hồ.
                    try {
                        showData();
                    } catch (Exception ex) {
                        Logger.appendLog(LOG_TAG, "showData lỗi: " + describeException(ex));
                    }

                    try {
                        initReader();
                    } catch (Exception ex) {
                        Logger.appendLog(LOG_TAG, "initReader lỗi: " + describeException(ex));
                        setConnectionCheckmark(CONNECTION_STATUS.ERROR);
                    }
                });
            }

        }).start();


    }
    public REFUEL_STATUS statusStartTCS = REFUEL_STATUS.NONE;
    Runnable OnConnected = () -> {
        Log.d("TCS", "OnConnected");
        setConnectionCheckmark(CONNECTION_STATUS.CONNECTING);
        if (tcsDevice.isConnect()){
            setConnectionCheckmark(CONNECTION_STATUS.OK);
        }
        Logger.appendLog(LOG_TAG, "TCS đã kết nối");

        resumeDataFlowIfBatchRunning();
    };

    Runnable OnDisconnected= () -> {
        Log.d("TCS", "OnDisconnected");
        // Callback TCS trước đây chỉ Log.d nên không có gì trong log gửi về: mất kết nối
        // giữa mẻ là sự kiện quan trọng nhất của màn hình này mà lại không để lại dấu vết.
        Logger.appendLog(LOG_TAG, "TCS MẤT KẾT NỐI"
                + (started ? " GIỮA MẺ - số trên màn hình sẽ đứng im tới khi nối lại" : ""));
        refuel_status = REFUEL_STATUS.NONE;
        setRefuelStatus(REFUEL_STATUS.NONE);
        statusStartTCS = REFUEL_STATUS.NONE;
        deviceIsReady = false;
        deviceIsError = true;
        setConnectionCheckmark(CONNECTION_STATUS.ERROR);
    };

    Runnable OnStartedDelivery= () -> {
        Log.d("TCS", "OnStartedDelivery");
        recordStartEvent();
        statusStartTCS = REFUEL_STATUS.STARTED;
        refuel_status = REFUEL_STATUS.STARTED;
        setRefuelStatus(REFUEL_STATUS.STARTED);
    };

    /**
     * TCS kết thúc mẻ qua hai pha: ENDING (đã ngưng bơm, còn chốt số/in vé) rồi mới tới END.
     * Ở pha này chỉ cập nhật trạng thái màn hình, chưa chốt số liệu.
     */
    Runnable OnEndingDelivery = () -> {
        Log.d("TCS", "OnEndingDelivery");
        Logger.appendLog(LOG_TAG, "TCS đang kết thúc mẻ (ENDING) - chờ số liệu chốt");
        refuel_status = REFUEL_STATUS.ENDING;
        setRefuelStatus(REFUEL_STATUS.ENDING);
    };

    /** Thiết bị đã về END: số liệu đã chốt, lúc này mới lấy và hoàn tất mẻ. */
    Runnable OnStoppedDelivery= () -> {
        Log.d("TCS", "OnStoppedDelivery");

        // Lấy số liệu cuối cùng khi statusStartTCS vẫn còn STARTED để updateRefuelDataTCS ghi nhận.
        if (tcsDevice != null) {
            tcsData = tcsDevice.getDeviceDataView();
            updateRefuelDataTCS();
            applyTcsTicketNumber(tcsData);
            Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                    "TCS END - số liệu chốt: Gross=%.0f Total=%.0f Temp=%.1f",
                    tcsData.getGrossQtyRound(), tcsData.getGrossTotalRound(),
                    tcsData.getTemperature()));
        }

        refuel_status = REFUEL_STATUS.ENDED;
        setRefuelStatus(REFUEL_STATUS.ENDED);
        statusStartTCS = REFUEL_STATUS.ENDED;
        doStopTCS();
    };

    Runnable OnRecivedData = () -> {
        lastMeterDataAt = System.currentTimeMillis();
        if (statusStartTCS == REFUEL_STATUS.STARTED){
            tcsData = tcsDevice.getDeviceDataView();
            updateRefuelDataTCS();
            applyTcsTicketNumber(tcsData);
            Log.d("TCS", "Data: " + tcsData.getConnectState() + " - " + tcsData.getGrossQtyRound() + " - " + tcsData.getGrossTotalRound());
        }

    };

    /**
     * Số ticket của TCS đóng vai trò số bán hàng của mẻ, giống SALENUMBER của LCR.
     *
     * <p>Lệnh 0x1D (SYS_TICKETNR) trả về số ticket <b>kế tiếp</b>, không phải số của mẻ đang
     * chạy: đối chiếu thực tế ngày 05/08 cho thấy mẻ mang ticket 100735 thì thiết bị trả về
     * 100736. Bộ giám sát TCS bên C# cũng dùng {@code nextNr - 1} cho ticket vừa xong.
     *
     * <p>Chỉ ghi nhận một lần cho mỗi mẻ để số hiển thị không nhảy khi thiết bị tăng ticket.
     */
    private void applyTcsTicketNumber(DeviceDataView data) {
        if (data == null || saleNumberReceived) return;

        long nextTicket = data.getTicketNumber();
        if (nextTicket <= 1) return;

        long ticket = nextTicket - 1;
        lcrSaleNumber = String.valueOf(ticket);
        lcrTicketNumber = lcrSaleNumber;
        saleNumberReceived = true;
        ticketNumberReceived = true;

        // TCS chỉ có một số; lưu vào cả hai trường để phần in và phần đối chiếu dùng chung.
        if (mItem != null) {
            mItem.setSaleNumber(lcrSaleNumber);
            mItem.setTicketNumber(lcrTicketNumber);
        }
        Logger.appendLog(LOG_TAG, "TCS Ticket/Sale Number: " + lcrSaleNumber
                + " (thiết bị trả về next=" + nextTicket + ")");

        runOnUiThread(() -> {
            TextView tv = findViewById(R.id.txtDeliveryNumber);
            if (tv != null) {
                tv.setText(lcrSaleNumber);
                tv.setTextColor(getResources().getColor(R.color.colorDarkGreen, getTheme()));
            }
        });
    }

    /**
     * Mô tả ngoại lệ đủ để truy được nguyên nhân từ log của máy ngoài hiện trường.
     *
     * <p>{@code ex.getMessage()} một mình là không đủ: thông điệp của NPE chỉ cho biết
     * "gọi phương thức trên null", không cho biết DÒNG NÀO. Kèm vài khung stack đầu tiên
     * thuộc code của app là đủ để chỉ thẳng vị trí.
     */
    private static String describeException(Throwable ex) {
        if (ex == null) return "null";

        StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName())
                .append(": ").append(ex.getMessage());

        StackTraceElement[] stack = ex.getStackTrace();
        int printed = 0;
        for (StackTraceElement frame : stack) {
            if (!frame.getClassName().startsWith("com.megatech.fms")) continue;
            sb.append(" | ").append(frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1))
                    .append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
            if (++printed >= 4) break;
        }
        // Không có khung nào thuộc app (lỗi ném từ thư viện): lấy tạm khung trên cùng.
        if (printed == 0 && stack.length > 0)
            sb.append(" | ").append(stack[0]);

        return sb.toString();
    }

    private void initReader() {
        Logger.appendLog(LOG_TAG, "Init Reader");

        TruckModel settingModel = currentApp.getSetting();
        if (settingModel == null) {
            Logger.appendLog(LOG_TAG, "Init Reader BỎ QUA: chưa có cấu hình xe");
            setConnectionCheckmark(CONNECTION_STATUS.ERROR);
            return;
        }

        // Phiên đăng nhập hỏng (token hết hạn) làm currentUser về null. Trước đây chỗ này ném
        // NPE giữa chừng initReader, và vì nó nằm chung lambda với showData() nên hậu quả là
        // màn hình trắng. Nay báo rõ và đi tiếp phần còn lại.
        if (currentUser == null)
            Logger.appendLog(LOG_TAG, "Init Reader: currentUser null — phiên đăng nhập có thể đã hết hạn");

        if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.LCR) {
            reader = LCRReader.create(this, storedIP, 10001, false);
            if (reader.isLCR600()) btnStart.setVisibility(View.VISIBLE);
            addListeners();
            startDataFreshnessWatchdog();
            deviceIsReady = reader.getConnected();

            if (deviceIsReady) setConnectionCheckmark(CONNECTION_STATUS.OK);
            else reader.doConnectDevice();

            this.model = new LCRDataModel();
            if (currentUser != null)
                model.setUserId(currentUser.getUserId());

            if (reader.isAlreadyStarted()) onStarted();

        } else if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
            tcsDevice = new TcsDevice(storedIP, 10001, OnConnected, OnDisconnected,
                    OnRecivedData, OnStartedDelivery, OnEndingDelivery, OnStoppedDelivery);
            tcsDevice.connect();
            tcsDevice.runTask();

            addListenersTCS();
            startDataFreshnessWatchdog();
            deviceIsReady = tcsDevice.isConnect();

            if (deviceIsReady) setConnectionCheckmark(CONNECTION_STATUS.OK);
            else setConnectionCheckmark(CONNECTION_STATUS.CONNECTING);
        }
    }


    /**
     * Sau bao lâu không có số mới thì coi là số đã đứng.
     *
     * <p>Chu kỳ đọc của TCS là 500 ms, nên 6 giây là đã lỡ hơn mười vòng — đủ chắc chắn để
     * không báo động vì một nhịp mạng chậm, mà vẫn đủ nhanh để người vận hành biết trước
     * khi kết thúc mẻ.
     */
    private static final long METER_DATA_STALE_MS = 6000L;

    private Timer tmrDataFreshness;

    /**
     * Canh xem số của đồng hồ có còn chảy không.
     *
     * <p>Nối được KHÔNG có nghĩa là đang nhận số: đo trên máy thật 18-08, app đứng ở 1832
     * trong khi đồng hồ đã lên 2155, dấu kết nối vẫn xanh suốt. Người vận hành không có
     * cách nào biết. Vòng canh này là thứ duy nhất phát hiện được.
     */
    private void startDataFreshnessWatchdog() {
        if (tmrDataFreshness != null) tmrDataFreshness.cancel();
        tmrDataFreshness = new Timer("FMS-Meter-Freshness");
        tmrDataFreshness.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isActive) return;
                // Mất kết nối đã có đường báo riêng; ở đây chỉ xét ca CÒN kết nối mà im số.
                boolean connected = tcsDevice != null ? tcsDevice.isConnect()
                        : (reader != null && reader.getConnected());
                if (!connected) return;

                long last = lastMeterDataAt;
                if (last <= 0) return;                // chưa từng có số, chưa kết luận được

                boolean stale = System.currentTimeMillis() - last > METER_DATA_STALE_MS;
                if (stale != meterDataStale) {
                    meterDataStale = stale;
                    Logger.appendLog(LOG_TAG, stale
                            ? "Đồng hồ ngừng gửi số dù vẫn còn kết nối"
                            : "Đồng hồ gửi số trở lại");
                    setConnectionCheckmark(stale ? CONNECTION_STATUS.STALE : CONNECTION_STATUS.OK);
                }
            }
        }, METER_DATA_STALE_MS, 2000L);
    }

    private boolean meterDataStale = false;

    /**
     * Thời điểm màn hình nhận được số mới từ đồng hồ, dùng chung cho cả LCR và TCS.
     *
     * <p>Đặt ở đây chứ không ở lớp thiết bị vì hai SDK báo dữ liệu theo hai cách khác nhau,
     * còn câu hỏi cần trả lời thì chỉ có một: màn hình có còn nhận được số không.
     */
    private volatile long lastMeterDataAt = 0;

    /**
     * Mở lại luồng dữ liệu cho mẻ đang dở sau khi nối lại đồng hồ.
     *
     * <p>{@code OnDisconnected} đặt {@code statusStartTCS} về NONE, mà {@code OnRecivedData}
     * chỉ cập nhật khi nó đang là STARTED. Không khôi phục ở đây thì nối lại xong mọi gói
     * dữ liệu đều bị vứt: số trên màn hình đứng im vĩnh viễn trong khi dấu kết nối vẫn xanh.
     * Đo trên máy thật 18-08 — app 1832, đồng hồ 2155.
     */
    private void resumeDataFlowIfBatchRunning() {
        if (!started || refuel_status == REFUEL_STATUS.ENDED) return;
        statusStartTCS = REFUEL_STATUS.STARTED;
        deviceIsError = false;
        meterDataStale = false;
        Logger.appendLog(LOG_TAG, "Mẻ đang dở — nhận lại số liệu từ đồng hồ");
    }

    /**
     * Tải dầu của tàu bay: hiển thị số THỰC TẾ khi hãng đã báo, còn không thì số dự kiến.
     *
     * <p>Hai số này không bao giờ cùng có nghĩa một lúc: dự kiến chỉ là con số chờ, có thực
     * tế rồi thì dự kiến hết giá trị. Trước đây màn hình in cả hai cạnh nhau và không có một
     * dòng nào thực hiện nguyên tắc đó — người vận hành thấy hai số tải dầu khác nhau nằm
     * sát nhau, ngay cạnh cặp "sản lượng dự kiến / thực tế" vốn đã dễ nhầm.
     *
     * <p>Dùng CHUNG một ô nhãn và một ô giá trị, đổi nội dung thay vì ẩn bớt ô: ẩn ô làm số
     * nhảy sang cột khác tuỳ tình huống, và người đọc lướt sẽ lấy nhầm cột.
     */
    private void showCapacity() {
        TextView label = findViewById(R.id.lblCapacity);
        TextView value = findViewById(R.id.refuelitem_capacity);
        if (label == null || value == null) return;   // layout dọc không có hàng này

        boolean hasActual = mItem != null && mItem.hasActualCapacity();
        double amount = mItem == null ? 0 : mItem.getCapacityToShow();

        label.setText(hasActual ? R.string.actual_capacity : R.string.projected_capacity);
        value.setText(String.format(java.util.Locale.US, "%.0f", amount));
        // Đỏ chỉ dành cho số thực tế — đó là số ràng buộc với hãng.
        value.setTextColor(hasActual
                ? getResources().getColor(R.color.colorRed, getTheme())
                : getResources().getColor(android.R.color.black, getTheme()));
    }

    private void clearListeners() {
        if (reader != null) {
            reader.setConnectionListener(null);
            reader.setStateListener(null);
            reader.setFieldDataListener(null);
        }
    }

    private void showData() {

        setTextBoxValue(R.id.refuelitem_detail_aircraftCode, mItem.getAircraftCode());
        setTextBoxValue(R.id.refuelitem_detail_Density, mItem.getDensity(), "%.4f");
        setTextBoxValue(R.id.refuelitem_detail_flightCode, mItem.getFlightCode());
        setTextBoxValue(R.id.refuelitem_detail_parking, mItem.getParkingLot());
        setTextBoxValue(R.id.refuelitem_detail_qc_no, mItem.getQualityNo());
        setTextBoxValue(R.id.refuelitem_detail_estimateAmount, mItem.getEstimateAmount(), "%.0f");
        setTextBoxValue(R.id.refuelitem_detail_Temperature, mItem.getManualTemperature(), "%.2f");
//        setTextBoxValue(R.id.refuelitem_detail_realAmount, mItem.getRealAmount(), "%.0f");
        setTextBoxValue(R.id.refuelitem_detail_aircraftType, mItem.getAircraftType());
        setTextBoxValue(R.id.refuelitem_detail_driver, mItem.getDriverName());
        setTextBoxValue(R.id.refuelitem_detail_operator, mItem.getOperatorName());
        setTextBoxValue(R.id.refuelitem_detail_charter_name, mItem.getInvoiceNameCharter());
        setTextBoxValue(R.id.refuelitem_detail_Density, mItem.getDensity(), "%.4f");
        setTextBoxValue(R.id.refuelitem_detail_Temperature, mItem.getManualTemperature(), "%.2f");

        setTextBoxValue(R.id.txtEndMeter, mItem.getEndNumber(), "%.0f");
        setTextBoxValue(R.id.txtGrossQty, mItem.getRealAmount(), "%.0f");
        setTextBoxValue(R.id.txtTemp, mItem.getTemperature(), "%.2f");

        showCapacity();

        Button btnApproach = findViewById(R.id.btnApproach);
        TextView lblApproach = findViewById(R.id.lblApproachTime);

        if (mItem.getApproachTime() != null) {
            // Đã có thời gian tiếp cận → hiển thị label, ẩn nút
            lblApproach.setText("Đã Tiếp cận: " + DateUtils.formatDate(mItem.getApproachTime(), "dd/MM/yyyy HH:mm"));
            lblApproach.setVisibility(View.VISIBLE);
            btnApproach.setVisibility(View.GONE);
        } else {
            // Chưa có → hiển thị nút, ẩn label
            lblApproach.setVisibility(View.GONE);
            btnApproach.setVisibility(View.VISIBLE);
        }

        activity = this;
        if (mItem.isAlert()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.inventory_alert)
                    .setIcon(R.drawable.ic_warning)
                    .setPositiveButton(R.string.btn_continue, (dialog, which) -> dialog.dismiss())
                    .setNegativeButton(R.string.btn_stop, (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .create()
                    .show();
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        if (mItem != null) {


            ArrayAdapter<AirlineModel> spinnerAdapter = new ArrayAdapter<>(activity, R.layout.support_simple_spinner_dropdown_item, airlines);
            spinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);


            Spinner airline_spinner = findViewById(R.id.refuelitem_detail_airline_spinner);
            airline_spinner.setAdapter(spinnerAdapter);

            if (mItem.getAirlineId() > 0) {
                for (int i = 0; i < airline_spinner.getCount(); i++) {
                    AirlineModel item = (AirlineModel) airline_spinner.getItemAtPosition(i);
                    if (mItem.getAirlineId() == item.getId()) {
                        airline_spinner.setSelection(i);
                        //((TextView) findViewById(R.id.refuelitem_detail_airline)).setText(item.getName());
                        mItem.setAirlineId(item.getId());
                        mItem.setProductName(item.getProductName());
                        mItem.setAirlineModel(item);
                        if (mItem.isInternational() && item.isInternational())
                            mItem.setPrice(item.getPrice());
                        else
                            mItem.setPrice(item.getPrice01());
                        mItem.setUnit(item.getUnit());
                        mItem.setTaxRate(!mItem.isInternational() && item.isInternational() ? BuildConfig.TAX_RATE : 0);
                        mItem.setCurrency(item.getCurrency());
                        setTextBoxValue(R.id.refuelitem_detail_airline, item.getName());
                        break;
                    }
                }
            }
            airline_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    AirlineModel selected = (AirlineModel) parent.getItemAtPosition(position);
                    mItem.setAirlineId(selected.getId());
                    if (mItem.isInternational() && selected.isInternational())
                        mItem.setPrice(selected.getPrice());
                    else
                        mItem.setPrice(selected.getPrice01());
                    mItem.setCurrency(selected.getCurrency());
                    mItem.setUnit(selected.getUnit());
                    mItem.setTaxRate(!mItem.isInternational() && selected.isInternational() ? BuildConfig.TAX_RATE : 0);
                    mItem.setProductName(selected.getProductName());
                    mItem.setAirlineModel(selected);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            if (!Objects.equals(mItem.getArrivalTime(), new Date(Long.MIN_VALUE)))
                ((TextView) findViewById(R.id.refuelitem_detail_arrival)).setText(simpleDateFormat.format(mItem.getArrivalTime()));
            if (!Objects.equals(mItem.getDepartureTime(), new Date(Long.MIN_VALUE)))
                ((TextView) findViewById(R.id.refuelitem_detail_departure)).setText(simpleDateFormat.format(mItem.getDepartureTime()));

        }


        showApproachConfirmIfNeeded();



        closeProgressDialog();
    }

    private void start() {
        startButtonPress = true;

        // ✅ Chạy trên background thread để không block main thread
        new Thread(() -> {
            try {
                reader.start();  // Có thể chờ lâu trên background thread
                runOnUiThread(() -> {
                    Logger.appendLog(LOG_TAG, "Lệnh khởi động đã gửi thành công");
                });
            } catch (Exception e) {
                Logger.appendLog(LOG_TAG, "Lỗi: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(RefuelDetailActivity.this,
                            "Lỗi khởi động: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void stop() {
        if (deviceIsError) {
            showForceStopDialog();
        } else {
            // ✅ Chạy trên background thread
            new Thread(() -> {
                try {
                    reader.end();  // Có thể chờ lâu trên background thread
                    runOnUiThread(() -> {
                        setRefuelStatus(REFUEL_STATUS.ENDING);
                        Logger.appendLog(LOG_TAG, "Lệnh dừng đã gửi thành công");
                    });
                } catch (Exception e) {
                    Logger.appendLog(LOG_TAG, "Lỗi: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(RefuelDetailActivity.this,
                                "Lỗi dừng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        }
    }
    private void stopTCS() {
        if (deviceIsError) {
            showForceStopDialog();
        } else {
            // Không ngắt kết nối ngay: chờ thiết bị chuyển từ ENDING sang END rồi mới chốt số liệu
            // (OnStoppedDelivery). Nếu quá thời gian chờ thì tự chốt bằng số liệu đang có.
            refuel_status = REFUEL_STATUS.ENDING;
            setRefuelStatus(REFUEL_STATUS.ENDING);
            Logger.appendLog(LOG_TAG, "Người dùng dừng TCS - chờ thiết bị về trạng thái END");

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (statusStartTCS != REFUEL_STATUS.ENDED) {
                    Logger.appendLog(LOG_TAG, "Quá thời gian chờ END của TCS - chốt số liệu hiện có");
                    if (tcsDevice != null) {
                        tcsData = tcsDevice.getDeviceDataView();
                        updateRefuelDataTCS();
                        applyTcsTicketNumber(tcsData);
                    }
                    refuel_status = REFUEL_STATUS.ENDED;
                    setRefuelStatus(REFUEL_STATUS.ENDED);
                    statusStartTCS = REFUEL_STATUS.ENDED;
                    doStopTCS();
                }
            }, TCS_END_WAIT_TIMEOUT_MS);
        }

    }

    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }

    private void showEditDialog(final int id, int inputType, String pattern) {
        TextView view = findViewById(id);
        if (view != null)
            showEditDialog(view, inputType, pattern);
    }


    private void showEditDialog(final TextView view, int inputType, String pattern) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m_Title);
        this.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, m_Title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);
        input.setText(view.getText());
        this.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "Old value: " + view.getText().toString());

        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }


        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {

        });

        builder.setNegativeButton(R.string.back, (dialog, which) -> dialog.cancel());

        Context context = this;
        final AlertDialog dialog = builder.create();// builder.show();

        dialog.show();
        input.requestFocus();
        if (view.getId() == R.id.refuelitem_detail_Density)
            input.setSelection(2, input.getText().length());
        else
            input.setSelection(0, input.getText().length());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doUpdateResult())
                    dialog.dismiss();
            }

            private boolean doUpdateResult() {
                try {
                    m_Text = input.getText().toString();
                    saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "New value: " + m_Text);
                    Pattern regex = Pattern.compile(pattern);
                    Matcher matcher = regex.matcher(m_Text);
                    if (!matcher.find()) {
                        Toast.makeText(getBaseContext(), getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return updateDialogResult(view.getId(), m_Text);
                } catch (Exception ex) {
                    Toast.makeText(getBaseContext(), R.string.invalid_number_format, Toast.LENGTH_LONG).show();
                    return false;

                }

            }
        });
    }

    private boolean updateDialogResult(int id, String m_Text) {
        try {
            switch (id) {
                case R.id.refuelitem_detail_aircraftCode:
                    mItem.setAircraftCode(m_Text);
                    //((TextView)findViewById(R.id.lblAircraftNo)).setText(refuelData.getAircraftCode());
                    break;

                case R.id.refuelitem_detail_aircraftType:
                    mItem.setAircraftType(m_Text);
                    break;
                case R.id.refuelitem_detail_Density:
                    double d = numberFormat.parse(m_Text).doubleValue();
                    if (d < 0.72 || d > 0.86) {
                        showErrorMessage(R.string.error_data, R.string.invalid_density, R.drawable.ic_error);

                        return false;
                    } else
                        mItem.setDensity(d);
                    break;
                case R.id.refuelitem_detail_Temperature:
                    mItem.setManualTemperature(numberFormat.parse(m_Text).doubleValue());
                    mItem.setTemperature(numberFormat.parse(m_Text).doubleValue());
                    break;

                case R.id.refuelitem_detail_parking:
                    mItem.setParkingLot(m_Text);

                    break;
                case R.id.refuelitem_detail_qc_no:
                    mItem.setQualityNo(m_Text);

                    break;
                case R.id.refuelitem_detail_charter_name:
                    mItem.setInvoiceNameCharter(m_Text);

                    break;
            }
        } catch (ParseException ex) {
            Toast.makeText(getBaseContext(), R.string.invalid_number_format, Toast.LENGTH_LONG).show();
            return false;
        }

        updateBinding();

        return true;
    }

    private void updateBinding() {
        enqueueSave(false, RefuelDetailActivity.this::warnIfNotSaved);

        showData();

    }

    private void showSelectUser() {

        if (userList != null) {
            Dialog dialog = new Dialog(this);
            SelectUserBinding binding = DataBindingUtil.inflate(dialog.getLayoutInflater(), R.layout.select_user, null, false);
            binding.setRefuelItem(mItem);
            dialog.setContentView(binding.getRoot());
            Spinner spn = dialog.findViewById(R.id.select_user_driver);

            ArrayAdapter<UserModel> spinnerAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, userList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(mItem.getDriverId(), userList));

            spn = dialog.findViewById(R.id.select_user_operator);

            spn.setAdapter(spinnerAdapter);
            spn.setSelection(findUser(mItem.getOperatorId(), userList));

            dialog.show();

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

                    if (driver.getId() == operator.getId()) {
                        new AlertDialog.Builder(dialog.getContext())
                                .setTitle(R.string.select_user)
                                .setMessage(R.string.error_same_user)
                                .setIcon(R.drawable.ic_error)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                })
                                .show();
                        return;
                    }

                    mItem.setDriverId(driver.getId());
                    mItem.setDriverName(driver.getName());

                    mItem.setOperatorId(operator.getId());
                    mItem.setOperatorName(operator.getName());

                    updateBinding();
                    dialog.dismiss();
                }
            });

            dialog.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
    }

    private int findUser(int userId, List<UserModel> userList) {
        int pos = 0;
        for (UserModel item : userList) {
            if (item.getId() == userId)
                return pos;
            pos++;
        }
        return -1;
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        int id = v.getId();
        //Logger.appendLog("Click: " + v.toString());
        switch (id) {
            case R.id.btnCancel:
                showCancelDialog();
                break;
            case R.id.btnStart:
                showConfirmDialog(started ? R.id.btnStop : R.id.btnStart);
                break;
            //case R.id.refuel_detail_chk_connect_lcr:
            case R.id.btnReconnect:
                reconnect();
                break;
            case R.id.btnRestart:
                //reader.initLCR();
                if (started)
                    currentApp.saveCurrentRefuel(mItem.getId(), mItem.getLocalId());
                showRestart();
                break;
            case R.id.btnForceStop:
                //reader.requestData();
                showForceStopDialog();
                break;
            case R.id.btnAddBM:
                Log.d("ADD_BM", "Click btnAddBM, mItem = " + (mItem == null ? "null" : mItem.getId()));
                showAddBMMenu(v);
                break;



            case R.id.refuelitem_detail_aircraftType:
                m_Title = getString(R.string.update_aircraftType);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuelitem_detail_aircraftCode:
                m_Title = getString(R.string.update_aircraftCode);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuelitem_detail_Density:
            case R.id.refuel_confirm_Density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                break;
            case R.id.refuelitem_detail_Temperature:
            case R.id.refuel_confirm_Temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            //case R.id.refuelitem_detail_realAmount:
            case R.id.refuel_confirm_real_amount:
                m_Title = getString(R.string.update_real_amount);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_confirm_start_meter:
                m_Title = getString(R.string.update_start_meter);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuel_confirm_end_meter:
                m_Title = getString(R.string.update_end_meter);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.refuelitem_detail_qc_no:
            case R.id.refuel_confirm_qc_no:
                m_Title = getString(R.string.update_qc_no);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuelitem_detail_parking:
                m_Title = getString(R.string.update_parking_lot);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                break;
            case R.id.refuelitem_detail_charter_name:
                showConfirmMessage(R.string.change_charter_confirm, new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {

                        m_Title = getString(R.string.update_charter_name);
                        showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                        return null;
                    }
                });

                break;
            case R.id.refuelitem_detail_airline:
                //openAirlineSpinner();
                break;

            case R.id.refuel_detail_chk_condition:
            case R.id.refuel_detail_chk_inventory:


                CheckedTextView chkTxt = findViewById(id);

                boolean isChecked = chkTxt.isChecked();
                chkTxt.setChecked(!isChecked);
                chkTxt.setCheckMarkDrawable(isChecked ? R.drawable.ic_unchecked : R.drawable.ic_checked);
                if (id == R.id.refuel_detail_chk_condition)
                    conditionIsReady = !isChecked;
                else
                    inventoryIsReady = !isChecked;
                if (refuel_status == REFUEL_STATUS.NONE) {
                    setEnableButton(deviceIsReady && conditionIsReady && inventoryIsReady);
                }
                break;
            case R.id.btnBack:
                finish();
                break;
            case R.id.dialog_endtime:

                showEndTimePicker();

                break;

            case R.id.refuelitem_detail_driver:
            case R.id.refuelitem_detail_operator:
                showSelectUser();
                break;

            case R.id.btnApproach:
                // Ghi nhận thời gian tiếp cận
                Date now = new Date();
                mItem.setApproachTime(now);

                // Cập nhật hiển thị
                TextView lblApproach = findViewById(R.id.lblApproachTime);
                lblApproach.setText("Tiếp cận: " + DateUtils.formatDate(now, "dd/MM/yyyy HH:mm"));
                lblApproach.setVisibility(View.VISIBLE);

                // Ẩn nút sau khi bấm
                v.setVisibility(View.GONE);

                // Lưu dữ liệu lại local
                enqueueSave(false, RefuelDetailActivity.this::warnIfNotSaved);
                break;
        }

    }

    private void showCancelDialog() {

        if (refuel_status == REFUEL_STATUS.STARTED) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.accept)
                    .setMessage(R.string.cancel_refuel_message)
                    .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            cancelRefuel();
                        }
                    })
                    .setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .show();
        }
    }

    private void showAddBMMenu(View anchor) {
        Log.d("ADD_BM", "showAddBMMenu");

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_add_bm, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            Log.d("ADD_BM", "Click menu: " + item.getItemId());

            Bundle args = buildBmArgs();

            switch (item.getItemId()) {
                case R.id.menu_add_bm2508: {
                    Logger.appendLog("ADD_BM", "Show BM2508 DialogFragment");

                    B2508NewItemFragement frag = new B2508NewItemFragement();

                    Bundle bundle = new Bundle();
                    bundle.putInt("REFUEL_ID", mItem.getId());
                    bundle.putInt("REFUEL_LOCAL_ID", mItem.getLocalId());
                    bundle.putString("REFUEL_UNIQUE_ID", mItem.getUniqueId());

                    bundle.putInt("AIRPORT_ID", FMSApplication.getApplication().getUser().getAirportId());
                    bundle.putString("AIRPORT_NAME", FMSApplication.getApplication().getUser().getAirport());

                    bundle.putInt("TRUCK_ID", mItem.getTruckId());
                    bundle.putString("TRUCK_NO", mItem.getTruckNo());

                    bundle.putInt("FLIGHT_ID", mItem.getFlightId());
                    bundle.putString("FLIGHT_CODE", mItem.getFlightCode());
                    bundle.putString("FLIGHT_NO", mItem.getFlightCode());

                    bundle.putString("AIRCRAFT_CODE", mItem.getAircraftCode());
                    bundle.putString("AIRCRAFT_TYPE", mItem.getAircraftType());

                    bundle.putString("PARKING_LOT", mItem.getParkingLot());
                    bundle.putString("ROUTE_NAME", mItem.getRouteName());

                    bundle.putInt("AIRLINE_ID", mItem.getAirlineId());
                    bundle.putString("AIRLINE_NAME",
                            mItem.getAirlineModel() != null ? mItem.getAirlineModel().getCode() : "");

                    frag.setArguments(bundle);
                    frag.show(getSupportFragmentManager(), "BM2508_NEW");
                    return true;
                }
                case R.id.menu_add_bm2504: {
                    Logger.appendLog("ADD_BM", "Show BM2504 DialogFragment");
                    B2504NewItemFragment frag = new B2504NewItemFragment();

                    Bundle bundle = new Bundle();
                    bundle.putInt("REFUEL_ID", mItem.getId());
                    bundle.putInt("REFUEL_LOCAL_ID", mItem.getLocalId());
                    bundle.putString("REFUEL_UNIQUE_ID", mItem.getUniqueId());

                    bundle.putInt("AIRPORT_ID", FMSApplication.getApplication().getUser().getAirportId());
                    bundle.putString("AIRPORT_NAME", FMSApplication.getApplication().getUser().getAirport());

                    bundle.putInt("TRUCK_ID", mItem.getTruckId());
                    bundle.putString("TRUCK_NO", mItem.getTruckNo());

                    bundle.putInt("FLIGHT_ID", mItem.getFlightId());
                    bundle.putString("FLIGHT_CODE", mItem.getFlightCode());
                    bundle.putString("AIRCRAFT_CODE", mItem.getAircraftCode());
                    bundle.putInt("AIRLINE_ID", mItem.getAirlineId());
                    bundle.putString("AIRLINE_NAME",
                            mItem.getAirlineModel() != null ? mItem.getAirlineModel().getCode() : "");
                    bundle.putString("PARKING_LOT", mItem.getParkingLot());

                    frag.setArguments(bundle);
                    frag.show(getSupportFragmentManager(), "BM2504_NEW");
                    return true;
                }
                case R.id.menu_add_bm2503: {
                    Logger.appendLog("ADD_BM", "Show BM2503 DialogFragment");

                    B2503NewItemFragment frag = new B2503NewItemFragment();

                    Bundle bundle = new Bundle();
                    bundle.putInt("REFUEL_ID", mItem.getId());
                    bundle.putInt("REFUEL_LOCAL_ID", mItem.getLocalId());
                    bundle.putString("REFUEL_UNIQUE_ID", mItem.getUniqueId());

                    bundle.putInt("AIRPORT_ID", FMSApplication.getApplication().getUser().getAirportId());
                    bundle.putString("AIRPORT_NAME", FMSApplication.getApplication().getUser().getAirport());

                    bundle.putInt("TRUCK_ID", mItem.getTruckId());
                    bundle.putString("TRUCK_NO", mItem.getTruckNo());

                    bundle.putInt("FLIGHT_ID", mItem.getFlightId());
                    bundle.putString("FLIGHT_CODE", mItem.getFlightCode());

                    bundle.putString("AIRCRAFT_CODE", mItem.getAircraftCode());
                    bundle.putString("AIRCRAFT_TYPE", mItem.getAircraftType());
                    bundle.putString("ROUTE_NAME", mItem.getRouteName());
                    bundle.putString("PARKING_LOT", mItem.getParkingLot());

                    bundle.putInt("AIRLINE_ID", mItem.getAirlineId());
                    bundle.putString("AIRLINE_NAME",
                            mItem.getAirlineModel() != null ? mItem.getAirlineModel().getCode() : "");

                    frag.setArguments(bundle);
                    frag.show(getSupportFragmentManager(), "BM2503_NEW");
                    return true;
                }
                case R.id.menu_add_bm2505: {
                    Logger.appendLog("ADD_BM", "Show BM2505 DialogFragment");
                    // Fragment tự tải master data nên chỉ cần truyền ngữ cảnh của phiếu.
                    B2505NewItemFragement.newInstance(args)
                            .show(getSupportFragmentManager(), "BM2505_NEW");
                    return true;
                }
            }
            return false;
        });

        popup.show();
    }

    @Override
    public void onBM2505Saved(com.megatech.fms.model.BM2505Model model) {
        // Màn hình chi tiết tra nạp không hiển thị danh sách BM2505 nên chỉ ghi log.
        Logger.appendLog("ADD_BM", "BM2505 saved locally, localId=" + model.getLocalId());
    }

    private Bundle buildBmArgs() {
        Bundle args = new Bundle();

        if (mItem == null) return args;

        args.putInt("REFUEL_ID", mItem.getId());
        args.putInt("REFUEL_LOCAL_ID", mItem.getLocalId());
        args.putString("REFUEL_UNIQUE_ID", mItem.getUniqueId());

        args.putInt("TRUCK_ID", mItem.getTruckId());
        args.putString("TRUCK_NO", mItem.getTruckNo());

        args.putInt("AIRPORT_ID", FMSApplication.getApplication().getUser().getAirportId());
        args.putString("AIRPORT_NAME", FMSApplication.getApplication().getUser().getAirport()); // thêm

        args.putInt("FLIGHT_ID", mItem.getFlightId());
        args.putString("FLIGHT_CODE", mItem.getFlightCode());
        args.putString("FLIGHT_NO", mItem.getFlightCode()); // nếu model có field này

        args.putString("AIRCRAFT_CODE", mItem.getAircraftCode());
        args.putString("AIRCRAFT_TYPE", mItem.getAircraftType());

        args.putString("PARKING_LOT", mItem.getParkingLot());
        args.putString("ROUTE_NAME", mItem.getRouteName());

        args.putInt("AIRLINE_ID", mItem.getAirlineId());
        args.putString("AIRLINE_NAME",
                mItem.getAirlineModel() != null ? mItem.getAirlineModel().getCode() : "");

        args.putString("REFUEL_JSON", mItem.toJson());
        if (airportslist != null) {
            args.putSerializable("AIRPORT_LIST", new ArrayList<>(airportslist));
        }

        if (truckList != null) {
            args.putSerializable("TRUCK_LIST", new ArrayList<>(truckList));
        }

        return args;
    }
    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void openBM2508() {
        try {
            Log.d("ADD_BM", "openBM2508 start");

            Intent i = new Intent(this, B2508NewItemFragement.class);
            i.putExtra("REFUEL_ID", mItem.getId());
            startActivity(i);

        } catch (Exception e) {
            Log.e("ADD_BM", "openBM2508 ERROR", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openBM2504() {
        Intent i = new Intent(this, B2504NewItemFragment.class);
        i.putExtra("REFUEL_ID", mItem.getId());
        startActivity(i);
    }
    private void openBM2503() {
        Logger.appendLog("ADD_BM", "Show BM2503 DialogFragment");

        B2503NewItemFragment frag = new B2503NewItemFragment();

        frag.show(
                getSupportFragmentManager(),
                "BM2503_NEW"
        );
    }







    private boolean cancelled = false;

    private void cancelRefuel() {
        mItem.setStatus(REFUEL_ITEM_STATUS.NONE);
        TruckModel settingModel = currentApp.getSetting();

        if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS && tcsDevice != null) {
            tcsDevice.disConnect();
        }

        cancelled = true;
        if (mItem.getId() == 0 && mItem.getLocalId() == 0)
            finish();
        else
            postData();
    }

    private int mHour, mMinute, mYear, mMonth, mDay;
    private final Context context = this;

    private void showEndTimePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(mItem.getEndTime());
        TimePickerDialog timePicker = new TimePickerDialog(this, android.R.style.Theme_Material_Light_Dialog
                , new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {

                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                //mItem.setEndTime(cal.getTime());
                ((EditText) inputDlg.findViewById(R.id.dialog_endtime)).setText(DateUtils.formatDate(cal.getTime(), "HH:mm"));
            }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
        timePicker.show();

    }


    private void showForceStopDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(R.string.force_stop_confirm);

        builder.setPositiveButton(getString(R.string.stop), (dialog, id) -> {


            dialog.dismiss();
            showDataInput();
        });
        builder.setNegativeButton(getString(R.string.back), (dialog, id) -> {
            dialog.dismiss();

        });

        Dialog dlg = builder.create();
        dlg.show();
        ((TextView) dlg.findViewById(android.R.id.message)).setTextSize(18);
        ((TextView) dlg.findViewById(android.R.id.button1)).setTextSize(18);
        ((TextView) dlg.findViewById(android.R.id.button2)).setTextSize(18);

    }

    private void setButtonText(REFUEL_STATUS status) {
        int btnText = R.string.start;
        btnStart.setVisibility(View.GONE);
        switch (status) {
            case NONE:
                btnText = R.string.start;
                break;
            case STARTING:
                btnText = R.string.sending_start;
                break;
            case STARTED:
                btnText = R.string.stop;
                break;
            case ENDING:
                btnText = R.string.sending_stop;
                break;
            case ENDED:
                btnText = R.string.start;
                break;
            default:
                break;
        }
        btnStart.setText(btnText);
    }

    private void addListeners() {

        reader.setRefuel(true);
        reader.setConnectionListener(new LCRReader.LCRConnectionListener() {
            @Override
            public void onConnected() {
                Logger.appendLog(LOG_TAG, "connectionListener onConnected");
                deviceIsReady = true;
                deviceIsError = false;
                meterDataStale = false;
                lastMeterDataAt = 0;   // chờ gói thật đầu tiên rồi mới kết luận số có chảy
                setConnectionCheckmark(CONNECTION_STATUS.OK);
                setEnableButton(started || (deviceIsReady && conditionIsReady && inventoryIsReady));
                //reader.requestSerial();
                if (reader.isAlreadyStarted())
                    onStarted();
            }

            @Override
            public void onError() {
                Logger.appendLog(LOG_TAG, " onConnectionError ");

                deviceIsReady = false;
                deviceIsError = true;
                lastErrorTime = new Date();
                setConnectionCheckmark(CONNECTION_STATUS.ERROR);
            }

            @Override
            public void onDeviceAdded(boolean failed) {
                Logger.appendLog("RFW", "  onDeviceAdded - ");


            }

            @Override
            public void onDisconnected() {
                Logger.appendLog("RFW", "  onDisconnected - ");

                deviceIsReady = false;
                deviceIsError = true;
                setConnectionCheckmark(CONNECTION_STATUS.ERROR);


            }

            @Override
            public void onCommandError(LCR_COMMAND command) {
                Logger.appendLog("RFW", "  onCommandError - " + command.toString());

            }

            @Override
            public void onConnectionStateChange(LCR_DEVICE_CONNECTION_STATE state) {
                Logger.appendLog("RFW", "  onConnectionStateChange - " + state.toString());

                if (state == LCR_DEVICE_CONNECTION_STATE.CONNECTING_NETWORK
                        || state == LCR_DEVICE_CONNECTION_STATE.CONNECTING_DEVICE
                        || state == LCR_DEVICE_CONNECTION_STATE.RECONNECTING)
                    setConnectionCheckmark(CONNECTION_STATUS.CONNECTING);

                if (state == LCR_DEVICE_CONNECTION_STATE.DISCONNECTED)
                    deviceIsError = true;

            }

        });

        reader.setFieldDataListener(new LCRReader.LCRDataListener() {
            @Override
            public void onDataChanged(LCRDataModel dataModel, LCRReader.FIELD_CHANGE field_change) {
                lastMeterDataAt = System.currentTimeMillis();

                switch (field_change) {
                    case SERIAL:
                        if (!dataModel.getSerialId().equals(deviceSerial)) {
                            if (deviceSerial == null || deviceSerial.isEmpty()) {
                                TruckModel setting = currentApp.getSetting();
                                setting.setDeviceSerial(dataModel.getSerialId());
                                currentApp.saveSetting(setting, false);
                            } else {
                                new AlertDialog.Builder(activity)
                                        .setTitle(R.string.invalid_device)
                                        .setMessage(R.string.invalid_device_confirm)
                                        .setIcon(R.drawable.ic_error)
                                        .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                dialogInterface.dismiss();
                                            }
                                        })
                                        .setNegativeButton(R.string.cancel_refuel, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                cancelRefuel();
                                                dialogInterface.dismiss();
                                            }
                                        })
                                        .create()
                                        .show();
                            }
                        }
                        break;
                    case ENDTIME:

                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_ENDTIME;


                        break;

                    case STARTTIME:
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_STARTTIME;
                        if (started && !btnStart.isEnabled()) {

                            //Really started after STARTTIME field received
                            btnStart.setEnabled(true);

                            setRefuelStatus(REFUEL_STATUS.STARTED);
                        }

                        break;
                    case GROSSQTY:
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_GROSSQTY;


                        break;
                    case TOTALIZER:
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_TOTALLIZER;

                        break;
                    case TEMPERATURE:
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_TEMPERATURE;

                        break;

                    case TICKETNUMBER:
                        // Trước đây không có case này: LCRReader vẫn bắn TICKETNUMBER về nhưng
                        // Activity bỏ qua, nên ticketNumberReceived luôn false và phần đối chiếu
                        // "số đồng hồ kết thúc vs ticket" không bao giờ chạy.
                        if (dataModel.getTicketNumber() != null && !dataModel.getTicketNumber().isEmpty()) {
                            lcrTicketNumber = dataModel.getTicketNumber();
                            ticketNumberReceived = true;
                            if (mItem != null) mItem.setTicketNumber(lcrTicketNumber);
                            Logger.appendLog(LOG_TAG, "Ticket Number: " + lcrTicketNumber);
                        }
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_TICKETNUMBER;
                        break;

                    case SALENUMBER:
                        if (dataModel.getSaleNumber() != null && !dataModel.getSaleNumber().isEmpty()) {
                            lcrSaleNumber = dataModel.getSaleNumber();
                            saleNumberReceived = true;
                            mItem.setSaleNumber(lcrSaleNumber);
                            Logger.appendLog(LOG_TAG, "Sale Number: " + lcrSaleNumber);
                            runOnUiThread(() -> {
                                TextView tv = findViewById(R.id.txtDeliveryNumber);
                                if (tv != null) {
                                    tv.setText(lcrSaleNumber);
                                    tv.setTextColor(getResources().getColor(R.color.colorDarkGreen, getTheme()));
                                }
                            });
                        }
                        field_data_flag = field_data_flag | FIELD_DATA_FLAG.FIELD_SALENUMBER; // nếu có
                        break;


                }


                model = dataModel;
                if (field_change != LCRReader.FIELD_CHANGE.DATE_FORMAT &&
                        field_change != LCRReader.FIELD_CHANGE.SERIAL)
                    if (refuel_status == REFUEL_STATUS.STARTED)
                        updateRefuelData();

                if (inputDlg != null)
                    dialogBinding.invalidateAll();
            }

            @Override
            public void onErrorMessage(String errorMsg) {

                Logger.appendLog("LCR", errorMsg);
            }

            @Override
            public void onFieldAddSucess(String field_name) {

            }
        });

        reader.setStateListener(new LCRReader.LCRStateListener() {
            @Override
            public void onEndDelivery() {
                Logger.appendLog("RFW", "  onEndDelivery");
            }

            @Override
            public void onStart() {
                //reader.requestData();
                //setEnableButton(false);
                Logger.appendLog("RFW", "stateListener  onStart");
                onStarted();

            }

            @Override
            public void onStop() {
                Logger.appendLog("RFW", " stateListener onStop");
                onStopped();
            }
        });
    }

    private void addListenersTCS() {
        if (inputDlg != null)
            dialogBinding.invalidateAll();
    }
    private void onStopped() {
        //if (!isActive) return;
        if (tmrCheckData != null) {
            tmrCheckData.cancel();
        }
        if (started) {
            refuel_status = REFUEL_STATUS.ENDING;
            setRefuelStatus(refuel_status);
        }
        if (started && endRetry < 3) {

            //wait 5s to receive END TIME and last GROSSQTY, TOTALIZER, TEMPERATURE data
            Timer tmrStop = new Timer();
            tmrStop.schedule(new TimerTask() {
                @Override
                public void run() {
                    endRetry++;
                    // if not receive ENDTIME within 5s, retry 3times, if not received ask user to input data manually
                    if ((field_data_flag & FIELD_DATA_FLAG.FIELD_ENDTIME) > 0) {
                        tmrStop.cancel();
                        doStop();

                    } else if (endRetry >= 3) {
                        tmrStop.cancel();
                        runOnUiThread(() -> {
                                    showDataInput(false);
                                }
                        );

                    }
                }
            }, 1000 * 2, 1000 * 3);
        }
    }

    private void showContinueConfirm() {

        new AlertDialog.Builder(this)
                .setMessage(R.string.refuelling_status)
                .setTitle(R.string.app_name)
                /*.setNegativeButton(R.string.restart, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Logger.appendLog("Confirm restart refuel");
                        restartRefuel();
                    }
                })*/
                .setPositiveButton(R.string.refuel_continue, (dialog, which) -> {

                    Logger.appendLog("Confirm continue refuel");
                    continueRefuel();
                })
                .create()
                .show();
    }

    private REFUEL_STATUS refuel_status = REFUEL_STATUS.NONE;

    private void continueRefuel() {


        startButtonPress = true;
        setRefuelStatus(REFUEL_STATUS.STARTED); //start();

    }

    private void showDataInput() {
        showDataInput(true);
    }

    private EditRefuelDialogBinding dialogBinding;

    private void showDataInput(boolean allowCancel) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        dialogBinding = DataBindingUtil.inflate(this.getLayoutInflater(), R.layout.edit_refuel_dialog, null, false);
        dialogBinding.setMItem(mItem);
        builder.setView(dialogBinding.getRoot());
        builder.setTitle(R.string.app_name)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        if (allowCancel)
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });

        builder.setCancelable(false);
        inputDlg = builder.create();
        inputDlg.setCanceledOnTouchOutside(false);
        inputDlg.setCancelable(false);
        inputDlg.show();
        if (!BuildConfig.FHS)
            inputDlg.findViewById(R.id.fhs_refueler).setVisibility(View.GONE);
        else {
            Spinner spn = inputDlg.findViewById(R.id.fhs_refueler_spinner);
            spn.setAdapter(new ArrayAdapter<TruckModel>(this, android.R.layout.select_dialog_item, trucks));
        }


        inputDlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    mItem.setEndNumber(numberFormat.parse(((EditText) inputDlg.findViewById(R.id.dialog_meter)).getText().toString()).doubleValue());
                    mItem.setOriginalEndMeter(mItem.getEndNumber());
                    double amount = numberFormat.parse(((EditText) inputDlg.findViewById(R.id.dialog_volume)).getText().toString()).doubleValue();
                    if (BuildConfig.FHS) {
                        float gal = Math.round(amount / RefuelItemData.GALLON_TO_LITTER);
                        mItem.setRealAmount(gal);
                        mItem.setVolume(amount);

                    } else
                        mItem.setRealAmount(numberFormat.parse(((EditText) inputDlg.findViewById(R.id.dialog_volume)).getText().toString()).doubleValue());
                    mItem.setTemperature(numberFormat.parse(((EditText) inputDlg.findViewById(R.id.dialog_temperature)).getText().toString()).doubleValue());
                    mItem.setManualTemperature(numberFormat.parse(((EditText) inputDlg.findViewById(R.id.dialog_temperature)).getText().toString()).doubleValue());
                    mItem.setStartNumber(mItem.getEndNumber() - amount);
                    if (BuildConfig.FHS) {
                        Spinner spn = inputDlg.findViewById(R.id.fhs_refueler_spinner);
                        TruckModel selectedTruck = (TruckModel) spn.getSelectedItem();
                        mItem.setTruckId(selectedTruck.getId());
                        mItem.setTruckNo(selectedTruck.getTruckNo());
                    }
                    mItem.setCompleted(true);
                } catch (Exception ex) {
                    Logger.appendLog("RFW", "Data input : " + ex.getLocalizedMessage());
                }
                if (mItem.getRealAmount() <= 0 || mItem.getRealAmount() >= mItem.getEndNumber() || mItem.getStartNumber() <= 0) {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.validate)
                            .setMessage(R.string.invalid_data_input)
                            .setIcon(R.drawable.ic_error)
                            .create().show();
                } else {
                    inputDlg.dismiss();
                    TruckModel settingModel = currentApp.getSetting();
                    if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                        doStopTCS();
                    } else {
                        doStop();
                    }
                }
            }
        });

    }


    private void onStarted() {
        Logger.appendLog("RFW", "onStarted " + started);
        if (!isActive) return;
        if (!started) {
            reader.requestData();

            // REQUEST SALENUMBER AND TICKETNUMBER
            requestSaleNumberAndTicket();

            field_data_flag = FIELD_DATA_FLAG.FIELD_NOT_DEFINED;

            tmrCheckData = new Timer();
            checkDataRetryCount = 0;
            TimerTask tmrTask = new TimerTask() {
                @Override
                public void run() {
                    if (!reader.isDeviceError()) {
                        boolean missingGross = (field_data_flag & FIELD_DATA_FLAG.FIELD_GROSSQTY) == 0;
                        boolean missingTotalizer = (field_data_flag & FIELD_DATA_FLAG.FIELD_TOTALLIZER) == 0;

                        if (missingGross || missingTotalizer) {
                            checkDataRetryCount++;
                            Logger.appendLog(LOG_TAG, "Thiếu dữ liệu field (GROSSQTY đã nhận=" + !missingGross
                                    + ", TOTALIZER đã nhận=" + !missingTotalizer + "), lần thử lại thứ " + checkDataRetryCount);

                            if (checkDataRetryCount >= MAX_CHECK_DATA_RETRY) {
                                // Ngừng tự động thử lại sau khi đạt ngưỡng, tránh request/log dồn dập
                                // nếu thiết bị lỗi kéo dài; ghi log cảnh báo để theo dõi hiện trường.
                                Logger.appendLog(LOG_TAG, "Đã đạt số lần thử lại tối đa (" + MAX_CHECK_DATA_RETRY
                                        + ") khi kiểm tra dữ liệu field, dừng tự động request lại.");
                                tmrCheckData.cancel();
                            } else {
                                reader.requestData();
                            }
                        } else {
                            // Đã nhận đủ dữ liệu trong chu kỳ này -> reset bộ đếm
                            checkDataRetryCount = 0;
                        }

                        // SALENUMBER chỉ được hỏi một lần lúc bắt đầu mẻ và không nằm trong
                        // requestData(), nên nếu thiết bị chưa trả lời thì số bán hàng sẽ
                        // trống suốt mẻ và không in được lên phiếu. Hỏi lại cho tới khi có.
                        if (!saleNumberReceived && checkDataRetryCount < MAX_CHECK_DATA_RETRY) {
                            Logger.appendLog(LOG_TAG, "Chưa nhận được SALENUMBER, yêu cầu lại");
                            requestSaleNumberAndTicket();
                        }
                    }
                    field_data_flag = FIELD_DATA_FLAG.FIELD_NOT_DEFINED;
                }
            };
            tmrCheckData.scheduleAtFixedRate(tmrTask, 1000 * 10, 1000 * 60);

            started = true;
            setRefuelStatus(REFUEL_STATUS.STARTED);
            recordStartEvent();
        }
    }

    /**
     * Ghi nhận thời điểm đồng hồ xác nhận bắt đầu cấp phát. Thời gian được chụp
     * ngay trong callback thiết bị và lưu local ở thread nền để không chặn UI.
     */
    private void recordStartEvent() {
        final Date eventTime;

        synchronized (startEventLock) {
            if (mItem == null || startEventRecorded) return;

            // Khi Activity được tạo lại giữa phiên, callback "already started" không
            // được phép ghi đè thời gian bắt đầu đã lưu trước đó.
            if (mItem.getStatus() == REFUEL_ITEM_STATUS.PROCESSING
                    && mItem.getStartTime() != null) {
                startEventRecorded = true;
                return;
            }

            startEventRecorded = true;
            eventTime = new Date();
            mItem.setStartTime(eventTime);
            mItem.setTruckId(currentApp.getTruckId());
            mItem.setTruckNo(currentApp.getTruckNo());
            mItem.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        }

        Logger.appendLog("RFW", "Meter started at " + eventTime.getTime()
                + ", save local item immediately");
        enqueueSave(false, this::warnIfNotSaved);
    }

    private void requestSaleNumberAndTicket() {
        if (reader != null && reader.getConnected()) {
            reader.requestSaleNumberAndTicket();  // ← Gọi method từ LCRReader
            Logger.appendLog(LOG_TAG, "Requested SALENUMBER and TICKETNUMBER");
        }
    }
    private void checkEndNumberVsTicketNumber() {
        if (!ticketNumberReceived || lcrTicketNumber.isEmpty()) {
            Logger.appendLog(LOG_TAG, "Ticket number not received from device");
            finalizStop(); // Tiếp tục nếu không có ticket
            return;
        }

        double endMeterFromInput = mItem.getEndNumber();

        try {
            double ticketNumValue = Double.parseDouble(lcrTicketNumber);
            double difference = Math.abs(endMeterFromInput - ticketNumValue);

            Logger.appendLog(LOG_TAG,
                    String.format("Comparing: EndNumber=%.0f, Ticket=%.0f, Difference=%.0f",
                            endMeterFromInput, ticketNumValue, difference));

            if (difference > 1.0) { // Sai khác > 1 lít
                showEndNumberMismatchWarning(endMeterFromInput, ticketNumValue, lcrTicketNumber);
            } else {
                finalizStop(); // OK, tiếp tục
            }
        } catch (NumberFormatException e) {
            Logger.appendLog(LOG_TAG, "Cannot parse ticket number: " + lcrTicketNumber);
            finalizStop();
        }
    }
    private void showEndNumberMismatchWarning(double inputEndNumber,
                                              double deviceTicketNumber,
                                              String ticketNumberStr) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ CẢNH BÁO SỐ ĐỒNG HỒ");
        builder.setIcon(R.drawable.ic_warning);

        String message = String.format(
                "Số đồng hồ kết thúc KHÔNG KHỚP với ticket device!\n\n" +
                        "📊 Nhập vào:     %.0f L\n" +
                        "🎫 Ticket device: %s\n" +
                        "➖ Chênh lệch:     %.0f L\n\n" +
                        "Bạn muốn tiếp tục hay sửa lại?",
                inputEndNumber,
                ticketNumberStr,
                Math.abs(inputEndNumber - deviceTicketNumber)
        );

        builder.setMessage(message);

        builder.setPositiveButton("✓ Tiếp Tục", (dialog, which) -> {
            Logger.appendLog(LOG_TAG, "User confirmed mismatch - continuing");
            dialog.dismiss();
            finalizStop();
        });

        builder.setNegativeButton("✎ Sửa Lại", (dialog, which) -> {
            Logger.appendLog(LOG_TAG, "User chose to edit - going back to input");
            dialog.dismiss();
            showDataInput(true);
        });

        builder.setCancelable(false);
        runOnUiThread(() -> builder.create().show());
    }

    private void finalizStop() {
        setRefuelStatus(REFUEL_STATUS.ENDED);
        started = false;

        try {
            if (mItem != null) {
                mItem.setEndTime(new Date());
                if (Math.abs(mItem.getEndTime().getTime() - mItem.getStartTime().getTime()) > 1000L * 60 * 60 * 24) {
                    mItem.setStartTime(mItem.getEndTime());
                }

                if (mItem.getDeviceEndTime() != null && mItem.getDeviceStartTime() != null && !mItem.isCompleted()) {
                    long dateDiff = mItem.getDeviceEndTime().getTime() - mItem.getDeviceStartTime().getTime();
                    if (dateDiff > 0) {
                        mItem.setStartTime(new Date(mItem.getEndTime().getTime() - dateDiff));
                    }
                }

                // Tồn xe KHÔNG được cập nhật ở đây. Trước đây trừ ngay tại chỗ này, trước
                // khi mẻ được ghi: lần lưu bị chặn thì tồn đã trừ 399 GL trong khi phiếu
                // vẫn là 0 GL. Nay chỉ trừ sau khi Room xác nhận, và chỉ ở đúng lần commit
                // tạo ra chuyển trạng thái sang DONE — xem postRefuelCompleted().
                mItem.setStatus(REFUEL_ITEM_STATUS.DONE);

                if (!BuildConfig.FHS) {
                    if (mItem.getTruckId() != currentApp.getTruckId()
                            && mItem.getReceiptNumber() != null && !mItem.getReceiptNumber().isEmpty()) {
                        mItem.setReceiptNumber(null);
                    }
                    mItem.setTruckId(currentApp.getTruckId());
                    mItem.setTruckNo(currentApp.getTruckNo());
                }
            }

            postData();
        } catch (Exception e) {
            saveLog(LogEntryModel.LOG_TYPE.ERROR_LOG, " finalizStop Error: " + e.getLocalizedMessage());
        }
    }

    private void setRefuelStatus(REFUEL_STATUS status) {
        Logger.appendLog("RFW", "setRefuelStatus " + status);
        runOnUiThread(() -> {
            TextView lblrefuelStatus = findViewById(R.id.lbl_refuel_status);
            refuel_status = status;
            setButtonText(status);
            btnBack.setEnabled(refuel_status == REFUEL_STATUS.NONE);

            switch (status) {

                case STARTING:
                    break;
                case ENDING:
                    lblrefuelStatus.setText(R.string.refuel_ending);
                    lblrefuelStatus.setBackgroundColor(getResources().getColor(R.color.bgPrimary, getTheme()));
                    findViewById(R.id.btnCancel).setVisibility(View.INVISIBLE);
                    break;
                case ENDED:
                    lblrefuelStatus.setText(R.string.refuel_ended);
                    lblrefuelStatus.setBackgroundColor(getResources().getColor(R.color.bgInfo, getTheme()));
                    //setEnableButton(false);
                    btnForceStop.setEnabled(false);
                    findViewById(R.id.btnCancel).setVisibility(View.INVISIBLE);
                    break;

                case STARTED:
                    started = true;
                    lblrefuelStatus.setText(R.string.refuel_processing);
                    lblrefuelStatus.setBackgroundColor(getResources().getColor(R.color.bgSuccess, getTheme()));
                    btnStart.setVisibility(View.GONE);
                    findViewById(R.id.btnCancel).setVisibility(View.VISIBLE);
                    //setEnableButton(true);
                    break;
                default:
                    break;


            }
        });
    }

    private void updateRefuelData() {
        if (mItem != null && mItem.getStatus() != REFUEL_ITEM_STATUS.DONE && mItem.getStatus() != REFUEL_ITEM_STATUS.NONE) {
            if (model.getGrossQty() > 0) {
                mItem.setRealAmount(model.getGrossQty());
                mItem.setGallon(model.getGrossQty());
            }
            mItem.setTemperature(model.getTemperature());

            if (mItem.getManualTemperature() <= 0)
                mItem.setManualTemperature(model.getTemperature());

            if (mItem.getStatus() != REFUEL_ITEM_STATUS.NONE)
                mItem.setDeviceStartTime(model.getStartTime());
            if (started)
                mItem.setDeviceEndTime(model.getEndTime());
            //mItem.setStartNumber(model.getEndMeterNumber() - model.getGrossQty());
            if (model.getEndMeterNumber() > 0) {
                //mItem.setStartNumber(model.getStartMeterNumber());
                mItem.setStartNumber(model.getEndMeterNumber() - model.getGrossQty());
                mItem.setEndNumber(model.getEndMeterNumber());
                mItem.setOriginalEndMeter(model.getEndMeterNumber());
            }

            mItem.setWaterSensor(model.getAnalogPortValueLive());

            runOnUiThread(() -> {
                TextView txtGrossQty = findViewById(R.id.txtGrossQty);
                txtGrossQty.setText(String.format("%.0f", model.getGrossQty()));

                ((TextView) findViewById(R.id.txtStartMeter)).setText(String.format("%,.0f", model.getStartMeterNumber()));
                ((TextView) findViewById(R.id.txtEndMeter)).setText(String.format("%,.0f", model.getEndMeterNumber()));

                TextView txtTemp = findViewById(R.id.txtTemp);
                txtTemp.setText(String.format("%.2f", model.getTemperature()));

                ((TextView) findViewById(R.id.txtWaterSensor)).setText(String.format("%,.0f", model.getAnalogPortValueLive()));
                safeSetText(R.id.txtDeliveryNumber, String.valueOf(model.getSaleNumber()));
                //safeSetText(R.id.txtDeliveryNumber, String.valueOf(model.getTicketNumber()));
            });

            saveData();
        }
        //Log.e("REFUEL", "Update refuel data");
    }

    private void safeSetText(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv != null && text != null && !text.equals("null")) {
            tv.setText(text);
        }
    }
    private void updateRefuelDataTCS() {
        TruckModel settingModel = currentApp.getSetting();
        //if (mItem != null && mItem.getStatus() != REFUEL_ITEM_STATUS.DONE && mItem.getStatus() != REFUEL_ITEM_STATUS.NONE) {
        if (statusStartTCS == REFUEL_STATUS.STARTED && mItem != null) {
            if (tcsData.getGrossQtyRound() > 0) {
                mItem.setRealAmount(tcsData.getGrossQtyRound());
                mItem.setGallon(tcsData.getGrossQtyRound());
            }
            mItem.setTemperature(tcsData.getTemperature());

            if (mItem.getManualTemperature() <= 0)
                mItem.setManualTemperature(tcsData.getTemperature());

            if (mItem.getStatus() != REFUEL_ITEM_STATUS.NONE)
                mItem.setDeviceStartTime(new Date());
            if (started)
                mItem.setDeviceEndTime(new Date());
            //mItem.setStartNumber(model.getEndMeterNumber() - model.getGrossQty());
            if (tcsData.getGrossTotalRound() > 0) {
                //mItem.setStartNumber(model.getStartMeterNumber());
                mItem.setStartNumber(tcsData.getGrossTotalRound() - tcsData.getGrossQtyRound());
                mItem.setEndNumber(tcsData.getGrossTotalRound());
                mItem.setOriginalEndMeter(tcsData.getGrossTotalRound());
            }

            mItem.setWaterSensor(0);

            runOnUiThread(() -> {
                TextView txtGrossQty = findViewById(R.id.txtGrossQty);
                txtGrossQty.setText(String.format("%.0f", tcsData.getGrossQtyRound()));

                ((TextView) findViewById(R.id.txtStartMeter)).setText(String.format("%,.0f", tcsData.getGrossTotalRound() - tcsData.getGrossQtyRound()));
                ((TextView) findViewById(R.id.txtEndMeter)).setText(String.format("%,.0f", tcsData.getGrossTotalRound()));

                TextView txtTemp = findViewById(R.id.txtTemp);
                txtTemp.setText(String.format("%.2f", tcsData.getTemperature()));

                ((TextView) findViewById(R.id.txtWaterSensor)).setText(String.format("%,.0f", 0.0)); // ← Sửa ở đây
            });
            saveData();
        }
        //Log.e("REFUEL", "Update refuel data");
    }
    private void doStop() {
        if (reader != null) {
            reader.setRefuel(false);
        }

        if (mItem != null && model != null) {
            if (mItem.getManualTemperature() == 0) {
                mItem.setManualTemperature(model.getTemperature());
            }
            if (mItem.getOriginalEndMeter() == 0) {
                mItem.setOriginalEndMeter(model.getEndMeterNumber());
            }
        }

        Logger.appendLog(LOG_TAG, "Device Sale Number: " + lcrSaleNumber);
        Logger.appendLog(LOG_TAG, "Device Ticket Number: " + lcrTicketNumber);
        Logger.appendLog(LOG_TAG, String.format(java.util.Locale.US,
                "User End Number: %.0f", mItem.getEndNumber()));

        // Kiểm tra ticket trước khi hoàn thành
        checkEndNumberVsTicketNumber();
    }
    private void doStopTCS() {
        if (tcsDevice != null) tcsDevice.disConnect();

        if (mItem != null && tcsData != null) {
            if (mItem.getManualTemperature() == 0) mItem.setManualTemperature(tcsData.getTemperature());
            if (mItem.getOriginalEndMeter() == 0) mItem.setOriginalEndMeter(tcsData.getGrossTotalRound());
        }

        finalizeStopCommon();
    }

    private void finalizeStopCommon() {
        Logger.appendLog(LOG_TAG, "finalizeStopCommon called - delegating to finalizStop");
        finalizStop();  // ← THÊM DÒNG NÀY
    }
    private enum REFUEL_STATUS {
        NONE,
        STARTING,
        STARTED,
        ENDING,
        ENDED
    }


    private void openConfirm() {

        if (mItem.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.REFUEL) {
            Intent intent = new Intent(this, RefuelDetailConfirmActivity.class);
            // Kèm baseline để màn hình Confirm còn lưu được (xem RefuelIntent).
            com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, mItem);
            //intent.putExtra("REFUEL_LOCAL_ID", mItem.getLocalId());
            int PREVIEW_OPEN = 1;
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, RefuelPreviewActivity.class);
            intent.putExtra("REFUEL_ID", mItem.getId());
            intent.putExtra("REFUEL_LOCAL_ID", mItem.getLocalId());
            intent.putExtra("REFUEL_UNIQUE_ID", mItem.getUniqueId());
            startActivity(intent);
        }

        finish();
    }

    /**
     * Xếp một lần ghi vào hàng đợi, thao tác trên BẢN SAO chụp ngay tại đây.
     *
     * <p>Hai luật bắt buộc:
     * <ul>
     *   <li>bản sao độc lập — đối tượng của màn hình còn bị luồng đồng hồ và nút End sửa
     *       tiếp sau khi task đã xếp hàng;</li>
     *   <li>chỉ {@code finalize = true} (đường End) mới được mang trạng thái {@code DONE}.
     *       Autosave chốt mẻ hộ là nguồn của lỗi mất trừ tồn xe.</li>
     * </ul>
     */
    private long lastAutosaveAt = 0;

    /**
     * Nhịp tối thiểu giữa hai lần LƯU SỐ ĐO TRUNG GIAN.
     *
     * <p>Thiết bị TCS gọi {@code OnRecivedData} theo TỪNG GÓI dữ liệu — vài lần mỗi giây,
     * khác LCR vốn một giây một lần. Lưu theo từng gói vừa nặng vừa làm ngập log: đo trên xe
     * thật 17-08 16:36, ba đến bốn lần lưu mỗi giây với cùng một bộ số. Số đo trung gian chỉ
     * cần đủ dày để không mất nhiều khi app bị kill; lần chốt của End không bao giờ bị hoãn.
     */
    private static final long MIN_AUTOSAVE_INTERVAL_MS = 1000L;

    private void enqueueSave(boolean finalize,
                             java.util.function.Consumer<RefuelItemData> onResult) {
        final RefuelItemData source = mItem;
        if (source == null) return;

        if (!finalize) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAutosaveAt < MIN_AUTOSAVE_INTERVAL_MS) return;
            lastAutosaveAt = now;
        }

        final RefuelItemData snapshot = source.snapshotForSave();

        if (!finalize && snapshot.getStatus() == REFUEL_ITEM_STATUS.DONE) {
            // Mẻ đã được End chốt; autosave không còn việc gì ở đây.
            Logger.appendLog("RFW", "Bỏ autosave trên mẻ đã DONE, để đường End tự lưu");
            return;
        }

        if (saveExecutor.isShutdown()) {
            Logger.appendLog("RFW", "Bỏ lần lưu vì hàng đợi đã đóng");
            return;
        }

        try {
            saveExecutor.execute(() -> {
            RefuelItemData result;
            try {
                // Dữ liệu nghiệp vụ phải là ảnh chụp lúc enqueue, nhưng baseline phải là
                // phiên bản mới nhất do task đứng ngay trước vừa commit. Nếu giữ baseline
                // cũ chụp cùng lúc với dữ liệu, End xếp sau autosave sẽ tự conflict vì
                // autosave đã tăng clientSeq trong khi End còn nằm chờ trong hàng đợi.
                snapshot.adoptSaveState(source);
                result = DataHelper.postRefuel(snapshot, false);

                // Lần chốt mẻ bị precondition chặn: thử lại theo kiểu PATCH — đọc row mới
                // nhất dưới khoá ghi rồi chỉ đắp đúng nhóm trường của vòng đời mẻ. Không có
                // bước này thì conflict thật lúc End là ngõ cụt: bấm "Thử lại" gửi lại đúng
                // baseline cũ nên hỏng mãi, số liệu mẻ nằm lại trên màn hình cho tới khi mất.
                if (finalize && result != null
                        && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                    Logger.appendLog("RFW", "Chốt mẻ bị chặn, thử lại bằng EndFieldsPatch");
                    result = DataHelper.saveEndFields(snapshot);
                }
            } catch (Throwable ex) {
                Logger.appendLog("RFW", "Lưu lỗi: " + ex);
                result = null;
            }

            // Hàng đợi ghi làm việc trên bản sao, nên phải trả phiên bản mới về cho đối
            // tượng của màn hình, nếu không lần lưu kế tiếp đứng trên baseline cũ.
            if (RefuelItemData.isCommitted(result))
                source.adoptSaveState(snapshot);

            final RefuelItemData delivered = result;
            runOnUiThread(() -> onResult.accept(delivered));
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // onDestroy có thể đóng queue đúng lúc callback thiết bị vừa yêu cầu autosave.
            // Không để race vòng đời này làm crash ứng dụng.
            Logger.appendLog("RFW", "Bỏ lần lưu vì hàng đợi vừa đóng");
        }
    }

    /**
     * Lưu số đo trung gian. Xếp cùng hàng đợi với lần lưu của End nên số đo cuối luôn là
     * số được ghi sau cùng — kể cả khi đồng hồ hồi lưu từ 400 về 399.
     */
    private void saveData() {
        enqueueSave(false, result -> {
            if (!RefuelItemData.isCommitted(result)) {
                warnIfNotSaved(result);
                return;
            }
            if (mItem == null) return;
            currentApp.saveCurrentRefuel(mItem.getId(), mItem.getLocalId());
        });
    }

    /**
     * Lưu lần chốt của mẻ.
     *
     * <p>Xếp vào CUỐI hàng đợi: mọi số đo trung gian chắc chắn ghi xong trước. Đây cũng là
     * đường DUY NHẤT được phép tạo chuyển trạng thái sang DONE, nên cờ
     * {@code transitionedToDone} — thứ quyết định việc trừ tồn xe — luôn về đúng chỗ.
     */
    private void postData() {
        if (mItem != null)
            Logger.appendLog("RFW", String.format(java.util.Locale.US,
                    "Post item chốt mẻ, EndNumber=%.0f RealAmount=%.0f",
                    mItem.getEndNumber(), mItem.getRealAmount()));

        enqueueSave(true, result -> {
            Logger.appendLog("RFW", "Post item completed");
            postRefuelCompleted(result);
        });
    }

    private long lastSaveWarningAt = 0;

    /** Khoảng cách tối thiểu giữa hai lần nhắc người dùng về lỗi lưu nền. */
    private static final long SAVE_WARNING_INTERVAL_MS = 30_000L;

    /**
     * Lưu nền bị chặn thì phải báo — nhưng KHÔNG bằng hộp thoại chặn màn hình.
     *
     * <p>Timer đọc đồng hồ lưu mỗi giây, nên một lỗi kéo dài sẽ sinh ra hàng chục hộp thoại
     * chồng lên nhau ngay giữa lúc đang bơm: người dùng không thao tác được, mà cũng không
     * đọc được cái nào. Đây là lỗi đã gặp thật khi chạy thử trên xe.
     *
     * <p>Nền thì báo bằng Toast, tối đa mỗi {@link #SAVE_WARNING_INTERVAL_MS} một lần. Hộp
     * thoại chặn chỉ dành cho hai thời điểm người dùng thực sự phải quyết định: bấm End và
     * bấm Xác nhận. Log thì vẫn ghi đủ mọi lần.
     */
    private int suppressedSaveWarnings = 0;

    private void warnIfNotSaved(RefuelItemData result) {
        if (RefuelItemData.isCommitted(result)) return;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastSaveWarningAt < SAVE_WARNING_INTERVAL_MS) {
            // Ghi từng lần sẽ ngập fms.log rồi ngập cả file gửi lên server: đo trên xe thật
            // 17-08 16:36 là 3-4 dòng mỗi giây. Nén lại nhưng PHẢI đếm, nếu không việc nén
            // biến thành mất bằng chứng.
            suppressedSaveWarnings++;
            return;
        }

        Logger.appendLog("RFW", String.format(java.util.Locale.US,
                "Lưu nền chưa thành công: %s%s",
                result == null ? "FAILED" : result.getSaveOutcome(),
                suppressedSaveWarnings > 0
                        ? " (đã nén " + suppressedSaveWarnings + " lần giống hệt)" : ""));
        suppressedSaveWarnings = 0;
        lastSaveWarningAt = now;

        runOnUiThread(() -> {
            if (!isFinishing())
                android.widget.Toast.makeText(this,
                        R.string.warn_refuel_background_save_failed,
                        android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private void postRefuelCompleted(RefuelItemData itemData) {
        if (mItem == null) return;   // màn hình đã bị huỷ trong lúc ghi

        // Kết thúc mẻ mà chưa ghi được xuống Room thì KHÔNG đi tiếp: mở màn hình xác nhận
        // lúc này là đưa người dùng đi nhập tiếp lên một phiếu chưa hề tồn tại số liệu.
        if (!RefuelItemData.isCommitted(itemData)) {
            Logger.appendLog("RFW", "Chưa lưu được mẻ ("
                    + (itemData == null ? "FAILED" : itemData.getSaveOutcome())
                    + "), ở lại màn hình để thử lại");
            setRefuelStatus(REFUEL_STATUS.ENDED);
            showEndSaveFailed();
            return;
        }

        if (itemData.getId() != mItem.getId())
            mItem.setId(itemData.getId());
        if (itemData.getLocalId() != mItem.getLocalId())
            mItem.setLocalId(itemData.getLocalId());
        if (itemData.getUniqueId() != mItem.getUniqueId())
            mItem.setUniqueId(itemData.getUniqueId());

        // Tồn xe chỉ đổi đúng một lần, tại lần commit thực sự chuyển mẻ sang DONE.
        if (itemData.isTransitionedToDone())
            applyStockChange();

        if (cancelled)
            finish();
        else
            openConfirm();
    }

    /**
     * Cập nhật tồn xe sau khi mẻ đã được ghi. Gọi đúng một lần cho mỗi mẻ: cờ
     * {@code transitionedToDone} chỉ bật ở lần lưu tạo ra chuyển trạng thái.
     */
    private void applyStockChange() {
        boolean isExtract = mItem.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.EXTRACT;
        float delta = (float) (isExtract ? mItem.getRealAmount() : -mItem.getRealAmount());
        currentApp.setCurrentAmount(currentApp.getCurrentAmount() + delta);

        Logger.appendLog("RFW", String.format(java.util.Locale.US,
                "Cập nhật tồn xe %+.0f sau khi mẻ đã ghi, uid=%s", delta, mItem.getUniqueId()));
    }

    private void showEndSaveFailed() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.error_refuel_end_save_failed)
                .setPositiveButton(R.string.retry, (dialog, which) -> postData())
                .setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showApproachConfirmIfNeeded() {
        if (mItem == null) return;
        if (mItem.getApproachTime() != null) return;
        if (approachPopupShown) return;

        approachPopupShown = true;

        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("Chuyến này chưa ghi nhận tiếp cận. Bạn có muốn ghi nhận thời điểm tiếp cận lúc này không?")
                .setPositiveButton("Có", (dialog, which) -> {
                    Date now = new Date();
                    mItem.setApproachTime(now);

                    TextView lblApproach = findViewById(R.id.lblApproachTime);
                    Button btnApproach = findViewById(R.id.btnApproach);

                    if (lblApproach != null) {
                        lblApproach.setText(
                                "Tiếp cận: " + DateUtils.formatDate(now, "dd/MM/yyyy HH:mm")
                        );
                        lblApproach.setVisibility(View.VISIBLE);
                    }

                    if (btnApproach != null) {
                        btnApproach.setVisibility(View.GONE);
                    }

                    // Lưu lại
                    enqueueSave(false, RefuelDetailActivity.this::warnIfNotSaved);

                    dialog.dismiss();
                })
                .setNegativeButton("Không", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }


    private boolean isActive = false;

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onStop() {
        //Logger.appendLog("RFW", "Power onstop");
        super.onStop();
        //isActive = false;
    }

    @Override
    protected void onPause() {
        //Logger.appendLog("RFW", "Power on pause");
        super.onPause();
        //isActive = false;
    }

    @Override
    protected void onResume() {
        //Logger.appendLog("RFW", "Power on resume");
        super.onResume();
        isActive = true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        clearListeners();
        if (tmrCheckData != null) {
            tmrCheckData.cancel();
        }
        if (tmrDataFreshness != null) {
            tmrDataFreshness.cancel();
            tmrDataFreshness = null;
        }

        // shutdown() chứ KHÔNG shutdownNow(): các lần ghi đã xếp hàng phải chạy cho xong.
        // Huỷ giữa chừng là vứt đúng số liệu mẻ mà màn hình vừa nhận được.
        saveExecutor.shutdown();
        mItem = null;
        Runtime.getRuntime().gc();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_POWER)
            return false;
        return super.onKeyDown(keyCode, event);
    }

    private void setEnableButton(boolean enabled) {
        btnStart.setEnabled(enabled);

    }

    private interface FIELD_DATA_FLAG {
        int FIELD_NOT_DEFINED = 0;
        int FIELD_ENDTIME = 1;
        int FIELD_STARTTIME = 2;
        int FIELD_GROSSQTY = 4;
        int FIELD_TOTALLIZER = 8;
        int FIELD_TEMPERATURE = 16;
        int FIELD_SALENUMBER = 32;
        int FIELD_TICKETNUMBER = 64;

        int FIELD_ALL = 31;
        int FIELD_ALL_DATA = 30;
        int FIELD_ALL_METER = 12;
    }

}
