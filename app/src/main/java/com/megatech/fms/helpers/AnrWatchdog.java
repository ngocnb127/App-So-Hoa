package com.megatech.fms.helpers;

import android.os.Handler;
import android.os.Looper;

public class AnrWatchdog extends Thread {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean ticked;
    private final long timeoutMs;

    public AnrWatchdog(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        setName("anr-watchdog");
        setDaemon(true);   // không giữ tiến trình sống
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            ticked = false;
            mainHandler.post(() -> ticked = true);   // gửi 1 task xuống luồng main

            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {
                return;
            }

            if (!ticked) {   // main không chạy được task trong timeoutMs -> đang treo
                StackTraceElement[] st = Looper.getMainLooper().getThread().getStackTrace();
                StringBuilder sb = new StringBuilder("Main treo > " + timeoutMs + "ms\n");
                for (StackTraceElement e : st) {
                    sb.append("\tat ").append(e).append("\n");
                }
                Logger.appendLog("ANR", sb.toString());

                // chờ main hồi phục rồi mới theo dõi tiếp (tránh log lặp liên tục)
                while (!ticked && !isInterrupted()) {
                    try {
                        Thread.sleep(timeoutMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }
}