package com.megatech.fms;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.enums.INVOICE_TYPE;
import com.megatech.fms.helpers.AppStateObserver;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.HttpClient;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserInfo;
import com.megatech.fms.receivers.WifiReceiver;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.megatech.fms.helpers.VersionCheckManager;
import com.megatech.fms.helpers.update.UpdateStatus;
import com.megatech.fms.helpers.TruckInvoiceSync;

public class FMSApplication extends Application implements LifecycleObserver {

    private static FMSApplication cApp;
    public static boolean isFHS = BuildConfig.FHS;
    private ScheduledExecutorService scheduler;


    @Override
    public void onCreate() {
        super.onCreate();

        // Gán trước mọi bước khởi tạo khác: nếu một bước bên dưới ném lỗi thì
        // getApplication() vẫn trả về instance thay vì null, tránh NPE dây chuyền
        // ở mọi nơi đang gọi FMSApplication.getApplication().
        cApp = this;

        AppStateObserver.init();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        checkDatabase();
        //registerDBService();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                VersionCheckManager.checkIfNeeded();
                maybeShowUpdateReminder(activity, VersionCheckManager.getStatus());
            }

            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {
                if (currentActivity == a) currentActivity = null;
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
        });

        // Kết quả kiểm tra về sau khi Activity đã resume xong: trước đây trạng thái chỉ
        // được đọc ngay tại onActivityResumed nên bản cập nhật vừa phát hiện phải đợi tới
        // lần resume kế tiếp mới hiện ra.
        VersionCheckManager.addListener(status -> maybeShowUpdateReminder(currentActivity, status));
    }

    /** Activity đang ở trên cùng, để hiển thị nhắc nhở khi kết quả kiểm tra về muộn. */
    private Activity currentActivity;
    /** Hộp thoại nhắc đang mở — bảo đảm mỗi lúc chỉ có một. */
    private android.app.AlertDialog updateDialog;

    private static final String PREF_UPDATE = "fms_update";
    private static final String PREF_POSTPONED_VERSION = "postponed_version_code";
    private static final String PREF_POSTPONED_AT = "postponed_at";
    private static final long POSTPONE_DURATION_MS = 8 * 60 * 60 * 1000L; // 8 giờ

    private void maybeShowUpdateReminder(Activity activity, UpdateStatus status) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (status == null || !status.hasUpdate()) return;

        // Người dùng đang ở màn hình cập nhật rồi thì không cần nhắc.
        if (activity instanceof VersionUpdateActivity) return;

        // Không cắt ngang nghiệp vụ đang dở: lập chứng từ, tra nạp, ký nhận, in hóa đơn.
        if (activity instanceof UpdateSensitiveScreen) return;

        // Một hộp thoại tại một thời điểm. Nếu không chặn, mỗi lần chuyển Activity hoặc
        // quay lại từ Settings/installer sẽ chồng thêm một hộp thoại nữa.
        if (updateDialog != null && updateDialog.isShowing()) return;

        long serverVersion = status.metadata.versionCode;
        if (isPostponed(serverVersion)) return;

        updateDialog = new android.app.AlertDialog.Builder(activity)
                .setTitle("Có phiên bản mới")
                .setMessage("Đã có phiên bản " + status.metadata.raw
                        + ". Bạn có muốn cập nhật ngay không?")
                .setCancelable(true)
                .setPositiveButton("Cập nhật", (dialog, which) ->
                        activity.startActivity(new Intent(activity, VersionUpdateActivity.class)))
                .setNegativeButton("Để sau", (dialog, which) -> {
                    postpone(serverVersion);
                    dialog.dismiss();
                })
                .setOnDismissListener(d -> updateDialog = null)
                .show();
    }

    /**
     * "Để sau" chỉ im lặng 8 giờ và chỉ với đúng phiên bản đã hoãn — một bản phát hành mới
     * hơn sẽ nhắc lại ngay. Cố ý không giới hạn số lần nhắc: giới hạn như vậy cho phép
     * thiết bị trốn cập nhật vĩnh viễn.
     */
    private boolean isPostponed(long versionCode) {
        SharedPreferences prefs = getSharedPreferences(PREF_UPDATE, Context.MODE_PRIVATE);
        if (prefs.getLong(PREF_POSTPONED_VERSION, -1) != versionCode) return false;
        long postponedAt = prefs.getLong(PREF_POSTPONED_AT, 0);
        return System.currentTimeMillis() - postponedAt < POSTPONE_DURATION_MS;
    }

    private void postpone(long versionCode) {
        getSharedPreferences(PREF_UPDATE, Context.MODE_PRIVATE).edit()
                .putLong(PREF_POSTPONED_VERSION, versionCode)
                .putLong(PREF_POSTPONED_AT, System.currentTimeMillis())
                .apply();
    }
    @Override
    public void onLowMemory() {
        trimCache(this);
        super.onLowMemory();
    }


    public static void trimCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }
    private void registerDBService() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate
                (() -> {
                    try {
                        DataHelper.Synchronize();
                    }
                    catch (Exception ex)
                    {}
                }, 0, 60, TimeUnit.SECONDS);
    }

    private void checkDatabase() {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        int dbVersion = preferences.getInt("DB_VERSION",0);

        if (dbVersion < BuildConfig.DB_VERSION)
        {
            deleteDatabase(BuildConfig.DB_FILE);
            clearSetting();
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("DB_VERSION",BuildConfig.DB_VERSION);
        editor.apply();
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase());
    }

    public static FMSApplication getApplication()
    {
        return cApp;

    }
    ///////////////////////////////////////////////
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onEnterForeground() {
        //Logger.appendLog("FMS", "Foreground");
        checkDatabase();
        startSyncScheduler(); // chỉ khởi chạy khi app lên foreground
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onEnterBackground() {
        //Logger.appendLog("FMS", "Background");
        stopSyncScheduler();
    }


    private void startSyncScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    DataHelper.Synchronize();
                    TruckInvoiceSync.synchronize();
                } catch (Exception ex) {}
            }, 0, 30, TimeUnit.SECONDS);
        }
    }

    private void stopSyncScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
///////////////////////////////////////////////


    private static UserInfo _user;
    public UserInfo getUser()
    {
        if (_user == null || _user.getUserId() <=0)
            _user =  UserInfo.fromSharedPreferences(this);
        return _user;
    }

    public String getDeviceIP() {
        //if (BuildConfig.DEBUG)
        //    return "viennam.ddns.net";
        return getSetting().getDeviceIP();
    }

    public String getTabletId() {
        //if (BuildConfig.DEBUG)
        //    return "viennam.ddns.net";
        return getSetting().getTabletSerial();
    }

    public void setDeviceIP(String deviceIP) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("IP",deviceIP);
        editor.apply();

    }

    public void logout() {
        UserInfo.logout(this);
    }

    public boolean isLoggedin()
    {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        String token = preferences.getString("TOKEN",null);
        long loginTime = preferences.getLong("LOGIN_TIME", 0);
        int build12 = preferences.getInt("BUILD", 0);
        long currentTime = new Date().getTime();

        boolean isLogged = token != null && build12>=12  && (currentTime - loginTime) / 1000 / 60 < 60*12;
        if (isLogged)
        {
            FirebaseCrashlytics.getInstance().setUserId( preferences.getString("USER_NAME",null));
        }
        return  isLogged;

    }
    private String printerAddress;
    public String getPrinterAddress() {
        //final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        //printerAddress = preferences.getString("PRINTER_ADDRESS","192.168.1.25");
        //return printerAddress;
        return getSetting().getPrinterIP();
    }


    public boolean isFirstUse() {

        return getTruckNo() == null || getTruckNo().equals("");
    }

    public void clearSetting()
    {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("FIRST_USER");
        editor.remove("IP");
        editor.remove("TRUCK_NO");
        editor.remove("PRINTER_ADDRESS");
        editor.remove("SETTING");
        editor.commit();
        setting = null;
    }
    public void clearTruckInfo()
    {

        TruckModel  settingModel = getSetting();
        settingModel.setTruckId(0);
        settingModel.setTruckNo("");
        saveSetting(settingModel);
    }
    public void saveSetting(TruckModel settingModel)
    {
        saveSetting(settingModel, true);
    }
    public void saveSetting(TruckModel settingModel, boolean post)
    {
        setting = settingModel;
        settingModel.setAppVersion(getAppVer());
        if (post) {
            HttpClient client = new HttpClient();
            TruckModel newModel = client.postTruck(settingModel);
            if (newModel != null)
                settingModel.setId(newModel.getId());
        }
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("FIRST_USER",false);
        editor.putString("IP",settingModel.getDeviceIP());
        editor.putString("TRUCK_NO",settingModel.getTruckNo());
        editor.putString("PRINTER_ADDRESS",printerAddress);
        editor.putString("SETTING",settingModel.toJson());
        editor.remove("ZEBRA_MAC_ADDRESS");

        editor.commit();


    }
    public void saveCurrentAmountTruck(TruckModel settingModel, boolean post)
    {
        setting = settingModel;
        settingModel.setAppVersion(getAppVer());
        if (post) {
            HttpClient client = new HttpClient();
            TruckModel newModel = client.postTruck(settingModel);
            if (newModel != null)
                settingModel.setId(newModel.getId());
        }
    }
    private TruckModel setting;
    public TruckModel getSetting()
    {
        if (setting == null) {
            final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
            String json = preferences.getString("SETTING", "");
            setting = TruckModel.fromJson(json);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("Tablet-Id",setting.getTabletSerial());
            crashlytics.setCustomKey("Truck-No",setting.getTruckNo());
        }
        return  setting;
    }
    public String getTruckNo() {
        return getSetting().getTruckNo();
    }
    public int getTruckId() {
        return getSetting().getTruckId();
    }

    public String getAppVer()
    {
        return BuildConfig.VERSION_NAME;
    }
    public double getCurrentAmount() {
//        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
//        return preferences.getdouble("CURRENT_AMOUNT",0);
        return getSetting().getCurrentAmount();
    }

    //Set Current Amount and post to database
    public void setCurrentAmount(double currentAmount) {

        TruckModel setting = getSetting();
        setting.setCurrentAmount(currentAmount);
        saveSetting(setting, false);
        saveCurrentAmountTruck(setting, true);

    }
    public void addCurrentAmount(double newAmount) {

        TruckModel setting = getSetting();
        setting.setCurrentAmount(setting.getCurrentAmount() + newAmount);
        saveSetting(setting, false);

    }
    public void initCurrentAmount(double currentAmount) {
//        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.putdouble("CURRENT_AMOUNT", currentAmount);
//        editor.commit();


    }
    public String getQCNo() {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        return preferences.getString("QC_NO","");
    }

    public void setQCNo(String qcNo) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        preferences.edit().putString("QC_NO", qcNo).apply();
    }

    public void setInventory(final double addedAmount, String qcNo) {
        double   currentAmount = getSetting().getCurrentAmount();
        //editor.putdouble("CURRENT_AMOUNT", currentAmount + addedAmount);
        setQCNo(qcNo);
        setCurrentAmount( currentAmount + addedAmount);


    }
    public void setInventory(final double addedAmount, String qcNo, boolean isFullFuel) {
        double currentAmount = getSetting().getCurrentAmount();
        double maxAmount = getSetting().getCapacity();

        setQCNo(qcNo);

        if (isFullFuel) {
            setCurrentAmount(maxAmount);
        } else {
            setCurrentAmount(currentAmount + addedAmount);
        }
    }

    public void saveShift(ShiftModel model) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        double currentAmount = getSetting().getCurrentAmount();
        SharedPreferences.Editor editor = preferences.edit();
        //editor.putdouble("CURRENT_AMOUNT", currentAmount + addedAmount);
        editor.putString("SHIFT", model.toJson());
        editor.commit();
    }

    public ShiftModel getShift() {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        String shiftJson = preferences.getString("SHIFT", "");
        ShiftModel model = ShiftModel.fromJson(shiftJson);
        return model;
    }

    public void saveCurrentRefuel(int id, int localId) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("REFUEL_ID", id);
        editor.putInt("REFUEL_LOCAL_ID", localId);
        editor.apply();
    }

    public void clearCurrentRefuel() {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("REFUEL_ID");
        editor.remove("REFUEL_LOCAL_ID");
        editor.apply();
    }

    public int getCurrentRefuel(boolean local) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        return preferences.getInt(local ? "REFUEL_LOCAL_ID" : "REFUEL_ID", 0);
    }

    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    public InvoiceFormModel[] getInvoiceForms() {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        int default_form_invoice = preferences.getInt("DEFAULT_FORM_INVOICE",0);
        int default_form_bill = preferences.getInt("DEFAULT_FORM_BILL",0);
        String json = preferences.getString("INVOICE_FORM_DATA","");
        if (json!=null)
        {
            InvoiceFormModel[] forms = gson.fromJson(json,InvoiceFormModel[].class);
            for(InvoiceFormModel item: forms)
            {
                if (item.getId() == default_form_invoice && item.getPrintTemplate() == INVOICE_TYPE.INVOICE)
                    item.setLocalDefault(true);

                if (item.getId() == default_form_bill && item.getPrintTemplate() == INVOICE_TYPE.BILL)
                    item.setLocalDefault(true);


            }
            return  forms;
        }

        return null;
    }

    public void saveInvoiceForms(InvoiceFormModel[] invoiceFormModels) {
        final SharedPreferences preferences = getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        String json = gson.toJson(invoiceFormModels);

        editor.putString("INVOICE_FORM_DATA", json);
        editor.commit();
    }
}
