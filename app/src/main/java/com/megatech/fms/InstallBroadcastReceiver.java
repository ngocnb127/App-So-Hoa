package com.megatech.fms;

import android.content.pm.PackageInstaller;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class InstallBroadcastReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "FMS_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!VersionUpdateActivity.PACKAGE_INSTALLED_ACTION.equals(intent.getAction())) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Log.d(LOG_TAG, "Kết quả cài đặt: status=" + status + ", msg=" + msg);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm); // mở dialog Cài đặt của hệ thống
                } else {
                    Toast.makeText(context, "Cần xác nhận cài đặt", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                Toast.makeText(context, "Install succeeded!", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(context, "Install failed: " + msg, Toast.LENGTH_LONG).show();
                break;
        }
    }
}