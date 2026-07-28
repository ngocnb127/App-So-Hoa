package com.megatech.fms.helpers;

import android.content.Context;
import android.os.Environment;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.model.LogEntryModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class Logger {

    private static Timer timer;
    private static long lastFileFlush = 0;
    private static long lastRefuelAnomalyFlush = 0;


    public static void saveLog(LogEntryModel.LOG_TYPE logType, String logText, String activitiName)
    {

        new Thread(()-> {
            DataHelper.postLog(logType, logText, activitiName);
        }).start();
    }

    public static void appendLog(String logText) {
        appendLog(null, logText);
    }
    public static void appendLog(String tag, String logText)
    {
        appendLog(tag, logText, null);
    }
    public static void appendLog(String tag, String logText, String activity) {
        android.util.Log.d(tag != null ? tag : "APPLOG", logText);
        Context ctx = FMSApplication.getApplication();
        String fileName = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/fms.log";
        File logFile = new File(fileName);

        if (tag != null)
            logText = "[" + tag + "] " + logText;

        // CŨ: logFile.delete()  -> mất log chưa gửi
        // MỚI: rotate sang .1 để giữ nội dung, chờ sendLog đẩy đi
        if (logFile.exists() && logFile.length() > 1024 * 1024 * 2) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            File rotated = new File(fileName + "." + ts + ".pending");
            logFile.renameTo(rotated);   // tên duy nhất, không cần check trùng
        }

        if (!logFile.exists()) {
            try { logFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            logText = "[" + format.format(new Date()) + "] " + logText;
            buf.append(logText);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Dedicated audit file for attempts to change a refuel after it has been
     * finalized. Keeping this separate makes production incidents searchable
     * without having to inspect the much larger application log.
     */
    public static synchronized void appendRefuelAnomaly(String logText) {
        android.util.Log.w("REFUEL_ANOMALY", logText);
        Context ctx = FMSApplication.getApplication();
        File logFile = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "refuel-anomaly.log");

        if (logFile.exists() && logFile.length() > 1024 * 1024) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            logFile.renameTo(new File(logFile.getAbsolutePath() + "." + ts + ".pending"));
        }

        try (BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true))) {
            SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");
            buf.append("[").append(format.format(new Date())).append("] ")
                    .append(logText == null ? "" : logText);
            buf.newLine();
        } catch (IOException ex) {
            android.util.Log.e("REFUEL_ANOMALY", "Cannot write anomaly log", ex);
        }
    }

    public static boolean sendLog() {
        android.util.Log.d("LOGSEND", "sendLog gọi");
        try {
            LogEntryAPI client = new LogEntryAPI();

            // 1) Kênh DB
            List<LogEntryModel> list = DataHelper.getLogList(100);
            if (list != null && !list.isEmpty()) {
                boolean postOK = client.postLogs(list);
                if (postOK) {
                    int[] ids = list.stream().mapToInt(model -> model.getLocalId()).toArray();
                    DataHelper.deleteLogs(ids);
                }
            }

            // 2) Kênh file
            Context ctx = FMSApplication.getApplication();
            String fileName = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/fms.log";
            File current = new File(fileName);

            long size = current.exists() ? current.length() : -1;
            android.util.Log.d("LOGSEND", "fms.log size=" + size);   // xác nhận file có nội dung không

// rotate phần dở -> .pending khi: >100KB HOẶC đã quá 60s từ lần gửi trước (miễn có nội dung)
            long now = System.currentTimeMillis();
            if (current.exists() && current.length() > 0
                    && (current.length() > 100 * 1024 || now - lastFileFlush > 60 * 1000)) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                current.renameTo(new File(fileName + "." + ts + ".pending"));
                lastFileFlush = now;
            }

            File parent = current.getParentFile();
            File anomaly = parent == null ? null : new File(parent, "refuel-anomaly.log");
            if (anomaly != null && anomaly.exists() && anomaly.length() > 0
                    && (anomaly.length() > 20 * 1024 || now - lastRefuelAnomalyFlush > 60 * 1000)) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                anomaly.renameTo(new File(anomaly.getAbsolutePath() + "." + ts + ".pending"));
                lastRefuelAnomalyFlush = now;
            }

            File[] pendings = (parent == null) ? null : parent.listFiles((d, n) ->
                    (n.startsWith("fms.log.") || n.startsWith("refuel-anomaly.log."))
                            && n.endsWith(".pending"));
            android.util.Log.d("LOGSEND", "pending=" + (pendings == null ? 0 : pendings.length));

            boolean allOk = true;
            if (pendings != null) {
                for (File p : pendings) {
                    if (p.length() == 0) { p.delete(); continue; }
                    boolean ok = client.postLogFile(p.getAbsolutePath());
                    android.util.Log.d("LOGSEND", "post " + p.getName() + " ok=" + ok);
                    if (ok) p.delete();
                    else allOk = false;
                }
            }
            return allOk;
        } catch (Exception ex) {
            android.util.Log.e("LOGSEND", "sendLog error", ex);
            return false;
        }
    }

    public static void writePrintLog(String log)
    {
        String fileName = Environment.getExternalStorageDirectory() + "/data.log";
        File logFile = new File(fileName);
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            //buf.append(log);
            //buf.newLine();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void writeCharArray(String log) {
        String fileName = Environment.getExternalStorageDirectory() + "/data.log";
        File logFile = new File(fileName);
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

            //SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            for (char ch : log.toCharArray()) {

                buf.append(String.format("%d ", (int) ch));



            }
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void flushPrevious() {
        Context ctx = FMSApplication.getApplication();
        String fileName = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/fms.log";
        File logFile = new File(fileName);
        if (logFile.exists() && logFile.length() > 0) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            File rotated = new File(fileName + "." + ts + ".pending");
            logFile.renameTo(rotated);   // biến phần dư phiên trước (kể cả crash) thành chunk chờ gửi
        }
    }
}
