package com.megatech.fms.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.megatech.fms.R;

public class WifiReceiver extends BroadcastReceiver {
    private final static String TAG = WifiReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
        if ((WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())
                || WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction()))
                && WifiManager.WIFI_STATE_ENABLED == wifiState) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Wifi is now enabled");
            }
           /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(new Intent(context, WifiActiveService.class));
            else*/
            context.startService(new Intent(context, WifiActiveService.class));
        }
    }

    /**
     * Getting the network info and displaying the notification is handled in a service
     * as we need to delay fetching the SSID name. If this is done when the receiver is
     * called, the name isn't yet available and you'll get null.
     *
     * As the broadcast receiver is flagged for termination as soon as onReceive() completes,
     * there's a chance that it will be killed before the handler has had time to finish. Placing
     * it in a service lets us control the lifetime.
     */
    public static class WifiActiveService extends Service {

        private final static String TAG = WifiActiveService.class.getSimpleName();

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            // Need to wait a bit for the SSID to get picked up;
            // if done immediately all we'll get is null
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    String mac = info.getMacAddress();
                    String ssid = info.getSSID();
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "The SSID & MAC are " + ssid + " " + mac);
                    }
                    createNotification(ssid, mac);
                    stopSelf();
                }
            }, 5000);
            return START_NOT_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        /**
         * Creates a notification displaying the SSID & MAC addr
         */
        private void createNotification(String ssid, String mac) {
            Notification n = new NotificationCompat.Builder(this,"Wifi Changed")
                    .setContentTitle("Wifi Connection")
                    .setContentText("Connected to " + ssid)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("You're connected to " + ssid + " at " + mac))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(0, n);
        }
    }
}
