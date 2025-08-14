package com.megatech.fms.helpers;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

public class AppStateObserver implements LifecycleObserver {

    private static boolean isForeground = false;

    public static void init() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppStateObserver());
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onEnterForeground() {
        isForeground = true;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onEnterBackground() {
        isForeground = false;
    }

    public static boolean isAppInForeground() {
        return isForeground;
    }
}
