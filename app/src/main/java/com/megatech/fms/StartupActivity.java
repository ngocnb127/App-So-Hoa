package com.megatech.fms;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.megatech.fms.helpers.InstallHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.TruckAPI;
import com.megatech.fms.model.TruckModel;

import java.util.concurrent.Callable;

public class StartupActivity extends BaseActivity {
    protected int SETTING_CODE = 2;
    protected int MAGICAL_NUMBER = 1;

    private final int REFUEL_CONTINUE = 44567;

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    private void showLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, LOGIN_CODE);
    }

    private final int REQUEST_WRITE_PERMISSION = 1;
    private final int REQUEST_BLUETOOTH_PERMISSION = 4;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkStoragePermission();

        if (currentApp.isLoggedin()) {
            if (currentApp.isFirstUse()) {
                setting();
            } else {
                showMain();
            }
        } else {

            showLogin();
        }

    }

    private final int LOGIN_CODE = 44563;

    private void showMain() {

        updateSetting();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();


    }

    private void updateSetting() {
       new Thread(new Runnable() {
           @Override
           public void run() {

               TruckModel setting = getSetting();
               if (setting.getTabletSerial() ==null) {
                   setting.setTabletSerial(getTabletId());
                   FMSApplication.getApplication().saveSetting(setting);

               }
               TruckModel model = new TruckAPI().getTruck();
               if (model!=null && setting.getReceiptCount() < model.getReceiptCount())
                   FMSApplication.getApplication().saveSetting(model);
           }
       }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == LOGIN_CODE) {
            if (resultCode == 0 && !currentApp.isFirstUse()) {
                showMain();
            } else
                setting();
        } else
            super.onActivityResult(requestCode, resultCode, data);
    }

    private void setting() {
        Intent intent = new Intent(this, SettingActivity.class);
        startActivityForResult(intent, SETTING_CODE);
        finish();
    }



    protected boolean checkStoragePermission() {

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
           Log.i("STARTUP","NO BLUETOOTH ENABLED");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED )
            {

                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT},REQUEST_BLUETOOTH_PERMISSION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED||
                    checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED||
                    checkSelfPermission(Manifest.permission.INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.REQUEST_INSTALL_PACKAGES,
                        Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_PHONE_STATE ,
                        Manifest.permission.BLUETOOTH ,
                        Manifest.permission.BLUETOOTH_ADMIN ,

                        Manifest.permission.BLUETOOTH_PRIVILEGED ,
                        Manifest.permission.INSTALL_PACKAGES  }, REQUEST_WRITE_PERMISSION);

            }


        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    private String getTabletId()
    {
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
          return deviceUniqueIdentifier;
        }
        catch (SecurityException e) {
            return null;
        }
    }


}
