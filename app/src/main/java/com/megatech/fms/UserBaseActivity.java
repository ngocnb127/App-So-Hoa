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
import android.text.InputFilter;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.megatech.fms.helpers.print.PrinterProvisioner;
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

    /**
     * Mở popup trạng thái router RUT.
     *
     * <p>Chỉ mở một bản: người dùng bấm nhanh hai lần trên thanh công cụ sẽ chồng hai popup
     * cùng gọi router, vừa tốn phiên đăng nhập vừa để lại một tấm nền không đóng được.
     */
    public void showRutStatus() {
        if (getSupportFragmentManager().findFragmentByTag(
                com.megatech.fms.rut.RutStatusBottomSheet.TAG) != null) return;
        com.megatech.fms.rut.RutStatusBottomSheet.newInstance()
                .show(getSupportFragmentManager());
    }

    protected Menu optionMenu;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionMenu = menu;
        // Lệnh factory là ZPL/SGD của Zebra; bản máy in kim không có gì để factory.
        MenuItem factory = menu.findItem(R.id.action_printer_factory);
        if (factory != null) factory.setVisible(BuildConfig.THERMAL_PRINTER);
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


            case R.id.action_rut_status:
                showRutStatus();
                break;
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
                // PHẢI ra luồng nền: sendLog() đọc bảng nhật ký trong Room, mà Room chặn cứng
                // truy vấn trên luồng giao diện (assertNotMainThread) — gọi thẳng ở đây là
                // VĂNG APP. StrictMode.permitAll() ở BaseActivity không che được, đó là chốt
                // riêng của Room. Cùng một lỗi với đường chốt mẻ, sửa 06-09-2026.
                //
                // Nút này còn là thứ người dùng bấm NGAY SAU khi gặp sự cố để gửi nhật ký về,
                // nên nó văng là mất luôn đường chẩn đoán của mọi lỗi khác.
                new Thread(() -> {
                    boolean sent = false;
                    try {
                        sent = Logger.sendLog();
                    } catch (Exception ex) {
                        Logger.appendLog("LOGSEND", "Gửi nhật ký lỗi: " + ex);
                    }
                    final boolean ok = sent;
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        if (ok)
                            showMessage(R.string.info, R.string.send_log_completed,
                                    R.drawable.ic_checked_circle, null);
                        else
                            showMessage(R.string.error, R.string.send_log_failed,
                                    R.drawable.ic_error, null);
                    });
                }, "FMS-Send-Log").start();
                break;

            case R.id.action_printer_factory:
                if (BuildConfig.THERMAL_PRINTER) startPrinterFactory();
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
    /**
     * Factory máy in, bước 1: đọc máy in đang cầm trên tay.
     *
     * <p>Đọc TRƯỚC khi hỏi xác nhận để hộp thoại nói được tên Bluetooth hiện tại và tên sẽ
     * hiện ra sau factory — người dùng tự ghép lại, nên phải biết tìm tên nào.
     */
    private void startPrinterFactory() {
        setProgressDialog();
        getZebraWorker().readPrinterIdentity(identity -> {
            closeProgressDialog();
            if (!isActive || isFinishing() || isDestroyed()) return;

            if (identity.error != null) {
                showErrorMessage(R.string.printer_factory,
                        getString(R.string.printer_factory_read_failed, identity.error),
                        R.drawable.ic_error);
                return;
            }
            if (identity.isCpclMode()) {
                showErrorMessage(R.string.printer_factory,
                        getString(R.string.printer_factory_cpcl, identity.language),
                        R.drawable.ic_warning);
                return;
            }
            showPrinterFactoryConfirm(identity);
        });
    }

    /** Factory máy in, bước 2: xác nhận, kèm ô nhập tên Bluetooth mới (tuỳ chọn). */
    private void showPrinterFactoryConfirm(PrinterProvisioner.Identity identity) {
        String unknown = getString(R.string.printer_factory_unknown);
        String currentName = identity.bluetoothName == null ? unknown : identity.bluetoothName;
        String serial = identity.serial == null ? unknown : identity.serial;

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView message = new TextView(this);
        message.setText(getString(R.string.printer_factory_confirm, currentName, serial));
        message.setTextSize(16);
        layout.addView(message);

        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.printer_factory_name_hint);
        nameInput.setSingleLine(true);
        nameInput.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(PrinterProvisioner.BLUETOOTH_NAME_MAX)});
        layout.addView(nameInput);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.printer_factory)
                .setIcon(R.drawable.ic_warning)
                .setView(scroll)
                .setCancelable(false)
                .setPositiveButton(R.string.printer_factory_button, null)
                .setNegativeButton(R.string.back, (d, which) -> {
                    Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION,
                            "Huỷ factory máy in", getPackageName());
                    d.dismiss();
                })
                .create();
        dialog.show();

        // Gắn sau show() để tên sai thì báo ngay trên ô nhập, KHÔNG đóng hộp thoại — đóng
        // là bắt người dùng đọc lại từ đầu chỉ vì gõ nhầm một dấu.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = nameInput.getText().toString();
            String problem = PrinterProvisioner.bluetoothNameProblem(typed);
            if (problem != null) {
                nameInput.setError(problem);
                return;
            }
            dialog.dismiss();
            String newName = PrinterProvisioner.normalizeBluetoothName(typed);
            Logger.saveLog(LogEntryModel.LOG_TYPE.USER_ACTION, "Xác nhận factory máy in "
                    + currentName + (newName == null ? "" : " → " + newName), getPackageName());
            runPrinterFactory(newName, newName != null ? newName
                    : getString(R.string.printer_factory_done_default_name, serial));
        });
    }

    /** Factory máy in, bước 3: gửi lệnh và báo tên cần ghép lại. */
    private void runPrinterFactory(String newName, String nameToPair) {
        setProgressDialog();
        getZebraWorker().setStateListener(new ZebraWorker.ZebraStateListener() {
            @Override
            public void onConnectionError() {
                closeProgressDialog();
                showErrorMessage(R.string.printer_connection_error);
            }

            @Override
            public void onError() {
                closeProgressDialog();
                showErrorMessage(R.string.printer_factory,
                        getString(R.string.printer_factory_failed), R.drawable.ic_error);
            }

            @Override
            public void onSuccess() {
                closeProgressDialog();
                showErrorMessage(R.string.printer_factory,
                        getString(R.string.printer_factory_done, nameToPair),
                        R.drawable.ic_info);
            }
        });
        getZebraWorker().factoryReset(newName);
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
