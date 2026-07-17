package com.megatech.fms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

public class InstallBroadcastReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "FMS_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Log.d(LOG_TAG, "Install callback: status=" + status + " msg=" + message);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // FIX 2: Hệ thống yêu cầu người dùng xác nhận cài đặt.
                // PHẢI startActivity intent này, nếu không cài đặt dừng vĩnh viễn.
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                } else {
                    Log.e(LOG_TAG, "STATUS_PENDING_USER_ACTION nhưng EXTRA_INTENT null");
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                Log.d(LOG_TAG, "Cài đặt THÀNH CÔNG");
                // App sẽ bị kill và khởi động lại bởi hệ thống sau khi update chính nó
                break;

            default:
                // STATUS_FAILURE, _ABORTED, _BLOCKED, _CONFLICT, _INCOMPATIBLE,
                // _INVALID, _STORAGE
                Log.e(LOG_TAG, "Cài đặt THẤT BẠI: status=" + status + " msg=" + message);
                Toast.makeText(context, "Cài đặt thất bại: " + message, Toast.LENGTH_LONG).show();
                break;
        }
    }
}