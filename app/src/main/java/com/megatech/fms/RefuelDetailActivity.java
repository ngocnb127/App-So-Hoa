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
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

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
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.LogEntryModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

public class RefuelDetailActivity extends UserBaseActivity implements View.OnClickListener {

    private List<AirlineModel> airlines = null;

    private List<TruckModel> trucks = null;

    private Button btnReconnect;
    private Button btnRestart;
    AlertDialog inputDlg;
    private Button btnBack;
    private final String LOG_TAG = "RFW";

    private enum CONNECTION_STATUS {
        OK,
        ERROR,
        CONNECTING
    }

    private RefuelItemData mItem;
    private Activity activity;
    private final boolean isEditing = false;
    private final boolean restartRequest = false;
    ActivityRefuelDetailBinding binding;
    Timer tmrCheckData = new Timer();
    private Button btnForceStop;
    private Date lastErrorTime = null;
    private int endRetry = 0;

    private String deviceSerial;

    private void setTextBoxValue(int id, Object value) {
        setTextBoxValue(id, value, "%s");
    }

    private void setTextBoxValue(int id, Object value, String pattern) {
        if (value != null)
            ((TextView) findViewById(id)).setText(String.format(pattern, value));
    }

    private void reconnect() {

        setConnectionCheckmark(CONNECTION_STATUS.CONNECTING);

        reader.doConnectDevice();
    }

    private String m_Text = "";


    ///Reader define

    private LCRReader reader = null;
    private LCRDataModel model;
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
        runOnUiThread(() -> {
            switch (status) {
                case OK:
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr)).setChecked(true);
                    ((TextView) findViewById(R.id.lbl_connection_status)).setText(getString(R.string.lcr_connection_ok));
                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(getResources().getColor(R.color.colorDarkGreen, getTheme()));
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr)).setCheckMarkDrawable(R.drawable.ic_checked_circle);
                    btnReconnect.setVisibility(View.INVISIBLE);
                    //btnStart.setEnabled(true);
                    //btnForceStop.setVisibility(View.INVISIBLE);
                    break;
                case CONNECTING:
                    CheckedTextView chkTxt = findViewById(R.id.refuel_detail_chk_connect_lcr);
                    if (chkTxt != null)
                        chkTxt.setCheckMarkDrawable(null);
                    findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

                    ((TextView) findViewById(R.id.lbl_connection_status)).setText(getString(R.string.lcr_connection_connecting));
                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(Color.BLACK);
                    btnReconnect.setVisibility(View.VISIBLE);
                    btnRestart.setVisibility(View.VISIBLE);
                    btnReconnect.setEnabled(false);
                    //btnRestart.setEnabled(false);
                    break;
                case ERROR:
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    ((TextView) findViewById(R.id.lbl_connection_status)).setText(getString(R.string.lcr_connection_error));
                    ((TextView) findViewById(R.id.lbl_connection_status)).setTextColor(Color.RED);
                    ((CheckedTextView) findViewById(R.id.refuel_detail_chk_connect_lcr)).setCheckMarkDrawable(R.drawable.ic_error);
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

        builder.setPositiveButton(getString(id == R.id.btnStart ? R.string.start : R.string.stop), (dialog, id12) -> {
            Logger.appendLog(id == R.id.btnStart ? "Confirm start " : "Confirm stop");
            if (id == R.id.btnStop) {
                stop();
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
                    initReader();
                    if (!isFinishing())
                        showData();
                });
            }

        }).start();


    }

    private void initReader() {
        Logger.appendLog(LOG_TAG, "Init LCR Reader");
        reader = LCRReader.create(this, storedIP, 10001, false);
        if (reader.isLCR600())
            btnStart.setVisibility(View.VISIBLE);

        addListeners();
        deviceIsReady = reader.getConnected();
        if (deviceIsReady) {
            setConnectionCheckmark(CONNECTION_STATUS.OK);
            //reader.requestSerial();
        } else
            reader.doConnectDevice();

        this.model = new LCRDataModel();
        model.setUserId(currentUser.getUserId());


        if (reader.isAlreadyStarted())
            onStarted();
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

        setTextBoxValue(R.id.refuelitem_projected_capacity, mItem.getProjectedCapacity(), "%.0f");
        setTextBoxValue(R.id.refuelitem_actual_capacity, mItem.getActualCapacity(), "%.0f");

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
        closeProgressDialog();
    }

    private void start() {

        startButtonPress = true;
        mItem.setStartTime(new Date());
        // send START DELIVERY command to LCR
        reader.start();
        //setRefuelStatus(REFUEL_STATUS.STARTING);

    }

    private void stop() {
        if (deviceIsError) {
            showForceStopDialog();
        } else {
            //send END DELIVERY command to LCR
            reader.end();
            setRefuelStatus(REFUEL_STATUS.ENDING);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.appendLog("RFW", "Data changed - post item");
                DataHelper.postRefuel(mItem, false);
            }
        }).start();

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

    private boolean cancelled = false;

    private void cancelRefuel() {
        mItem.setStatus(REFUEL_ITEM_STATUS.NONE);
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

    private void onStopped() {
        //if (!isActive) return;

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
                    doStop();
                }
            }
        });

    }


    private void onStarted() {
        Logger.appendLog("RFW", "onStarted " + started);
        if (!isActive) return;
        if (!started) {
            reader.requestData();
            field_data_flag = FIELD_DATA_FLAG.FIELD_NOT_DEFINED;
            tmrCheckData = new Timer();
            TimerTask tmrTask = new TimerTask() {
                @Override
                public void run() {

                    if (!reader.isDeviceError()) {
                        if ((field_data_flag & FIELD_DATA_FLAG.FIELD_GROSSQTY) == 0
                                || (field_data_flag & FIELD_DATA_FLAG.FIELD_TOTALLIZER) == 0) {
                            tmrCheckData.cancel();
                            reader.requestData();
                        }

                    }
                    field_data_flag = FIELD_DATA_FLAG.FIELD_NOT_DEFINED;
                }
            };
            tmrCheckData.scheduleAtFixedRate(tmrTask, 1000 * 10, 1000 * 60);

            started = true;
            setRefuelStatus(REFUEL_STATUS.STARTED);
            new Thread(() -> {
                if (mItem != null) {
                    Logger.appendLog("RFW", "Set item processing status");
                    mItem.setStartTime(new Date());

                    mItem.setTruckId(currentApp.getTruckId());
                    mItem.setTruckNo(currentApp.getTruckNo());
                    mItem.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
                    DataHelper.postRefuel(mItem, false);

                }
            }).start();

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
            });

            saveData();
        }
        //Log.e("REFUEL", "Update refuel data");
    }

    private void doStop() {
        //Logger.appendLog("RFW", "doStop");
        setRefuelStatus(REFUEL_STATUS.ENDED);
        started = false;
        reader.setRefuel(false);

        try {
            if (mItem != null) {

                mItem.setEndTime(new Date());
                if (Math.abs(mItem.getEndTime().getTime() - mItem.getStartTime().getTime()) > 1000 * 60 * 60 * 24)
                    mItem.setStartTime(mItem.getEndTime());

                if (mItem.getDeviceEndTime() != null && mItem.getDeviceStartTime() != null && !mItem.isCompleted()) {
                    long dateDiff = mItem.getDeviceEndTime().getTime() - mItem.getDeviceStartTime().getTime();
                    if (dateDiff > 0)
                        mItem.setStartTime(new Date(mItem.getEndTime().getTime() - dateDiff));
                }

                if (mItem.getManualTemperature() == 0)
                    mItem.setManualTemperature(model.getTemperature());
                if (mItem.getOriginalEndMeter() == 0)
                    mItem.setOriginalEndMeter(model.getEndMeterNumber());
                boolean isExtract = mItem.getRefuelItemType() == RefuelItemData.REFUEL_ITEM_TYPE.EXTRACT;
                currentApp.setCurrentAmount(currentApp.getCurrentAmount() + (float) (isExtract ? mItem.getRealAmount() : -mItem.getRealAmount()));
                mItem.setStatus(REFUEL_ITEM_STATUS.DONE);
                if (!BuildConfig.FHS) {
                    if (mItem.getTruckId() != currentApp.getTruckId() && mItem.getReceiptNumber() != null && !mItem.getReceiptNumber().isEmpty()) {
                        mItem.setReceiptNumber(null);
                    }
                    mItem.setTruckId(currentApp.getTruckId());
                    mItem.setTruckNo(currentApp.getTruckNo());
                }
                //mItem.setStartNumber(mItem.getEndNumber() - mItem.getRealAmount());
            }

            //Logger.appendLog("RFW", "end doStop");
            postData();
        } catch (Exception e) {
            this.saveLog(LogEntryModel.LOG_TYPE.ERROR_LOG, " doStop Error: " + e.getLocalizedMessage());
        }


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
            intent.putExtra("REFUEL", mItem.toJson());
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

    private void saveData() {
        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                //Logger.appendLog("RFW", "saving item");
                return DataHelper.postRefuel(mItem, false);
            }

            @Override
            protected void onPostExecute(RefuelItemData itemData) {
                //Logger.appendLog("RFW", "saving item completed");
                if (itemData!=null) {
                    if (itemData.getId() != mItem.getId())
                        mItem.setId(itemData.getId());
                    if (itemData.getLocalId() != mItem.getLocalId())
                        mItem.setLocalId(itemData.getLocalId());
                    if (itemData.getUniqueId() != mItem.getUniqueId())
                        mItem.setUniqueId(itemData.getUniqueId());
                    currentApp.saveCurrentRefuel(mItem.getId(), mItem.getLocalId());
                }
                super.onPostExecute(itemData);

            }
        }.execute();
    }

    private void postData() {

        new AsyncTask<Void, Void, RefuelItemData>() {
            @Override
            protected RefuelItemData doInBackground(Void... voids) {
                Logger.appendLog("RFW", "Post item");
                return DataHelper.postRefuel(mItem, false);
            }

            @Override
            protected void onPostExecute(RefuelItemData itemData) {
                Logger.appendLog("RFW", "Post item completed");

                postRefuelCompleted(itemData);
                super.onPostExecute(itemData);

            }
        }.execute();
    }

    private void postRefuelCompleted(RefuelItemData itemData) {
        if (itemData != null) {
            if (itemData.getId() != mItem.getId())
                mItem.setId(itemData.getId());
            if (itemData.getLocalId() != mItem.getLocalId())
                mItem.setLocalId(itemData.getLocalId());
            if (itemData.getUniqueId() != mItem.getUniqueId())
                mItem.setUniqueId(itemData.getUniqueId());
        }
        if (cancelled)
            finish();
        else
            openConfirm();
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
        //Logger.appendLog("RFW", "Power on destroy");

        super.onDestroy();
        isActive = false;
        clearListeners();
        //reader.doDisconnectDevice();
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

        int FIELD_ALL = 31;
        int FIELD_ALL_DATA = 30;
        int FIELD_ALL_METER = 12;
    }
}
