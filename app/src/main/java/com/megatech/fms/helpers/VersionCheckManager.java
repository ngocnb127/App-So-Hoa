package com.megatech.fms.helpers;

import android.content.Context;
import android.util.Log;

import com.megatech.fms.BuildConfig;

public class VersionCheckManager {
    private static final String LOG_TAG = "VersionCheck";

    private static volatile boolean hasUpdate = false;
    private static volatile String updateUrl;
    private static volatile long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000; // 5 phút — tránh gọi server mỗi lần mở activity

    public static boolean isHasUpdate() {
        return hasUpdate;
    }

    public static String getUpdateUrl() {
        return updateUrl;
    }

    /** Gọi mỗi lần activity resume — tự throttle, không spam server */
    public static void checkIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return;
        lastCheckTime = now;
        new Thread(VersionCheckManager::doCheck).start();
    }

    /** Gọi ngay lập tức, bỏ qua throttle — dùng khi cần check tức thời (ví dụ mở VersionUpdateActivity) */
    public static void forceCheck() {
        lastCheckTime = System.currentTimeMillis();
        new Thread(VersionCheckManager::doCheck).start();
    }

    private static void doCheck() {
        try {
            String versionFile = BuildConfig.THERMAL_PRINTER ? "files/thermalUpdate.txt" : "files/versionUpdate.txt";
            String versionUrl = joinUrl(BuildConfig.API_BASE_URL, versionFile);

            HttpClient client = new HttpClient();
            String versionInfo = client.getContent(versionUrl);

            if (versionInfo == null || versionInfo.trim().isEmpty()) {
                Log.w(LOG_TAG, "Không nhận được version info — giữ nguyên trạng thái cũ");
                return;
            }

            versionInfo = versionInfo.trim().replace("\uFEFF", "");
            String[] info = versionInfo.split("-");
            if (info.length < 3) {
                Log.e(LOG_TAG, "versionInfo sai định dạng: " + versionInfo);
                return;
            }

            long newVersionCode = Long.parseLong(info[0].trim());
            long newPatchNumber = Long.parseLong(info[1].trim());
            long currentVersionCode = BuildConfig.VERSION_CODE;
            long currentPatchNumber = BuildConfig.PATCH_NUMBER;

            boolean update = (newVersionCode > currentVersionCode)
                    || (newVersionCode == currentVersionCode && newPatchNumber > currentPatchNumber);

            String url = joinUrl(BuildConfig.API_BASE_URL, "files/" +
                    (BuildConfig.THERMAL_PRINTER ? "thermal-" : "fms-release-") +
                    versionInfo + ".apk");

            hasUpdate = update;
            updateUrl = url;

            Log.d(LOG_TAG, "Check xong: current=" + currentVersionCode + "." + currentPatchNumber
                    + " server=" + newVersionCode + "." + newPatchNumber + " hasUpdate=" + update);

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Lỗi check version nền", ex);
        }
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (path.startsWith("/")) path = path.substring(1);
        return base + "/" + path;
    }
}