package com.megatech.fms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.megatech.fms.helpers.AppVersionInfo;
import com.megatech.fms.helpers.VersionCheckManager;
import com.megatech.fms.helpers.update.ApkValidator;
import com.megatech.fms.helpers.update.UpdateChannel;
import com.megatech.fms.helpers.update.UpdateSafetyGuard;
import com.megatech.fms.helpers.update.UpdateStatus;
import com.megatech.fms.helpers.update.ValidatedApk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionUpdateActivity extends BaseActivity implements View.OnClickListener {

    private static final String LOG_TAG = "FMS_UPDATE";
    public static final String PACKAGE_INSTALLED_ACTION =
            "com.megatech.fms.SESSION_API_PACKAGE_INSTALLED";

    private static final int REQ_UNKNOWN_SOURCES = 1002;

    /** Phiên bản máy chủ công bố ở lần kiểm tra thành công gần nhất. */
    private AppVersionInfo serverVersion;
    private ValidatedApk pendingInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_update);

        TextView txt = findViewById(R.id.info_dialog_version);
        if (txt != null) txt.setText("Phiên bản: " + BuildConfig.VERSION_CODE + "-"
                + BuildConfig.BUILD_DATE + "." + BuildConfig.PATCH_NUMBER);

        findViewById(R.id.btnUpdate).setEnabled(false);

        VersionCheckManager.addListener(statusListener);
        VersionCheckManager.forceCheck();
    }

    @Override
    protected void onDestroy() {
        VersionCheckManager.removeListener(statusListener);
        super.onDestroy();
    }

    private final VersionCheckManager.Listener statusListener = this::renderStatus;

    private void renderStatus(UpdateStatus status) {
        if (isFinishing() || isDestroyed()) return;

        TextView msg = findViewById(R.id.version_check_message);
        Button btnUpdate = findViewById(R.id.btnUpdate);
        if (msg == null || btnUpdate == null) return;

        switch (status.phase) {
            case CHECKING:
                msg.setText(R.string.version_updating);
                btnUpdate.setEnabled(false);
                break;

            case UPDATE_AVAILABLE:
                serverVersion = status.metadata;
                msg.setText(getString(R.string.new_version_available) + "\n" + status.metadata.raw);
                btnUpdate.setEnabled(true);
                break;

            case UP_TO_DATE:
                serverVersion = null;
                msg.setText(R.string.newest_version_using);
                btnUpdate.setEnabled(false);
                break;

            case CHECK_FAILED:
                // Không hiển thị thông tin phiên bản của lần kiểm tra trước như thể nó
                // đang sẵn sàng cài: chưa xác nhận được với máy chủ thì không cho tải.
                serverVersion = null;
                msg.setText(status.error != null ? status.error : getString(R.string.file_update_error));
                btnUpdate.setEnabled(false);
                break;

            default:
                btnUpdate.setEnabled(false);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnUpdate) {
            if (serverVersion == null) {
                Toast.makeText(this, R.string.file_update_error, Toast.LENGTH_LONG).show();
                return;
            }
            setUpdateButtonBusy(true);
            new SafetyCheckTask().execute();
        } else if (id == R.id.btnBack) {
            finish();
        }
    }

    private void setUpdateButtonBusy(boolean busy) {
        Button btn = findViewById(R.id.btnUpdate);
        if (btn == null) return;
        btn.setEnabled(!busy);
        btn.setText(busy ? R.string.version_updating : R.string.update_version);
    }

    /**
     * Kiểm tra dữ liệu nghiệp vụ trước khi tải. Truy vấn Room nên phải chạy nền.
     */
    private final class SafetyCheckTask extends AsyncTask<Void, Void, UpdateSafetyGuard.Decision> {
        @Override
        protected UpdateSafetyGuard.Decision doInBackground(Void... voids) {
            return UpdateSafetyGuard.canInstallNow();
        }

        @Override
        protected void onPostExecute(UpdateSafetyGuard.Decision decision) {
            if (isFinishing() || isDestroyed()) return;
            if (!decision.safe) {
                Toast.makeText(VersionUpdateActivity.this, decision.reason, Toast.LENGTH_LONG).show();
                setUpdateButtonBusy(false);
                return;
            }
            new DownloadTask().execute(UpdateChannel.apkUrl(serverVersion.raw));
        }
    }

    /**
     * Tải APK vào file .part rồi mới đổi tên. Nếu ghi thẳng vào file đích, một lần mất mạng
     * giữa chừng sẽ để lại APK cụt và lần cài sau đọc đúng file hỏng đó.
     */
    private final class DownloadTask extends AsyncTask<String, Void, File> {
        @Override
        protected File doInBackground(String... urls) {
            HttpURLConnection conn = null;
            File part = null;
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && !dir.exists() && !dir.mkdirs()) {
                    Log.e(LOG_TAG, "Không tạo được thư mục tải về");
                    return null;
                }

                File dest = new File(dir, "fms-update.apk");
                part = new File(dir, "fms-update.apk.part"); // cùng thư mục -> rename được

                URL url = new URL(urls[0]);
                Log.d(LOG_TAG, "Tải APK từ: " + url);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "*/*");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e(LOG_TAG, "Tải thất bại, HTTP " + code);
                    return null;
                }

                long total = 0;
                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(part)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = input.read(buffer)) > 0) {
                        output.write(buffer, 0, len);
                        total += len;
                    }
                    output.flush();
                    output.getFD().sync();
                } // stream đóng hẳn trước khi đổi tên

                if (total <= 0) {
                    Log.e(LOG_TAG, "Tải về 0 byte");
                    return null;
                }

                // Xóa bản cũ có kiểm soát; rename không ghi đè được trên mọi hệ tệp.
                if (dest.exists() && !dest.delete()) {
                    Log.e(LOG_TAG, "Không xóa được APK cũ: " + dest);
                    return null;
                }
                if (!part.renameTo(dest)) {
                    Log.e(LOG_TAG, "Không đổi tên được " + part + " -> " + dest);
                    return null;
                }

                Log.d(LOG_TAG, "Tải xong " + total + " byte -> " + dest);
                return dest;

            } catch (Exception e) {
                Log.e(LOG_TAG, "Lỗi khi tải APK", e);
                return null;
            } finally {
                if (conn != null) conn.disconnect();
                // File dở dang không bao giờ được để lại.
                if (part != null && part.exists() && !part.delete()) {
                    Log.w(LOG_TAG, "Không xóa được file tạm " + part);
                }
            }
        }

        @Override
        protected void onPostExecute(File apk) {
            if (isFinishing() || isDestroyed()) return;
            if (apk == null) {
                Toast.makeText(VersionUpdateActivity.this,
                        "Không thể tải tệp cập nhật", Toast.LENGTH_LONG).show();
                setUpdateButtonBusy(false);
                return;
            }
            validateThenInstall(apk);
        }
    }

    /**
     * Không có đường nào đi thẳng từ file tải về tới trình cài đặt: installApp() chỉ nhận
     * ValidatedApk, và chỉ ApkValidator tạo được kiểu đó.
     */
    private void validateThenInstall(File apk) {
        try {
            long expected = serverVersion != null ? serverVersion.versionCode : 0;
            pendingInstall = ApkValidator.validate(this, apk, expected);
        } catch (ApkValidator.ValidationException ex) {
            Log.e(LOG_TAG, "APK không hợp lệ: " + ex.getMessage());
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            if (apk.exists() && !apk.delete()) {
                Log.w(LOG_TAG, "Không xóa được APK không hợp lệ " + apk);
            }
            setUpdateButtonBusy(false);
            return;
        }

        if (!ensureCanInstallUnknownSources()) {
            Log.w(LOG_TAG, "Chưa bật cài từ nguồn không xác định — mở Settings, chờ quay lại.");
            return;
        }
        installApp(pendingInstall);
    }

    /**
     * Cài bằng PackageInstaller trên mọi API level (API 21+ đã có). Bản trước chỉ dùng nó
     * từ API 29 và rơi về ACTION_INSTALL_PACKAGE + Uri.fromFile kèm một thủ thuật
     * StrictMode để né FileUriExposedException; đường đó đã bỏ hẳn.
     */
    private void installApp(ValidatedApk apk) {
        PackageInstaller.Session session = null;
        int sessionId = -1;
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(BuildConfig.APPLICATION_ID);

            sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            try (InputStream in = new FileInputStream(apk.getFile());
                 OutputStream out = session.openWrite("FMS-SESSION", 0, apk.getFile().length())) {
                byte[] buf = new byte[65536];
                int c;
                while ((c = in.read(buf)) != -1) out.write(buf, 0, c);
                session.fsync(out);
            }

            Intent broadcast = new Intent(this, InstallBroadcastReceiver.class)
                    .setAction(PACKAGE_INSTALLED_ACTION)
                    .setPackage(getPackageName());

            // FLAG_MUTABLE là bắt buộc: PackageInstaller phải gắn thêm EXTRA_STATUS và
            // EXTRA_INTENT vào PendingIntent khi trả kết quả. Với FLAG_IMMUTABLE, receiver
            // nhận intent rỗng, không bao giờ thấy STATUS_PENDING_USER_ACTION, và người dùng
            // bấm Cập nhật xong thì "không thấy gì xảy ra".
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                piFlags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(this, sessionId, broadcast, piFlags);

            session.commit(pi.getIntentSender());
            Log.d(LOG_TAG, "Đã commit session cài đặt id=" + sessionId);
            session.close();
            session = null;

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Lỗi khi cài đặt APK", ex);
            Toast.makeText(this, ex.getMessage() != null ? ex.getMessage() : "Lỗi không xác định",
                    Toast.LENGTH_LONG).show();
            setUpdateButtonBusy(false);
            // Session không bỏ dở sẽ chiếm chỗ và làm lần cài sau khó chẩn đoán.
            if (session != null) {
                session.close();
            }
            if (sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Exception abandonEx) {
                    Log.w(LOG_TAG, "Không hủy được session " + sessionId, abandonEx);
                }
            }
        }
    }

    private boolean ensureCanInstallUnknownSources() {
        if (getPackageManager().canRequestPackageInstalls()) return true;
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(i, REQ_UNKNOWN_SOURCES);
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_UNKNOWN_SOURCES) return;

        if (getPackageManager().canRequestPackageInstalls()) {
            if (pendingInstall != null) {
                Log.d(LOG_TAG, "Đã bật cài từ nguồn không xác định, cài tiếp");
                installApp(pendingInstall);
            }
        } else {
            Toast.makeText(this, "Chưa bật quyền cài từ nguồn không xác định",
                    Toast.LENGTH_LONG).show();
            setUpdateButtonBusy(false);
        }
    }
}
