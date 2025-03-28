package com.megatech.fms;

import static com.megatech.fms.BuildConfig.API_BASE_URL;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.InstallHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionUpdateActivity extends BaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_update);
        TextView txt = findViewById(R.id.info_dialog_version);
        txt.setText(String.format("%s", BuildConfig.VERSION_NAME));

        new CheckVersionAsyncTask().execute(API_BASE_URL + (BuildConfig.THERMAL_PRINTER? "files/thermal.txt": "files/version.txt"));
    }

    String update_url;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btnUpdate:

                findViewById(R.id.btnUpdate).setEnabled(false);
                ((Button) findViewById(R.id.btnUpdate)).setText(R.string.version_updating);
                if (checkStoragePermission())
                    new UpdateAsyncTask().execute(update_url);
                break;
            case R.id.btnBack:
                finish();
                break;
        }
    }

    private final class CheckVersionAsyncTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... strings) {
            String url = strings[0];
            HttpClient client = new HttpClient();
            String data = client.getContent(url);
            if (data != null) {
                String[] info = data.split("\\-");

                return data;
            }
            return null;
        }

        @Override
        protected void onPostExecute(String versionInfo) {
            try {
                String[] info = versionInfo.split("-");
                String version = info[0];
                String build = info[1];
                long newVersion = Long.parseLong(version);
                long currentVersion = BuildConfig.VERSION_CODE;
                String currentVersionName = BuildConfig.VERSION_NAME;

                if (newVersion > currentVersion) {
                    ((TextView) findViewById(R.id.version_check_message)).setText(getString(R.string.new_version_available));
                    ((TextView) findViewById(R.id.version_check_message)).setTextColor(getResources().getColor(R.color.colorPrimary));
                    findViewById(R.id.btnUpdate).setEnabled(true);
                } else {
                    ((TextView) findViewById(R.id.version_check_message)).setText(getString(R.string.newest_version_using));
                }
                update_url = API_BASE_URL + "/files/" + (BuildConfig.THERMAL_PRINTER ? "thermal-" : "fms-release-") + versionInfo + ".apk";

            } catch (Exception ex) {
                showErrorMessage(R.string.file_update_error);
            }
            //super.onPostExecute(aLong);
        }
    }

    private final class UpdateAsyncTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... urls) {
            try {

                URL url = new URL(urls[0]);


                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");

                urlConnection.setRequestProperty("Content-Type", "application/octet-stream");
                urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
                urlConnection.setRequestProperty("Accept", "*/*");

                urlConnection.connect();
                //File sdcard = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "fms");
                File sdcard = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (!sdcard.exists())
                    sdcard.mkdirs();
                File file = new File(sdcard, "fms-release.apk");

                FileOutputStream fileOutput = new FileOutputStream(file);
                InputStream inputStream = urlConnection.getInputStream();

                byte[] buffer = new byte[1024];
                int bufferLength = 0;

                while ((bufferLength = inputStream.read(buffer)) > 0) {
                    fileOutput.write(buffer, 0, bufferLength);
                }
                fileOutput.close();
                return file.toString();


            } catch (IOException e) {
                Log.e("FMS", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String filePath) {
            installApp(filePath);


        }
    }

    private void installApp(String filePath) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {

                install(this, new FileInputStream(filePath));

            } else {

                if (filePath != null) {
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);

                    intent.setData(Uri.fromFile(new File(filePath)));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    getApplicationContext().startActivity(intent);
                }

            }
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            //currentApp.clearSetting();
        }
    }

    private final int REQUEST_WRITE_PERMISSION = 1;

    protected boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.REQUEST_INSTALL_PACKAGES}, REQUEST_WRITE_PERMISSION);
                return false;
            }
            return true;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        new UpdateAsyncTask().execute(update_url);
    }


    public  void install(Context ctx, InputStream in) throws IOException {
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
    private  static final String PACKAGE_INSTALLED_ACTION =
            "com.megatech.fms.SESSION_API_PACKAGE_INSTALLED";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle extras = intent.getExtras();
        if (InstallHelper.PACKAGE_INSTALLED_ACTION.equals(intent.getAction())) {
            int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
            String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    // This test app isn't privileged, so the user has to confirm the install.
                    Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
                    startActivity(confirmIntent);
                    break;

                case PackageInstaller.STATUS_SUCCESS:
                    Toast.makeText(this, "Install succeeded!", Toast.LENGTH_SHORT).show();
                    break;

                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    Toast.makeText(this, "Install failed! " + status + ", " + message,
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(this, "Unrecognized status received from installer: " + status,
                            Toast.LENGTH_SHORT).show();
            }
        }
    }
}
