package com.megatech.fms;

import static com.megatech.fms.BuildConfig.API_BASE_URL;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.InstallHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionUpdateActivity extends BaseActivity implements View.OnClickListener {

    private static final String LOG_TAG = "FMS_UPDATE";
    public static final String PACKAGE_INSTALLED_ACTION =
            "com.megatech.fms.SESSION_API_PACKAGE_INSTALLED";

    private static final int REQUEST_WRITE_PERMISSION = 1001;
    private static final int REQ_UNKNOWN_SOURCES = 1002;

    private String update_url;
    private String lastDownloadedPath; // để retry sau khi bật unknown sources





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_update);

        TextView txt = findViewById(R.id.info_dialog_version);
        if (txt != null) txt.setText("Phiên bản: " + BuildConfig.VERSION_CODE);

        String versionFile = BuildConfig.THERMAL_PRINTER ? "files/thermal.txt" : "files/version.txt";
        String versionUrl = API_BASE_URL + "/" + versionFile;
        Log.d(LOG_TAG, "Checking version from: " + versionUrl);

        new CheckVersionAsyncTask().execute(versionUrl);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnUpdate) {
            Log.d(LOG_TAG, "Nút Cập nhật được bấm");
            findViewById(R.id.btnUpdate).setEnabled(false);
            ((Button) findViewById(R.id.btnUpdate)).setText(R.string.version_updating);

            if (!checkStoragePermission()) return;

            if (update_url == null || update_url.trim().isEmpty()) {
                Log.e(LOG_TAG, "Lỗi: update_url chưa được khởi tạo");
                Toast.makeText(this, "URL cập nhật không hợp lệ", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(LOG_TAG, "Bắt đầu tải APK từ: " + update_url);
            new UpdateAsyncTask().execute(update_url);
        } else if (id == R.id.btnBack) {
            finish();
        }
    }

    /*** Async: lấy version.txt / thermal.txt ***/
    private final class CheckVersionAsyncTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... strings) {
            String url = strings[0];
            Log.d(LOG_TAG, "Đang tải nội dung từ: " + url);
            HttpClient client = new HttpClient();
            return client.getContent(url);
        }

        @Override
        protected void onPostExecute(String versionInfo) {
            if (versionInfo == null || versionInfo.trim().isEmpty()) {
                Log.e(LOG_TAG, "Không nhận được dữ liệu version từ server");
                showErrorMessage(R.string.file_update_error);
                return;
            }
            Log.d(LOG_TAG, "Dữ liệu version nhận được: " + versionInfo);

            try {
                String[] info = versionInfo.split("-");
                if (info.length < 2) {
                    Log.e(LOG_TAG, "versionInfo sai định dạng: " + versionInfo);
                    showErrorMessage(R.string.file_update_error);
                    return;
                }

                long newVersion = Long.parseLong(info[0]);
                long currentVersion = BuildConfig.VERSION_CODE;

                update_url = API_BASE_URL + "/files/" +
                        (BuildConfig.THERMAL_PRINTER ? "thermal-" : "fms-release-") +
                        versionInfo + ".apk";

                Log.d(LOG_TAG, "Tạo đường dẫn update_url: " + update_url);

                TextView msg = findViewById(R.id.version_check_message);
                if (newVersion > currentVersion) {
                    msg.setText(getString(R.string.new_version_available) + "\n" + update_url);
                    msg.setTextColor(getResources().getColor(R.color.colorPrimary));
                    findViewById(R.id.btnUpdate).setEnabled(true);
                } else {
                    msg.setText(getString(R.string.newest_version_using));
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Lỗi khi xử lý versionInfo", ex);
                showErrorMessage(R.string.file_update_error);
            }
        }
    }

    /*** Async: tải APK ***/
    private final class UpdateAsyncTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... urls) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urls[0]);
                Log.d(LOG_TAG, "Kết nối đến: " + url);

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "*/*");
                conn.connect();

                int code = conn.getResponseCode();
                Log.d(LOG_TAG, "Response code: " + code);
                if (code != 200) {
                    Log.e(LOG_TAG, "Tải file thất bại. Mã lỗi HTTP: " + code);
                    return null;
                }

                File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (dir != null && !dir.exists()) dir.mkdirs();

                File file = new File(dir, "fms-release.apk");
                Log.d(LOG_TAG, "Lưu file về: " + file.getAbsolutePath());

                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len, total = 0;
                    while ((len = input.read(buffer)) > 0) {
                        output.write(buffer, 0, len);
                        total += len;
                    }
                    Log.d(LOG_TAG, "Tải file thành công. Tổng bytes: " + total);
                }

                return file.getAbsolutePath();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Lỗi khi tải APK", e);
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String filePath) {
            if (filePath == null) {
                Log.e(LOG_TAG, "Tải file không thành công, filePath null");
                Toast.makeText(VersionUpdateActivity.this, "Không thể tải tệp cập nhật", Toast.LENGTH_LONG).show();
                findViewById(R.id.btnUpdate).setEnabled(true);
                ((Button) findViewById(R.id.btnUpdate)).setText(R.string.version_updating);
                return;
            }
            lastDownloadedPath = filePath;

            // Pre-check: Unknown sources
            if (!ensureCanInstallUnknownSources()) {
                Log.w(LOG_TAG, "Chưa bật Install unknown apps -> mở Settings, chờ quay lại rồi sẽ retry cài.");
                return;
            }
            proceedInstall(filePath);

        }
    }

    /*** Cài APK ***/
    private void installApp(String filePath) {
        try {
            Log.d(LOG_TAG, "installApp() filePath = " + filePath);
            if (filePath == null || filePath.trim().isEmpty()) {
                Toast.makeText(this, "Đường dẫn file không hợp lệ!", Toast.LENGTH_LONG).show();
                return;
            }
            File apk = new File(filePath);
            if (!apk.exists()) {
                Toast.makeText(this, "Không tìm thấy file cài đặt!", Toast.LENGTH_LONG).show();
                return;
            }

            // Log thông tin APK để chẩn đoán các lỗi chữ ký / package khác
            logApkInfo(filePath);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try (InputStream in = new FileInputStream(apk)) {
                    installWithPackageInstaller(this, in);
                }
            } else {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setData(Uri.fromFile(apk));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Lỗi khi cài đặt APK", ex);
            Toast.makeText(this, ex.getMessage() != null ? ex.getMessage() : "Lỗi không xác định", Toast.LENGTH_LONG).show();
        }
    }

    /** dùng PackageInstaller + BroadcastReceiver để nhận kết quả **/
    public void install(Context ctx, InputStream in) throws Exception {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(BuildConfig.APPLICATION_ID);

        // Android 12+ nếu là Device Owner có thể bỏ user action:
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //     params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        // }

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        try (OutputStream out = session.openWrite("FMS-SESSION", 0, -1)) {
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            session.fsync(out);
        } finally {
            in.close();
        }

        // VersionUpdateActivity.install(...)
        Intent broadcastIntent = new Intent(getApplicationContext(), InstallBroadcastReceiver.class);
        broadcastIntent.setAction(PACKAGE_INSTALLED_ACTION);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(),
                0,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        session.commit(pendingIntent.getIntentSender());
        session.close();
    }

    /** Chỉ xin WRITE_EXTERNAL_STORAGE cho Android 9-; Android 10+ không cần */
    protected boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasWrite = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasWrite) {
                requestPermissions(new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_WRITE_PERMISSION);
                return false;
            }
        }
        return true;
    }

    /** Gọi khi tải xong để tiến hành cài */
    private void proceedInstall(String filePath) {
        lastDownloadedPath = filePath;
        if (!ensureCanInstallUnknownSources()) {
            Log.w(LOG_TAG, "Chưa bật Install unknown apps → mở Settings, chờ quay lại rồi retry.");
            return;
        }
        installApp(filePath);
    }


    /** Check & mở Settings nếu chưa bật “Install unknown apps” cho app */
    private boolean ensureCanInstallUnknownSources() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean allowed = getPackageManager().canRequestPackageInstalls();
            if (!allowed) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_UNKNOWN_SOURCES);
                return false;
            }
        }
        return true;
    }

    /** Retry sau khi bật unknown sources **/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UNKNOWN_SOURCES) {
            if (ensureCanInstallUnknownSources()) {
                if (lastDownloadedPath != null) {
                    Log.d(LOG_TAG, "Đã bật Unknown sources, retry install: " + lastDownloadedPath);
                    installApp(lastDownloadedPath);
                }
            } else {
                Toast.makeText(this, "Chưa bật quyền cài từ nguồn không xác định", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Dùng PackageInstaller + BroadcastReceiver nhận kết quả */
    private void installWithPackageInstaller(Context ctx, InputStream in) throws Exception {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(BuildConfig.APPLICATION_ID);

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        try (OutputStream out = session.openWrite("FMS-SESSION", 0, -1)) {
            byte[] buf = new byte[65536];
            int c;
            while ((c = in.read(buf)) != -1) out.write(buf, 0, c);
            session.fsync(out);
        } finally {
            in.close();
        }

        Intent broadcast = new Intent(ctx, InstallBroadcastReceiver.class);
        broadcast.setAction(PACKAGE_INSTALLED_ACTION);

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, 0, broadcast,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        session.commit(pi.getIntentSender());
        session.close();
    }


    /** Logging thông tin gói APK tải về (packageName/version) **/
    private void logApkInfo(String apkPath) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pi = pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0));
            } else {
                //noinspection deprecation
                pi = pm.getPackageArchiveInfo(apkPath, 0);
            }
            if (pi != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
                    Log.d(LOG_TAG, "APK info → pkg=" + pi.packageName
                            + ", verName=" + pi.versionName
                            + ", verCode=" + pi.getLongVersionCode());
                } else {
                    //noinspection deprecation
                    Log.d(LOG_TAG, "APK info → pkg=" + pi.packageName
                            + ", verName=" + pi.versionName
                            + ", verCode=" + pi.versionCode);
                }
            } else {
                Log.w(LOG_TAG, "Không đọc được PackageInfo của APK");
            }

        } catch (Throwable t) {
            Log.e(LOG_TAG, "Lỗi đọc APK info", t);
        }
    }

    /** runtime permission callback **/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this, "Thiếu quyền ghi bộ nhớ (Android 9-)", Toast.LENGTH_LONG).show();
                findViewById(R.id.btnUpdate).setEnabled(true);
                ((Button) findViewById(R.id.btnUpdate)).setText(R.string.version_updating);
            } else if (update_url != null) {
                new UpdateAsyncTask().execute(update_url);
            }
        }
    }

}