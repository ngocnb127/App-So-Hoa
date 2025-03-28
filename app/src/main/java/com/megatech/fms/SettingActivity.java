package com.megatech.fms;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;
import com.megatech.fms.databinding.ActivitySettingBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.LCRReader;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.TruckModel;

import java.lang.ref.WeakReference;
import java.util.List;

//import static com.megatech.fms.BuildConfig.IMEI_LIST;

public class SettingActivity extends UserBaseActivity {

    private final Context ctx = this;

    private  LCRReader reader;

    private TruckModel settingModel;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        //if (!currentApp.isFirstUse())
        //    finish();
        settingModel = currentApp.getSetting();



        RadioButton rad520 = (RadioButton) findViewById(R.id.rad_zq520);
        RadioButton rad511 = (RadioButton) findViewById(R.id.rad_zq511);
        findViewById(R.id.row_thermal_printer_type).setVisibility(BuildConfig.THERMAL_PRINTER?View.VISIBLE:View.GONE);

        findViewById(R.id.btnBack).setVisibility(currentApp.isFirstUse()?View.GONE:View.VISIBLE);

        final Button btnTest = findViewById(R.id.btnTest);
        final Button btnSave = findViewById(R.id.btnUpdate);
        final Button btnBack =findViewById(R.id.btnBack);
        final EditText txtIp = findViewById(R.id.txtIP);
        final EditText txtPrinter = findViewById(R.id.txtPrinter);
        final EditText txtIMEI = findViewById(R.id.txtIMEI);


        new LoadTrucksAsync(this).execute();

        txtIp.setText(settingModel.getDeviceIP());
        txtPrinter.setText(settingModel.getPrinterIP());

        rad520.setChecked(settingModel.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ520);
        rad511.setChecked(settingModel.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ511);

        getDeviceIMEI();

        btnBack.setOnClickListener(view -> finish());
        btnTest.setOnClickListener(v -> {
            v.setEnabled(false);
            btnSave.setEnabled(false);
            btnTest.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            String ip = txtIp.getText().toString();
            reader =  new LCRReader(ctx, ip);

            reader.setConnectionListener(new LCRReader.LCRConnectionListener() {
                @Override
                public void onConnected() {

                    reader.requestSerial();

                }

                @Override
                public void onError() {
                    v.setEnabled(true);
                    btnSave.setEnabled(true);
                    btnTest.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.ic_error, getTheme()), null, null, null);
                    reader.destroy();
                }

                @Override
                public void onDeviceAdded(boolean failed) {

                }

                @Override
                public void onDisconnected() {

                }

                @Override
                public void onCommandError(LCR_COMMAND command) {

                }

                @Override
                public void onConnectionStateChange(LCR_DEVICE_CONNECTION_STATE state) {

                }
            });

            reader.setFieldDataListener(new LCRReader.LCRDataListener() {
                @Override
                public void onDataChanged(LCRDataModel dataModel, LCRReader.FIELD_CHANGE field_change) {
                    v.setEnabled(true);
                    btnSave.setEnabled(true);
                    if (field_change == LCRReader.FIELD_CHANGE.SERIAL) {
                        settingModel.setDeviceSerial(dataModel.getSerialId());
                        reader.destroy();
                        btnTest.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.ic_check, getTheme()), null, null, null);
                    }
                }

                @Override
                public void onErrorMessage(String errorMsg) {

                }

                @Override
                public void onFieldAddSucess(String field_name) {

                }
            });

            reader.doConnectDevice();
        });
        ProgressBar loading_bar = findViewById(R.id.loading_bar);

        btnSave.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                loading_bar.setVisibility(View.VISIBLE);
                String ip = txtIp.getText().toString();

                String printerAddress = txtPrinter.getText().toString();

                settingModel.setDeviceIP(ip);
                settingModel.setPrinterIP(printerAddress);


                if (BuildConfig.THERMAL_PRINTER)
                {
                    RadioButton rad = (RadioButton) findViewById(R.id.rad_zq520);

                    settingModel.setThermalPrinterType(rad.isChecked()? TruckModel.THERMAL_PRINTER_TYPE.ZQ520: TruckModel.THERMAL_PRINTER_TYPE.ZQ511);
                }
                EditText editText = findViewById(R.id.txtCurrentAmount);
                float currentAmount = Float.parseFloat(editText.getText().toString());
                settingModel.setCurrentAmount(currentAmount);
                currentApp.saveSetting(settingModel);

                loading_bar.setVisibility(View.GONE);

                setResult(Activity.RESULT_OK);

                finish();
                //restartApp();
            }
        });

        //getSSID();

    }

    private void getSSID() {
        WifiManager wifiManager = (WifiManager)
                this.getSystemService(Context.WIFI_SERVICE);

        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess(wifiManager);
                } else {
                    // scan failure handling
                    scanFailure(wifiManager);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        this.registerReceiver(wifiScanReceiver, intentFilter);

        wifiManager.startScan();



    }
    private void scanSuccess(WifiManager wifiManager) {
        List<ScanResult> results = wifiManager.getScanResults();
    }

    private void scanFailure(WifiManager wifiManager) {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        List<ScanResult> results = wifiManager.getScanResults();
    }

    private void restartApp() {
        Intent intent = new Intent(this, StartupActivity.class);
        int mPendingIntentId = MAGICAL_NUMBER;
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId, intent,  PendingIntent.FLAG_IMMUTABLE);
        AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    private final int MAGICAL_NUMBER = 245634;
    private final int REQUEST_READ_PHONE_STATE=1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_PHONE_STATE) {
            assignIMEI();
        }
    }

    public void getDeviceIMEI() {

        try {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_READ_PHONE_STATE);
            } else {

                assignIMEI();

            }
        }catch (SecurityException e)
        {

            Log.e("ERROR",e.getMessage());
        }

    }

    private void assignIMEI() {
        try {

            String deviceUniqueIdentifier = null;
            if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
                if (null != tm) {
                    deviceUniqueIdentifier = tm.getDeviceId();
                }
            }
            if (null == deviceUniqueIdentifier || 0 == deviceUniqueIdentifier.length()) {
                deviceUniqueIdentifier = "SOFTWARE_ID_" + Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
            if (null == deviceUniqueIdentifier || 0 == deviceUniqueIdentifier.length()) {

            }
            settingModel.setTabletSerial(deviceUniqueIdentifier);
            ((EditText)findViewById(R.id.txtIMEI)).setText(settingModel.getTabletSerial());
        }
        catch (SecurityException e)
        {

        }

    }

    private void showMessage(String message, boolean error)
    {
        if (error)
        {
            final EditText txtIp = findViewById(R.id.txtIP);
            txtIp.setError(message);
            Log.e("Failed ",message);

        }
        else {
            Toast.makeText(this,message,Toast.LENGTH_LONG).show();

            Log.i("OK",message);
        }

    }
    private void setButtonEnable(boolean enabled)
    {
        Button btnTest = findViewById(R.id.btnTest);
        btnTest.setEnabled(enabled);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    public static class LoadTrucksAsync extends AsyncTask<Void, Void, List<TruckModel>>
    {
        private final WeakReference<SettingActivity> activityWeakReference;

        public  LoadTrucksAsync(SettingActivity ctx)
        {
                activityWeakReference = new WeakReference<>(ctx);
        }
        @Override
        protected List<TruckModel> doInBackground(Void... voids) {

            return DataHelper.getTrucks();
        }

        @Override
        protected void onPostExecute(List<TruckModel> trucks) {
            activityWeakReference.get().populateTrucks(trucks);
        }
    }

    private void populateTrucks(List<TruckModel> trucks) {

        ArrayAdapter<TruckModel> spinnerAdapter = new ArrayAdapter<TruckModel>(this, R.layout.support_simple_spinner_dropdown_item, trucks);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        final Spinner truckSpinner = findViewById(R.id.spinner_truck);
        truckSpinner.setAdapter(spinnerAdapter);
        truckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TruckModel model = (TruckModel) parent.getItemAtPosition(position);
                if (model != null) {
                    settingModel.setTruckNo(model.getTruckNo());
                    settingModel.setId(model.getId());
                    settingModel.setCapacity(model.getCapacity());
                    settingModel.setReceiptCode(model.getReceiptCode());
                    settingModel.setReceiptCount(model.getReceiptCount());
                    ((EditText) findViewById(R.id.txtCurrentAmount)).setText(String.format("%.0f", model.getCurrentAmount()));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        if (trucks.size() > 0) {
            TruckModel model = (TruckModel) truckSpinner.getItemAtPosition(0);
            if (model != null) {
                settingModel.setTruckNo(model.getTruckNo());
                settingModel.setId(model.getId());
                settingModel.setCapacity(model.getCapacity());
                settingModel.setReceiptCode(model.getReceiptCode());
                settingModel.setReceiptCount(model.getReceiptCount());
                ((EditText) findViewById(R.id.txtCurrentAmount)).setText(String.format("%.0f", model.getCurrentAmount()));
            }
        }
    }
}
