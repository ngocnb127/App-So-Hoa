package com.megatech.fms;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.adapters.ImageViewBindingAdapter;
import androidx.legacy.content.WakefulBroadcastReceiver;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.LogEntryModel;


import static com.megatech.fms.BuildConfig.DEBUG;

public class UserBaseActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkLogin()) {
            finish();
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }

/*
        if (currentApp.isFirstUse())
        {

            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
        }
*/


    }

    public  final static  String SYNC_BROADCAST = "com.megatech.syn_broadcast";

    @Override
    protected void onResume() {
        try {
            super.onResume();
            setTruckInfo();
        }
        catch (Exception ex)
        {
            Logger.appendLog("UserBaseActivity",ex.getMessage());
        }

    }

    protected void setTruckInfo() {
        // Nhiều màn hình dùng layout không có thanh toolbar nên không có nhãn này. Trước đây
        // chỗ đó ném NPE, bị catch ở onResume và ghi log — 136 dòng nhiễu mỗi ngày mỗi xe,
        // đồng thời chặn luôn phần việc phía sau của setTruckInfo.
        TextView lblInventory = findViewById(R.id.lbltoolbar_Inventory);
        if (lblInventory == null) return;

        String truckNo = currentApp.getTruckNo();
        String amountStr = String.format("%.0f", currentApp.getCurrentAmount());

        String fullText = truckNo + ": " + amountStr;

        SpannableString spannable = new SpannableString(fullText);

// vị trí bắt đầu của số
        int start = fullText.indexOf(amountStr);
        int end = start + amountStr.length();

// màu đỏ
        spannable.setSpan(
                new ForegroundColorSpan(Color.BLUE),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

// in đậm
        spannable.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        // 🔴 Tăng size (1.2f = lớn hơn 20%)
        spannable.setSpan(
                new RelativeSizeSpan(1.5f),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        lblInventory.setText(spannable);
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean hasModified = DataHelper.checkLocalModified();
                return  hasModified;
            }

            @Override
            protected void onPostExecute(Boolean hasModified) {
                super.onPostExecute(hasModified);

            }
        }.execute();



    }


    protected void setToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            Button btnInvoice = findViewById(R.id.btnInvoice);
            btnInvoice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    invoice();
                }
            });
            Button btnInventory = findViewById(R.id.btn_2502);
            btnInventory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openBM2502();
                }
            });

//            Button btn_2505 = findViewById(R.id.btn_bm_2505);
//            if (btn_2505 != null)
//                btn_2505.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        openBM2505();
//                    }
//                });
//
//            Button btn_2508 = findViewById(R.id.btn_bm_2508);
//            if (btn_2508 != null)
//                btn_2508.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        openBM2508();
//                    }
//                });
//
//            Button btn_2307 = findViewById(R.id.btn_a_2307);
//            if (btn_2307 != null)
//                btn_2307.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        openA2307();
//                    }
//                });

            Button btnBieuMau = findViewById(R.id.btnBieuMau);
            if (btnBieuMau != null) {
                btnBieuMau.setOnClickListener(v -> openBieuMau());
            }

            Button btnSetting = findViewById(R.id.btnSetting);
            if (btnSetting != null)
                btnSetting.setOnClickListener(v -> setting());

            Button btnRefuel = findViewById(R.id.btnRefuel);
            if (btnRefuel != null)
                btnRefuel.setOnClickListener(v -> refuel());

            Button btnExtract = findViewById(R.id.btnExtract);
            if (btnExtract != null)
                btnExtract.setOnClickListener(v -> extract());
            Button btnLogout = findViewById(R.id.btnLogout);
            if (btnLogout != null)
                btnLogout.setOnClickListener(v -> logout());
            Button btnReceipt = findViewById(R.id.btnReceipt);
            if (btnReceipt != null)
                btnReceipt.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ReceiptActivity.class);
                    startActivity(intent);
                });
            setTruckInfo();
        }

    }

    private void sync() {
        setProgressDialog();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.Synchronize();
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                super.onPostExecute(unused);
                closeProgressDialog();
            }
        }.execute();

    }

    private void openBM2505() {
        Intent intent = new Intent(this, B2505Activity.class);
        startActivity(intent);
    }

    private void openBM2508() {
        Intent intent = new Intent(this, B2508Activity.class);
        startActivity(intent);
    }

    private void openA2307() {
        Intent intent = new Intent(this, A2307Activity.class);
        startActivity(intent);
    }

    private void openBieuMau() {
        try {
            Intent intent = new Intent(this, MainBieuMau.class);
            startActivity(intent);
        } catch (Exception ex) {
            Log.e("BIEU_MAU", "Open MainBieuMau error", ex);
        }
    }


    private void refuel() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private void extract() {
        Intent intent = new Intent(this, ExtractActivity.class);
        startActivity(intent);
    }

    private void openBM2502() {
        try {
            Intent intent = new Intent(this, B2502Activity.class);
            startActivity(intent);
        } catch (Exception ex) {
            Log.e("INVENTORY", ex.getMessage());
        }

    }

    private void invoice() {
        Intent intent = new Intent(this, TruckInvoiceActivity.class);
        startActivity(intent);

    }


    private boolean checkLogin() {

        return currentApp.isLoggedin();

    }

    protected int SETTING_CODE = 2;

    public void setting() {
        Intent intent = new Intent(this, SettingActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

    }

    protected Menu optionMenu;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //super.onOptionsItemSelected(item);
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {


            case R.id.action_info:
                showUpdate();
                break;
            case R.id.action_settings:
                setting();
                break;

            case R.id.action_restart:
                showRestart();
                break;
            case R.id.action_send_log:
                if (Logger.sendLog()) {
                    showMessage(R.string.info, R.string.send_log_completed,R.drawable.ic_checked_circle, null);
                }
                break;

            case R.id.action_printer_test:

                if (BuildConfig.THERMAL_PRINTER){
                    getZebraWorker().setStateListener(new ZebraWorker.ZebraStateListener() {
                        @Override
                        public void onConnectionError() {

                            runOnUiThread(() -> {
                                showErrorMessage(R.string.printer_connection_error);
                            });


                        }

                        @Override
                        public void onError() {
                            runOnUiThread(() -> {
                                showErrorMessage(R.string.printer_error);
                            });
                        }

                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                showInfoMessage(R.string.print_test_ok);
                            });
                        }
                    });
                    getZebraWorker().prinTest();
                }
                else {
                    printWorker = new PrintWorker();
                    printWorker.setPrintStateListener(new PrintWorker.PrintStateListener() {
                        @Override
                        public void onConnectionError() {

                            runOnUiThread(() -> {
                                showErrorMessage(R.string.printer_connection_error);
                            });


                        }

                        @Override
                        public void onError() {
                            runOnUiThread(() -> {
                                showErrorMessage(R.string.printer_error);
                            });
                        }

                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                showInfoMessage(R.string.print_test_ok);
                            });
                        }
                    });
                    printWorker.printTest();
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    PrintWorker printWorker = new PrintWorker();

    /**
     * Khởi tạo LƯỜI, không phải ở mức trường.
     *
     * <p>Trước đây là {@code ZebraWorker zebraWorker = new ZebraWorker(this);} — chạy trong
     * constructor của Activity, tức TRƯỚC khi Android gắn Context. Constructor của ZebraWorker
     * gọi ngay getSharedPreferences trên Context null nên nổ mọi lần mở màn hình: 98 dòng lỗi
     * mỗi ngày mỗi xe, và đối tượng tạo ra không dùng được vào việc gì.
     */
    private ZebraWorker zebraWorker;

    protected ZebraWorker getZebraWorker() {
        if (zebraWorker == null)
            zebraWorker = new ZebraWorker(this);
        return zebraWorker;
    }
    private void showUpdate() {
        Intent intent = new Intent(this, VersionUpdateActivity.class);
        startActivity(intent);
    }

    protected void showRestart() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.restart_confirm)
                .setIcon(R.drawable.ic_question)
                .setPositiveButton(R.string.restart_app, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION,"Confirm restart app", this.getClass().getName());
                        dialog.dismiss();

                        restartApp();
                    }
                })
                .setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        Logger.appendLog("Cancel restart app");
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    private final int RESTART_CODE = 3;



    private void restartApp() {
        //Intent intent = new Intent(this, StartupActivity.class);
        Intent intent = getBaseContext().getPackageManager().
                getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
/*            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(UserBaseActivity.this, mPendingIntentId, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager mgr = (AlarmManager) UserBaseActivity.this.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent);*/
            //System.exit(0);
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            startActivity(mainIntent);
            Runtime.getRuntime().exit(0);
        }
/*
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, RESTART_CODE);
        finish();

*/
    }


    private void logout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.app_name);
        builder.setMessage(R.string.logout_message);
        builder.setPositiveButton(R.string.exit,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        doLogout();
                    }
                });
        builder.setNegativeButton(getString(R.string.back),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        // after calling setter methods
        builder.create().show();
    }

    private void doLogout() {
        currentApp.logout();
        finish();
//        Intent intent = new Intent(this, LoginActivity.class);
//        startActivity(intent);
    }


}
