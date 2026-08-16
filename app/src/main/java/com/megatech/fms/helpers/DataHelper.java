package com.megatech.fms.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;


import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.UserBaseActivity;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.CheckTrucks;
import com.megatech.fms.data.entity.Flight;
import com.megatech.fms.data.entity.Invoice;
import com.megatech.fms.data.entity.LogEntry;
import com.megatech.fms.data.entity.Product;
import com.megatech.fms.data.entity.Receipt;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.data.entity.Review;
import com.megatech.fms.data.entity.Shift;
import com.megatech.fms.data.entity.Truck;
import com.megatech.fms.data.entity.TruckFuel;
import com.megatech.fms.data.entity.User;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM2504Model;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.LogEntryModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.ReviewModel;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class DataHelper {

    /**
     * Serialize read/merge/write operations for RefuelItem. Activities and the
     * background synchronizer can otherwise write different in-memory snapshots
     * of the same row at the same time.
     */
    private static final Object REFUEL_WRITE_LOCK = new Object();
    private static final AtomicBoolean processing = new AtomicBoolean(false);
    private static final AtomicBoolean syncLocked = new AtomicBoolean(false);
    private static final AtomicBoolean syncPending = new AtomicBoolean(false);
    private static final AtomicInteger activeSyncTasks = new AtomicInteger(0);
    private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "FMS-Sync-Worker"));
    private static final boolean isDebug = BuildConfig.DEBUG || true;

    private static final Object DEPENDENCY_LOCK = new Object();

    /**
     * Dependency được tạo LAZY, không phải lúc class load: khởi tạo sớm kéo theo cả
     * Crashlytics/Room và nếu hỏng thì cả class DataHelper chết vĩnh viễn với
     * NoClassDefFoundError ở mọi lời gọi về sau.
     *
     * <p>Không bao giờ để null lọt ra ngoài: khởi tạo thất bại thì ném
     * {@link IllegalStateException} kèm nguyên nhân, ngay tại chỗ khởi tạo.
     */
    private static HttpClient httpClient;
    private static DataRepository repo;

    private static DataRepository requireRepository() {
        synchronized (DEPENDENCY_LOCK) {
            if (repo == null)
                repo = createRepository(FMSApplication.getApplication());
            return repo;
        }
    }

    private static HttpClient requireHttpClient() {
        synchronized (DEPENDENCY_LOCK) {
            if (httpClient == null)
                httpClient = createHttpClient(FMSApplication.getApplication());
            return httpClient;
        }
    }

    @androidx.annotation.VisibleForTesting
    static DataRepository createRepository(Context app) {
        if (app == null)
            throw new IllegalStateException(
                    "FMSApplication chưa khởi tạo — không tạo được DataRepository");
        try {
            return DataRepository.getInstance(AppDatabase.getInstance(app.getApplicationContext()));
        } catch (RuntimeException ex) {
            Log.e("DataHelper", "Không khởi tạo được DataRepository", ex);
            throw new IllegalStateException("Không khởi tạo được DataRepository", ex);
        }
    }

    @androidx.annotation.VisibleForTesting
    static HttpClient createHttpClient(Context app) {
        if (app == null)
            throw new IllegalStateException(
                    "FMSApplication chưa khởi tạo — không tạo được HttpClient");
        try {
            return new HttpClient();
        } catch (RuntimeException ex) {
            Log.e("DataHelper", "Không khởi tạo được HttpClient", ex);
            throw new IllegalStateException("Không khởi tạo được HttpClient", ex);
        }
    }

    /**
     * Seam chỉ dành cho test: cài cả hai dependency cùng lúc để không rơi vào trạng thái
     * nửa thật nửa giả. Production không bao giờ gọi hai hàm này — chúng là package-private.
     *
     * <p>Dependency là static toàn cục nên test dùng seam <b>không được chạy song song</b>,
     * và phải gọi {@link #resetTestDependencies()} trong {@code @After}/{@code finally}.
     */
    @androidx.annotation.VisibleForTesting
    static void installTestDependencies(DataRepository testRepo, HttpClient testHttpClient) {
        synchronized (DEPENDENCY_LOCK) {
            repo = testRepo;
            httpClient = testHttpClient;
        }
    }

    @androidx.annotation.VisibleForTesting
    static void resetTestDependencies() {
        synchronized (DEPENDENCY_LOCK) {
            // Trả về null để lần dùng kế tiếp khởi tạo lại theo đường production.
            repo = null;
            httpClient = null;
        }
        conflictStreak.clear();
        verificationState.clear();
        inFlightRefuelKeys.clear();
        syncLocked.set(false);
        syncPending.set(false);
    }

    private static void startSyncTask(String name, Runnable task) {
        activeSyncTasks.incrementAndGet();
        try {
            syncExecutor.execute(() -> {
            try {
                task.run();
            } catch (Throwable ex) {
                Logger.appendLog("SYNC", name + " failed: " + ex.getMessage());
            } finally {
                finishSyncTask();
            }
            });
        } catch (Throwable ex) {
            Logger.appendLog("SYNC", name + " could not be queued: " + ex.getMessage());
            finishSyncTask();
        }
    }

    private static void finishSyncTask() {
        if (activeSyncTasks.decrementAndGet() == 0) {
            processing.set(false);
            notifyDataChanged("SYNC");
            if (!syncLocked.get() && syncPending.getAndSet(false)) {
                Synchronize();
            }
        }
    }

    /**
     * Báo cho các màn hình đang mở là dữ liệu trong Room vừa đổi.
     *
     * <p>Gửi được nhiều lần trong một phiên sync: phần dữ liệu nào xong trước thì màn hình
     * thấy trước, không phải đợi worker chạy hết mọi loại biểu mẫu.
     */
    private static void notifyDataChanged(String name) {
        try {
            Intent intent = new Intent(UserBaseActivity.SYNC_BROADCAST);
            intent.putExtra("Name", name);
            FMSApplication.getApplication().sendBroadcast(intent);
        } catch (Exception ex) {
            Logger.appendLog("SYNC", "notifyDataChanged " + name + " failed: " + ex.getMessage());
        }
    }


    public static List<TruckModel> getTrucks() {
        List<TruckModel> remoteTrucks = requireHttpClient().getTrucks();
        if (remoteTrucks != null && !remoteTrucks.isEmpty()) {
            for (TruckModel model : remoteTrucks) {
                requireRepository().insertTruck(Truck.fromTruckModel(model));
            }
            return remoteTrucks;
        }

        // Cài mới sẽ ưu tiên API; khi mất mạng mới dùng dữ liệu đã lưu local.
        return requireRepository().getTrucks();
    }

    public static List<TruckModel> getFHSTrucks() {

        return requireRepository().getFHSTrucks();


    }

    public static List<RefuelItemData> getRefuelList(boolean self, int type) {
        if (isDebug) {

          /*  new Runnable() {
                @Override
                public void run() {
                    Synchronize();
                }
            }.run();*/

            ShiftModel shiftModel = FMSApplication.getApplication().getShift();
            Date d = new Date();
            if (shiftModel == null || d.compareTo(shiftModel.getStartTime()) < 0 || d.compareTo(shiftModel.getEndTime()) > 0) {
                ShiftModel model = requireHttpClient().getShift();
                if (model != null) {
                    FMSApplication.getApplication().saveShift(model);
                    shiftModel = model;
                }
            }
            if (shiftModel == null) {
                shiftModel = new ShiftModel();
                shiftModel.setSelected(true);
            }
            ShiftModel selected = shiftModel.isSelected() ? shiftModel : null;
            if (selected == null) {
                if (shiftModel.getPrevShift() != null && shiftModel.getPrevShift().isSelected())
                    selected = shiftModel.getPrevShift();
                else if (shiftModel.getNextShift() != null && shiftModel.getNextShift().isSelected())
                    selected = shiftModel.getNextShift();
            }
            if (selected != null) {

                long start = selected.getStartTime().getTime() - 30 * 60 * 1000;
                long end = selected.getEndTime().getTime() + 30 * 60 * 1000;

                return requireRepository().getRefuelList(FMSApplication.getApplication().getTruckNo(), FMSApplication.getApplication().getTruckId(), self, type, start, end);
            }
            return requireRepository().getRefuelList(FMSApplication.getApplication().getTruckNo(), FMSApplication.getApplication().getTruckId(), self, type);
        } else
            return requireHttpClient().getRefuelList(self);
    }

    public static RefuelItemData getRefuelItem(String uniqueId) {
        return getRefuelItem(uniqueId, false);
    }

    public static RefuelItemData getRefuelItem(String uniqueId, boolean locked) {
        RefuelItemData remoteItem = requireHttpClient().getRefuelItem(uniqueId);

        // Read/check/create must be atomic: two threads opening the same refuel
        // would otherwise both see null and insert a duplicate row.
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem localItem = requireRepository().getRefuel(uniqueId);

            if (localItem == null) {
                if (remoteItem == null) {
                    return null;
                }
                localItem = RefuelItem.fromRefuelItemData(remoteItem);
                requireRepository().insertRefuel(localItem);
                remoteItem.setLocalId(localItem.getLocalId());
                // Baseline lấy từ chính row vừa ghi.
                return localItem.toRefuelItemData();
            }

            // Trộn theo quyền sở hữu trường rồi GHI XUỐNG ROOM trước khi dựng model trả về.
            // Nếu chỉ trả object cho màn hình mà Room giữ bản cũ thì snapshot đứng trên một
            // phiên bản không tồn tại ở nguồn chuẩn ⇒ lần lưu kế tiếp lập tức conflict.
            if (remoteItem != null)
                applyRemoteToLocal("GET_ITEM", localItem, remoteItem);

            remoteItem = localItem.toRefuelItemData();
            List<RefuelItem> others = requireRepository().getOthers(uniqueId);
            remoteItem.setOthers(new ArrayList<>());
            for (RefuelItem item : others) {
                remoteItem.getOthers().add(item.toRefuelItemData());
            }
        }

        return remoteItem;
    }

    /**
     * Cập nhật một trường metadata trên bản ghi MỚI NHẤT trong Room.
     *
     * <p>Dùng cho các thao tác chỉ đổi metadata (rời đi, số receipt, số hoá đơn...).
     * Tuyệt đối không POST lại business payload đang giữ trên Activity: snapshot đó có
     * thể đã cũ và sẽ ghi đè số liệu đồng hồ vừa chốt.
     *
     * @return bản ghi sau khi patch, hoặc null nếu không tìm thấy row.
     */
    public static PatchResult patchRefuel(String uniqueId, RefuelPatch patch) {
        if (uniqueId == null || uniqueId.isEmpty() || patch == null)
            return PatchResult.failed("INVALID_ARGUMENT", null);

        PatchResult result;
        // Đọc – sửa – ghi trong CÙNG một lần giữ khoá: nếu tách ra thì giữa lúc đọc và lúc
        // ghi vẫn còn khe cho luồng khác chen vào, đúng loại race đang phải xử lý.
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem localItem = requireRepository().getRefuel(uniqueId);
            if (localItem == null)
                return PatchResult.failed("NOT_FOUND", null);

            RefuelItemData latest = localItem.toRefuelItemData();
            RefuelItemData beforePatch = localItem.toRefuelItemData();

            patch.apply(latest);

            // Patch chỉ được đụng metadata. Nếu nó lỡ đổi số liệu chốt của mẻ thì từ chối,
            // vì đường ghi số liệu đồng hồ phải là luồng riêng có kiểm soát.
            String finalDiff = RefuelSyncGuard.describeFinalValueDiff(beforePatch, latest);
            if (finalDiff != null) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=PATCH_REJECTED uid=%s reason=FINAL_VALUES_TOUCHED diff=%s thread=%s",
                        uniqueId, finalDiff, Thread.currentThread().getName()));
                return PatchResult.failed("FINAL_VALUES_TOUCHED", beforePatch);
            }

            latest.setClientSeq(localItem.getClientSeq() + 1);
            localItem.updateData(latest);
            resumeSync(localItem);
            localItem.setLocalModified(true);
            requireRepository().insertRefuel(localItem);

            result = PatchResult.applied(localItem.toRefuelItemData());
        }

        Synchronize();
        return result;
    }

    /** Thao tác patch trên bản ghi mới nhất — chỉ được đụng vào trường metadata. */
    public interface RefuelPatch {
        void apply(RefuelItemData latest);
    }

    /** Kết quả tường minh của {@link #patchRefuel}: gọi xong luôn biết đã ghi hay chưa. */
    public static class PatchResult {
        public final boolean applied;
        public final String reason;
        public final RefuelItemData data;

        private PatchResult(boolean applied, String reason, RefuelItemData data) {
            this.applied = applied;
            this.reason = reason;
            this.data = data;
        }

        static PatchResult applied(RefuelItemData data) {
            return new PatchResult(true, null, data);
        }

        static PatchResult failed(String reason, RefuelItemData current) {
            return new PatchResult(false, reason, current);
        }
    }

    public static void lockSync() {
        syncLocked.set(true);
    }

    public static void unlockSync() {
        syncLocked.set(false);
        if (syncPending.getAndSet(false)) {
            Synchronize();
        }
    }

    public static RefuelItemData getItemToRefuel(Integer flightId) {
        RefuelItem localItem = requireRepository().getRefuelByFlightAndTruck(flightId, FMSApplication.getApplication().getTruckId());
        if (localItem != null)
            return localItem.toRefuelItemData();
        else
            return null;

    }

    public static RefuelItemData getRefuelItem(Integer id, Integer localId) {
        RefuelItemData remoteItem;

        RefuelItem localItem = requireRepository().getRefuel(id, localId);
        if (id == 0 && localItem != null)
            id = localItem.getId();
        Logger.appendLog("DTH", "Start remote loading item " + id + " - " + localId);

        remoteItem = requireHttpClient().getRefuelItem(id);
        Logger.appendLog("DTH", "End remote loading item " + id + " - " + localId);

        // Re-read inside the lock: the row may have been created or modified while
        // the HTTP request above was running.
        synchronized (REFUEL_WRITE_LOCK) {
            localItem = requireRepository().getRefuel(id, localId);

            // Cùng quy tắc với getRefuelItem(uniqueId): trộn theo quyền sở hữu trường,
            // ghi xuống Room, rồi mới dựng model trả về cho màn hình.
            if (localItem == null) {
                if (remoteItem == null) return null;

                RefuelItem serverRow = RefuelItem.fromRefuelItemData(remoteItem);
                serverRow.setLocalModified(false);
                serverRow.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                requireRepository().insertRefuel(serverRow);
                return serverRow.toRefuelItemData();
            }

            if (remoteItem != null)
                applyRemoteToLocal("GET_ITEM_BY_ID", localItem, remoteItem);

            remoteItem = localItem.toRefuelItemData();
            List<RefuelItem> others = requireRepository().getOthers(localId);
            remoteItem.setOthers(new ArrayList<>());
            for (RefuelItem item : others) {
                remoteItem.getOthers().add(item.toRefuelItemData());
            }
        }

        return remoteItem;

    }

    /**
     * Bảo trì sau khi nâng cấp — chạy MỘT LẦN cho mỗi phiên bản, tại thời điểm đăng nhập.
     *
     * <p>Việc cần làm: đưa các phiếu đang kẹt {@code postStatus = ERROR} trở lại hàng đợi.
     * Luật ACK cũ đòi ServerRevision phải tăng, mà server có những đường ghi hợp lệ không
     * tăng nó, nên gần như mọi phiếu đã POST đều bị gắn cờ conflict. Cờ đó loại phiếu khỏi
     * {@code getModifiedForSync()} cho tới khi người dùng mở ra sửa lại — nghĩa là không dọn
     * thì phiếu cũ vẫn phải gõ tay dù bản vá đã cài.
     *
     * <p>Chỉ chạy khi versionCode đổi: nếu chạy mọi lần đăng nhập thì một phiếu conflict thật
     * sẽ bị đánh thức lặp đi lặp lại.
     */
    public static void runUpgradeMaintenance(android.content.Context context) {
        if (context == null) return;

        try {
            android.content.SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences("FMS", android.content.Context.MODE_PRIVATE);

            int lastVersion = prefs.getInt("LAST_MAINTENANCE_VERSION", 0);
            if (lastVersion == BuildConfig.VERSION_CODE) return;

            int resumed = requireRepository().resumeConflictedRefuels();
            prefs.edit().putInt("LAST_MAINTENANCE_VERSION", BuildConfig.VERSION_CODE).apply();

            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "Bảo trì nâng cấp %d -> %d: đưa %d phiếu kẹt trở lại hàng đợi",
                    lastVersion, BuildConfig.VERSION_CODE, resumed));
        } catch (Exception ex) {
            // Bảo trì hỏng không được phép chặn đăng nhập.
            Logger.appendLog("SYNC", "Bảo trì nâng cấp lỗi: " + ex.getMessage());
        }
    }

    public static boolean checkLocalModified() {

        return requireRepository().getLocalModified();
    }

    public static void Synchronize() {
//        Logger.appendLog("SYNC", "Start synchronize");
//
//        if (!AppStateObserver.isAppInForeground()) {
//            Logger.appendLog("SYNC", "Abort sync — app in background");
//            return;
//        }
        if (syncLocked.get()) {
            syncPending.set(true);
            return;
        }
        if (processing.compareAndSet(false, true)) {   // chỉ MỘT phiên sync vào được
            activeSyncTasks.set(1); // coordinator giữ phiên sync cho tới khi đã tạo đủ worker
            Logger.appendLog("SYNC", "START Receipt thread=" + Thread.currentThread().getName());
            //post local modified data
            startSyncTask("core", () -> {

                try{
                    syncModifiedRefuels();

                    Date d = requireRepository().getLastModifiedRefuel();
                    List<RefuelItemData> remoteList = requireHttpClient().getModifiedRefuels(0, d);
                    if (remoteList != null) {
                        int[] ids = new int[remoteList.size()];
                        int i = 0;
                        for (RefuelItemData model : remoteList) {
                            if (!model.isDeleted()) {
                                RefuelItem remoteItem = RefuelItem.fromRefuelItemData(model);
                                synchronized (REFUEL_WRITE_LOCK) {
                                    RefuelItem localItem = requireRepository().getRefuel(remoteItem.getUniqueId());
                                    if (localItem == null) {
                                        localItem = requireRepository().getRefuel(remoteItem.getId(), remoteItem.getLocalId());
                                    }

                                    if (localItem == null) {
                                        requireRepository().insertRefuel(remoteItem);
                                    } else {
                                        applyRemoteToLocal("REMOTE_PULL", localItem, model);
                                    }
                                }

                                Flight flight = new Flight();
                                flight.setId(model.getFlightId());
                                flight.setCode(model.getFlightCode());
                                flight.setAircraftCode(model.getAircraftCode());
                                flight.setAircraftType(model.getAircraftType());
                                flight.setRefuelScheduledTime(model.getRefuelTime());
                                flight.setRouteName(model.getRouteName());
                                flight.setParkingLot(model.getParkingLot());
                                flight.setAirlineId(model.getAirlineId());
                                requireRepository().insertFlight(flight);
                            } else if (model.getId() > 0)
                                ids[i++] = model.getId();
                        }
                        requireRepository().removeDeletedRefuels(ids);
                    }


                    // Dọn dữ liệu quá hạn chạy ở worker riêng bên dưới (startSyncTask
                    // "retention"): nó đụng cả file trên đĩa nên không được nằm chắn trước
                    // bước báo dữ liệu tra nạp đã sẵn sàng.

                    // Kế hoạch tra nạp đã nằm trong Room ở đây. Báo ngay cho màn hình thay vì
                    // đợi hết phiên sync: phía sau còn receipt, invoice, BM25xx, master-data...
                    // chạy tuần tự trên cùng một worker nên chờ tới cuối là chờ rất lâu.
                    notifyDataChanged("REFUEL");

                    List<Review> modifiedReview = requireRepository().getModifiedReview();

                    if (modifiedReview.size()>0)
                    {
                        ReviewAPI reviewClient = new ReviewAPI();
                        for (Review item:modifiedReview)
                        {
                            ReviewModel itemModel = item.toModel();
                            ReviewModel postedModel = reviewClient.postReview(itemModel);
                            if (postedModel!=null)
                            {
                                item.setLocalModified(false);
                                item.setId(postedModel.getId());
                                item.setJsonData(postedModel.toJson());
                                requireRepository().postReview(item);
                            }

                        }
                    }

                    List<Receipt> modifiedReceipt = requireRepository().getModifiedReceipt();
                    ReceiptAPI client = new ReceiptAPI();
                    if (modifiedReceipt.size() > 0) {
                        for (Receipt item : modifiedReceipt) {

                            // chốt: nếu bản này đã được luồng khác sync xong thì bỏ qua (phòng race còn sót)
                            if (!item.isLocalModified()) {
                                Logger.appendLog("DTH", "Sync SKIP đã sync num=" + item.getNumber());
                                continue;
                            }

                            ReceiptModel itemData = item.toModel();
                            Logger.appendLog("DTH", "Sync POST num=" + itemData.getNumber()
                                    + " uniqueId=" + itemData.getUniqueId()
                                    + " localId=" + item.getLocalId()
                                    + " thread=" + Thread.currentThread().getName());

                            ReceiptModel newData = client.post(itemData);

                            Logger.appendLog("DTH", "Sync RESULT num=" + itemData.getNumber()
                                    + " uniqueId=" + itemData.getUniqueId()
                                    + " serverId=" + (newData == null ? "null" : newData.getId()));

                            if (newData != null) {
                                newData.setPdfPath(itemData.getPdfPath());
                                newData.setSignaturePath(itemData.getSignaturePath());
                                newData.setSellerSignaturePath(itemData.getSellerSignaturePath());
                                newData.setSignImageString(null);
                                newData.setPdfImageString(null);

                                item.setLocalModified(false);
                                item.setId(newData.getId());
                                item.setJsonData(newData.toJson());
                                requireRepository().insertReceipt(item);
                            }
                        }
                    }
                    List<Invoice> modifiedInvoice = requireRepository().getModifiedInvoice();
                    InvoiceAPI invoiceAPI = new InvoiceAPI();
                    if (modifiedInvoice.size() > 0) {
                        for (Invoice item : modifiedInvoice) {
                            InvoiceModel itemData = item.toModel();
                            InvoiceModel newData = invoiceAPI.post(itemData);

                            if (newData != null) {


                                item.setLocalModified(false);

                                item.setId(newData.getId());
                                item.setJsonData(newData.toJson());
                                requireRepository().insertInvoice(item);
                            }
                        }
                    }
                }
                finally {
                    // startSyncTask sẽ đóng worker và chỉ mở khóa khi mọi worker đã xong.
                }
            });
            // synchronize truck fuels items
            startSyncTask("truck-fuel", () -> {

                List<TruckFuel> modified = requireRepository().getModifiedTruckFuel();
                if (modified.size() > 0) {
                    for (TruckFuel item : modified) {
                        TruckFuelModel itemData = item.toTruckFuelModel();
                        Logger.appendLog("B2502", "Sync localId=" + itemData.getLocalId()
                                + ", requestId=" + itemData.getId());
                        TruckFuelModel newData = requireHttpClient().postTruckFuel(itemData);
                        if (newData != null) {
                            // Giữ payload local đầy đủ; response POST của API có thể chỉ trả một phần field.
                            item.setId(newData.getId());
                            itemData.setId(newData.getId());
                            item.setJsonData(itemData.toJson());
                            item.setLocalModified(false);
                            requireRepository().insertTruckFuel(item);
                            Logger.appendLog("B2502", "Synced localId=" + item.getLocalId()
                                    + ", responseId=" + newData.getId());
                        }
                    }
                }
                List<TruckFuelModel> lstModel = requireHttpClient().getTruckFuels();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (TruckFuelModel model : lstModel) {
                        requireRepository().mergeRemoteTruckFuel(TruckFuel.fromTruckFuelModel(model));

                    }

                }

            });

            //sync BM2505
            startSyncTask("bm2505", () -> {

                List<BM2505> modified = requireRepository().getModifiedBM2505();
                if (modified.size() > 0) {
                    for (BM2505 item : modified) {
                        BM2505Model itemData = item.toModel();
                        BM2505Model newData = requireHttpClient().postBM2505(itemData);
                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());
                            requireRepository().insertBM2505(item);
                        }
                    }
                }
                List<BM2505Model> lstModel = requireHttpClient().getBM2505List();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (BM2505Model model : lstModel) {
                        requireRepository().mergeRemoteBM2505(BM2505.fromModel(model));

                    }

                }

                List<BM2505ContainerModel> lstContainer = requireHttpClient().getBM2505ContainerList();

                if (lstContainer != null) {
                    int i = 0;
                    for (BM2505ContainerModel model : lstContainer) {
                        requireRepository().insertBM2505Container(BM2505Container.fromModel(model));

                    }

                }

            });

            // =====================
            // SYNC BM2503
            // =====================
            startSyncTask("bm2503", () -> {

                List<BM2503> modified = requireRepository().getModifiedBM2503();
                if (modified.size() > 0) {
                    for (BM2503 item : modified) {

                        BM2503Model itemData = item.toModel();
                        BM2503Model newData = requireHttpClient().postBM2503(itemData);

                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());

                            requireRepository().insertBM2503(item);
                        }
                    }
                }

                // pull từ server về
                List<BM2503Model> lstModel = requireHttpClient().getBM2503List();
                if (lstModel != null) {
                    for (BM2503Model model : lstModel) {
                        requireRepository().mergeRemoteBM2503(BM2503.fromModel(model));
                    }
                }

            });

            // =====================
// SYNC BM2504
// =====================
            startSyncTask("bm2504", () -> {

                // ===== PUSH LOCAL MODIFIED =====
                List<BM2504> modified = requireRepository().getModifiedBM2504();
                if (modified != null && modified.size() > 0) {

                    for (BM2504 item : modified) {

                        BM2504Model itemData = item.toModel();
                        BM2504Model newData = requireHttpClient().postBM2504(itemData);

                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());

                            requireRepository().insertBM2504(item);
                        }
                    }
                }

                // ===== PULL FROM SERVER =====
                List<BM2504Model> lstModel = requireHttpClient().getBM2504List();
                if (lstModel != null && lstModel.size() > 0) {

                    for (BM2504Model model : lstModel) {
                        requireRepository().mergeRemoteBM2504(BM2504.fromModel(model));
                    }
                }

            });


            //sync BM2307A
            startSyncTask("check-trucks", () -> {

                List<CheckTrucks> modified = requireRepository().getModifiedCheckTrucks();
                if (modified.size() > 0) {
                    for (CheckTrucks item : modified) {
                        CheckTrucksModel itemData = item.toModel();
                        CheckTrucksModel newData = requireHttpClient().postCheckTrucks(itemData);
                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());
                            requireRepository().insertCheckTrucks(item);
                        }
                    }
                }
                List<CheckTrucksModel> lstModel = requireHttpClient().getCheckTrucksList();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (CheckTrucksModel model : lstModel) {
                        requireRepository().mergeRemoteCheckTrucks(CheckTrucks.fromModel(model));
                    }

                }
            });
            //sync BM2508
            startSyncTask("bm2508", () -> {

                List<BM2508> modified = requireRepository().getModifiedBM2508();
                if (modified.size() > 0) {
                    for (BM2508 item : modified) {
                        BM2508Model itemData = item.toModel();
                        BM2508Model newData = requireHttpClient().postBM2508Post2(itemData);
                        if (newData != null) {
                            item.setId(newData.getId());
                            itemData.setId(newData.getId());
                            item.setJsonData(itemData.toJson());
                            // Dữ liệu chính đã thành công: không POST lại, dù ảnh có thể chưa gửi được.
                            item.setLocalModified(false);
                            item.setAttachmentPending(hasCompleteBM2508Attachments(itemData));
                            requireRepository().insertBM2508(item);
                        }
                    }
                }

                // Ảnh/chữ ký có hàng đợi riêng; lỗi chỉ retry ảnh theo Id đã có trên server.
                List<BM2508> pendingAttachments = requireRepository().getPendingBM2508Attachments();
                ReceiptAPI attachmentApi = new ReceiptAPI();
                for (BM2508 item : pendingAttachments) {
                    BM2508Model itemData = item.toModel();
                    if (!hasCompleteBM2508Attachments(itemData)) {
                        // Thiếu ảnh/chữ ký hoặc file đã bị xóa: dừng retry, giữ nguyên dữ liệu phiếu.
                        item.setAttachmentPending(false);
                        requireRepository().insertBM2508(item);
                        Logger.appendLog("BM2508_ATTACHMENT", "Stop retry, missing local attachment. id=" + item.getId());
                    } else if (attachmentApi.postMultipartBM2508(itemData) != null) {
                        item.setAttachmentPending(false);
                        requireRepository().insertBM2508(item);
                    }
                }
                List<BM2508Model> lstModel = requireHttpClient().getBM2508List();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (BM2508Model model : lstModel) {
                        requireRepository().mergeRemoteBM2508(BM2508.fromModel(model));
                        ReceiptAPI client = new ReceiptAPI();
                        // Gửi file ảnh lên API
                        //client.postMultipartBM2508(model);
                    }

                }
            });
            //update airlines, users from another thread
            startSyncTask("master-data", () -> {
                List<AirlineModel> lstModel = requireHttpClient().getAirlines();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (AirlineModel model : lstModel) {
                        requireRepository().insertAirline(Airline.fromAirlineModel(model));

                        //ids[i++] = model.getId();
                    }
                    //requireRepository().deleteOudateTrucks(ids);
                }

                List<AirportsModel> lstAirports = requireHttpClient().getAirports();
                if (lstAirports != null) {
                    for (AirportsModel model : lstAirports) {
                        requireRepository().insertAirports(Airports.fromAirportsModel(model));
                    }
                }

                // sync Product
                startSyncTask("products", () -> {


                    List<ProductModel> lstproduct = requireHttpClient().getProductList();

                    if (lstproduct != null && lstproduct.size() > 0) {
                        for (ProductModel model : lstproduct) {
                            requireRepository().insertProduct(Product.fromModel(model));
                        }
                    }

                });

                //Users

                List<UserModel> lstUser = requireHttpClient().getUsers();

                if (lstUser != null) {
                    int[] ids = new int[lstUser.size()];
                    int i = 0;
                    for (UserModel model : lstUser) {

                        requireRepository().insertUser(User.fromUserModel(model));

                        //ids[i++] = model.getId();
                    }
                    //requireRepository().deleteOudateTrucks(ids);
                }

                InvoiceFormModel[] invoiceForms = getInvoiceForms();
                if (invoiceForms != null)
                    FMSApplication.getApplication().saveInvoiceForms(invoiceForms);

                List<TruckModel> lstTrucks = requireHttpClient().getTrucks();

                if (lstTrucks != null) {
                    int[] ids = new int[lstTrucks.size()];
                    int i = 0;
                    for (TruckModel model : lstTrucks) {
                        requireRepository().insertTruck(Truck.fromTruckModel(model));

                        ids[i++] = model.getId();
                    }
                    //requireRepository().deleteOudateTrucks(ids);
                }

            });

            startSyncTask("logs", () -> {
                Logger.sendLog();
            });

            // Dọn dữ liệu quá hạn KHÔNG nằm ở đây: xem DataRetention.purgeAfterLogin().
            // Đặt vào chu kỳ đồng bộ (~30 giây/lần) là thừa — dữ liệu chỉ già đi theo ngày,
            // nên quét lại vài nghìn lần mỗi ngày không dọn thêm được gì.

            startSyncTask("screenshots", () -> {
                ScreenshotAPI api = new ScreenshotAPI();
                try {
                    File folder = FMSApplication.getApplication()
                            .getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    FilenameFilter filter = (dir, name) -> name.startsWith("screenshot_") && name.endsWith(".jpg");
                    File[] files = folder.listFiles(filter);
                    for (File f : files) {
                        if (api.postScreenshot(f))
                            f.delete();

                    }
                } catch (Exception ex) {
                    Logger.appendLog("SYNC", "screenshots failed: " + ex.getMessage());
                }
            });
            //get n
            finishSyncTask(); // coordinator đã tạo xong toàn bộ worker
        }
        else {
            syncPending.set(true);
            Logger.appendLog("SYNC", "SKIP receipt (đang chạy) thread=" + Thread.currentThread().getName());
        }

    }

    /**
     * Đẩy các phiếu tra nạp còn thay đổi chưa gửi lên server.
     *
     * <p>Tách riêng khỏi {@link #Synchronize()} để test gọi được đồng bộ, không phải chờ
     * executor và không kéo theo receipt/BM/master-data/screenshots.
     *
     * <p>Thứ tự guard cho từng row: DAO đã loại {@code postStatus = ERROR} → đang trong
     * verification backoff thì bỏ qua → không giành được marker in-flight thì bỏ qua →
     * POST trong {@code try} → xoá marker trong {@code finally}.
     */
    @androidx.annotation.VisibleForTesting
    static void syncModifiedRefuels() {
        List<RefuelItem> modified = requireRepository().getModifiedRefuel();
        if (modified == null || modified.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (RefuelItem item : modified) {

            if (shouldDeferVerificationSync(item.getUniqueId(), now)) {
                Logger.appendLog("SYNC", "Defer POST (verification backoff) uid=" + item.getUniqueId());
                continue;
            }

            String postKey = refuelPostKey(item);
            if (!tryBeginRefuelPost(postKey)) {
                Logger.appendLog("SYNC", "Skip POST (đang có request bay) uid=" + item.getUniqueId());
                continue;
            }

            try {
                boolean needVerification = false;
                long verificationSeq = 0;

                // Đọc lại row mới nhất DƯỚI KHOÁ rồi mới dựng request: danh sách modified
                // được đọc ngoài khoá nên phần tử trong đó có thể đã cũ.
                RefuelItemData itemData;
                synchronized (REFUEL_WRITE_LOCK) {
                    RefuelItem fresh = requireRepository().getRefuel(item.getId(), item.getLocalId());
                    if (fresh == null || !fresh.isLocalModified()) continue;
                    item = fresh;
                    itemData = fresh.toRefuelItemData();
                }

                RefuelItemData newData = requireHttpClient().postRefuel(itemData);
                // Log trước mọi nhánh xử lý — các nhánh bên dưới có continue sớm.
                logPostExchange("BACKGROUND_SYNC", itemData, newData);

                if (newData == null) continue;

                if (newData.getStatus() == REFUEL_ITEM_STATUS.DONE
                        && itemData.getStatus() != REFUEL_ITEM_STATUS.DONE
                        && finalRefuelValuesChanged(newData, itemData)) {
                    logFinalRefuelChange("BACKGROUND_SYNC", newData, itemData,
                            "SERVER_REJECTED_DOWNGRADE");
                }

                synchronized (REFUEL_WRITE_LOCK) {
                    // Re-read after HTTP: an Activity may have saved a newer
                    // snapshot while this request was in flight.
                    RefuelItem newestLocal = requireRepository().getRefuel(item.getId(), item.getLocalId());
                    if (newestLocal == null) {
                        newestLocal = item;
                    }

                    // Row đã tiến lên trong lúc request bay: giữ bản mới hơn, chỉ nhận
                    // metadata server xác nhận và để nó tiếp tục nằm trong hàng đợi.
                    if (newestLocal.getClientSeq() != itemData.getClientSeq()) {
                        // Row đã tiến lên. Chỉ nhận metadata khi response đúng là ACK của
                        // request vừa gửi; response non-ACK không được nâng ServerRevision
                        // của một phiên bản mới hơn — làm vậy sẽ khiến chính bản mới đó
                        // trông như đã được server xác nhận.
                        if (RefuelSyncGuard.looksLikeServerAck(itemData, newData)) {
                            RefuelItemData keptBeforeMerge = newestLocal.toRefuelItemData();
                            RefuelSyncGuard.mergeServerMetadata(newestLocal, newData);
                            if (keptBeforeMerge.getStatus() == REFUEL_ITEM_STATUS.DONE
                                    && finalRefuelValuesChanged(keptBeforeMerge, newData)) {
                                logFinalRefuelChange("BACKGROUND_SYNC",
                                        keptBeforeMerge, newData, "PRESERVE_NEWER_LOCAL");
                            }
                            requireRepository().insertRefuel(newestLocal);
                        } else {
                            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                                    "Row moved on, bỏ qua response non-ACK uid=%s seqSent=%d seqNow=%d",
                                    newestLocal.getUniqueId(), itemData.getClientSeq(),
                                    newestLocal.getClientSeq()));
                        }
                        continue;
                    }

                    // Never let an old PROCESSING response downgrade a row
                    // which is already known locally as DONE.
                    if (newestLocal.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                            && newData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                        logFinalRefuelChange("BACKGROUND_SYNC",
                                newestLocal.toRefuelItemData(), newData, "BLOCK_OLD_RESPONSE");
                        Logger.appendLog("SYNC", "Ignore non-DONE response for DONE refuel "
                                + newestLocal.getId());
                        continue;
                    }

                    if (RefuelSyncGuard.looksLikeServerAck(itemData, newData)) {
                        // Server đã áp dụng đúng payload vừa gửi: giữ payload
                        // nghiệp vụ local, chỉ nhận metadata server xác nhận.
                        RefuelSyncGuard.applyServerAck(newestLocal, newData);
                        newestLocal.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                        clearConflictStreak(newestLocal.getUniqueId());
                        verificationState.remove(newestLocal.getUniqueId());
                    } else {
                        // Chưa xác nhận được: giữ nguyên dữ liệu local, không
                        // adopt payload của response.
                        noteUnconfirmedPost(newestLocal, itemData, newData);
                        needVerification = RefuelSyncGuard.isInconclusive(
                                RefuelSyncGuard.describeAck(itemData, newData));
                        verificationSeq = newestLocal.getClientSeq();
                    }
                    requireRepository().insertRefuel(newestLocal);
                }

                // GET xác thực chạy ngoài khoá ghi.
                if (needVerification)
                    verifyInconclusivePost(item.getUniqueId(), itemData, newData, verificationSeq);

            } finally {
                endRefuelPost(postKey);
            }
        }
    }

    public static void postRefuels(List<RefuelItemData> refuels) {
        postRefuels(refuels, false);
    }

    public static void postRefuels(List<RefuelItemData> refuels, boolean remotePost) {
        try {
            for (RefuelItemData item : refuels) {
                postRefuel(item, remotePost);
            }
        } catch (Exception ex) {
            Logger.appendLog("DTH", ex.getMessage());
        }
        Synchronize();
    }

    /**
     * Áp bản server lên một row đã có, tách theo QUYỀN SỞ HỮU TRƯỜNG.
     *
     * <p>Kế hoạch bay (chuyến, bãi đỗ, giờ dự kiến, cờ huỷ) do server sở hữu nên luôn được
     * ghi đè — kể cả khi row đang có thay đổi local chưa gửi. Số liệu của mẻ (đồng hồ, tỉ
     * trọng, trạng thái, cờ đã in) do thiết bị sở hữu nên chỉ nhận bản server khi local
     * không còn gì chờ gửi.
     *
     * <p>Không còn điều kiện "ServerRevision phải tăng". Server có đường ghi hợp lệ mà không
     * tăng revision (đổi số xe của phiếu, bỏ cờ xoá chuyến), và bản thân thay đổi chuyến bay
     * không bao giờ tăng revision của phiếu — nên cổng cũ đã chặn đứng mọi cập nhật loại này
     * mà không để lại một dòng log nào.
     *
     * <p>Phải gọi khi đang giữ {@link #REFUEL_WRITE_LOCK}.
     */
    private static void applyRemoteToLocal(String source, RefuelItem localItem, RefuelItemData remote) {
        if (localItem == null || remote == null) return;

        RefuelItemData before = localItem.toRefuelItemData();

        boolean localDirty = localItem.isLocalModified();
        boolean statusDowngrade = localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                && remote.getStatus() != REFUEL_ITEM_STATUS.DONE;
        // Bản server đang đứng trên một phiên bản CŨ HƠN bản đang có ở máy. Cờ dirty không
        // bắt được ca này: nó bị xoá ngay khi POST thành công, nên trong lúc đồng hồ chạy vẫn
        // còn khe cho một bản đọc cũ (cache, luồng export nền) ghi đè số vừa chốt — đúng hình
        // dạng sự cố 1110 bị 862 ghi đè.
        //
        // Lưu ý khác biệt với cổng cũ: ở đây KHÔNG đòi phiên bản phải tăng thì mới nhận. Chỉ
        // khi nó ĐI LÙI mới coi là bản đọc cũ. Phiên bản đứng yên — trường hợp của mọi thay
        // đổi chuyến bay — vẫn được nhận bình thường.
        //
        // ClientSeq chỉ dùng được khi server THỰC SỰ giữ nó. Dữ liệu thật cho thấy server
        // trả ClientSeq = 0 trong khi ServerRevision đã lên 19, tức là cột đó không được
        // ghi. Nếu coi 0 là "phiên bản 0" thì mọi phiếu mà máy từng sửa (seq > 0) sẽ vĩnh
        // viễn không nhận được sửa đổi từ web — đúng cái lỗi đang đi chữa. Vậy 0 = "không
        // có thông tin", không phải "cũ".
        boolean remoteSeqIsBehind = remote.getClientSeq() > 0
                && remote.getClientSeq() < localItem.getClientSeq();
        boolean remoteBehindLocal = remoteSeqIsBehind
                || remote.getServerRevision() < localItem.getServerRevision();
        boolean adoptClientOwned = !localDirty && !statusDowngrade && !remoteBehindLocal;

        RefuelItemData merged = RefuelSyncGuard.applyRemote(localItem, remote, adoptClientOwned);
        if (merged == null) {
            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s MERGE_FAILED uid=%s — giữ nguyên bản local",
                    source, localItem.getUniqueId()));
            return;
        }

        // Row đã sạch mà vẫn mang cờ conflict là tàn dư của lượt gửi trước: không còn gì
        // chờ gửi thì cũng không còn gì để xung đột.
        if (adoptClientOwned && localItem.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR) {
            localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
            clearConflictStreak(localItem.getUniqueId());
        }

        requireRepository().insertRefuel(localItem);

        String serverDiff = RefuelSyncGuard.describeServerOwnedDiff(before, merged);
        if (serverDiff != null)
            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s SERVER_FIELDS uid=%s %s", source, localItem.getUniqueId(), serverDiff));

        // Mọi lần KHÔNG nhận nhóm trường client sở hữu đều phải để lại dấu vết. Chính vì
        // nhánh bỏ qua trước đây im lặng mà lỗi mất cập nhật sống được rất lâu.
        if (!adoptClientOwned) {
            if (statusDowngrade)
                logFinalRefuelChange(source, before, remote, "BLOCK_DOWNGRADE");

            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s KEEP_LOCAL_VALUES uid=%s reason=%s localSeq=%d localRev=%d remoteRev=%d",
                    source, localItem.getUniqueId(),
                    statusDowngrade ? "LOCAL_DONE_REMOTE_NOT_DONE"
                            : localDirty ? "LOCAL_MODIFIED" : "REMOTE_BEHIND_LOCAL_SEQ",
                    // Giá trị TRƯỚC khi trộn: sau khi trộn seq/rev đã lấy max nên in ra
                    // sẽ không còn nói lên được vì sao quyết định như vậy.
                    before.getClientSeq(), before.getServerRevision(),
                    remote.getServerRevision()));
        }
    }

    /** Số lần POST liên tiếp không xác nhận được, theo uniqueId — chặn vòng lặp retry vô hạn. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> conflictStreak =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int MAX_UNCONFIRMED_POSTS = 3;

    private static void clearConflictStreak(String uniqueId) {
        if (uniqueId != null) conflictStreak.remove(uniqueId);
    }

    /**
     * Trạng thái backoff của GET xác thực, theo uniqueId. Chỉ nằm trong RAM: khởi động lại
     * app sẽ reset backoff (mỗi row được thử lại tối đa {@link #MAX_VERIFICATION_ATTEMPTS}
     * lần cho mỗi lần chạy app), đủ để không có vòng lặp POST+GET vô hạn.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> verificationState =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final long[] VERIFICATION_BACKOFF_MS = {
            5 * 60_000L, 15 * 60_000L, 60 * 60_000L};

    private static boolean shouldAttemptVerification(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) return false;

        long now = System.currentTimeMillis();
        long[] state = verificationState.get(uniqueId);   // {attempts, nextAttemptAt, clientSeq}
        if (state == null) return true;
        if (state[0] >= MAX_VERIFICATION_ATTEMPTS) return false;
        return now >= state[1];
    }

    /**
     * Các phiếu đang có một POST bay trên đường, khoá theo từng phiếu.
     *
     * <p>Direct POST và background sync dùng CHUNG tập này: nếu không, trong lúc direct
     * request đang bay, background vẫn đọc được row dirty và POST lần hai cùng một
     * ClientSeq — hai request song song, server chưa hard-reject stale nên có thể apply cả
     * hai và đẩy ServerRevision lên nhiều lần.
     *
     * <p>Chỉ nằm trong RAM: app bị kill thì marker mất, còn payload đã ở Room nên lần chạy
     * sau vẫn gửi lại được.
     */
    private static final java.util.Set<String> inFlightRefuelKeys =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Đăng ký nguyên tử. Dùng giá trị trả về của {@code add()}, không check-then-add
     * (hai luồng có thể cùng thấy "chưa có" rồi cùng thêm).
     *
     * @return true nếu giành được quyền gửi cho phiếu này.
     */
    @androidx.annotation.VisibleForTesting
    static boolean tryBeginRefuelPost(String key) {
        return key != null && !key.isEmpty() && inFlightRefuelKeys.add(key);
    }

    @androidx.annotation.VisibleForTesting
    static void endRefuelPost(String key) {
        if (key != null) inFlightRefuelKeys.remove(key);
    }

    /**
     * Khoá của một phiếu: ưu tiên uniqueId; phiếu chưa có uniqueId dùng localId — sau khi
     * đã persist thì localId luôn > 0, nên không có chuyện nhiều phiếu mới dùng chung khoá.
     */
    private static String refuelPostKey(RefuelItem item) {
        if (item == null) return null;
        String uid = item.getUniqueId();
        if (uid != null && !uid.trim().isEmpty()) return uid;
        return "local:" + item.getLocalId();
    }

    /**
     * Row đang chờ hết backoff của GET xác thực thì KHÔNG được POST lại.
     *
     * <p>Trước đây backoff chỉ hoãn GET; row vẫn dirty và vẫn nằm trong hàng đợi nên mỗi
     * lượt Synchronize lại POST tiếp — chính thứ mà backoff sinh ra để tránh.
     */
    @androidx.annotation.VisibleForTesting
    static boolean shouldDeferVerificationSync(String uniqueId, long now) {
        if (uniqueId == null || uniqueId.isEmpty()) return false;

        long[] state = verificationState.get(uniqueId);   // {attempts, nextAttemptAt, clientSeq}
        if (state == null) return false;
        if (state[0] >= MAX_VERIFICATION_ATTEMPTS) return false;   // đã hết lượt, xử lý ở nhánh ERROR
        return now < state[1];
    }

    /**
     * Test dùng để mô phỏng "đã hết thời gian chờ": xoá mốc backoff nhưng GIỮ số lần đã thử,
     * nhờ đó kiểm chứng được giới hạn số lần mà không phải chờ thật.
     */
    @androidx.annotation.VisibleForTesting
    static void expireVerificationBackoff(String uniqueId) {
        long[] state = verificationState.get(uniqueId);
        if (state != null) state[1] = 0;
    }

    private static void noteVerificationAttempt(String uniqueId, long clientSeq) {
        if (uniqueId == null || uniqueId.isEmpty()) return;
        verificationState.compute(uniqueId, (key, state) -> {
            // State gắn với đúng phiên bản đang chờ xác thực: người dùng sửa tiếp thì
            // state cũ không còn áp cho phiên bản mới.
            boolean sameVersion = state != null && state[2] == clientSeq;
            long attempts = sameVersion ? state[0] + 1 : 1;
            int idx = (int) Math.min(attempts - 1, VERIFICATION_BACKOFF_MS.length - 1);
            return new long[]{attempts,
                    System.currentTimeMillis() + VERIFICATION_BACKOFF_MS[idx], clientSeq};
        });
    }

    /**
     * Đối chiếu trạng thái server bằng một GET, dùng cho nhánh
     * {@link RefuelSyncGuard#LEGACY_PROJECTION_UNKNOWN} — nơi projection của POST response
     * trả RealAmount = 0 nên không thể kết luận.
     *
     * <p><b>Chỉ ĐỌC để đối chiếu.</b> Kết quả GET không bao giờ được ghi vào Room: chính
     * đường "lấy object server đè lên bản local" là nguyên nhân của sự cố đang xử lý.
     *
     * <p>Phải gọi NGOÀI {@code REFUEL_WRITE_LOCK} — không giữ khoá ghi trong lúc chờ HTTP.
     */
    private static void verifyInconclusivePost(String uniqueId, RefuelItemData request,
                                               RefuelItemData postResponse, long clientSeqAtPost) {
        if (!shouldAttemptVerification(uniqueId)) return;
        noteVerificationAttempt(uniqueId, clientSeqAtPost);

        RefuelItemData verification = requireHttpClient().getRefuelItem(uniqueId);

        String outcome;
        if (verification == null) {
            outcome = "GET_FAILED";
        } else if (postResponse != null
                && verification.getServerRevision() < postResponse.getServerRevision()) {
            // Revision thấp hơn chính response vừa nhận ⇒ có thể là replica/cache cũ.
            // Dù giá trị có tình cờ khớp cũng chưa đủ để xác nhận.
            outcome = "STALE_READ rev=" + verification.getServerRevision()
                    + " postRev=" + postResponse.getServerRevision();
        } else {
            String diff = RefuelSyncGuard.describeFinalValueDiff(request, verification,
                    request.getStatus());
            outcome = diff == null ? "CONFIRMED" : "MISMATCH " + diff;
        }

        Logger.appendLog("POST_EXCHANGE", String.format(java.util.Locale.US,
                "VERIFY_GET uid=%s result=%s thread=%s", uniqueId, outcome,
                Thread.currentThread().getName()));

        if (!"CONFIRMED".equals(outcome)) {
            markExhaustedVerification(uniqueId);
            return;
        }

        // Trạng thái trên server hiện đã đúng mục tiêu — không cần biết do request nào ghi.
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem row = requireRepository().getRefuel(uniqueId);
            if (row == null) return;

            // Row bị sửa tiếp trong lúc GET đang bay (kể cả chỉ là metadata như LeaveTime,
            // vốn không đổi giá trị chốt): thay đổi đó chưa được gửi nên không được xoá dirty.
            if (row.getClientSeq() != clientSeqAtPost) {
                Logger.appendLog("POST_EXCHANGE", String.format(java.util.Locale.US,
                        "VERIFY_GET uid=%s result=LOCAL_MOVED_ON seqAtPost=%d seqNow=%d",
                        uniqueId, clientSeqAtPost, row.getClientSeq()));
                return;
            }

            if (RefuelSyncGuard.describeFinalValueDiff(request, row.toRefuelItemData(),
                    request.getStatus()) != null) {
                Logger.appendLog("POST_EXCHANGE", "VERIFY_GET uid=" + uniqueId
                        + " result=LOCAL_MOVED_ON, giữ nguyên hàng đợi");
                return;
            }

            // Nhận metadata từ bản có revision cao hơn: GET có thể đã thấy phiên bản mới
            // hơn chính response của POST.
            RefuelItemData ackSource = postResponse == null
                    || verification.getServerRevision() > postResponse.getServerRevision()
                    ? verification : postResponse;

            RefuelSyncGuard.applyServerAck(row, ackSource);
            row.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
            requireRepository().insertRefuel(row);
            clearConflictStreak(uniqueId);
            verificationState.remove(uniqueId);
        }
    }

    /**
     * Hết lượt xác thực mà vẫn không kết luận được: chuyển row sang conflict để nó rời
     * hàng đợi tự động, tránh POST + GET lặp lại vô hạn. Dữ liệu local giữ nguyên.
     */
    private static void markExhaustedVerification(String uniqueId) {
        long[] state = verificationState.get(uniqueId);
        if (state == null || state[0] < MAX_VERIFICATION_ATTEMPTS) return;

        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem row = requireRepository().getRefuel(uniqueId);
            if (row == null || row.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR) return;

            // Người dùng đã sửa tiếp sau khi POST: phiên bản mới chưa hề thất bại lần nào,
            // không được đánh ERROR cho nó.
            if (row.getClientSeq() != state[2]) {
                verificationState.remove(uniqueId);
                Logger.appendLog("SYNC", "Bỏ verification state cũ, row đã có phiên bản mới uid="
                        + uniqueId);
                return;
            }

            row.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);
            row.setLocalModified(true);
            requireRepository().insertRefuel(row);

            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=VERIFICATION_EXHAUSTED uid=%s attempts=%d localId=%d",
                    uniqueId, state[0], row.getLocalId()));
        }
    }

    /**
     * Đưa một row đang giữ conflict trở lại hàng đợi đồng bộ tự động.
     *
     * <p>Gọi khi người dùng lưu lại thành công trên nền phiên bản mới nhất — đó là tín
     * hiệu conflict đã được giải quyết. Trạng thái ERROR nằm trong DB nên sau khi khởi
     * động lại app, row vẫn không tự retry cho tới khi có thao tác này.
     */
    private static void resumeSync(RefuelItem localItem) {
        if (localItem == null) return;
        if (localItem.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR) {
            localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.NONE);
            Logger.appendLog("DTH", "resumeSync uid=" + localItem.getUniqueId()
                    + " localId=" + localItem.getLocalId());
        }
        clearConflictStreak(localItem.getUniqueId());
    }

    /**
     * POST đã đi tới server nhưng không xác nhận được là dữ liệu có được ghi hay không.
     *
     * <p>Nguyên tắc: KHÔNG mất dữ liệu local. Row giữ nguyên payload, giữ
     * {@code localModified = true} để còn cơ hội gửi lại; sau {@link #MAX_UNCONFIRMED_POSTS}
     * lần liên tiếp thì đánh dấu ERROR để không quay vòng vô hạn với server.
     */
    private static void noteUnconfirmedPost(RefuelItem localItem, RefuelItemData request,
                                            RefuelItemData response) {
        if (localItem == null) return;

        String uid = localItem.getUniqueId();
        String reason = RefuelSyncGuard.describeAck(request, response);

        // Giới hạn projection của server (phiếu cũ trả RealAmount = 0): không kết luận được
        // là đã ghi hay chưa. Giữ row ở hàng đợi để lượt sau thử lại, nhưng KHÔNG tính vào
        // streak — nếu tính, toàn bộ phiếu cũ sẽ bị đẩy vào ERROR hàng loạt.
        boolean inconclusive = RefuelSyncGuard.isInconclusive(reason);
        int streak = inconclusive || uid == null
                ? 0
                : conflictStreak.merge(uid, 1, Integer::sum);

        localItem.setLocalModified(true);
        if (streak >= MAX_UNCONFIRMED_POSTS)
            localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);

        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=CONFLICT_PENDING reason=%s streak=%d id=%d localId=%d uid=%s "
                        + "reqStatus=%s reqSeq=%d reqBaseRev=%d reqAmount=%.0f reqEnd=%.0f "
                        + "resStatus=%s resSeq=%d resRev=%d resAmount=%.0f resEnd=%.0f thread=%s",
                reason,
                streak, localItem.getId(), localItem.getLocalId(), uid,
                request == null ? "null" : String.valueOf(request.getStatus()),
                request == null ? -1 : request.getClientSeq(),
                request == null ? -1 : request.getBaseServerRevision(),
                request == null ? 0 : request.getRealAmount(),
                request == null ? 0 : request.getEndNumber(),
                response == null ? "null" : String.valueOf(response.getStatus()),
                response == null ? -1 : response.getClientSeq(),
                response == null ? -1 : response.getServerRevision(),
                response == null ? 0 : response.getRealAmount(),
                response == null ? 0 : response.getEndNumber(),
                Thread.currentThread().getName()));
    }

    /**
     * Ghi lại cặp request/response của MỌI POST, gọi ngay sau khi nhận HTTP response và
     * trước mọi nhánh xử lý — đây là dấu vết duy nhất để truy nguyên các ca ghi đè số liệu.
     */
    private static void logPostExchange(String source, RefuelItemData request, RefuelItemData response) {
        if (request == null) return;

        String verdict = response == null
                ? "NO_RESPONSE"
                : RefuelSyncGuard.describeAck(request, response);

        Logger.appendLog("POST_EXCHANGE", String.format(java.util.Locale.US,
                "%s uid=%s id=%d localId=%d | req status=%s seq=%d baseSeq=%d baseRev=%d amount=%.0f start=%.0f end=%.0f "
                        + "| res status=%s seq=%d rev=%d amount=%.0f start=%.0f end=%.0f "
                        + "| result=%s thread=%s",
                source, request.getUniqueId(), request.getId(), request.getLocalId(),
                request.getStatus(), request.getClientSeq(),
                request.getBaseClientSeq(), request.getBaseServerRevision(),
                request.getRealAmount(), request.getStartNumber(), request.getEndNumber(),
                response == null ? "-" : String.valueOf(response.getStatus()),
                response == null ? -1 : response.getClientSeq(),
                response == null ? -1 : response.getServerRevision(),
                response == null ? 0 : response.getRealAmount(),
                response == null ? 0 : response.getStartNumber(),
                response == null ? 0 : response.getEndNumber(),
                verdict == null ? "ACK" : "CONFLICT:" + verdict,
                Thread.currentThread().getName()));
    }

    private static void logVersionConflict(String source, RefuelItem stored, RefuelItemData incoming,
                                           RefuelSyncGuard.SaveDecision decision) {
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=VERSION_CONFLICT reason=%s source=%s id=%d localId=%d uid=%s "
                        + "storedSeq=%d storedRev=%d baseSeq=%d baseRev=%d "
                        + "storedAmount=%.0f storedEnd=%.0f incomingAmount=%.0f incomingEnd=%.0f thread=%s",
                decision, source, stored.getId(), stored.getLocalId(), stored.getUniqueId(),
                stored.getClientSeq(), stored.getServerRevision(),
                incoming.getBaseClientSeq(), incoming.getBaseServerRevision(),
                stored.toRefuelItemData().getRealAmount(), stored.getEndNumber(),
                incoming.getRealAmount(), incoming.getEndNumber(),
                Thread.currentThread().getName()));
    }

    private static boolean finalRefuelValuesChanged(RefuelItemData current, RefuelItemData incoming) {
        if (current == null || incoming == null) return false;
        return current.getStatus() != incoming.getStatus()
                || Double.compare(current.getRealAmount(), incoming.getRealAmount()) != 0
                || Double.compare(current.getStartNumber(), incoming.getStartNumber()) != 0
                || Double.compare(current.getEndNumber(), incoming.getEndNumber()) != 0
                || !java.util.Objects.equals(current.getEndTime(), incoming.getEndTime());
    }

    /**
     * Propagate the stored row identity back onto the caller's object.
     *
     * <p>{@link #postRefuels(List, boolean)} and RefuelPreviewActivity discard the
     * return value, so a caller holding a freshly created item (id=0, localId=0 —
     * every split item) would keep posting 0/0 and insert a new row on every save.
     * Only identity/version fields are copied: the caller may legitimately hold
     * edits that are newer than the stored row.
     */
    private static RefuelItemData finishLocalSave(RefuelItemData caller, RefuelItem stored) {
        if (stored == null) return null;
        if (caller != null) {
            if (stored.getId() > 0)
                caller.setId(stored.getId());
            caller.setLocalId(stored.getLocalId());
            if (stored.getUniqueId() != null && !stored.getUniqueId().isEmpty())
                caller.setUniqueId(stored.getUniqueId());
            caller.setLocalModified(stored.isLocalModified());
            caller.setClientSeq(stored.getClientSeq());
            caller.setServerRevision(stored.getServerRevision());

            // Snapshot của màn hình vừa gọi giờ đứng trên phiên bản mới nhất mà chính nó
            // vừa tạo ra. Không cập nhật base ở đây thì lần lưu kế tiếp từ cùng màn hình
            // sẽ bị precondition hiểu nhầm là snapshot cũ.
            caller.setBaseClientSeq(stored.getClientSeq());
            caller.setBaseServerRevision(stored.getServerRevision());
            caller.setBaseBusinessFingerprint(
                    RefuelSyncGuard.businessFingerprintOfJson(stored.getJsonData()));
        }
        return stored.toRefuelItemData();
    }

    /**
     * Kết thúc một lần lưu KHÔNG thành công (conflict phiên bản, snapshot bị chặn,
     * response không xác nhận được).
     *
     * <p>Tuyệt đối KHÔNG nâng base version của caller: nếu nâng, chính snapshot cũ vừa
     * bị từ chối sẽ vượt qua precondition ở lần lưu kế tiếp và ghi đè row.
     * Trả về bản ghi mới nhất để màn hình gọi có thể nạp lại và hiển thị dữ liệu đúng.
     */
    private static RefuelItemData finishConflict(RefuelItem stored) {
        return stored == null ? null : stored.toRefuelItemData();
    }

    /**
     * Audit an attempt to change an already finalized refuel.
     *
     * @param kept     values that survive in local storage after this operation
     * @param rejected values that are discarded — the incoming payload for every
     *                 BLOCK or PRESERVE action, the previous local values for
     *                 ALLOW_DONE_EDIT
     */
    private static void logFinalRefuelChange(String source, RefuelItemData kept,
                                             RefuelItemData rejected, String action) {
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=FINAL_REFUEL_CHANGE source=%s action=%s thread=%s "
                        + "id=%d localId=%d uid=%s flight=%s truck=%s "
                        + "keptStatus=%s keptAmount=%.0f keptStart=%.0f keptEnd=%.0f keptEndTime=%s "
                        + "rejectedStatus=%s rejectedAmount=%.0f rejectedStart=%.0f rejectedEnd=%.0f rejectedEndTime=%s "
                        + "keptClientSeq=%d keptServerRevision=%d rejectedClientSeq=%d rejectedServerRevision=%d",
                source, action, Thread.currentThread().getName(), kept.getId(), kept.getLocalId(),
                kept.getUniqueId(), kept.getFlightCode(), kept.getTruckNo(),
                kept.getStatus(), kept.getRealAmount(), kept.getStartNumber(),
                kept.getEndNumber(), String.valueOf(kept.getEndTime()),
                rejected.getStatus(), rejected.getRealAmount(), rejected.getStartNumber(),
                rejected.getEndNumber(), String.valueOf(rejected.getEndTime()),
                kept.getClientSeq(), kept.getServerRevision(),
                rejected.getClientSeq(), rejected.getServerRevision()));
    }

    public static RefuelItemData postRefuel(RefuelItemData refuelData) {
        RefuelItemData postedItem = postRefuel(refuelData, false);
        // call synchronize to update remote database
        if (postedItem != null && postedItem.getStatus() == REFUEL_ITEM_STATUS.DONE)
            Synchronize();
        return postedItem;
    }

    public static RefuelItemData postRefuel(RefuelItemData refuelData, boolean remotePost) {
        if (refuelData != null) {
            Logger.appendLog("DTH", "postRefuel " + refuelData.getId() + " - " + refuelData.getLocalId());
            Logger.appendLog("DTH", String.format("FlightCode: %s Amount : %.0f Start Number: %.0f End Number: %.0f", refuelData.getFlightCode(), refuelData.getRealAmount(), refuelData.getStartNumber(), refuelData.getEndNumber()));

            RefuelItem localItem;
            String jsonBeforeRequest;
            // Thông tin cho GET xác thực, chỉ chạy sau khi đã rời khoá ghi.
            String pendingVerificationUid = null;
            RefuelItemData pendingVerificationResponse = null;
            long pendingVerificationSeq = 0;
            long requestSeq = 0;
            RefuelItemData pendingResult = null;
            boolean userChange = true;
            boolean newerLocalPending = false;
            String postKey = null;
            boolean hasPostSlot = false;

            synchronized (REFUEL_WRITE_LOCK) {
                localItem = requireRepository().getRefuel(refuelData.getId(), refuelData.getLocalId());
                jsonBeforeRequest = localItem == null ? null : localItem.getJsonData();
                if (localItem == null) {
                    /// if item not exists in local database
                    // Row mới: bắt đầu từ seq 1 để server không còn thấy "seq=0 <= db=0".
                    refuelData.setClientSeq(Math.max(refuelData.getClientSeq(), 0) + 1);
                    localItem = RefuelItem.fromRefuelItemData(refuelData);
                } else {
                    if (localItem.getId() > 0 && refuelData.getId() == 0)
                        refuelData.setId((localItem.getId()));

                    RefuelItemData currentData = localItem.toRefuelItemData();
                    if (currentData.getStatus() == REFUEL_ITEM_STATUS.DONE
                            && finalRefuelValuesChanged(currentData, refuelData)) {
                        // An explicit DONE edit wins, so it is the value that is kept.
                        boolean incomingWins = refuelData.getStatus() == REFUEL_ITEM_STATUS.DONE;
                        logFinalRefuelChange(remotePost ? "DIRECT_POST" : "LOCAL_SAVE",
                                incomingWins ? refuelData : currentData,
                                incomingWins ? currentData : refuelData,
                                incomingWins ? "ALLOW_DONE_EDIT" : "BLOCK_DOWNGRADE");
                    }

                    // A Preview/Confirm screen may still hold an old PROCESSING
                    // object after the same refuel has been finalized. Do not write
                    // that stale snapshot back to SQLite or schedule it for retry.
                    if (localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                            && refuelData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                        Logger.appendLog("DTH", "Ignore stale non-DONE snapshot for DONE refuel "
                                + localItem.getId());
                        return finishConflict(localItem);
                    }

                    // Precondition: snapshot phải đứng trên đúng phiên bản VÀ đúng payload
                    // nền của row. Xem RefuelSyncGuard.decideSave để biết vì sao chỉ so
                    // version là không đủ theo cả hai chiều.
                    RefuelSyncGuard.SaveDecision decision = RefuelSyncGuard.decideSave(
                            localItem.getClientSeq(), localItem.getServerRevision(),
                            RefuelSyncGuard.businessFingerprintOfJson(localItem.getJsonData()),
                            refuelData.getBaseClientSeq(), refuelData.getBaseServerRevision(),
                            refuelData.getBaseBusinessFingerprint());

                    if (!decision.isAllowed()) {
                        logVersionConflict(remotePost ? "DIRECT_POST" : "LOCAL_SAVE",
                                localItem, refuelData, decision);
                        return finishConflict(localItem);
                    }

                    if (decision == RefuelSyncGuard.SaveDecision.ALLOW_REBASE_SERVER_METADATA) {
                        // Server chỉ cấp thêm metadata trong lúc màn hình đang mở: nhận
                        // metadata đó rồi cho lưu tiếp, KHÔNG đụng payload người dùng vừa sửa.
                        int oldBaseRevision = refuelData.getBaseServerRevision();

                        refuelData.setId(localItem.getId());
                        if (localItem.getUniqueId() != null && !localItem.getUniqueId().isEmpty())
                            refuelData.setUniqueId(localItem.getUniqueId());
                        refuelData.setServerRevision(localItem.getServerRevision());
                        if (localItem.getDateUpdated() != null)
                            refuelData.setDateUpdated(localItem.getDateUpdated());
                        refuelData.setBaseServerRevision(localItem.getServerRevision());
                        refuelData.setBaseBusinessFingerprint(
                                RefuelSyncGuard.businessFingerprintOfJson(localItem.getJsonData()));

                        Logger.appendLog("DTH", String.format(java.util.Locale.US,
                                "REBASED_ON_SERVER_METADATA uid=%s oldBaseRev=%d storedRev=%d seq=%d",
                                localItem.getUniqueId(), oldBaseRevision,
                                localItem.getServerRevision(), localItem.getClientSeq()));
                    }

                    // Chỉ thay đổi nghiệp vụ thật mới được cấp seq mới. Gửi lại nguyên trạng
                    // phải giữ nguyên sequence, nếu không version bị thổi phồng và chính cơ
                    // chế chống stale mất tác dụng.
                    userChange = RefuelSyncGuard.hasBusinessPayloadChanged(currentData, refuelData);

                    if (userChange) {
                        refuelData.setClientSeq(localItem.getClientSeq() + 1);
                        localItem.updateData(refuelData);
                    } else {
                        refuelData.setClientSeq(localItem.getClientSeq());
                    }
                }

                if (userChange) {
                    // Ghi local thành công ⇒ row quay lại hàng đợi đồng bộ tự động, và
                    // backoff xác thực của phiên bản cũ không còn ý nghĩa.
                    resumeSync(localItem);
                    verificationState.remove(localItem.getUniqueId());
                }

                // OFFLINE-FIRST: payload của người dùng được ghi xuống Room TRƯỚC khi gọi
                // HTTP, cho cả remotePost. Trước đây nhánh remotePost chỉ giữ thay đổi trong
                // bộ nhớ rồi sau HTTP lại re-read row từ DB và gán đè localItem — payload vừa
                // gửi bị mất nếu response không được adopt, và mất luôn nếu app bị kill
                // trong lúc request đang bay.
                if (userChange) {
                    localItem.setLocalModified(true);
                    requireRepository().insertRefuel(localItem);
                }
                requestSeq = localItem.getClientSeq();

                if (!remotePost) {
                    return finishLocalSave(refuelData, localItem);
                }

                // NO-OP: không có gì mới để gửi. Không được dùng một lần gọi lặp để vượt
                // backoff, cũng không được đánh thức một row đang giữ conflict (ERROR) —
                // chỉ một thay đổi thật của người dùng mới đưa row đó trở lại hàng đợi.
                if (!userChange) {
                    if (localItem.getPostStatus() == RefuelItem.ITEM_POST_STATUS.ERROR) {
                        Logger.appendLog("DTH", "Bỏ qua NO-OP trên row đang conflict uid="
                                + localItem.getUniqueId());
                        return finishLocalSave(refuelData, localItem);
                    }
                    if (shouldDeferVerificationSync(localItem.getUniqueId(), System.currentTimeMillis())) {
                        Logger.appendLog("DTH", "Defer direct POST (verification backoff) uid="
                                + localItem.getUniqueId());
                        return finishLocalSave(refuelData, localItem);
                    }
                }

                // Giành quyền gửi ngay khi còn giữ khoá, để không có hai request cùng phiếu.
                postKey = refuelPostKey(localItem);
                hasPostSlot = tryBeginRefuelPost(postKey);
                if (!hasPostSlot) {
                    // Phiếu đang có request bay: đã lưu local, để lượt sync sau gửi tiếp.
                    Logger.appendLog("DTH", "Defer direct POST (đang có request bay) uid="
                            + localItem.getUniqueId());
                    return finishLocalSave(refuelData, localItem);
                }
            }

            try {
                RefuelItemData postedItem = requireHttpClient().postRefuel(refuelData);
                // Log NGAY sau khi nhận response, trước mọi nhánh xử lý: các nhánh bên dưới
                // có return sớm nên nếu log ở trong đó sẽ mất dấu vết đúng những ca cần điều tra.
                logPostExchange("DIRECT_POST", refuelData, postedItem);
                synchronized (REFUEL_WRITE_LOCK) {
                    RefuelItem newestLocal = requireRepository().getRefuel(refuelData.getId(), refuelData.getLocalId());
                    if (newestLocal != null) {
                        localItem = newestLocal;
                    }

                    // Row đã bị ghi tiếp trong lúc request đang bay ⇒ bản trong Room mới hơn
                    // payload vừa gửi. Giữ nguyên row, chỉ nhận metadata server xác nhận và
                    // để nó tiếp tục nằm trong hàng đợi.
                    if (localItem.getClientSeq() != requestSeq) {
                        if (postedItem != null) {
                            RefuelItemData keptBeforeMerge = localItem.toRefuelItemData();
                            RefuelSyncGuard.mergeServerMetadata(localItem, postedItem);
                            if (keptBeforeMerge.getStatus() == REFUEL_ITEM_STATUS.DONE
                                    && finalRefuelValuesChanged(keptBeforeMerge, postedItem)) {
                                logFinalRefuelChange("DIRECT_POST", keptBeforeMerge,
                                        postedItem, "PRESERVE_NEWER_LOCAL");
                            }
                            requireRepository().insertRefuel(localItem);
                        }
                        Logger.appendLog("DTH", String.format(java.util.Locale.US,
                                "DIRECT_POST row moved on uid=%s seqAtPost=%d seqNow=%d",
                                localItem.getUniqueId(), requestSeq, localItem.getClientSeq()));
                        // Chỉ đây mới có bằng chứng có version local mới hơn đang chờ gửi.
                        newerLocalPending = localItem.getClientSeq() > requestSeq;
                        return finishConflict(localItem);
                    }

                    if (postedItem != null) {
                        // Preserve a newer local DONE if an older non-DONE response
                        // happens to complete afterwards.
                        if (localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                                && postedItem.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                            logFinalRefuelChange("DIRECT_POST", localItem.toRefuelItemData(),
                                    postedItem, "BLOCK_OLD_RESPONSE");
                            return finishConflict(localItem);
                        }

                        if (RefuelSyncGuard.looksLikeServerAck(refuelData, postedItem)) {
                            RefuelSyncGuard.applyServerAck(localItem, postedItem);
                            localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                            clearConflictStreak(localItem.getUniqueId());
                        } else {
                            // Không chứng minh được server đã ghi payload này. Không adopt
                            // dữ liệu nghiệp vụ của response, giữ nguyên bản local và để row
                            // ở trạng thái chờ đồng bộ.
                            noteUnconfirmedPost(localItem, refuelData, postedItem);
                            requireRepository().insertRefuel(localItem);

                            boolean inconclusive = RefuelSyncGuard.isInconclusive(
                                    RefuelSyncGuard.describeAck(refuelData, postedItem));
                            String uid = localItem.getUniqueId();
                            RefuelItemData conflictResult = finishConflict(localItem);

                            if (inconclusive) {
                                // Thoát khoá ghi trước khi gọi HTTP.
                                pendingVerificationUid = uid;
                                pendingVerificationResponse = postedItem;
                                // Mốc so sánh là phiên bản của row VỪA ĐƯỢC GHI, không phải
                                // của snapshot: mọi thay đổi sau đó đều là thay đổi chưa gửi.
                                pendingVerificationSeq = localItem.getClientSeq();
                                pendingResult = conflictResult;
                            } else {
                                return conflictResult;
                            }
                        }
                    }
                    // postedItem == null: HTTP không khả dụng/bị bỏ qua. Payload đã nằm trong
                    // Room từ trước khi gọi HTTP và row vẫn đang localModified nên nó tự nằm
                    // trong hàng đợi đồng bộ — không cần ghi lại gì thêm.

                    if (pendingResult == null) {
                        requireRepository().insertRefuel(localItem);
                        return finishLocalSave(refuelData, localItem);
                    }
                }

                // Chỉ còn nhánh "không kết luận được": đối chiếu bằng GET, ngoài khoá ghi.
                verifyInconclusivePost(pendingVerificationUid, refuelData,
                        pendingVerificationResponse, pendingVerificationSeq);
                return pendingResult;

            } finally {
                if (hasPostSlot) endRefuelPost(postKey);

                // KHÔNG tự kích hoạt sync chỉ vì row còn dirty: HTTP lỗi/non-ACK cũng giữ
                // dirty, làm vậy sẽ thành vòng lặp POST-lỗi-POST. Chỉ lên lịch khi có bằng
                // chứng row đã tiến lên trong lúc request bay, tức là có version mới chờ gửi.
                if (newerLocalPending) {
                    Logger.appendLog("DTH", "Schedule follow-up sync (có version local mới hơn)");
                    Synchronize();
                }
            }
        }
        return null;
    }


    public static List<AirlineModel> getAirlines() {
        if (isDebug) {


            List<AirlineModel> localList = requireRepository().getAirlines();

            return localList;
        }
        return requireHttpClient().getAirlines();

    }

    public static List<UserModel> getUsers() {
        if (isDebug) {
            return requireRepository().getUsers();
        }
        return requireHttpClient().getUsers();
    }

    public static List<ProductModel> getProducts() {
        if (isDebug) {


            List<ProductModel> localList = requireRepository().getProductList();

            return localList;
        }
        return requireHttpClient().getProductList();

    }

    public static InvoiceFormModel[] getInvoiceForms() {

        return requireHttpClient().getInvoiceForms();
    }

    public static void postInvoice(InvoiceModel model) {
        Invoice localModel = Invoice.fromModel(model);
        localModel.setLocalModified(true);
        requireRepository().insertInvoice(localModel);
        InvoiceModel postInv = new InvoiceAPI().post(model);
        if (postInv != null) {
            localModel.setId(postInv.getId());
            localModel.setLocalModified(false);
            requireRepository().insertInvoice(localModel);
        }
        Synchronize();
    }

    public static RefuelItemData getImcomplete() {
        RefuelItemData item = requireRepository().getIncomplete(FMSApplication.getApplication().getTruckNo());
        return item;
    }

    public static List<TruckFuelModel> getTruckFuels() {
        return getTruckFuels(new Date());
    }

    public static List<TruckFuelModel> getTruckFuels(Date date) {
        return requireRepository().getTruckFuels(date);
    }

    public static void postTruckFuel(TruckFuelModel model) {
        TruckFuel localModel = TruckFuel.fromTruckFuelModel(model);
        localModel.setLocalModified(true);
        requireRepository().insertTruckFuel(localModel);
        // call synchronize to update remote database
        Synchronize();

    }

    public static void deleteTruckFuels(int[] ids) {
        requireRepository().deleteTruckFuels(ids);
        // call synchronize to update remote database
        Synchronize();

    }

    public static List<BM2505Model> getBM2505List(Date date) {
        return requireRepository().getBM2505List(date);
    }

    public static List<BM2508Model> getBM2508List(Date date) {
        return requireRepository().getBM2508List(date);
    }

    public static List<CheckTrucksModel> getCheckTrucksList(Date date) {
        return requireRepository().getCheckTrucksList(date);
    }

    /**
     * Ghi phiếu BM2505 xuống local rồi kích hoạt đồng bộ.
     *
     * @return true nếu ghi local thành công. Giá trị true KHÔNG có nghĩa là đã đồng bộ được
     * lên server: bản ghi được đánh dấu isLocalModified và chờ lượt đồng bộ kế tiếp.
     */
    public static boolean postBM2505(BM2505Model model) {

        try {
            BM2505 localModel = BM2505.fromModel(model);
            if (localModel == null) return false;

            localModel.setLocalModified(true);
            int localId = requireRepository().insertBM2505(localModel);
            if (localId <= 0) return false;

            // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
            model.setLocalId(localId);
        } catch (Exception ex) {
            Log.e("postBM2505", ex.getMessage() == null ? "insert failed" : ex.getMessage());
            return false;
        }

        // call synchronize to update remote database
        Synchronize();
        return true;
    }

    // ===== BM2503 =====
    public static List<BM2503Model> getBM2503List(Date date) {
        return requireRepository().getBM2503List(date);
    }
    public static void postBM2503(BM2503Model model) {

        BM2503 localModel = BM2503.fromModel(model);
        localModel.setLocalModified(true);

        requireRepository().insertBM2503(localModel);

        // gọi sync giống các nghiệp vụ khác
        Synchronize();
    }
    public static void deleteBM2503(int[] ids) {
        requireRepository().deleteBM2503(ids);
        Synchronize();
    }
    // ===== BM2504 =====

    public static List<BM2504Model> getBM2504List(Date date) {
        return requireRepository().getBM2504List(date);
    }

    public static void postBM2504(BM2504Model model) {

        BM2504 localModel = BM2504.fromModel(model);
        localModel.setLocalModified(true);

        requireRepository().insertBM2504(localModel);

        // gọi sync giống các nghiệp vụ khác
        Synchronize();
    }

    public static void deleteBM2504(int[] ids) {
        requireRepository().deleteBM2504(ids);
        Synchronize();
    }




    public static void postBM2508(BM2508Model model) {

        BM2508 localModel = BM2508.fromModel(model);
        localModel.setLocalModified(true);
        localModel.setAttachmentPending(hasCompleteBM2508Attachments(model));
        requireRepository().insertBM2508(localModel);
        // call synchronize to update remote database
        Synchronize();
    }

    public static boolean hasCompleteBM2508Attachments(BM2508Model model) {
        if (model == null) return false;
        String airlinePath = model.getAirlineSignaturePath();
        String skypecPath = model.getUserSkypecSignaturePath();
        if (airlinePath == null || airlinePath.trim().isEmpty()
                || skypecPath == null || skypecPath.trim().isEmpty()) return false;
        return new File(airlinePath).isFile() && new File(skypecPath).isFile();
    }

    public static void postCheckTrucks(CheckTrucksModel model) {

        CheckTrucks localModel = CheckTrucks.fromModel(model);
        localModel.setLocalModified(true);
        requireRepository().insertCheckTrucks(localModel);
        // call synchronize to update remote database
        Synchronize();
    }

    public static List<FlightModel> getFlights() {
        return getFlights(new Date());
    }


    public static List<FlightModel> getFlights(Date date) {
        return requireRepository().getFlights(date);
    }

    public static List<AirportsModel> getAirports() {
        if (isDebug) {
            return requireRepository().getAirports();
        }
        return requireHttpClient().getAirports();

    }
    public static List<ShiftModel> getShifts() {
        if (isDebug) {
            List<ShiftModel> lstModel = requireHttpClient().getShifts();

            if (lstModel != null) {
                int[] ids = new int[lstModel.size()];
                int i = 0;
                for (ShiftModel model : lstModel) {
                    requireRepository().insertShift(Shift.fromShiftModel(model));
                    ids[i++] = model.getId();
                }
                requireRepository().deleteOudateShift(ids);
            }
            List<ShiftModel> localList = requireRepository().getShifts();
            return localList;
        }
        return requireHttpClient().getShifts();

    }
    public static void deleteBM2505(int[] ids) {
        requireRepository().deleteBM2505(ids);
        Synchronize();
    }

    public static void deleteBM2508(int[] ids) {
        requireRepository().deleteBM2508(ids);
        Synchronize();
    }

    public static void deleteCheckTrucks(int[] ids) {
        requireRepository().deleteCheckTrucks(ids);
        Synchronize();
    }

    public static void postReceipt(ReceiptModel model) {
        Logger.appendLog("DTH", "postReceipt num=" + model.getNumber()
                + " uniqueId=" + model.getUniqueId()
                + " thread=" + Thread.currentThread().getName());

        Receipt localModel = Receipt.fromModel(model);
        localModel.setLocalModified(true);
        long rowId = requireRepository().insertReceipt(localModel);
        Logger.appendLog("DTH", "postReceipt INSERT rowId=" + rowId + " num=" + model.getNumber());
        if (rowId > 0) {
            Synchronize();
        }
    }

    public static void cancelReceipts(String[] printedItems, String reason) {
        requireRepository().cancelReceipts(printedItems, reason);
        Synchronize();
    }

    public static List<ReceiptModel> getReceiptList(Date date) {
        List<Receipt> modified = requireRepository().getReceiptList(date);
        List<ReceiptModel> lst = new ArrayList<>();
        for (Receipt local : modified) {
            lst.add(local.toModel());
        }
        return lst;
    }

    public static void postLog(LogEntryModel.LOG_TYPE tag, String logText, String activity) {
        new Thread(()-> {
            requireRepository().postLog(new LogEntryModel(tag, logText, activity));
        }).start();
    }

    public static List<LogEntryModel> getLogList(int limit) {
        List<LogEntry> modified = requireRepository().getLogList(limit);
        List<LogEntryModel> lst = new ArrayList<>();
        for (LogEntry local : modified) {
            lst.add(local.toModel());
        }
        return lst;
    }

    public static void deleteLogs(int[] ids) {
        new Thread(() -> {
            requireRepository().deleteLogs(ids);
        }).start();
    }

    public static ReviewModel getReview(int id) {
        return null;
    }

    public static void postReview(ReviewModel model) {
        requireRepository().postReview(Review.fromModel(model));
    }

    public static ReviewModel getReviewByFlight(int flightId) {
        return requireRepository().getReviewByFlight(flightId);
    }
    public static ReviewModel getReviewByFlight(String flightId) {
        return requireRepository().getReviewByFlight(flightId);
    }
    public static boolean checkReview(int flightId, String flightUniqueId) {
        return requireRepository().checkReview(flightId,flightUniqueId);
    }

    public static ReceiptModel getReceipt(String uniqueId) {
        Receipt receipt =  requireRepository().getReceipt(uniqueId);
        if (receipt!=null)
            return receipt.toModel();
        else return null;
    }

    public static List<BM2505ContainerModel> getBM2505ContainerList() {

        return requireRepository().getBM2505ContainerList();
    }

    public static Double getLatestDensityFromLocal() {
        return requireRepository().getLatestDensityFromLocal();
    }





}
