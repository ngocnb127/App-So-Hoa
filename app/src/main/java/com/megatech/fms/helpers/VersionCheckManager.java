package com.megatech.fms.helpers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.helpers.update.UpdateChannel;
import com.megatech.fms.helpers.update.UpdateStatus;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nguồn kiểm tra phiên bản DUY NHẤT của ứng dụng.
 *
 * Trước đây MainActivity, UserBaseActivity, VersionUpdateActivity và lớp này mỗi nơi tự
 * gọi server, tự parse và tự so sánh — với hai bộ endpoint khác nhau, nên badge trên màn
 * hình chính và hộp thoại nhắc có thể đưa ra kết luận trái ngược. Giờ mọi UI đọc trạng
 * thái từ đây và không nơi nào tự ghép URL.
 */
public final class VersionCheckManager {

    private static final String LOG_TAG = "VersionCheck";
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000;

    public interface Listener {
        void onUpdateStatusChanged(UpdateStatus status);
    }

    private static volatile UpdateStatus status = UpdateStatus.idle();
    private static volatile long lastCheckStartedAt = 0;
    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VersionCheckManager() {
    }

    public static UpdateStatus getStatus() {
        return status;
    }

    /** Nhận trạng thái hiện tại ngay khi đăng ký, rồi nhận mọi thay đổi sau đó. */
    public static void addListener(Listener listener) {
        if (listener == null) return;
        listeners.addIfAbsent(listener);
        listener.onUpdateStatusChanged(status);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Gọi mỗi lần activity resume — tự throttle, không spam server. */
    public static void checkIfNeeded() {
        if (!UpdateChannel.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastCheckStartedAt < CHECK_INTERVAL_MS) return;
        startCheck();
    }

    /** Bỏ qua throttle — dùng khi người dùng chủ động mở màn hình cập nhật. */
    public static void forceCheck() {
        if (!UpdateChannel.isEnabled()) return;
        startCheck();
    }

    private static void startCheck() {
        // Chỉ một lần kiểm tra chạy tại một thời điểm: nếu không, vài Activity resume liên
        // tiếp sẽ tạo vài thread cùng ghi vào status và kết quả cuối phụ thuộc vào thứ tự
        // trả lời của server.
        if (!checking.compareAndSet(false, true)) return;
        lastCheckStartedAt = System.currentTimeMillis();
        publish(status.checking());
        new Thread(VersionCheckManager::doCheck, "fms-version-check").start();
    }

    private static void doCheck() {
        try {
            String url = UpdateChannel.metadataUrl();
            String versionInfo = new HttpClient().getContent(url);

            if (versionInfo == null || versionInfo.trim().isEmpty()) {
                // Không phân biệt được "không có bản mới" với "không hỏi được server",
                // nên phải báo thất bại. Báo UP_TO_DATE ở đây sẽ giấu mất bản cập nhật thật.
                publish(status.checkFailed("Không kết nối được máy chủ cập nhật"));
                return;
            }

            AppVersionInfo serverVersion = AppVersionInfo.parse(versionInfo);
            if (serverVersion == null) {
                Log.e(LOG_TAG, "Metadata sai định dạng: [" + versionInfo + "]");
                publish(status.checkFailed("Dữ liệu phiên bản trên máy chủ không hợp lệ"));
                return;
            }

            long now = System.currentTimeMillis();
            boolean hasUpdate = serverVersion.isNewerThanCurrent();
            Log.d(LOG_TAG, "Kênh=" + UpdateChannel.current()
                    + " hiện tại=" + BuildConfig.VERSION_CODE
                    + " máy chủ=" + serverVersion.raw
                    + " cóBảnMới=" + hasUpdate);

            publish(hasUpdate
                    ? status.updateAvailable(serverVersion, now)
                    : status.upToDate(now));

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Lỗi kiểm tra phiên bản", ex);
            publish(status.checkFailed("Lỗi kiểm tra phiên bản"));
        } finally {
            checking.set(false);
        }
    }

    private static void publish(UpdateStatus next) {
        status = next;
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.onUpdateStatusChanged(next);
                } catch (Throwable t) {
                    Log.e(LOG_TAG, "Listener ném lỗi", t);
                }
            }
        });
    }
}
