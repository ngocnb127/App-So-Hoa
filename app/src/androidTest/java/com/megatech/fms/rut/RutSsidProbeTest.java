package com.megatech.fms.rut;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Dò riêng đường lấy SSID: cần biết ĐƯỜNG NÀO bị chặn chứ không chỉ biết là null. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RutSsidProbeTest {

    private static final String TAG = "RUT_SSID";

    @Test
    public void showWhichPathCanReadTheSsid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;

            TransportInfo info = capabilities.getTransportInfo();
            Log.i(TAG, "transportInfo lớp = " + (info == null ? "null" : info.getClass().getName()));
            if (info instanceof WifiInfo)
                Log.i(TAG, "transportInfo SSID = [" + ((WifiInfo) info).getSSID() + "]");
        }

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo legacy = wifiManager == null ? null : wifiManager.getConnectionInfo();
        Log.i(TAG, "WifiManager SSID   = [" + (legacy == null ? "null" : legacy.getSSID()) + "]");
        Log.i(TAG, "WifiManager BSSID  = [" + (legacy == null ? "null" : legacy.getBSSID()) + "]");

        Log.i(TAG, "FINE_LOCATION      = " + context.checkSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION));
        Log.i(TAG, "NEARBY_WIFI        = " + context.checkSelfPermission(
                "android.permission.NEARBY_WIFI_DEVICES"));
    }
}
