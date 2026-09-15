package com.megatech.fms.rut;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.megatech.fms.helpers.Logger;

/**
 * Nơi duy nhất giữ cấu hình router RUT.
 *
 * <p>Tài khoản quản trị router là bí mật thật: ai có nó thì đổi được cấu hình mạng của cả
 * xe. Vì vậy lưu bằng {@link EncryptedSharedPreferences} với khoá nằm trong Android
 * Keystore, không phải SharedPreferences thường.
 *
 * <p>Nếu lớp mã hoá không dựng được trên một thiết bị nào đó (Keystore hỏng, ROM lạ), lùi
 * về SharedPreferences thường thay vì làm chết tính năng — và GHI LOG việc lùi đó, để sự
 * suy giảm bảo mật không diễn ra âm thầm.
 */
public final class RutConfigStore {

    private static final String LOG_TAG = "RUT";

    private static final String PREF_FILE = "fms_rut_config";
    private static final String PREF_FILE_FALLBACK = "fms_rut_config_plain";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SERIAL = "expected_serial";
    private static final String KEY_CERT_PIN = "cert_pin_sha256";

    /**
     * Địa chỉ LAN mặc định của router RUT. Cố định theo quy ước triển khai của đội vận
     * hành: mọi xe đều đặt router ở địa chỉ này.
     */
    public static final String DEFAULT_BASE_URL = "https://192.168.1.1";

    /** Tài khoản quản trị mặc định của RutOS. */
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "Abcd1234";

    private final SharedPreferences prefs;

    public RutConfigStore(Context context) {
        this.prefs = openPrefs(context.getApplicationContext());
    }

    private static SharedPreferences openPrefs(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context, PREF_FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Throwable ex) {
            Logger.appendLog(LOG_TAG, "Không dựng được kho mã hoá, dùng kho thường: "
                    + Logger.describe(ex));
            return context.getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE);
        }
    }

    public RutConnectionConfig load() {
        return new RutConnectionConfig(
                prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL),
                prefs.getString(KEY_USERNAME, DEFAULT_USERNAME),
                prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD),
                prefs.getString(KEY_SERIAL, null),
                prefs.getString(KEY_CERT_PIN, null));
    }

    /** Ghép cặp: nhớ serial của router vừa xác thực được. */
    public void rememberSerial(@Nullable String serial) {
        if (serial == null || serial.trim().isEmpty()) return;
        prefs.edit().putString(KEY_SERIAL, serial.trim()).apply();
    }

    /**
     * Nhớ vân tay chứng thư TLS của router ở lần bắt tay đầu tiên.
     *
     * <p>Router xuất xưởng với chứng thư tự ký, không có CA nào chứng thực. Ghim ở lần đầu
     * (trust on first use) rồi bắt buộc khớp ở mọi lần sau là mức bảo vệ thực tế cao nhất
     * còn lại: một thiết bị khác trong cùng LAN giả danh router sẽ bị từ chối ngay.
     */
    public void rememberCertificate(@Nullable String sha256Hex) {
        if (sha256Hex == null || sha256Hex.isEmpty()) return;
        prefs.edit().putString(KEY_CERT_PIN, sha256Hex).apply();
    }

    /** Quên ghép cặp — dùng khi thay router cho xe. */
    public void clearPairing() {
        prefs.edit().remove(KEY_SERIAL).remove(KEY_CERT_PIN).apply();
    }

    public void save(RutConnectionConfig config) {
        prefs.edit()
                .putString(KEY_BASE_URL, config.baseUrl)
                .putString(KEY_USERNAME, config.username)
                .putString(KEY_PASSWORD, config.password)
                .putString(KEY_SERIAL, config.expectedSerial)
                .putString(KEY_CERT_PIN, config.pinnedCertificateSha256)
                .apply();
    }
}
