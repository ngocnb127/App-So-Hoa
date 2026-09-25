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
import com.megatech.fms.helpers.MeterFieldHealth;
import com.megatech.fms.helpers.RefuelApproachGuard;
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

    /**
     * Các hộp thoại màn này đang mở.
     *
     * <p>Đo trên máy thật (Crashlytics, bản 118, 13 thiết bị):
     * {@code IllegalArgumentException: View=DecorView[RefuelDetailActivity] not attached to
     * window manager} — bấm một nút trên hộp thoại thuộc về một Activity ĐÃ kết thúc.
     *
     * <p>Vì sao bọc {@code try/catch} quanh {@code dialog.dismiss()} KHÔNG cứu được: sau mỗi
     * lần bấm nút, {@code AlertController} tự gửi thêm một lệnh dismiss của riêng nó
     * ({@code MSG_DISMISS_DIALOG}). Lệnh đó nằm trong khung của hệ thống, ta không bắt được,
     * và nó ném đúng exception này. Cách duy nhất là ĐỪNG để hộp thoại sống lâu hơn cửa sổ
     * của màn hình — không còn hộp thoại thì không còn nút để bấm.
     */
    private final List<Dialog> openDialogs = new ArrayList<>();

    /** Ghi lại một hộp thoại vừa mở. Trả về chính nó để gọi lồng ngay tại chỗ {@code show()}. */
    private <T extends Dialog> T track(T dialog) {
        if (dialog != null) openDialogs.add(dialog);
        return dialog;
    }

    /**
     * Đóng mọi hộp thoại còn mở của màn này.
     *
     * <p>Gọi ở {@code onPause} khi màn hình đang KẾT THÚC, và một lần nữa ở {@code onDestroy}
     * làm lưới cuối. Cố ý KHÔNG đóng khi chỉ là tạm dừng (tắt màn hình, chuyển app): hộp nhập
     * tay đang giữ số đồng hồ người dùng vừa gõ, đóng nó đi là làm mất dữ liệu thật.
     */
    private void dismissOpenDialogs() {
        for (Dialog dialog : new ArrayList<>(openDialogs)) {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
                // Đóng một hộp thoại đã chết không phải là lỗi cần báo.
            }
        }
        openDialogs.clear();
        inputDlg = null;
    }

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
                // Nhánh TCS ở đây KHÔNG BAO GIỜ CHẠY, và đó là ĐÚNG THIẾT KẾ.
                //
                // Xe TCS bắt đầu và dừng NGAY TẠI ĐỒNG HỒ, không điều khiển từ app (chủ dự án
                // xác nhận 2026-09-05). Vì vậy startButtonSupported chỉ bật cho LCR600, btnStart
                // không hiện trên TCS, nên không có đường nào bấm tới đây.
                //
                // Giữ lại nhánh này thay vì xoá: nếu sau này TCS được phép điều khiển từ xa thì
                // đây là chỗ nối lại, và xoá đi rồi viết lại dễ sai hơn là để nguyên có ghi chú.
                // Đường kết thúc THẬT của TCS là sự kiện từ thiết bị -> doStopTCS() -> finalizStop().
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
        track(builder.create()).show();
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
            // Chỉ LCR600 nhận được lệnh bắt đầu/dừng từ xa. Ghi lại thành cờ để hàm quyết
            // định hiển thị dùng chung, thay vì đặt visibility một lần rồi bị ghi đè.
            //
            // Cờ này CỐ Ý chỉ được đặt trong nhánh LCR: xe TCS bắt đầu và dừng NGAY TẠI ĐỒNG HỒ
            // (chủ dự án xác nhận 2026-09-05), nên trên TCS nút Bắt đầu/Dừng không bao giờ hiện.
            // Đừng "sửa" bằng cách đặt cờ này ở nhánh TCS — start() gọi thẳng reader.start(), mà
            // trên TCS reader không được tạo, nên hiện nút ở đó là crash.
            startButtonSupported = reader.isLCR600();
            if (startButtonSupported) btnStart.setVisibility(View.VISIBLE);
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
        // Phiên đo mới: xoá số đếm cũ và bật khoảng ân hạn của hàng đợi đăng ký trường.
        MeterFieldHealth.shared().beginSession(android.os.SystemClock.elapsedRealtime());
        meterDataStale = false;
        meterFieldLogLatch.beginSession();
        tmrDataFreshness = new Timer("FMS-Meter-Freshness");
        tmrDataFreshness.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isActive) return;
                // Mất kết nối đã có đường báo riêng; ở đây chỉ xét ca CÒN kết nối mà im số.
                boolean connected = tcsDevice != null ? tcsDevice.isConnect()
                        : (reader != null && reader.getConnected());
                if (!connected) return;

                // Sức khoẻ TỪNG TRƯỜNG chỉ để GHI VẾT, KHÔNG bật cảnh báo trên màn hình.
                //
                // Đây là quyết định có chủ ý, không phải bỏ sót. Phép đo theo trường quá nhạy
                // với những chuyện bình thường của thiết bị — trường đọc một lần rồi thôi,
                // trường phụ im tiếng, số tổng bị bộ lọc giữ lại vài nhịp — nên nó bật cảnh
                // báo trong lúc đồng hồ vẫn trả số đúng. Cảnh báo sai vài lần thì đến lần
                // đồng hồ chết thật cũng không còn ai nhìn.
                //
                // Cái thật sự bảo vệ số liệu KHÔNG nằm ở màu sắc mà ở hai chỗ khác, cả hai
                // vẫn nguyên: MeterFieldHealth.resolveStartNumber chặn số đồng hồ đầu mẻ hỏng
                // ghi xuống Room, và dòng nhật ký ngay dưới đây để đối soát sau ca. Cảnh báo
                // cho người vận hành chỉ còn dựa trên MỘT tín hiệu: không có gói nào về.
                java.util.List<MeterFieldHealth.Field> staleFields =
                        MeterFieldHealth.shared().staleFields(
                                android.os.SystemClock.elapsedRealtime());
                if (meterFieldLogLatch.update(!staleFields.isEmpty())) {
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=%s uid=%s fields=%s health=%s",
                            staleFields.isEmpty() ? "METER_FIELD_RECOVERED" : "METER_FIELD_STALE",
                            mItem == null ? "null" : mItem.getUniqueId(),
                            staleFields,
                            MeterFieldHealth.shared().snapshotForLog()));
                }

                long last = lastMeterDataAt;

                // Chỉ CẢNH BÁO: dấu kết nối đổi màu, không dialog, không dừng đo, không
                // chặn Start/Stop. Tự tắt vì tính lại từ đầu mỗi chu kỳ, không có cờ dính.
                boolean stale = last > 0
                        && System.currentTimeMillis() - last > METER_DATA_STALE_MS;
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

    /**
     * Chốt GHI VẾT sức khoẻ trường: chỉ để nhật ký ghi mỗi lần CHUYỂN trạng thái thay vì
     * ghi lại y hệt mỗi 2 giây. Không còn điều khiển bất cứ thứ gì trên màn hình.
     */
    private final MeterFieldHealth.WarningLatch meterFieldLogLatch =
            new MeterFieldHealth.WarningLatch();

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

    /** Listener màn này gắn vào đồng hồ dùng chung — giữ lại để chỉ gỡ đúng chúng. */
    private LCRReader.LCRConnectionListener lcrConnectionListener;
    private LCRReader.LCRDataListener lcrDataListener;
    private LCRReader.LCRStateListener lcrStateListener;

    /**
     * Gỡ listener CỦA MÀN NÀY. Không dùng {@code setXxxListener(null)}: onDestroy chạy muộn,
     * lúc màn kế tiếp (màn in, hoặc màn tra nạp của chuyến sau) đã gắn listener của nó vào
     * cùng đồng hồ — gán null là gỡ nhầm của màn đó.
     */
    private void clearListeners() {
        if (reader != null)
            reader.removeListeners(lcrDataListener, lcrConnectionListener, lcrStateListener);
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
            track(new AlertDialog.Builder(activity)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.inventory_alert)
                    .setIcon(R.drawable.ic_warning)
                    .setPositiveButton(R.string.btn_continue, (dialog, which) -> dialog.dismiss())
                    .setNegativeButton(R.string.btn_stop, (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .create()).show();
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

        track(dialog).show();
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
        // Mọi caller của hàm này là một hộp thoại người dùng vừa nhập xong.
        enqueueSave(false, SaveKind.USER_EDIT, RefuelDetailActivity.this::warnIfNotSaved);

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

            track(dialog).show();

            dialog.findViewById(R.id.btn_select).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    Spinner spnDriver = dialog.findViewById(R.id.select_user_driver);
                    UserModel driver = (UserModel) spnDriver.getSelectedItem();


                    Spinner spnOperator = dialog.findViewById(R.id.select_user_operator);
                    UserModel operator = (UserModel) spnOperator.getSelectedItem();

                    if (driver.getId() == operator.getId()) {
                        track(new AlertDialog.Builder(dialog.getContext())
                                .setTitle(R.string.select_user)
                                .setMessage(R.string.error_same_user)
                                .setIcon(R.drawable.ic_error)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                })
                                .create()).show();
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
                // Chuyến trước chưa bấm Rời đi thì không được mở chuyến mới. Kiểm tra đọc
                // Room nên chạy ở thread nền, ghi mốc tiếp cận chỉ khi được phép.
                checkApproachAllowed(() -> recordApproach(v));
                break;
        }

    }

    private void showCancelDialog() {

        if (refuel_status == REFUEL_STATUS.STARTED) {
            track(new AlertDialog.Builder(this)
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
                    .create()).show();
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
        track(timePicker).show();

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

        Dialog dlg = track(builder.create());
        dlg.show();
        ((TextView) dlg.findViewById(android.R.id.message)).setTextSize(18);
        ((TextView) dlg.findViewById(android.R.id.button1)).setTextSize(18);
        ((TextView) dlg.findViewById(android.R.id.button2)).setTextSize(18);

    }

    /**
     * Màn tra nạp là màn hình DUY NHẤT còn chặn Back, và chỉ chặn khi mẻ ĐANG chạy: rời màn
     * hình lúc đó là bỏ dở một mẻ đang bơm. Ở NONE và ENDED thì Back đi được bình thường.
     */
    @Override
    protected boolean isBackBlocked() {
        return isRefuelRunning(refuel_status);
    }

    /** Mẻ đang chạy: KHÔNG được rời màn hình giữa chừng. */
    private static boolean isRefuelRunning(REFUEL_STATUS status) {
        return status == REFUEL_STATUS.STARTING
                || status == REFUEL_STATUS.STARTED
                || status == REFUEL_STATUS.ENDING;
    }

    /**
     * Nút Bắt đầu/Dừng chỉ có nghĩa trên dòng máy có lệnh điều khiển từ xa (LCR600). Cờ này
     * do {@code initReader()} bật, thay cho việc đặt visibility một lần rồi bị ghi đè.
     */
    private boolean startButtonSupported = false;

    /**
     * Quyết định hiển thị DUY NHẤT của btnStart.
     *
     * <p>Trước đây dòng đầu hàm luôn {@code setVisibility(GONE)} và không nhánh nào bật lại,
     * nên nút biến mất ngay sau lần đổi trạng thái đầu tiên — đường {@code stop()} vì thế
     * không bao giờ chạy được. Nay nút hiện ở các trạng thái có nghĩa (NONE để bắt đầu,
     * STARTED để dừng) và ẩn ở các trạng thái đang chuyển tiếp.
     *
     * <p>KHÔNG đụng {@code btnForceStop}: nút đó luôn hiện, luôn bấm được.
     */
    private void setButtonText(REFUEL_STATUS status) {
        int btnText = R.string.start;
        boolean visible = startButtonSupported
                && (status == REFUEL_STATUS.NONE || status == REFUEL_STATUS.STARTED);
        btnStart.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        reader.setConnectionListener(lcrConnectionListener = new LCRReader.LCRConnectionListener() {
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

        reader.setFieldDataListener(lcrDataListener = new LCRReader.LCRDataListener() {
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
                                track(new AlertDialog.Builder(activity)
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
                                        .create()).show();
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

        reader.setStateListener(lcrStateListener = new LCRReader.LCRStateListener() {
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

        track(new AlertDialog.Builder(this)
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
                .create()).show();
    }

    private REFUEL_STATUS refuel_status = REFUEL_STATUS.NONE;

    private void continueRefuel() {


        startButtonPress = true;
        setRefuelStatus(REFUEL_STATUS.STARTED); //start();

    }

    private void showDataInput() {
        showDataInput(true);
    }

    /** Mã lỗi của hộp nhập tay — độc lập với chuỗi hiển thị để kiểm thử được. */
    static final String PROBLEM_AMOUNT = "AMOUNT";
    static final String PROBLEM_START_METER = "START_METER";
    static final String PROBLEM_END_METER = "END_METER";

    /**
     * Liệt kê ĐÚNG những ô sai của hộp nhập tay sau Dừng khẩn.
     *
     * <p>Trước đây ba điều kiện gộp chung một câu "Số liệu nhập không hợp lệ", nên người
     * dùng không biết phải sửa ô nào.
     */
    static java.util.List<String> manualInputProblems(double realAmount,
                                                      double startNumber,
                                                      double endNumber) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (realAmount <= 0) problems.add(PROBLEM_AMOUNT);
        if (startNumber <= 0) problems.add(PROBLEM_START_METER);
        // Sản lượng không thể lớn hơn chính số đồng hồ kết thúc.
        if (realAmount >= endNumber) problems.add(PROBLEM_END_METER);
        return problems;
    }

    private static int manualInputProblemLabel(String problem) {
        switch (problem) {
            case PROBLEM_AMOUNT:
                return R.string.real_amount;
            case PROBLEM_START_METER:
                return R.string.start_meter;
            default:
                return R.string.end_meter;
        }
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
        else
            // Hộp nhập tay bắt buộc (thiết bị không trả về giờ kết thúc) TRƯỚC ĐÂY không có
            // lối ra nào: không nút Huỷ, không cho huỷ bằng chạm ngoài. Nhập sai một ô là
            // kẹt hẳn. Nay luôn còn nút "Để sau": đóng hộp, GIỮ NGUYÊN số liệu đang có và
            // trả màn hình về trạng thái đang tra nạp để còn Dừng khẩn / Huỷ / thử lại.
            builder.setNegativeButton(R.string.input_later, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Logger.appendLog("RFW", "Người dùng hoãn nhập tay, trở lại màn tra nạp");
                    dialog.dismiss();
                    setRefuelStatus(REFUEL_STATUS.STARTED);
                }
            });

        builder.setCancelable(false);
        inputDlg = track(builder.create());
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
                    // Nhánh nhập tay theo LÍT — không còn xe nào dùng đồng hồ lít.
                    // if (BuildConfig.FHS) {
                    //     float gal = Math.round(amount / RefuelItemData.GALLON_TO_LITTER);
                    //     mItem.setRealAmount(gal);
                    //     mItem.setVolume(amount);
                    // } else
                    mItem.setRealAmount(amount);
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
                java.util.List<String> problems = manualInputProblems(
                        mItem.getRealAmount(), mItem.getStartNumber(), mItem.getEndNumber());
                if (!problems.isEmpty()) {
                    // Hộp báo lỗi cũ KHÔNG có nút nào và không nói ô nào sai: người dùng chỉ
                    // còn cách chạm ra ngoài để đoán. Nay có nút OK và chỉ đúng ô phải sửa.
                    StringBuilder msg = new StringBuilder(getString(R.string.invalid_data_input));
                    for (String p : problems)
                        msg.append("\n• ").append(getString(manualInputProblemLabel(p)));
                    track(new AlertDialog.Builder(activity)
                            .setTitle(R.string.validate)
                            .setMessage(msg.toString())
                            .setIcon(R.drawable.ic_error)
                            .setPositiveButton("OK", (d, w) -> d.dismiss())
                            .create()).show();
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
        // Đây là CHUYỂN TRẠNG THÁI một lần, không phải số đo trung gian: nó ghi giờ bắt đầu
        // và đóng dấu TruckId/TruckNo của xe này lên phiếu. Để nó rơi vào nhịp autosave thì
        // một gói dữ liệu thiết bị đến trước đó dưới một giây sẽ nuốt mất cả hai.
        enqueueSave(false, SaveKind.STATE_EVENT, this::warnIfNotSaved);
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

            // Ticket = 0 nghĩa là đồng hồ KHÔNG CÓ số ticket, không phải số ticket bằng 0.
            // So với 0 thì chênh lệch luôn đúng bằng số đồng hồ — cảnh báo lúc nào cũng nổ và
            // luôn sai. Đo trên xe HAN3-20-7006 ngày 25-08-2026: mọi mẻ đều nhận
            // "Ticket Number: 0" rồi báo "chênh lệch 78.514.784 L".
            if (ticketNumValue <= 0) {
                Logger.appendLog(LOG_TAG, "Thiết bị không trả số ticket (=0), bỏ qua đối chiếu");
                finalizStop();
                return;
            }

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
                        "➖ Chênh lệch:     %.0f L",
                inputEndNumber,
                ticketNumberStr,
                Math.abs(inputEndNumber - deviceTicketNumber)
        );

        builder.setMessage(message);

        builder.setPositiveButton("ĐÃ HIỂU", (dialog, which) -> {
            Logger.appendLog(LOG_TAG, "User acknowledged end-meter mismatch warning");
            dialog.dismiss();
            finalizStop();
        });

        builder.setCancelable(false);
        runOnUiThread(() -> track(builder.create()).show());
    }

    private void finalizStop() {
        setRefuelStatus(REFUEL_STATUS.ENDED);
        started = false;

        // Một dòng tổng kết sức khoẻ đọc trường, để đối soát sau ca: cặp số đồng hồ có thể
        // trông hợp lệ mà vẫn sai nếu một trường đã chết giữa mẻ.
        try {
            long now = android.os.SystemClock.elapsedRealtime();
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=METER_FIELD_SUMMARY uid=%s stale=%s held=%b health=%s"
                            + " amount=%.0f start=%.0f end=%.0f",
                    mItem == null ? "null" : mItem.getUniqueId(),
                    MeterFieldHealth.shared().staleFields(now),
                    startNumberHeld,
                    MeterFieldHealth.shared().snapshotForLog(),
                    mItem == null ? 0d : mItem.getRealAmount(),
                    mItem == null ? 0d : mItem.getStartNumber(),
                    mItem == null ? 0d : mItem.getEndNumber()));
        } catch (Exception ignored) {
            // Ghi vết không bao giờ được phép làm hỏng việc kết thúc mẻ.
        }

        try {
            if (mItem != null) {
                mItem.setEndTime(new Date());

                // Giờ bắt đầu chỉ được lấy từ đúng một trong hai nguồn:
                //
                //   1. Thời điểm bắt được sự kiện start của đồng hồ (LCR/TCS) — ưu tiên
                //      tuyệt đối, đã chụp ở recordStartEvent() và lưu ngay xuống Room.
                //   2. Nếu không bắt được sự kiện đó: thời điểm bấm kết thúc thủ công,
                //      tức chính giờ kết thúc vừa đóng dấu ở trên.
                //
                // Trước đây chỗ này suy ngược StartTime = EndTime − (DeviceEndTime −
                // DeviceStartTime), đè lên cả mốc đã bắt đúng ở nguồn 1. Còn khi không bắt
                // được start thì StartTime giữ nguyên giá trị khởi tạo của RefuelItemData
                // (`new Date()` lúc dựng object từ kế hoạch bay) nên phiếu đi tiếp với giờ
                // tạo kế hoạch — lệch hàng giờ so với lúc tra nạp thật.
                //
                // PROCESSING là dấu vết bền của nguồn 1: chỉ recordStartEvent() đặt trạng
                // thái này, và nó sống sót qua việc Activity bị dựng lại giữa mẻ, khác cờ
                // startEventRecorded chỉ nằm trong bộ nhớ.
                boolean meterStartCaptured = startEventRecorded
                        || mItem.getStatus() == REFUEL_ITEM_STATUS.PROCESSING;

                if (meterStartCaptured) {
                    Logger.appendLog(LOG_TAG, "Giờ bắt đầu lấy theo sự kiện start của đồng hồ: "
                            + DateUtils.formatDate(mItem.getStartTime(), "dd/MM/yyyy HH:mm:ss"));
                } else {
                    mItem.setStartTime(mItem.getEndTime());
                    Logger.appendLog(LOG_TAG, "Không bắt được sự kiện start của đồng hồ,"
                            + " lấy giờ bấm kết thúc làm giờ bắt đầu: "
                            + DateUtils.formatDate(mItem.getStartTime(), "dd/MM/yyyy HH:mm:ss"));
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
            // Chỉ khoá nút Trở lại khi mẻ ĐANG chạy. Ở ENDED mẻ đã kết thúc, người dùng phải
            // còn đường rời màn hình — khoá cả ở đây từng làm màn hình chết khi lần chốt mẻ
            // chưa ghi được.
            btnBack.setEnabled(!isRefuelRunning(refuel_status));

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
                    // KHÔNG disable btnForceStop: nút Dừng khẩn phải LUÔN hiện và LUÔN bấm
                    // được ở mọi trạng thái. Trước đây tắt ở đây làm màn hình ENDED không
                    // còn nút nào đi tiếp khi lần chốt mẻ chưa ghi được.
                    findViewById(R.id.btnCancel).setVisibility(View.INVISIBLE);
                    break;

                case STARTED:
                    started = true;
                    lblrefuelStatus.setText(R.string.refuel_processing);
                    lblrefuelStatus.setBackgroundColor(getResources().getColor(R.color.bgSuccess, getTheme()));
                    // Hiển thị btnStart do setButtonText() quyết định (nút "Dừng" ở trạng
                    // thái STARTED); ẩn lại ở đây là thứ đã làm mất đường dừng bình thường.
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
                applyStartNumber(model.getEndMeterNumber(), model.getGrossQty());
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

    /**
     * Ghi số đồng hồ đầu mẻ, nhưng KHÔNG để một giá trị hỏng đè lên giá trị còn dùng được.
     *
     * <p>{@code StartNumber = EndMeter − Gross} chỉ đúng khi cả hai trường cùng sống. Khi
     * TOTALIZER đứng mà GROSSQTY vẫn tăng, hiệu này giảm dần và có thể ÂM rồi được ghi thẳng
     * xuống Room. Ở đây chỉ giữ nguyên giá trị cũ trong đúng hai ca đó — vẫn hiển thị, vẫn
     * lưu, vẫn cho kết thúc mẻ.
     *
     * <p>Việc này biến "số âm — sai rõ" thành "cặp số hợp lý nhưng có thể sai", nên bắt buộc
     * phải để lại dấu vết: ghi anomaly ở lần CHUYỂN TRẠNG THÁI (không phải mỗi nhịp một
     * giây) và một dòng tổng kết ở {@code finalizStop()}.
     */
    private void applyStartNumber(double endMeter, double gross) {
        if (mItem == null) return;

        boolean totalizerStale = MeterFieldHealth.shared().isStale(
                MeterFieldHealth.Field.TOTALIZER, android.os.SystemClock.elapsedRealtime());
        double previous = mItem.getStartNumber();
        boolean held = MeterFieldHealth.startNumberHeld(previous, endMeter, gross, totalizerStale);

        if (held != startNumberHeld) {
            startNumberHeld = held;
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=%s uid=%s totalizerStale=%b keptStart=%.0f end=%.0f gross=%.0f",
                    held ? "START_NUMBER_HELD" : "START_NUMBER_RESUMED",
                    mItem.getUniqueId(), totalizerStale, previous, endMeter, gross));
        }

        mItem.setStartNumber(MeterFieldHealth.resolveStartNumber(
                previous, endMeter, gross, totalizerStale));
    }

    /** Lần tính số đồng hồ đầu gần nhất có bị bỏ qua hay không; chỉ để ghi vết chuyển trạng thái. */
    private boolean startNumberHeld = false;

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
                applyStartNumber(tcsData.getGrossTotalRound(), tcsData.getGrossQtyRound());
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

    /** Nguồn của một lần lưu. Ba nguồn này có ba luật khác nhau, không được gộp làm một. */
    private enum SaveKind {
        /**
         * Số đo trung gian của LCR/TCS. Bóp theo {@link #MIN_AUTOSAVE_INTERVAL_MS}, và không
         * phục hồi bằng patch: lần sau của thiết bị sẽ mang số mới hơn.
         */
        DEVICE_AUTOSAVE,
        /**
         * Người dùng vừa nhập trong hộp thoại và hộp thoại đã đóng. Không bao giờ bị bóp
         * nhịp, và nếu bị chặn thì đắp lại đúng nhóm trường màn hình này cho nhập.
         *
         * <p>Nhịp autosave sinh ra để chặn TCS ghi vài lần mỗi giây, nhưng nó từng chặn
         * chung cả đường nhập tay: mọi hộp thoại ở đây (nhiệt độ, tỉ trọng, số hoá nghiệm,
         * bãi đỗ, tàu bay, chọn nhân viên) đều đi qua {@code updateBinding()}. Đồng hồ vừa
         * autosave dưới một giây là giá trị vừa gõ bị bỏ, mà {@code onResult} cũng không
         * được gọi nên màn hình không có gì để cảnh báo — hộp thoại đóng như đã lưu.
         */
        USER_EDIT,
        /**
         * Chuyển trạng thái một lần: bắt được sự kiện start của đồng hồ, bấm Tiếp cận.
         * Không bị bóp nhịp, nhưng cũng KHÔNG đi đường patch — nó ghi những trường ngoài
         * phạm vi nhập tay (giờ bắt đầu, TruckId/TruckNo, trạng thái).
         */
        STATE_EVENT
    }

    private void enqueueSave(boolean finalize,
                             java.util.function.Consumer<RefuelItemData> onResult) {
        enqueueSave(finalize, SaveKind.DEVICE_AUTOSAVE, onResult);
    }

    private void enqueueSave(boolean finalize, SaveKind kind,
                             java.util.function.Consumer<RefuelItemData> onResult) {
        final RefuelItemData source = mItem;
        if (source == null) return;

        if (!finalize && kind == SaveKind.DEVICE_AUTOSAVE) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAutosaveAt < MIN_AUTOSAVE_INTERVAL_MS) return;
            lastAutosaveAt = now;
        }

        final RefuelItemData snapshot = source.snapshotForSave();

        if (!finalize && snapshot.getStatus() == REFUEL_ITEM_STATUS.DONE) {
            // Mẻ đã được End chốt; autosave không còn việc gì ở đây.
            Logger.appendLog("RFW", "Bỏ autosave trên mẻ đã DONE, để đường End tự lưu");
            // Người dùng vừa nhập thì PHẢI biết là không lưu được. Trả kết quả rỗng để màn
            // hình cảnh báo, thay vì đóng hộp thoại im lặng như thể đã ghi.
            if (kind != SaveKind.DEVICE_AUTOSAVE && onResult != null) onResult.accept(null);
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
                // Màn hình này KHÔNG chặn mẻ của chuyến chưa phân công cho xe: người đang
                // đứng tại tàu bay biết rõ nhất mẻ nào vừa bơm. Việc bất thường được ghi vào
                // nhật ký để đối soát sau ca, nhưng dữ liệu luôn được nhận.
                result = DataHelper.postRefuelFromRefuelScreen(snapshot, false);

                // Lần chốt mẻ bị precondition chặn: thử lại theo kiểu PATCH — đọc row mới
                // nhất dưới khoá ghi rồi chỉ đắp đúng nhóm trường của vòng đời mẻ. Không có
                // bước này thì conflict thật lúc End là ngõ cụt: bấm "Thử lại" gửi lại đúng
                // baseline cũ nên hỏng mãi, số liệu mẻ nằm lại trên màn hình cho tới khi mất.
                if (finalize && result != null
                        && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                    // Nhịp hai PHẢI mang theo quyền tiếp quản như nhịp một: lời gọi này đến
                    // từ chính người đang đứng bơm. Dùng đường fail-closed ở đây là không
                    // nhất quán, và chính nó biến một xung đột tạm thời thành ngõ cụt cố
                    // định cho mẻ tra nạp hộ (chuyến chưa phân công cho xe).
                    Logger.appendLog("RFW", "Chốt mẻ bị chặn, thử lại bằng EndFieldsPatch");
                    result = DataHelper.saveEndFieldsFromRefuelScreen(snapshot);
                }

                // Đối xứng với đường End: người dùng vừa gõ mà baseline đã dịch (lượt pull
                // nền, hoặc autosave thiết bị vừa tăng ClientSeq) thì gõ lại là việc của
                // máy. Đo trên máy thật 27-08-2026 13:43:34: sửa bãi đỗ trên màn này trả
                // CONFLICT và REBASE_SCREEN_REFUSED, giá trị mất hẳn.
                if (!finalize && kind == SaveKind.USER_EDIT && result != null
                        && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                    Logger.appendLog("RFW", "Sửa tay bị chặn, thử lại bằng DetailFieldsPatch");
                    result = DataHelper.saveDetailFields(snapshot);
                }
            } catch (Throwable ex) {
                Logger.appendLog("RFW", "Lưu lỗi: " + ex);
                result = null;
            }

            // Hàng đợi ghi làm việc trên bản sao, nên phải trả phiên bản mới về cho đối
            // tượng của màn hình, nếu không lần lưu kế tiếp đứng trên baseline cũ.
            if (RefuelItemData.isCommitted(result)) {
                source.adoptSaveState(snapshot);
            } else if (result != null
                    && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.CONFLICT) {
                // Lưu bị chặn: màn hình PHẢI đứng lại lên row hiện tại. Nếu không, baseline
                // cũ ở lại mãi và mọi autosave sau đều hỏng — mỗi giây một lần cho tới khi
                // rời màn hình. rebaseScreenOnStored chỉ nhận khi payload nghiệp vụ không
                // đổi, nên xung đột thật vẫn bị chặn nguyên như cũ.
                DataHelper.rebaseScreenOnStored(source);
            }

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

        // Kết thúc mẻ mà chưa ghi được xuống Room thì PHẢI báo — nhưng KHÔNG chặn cứng.
        //
        // Bản chặn cứng trước đây chỉ có "Thử lại" và "Trở lại": khi nguyên nhân là một lỗi
        // dai dẳng (guard sở hữu chặn đường patch, xem DataHelper.saveScopedFields), mọi lần
        // Thử lại đều hỏng y hệt và người dùng đứng chết tại màn hình tra nạp — không sang
        // được màn xác nhận, cũng không làm được gì khác. Đo trên xe thật 30-08-2026 09:08:
        // ba lần bấm liên tiếp, cả ba đều FAILED.
        //
        // Người đứng tại tàu bay phải luôn còn đường đi tiếp. Cảnh báo rõ ràng để họ chủ động
        // đối chiếu lại số liệu ở màn hình sau, còn dấu vết thì đã đủ trong nhật ký.
        if (!RefuelItemData.isCommitted(itemData)) {
            // KHÔNG hộp thoại, KHÔNG chặn: ghi đủ nguyên nhân vào fms.log rồi ĐI TIẾP ngay.
            // Hộp thoại "Thử lại / Tiếp tục" trước đây vẫn là một lần chặn giữa lúc người
            // dùng đứng cạnh tàu bay, mà "Thử lại" thì vô nghĩa khi nguyên nhân cố định.
            // Màn hình Xác nhận có đường lưu riêng nên mẻ vẫn còn cơ hội được ghi ở đó.
            logEndSaveFailureCause(itemData);
            setRefuelStatus(REFUEL_STATUS.ENDED);
            continueWithoutSavedEnd(itemData);
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

    /**
     * Báo mẻ chưa ghi được, KHÔNG chặn quy trình.
     *
     * <p>Ba lối ra, không lối nào là ngõ cụt: "Thử lại" ghi lại, "Tiếp tục" đi tiếp kèm cảnh
     * báo phải đối chiếu số liệu, và huỷ hộp thoại thì ở lại màn hình tra nạp với đầy đủ thao
     * tác. Hộp thoại cũ chỉ có hai lối đầu-hàng nên một lỗi dai dẳng là kẹt hẳn.
     */
    /**
     * Ghi NGUYÊN NHÂN của lần chốt mẻ không vào được Room, vào {@code fms.log}.
     *
     * <p>Trước đây chỗ này chỉ ghi đúng mã kết quả ({@code CONFLICT} / {@code FAILED}) rồi bật
     * hộp thoại. Đọc lại nhật ký sau ca thì không đủ để biết vì sao: không rõ mẻ có thuộc xe
     * này không, bản gốc lệch bao nhiêu bậc, hay số liệu lúc đó là bao nhiêu. Ba câu hỏi đó
     * mới là thứ phân biệt hai nguyên nhân hoàn toàn khác nhau:
     *
     * <ul>
     *   <li><b>Chuyến chưa phân công cho xe</b> — mẻ mang dấu xe khác (hoặc chưa có dấu xe)
     *       nên đường vá từ chối ghi. Lý do CỐ ĐỊNH: thử lại bao nhiêu lần cũng hỏng y hệt.</li>
     *   <li><b>Bản gốc bị dịch</b> — một nguồn ghi khác vừa chạm vào đúng bản ghi này giữa lúc
     *       lệnh chốt còn trên đường. Lý do NHẤT THỜI: lần sau thường qua.</li>
     * </ul>
     *
     * <p>Ghi cả vào nhật ký chung lẫn nhật ký bất thường: cái trước để đọc theo dòng thời gian
     * của mẻ, cái sau để lọc nhanh khi đối soát cuối ca.
     */
    private void logEndSaveFailureCause(RefuelItemData itemData) {
        String outcome = itemData == null ? "FAILED" : String.valueOf(itemData.getSaveOutcome());
        String uid = mItem == null ? "null" : mItem.getUniqueId();

        int currentTruckId = 0;
        try {
            TruckModel setting = currentApp.getSetting();
            if (setting != null) currentTruckId = setting.getId();
        } catch (Exception ignored) {
            // Đọc cấu hình hỏng thì vẫn phải ghi được phần còn lại của chẩn đoán.
        }

        int itemTruckId = mItem == null ? 0 : mItem.getTruckId();
        String itemTruckNo = mItem == null ? "null" : mItem.getTruckNo();
        boolean assignedToThisTruck = itemTruckId > 0 && itemTruckId == currentTruckId;
        boolean unassigned = itemTruckId <= 0;

        // Bản gốc mà lệnh chốt mang theo, so với bản đang nằm trong máy.
        long baseSeq = mItem == null ? -1 : mItem.getBaseClientSeq();
        long storedSeq = itemData == null ? -1 : itemData.getClientSeq();
        boolean baselineMoved = baseSeq >= 0 && storedSeq >= 0 && storedSeq != baseSeq;

        String likelyCause;
        if (!assignedToThisTruck) {
            likelyCause = unassigned
                    ? "CHUYEN_CHUA_PHAN_CONG (mẻ chưa mang dấu xe nào)"
                    : "ME_MANG_DAU_XE_KHAC (truckId=" + itemTruckId
                            + " khác xe hiện tại " + currentTruckId + ")";
        } else if (baselineMoved) {
            likelyCause = "BAN_GOC_BI_DICH (nguồn ghi khác vừa chạm vào bản ghi này)";
        } else {
            likelyCause = "CHUA_XAC_DINH (xem dòng END_PATCH / PATCH_BLOCKED ngay trước dòng này)";
        }

        String detail = String.format(java.util.Locale.US,
                "END_SAVE_FAILED uid=%s outcome=%s likelyCause=%s"
                        + " assignedToThisTruck=%s itemTruckId=%d itemTruckNo=%s currentTruckId=%d"
                        + " baseClientSeq=%d storedClientSeq=%d baselineMoved=%s"
                        + " status=%s flight=%s amount=%.0f start=%.0f end=%.0f",
                uid, outcome, likelyCause,
                assignedToThisTruck, itemTruckId, itemTruckNo, currentTruckId,
                baseSeq, storedSeq, baselineMoved,
                mItem == null ? "null" : String.valueOf(mItem.getStatus()),
                mItem == null ? "null" : mItem.getFlightCode(),
                mItem == null ? 0d : mItem.getRealAmount(),
                mItem == null ? 0d : mItem.getStartNumber(),
                mItem == null ? 0d : mItem.getEndNumber());

        Logger.appendLog("RFW", detail);
        Logger.appendRefuelAnomaly("event=" + detail);
    }

    /**
     * Đi tiếp dù mẻ chưa ghi được vào Room.
     *
     * <p>KHÔNG nhận id/localId/uniqueId từ kết quả bị chặn: nó là bản đọc lại của row đang có
     * trong máy, không phải xác nhận rằng số liệu vừa bơm đã được ghi. Màn hình sau mở đúng
     * phiếu mà màn hình này đang giữ, và tự lưu lại được bằng đường patch của nó.
     *
     * <p>Cũng KHÔNG trừ tồn xe: tồn chỉ đổi tại lần lưu thực sự chuyển mẻ sang {@code DONE}.
     * Trừ ở đây thì lần lưu thành công sau đó sẽ trừ lần thứ hai.
     */
    private void continueWithoutSavedEnd(RefuelItemData itemData) {
        Logger.appendLog("RFW", "Người dùng chọn đi tiếp khi mẻ chưa ghi được ("
                + (itemData == null ? "FAILED" : itemData.getSaveOutcome())
                + "), uid=" + (mItem == null ? "null" : mItem.getUniqueId()));
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=END_SAVE_FAILED_CONTINUED uid=%s outcome=%s flight=%s"
                        + " amount=%.0f start=%.0f end=%.0f",
                mItem == null ? "null" : mItem.getUniqueId(),
                itemData == null ? "FAILED" : itemData.getSaveOutcome(),
                mItem == null ? "null" : mItem.getFlightCode(),
                mItem == null ? 0d : mItem.getRealAmount(),
                mItem == null ? 0d : mItem.getStartNumber(),
                mItem == null ? 0d : mItem.getEndNumber()));

        if (mItem == null) return;

        android.widget.Toast.makeText(this,
                R.string.warn_refuel_end_save_failed_continued,
                android.widget.Toast.LENGTH_LONG).show();

        if (cancelled)
            finish();
        else
            openConfirm();
    }

    private void showApproachConfirmIfNeeded() {
        if (mItem == null) return;
        if (mItem.getApproachTime() != null) return;
        if (approachPopupShown) return;

        approachPopupShown = true;

        track(new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("Chuyến này chưa ghi nhận tiếp cận. Bạn có muốn ghi nhận thời điểm tiếp cận lúc này không?")
                .setPositiveButton("Có", (dialog, which) -> {
                    dialog.dismiss();
                    checkApproachAllowed(() -> recordApproach(findViewById(R.id.btnApproach)));
                })
                .setNegativeButton("Không", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .create()).show();
    }

    /**
     * Ghi mốc tiếp cận cho phiếu đang mở và cập nhật hiển thị.
     *
     * @param btnApproach nút vừa bấm, ẩn đi sau khi ghi; có thể null nếu layout hiện tại
     *                    không có nút (bản dọc không hiển thị khối tiếp cận).
     */
    private void recordApproach(View btnApproach) {
        if (mItem == null) return;

        Date now = new Date();
        mItem.setApproachTime(now);

        TextView lblApproach = findViewById(R.id.lblApproachTime);
        if (lblApproach != null) {
            lblApproach.setText("Tiếp cận: " + DateUtils.formatDate(now, "dd/MM/yyyy HH:mm"));
            lblApproach.setVisibility(View.VISIBLE);
        }

        if (btnApproach != null) {
            btnApproach.setVisibility(View.GONE);
        }

        // Người dùng vừa bấm Tiếp cận: nút đã ẩn đi rồi nên lần lưu này không được phép rơi.
        enqueueSave(false, SaveKind.STATE_EVENT, RefuelDetailActivity.this::warnIfNotSaved);
    }

    /**
     * Chạy {@code onAllowed} trên UI thread nếu không còn chuyến nào đã tiếp cận mà chưa
     * bấm Rời đi; ngược lại hiện hộp thoại chặn và không làm gì thêm.
     */
    private void checkApproachAllowed(Runnable onAllowed) {
        new Thread(() -> {
            List<RefuelItemData> blocking = RefuelApproachGuard.findBlocking(
                    mItem == null ? null : mItem.getUniqueId());

            runOnUiThread(() -> {
                if (isFinishing()) return;

                if (blocking.isEmpty()) {
                    onAllowed.run();
                    return;
                }

                Logger.appendLog(LOG_TAG, "Chặn tiếp cận, còn " + blocking.size()
                        + " chuyến chưa rời đi");

                RefuelItemData blockingItem = blocking.get(0);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(RefuelApproachGuard.buildMessage(blocking))
                        .setPositiveButton(getString(R.string.leave_named_flight,
                                RefuelApproachGuard.flightName(blockingItem)),
                                (clickedDialog, which) -> {
                            clickedDialog.dismiss();
                            leaveBlockingAndContinue(blockingItem, onAllowed);
                        })
                        .setNegativeButton("Đã hiểu", (clickedDialog, which) -> clickedDialog.dismiss())
                        .setCancelable(false)
                        .create();
                dialog.setOnShowListener(ignored -> highlightLeaveButton(dialog));
                track(dialog).show();
            });
        }).start();
    }

    private void highlightLeaveButton(AlertDialog dialog) {
        Button leaveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (leaveButton == null) return;
        leaveButton.setBackgroundColor(Color.YELLOW);
        leaveButton.setTextColor(Color.BLACK);
    }

    private void leaveBlockingAndContinue(RefuelItemData blockingItem, Runnable onAllowed) {
        new Thread(() -> {
            boolean saved = RefuelApproachGuard.leaveNow(blockingItem);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!saved) {
                    Logger.appendLog(LOG_TAG, "Không lưu được giờ rời đi cho chuyến đang chặn");
                    showErrorMessage(R.string.save_leave_time_failed);
                    return;
                }

                // Kiểm tra lại thay vì tiếp cận thẳng: dữ liệu cũ có thể chứa hơn một chuyến
                // chưa rời đi. Khi đã sạch, tiếp tục thao tác mà người dùng vừa yêu cầu.
                checkApproachAllowed(onAllowed);
            });
        }).start();
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

        // CHỈ khi màn hình đang kết thúc, không phải mọi lần tạm dừng. Đây là thời điểm sớm
        // nhất biết chắc cửa sổ sắp biến mất; đóng hộp thoại ở đây thì không còn nút nào để
        // bấm vào một Activity đã chết. Tắt màn hình hay chuyển app KHÔNG rơi vào nhánh này —
        // hộp nhập tay đang giữ số đồng hồ người dùng vừa gõ, đóng nó là mất dữ liệu thật.
        if (isFinishing()) dismissOpenDialogs();
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
        // Lưới cuối: có đường kết thúc không đi qua onPause với isFinishing() đã bật (bị hệ
        // thống thu hồi, đổi cấu hình). Hộp thoại sót lại là một cửa sổ mồ côi.
        dismissOpenDialogs();
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
