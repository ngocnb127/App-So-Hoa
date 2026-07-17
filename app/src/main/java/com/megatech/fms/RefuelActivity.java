package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.LCRReader;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.List;


public class RefuelActivity extends UserBaseActivity  implements View.OnClickListener {

    private LCRReader reader;
    private final List<String> loggerList = new ArrayList<>();
    HttpClient client = new HttpClient();

    @Override
    public void finish() {
        setResult(result);

        super.finish();
    }
    private final int result = Activity.RESULT_OK;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refuel);

        findViewById(R.id.toolbar).setVisibility(View.GONE);


        String storedIP = currentApp.getDeviceIP();
        prepareFlightInfo();

        this.model = new LCRDataModel();
        model.setUserId(currentUser.getUserId());

        reader =   LCRReader.create(this, storedIP, 10001, false);// LCRReader.create(this,storedIP, 10001);

        deviceIsReady = reader.getConnected();

        if (deviceIsReady)
        {
            findViewById(R.id.progressBar2).setVisibility(View.GONE);
            ((CheckedTextView)findViewById(R.id.refuel_chk_connect_lcr)).setChecked(true);
            ((CheckedTextView)findViewById(R.id.refuel_chk_connect_lcr)).setCheckMarkDrawable(R.drawable.ic_checked_circle);
        }

        addListeners();

    }
    RefuelItemData refuelData;

    private void prepareFlightInfo() {
        Bundle b = getIntent().getExtras();
        Integer flightId = b.getInt("REFUEL_ID",2);
        String mData = b.getString("REFUEL","");
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

        refuelData = gson.fromJson(mData, RefuelItemData.class);

        if (refuelData == null && flightId !=null) {
            refuelData = client.getRefuelItem(flightId);
        }
        if (refuelData!=null){
            ((TextView)findViewById(R.id.txtFlightCode)).setText(refuelData.getFlightCode());
            ((TextView)findViewById(R.id.txtAircraftCode)).setText(refuelData.getAircraftCode());
            ((TextView)findViewById(R.id.txtParkingLot)).setText(refuelData.getParkingLot());
            ((TextView)findViewById(R.id.txtEstimateAmount)).setText(String.format("%.2f",refuelData.getEstimateAmount()));

        }
    }

    private boolean deviceIsReady = false;
    private boolean conditionIsReady = false;
    private  boolean inventoryIsReady = false;
    private boolean startButtonPress = false;

    private void addListeners() {

        Button btnStart  = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmDialog(R.id.btnStart);

            }
        });
        Button btnPause  = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause();

            }
        });
        Button btnStop  = findViewById(R.id.btnStop);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmDialog(R.id.btnStop);


            }
        });

        reader.setConnectionListener(new LCRReader.LCRConnectionListener() {
            @Override
            public void onConnected() {
                deviceIsReady = true;
                findViewById(R.id.progressBar2).setVisibility(View.GONE);
                ((CheckedTextView)findViewById(R.id.refuel_chk_connect_lcr)).setChecked(true);
                ((CheckedTextView)findViewById(R.id.refuel_chk_connect_lcr)).setCheckMarkDrawable(R.drawable.ic_checked_circle);
                setEnableButton(deviceIsReady && conditionIsReady && inventoryIsReady);

            }

            @Override
            public void onError() {

                findViewById(R.id.progressBar2).setVisibility(View.GONE);
                ((CheckedTextView)findViewById(R.id.refuel_chk_connect_lcr)).setCheckMarkDrawable(R.drawable.ic_error);
            }

            @Override
            public void onDeviceAdded(boolean failed) {

                //reader.doConnectDevice();
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                TextView txtGrossQty = findViewById(R.id.txtGrossQty);
                txtGrossQty.setText(String.format("%.2f",dataModel.getGrossQty()));

                TextView txtTemp = findViewById(R.id.txtTemp);
                txtTemp.setText(String.format("%.2f",dataModel.getTemperature()));

                ((TextView)findViewById(R.id.txtStartMeter)).setText(String.format("%.2f",dataModel.getStartMeterNumber()));
                ((TextView)findViewById(R.id.txtEndMeter)).setText(String.format("%.2f",dataModel.getEndMeterNumber()));
                m_Text = String.format("%.2f",dataModel.getTemperature());

                setTruckInfo();
                model = dataModel;
            }

            @Override
            public void onErrorMessage(String errorMsg) {
                loggerList.add(errorMsg);
                if (loggerList.size()>15)
                    loggerList.remove(0);
                String logText = "";
                for (String err: loggerList) {
                    logText = logText + err +"\n";

                }
            }

            @Override
            public void onFieldAddSucess(String field_name) {

            }
        });

        reader.setStateListener(new LCRReader.LCRStateListener() {
            @Override
            public void onEndDelivery() {

            }

            @Override
            public void onStart() {
                //reader.requestData();
                setEnableButton(false);
            }

            @Override
            public void onStop() {
                //reader.stopRequestData();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        doStop();
                    }
                });
            }
        });
    }
    private String m_Text = "";
    private void showStopDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.refuel_mannual_temperature));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(m_Text);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_Text = input.getText().toString();
                refuelData.setManualTemperature(Double.parseDouble(m_Text));
                stop();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
    private void doStop() {

        //setEnableButton(true, false);
        ///post data
        try{
            if (refuelData!=null) {
                refuelData.setRealAmount( model.getGrossQty());
                refuelData.setTemperature(model.getTemperature());
                refuelData.setStartTime(model.getStartTime());
                refuelData.setEndTime(model.getEndTime());
                //refuelData.setStartNumber(model.getStartMeterNumber());
                refuelData.setStartNumber(model.getEndMeterNumber() - model.getGrossQty());
                refuelData.setEndNumber(model.getEndMeterNumber());
                refuelData.setOriginalEndMeter(model.getEndMeterNumber());
                currentApp.setCurrentAmount(currentApp.getCurrentAmount()-model.getGrossQty());
                openPreview();
            }

        }
        catch (Exception e)
        {}
    }

    private void openPreview() {

        finish();
/*
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

        String data = gson.toJson(refuelData);

        Intent intent = new Intent(this, RefuelPreviewActivity.class);
        intent.putExtra("REFUEL", data);
        startActivityForResult(intent, PREVIEW_OPEN);
        //finishAffinity();

*/
    }
    private final int PREVIEW_OPEN = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.finish();
    }

    private LCRDataModel model;

    private void start() {


        startButtonPress = true;

        reader.start();

    }

    private void showConfirmDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(id == R.id.btnStart ? R.string.start_confirm : R.string.stop_confirm);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (id == R.id.btnStop) {
                    stop();
                } else if (id == R.id.btnStart) {
                    start();

                }
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        builder.create().show();

    }

    private void pause()
    {
        reader.pause();
    }
    private void stop() {
        reader.end();


    }


    private void setEnableButton(boolean enabled)
    {
        findViewById(R.id.btnStart).setEnabled(enabled);
        findViewById(R.id.btnStop).setEnabled(!enabled && startButtonPress);
    }

    protected void onDestroy() {
        super.onDestroy();
//        if (reader!=null)
//            reader.destroy();
    }

    @Override
    public void onClick(View v) {

        int id = v.getId();
        if (id == R.id.refuel_chk_connect_lcr) {
            if (reader.isDeviceError())
                reconnect();
            return;
        }
        CheckedTextView chkTxt = findViewById(id);

        boolean isChecked = chkTxt.isChecked();
        chkTxt.setChecked(!isChecked);
        chkTxt.setCheckMarkDrawable(isChecked ? R.drawable.ic_unchecked : R.drawable.ic_checked);
        if (id == R.id.refuel_check_condition)
            conditionIsReady = !isChecked;
        else
            inventoryIsReady = !isChecked;
        setEnableButton(deviceIsReady && conditionIsReady && inventoryIsReady);



    }

    private void reconnect() {
        CheckedTextView chkTxt = findViewById(R.id.refuel_chk_connect_lcr);
        chkTxt.setCheckMarkDrawable(null);
        findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);
        reader.doConnectDevice();
    }
}
