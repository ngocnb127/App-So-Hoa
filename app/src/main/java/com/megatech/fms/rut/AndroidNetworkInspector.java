package com.megatech.fms.rut;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Hỏi hệ điều hành xem điện thoại đang thực sự đi ra Internet bằng đường nào.
 *
 * <p>Gọi được API của router mới chỉ chứng minh máy vào được LAN của router. Điện thoại
 * hoàn toàn có thể vừa nối Wi-Fi router vừa chạy Internet bằng 4G của chính nó — Android
 * làm đúng như vậy khi Wi-Fi bị đánh giá là không có Internet. Đó là ca mà người dùng ngoài
 * hiện trường tưởng đang dùng SIM của xe nhưng thật ra đang tiêu data cá nhân.
 */
public final class AndroidNetworkInspector {

    private final Context context;
    private final ConnectivityManager manager;

    public AndroidNetworkInspector(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (ConnectivityManager)
                this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Tên Wi-Fi đang nối — CHỈ để hiển thị.
     *
     * <p>Tuyệt đối không dùng SSID làm bằng chứng nhận dạng router: đặt trùng tên Wi-Fi là
     * việc bất kỳ ai cũng làm được trong ba mươi giây. Danh tính vẫn chỉ đến từ serial đọc
     * qua API đã xác thực.
     */
    @Nullable
    public String currentWifiSsid(@Nullable Network wifi) {
        String ssid = null;
        try {
            // Android 10+ : đọc từ chính Network đã chọn, nên không nhầm sang Wi-Fi khác.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && manager != null && wifi != null) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(wifi);
                TransportInfo info =
                        capabilities == null ? null : capabilities.getTransportInfo();
                if (info instanceof WifiInfo) ssid = cleanSsid(((WifiInfo) info).getSSID());
            }
            if (ssid == null) {
                WifiManager wifiManager =
                        (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager == null ? null : wifiManager.getConnectionInfo();
                ssid = cleanSsid(info == null ? null : info.getSSID());
            }
        } catch (Throwable ignored) {
            // Thiếu quyền hay nhà sản xuất chặn: tên Wi-Fi là thứ trang trí, không đáng để
            // làm hỏng cả lần đọc trạng thái.
            return null;
        }
        return ssid;
    }

    /**
     * Android trả SSID trong dấu nháy kép, và trả {@code <unknown ssid>} khi không được
     * phép đọc. Hiện nguyên văn mấy thứ đó lên popup là hiện rác cho người dùng.
     */
    @Nullable
    static String cleanSsid(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            value = value.substring(1, value.length() - 1).trim();
        if (value.isEmpty()) return null;

        String lower = value.toLowerCase(Locale.US);
        if (lower.equals("<unknown ssid>") || lower.equals("unknown ssid")
                || lower.equals("<none>") || lower.equals("null") || lower.equals("0x"))
            return null;
        return value;
    }

    /** Mạng Wi-Fi đang có, kể cả khi nó KHÔNG phải mạng mặc định của hệ thống. */
    @Nullable
    public Network findWifiNetwork() {
        if (manager == null) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return network;
        }
        return null;
    }

    /** Mạng mà hệ thống đang dùng làm mặc định cho lưu lượng Internet. */
    @Nullable
    public Network activeNetwork() {
        return manager == null ? null : manager.getActiveNetwork();
    }

    public boolean isActiveNetworkCellular() {
        return hasTransport(activeNetwork(), NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    public boolean isActiveNetworkWifi() {
        return hasTransport(activeNetwork(), NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** Wi-Fi đã được hệ điều hành xác nhận là ra được Internet thật. */
    public boolean isWifiValidated(@Nullable Network wifi) {
        if (manager == null || wifi == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(wifi);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private boolean hasTransport(@Nullable Network network, int transport) {
        if (manager == null || network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(transport);
    }
}
