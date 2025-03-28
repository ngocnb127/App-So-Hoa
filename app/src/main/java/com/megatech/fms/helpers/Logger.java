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
        Context ctx = FMSApplication.getApplication();

        String fileName = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/fms.log" ;
        File logFile = new File(fileName);

        if (tag != null)
            logText = "[" + tag + "] " + logText;
        if (logFile.exists() && logFile.length() > 1024 * 1024 * 8) {
            Date d = new Date();

            logFile.delete();
           // logFile = new File(fileName);
        }
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
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

    public static boolean sendLog() {
        try {

            LogEntryAPI client = new LogEntryAPI();

            List<LogEntryModel> list = DataHelper.getLogList(100);

            boolean postOK = client.postLogs(list);
            if (postOK)
            {
                int[] ids =  list.stream().mapToInt(model->model.getLocalId()).toArray();
                DataHelper.deleteLogs(ids);
            }

            Context ctx = FMSApplication.getApplication();

            String fileName = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/fms.log";

            return client.postLogFile( fileName);


        } catch (Exception ex) {
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
}
