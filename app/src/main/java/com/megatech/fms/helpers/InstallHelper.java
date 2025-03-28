package com.megatech.fms.helpers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.MainActivity;
import com.megatech.fms.StartupActivity;
import com.megatech.fms.VersionUpdateActivity;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InstallHelper {

    public static final String PACKAGE_INSTALLED_ACTION =
            "com.megatech.fms.SESSION_API_PACKAGE_INSTALLED";

    public static void install(Context ctx, InputStream in) throws IOException {
        PackageInstaller packageInstaller = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(BuildConfig.APPLICATION_ID);
        // set params
        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        OutputStream out = session.openWrite("COSU", 0, -1);
        byte[] buffer = new byte[65536];
        int c;

        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
        session.fsync(out);
        in.close();
        out.close();

        Intent intent = new Intent(ctx, VersionUpdateActivity.class);
        intent.setAction(PACKAGE_INSTALLED_ACTION);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0);
        IntentSender statusReceiver = pendingIntent.getIntentSender();

        session.commit(statusReceiver);
        session.close();
    }
}
