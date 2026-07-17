package com.megatech.fms;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
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
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.LogEntryModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.megatech.fms.BuildConfig.API_BASE_URL;
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
        TextView lblInventory = findViewById(R.id.lbltoolbar_Inventory);
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
        Intent intent = new Intent(this, PrintReceiptActivity.class);
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
                    zebraWorker.setStateListener(new ZebraWorker.ZebraStateListener() {
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
                    zebraWorker.prinTest();
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
    ZebraWorker zebraWorker = new ZebraWorker(this);
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

    private void checkVersion() {

    }

    private Dialog dlg;
    private String update_url;

    private void showInfoDialog() {

        dlg = new Dialog(this);

        dlg.setContentView(R.layout.info_dialog);
        dlg.setTitle(R.string.update_version);
        TextView txt = dlg.findViewById(R.id.info_dialog_version);
        txt.setText(String.format("%d", BuildConfig.VERSION_CODE));

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                new CheckVersionAsyncTask().execute(API_BASE_URL + "files/version.txt");
            }
        });

        dlg.findViewById(R.id.btn_version_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });

        dlg.findViewById(R.id.btn_version_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.findViewById(R.id.btn_version_update).setEnabled(false);
                ((Button) dlg.findViewById(R.id.btn_version_update)).setText(R.string.version_updating);
                if (checkStoragePermission())
                    new UpdateAsyncTask().execute(update_url);
            }
        });
        dlg.show();
    }


    private final class CheckVersionAsyncTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... strings) {
            String url = strings[0];
            HttpClient client = new HttpClient();
            String data = client.getContent(url);
            if (data != null) {
                String[] info = data.split("-");
                String version = info[0];
                return data;
            }
            return null;
        }

        @Override
        protected void onPostExecute(String versionInfo) {
            String[] info = versionInfo.split("\\-");
            String version = info[0];
            long newVersion = Long.parseLong(version);
            long currentVersion = BuildConfig.VERSION_CODE;

            if (newVersion > currentVersion) {
                ((TextView) dlg.findViewById(R.id.version_check_message)).setText(getString(R.string.new_version_available));
                dlg.findViewById(R.id.btn_version_update).setEnabled(true);
            } else {
                ((TextView) dlg.findViewById(R.id.version_check_message)).setText(getString(R.string.newest_version_using));
            }
            update_url = API_BASE_URL + "/files/" + "fms-release-" + versionInfo + ".apk";
            if (DEBUG)
                update_url = API_BASE_URL + "/files/" + "fms-debug-" + versionInfo + ".apk";

            //super.onPostExecute(aLong);
        }
    }

    private final class UpdateAsyncTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... urls) {
            try {

                URL url = new URL(urls[0]);


                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");

                urlConnection.setRequestProperty("Content-Type", "application/octet-stream");
                urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
                urlConnection.setRequestProperty("Accept", "*/*");

                urlConnection.connect();
                File sdcard = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "fms");

                if (!sdcard.exists())
                    sdcard.mkdirs();
                File file = new File(sdcard, "fms-release.apk");

                FileOutputStream fileOutput = new FileOutputStream(file);
                InputStream inputStream = urlConnection.getInputStream();

                byte[] buffer = new byte[1024];
                int bufferLength = 0;

                while ((bufferLength = inputStream.read(buffer)) > 0) {
                    fileOutput.write(buffer, 0, bufferLength);
                }
                fileOutput.close();
                return file.toString();


            } catch (IOException e) {
                Log.e("FMS", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String filePath) {

            try {
                if (filePath != null) {
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);

                    //i.setDataAndType(Uri.fromFile(new File(filePath)), "application/vnd.android.package-archive");
                    intent.setData(Uri.fromFile(new File(filePath)));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    getApplicationContext().startActivity(intent);
                }
            } catch (Exception ex) {
                Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private final int REQUEST_WRITE_PERMISSION = 1;

    protected boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.REQUEST_INSTALL_PACKAGES}, REQUEST_WRITE_PERMISSION);
                return false;
            }
            return true;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        new UpdateAsyncTask().execute(update_url);
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
