package com.megatech.fms.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;


import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.UserBaseActivity;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.BM7501Repository;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501PostResult;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.Airline;
import com.megatech.fms.data.entity.Airports;
import com.megatech.fms.data.entity.BM2503;
import com.megatech.fms.data.entity.BM2504;
import com.megatech.fms.data.entity.BM2505;
import com.megatech.fms.data.entity.BM2505Container;
import com.megatech.fms.data.entity.BM2506;
import com.megatech.fms.data.entity.BM2508;
import com.megatech.fms.data.entity.BM2509;
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
import com.megatech.fms.model.BM2506Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.BM2509Model;
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

import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static long lastSyncLockSkipLog = 0L;

    /** Nén log bỏ qua vì khoá: mỗi 30 giây một dòng là đủ để biết khoá đang treo. */
    private static synchronized boolean shouldLogSyncLockSkip() {
        long now = System.currentTimeMillis();
        if (now - lastSyncLockSkipLog < 30_000L) return false;
        lastSyncLockSkipLog = now;
        return true;
    }
    private static final AtomicBoolean syncPending = new AtomicBoolean(false);
    private static final AtomicInteger activeSyncTasks = new AtomicInteger(0);
    private static final AtomicInteger refuelReadGeneration = new AtomicInteger(0);
    /** Token mới nhất đã BẮT ĐẦU cho từng UID; dùng để bỏ callback UI cũ. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer>
            latestRefuelReads = new java.util.concurrent.ConcurrentHashMap<>();
    /** Token mới nhất đã COMMIT xuống Room; pull mới phải chặn callback GET cũ đang bay. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer>
            latestAppliedRefuelReads = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String REFUEL_PULL_CURSOR_KEY_PREFIX =
            "REFUEL_MODIFIED_CURSOR_MS_V1_";
    /** Query chồng lại 5 phút để không mất các row cùng timestamp/đến trễ ở biên cursor. */
    private static final long REFUEL_PULL_CURSOR_OVERLAP_MS = 5 * 60 * 1000L;
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
        unconfirmedBackoff.clear();
        PostConflictPolicy.shared().clearAll();
        lastConflictSignature.clear();
        inFlightRefuelKeys.clear();
        latestRefuelReads.clear();
        latestAppliedRefuelReads.clear();
        refuelReadGeneration.set(0);
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

    /** Danh sách xe đã lưu trong máy (chu kỳ đồng bộ làm mới). Không gọi mạng. */
    public static List<TruckModel> getLocalTrucks() {
        return requireRepository().getTrucks();
    }

    public static List<TruckModel> getFHSTrucks() {

        return requireRepository().getFHSTrucks();


    }

    /**
     * Phiếu của xe hiện tại đọc thẳng từ Room. Khác {@link #getRefuelList}, hàm này không
     * bao giờ gọi HTTP nên dùng được cho kiểm tra phải chạy đúng cả khi mất sóng.
     * Chạm DB, phải gọi ở thread nền.
     */
    public static List<RefuelItemData> getLocalRefuelList() {
        return requireRepository().getLocalRefuelList(
                FMSApplication.getApplication().getTruckNo());
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

    /**
     * Ghi và đọc lại đúng tập {@code Others} trong response server.
     *
     * <p>Trả {@code null} khi endpoint không mang một snapshot Others đã được parser xác nhận;
     * caller khi đó mới được fallback về cache Room. Danh sách rỗng hợp lệ được trả thành
     * danh sách rỗng, không bị đánh đồng với "server không gửi Others".
     */
    private static List<RefuelItemData> persistCompleteServerOthers(
            String source, RefuelItemData serverCurrent, int readGeneration,
            Set<String> committedUids) {
        if (serverCurrent == null || !serverCurrent.hasCompleteOthersSnapshot()) return null;

        if (serverCurrent.getUniqueId() == null
                || serverCurrent.getUniqueId().trim().isEmpty()) {
            throw otherSnapshotFailure(source, "root thiếu UniqueId");
        }

        List<RefuelItemData> snapshots = serverCurrent.getOthers();
        if (snapshots == null) snapshots = Collections.emptyList();

        Set<String> seenUids = new HashSet<>();
        List<String> snapshotUids = new ArrayList<>();
        snapshotUids.add(serverCurrent.getUniqueId());
        for (RefuelItemData snapshot : snapshots) {
            if (snapshot == null
                    || snapshot.getUniqueId() == null
                    || snapshot.getUniqueId().trim().isEmpty()
                    || !seenUids.add(snapshot.getUniqueId())
                    || snapshot.getUniqueId().equals(serverCurrent.getUniqueId())) {
                throw otherSnapshotFailure(
                        source, "định danh Others rỗng/trùng/khớp root");
            }
            snapshotUids.add(snapshot.getUniqueId());

            String parentFlightUid = serverCurrent.getFlightUniqueId();
            String childFlightUid = snapshot.getFlightUniqueId();
            boolean comparableFlightUid = parentFlightUid != null && !parentFlightUid.isEmpty()
                    && childFlightUid != null && !childFlightUid.isEmpty();
            boolean wrongFlight = comparableFlightUid
                    ? !parentFlightUid.equals(childFlightUid)
                    : serverCurrent.getFlightId() > 0 && snapshot.getFlightId() > 0
                    && serverCurrent.getFlightId() != snapshot.getFlightId();
            if (wrongFlight) {
                throw otherSnapshotFailure(
                        source, "mẻ khác chuyến uid=" + snapshot.getUniqueId());
            }
        }

        // Root và mọi child là một snapshot. Nếu bất kỳ UID nào đã có request/commit mới
        // hơn thì bỏ toàn collection; không được ghép root cũ với một nửa child mới.
        if (!canApplyRefuelRead(readGeneration, snapshotUids)) {
            throw otherSnapshotFailure(
                    source, "snapshot cũ hơn request khác uid="
                            + serverCurrent.getUniqueId());
        }

        String ownTruck = currentTruckNoSafe();
        int ownTruckId = currentTruckIdSafe();
        List<RefuelItemData> foreignSnapshots = new ArrayList<>();
        List<RefuelItemData> currentTruckSnapshots = new ArrayList<>();
        List<String> membershipUids = new ArrayList<>(snapshots.size());

        for (RefuelItemData snapshot : snapshots) {
            membershipUids.add(snapshot.getUniqueId());
            TruckOwnership ownership = classifyTruck(snapshot, ownTruck, ownTruckId);
            if (ownership == TruckOwnership.UNKNOWN) {
                throw otherSnapshotFailure(
                        source, "không xác định được xe sở hữu uid="
                                + snapshot.getUniqueId());
            }
            if (ownership == TruckOwnership.FOREIGN)
                foreignSnapshots.add(snapshot);
            else
                currentTruckSnapshots.add(snapshot);
        }

        try {
            // Cả foreign full-replace lẫn own safe-merge nằm trong cùng transaction. Nếu một
            // child không merge được, không để UI nhìn thấy nửa collection mới/nửa cũ.
            requireRepository().runInTransaction(() -> {
                int replaced = requireRepository()
                        .replaceRemoteRefuelSnapshots(foreignSnapshots);
                if (replaced != foreignSnapshots.size())
                    throw new IllegalStateException("batch xe khác không xử lý đủ");

                for (RefuelItemData snapshot : currentTruckSnapshots) {
                    // Một chuyến có thể chứa nhiều mẻ của cùng xe (tách mẻ/nạp thêm). Chúng
                    // vẫn do máy này sở hữu và phải qua merge bảo vệ local.
                    RefuelItem local = requireRepository().getRefuel(snapshot.getUniqueId());
                    if (local == null && snapshot.getId() > 0) {
                        RefuelItem byId = requireRepository().getRefuel(snapshot.getId(), 0);
                        if (byId != null && byId.getUniqueId() != null
                                && !snapshot.getUniqueId().equals(byId.getUniqueId()))
                            throw new IllegalStateException(
                                    "Id child trỏ UID khác: " + snapshot.getUniqueId());
                        local = byId;
                    }
                    if (local == null) {
                        local = RefuelItem.fromRefuelItemData(snapshot);
                        local.setLocalId(0);
                        if (snapshot.getRawJson() != null)
                            local.setJsonData(snapshot.getRawJson());
                        local.setRemoteReplica(false);
                        requireRepository().insertRefuel(local);
                    } else {
                        CurrentMergeResult merge = applyRemoteToLocal(
                                source + "_OWN", local, snapshot);
                        if (merge == CurrentMergeResult.FAILED)
                            throw new IllegalStateException(
                                    "không merge được uid=" + snapshot.getUniqueId());
                        // Chỉ snapshot server CURRENT mới được gỡ cờ.
                        local.setRemoteReplica(false);
                        requireRepository().insertRefuel(local);
                    }
                }

                // Membership và payload child là MỘT snapshot. Nếu root chưa tồn tại hoặc
                // UPDATE thất bại, rollback toàn bộ child; không bao giờ để collection mới
                // đi cùng danh sách UID cũ sau crash/lỗi DB.
                if (!requireRepository().replaceRemoteOthersMembership(
                        serverCurrent.getUniqueId(), membershipUids)) {
                    throw new IllegalStateException("không lưu được membership Others");
                }
            });
        } catch (RuntimeException ex) {
            if (!(ex instanceof OtherSnapshotException))
                Logger.appendLog("SYNC", source + " OTHER_SNAPSHOT_FAILED: "
                        + ex.getMessage());
            throw ex;
        }

        committedUids.addAll(snapshotUids);

        // Dựng lại từ Room để object trên màn hình mang đúng localId/base version, nhưng chỉ
        // lấy ĐÚNG các UID server vừa trả — không kéo thêm row cũ cùng FlightId vào hoá đơn.
        List<RefuelItemData> stored = new ArrayList<>(snapshots.size());
        for (RefuelItemData snapshot : snapshots) {
            RefuelItem row = requireRepository().getRefuel(snapshot.getUniqueId());
            if (row == null) {
                throw otherSnapshotFailure(
                        source, "không đọc lại được uid=" + snapshot.getUniqueId());
            }
            stored.add(row.toRefuelItemData());
        }
        return stored;
    }

    private static final class OtherSnapshotException extends IllegalStateException {
        OtherSnapshotException(String message) {
            super(message);
        }
    }

    private static OtherSnapshotException otherSnapshotFailure(
            String source, String detail) {
        Logger.appendLog("SYNC", source + " OTHER_SNAPSHOT_FAILED: " + detail);
        return new OtherSnapshotException(detail);
    }

    private enum ReplicaWriteResult {
        APPLIED,
        KEPT_NEWER,
        FAILED
    }

    /**
     * Kết quả merge dữ liệu server vào mẻ xe hiện tại. KEPT_NEWER vẫn có thể đã nhận các
     * field server-owned, nhưng payload đi kèm không đủ tươi để cập nhật Others/Flight.
     */
    private enum CurrentMergeResult {
        APPLIED,
        KEPT_NEWER,
        FAILED
    }

    /** Full replace một mẻ server-replica và nói rõ payload đến có thực sự được áp dụng. */
    private static ReplicaWriteResult replaceRemoteReplica(
            String source, RefuelItemData remote) {
        try {
            DataRepository.RemoteSnapshotResult result = requireRepository()
                    .replaceRemoteRefuelSnapshotsDetailed(Collections.singletonList(remote));
            if (result.applied == 1) return ReplicaWriteResult.APPLIED;
            if (result.keptNewer == 1) return ReplicaWriteResult.KEPT_NEWER;
            return ReplicaWriteResult.FAILED;
        } catch (RuntimeException ex) {
            Logger.appendLog("SYNC", source + " REMOTE_REPLICA_FAILED uid="
                    + (remote == null ? "null" : remote.getUniqueId())
                    + ": " + ex.getMessage());
            return ReplicaWriteResult.FAILED;
        }
    }

    /** Gắn cache Room khi response không có một collection Others có thẩm quyền. */
    private static void attachCachedOthers(RefuelItemData item, String uniqueId) {
        item.setOthers(new ArrayList<>());
        for (String otherUid : getCachedOtherIds(uniqueId)) {
            RefuelItem other = requireRepository().getRefuel(otherUid);
            if (other != null) item.getOthers().add(other.toRefuelItemData());
        }
        item.setCompleteOthersSnapshot(false);
    }

    /**
     * Ưu tiên membership authoritative đã persist. Chỉ máy nâng cấp chưa từng nhận snapshot
     * mới phải fallback truy vấn cùng FlightId; sau lần thành công đầu tiên row stale không
     * còn có thể sống lại khi request kế tiếp mất mạng.
     */
    private static List<String> getCachedOtherIds(String uniqueId) {
        List<String> authoritative =
                requireRepository().getRemoteOthersMembership(uniqueId);
        if (authoritative != null) return new ArrayList<>(authoritative);

        List<String> legacy = new ArrayList<>();
        for (RefuelItem other : requireRepository().getOthers(uniqueId)) {
            String otherUid = other.getUniqueId();
            if (otherUid != null && !otherUid.isEmpty()) legacy.add(otherUid);
        }
        return legacy;
    }

    /** Gắn collection server có thẩm quyền hoặc cache và giữ rõ trạng thái freshness. */
    private static void attachResolvedOthers(
            RefuelItemData item, List<RefuelItemData> serverOthers, String uniqueId) {
        if (serverOthers != null) {
            item.setOthers(serverOthers);
            item.setCompleteOthersSnapshot(true);
        } else {
            attachCachedOthers(item, uniqueId);
        }
    }

    public static RefuelItemData getRefuelItem(String uniqueId, boolean locked) {
        int generation = beginRefuelRead(uniqueId);
        RefuelItemData response = requireHttpClient().getRefuelItem(uniqueId);
        synchronized (REFUEL_WRITE_LOCK) {
            if (!isLatestRefuelRead(uniqueId, generation))
                return readCachedRefuel(uniqueId);
            return reconcileRefuelItem(uniqueId, response, "GET_ITEM", generation);
        }
    }

    /** Đăng ký thứ tự request trước khi rời khoá để response tới muộn không thể thắng. */
    private static int beginRefuelRead(String uniqueId) {
        int generation = refuelReadGeneration.incrementAndGet();
        if (uniqueId != null) {
            synchronized (REFUEL_WRITE_LOCK) {
                latestRefuelReads.put(uniqueId, generation);
            }
        }
        return generation;
    }

    private static boolean isLatestRefuelRead(String uniqueId, int generation) {
        if (generation <= 0 || uniqueId == null) return true;
        Integer latest = latestRefuelReads.get(uniqueId);
        Integer applied = latestAppliedRefuelReads.get(uniqueId);
        return latest != null && latest == generation
                && (applied == null || applied <= generation);
    }

    /**
     * Kiểm tra một response có còn quyền ghi mọi UID của snapshot hay không.
     * Phải gọi dưới REFUEL_WRITE_LOCK; chỉ markApplied sau khi transaction DB thành công.
     */
    private static boolean canApplyRefuelRead(int generation, Iterable<String> uniqueIds) {
        if (generation <= 0) return true;
        for (String uid : uniqueIds) {
            if (uid == null || uid.isEmpty()) return false;
            Integer started = latestRefuelReads.get(uid);
            Integer applied = latestAppliedRefuelReads.get(uid);
            if ((started != null && started > generation)
                    || (applied != null && applied > generation)) return false;
        }
        return true;
    }

    private static boolean canApplyRefuelRead(int generation, String uniqueId) {
        return canApplyRefuelRead(generation, Collections.singletonList(uniqueId));
    }

    /** Chỉ gọi sau commit thành công, vẫn dưới REFUEL_WRITE_LOCK. */
    private static void markRefuelReadApplied(int generation, Iterable<String> uniqueIds) {
        if (generation <= 0) return;
        for (String uid : uniqueIds) {
            if (uid != null && !uid.isEmpty())
                latestAppliedRefuelReads.merge(uid, generation, Math::max);
        }
    }

    @androidx.annotation.VisibleForTesting
    static int beginUnscopedRefuelRead() {
        return refuelReadGeneration.incrementAndGet();
    }

    /** Đọc cache mà không ghi gì; dùng khi response của request cũ đã bị supersede. */
    private static RefuelItemData readCachedRefuel(String uniqueId) {
        RefuelItem local = requireRepository().getRefuel(uniqueId);
        if (local == null) return null;
        RefuelItemData result = local.toRefuelItemData();
        attachCachedOthers(result, uniqueId);
        return result;
    }

    /** Ghép một response đã tải sẵn; không tự gọi HTTP để caller giữ được single-fetch. */
    private static RefuelItemData reconcileRefuelItem(
            String uniqueId, RefuelItemData remoteItem, String source, int readGeneration) {
        // Read/check/create must be atomic: two threads opening the same refuel
        // would otherwise both see null and insert a duplicate row.
        synchronized (REFUEL_WRITE_LOCK) {
            if (remoteItem != null && (uniqueId == null
                    || remoteItem.getUniqueId() == null
                    || !uniqueId.equals(remoteItem.getUniqueId()))) {
                Logger.appendLog("SYNC", source + " RESPONSE_UID_MISMATCH requested="
                        + uniqueId + " actual=" + remoteItem.getUniqueId());
                remoteItem = null;
            }
            if (remoteItem != null && !canApplyRefuelRead(readGeneration, uniqueId))
                return readCachedRefuel(uniqueId);

            final RefuelItemData response = remoteItem;
            final RefuelItemData[] result = {null};
            final Set<String> committedUids = new HashSet<>();
            try {
                // Root, payload Others và membership cùng nằm trong MỘT outer transaction.
                // Child lỗi phải rollback luôn root, không để lại snapshot nửa mới/nửa cũ.
                requireRepository().runInTransaction(() -> result[0] =
                        reconcileRefuelItemInTransaction(uniqueId, response, source,
                                readGeneration, committedUids));
            } catch (RuntimeException ex) {
                if (!(ex instanceof OtherSnapshotException))
                    Logger.appendLog("SYNC", source + " SNAPSHOT_TRANSACTION_FAILED uid="
                            + uniqueId + ": " + ex.getMessage());
                return readCachedRefuel(uniqueId);
            }
            markRefuelReadApplied(readGeneration, committedUids);
            return result[0];
        }
    }

    /** Phải gọi dưới REFUEL_WRITE_LOCK và trong transaction Room của root. */
    private static RefuelItemData reconcileRefuelItemInTransaction(
            String uniqueId, RefuelItemData remoteItem, String source, int readGeneration,
            Set<String> committedUids) {
        RefuelItem localItem = requireRepository().getRefuel(uniqueId);
        if (remoteItem == null) {
            if (localItem == null) return null;
            RefuelItemData cached = localItem.toRefuelItemData();
            attachCachedOthers(cached, uniqueId);
            return cached;
        }
        TruckOwnership remoteOwnership = classifyTruck(
                remoteItem, currentTruckNoSafe(), currentTruckIdSafe());

        if (remoteItem != null && remoteOwnership == TruckOwnership.UNKNOWN) {
            Logger.appendLog("SYNC", source + " REMOTE_OWNERSHIP_INVALID uid=" + uniqueId);
            remoteItem = null;
        }
        boolean requestedReplica = remoteOwnership == TruckOwnership.FOREIGN
                || (localItem != null && localItem.isRemoteReplica()
                && remoteOwnership != TruckOwnership.CURRENT);

        if (requestedReplica) {
            ReplicaWriteResult write = replaceRemoteReplica(source, remoteItem);
            if (write == ReplicaWriteResult.FAILED)
                throw new IllegalStateException("không lưu được remote root uid=" + uniqueId);

            // Payload bị nhận diện cũ hơn chỉ được dùng để xác nhận row hiện tại; Flight và
            // membership của chính payload cũ không có quyền thay snapshot mới hơn.
            RefuelItemData membershipSource = write == ReplicaWriteResult.APPLIED
                    ? remoteItem : null;
            committedUids.add(uniqueId);

            localItem = requireRepository().getRefuel(uniqueId);
            if (localItem == null)
                throw new IllegalStateException("remote root không tồn tại sau replace");
            List<RefuelItemData> serverOthers = persistCompleteServerOthers(
                    source, membershipSource, readGeneration, committedUids);
            RefuelItemData result = localItem.toRefuelItemData();
            attachResolvedOthers(result, serverOthers, uniqueId);
            return result;
        }

        if (localItem == null && remoteItem.getId() > 0) {
            RefuelItem byId = requireRepository().getRefuel(remoteItem.getId(), 0);
            if (byId != null && byId.getUniqueId() != null
                    && !uniqueId.equals(byId.getUniqueId()))
                throw new IllegalStateException("Id root trỏ UID khác uid=" + uniqueId);
            localItem = byId;
        }

        CurrentMergeResult rootMerge = CurrentMergeResult.APPLIED;
        if (localItem == null) {
            if (remoteItem == null || remoteOwnership != TruckOwnership.CURRENT) return null;
            localItem = RefuelItem.fromRefuelItemData(remoteItem);
            // localId của payload server không bao giờ là primary key của tablet này.
            localItem.setLocalId(0);
            if (remoteItem.getRawJson() != null) localItem.setJsonData(remoteItem.getRawJson());
            localItem.setRemoteReplica(false);
            requireRepository().insertRefuel(localItem);
            committedUids.add(uniqueId);
        } else if (remoteItem != null && remoteOwnership == TruckOwnership.CURRENT) {
            rootMerge = applyRemoteToLocal(source, localItem, remoteItem);
            if (rootMerge == CurrentMergeResult.FAILED)
                throw new IllegalStateException("không merge được current root uid=" + uniqueId);
            localItem.setRemoteReplica(false);
            requireRepository().insertRefuel(localItem);
            committedUids.add(uniqueId);
        }

        // Root cũ vẫn có thể bổ sung trường server-owned an toàn, nhưng Others đi kèm nó
        // không còn là snapshot có thẩm quyền và tuyệt đối không được làm tụt membership.
        RefuelItemData membershipSource = rootMerge == CurrentMergeResult.APPLIED
                ? remoteItem : null;
        List<RefuelItemData> serverOthers = persistCompleteServerOthers(
                source, membershipSource, readGeneration, committedUids);
        RefuelItem stored = requireRepository().getRefuel(uniqueId);
        if (stored == null) return null;
        RefuelItemData result = stored.toRefuelItemData();
        attachResolvedOthers(result, serverOthers, uniqueId);
        return result;
    }

    /** Kết quả một lượt kéo lại mẻ của xe khác. */
    public static final class RefreshResult {
        public final int total;
        public final int failed;
        /** Server đã xác nhận đầy đủ membership của collection Others. */
        public final boolean collectionComplete;

        RefreshResult(int total, int failed, boolean collectionComplete) {
            this.total = total;
            this.failed = failed;
            this.collectionComplete = collectionComplete;
        }

        /** Có mẻ lỗi hoặc chưa chứng minh được collection đầy đủ hay không. */
        public boolean hasFailure() {
            return failed > 0 || !collectionComplete;
        }
    }

    /** Một lần tải Preview: model và trạng thái Others cùng xuất phát từ đúng một root GET. */
    public static final class PreviewLoadResult {
        public final RefuelItemData item;
        public final RefreshResult others;
        /** Một lượt mới hơn cho cùng UID đã bắt đầu; UI phải bỏ kết quả này. */
        public final boolean superseded;

        PreviewLoadResult(RefuelItemData item, RefreshResult others) {
            this(item, others, false);
        }

        PreviewLoadResult(
                RefuelItemData item, RefreshResult others, boolean superseded) {
            this.item = item;
            this.others = others;
            this.superseded = superseded;
        }
    }

    /**
     * Tải dữ liệu cho Preview bằng đúng một request gốc.
     *
     * <p>Nếu response có {@code Others} đầy đủ, UI dùng nguyên membership của response đó.
     * Endpoint cũ/response lỗi mới fallback theo các UID đã cache; fallback không bao giờ
     * được báo là collection hoàn chỉnh vì nó không thể phát hiện xe mới.
     */
    public static PreviewLoadResult loadRefuelForPreview(String uniqueId) {
        // ĐẨY thay đổi chưa gửi TRƯỚC khi đọc lại. Đây là một THỨ TỰ, không phải một cửa sổ
        // race hẹp hơn: GET chỉ chạy sau khi POST đã xong.
        //
        // Không có bước này thì GET trả bản server vẫn CŨ HƠN thứ người dùng vừa nhập, và
        // lượt trộn ngay sau đó phủ nhóm SHARED (bãi đỗ, số hiệu/loại tàu bay, đường bay,
        // hãng bay) từ bản cũ đó đè lên bản local — người dùng thấy sửa xong, load lại thì
        // mất. Đo trên máy thật 27-08-2026.
        //
        // Đẩy trước cũng là điều kiện để XE KHÁC nhìn thấy thay đổi: màn hình xem trước giữ
        // khoá đồng bộ nên Synchronize() không đẩy gì trong suốt thời gian màn hình còn mở.
        pushPendingBeforeRefuelRead();
        int generation = beginRefuelRead(uniqueId);
        RefuelItemData remote = requireHttpClient().getRefuelItem(uniqueId);
        RefuelItemData item;
        synchronized (REFUEL_WRITE_LOCK) {
            if (!isLatestRefuelRead(uniqueId, generation)) {
                return new PreviewLoadResult(
                        readCachedRefuel(uniqueId),
                        new RefreshResult(0, 0, false), true);
            }
            item = reconcileRefuelItem(uniqueId, remote, "PREVIEW_GET", generation);
        }

        RefreshResult others;
        if (item != null && item.hasCompleteOthersSnapshot()) {
            int total = item.getOthers() == null ? 0 : item.getOthers().size();
            others = new RefreshResult(total, 0, true);
        } else {
            others = refreshCachedOthers(uniqueId, item, generation);
        }
        boolean superseded = !isLatestRefuelRead(uniqueId, generation);
        return new PreviewLoadResult(item, others, superseded);
    }

    /**
     * Gửi các mẻ còn chờ trước khi đọc lại từ server.
     *
     * <p>Mất mạng vẫn phải đọc được bản local, nên lỗi ở đây chỉ ghi log: dữ liệu còn nguyên
     * trong hàng đợi và lượt sau gửi tiếp.
     */
    private static void pushPendingBeforeRefuelRead() {
        try {
            syncModifiedRefuels();
        } catch (Throwable ex) {
            Logger.appendLog("SYNC", "PUSH_BEFORE_READ lỗi: " + ex.getMessage());
        }
    }

    /**
     * Kéo lại bản mới nhất của các mẻ do xe KHÁC thực hiện trên cùng chuyến.
     *
     * <p>{@link #getRefuelItem(String)} chỉ gọi HTTP cho mẻ đang mở; danh sách "others" đọc
     * thẳng từ Room nên nó đứng yên ở bản tải về từ lần đồng bộ trước. Màn hình xuất hoá đơn
     * gộp giờ bắt đầu/kết thúc bằng MIN/MAX trên chính danh sách đó, nên chỉ cần một mẻ cũ là
     * hoá đơn mang giờ sai trong khi server đã có dữ liệu đúng — đúng hình dạng sự cố phiếu
     * 2619EY0 in giờ bắt đầu 06:34 trong khi mẻ sớm nhất bắt đầu 15:28.
     *
     * <p>Không kiểm tra trạng thái mạng trước khi gọi: có sóng không đồng nghĩa với gọi được
     * server. Tín hiệu đáng tin duy nhất là kết quả của chính lượt gọi, nên hàm đếm số mẻ
     * thất bại và để màn hình quyết định cảnh báo thế nào.
     *
     * <p>Chạm mạng và DB, phải gọi ở thread nền.
     */
    public static RefreshResult refreshOthers(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty())
            return new RefreshResult(0, 0, true);
        int generation = beginRefuelRead(uniqueId);

        // Đường chính: một response theo mẻ đang mở phải mang trọn collection Others. Nó
        // vừa phát hiện được xe mới chưa có trong Room, vừa tránh N request cho N xe.
        RefuelItemData currentRemote = requireHttpClient().getRefuelItem(uniqueId);
        if (currentRemote != null && (currentRemote.getUniqueId() == null
                || !uniqueId.equals(currentRemote.getUniqueId()))) {
            Logger.appendLog("SYNC", "REFRESH_OTHERS RESPONSE_UID_MISMATCH requested="
                    + uniqueId + " actual=" + currentRemote.getUniqueId());
            currentRemote = null;
        }
        if (currentRemote != null && classifyTruck(currentRemote,
                currentTruckNoSafe(), currentTruckIdSafe()) == TruckOwnership.UNKNOWN) {
            Logger.appendLog("SYNC", "REFRESH_OTHERS ROOT_OWNERSHIP_INVALID uid="
                    + uniqueId);
            currentRemote = null;
        }
        if (currentRemote != null && currentRemote.hasCompleteOthersSnapshot()) {
            int total = currentRemote.getOthers() == null ? 0 : currentRemote.getOthers().size();
            RefuelItemData refreshedRoot;
            synchronized (REFUEL_WRITE_LOCK) {
                if (!isLatestRefuelRead(uniqueId, generation))
                    return new RefreshResult(total, 0, false);
                refreshedRoot = reconcileRefuelItem(
                        uniqueId, currentRemote, "REFRESH_OTHERS", generation);
            }
            boolean complete = refreshedRoot != null
                    && refreshedRoot.hasCompleteOthersSnapshot();
            int failed = complete ? 0 : Math.max(1, total);
            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "REFRESH_OTHERS uid=%s total=%d failed=%d", uniqueId, total, failed));
            return new RefreshResult(total, failed, complete);
        }

        // Server/endpoint cũ không có collection Others: fallback các UID đã cache. Đây chỉ
        // là lưới an toàn; nó không thể phát hiện mẻ mới nên lượt root ở trên luôn được ưu tiên.
        return refreshCachedOthers(uniqueId, null, generation);
    }

    /** Làm mới best-effort các UID đã biết; không thể chứng minh membership là đầy đủ. */
    private static RefreshResult refreshCachedOthers(
            String uniqueId, RefuelItemData targetItem, int readGeneration) {
        if (uniqueId == null || uniqueId.isEmpty())
            return new RefreshResult(0, 0, true);

        List<String> otherIds = new ArrayList<>();
        synchronized (REFUEL_WRITE_LOCK) {
            otherIds.addAll(getCachedOtherIds(uniqueId));
        }

        int failed = 0;
        for (String otherId : otherIds) {
            // Gọi mạng NGOÀI khoá: giữ khoá ghi suốt một lượt HTTP sẽ chặn cả luồng đồng bộ nền.
            int childGeneration = beginRefuelRead(otherId);
            RefuelItemData remote = requireHttpClient().getRefuelItem(otherId);
            if (remote == null) {
                failed++;
                continue;
            }
            if (remote.getUniqueId() == null || !otherId.equals(remote.getUniqueId())) {
                failed++;
                Logger.appendLog("SYNC", "REFRESH_OTHERS RESPONSE_UID_MISMATCH requested="
                        + otherId + " actual=" + remote.getUniqueId());
                continue;
            }

            synchronized (REFUEL_WRITE_LOCK) {
                if (!isLatestRefuelRead(uniqueId, readGeneration))
                    return new RefreshResult(otherIds.size(), failed, false);
                if (!isLatestRefuelRead(otherId, childGeneration)) {
                    failed++;
                    continue;
                }
                RefuelItem localItem = requireRepository().getRefuel(otherId);
                TruckOwnership ownership = classifyTruck(
                        remote, currentTruckNoSafe(), currentTruckIdSafe());
                if (ownership == TruckOwnership.FOREIGN) {
                    ReplicaWriteResult write = replaceRemoteReplica(
                            "REFRESH_OTHERS", remote);
                    if (write == ReplicaWriteResult.FAILED) failed++;
                    else
                        markRefuelReadApplied(childGeneration,
                                Collections.singletonList(otherId));
                } else if (ownership == TruckOwnership.CURRENT) {
                    if (localItem == null) {
                        localItem = RefuelItem.fromRefuelItemData(remote);
                        localItem.setLocalId(0);
                        if (remote.getRawJson() != null)
                            localItem.setJsonData(remote.getRawJson());
                        localItem.setRemoteReplica(false);
                        requireRepository().insertRefuel(localItem);
                        markRefuelReadApplied(childGeneration,
                                Collections.singletonList(otherId));
                    } else if (applyRemoteToLocal("REFRESH_OTHERS_OWN", localItem, remote)
                            != CurrentMergeResult.FAILED) {
                        localItem.setRemoteReplica(false);
                        requireRepository().insertRefuel(localItem);
                        markRefuelReadApplied(childGeneration,
                                Collections.singletonList(otherId));
                    } else {
                        failed++;
                    }
                } else {
                    failed++;
                    Logger.appendLog("SYNC", "REFRESH_OTHERS OWNERSHIP_INVALID uid="
                            + otherId);
                }
            }
        }

        if (targetItem != null) {
            synchronized (REFUEL_WRITE_LOCK) {
                if (isLatestRefuelRead(uniqueId, readGeneration))
                    attachCachedOthers(targetItem, uniqueId);
            }
        }

        Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                "REFRESH_OTHERS uid=%s total=%d failed=%d collectionComplete=false",
                uniqueId, otherIds.size(), failed));

        return new RefreshResult(otherIds.size(), failed, false);
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
    /**
     * Nhóm trường CHỨNG TỪ: những gì việc in phiếu/hoá đơn được phép ghi lên một mẻ.
     *
     * <p>Đây là ranh giới của {@link #patchRefuelDocument}. Thêm khoá vào đây là nới quyền
     * ghi lên mẻ của xe khác, nên chỉ thêm khi trường đó thực sự do việc phát hành chứng từ
     * sinh ra. Số liệu đồng hồ, thời gian, nhiệt độ, tỉ trọng KHÔNG bao giờ thuộc nhóm này.
     */
    private static final java.util.Set<String> DOCUMENT_KEYS =
            java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
                    "ReceiptNumber", "ReceiptUniqueId", "ReceiptCount", "WeightNote",
                    "PrintStatus", "Printed",
                    "InvoiceNumber", "InvoiceFormId", "PrintTemplate",
                    "Price", "TaxRate")));

    /**
     * Ghi metadata chứng từ, CHO PHÉP cả mẻ của xe khác — đường "in hộ".
     *
     * <p>Hiện trường có nghiệp vụ thật: máy in của xe B hỏng, xe A in hộ phiếu cho mẻ của B.
     * {@link #patchRefuel} fail-closed trên replica nên không có bước này thì phiếu in ra
     * nhưng mẻ của B vẫn mang trạng thái chưa in — cả hai máy đều không biết, và mẻ đó bị in
     * lần hai với một số phiếu khác.
     *
     * <p>Hai điều kiện KHÔNG được nới:
     * <ul>
     *   <li>chỉ các khoá trong {@link #DOCUMENT_KEYS} được đổi — một patch lỡ chạm số liệu
     *       mẻ bị từ chối nguyên vẹn, đúng như {@link #patchRefuel};</li>
     *   <li>với mẻ xe khác, SERVER PHẢI NHẬN TRƯỚC rồi mới ghi xuống Room. Replica bị lượt
     *       pull ghi đè toàn bộ ({@code replaceRemoteRefuelSnapshots}), nên một dấu chỉ nằm
     *       ở máy sẽ bị xoá ở lần đồng bộ kế tiếp — ghi trước là tạo ra dấu vết giả.</li>
     * </ul>
     *
     * <p>Mẻ của chính xe này đi nguyên đường {@link #patchRefuel} cũ.
     */
    public static PatchResult patchRefuelDocument(String uniqueId, RefuelPatch patch) {
        if (uniqueId == null || uniqueId.isEmpty() || patch == null)
            return PatchResult.failed("INVALID_ARGUMENT", null);

        RefuelItemData before;
        RefuelItemData patched;
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem localItem = requireRepository().getRefuel(uniqueId);
            if (localItem == null) return PatchResult.failed("NOT_FOUND", null);

            TruckOwnership ownership = classifyStoredTruck(localItem,
                    currentTruckNoSafe(), currentTruckIdSafe());
            boolean foreign = localItem.isRemoteReplica() || ownership == TruckOwnership.FOREIGN;

            // Mẻ của xe này: không có gì phải nới, đi đúng đường cũ.
            if (!foreign) return patchRefuel(uniqueId, patch);

            before = localItem.toRefuelItemData();
            patched = localItem.toRefuelItemData();
            patch.apply(patched);

            String finalDiff = RefuelSyncGuard.describeFinalValueDiff(before, patched);
            if (finalDiff != null) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=DOCUMENT_PATCH_REJECTED uid=%s reason=FINAL_VALUES_TOUCHED"
                                + " diff=%s thread=%s",
                        uniqueId, finalDiff, Thread.currentThread().getName()));
                return PatchResult.failed("FINAL_VALUES_TOUCHED", before);
            }

            java.util.List<String> outside = keysOutsideDocumentScope(before, patched);
            if (!outside.isEmpty()) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=DOCUMENT_PATCH_REJECTED uid=%s reason=NON_DOCUMENT_FIELD"
                                + " keys=%s thread=%s",
                        uniqueId, outside, Thread.currentThread().getName()));
                return PatchResult.failed("FOREIGN_NON_DOCUMENT_FIELD", before);
            }

            // Không có gì đổi: không làm phiền server, cũng không đánh dấu gì thêm.
            if (before.toJson().equals(patched.toJson()))
                return PatchResult.applied(before);
        }

        // NGOÀI khoá ghi: gọi HTTP. Giữ khoá qua một request mạng sẽ đóng băng mọi đường ghi
        // mẻ khác trong lúc chờ.
        Logger.appendLog("DTH", "DOCUMENT_PATCH_FOREIGN uid=" + uniqueId
                + " receipt=" + patched.getReceiptNumber()
                + " invoice=" + patched.getInvoiceNumber());

        RefuelItemData response;
        try {
            response = requireHttpClient().postRefuel(patched);
        } catch (Throwable ex) {
            Logger.appendLog("DTH", "DOCUMENT_PATCH_FOREIGN lỗi gửi: " + ex);
            response = null;
        }

        if (response == null || response.getUniqueId() == null
                || !response.getUniqueId().equals(uniqueId)) {
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=DOCUMENT_PATCH_REMOTE_REJECTED uid=%s responseUid=%s thread=%s",
                    uniqueId, response == null ? "null" : response.getUniqueId(),
                    Thread.currentThread().getName()));
            return PatchResult.failed("REMOTE_PUSH_FAILED", before);
        }

        synchronized (REFUEL_WRITE_LOCK) {
            // Đọc lại: lượt pull nền có thể đã thay row trong lúc request đang bay.
            RefuelItem stored = requireRepository().getRefuel(uniqueId);
            if (stored == null) return PatchResult.failed("NOT_FOUND", null);

            RefuelItemData latest = stored.toRefuelItemData();
            patch.apply(latest);
            if (RefuelSyncGuard.describeFinalValueDiff(stored.toRefuelItemData(), latest) != null
                    || !keysOutsideDocumentScope(stored.toRefuelItemData(), latest).isEmpty()) {
                // Row đã đổi tới mức patch không còn nằm gọn trong nhóm chứng từ. Server đã
                // nhận rồi, nên đây không phải lỗi ghi — chỉ là bản trong máy sẽ theo lượt
                // pull kế tiếp.
                Logger.appendLog("DTH", "DOCUMENT_PATCH_FOREIGN row đã đổi, để pull đồng bộ"
                        + " uid=" + uniqueId);
                return PatchResult.applied(stored.toRefuelItemData());
            }

            stored.updateData(latest);
            // Vẫn là replica của xe khác: KHÔNG đưa vào hàng đợi gửi nền, server đã có bản
            // này rồi. Cờ replica giữ nguyên để mọi đường ghi khác vẫn fail-closed như cũ.
            stored.setLocalModified(false);
            requireRepository().insertRefuel(stored);
            return PatchResult.applied(stored.toRefuelItemData());
        }
    }

    /** Các khoá bị patch đổi mà KHÔNG thuộc nhóm chứng từ. Rỗng nghĩa là patch hợp lệ. */
    private static java.util.List<String> keysOutsideDocumentScope(
            RefuelItemData before, RefuelItemData after) {
        java.util.List<String> outside = new java.util.ArrayList<>();
        try {
            com.google.gson.JsonObject from =
                    com.google.gson.JsonParser.parseString(before.toJson()).getAsJsonObject();
            com.google.gson.JsonObject to =
                    com.google.gson.JsonParser.parseString(after.toJson()).getAsJsonObject();

            java.util.Set<String> keys = new java.util.HashSet<>(from.keySet());
            keys.addAll(to.keySet());
            for (String key : keys) {
                if (DOCUMENT_KEYS.contains(key)) continue;
                if (!RefuelValues.equal(key, from.get(key), to.get(key))) outside.add(key);
            }
        } catch (RuntimeException ex) {
            // Không so được thì coi như patch chạm chỗ không được phép: fail-closed.
            outside.add("UNPARSEABLE");
        }
        return outside;
    }

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

            TruckOwnership ownership = classifyStoredTruck(localItem,
                    currentTruckNoSafe(), currentTruckIdSafe());
            if (localItem.isRemoteReplica() || ownership == TruckOwnership.FOREIGN)
                return PatchResult.failed("FOREIGN_READ_ONLY", localItem.toRefuelItemData());
            if (ownership != TruckOwnership.CURRENT)
                return PatchResult.failed("OWNERSHIP_UNKNOWN", localItem.toRefuelItemData());

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


    /**
     * Đẩy các chứng từ còn chờ gửi.
     *
     * <p>Tách khỏi worker "core" để có thể đẩy chứng từ NGAY cả khi lượt sync đầy đủ đang
     * bị khoá. Chứng từ là kết quả cuối cùng của một mẻ tra nạp, nên nó phải lên tới server
     * sớm nhất có thể, không đợi người dùng rời màn hình xem trước.
     */
    @androidx.annotation.VisibleForTesting
    static void syncModifiedReceipts() {
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
    }

    /**
     * Chỉ ĐẨY những gì còn chờ gửi, không kéo bản server về.
     *
     * <p>Khoá của màn hình xem trước tồn tại để lượt pull không ghi đè phiếu đang xem/đang
     * in — nó không có lý do gì để giữ lại dữ liệu người dùng vừa chốt. Đo trên máy thật
     * 17-08 21:46: bấm Rời đi rồi xuất chứng từ xong, dữ liệu nằm đủ trong Room nhưng
     * server không nhận được gì, vì người dùng còn đứng ở màn hình đó.
     */
    public static void pushPendingInBackground() {
        new Thread(DataHelper::pushPendingOnly, "FMS-Push-Only").start();
    }

    @androidx.annotation.VisibleForTesting
    static void pushPendingOnly() {
        try {
            syncModifiedRefuels();
        } catch (Throwable ex) {
            Logger.appendLog("SYNC", "push-only refuel lỗi: " + ex.getMessage());
        }
        try {
            syncModifiedReceipts();
        } catch (Throwable ex) {
            Logger.appendLog("SYNC", "push-only receipt lỗi: " + ex.getMessage());
        }
    }

    /** Còn lượt sync nào đang bị khoá nuốt và chưa được chạy lại không. */
    @androidx.annotation.VisibleForTesting
    public static boolean hasPendingSyncRequest() {
        return syncPending.get();
    }

    @androidx.annotation.VisibleForTesting
    static boolean isSyncProcessing() {
        return processing.get();
    }

    public static void unlockSync() {
        syncLocked.set(false);
        Logger.appendLog("SYNC", "UNLOCK sync"
                + (syncPending.get() ? " (có lượt đang chờ, chạy ngay)" : ""));
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
        String readKey = localItem != null && localItem.getUniqueId() != null
                && !localItem.getUniqueId().isEmpty()
                ? localItem.getUniqueId() : "id:" + id;
        int readGeneration = beginRefuelRead(readKey);
        Logger.appendLog("DTH", "Start remote loading item " + id + " - " + localId);

        remoteItem = requireHttpClient().getRefuelItem(id);
        Logger.appendLog("DTH", "End remote loading item " + id + " - " + localId);

        // Re-read inside the lock: the row may have been created or modified while
        // the HTTP request above was running.
        synchronized (REFUEL_WRITE_LOCK) {
            localItem = requireRepository().getRefuel(id, localId);
            if (!isLatestRefuelRead(readKey, readGeneration)) {
                if (localItem == null) return null;
                RefuelItemData cached = localItem.toRefuelItemData();
                attachCachedOthers(cached, localItem.getUniqueId());
                return cached;
            }
            if (remoteItem != null && id != null && id > 0
                    && (remoteItem.getId() == null
                    || remoteItem.getId().intValue() != id.intValue())) {
                Logger.appendLog("SYNC", "GET_ITEM_BY_ID RESPONSE_ID_MISMATCH expected="
                        + id + " actual=" + remoteItem.getId());
                remoteItem = null;
            }
            if (remoteItem != null && localItem != null
                    && localItem.getUniqueId() != null
                    && !localItem.getUniqueId().isEmpty()
                    && !localItem.getUniqueId().equals(remoteItem.getUniqueId())) {
                Logger.appendLog("SYNC", "GET_ITEM_BY_ID RESPONSE_UID_MISMATCH expected="
                        + localItem.getUniqueId() + " actual=" + remoteItem.getUniqueId());
                remoteItem = null;
            }
            String resolvedUid = remoteItem != null ? remoteItem.getUniqueId()
                    : localItem == null ? null : localItem.getUniqueId();
            if (resolvedUid == null || resolvedUid.isEmpty())
                return localItem == null ? null : localItem.toRefuelItemData();

            // Alias token "id:..." sang UID thật. reconcile kiểm cả latest-started và
            // latest-applied của UID nên GET theo Id cũng không thể thắng GET theo UID mới hơn.
            if (!canApplyRefuelRead(readGeneration, resolvedUid)) {
                RefuelItem cached = requireRepository().getRefuel(resolvedUid);
                return cached == null ? null : cached.toRefuelItemData();
            }
            return reconcileRefuelItem(
                    resolvedUid, remoteItem, "GET_ITEM_BY_ID", readGeneration);
        }

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
        // Room chặn truy cập DB trên main thread. Đo trên xe thật 17-08:
        // "Bảo trì nâng cấp lỗi: Cannot access database on the main thread" — nghĩa là
        // backfill CHƯA TỪNG chạy trên bất kỳ xe nào.
        new Thread(() -> runUpgradeMaintenanceBlocking(context), "FMS-Upgrade-Maintenance")
                .start();
    }

    private static void runUpgradeMaintenanceBlocking(android.content.Context context) {
        try {
            android.content.SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences("FMS", android.content.Context.MODE_PRIVATE);

            int lastVersion = prefs.getInt("LAST_MAINTENANCE_VERSION", 0);
            // Migration 13→14 chỉ có thể thêm cột với DEFAULT 0; cấu hình xe nằm ngoài DB
            // nên phải phân loại ở tầng ứng dụng. Chạy cả khi version đã được đánh dấu xong:
            // lần đăng nhập đầu có thể chưa đọc được setting, lượt sau phải còn cơ hội sửa.
            int quarantined = quarantineLegacyForeignRefuels();
            if (lastVersion == BuildConfig.VERSION_CODE) {
                if (quarantined > 0)
                    Logger.appendLog("SYNC", "Quarantine bổ sung " + quarantined
                            + " mẻ xe khác legacy");
                return;
            }

            int resumed = requireRepository().resumeConflictedRefuels();
            int backfilled = backfillColumnsFromJson();
            prefs.edit().putInt("LAST_MAINTENANCE_VERSION", BuildConfig.VERSION_CODE).apply();

            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "Bảo trì nâng cấp %d -> %d: quarantine %d mẻ xe khác, "
                            + "đưa %d phiếu kẹt trở lại hàng đợi, chiếu lại cột cho %d phiếu",
                    lastVersion, BuildConfig.VERSION_CODE,
                    quarantined, resumed, backfilled));
        } catch (Exception ex) {
            // Bảo trì hỏng không được phép chặn đăng nhập.
            Logger.appendLog("SYNC", "Bảo trì nâng cấp lỗi: " + ex.getMessage());
        }
    }

    /**
     * Đánh dấu các replica legacy sau migration. Chỉ thay metadata đồng bộ; payload server
     * trong {@code jsonData} và các cột nghiệp vụ được giữ nguyên.
     */
    @androidx.annotation.VisibleForTesting
    static int quarantineLegacyForeignRefuels() {
        int changed = 0;
        String ownTruck = currentTruckNoSafe();
        int ownTruckId = currentTruckIdSafe();

        synchronized (REFUEL_WRITE_LOCK) {
            List<RefuelItem> rows = requireRepository().getAllRefuels();
            if (rows == null) return 0;

            for (RefuelItem row : rows) {
                try {
                    if (classifyStoredTruck(row, ownTruck, ownTruckId)
                            != TruckOwnership.FOREIGN) continue;

                    boolean needsChange = !row.isRemoteReplica()
                            || row.isLocalModified()
                            || row.getPostStatus() != RefuelItem.ITEM_POST_STATUS.SUCCESS;
                    if (!needsChange) continue;

                    row.setRemoteReplica(true);
                    row.setLocalModified(false);
                    row.setSynced(true);
                    row.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                    requireRepository().insertRefuel(row);
                    changed++;
                } catch (Exception ex) {
                    Logger.appendLog("SYNC", "Quarantine replica legacy lỗi localId="
                            + row.getLocalId() + ": " + ex.getMessage());
                }
            }
        }
        return changed;
    }

    /**
     * Chiếu lại CỘT từ {@code jsonData} cho dữ liệu đã có trên máy.
     *
     * <p>Các bản trước chỉ ghi vài cột nên phiếu cũ mang {@code realAmount},
     * {@code endNumber}, {@code density}, {@code qualityNo}... bằng 0/null trong khi
     * {@code jsonData} đúng. Bản này ghi đủ cột từ nay, nhưng dữ liệu CŨ trên xe thì không
     * tự sửa — mọi màn hình đọc theo cột vẫn hiện 0 GL cho lịch sử. Chạy một lần khi nâng
     * cấp để cột và JSON nói cùng một chuyện.
     *
     * <p>KHÔNG đụng {@code jsonData}, không đụng cờ hàng đợi, không tăng version: đây thuần
     * tuý là sửa lại phần chiếu, không phải một lần sửa dữ liệu.
     *
     * @return số phiếu đã phải chiếu lại
     */
    @androidx.annotation.VisibleForTesting
    static int backfillColumnsFromJson() {
        int fixed = 0;

        synchronized (REFUEL_WRITE_LOCK) {
            List<RefuelItem> all = requireRepository().getAllRefuels();
            if (all == null) return 0;

            for (RefuelItem row : all) {
                try {
                    RefuelItemData data = row.toRefuelItemData();
                    if (data == null) continue;

                    // Chỉ ghi khi cột thực sự lệch, để không đụng vào row đã đúng.
                    if (Math.abs(row.getRealAmount() - data.getRealAmount()) < 0.001
                            && Math.abs(row.getEndNumber() - data.getEndNumber()) < 0.001
                            && Math.abs(row.getStartNumber() - data.getStartNumber()) < 0.001
                            && Math.abs(row.getDensity() - data.getDensity()) < 0.001)
                        continue;

                    boolean dirtyBefore = row.isLocalModified();
                    RefuelItem.ITEM_POST_STATUS postBefore = row.getPostStatus();

                    row.projectColumnsFrom(data);

                    // Giữ nguyên trạng thái hàng đợi: chiếu lại cột không phải là sửa dữ liệu
                    // nên không được đẩy phiếu đã gửi vào hàng đợi lần nữa.
                    row.setLocalModified(dirtyBefore);
                    row.setPostStatus(postBefore);
                    requireRepository().insertRefuel(row);
                    fixed++;
                } catch (Exception ex) {
                    Logger.appendLog("SYNC", "Backfill cột lỗi localId="
                            + row.getLocalId() + ": " + ex.getMessage());
                }
            }
        }
        return fixed;
    }

    public static boolean checkLocalModified() {

        return requireRepository().getLocalModified();
    }

    private static String refuelPullCursorKey() {
        String truckNo = currentTruckNoSafe();
        return REFUEL_PULL_CURSOR_KEY_PREFIX + currentTruckIdSafe() + "_"
                + (truckNo == null ? "" : truckNo);
    }

    /** Cursor chỉ thuộc endpoint modified; cố ý không suy ra từ bất kỳ row Room nào. */
    @androidx.annotation.VisibleForTesting
    static Date readRefuelPullCursor() {
        FMSApplication app = FMSApplication.getApplication();
        if (app == null) return null;
        long cursor = app.getSharedPreferences("FMS", Context.MODE_PRIVATE)
                .getLong(refuelPullCursorKey(), 0L);
        if (cursor <= 0L) return null;
        return new Date(Math.max(0L, cursor - REFUEL_PULL_CURSOR_OVERLAP_MS));
    }

    /** Chỉ gọi sau khi toàn bộ batch đã được ingest/giữ-newer an toàn. */
    @androidx.annotation.VisibleForTesting
    static boolean commitRefuelPullCursor(List<RefuelItemData> remoteList) {
        long newest = 0L;
        if (remoteList != null) {
            for (RefuelItemData item : remoteList) {
                // Không nhảy qua một item mà server không cấp thứ tự. Chấp nhận tải lặp
                // an toàn còn hơn biến thay đổi đó thành một khoảng trống vĩnh viễn.
                if (item == null || item.getDateUpdated() == null) return false;
                newest = Math.max(newest, item.getDateUpdated().getTime());
            }
        }
        if (newest <= 0L) return false; // retry idempotent; không tự bịa high-watermark.
        if (newest > System.currentTimeMillis() + REFUEL_PULL_CURSOR_OVERLAP_MS) {
            Logger.appendLog("SYNC", "REMOTE_PULL CURSOR_FUTURE_TIMESTAMP");
            return false;
        }

        FMSApplication app = FMSApplication.getApplication();
        if (app == null) return false;
        android.content.SharedPreferences prefs =
                app.getSharedPreferences("FMS", Context.MODE_PRIVATE);
        long current = prefs.getLong(refuelPullCursorKey(), 0L);
        if (newest <= current) return true;
        boolean saved = prefs.edit().putLong(refuelPullCursorKey(), newest).commit();
        if (!saved)
            Logger.appendLog("SYNC", "REMOTE_PULL CURSOR_COMMIT_FAILED");
        return saved;
    }

    @androidx.annotation.VisibleForTesting
    static void clearRefuelPullCursorForTesting() {
        FMSApplication app = FMSApplication.getApplication();
        if (app != null)
            app.getSharedPreferences("FMS", Context.MODE_PRIVATE)
                    .edit().remove(refuelPullCursorKey()).commit();
    }

    /**
     * Ingest một response modified-list. Mỗi root + Others + Flight là một transaction;
     * batch chỉ được tiến cursor khi tất cả item đều APPLIED hoặc chủ ý KEPT_NEWER.
     */
    @androidx.annotation.VisibleForTesting
    static boolean applyModifiedRefuelBatch(
            List<RefuelItemData> remoteList, int readGeneration) {
        if (remoteList == null) return false;
        Set<String> responseUids = new HashSet<>();
        Set<Integer> responseIds = new HashSet<>();
        for (RefuelItemData model : remoteList) {
            if (model == null) {
                Logger.appendLog("SYNC", "REMOTE_PULL INVALID_BATCH: item null");
                return false;
            }
            String uid = model.getUniqueId();
            if (!model.isDeleted() && (uid == null || uid.trim().isEmpty())) {
                Logger.appendLog("SYNC", "REMOTE_PULL INVALID_BATCH: thiếu UniqueId");
                return false;
            }
            if (uid != null && !uid.trim().isEmpty() && !responseUids.add(uid)) {
                Logger.appendLog("SYNC", "REMOTE_PULL INVALID_BATCH: trùng uid=" + uid);
                return false;
            }
            if (model.getId() > 0 && !responseIds.add(model.getId())) {
                Logger.appendLog("SYNC", "REMOTE_PULL INVALID_BATCH: trùng id="
                        + model.getId());
                return false;
            }
            if (model.isDeleted() && (uid == null || uid.trim().isEmpty())
                    && model.getId() <= 0) {
                Logger.appendLog("SYNC", "REMOTE_PULL INVALID_BATCH: tombstone thiếu id");
                return false;
            }
        }
        boolean complete = true;
        beginPullLogBatch();
        for (RefuelItemData model : remoteList) {
            boolean accepted = model != null && (model.isDeleted()
                    ? applyRemotePullDeletion(model, readGeneration)
                    : applyRemotePullItem(model, readGeneration));
            if (!accepted) complete = false;
        }
        endPullLogBatch("REMOTE_PULL");
        return complete;
    }

    private static boolean applyRemotePullItem(
            RefuelItemData model, int readGeneration) {
        String uid = model.getUniqueId();
        if (uid == null || uid.trim().isEmpty()) {
            Logger.appendLog("SYNC", "REMOTE_PULL INVALID_IDENTITY: thiếu UniqueId");
            return false;
        }

        synchronized (REFUEL_WRITE_LOCK) {
            if (!canApplyRefuelRead(readGeneration, uid)) return false;

            Set<String> committedUids = new HashSet<>();
            final boolean[] appliedPayload = {false};
            try {
                requireRepository().runInTransaction(() -> {
                    TruckOwnership ownership = classifyTruck(
                            model, currentTruckNoSafe(), currentTruckIdSafe());
                    if (ownership == TruckOwnership.FOREIGN) {
                        ReplicaWriteResult write = replaceRemoteReplica("REMOTE_PULL", model);
                        if (write == ReplicaWriteResult.FAILED)
                            throw new IllegalStateException("không lưu được replica uid=" + uid);
                        committedUids.add(uid);
                        if (write == ReplicaWriteResult.KEPT_NEWER) return;
                        appliedPayload[0] = true;
                    } else if (ownership == TruckOwnership.CURRENT) {
                        RefuelItem local = requireRepository().getRefuel(uid);
                        if (local == null && model.getId() > 0)
                            local = requireRepository().getRefuel(model.getId(), 0);
                        if (local != null && local.getUniqueId() != null
                                && !uid.equals(local.getUniqueId()))
                            throw new IllegalStateException("Id trỏ UID khác uid=" + uid);

                        if (local == null) {
                            local = RefuelItem.fromRefuelItemData(model);
                            local.setLocalId(0);
                            if (model.getRawJson() != null)
                                local.setJsonData(model.getRawJson());
                            local.setRemoteReplica(false);
                            requireRepository().insertRefuel(local);
                        } else {
                            CurrentMergeResult merge = applyRemoteToLocal(
                                    "REMOTE_PULL", local, model);
                            if (merge == CurrentMergeResult.FAILED)
                                throw new IllegalStateException("không merge được uid=" + uid);
                            local.setRemoteReplica(false);
                            requireRepository().insertRefuel(local);
                            committedUids.add(uid);
                            if (merge == CurrentMergeResult.KEPT_NEWER) return;
                        }
                        committedUids.add(uid);
                        appliedPayload[0] = true;
                    } else {
                        throw new IllegalStateException(
                                "không xác định được xe sở hữu uid=" + uid);
                    }

                    // KEPT_NEWER không được dùng Flight/Others của payload cũ.
                    if (!appliedPayload[0]) return;
                    persistCompleteServerOthers(
                            "REMOTE_PULL", model, readGeneration, committedUids);
                    insertFlightFromRefuel(model);
                });
            } catch (RuntimeException ex) {
                if (!(ex instanceof OtherSnapshotException))
                    Logger.appendLog("SYNC", "REMOTE_PULL ITEM_FAILED uid=" + uid
                            + ": " + ex.getMessage());
                return false;
            }
            markRefuelReadApplied(readGeneration, committedUids);
            return true;
        }
    }

    private static void insertFlightFromRefuel(RefuelItemData model) {
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
    }

    private static boolean applyRemotePullDeletion(
            RefuelItemData tombstone, int readGeneration) {
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem byUid = tombstone.getUniqueId() == null
                    ? null : requireRepository().getRefuel(tombstone.getUniqueId());
            RefuelItem byId = tombstone.getId() > 0
                    ? requireRepository().getRefuel(tombstone.getId(), 0) : null;
            if (byUid != null && byId != null && byUid.getLocalId() != byId.getLocalId()) {
                Logger.appendLog("SYNC", "REMOTE_PULL DELETE_IDENTITY_CONFLICT id="
                        + tombstone.getId());
                return false;
            }
            RefuelItem stored = byUid != null ? byUid : byId;
            if (stored == null) {
                if (tombstone.getUniqueId() != null)
                    markRefuelReadApplied(readGeneration,
                            Collections.singletonList(tombstone.getUniqueId()));
                else if (tombstone.getId() > 0)
                    markRefuelReadApplied(readGeneration,
                            Collections.singletonList("id:" + tombstone.getId()));
                return true; // tombstone đã được áp dụng từ lượt trước.
            }

            String uid = stored.getUniqueId();
            if (tombstone.getId() > 0 && stored.getId() > 0
                    && tombstone.getId() != stored.getId()) {
                Logger.appendLog("SYNC", "REMOTE_PULL DELETE_ID_MISMATCH uid=" + uid
                        + " expected=" + stored.getId() + " actual=" + tombstone.getId());
                return false;
            }
            if (tombstone.getUniqueId() != null && uid != null
                    && !tombstone.getUniqueId().equals(uid)) {
                Logger.appendLog("SYNC", "REMOTE_PULL DELETE_UID_MISMATCH id="
                        + tombstone.getId());
                return false;
            }
            if (uid != null && !canApplyRefuelRead(readGeneration, uid)) return false;

            boolean higherRevision = tombstone.getServerRevision() > 0
                    && tombstone.getServerRevision() > stored.getServerRevision();
            boolean staleRevision = stored.getServerRevision() > 0
                    && tombstone.getServerRevision() > 0
                    && tombstone.getServerRevision() < stored.getServerRevision();
            boolean staleDate = !higherRevision && stored.getDateUpdated() != null
                    && tombstone.getDateUpdated() != null
                    && tombstone.getDateUpdated().before(stored.getDateUpdated());
            if (staleRevision || staleDate) {
                if (uid != null)
                    markRefuelReadApplied(readGeneration, Collections.singletonList(uid));
                return true;
            }
            if (stored.isLocalModified()) {
                Logger.appendLog("SYNC", "REMOTE_PULL DELETE_BLOCKED_LOCAL_DIRTY uid=" + uid);
                return false;
            }
            if (!requireRepository().removeRemoteDeletedRefuel(stored)) {
                Logger.appendLog("SYNC", "REMOTE_PULL DELETE_FAILED uid=" + uid);
                return false;
            }
            if (uid != null)
                markRefuelReadApplied(readGeneration, Collections.singletonList(uid));
            return true;
        }
    }

    public static void Synchronize() {
//        Logger.appendLog("SYNC", "Start synchronize");
//
//        if (!AppStateObserver.isAppInForeground()) {
//            Logger.appendLog("SYNC", "Abort sync — app in background");
//            return;
//        }
        if (syncLocked.get()) {
            // Thoát IM LẶNG ở đây từng làm không thể chẩn đoán: đo trên máy thật 17-08,
            // người dùng bấm Rời đi rồi xuất chứng từ, dữ liệu vào Room đủ nhưng server
            // không nhận được gì suốt 3,5 phút — vì màn hình xem trước giữ khoá và mọi
            // Synchronize() trả về không để lại một dòng nào.
            // Khoá chặn CẢ HAI CHIỀU trong suốt thời gian màn hình xem trước còn mở, nên
            // phiếu đã chốt nằm lại trong Room cho tới khi người dùng rời màn hình. Đo trên
            // máy thật 17-08 21:46. Chưa đổi hành vi: khoá cũng đang bảo vệ bất biến chống
            // POST song song, nới nó ra là một quyết định về nghiệp vụ.
            syncPending.set(true);
            if (shouldLogSyncLockSkip())
                Logger.appendLog("SYNC", "Khoá pull (màn hình xem trước) - vẫn đẩy phiếu chờ");
            return;
        }
        if (processing.compareAndSet(false, true)) {   // chỉ MỘT phiên sync vào được
            activeSyncTasks.set(1); // coordinator giữ phiên sync cho tới khi đã tạo đủ worker
            Logger.appendLog("SYNC", "START Receipt thread=" + Thread.currentThread().getName());
            //post local modified data
            startSyncTask("core", () -> {

                try{
                    syncModifiedRefuels();

                    // Cursor của endpoint phải độc lập với DateUpdated trong Room. MAX(Room)
                    // có thể vừa tăng bởi một POST/GET chi tiết và làm nhảy qua những xe khác
                    // chưa từng được pull về máy này.
                    Date d = readRefuelPullCursor();
                    int pullGeneration = beginUnscopedRefuelRead();
                    List<RefuelItemData> remoteList =
                            requireHttpClient().getModifiedRefuels(0, d);
                    if (remoteList != null) {
                        boolean complete = applyModifiedRefuelBatch(
                                remoteList, pullGeneration);
                        if (complete) commitRefuelPullCursor(remoteList);
                        else Logger.appendLog("SYNC",
                                "REMOTE_PULL BATCH_INCOMPLETE - giữ nguyên cursor để tải lại");
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

                    syncModifiedReceipts();

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
                            // Không ghi lại `item`: đó là bản chụp trước khi gửi, người dùng có thể
                            // đã lưu bản sửa trong lúc gói còn trên đường.
                            boolean settled = requireRepository().markTruckFuelSynced(item, newData.getId());
                            Logger.appendLog("B2502", "Synced localId=" + item.getLocalId()
                                    + ", responseId=" + newData.getId()
                                    + (settled ? "" : ", có bản lưu mới trong lúc gửi -> giữ chờ gửi"));
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
                            // Không ghi lại `item`: người dùng có thể đã lưu bản sửa trong lúc gửi.
                            if (!requireRepository().markBM2505Synced(item, newData.getId()))
                                Logger.appendLog("BM2505", "Có bản lưu mới trong lúc gửi -> giữ chờ gửi, localId="
                                        + item.getLocalId());
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
            // SYNC BM2506 / BM2509 (BM2506_BM2509_ANDROID.md mục 4, 5)
            // =====================
            startSyncTask("bm2506", () -> {
                for (BM2506 item : requireRepository().getModifiedBM2506()) {
                    String sent = item.getJsonData();
                    BM2506Model model = item.toModel();
                    if (item.getId() > 0)
                        model.setId(item.getId());
                    HttpResponse r = requireHttpClient().postBM2506(model);
                    int code = r.getResponseCode();
                    if (model.isDeleted()) {
                        if (code == 200 || code == 404 || code == 410)
                            requireRepository().deleteLocalBM2506(item.getLocalId());
                    } else if (code == 200) {
                        BM2506Model saved = BM2506Model.fromJson(r.getData());
                        // UniqueId là của máy: response thiếu trường này thì Gson để lại UUID ngẫu
                        // nhiên của constructor, lần gửi sau server không nhận ra phiếu nữa
                        saved.setUniqueId(model.getUniqueId());
                        // người dùng sửa trong lúc đang gửi: chỉ gắn id, giữ bản local để lượt sau gửi tiếp
                        requireRepository().markBM2506Posted(item.getLocalId(), sent, BM2506.fromModel(saved));
                    } else if (code == 410) {
                        requireRepository().deleteLocalBM2506(item.getLocalId());
                        Logger.appendLog("BM2506", "Phiếu đã bị xoá trên server: " + model.getUniqueId());
                    } else if (code == 400 || code == 403 || code == 404) {
                        // gửi lại y nguyên vẫn lỗi -> dừng, hiện Message cho người dùng sửa
                        model.setSyncError(formErrorMessage(r));
                        requireRepository().markBM2506Rejected(item.getLocalId(), sent, model.toJson());
                    } // mất mạng / 401 / 5xx: giữ nguyên, lượt sau gửi lại (an toàn nhờ UniqueId)
                }
                Date[] range = formSyncRange();
                List<BM2506Model> lstModel = requireHttpClient().getBM2506List(range[0], range[1]);
                if (lstModel != null)
                    for (BM2506Model model : lstModel)
                        requireRepository().mergeRemoteBM2506(BM2506.fromModel(model));
            });
            startSyncTask("bm2509", () -> {
                for (BM2509 item : requireRepository().getModifiedBM2509()) {
                    String sent = item.getJsonData();
                    BM2509Model model = item.toModel();
                    if (item.getId() > 0)
                        model.setId(item.getId());
                    HttpResponse r = requireHttpClient().postBM2509(model);
                    int code = r.getResponseCode();
                    if (model.isDeleted()) {
                        if (code == 200 || code == 404 || code == 410)
                            requireRepository().deleteLocalBM2509(item.getLocalId());
                    } else if (code == 200) {
                        // [7], [11] lấy theo response của server
                        BM2509Model saved = BM2509Model.fromJson(r.getData());
                        saved.setUniqueId(model.getUniqueId()); // xem BM2506 ở trên
                        requireRepository().markBM2509Posted(item.getLocalId(), sent, BM2509.fromModel(saved));
                    } else if (code == 410) {
                        requireRepository().deleteLocalBM2509(item.getLocalId());
                        Logger.appendLog("BM2509", "Phiếu đã bị xoá trên server: " + model.getUniqueId());
                    } else if (code == 400 || code == 403 || code == 404) {
                        model.setSyncError(formErrorMessage(r));
                        requireRepository().markBM2509Rejected(item.getLocalId(), sent, model.toJson());
                    }
                }
                Date[] range = formSyncRange();
                List<BM2509Model> lstModel = requireHttpClient().getBM2509List(range[0], range[1]);
                if (lstModel != null)
                    for (BM2509Model model : lstModel)
                        requireRepository().mergeRemoteBM2509(BM2509.fromModel(model));
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
                            // Không ghi lại `item`: người dùng có thể đã lưu bản sửa trong lúc gửi.
                            if (!requireRepository().markBM2503Synced(item, newData.getId()))
                                Logger.appendLog("BM2503", "Có bản lưu mới trong lúc gửi -> giữ chờ gửi, localId="
                                        + item.getLocalId());
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
                            // Không ghi lại `item`: người dùng có thể đã lưu bản sửa trong lúc gửi.
                            if (!requireRepository().markBM2504Synced(item, newData.getId()))
                                Logger.appendLog("BM2504", "Có bản lưu mới trong lúc gửi -> giữ chờ gửi, localId="
                                        + item.getLocalId());
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
                            // Không ghi lại `item`: người dùng có thể đã lưu bản sửa trong lúc gửi.
                            if (!requireRepository().markCheckTrucksSynced(item, newData.getId()))
                                Logger.appendLog("BM2307A", "Có bản lưu mới trong lúc gửi -> giữ chờ gửi, localId="
                                        + item.getLocalId());
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
            // sync BM 75.01 (phiếu yêu cầu hút nhiên liệu)
            startSyncTask("bm7501", DataHelper::syncBM7501);

            //sync BM2508
            startSyncTask("bm2508", () -> {

                List<BM2508> modified = requireRepository().getModifiedBM2508();
                if (modified.size() > 0) {
                    for (BM2508 item : modified) {
                        BM2508Model itemData = item.toModel();
                        BM2508Model newData = requireHttpClient().postBM2508Post2(itemData);
                        if (newData != null) {
                            // post2 gửi kèm chữ ký trong cùng gói, không còn lượt gửi ảnh riêng.
                            // Không ghi lại `item`: người dùng có thể đã lưu bản sửa trong lúc gửi.
                            if (!requireRepository().markBM2508Synced(item, newData.getId()))
                                Logger.appendLog("BM2508", "Có bản lưu mới trong lúc gửi -> giữ chờ gửi, localId="
                                        + item.getLocalId());
                        }
                    }
                }

                // Cờ ảnh chờ gửi của bản cũ (hàng đợi api/bm2508/multipart, server trả 400 kể cả
                // khi Id đúng). Ảnh đã đi theo post2 nên chỉ gỡ cờ, để phiếu không bị kẹt khỏi
                // lượt tải về và khỏi dọn dữ liệu cũ.
                requireRepository().clearBM2508AttachmentPending();

                List<BM2508Model> lstModel = requireHttpClient().getBM2508List();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (BM2508Model model : lstModel) {
                        requireRepository().mergeRemoteBM2508(BM2508.fromModel(model));
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
    /**
     * Số xe của máy, hoặc null khi chưa đọc được.
     *
     * <p>Đi qua SharedPreferences và Firebase Remote Config, cả hai đều có thể chưa sẵn sàng
     * (khởi động sớm, môi trường test). Đường ghi xử lý trạng thái này theo fail-closed: chưa
     * biết chắc xe sở hữu thì giữ dữ liệu trong Room, không POST nhầm dữ liệu của xe khác.
     */
    private static String currentTruckNoSafe() {
        try {
            return FMSApplication.getApplication().getTruckNo();
        } catch (Exception | NoClassDefFoundError ex) {
            return null;
        }
    }

    private static int currentTruckIdSafe() {
        try {
            return FMSApplication.getApplication().getTruckId();
        } catch (Exception | NoClassDefFoundError ex) {
            return 0;
        }
    }

    private static String normalizeTruckNo(String value) {
        return value == null ? "" : value.trim();
    }

    private enum TruckOwnership { CURRENT, FOREIGN, UNKNOWN }

    /**
     * So cả số xe lẫn id theo kiểu ba trạng thái. Hai định danh cho kết quả trái nhau là
     * UNKNOWN (dữ liệu lỗi), tuyệt đối không được suy thành FOREIGN rồi full-replace.
     */
    private static TruckOwnership classifyTruck(
            RefuelItemData item, String ownTruck, int ownTruckId) {
        if (item == null) return TruckOwnership.UNKNOWN;

        return classifyTruckIdentity(
                item.getTruckNo(), item.getTruckId(), ownTruck, ownTruckId);
    }

    private static TruckOwnership classifyTruckIdentity(
            String truckNo, int truckId, String ownTruck, int ownTruckId) {

        String ownNo = normalizeTruckNo(ownTruck);
        String itemNo = normalizeTruckNo(truckNo);
        Boolean numberMatches = !ownNo.isEmpty() && !itemNo.isEmpty()
                ? itemNo.equalsIgnoreCase(ownNo) : null;
        Boolean idMatches = ownTruckId > 0 && truckId > 0
                ? truckId == ownTruckId : null;

        if (numberMatches != null && idMatches != null
                && !numberMatches.equals(idMatches)) {
            return TruckOwnership.UNKNOWN;
        }
        Boolean match = numberMatches != null ? numberMatches : idMatches;
        if (match == null) return TruckOwnership.UNKNOWN;
        return match ? TruckOwnership.CURRENT : TruckOwnership.FOREIGN;
    }

    /**
     * Row legacy có thể lệch giữa jsonData và cột Room. Hai nguồn nói khác nhau phải
     * UNKNOWN để mọi đường ghi fail-closed; không được chọn nguồn thuận tiện rồi POST nhầm.
     */
    private static TruckOwnership classifyStoredTruck(
            RefuelItem item, String ownTruck, int ownTruckId) {
        if (item == null) return TruckOwnership.UNKNOWN;

        TruckOwnership columns = classifyTruckIdentity(
                item.getTruckNo(), item.getTruckId(), ownTruck, ownTruckId);
        TruckOwnership payload;
        try {
            payload = classifyTruck(item.toRefuelItemData(), ownTruck, ownTruckId);
        } catch (RuntimeException ex) {
            return TruckOwnership.UNKNOWN;
        }

        if (columns != TruckOwnership.UNKNOWN && payload != TruckOwnership.UNKNOWN
                && columns != payload)
            return TruckOwnership.UNKNOWN;
        return payload != TruckOwnership.UNKNOWN ? payload : columns;
    }

    private enum RefuelWriteAccess { CURRENT, REMOTE_REPLICA, INVALID_OWNERSHIP }

    private static RefuelItem findStoredRefuel(RefuelItemData item) {
        if (item == null) return null;
        RefuelItem stored = null;
        if (item.getUniqueId() != null && !item.getUniqueId().isEmpty())
            stored = requireRepository().getRefuel(item.getUniqueId());
        if (stored == null)
            stored = requireRepository().getRefuel(item.getId(), item.getLocalId());
        return stored;
    }

    /**
     * Quyền ghi fail-closed. Cả snapshot gọi vào và row đang lưu phải thuộc CURRENT; marker
     * replica không bao giờ được client/UI tự gỡ, kể cả snapshot cũ vẫn mang số xe hiện tại.
     */
    private static RefuelWriteAccess classifyWriteAccess(
            RefuelItemData item, String ownTruck, int ownTruckId) {
        TruckOwnership incoming = classifyTruck(item, ownTruck, ownTruckId);
        RefuelItem stored = findStoredRefuel(item);

        if (stored != null) {
            TruckOwnership storedOwnership = classifyStoredTruck(
                    stored, ownTruck, ownTruckId);
            boolean storedReadOnly = stored.isRemoteReplica()
                    || storedOwnership == TruckOwnership.FOREIGN;
            if (storedReadOnly) {
                // Snapshot foreign khớp row foreign là skip batch bình thường. Snapshot cũ
                // còn tự nhận CURRENT trong khi Room đã đổi thành replica là ROLE_CHANGED,
                // phải báo lỗi để UI reload thay vì giả vờ lưu thành công.
                return incoming == TruckOwnership.FOREIGN
                        ? RefuelWriteAccess.REMOTE_REPLICA
                        : RefuelWriteAccess.INVALID_OWNERSHIP;
            }
            if (storedOwnership != TruckOwnership.CURRENT
                    || incoming != TruckOwnership.CURRENT)
                return RefuelWriteAccess.INVALID_OWNERSHIP;
            return RefuelWriteAccess.CURRENT;
        }

        if (incoming == TruckOwnership.CURRENT) return RefuelWriteAccess.CURRENT;
        if (incoming == TruckOwnership.FOREIGN) return RefuelWriteAccess.REMOTE_REPLICA;
        return RefuelWriteAccess.INVALID_OWNERSHIP;
    }

    /** Từ chối một đường ghi bất thường; normal batch replica đã được lọc trước và không log. */
    private static RefuelItemData rejectRefuelWrite(
            RefuelItemData attempted, RefuelWriteAccess access, String source) {
        RefuelItem stored = findStoredRefuel(attempted);
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=REFUEL_WRITE_REJECTED uid=%s reason=%s source=%s thread=%s",
                attempted == null ? "null" : attempted.getUniqueId(), access, source,
                Thread.currentThread().getName()));
        RefuelItemData rejected = stored == null ? attempted : stored.toRefuelItemData();
        if (rejected != null)
            rejected.setSaveOutcome(RefuelItemData.SAVE_OUTCOME.FAILED);
        return rejected;
    }

    /**
     * Xác minh response thực sự thuộc đúng request trước khi nhận ACK hoặc metadata.
     * Applied=true không đủ: proxy/backend trả nhầm payload vẫn có thể mang cờ này.
     */
    private static String validatePostResponseIdentity(
            RefuelItemData request, RefuelItemData response,
            String ownTruck, int ownTruckId) {
        if (response == null) return null;
        if (request == null) return "REQUEST_NULL";

        String requestUid = request.getUniqueId();
        String responseUid = response.getUniqueId();
        if (requestUid == null || requestUid.isEmpty()
                || responseUid == null || !requestUid.equals(responseUid)) {
            return "UID_MISMATCH requested=" + requestUid + " actual=" + responseUid;
        }

        int requestId = request.getId() == null ? 0 : request.getId();
        int responseId = response.getId() == null ? 0 : response.getId();
        if (requestId > 0 && responseId != requestId) {
            return "ID_MISMATCH requested=" + requestId + " actual=" + responseId;
        }

        TruckOwnership responseOwnership = classifyTruck(response, ownTruck, ownTruckId);
        if (responseOwnership != TruckOwnership.CURRENT) {
            return "OWNERSHIP_" + responseOwnership;
        }
        return null;
    }

    private static void logRejectedPostResponse(
            String source, RefuelItemData request, RefuelItemData response, String reason) {
        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=REFUEL_POST_RESPONSE_REJECTED source=%s reason=%s "
                        + "requestUid=%s responseUid=%s responseId=%d thread=%s",
                source, reason,
                request == null ? "null" : request.getUniqueId(),
                response == null ? "null" : response.getUniqueId(),
                response == null || response.getId() == null ? 0 : response.getId(),
                Thread.currentThread().getName()));
    }

    /** Hàng đợi nền chỉ được gửi row chắc chắn thuộc xe hiện tại. */
    private static boolean isWritableCurrentTruckItem(
            RefuelItem item, String ownTruck, int ownTruckId) {
        return item != null
                && !item.isRemoteReplica()
                && classifyStoredTruck(item, ownTruck, ownTruckId)
                == TruckOwnership.CURRENT;
    }

    @androidx.annotation.VisibleForTesting
    static void syncModifiedRefuels() {
        List<RefuelItem> modified = requireRepository().getModifiedRefuel();
        if (modified == null || modified.isEmpty()) return;

        long now = System.currentTimeMillis();

        String ownTruck = currentTruckNoSafe();
        int ownTruckId = currentTruckIdSafe();

        for (RefuelItem item : modified) {

            if (!isWritableCurrentTruckItem(item, ownTruck, ownTruckId)) continue;

            if (shouldDeferVerificationSync(item.getUniqueId(), now)) {
                Logger.appendLog("SYNC", "Defer POST (verification backoff) uid=" + item.getUniqueId());
                continue;
            }

            if (shouldDeferUnconfirmed(item.getUniqueId(), item.getClientSeq(), now)) {
                Logger.appendLog("SYNC", "Defer POST (chờ sau nhiều lần không xác nhận) uid="
                        + item.getUniqueId());
                continue;
            }

            // Server đã từ chối bằng 409/412/423: hoãn theo backoff, và sau khi hết lượt thì
            // DỪNG hẳn việc thử lại tự động. Row vẫn dirty, vẫn nằm trong hàng đợi.
            if (PostConflictPolicy.shared().shouldDefer(
                    item.getUniqueId(), item.getClientSeq(), now)) {
                Logger.appendLog("SYNC", "Defer POST (server từ chối, backoff xung đột) uid="
                        + item.getUniqueId());
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
                    RefuelItem fresh = requireRepository().getRefuel(item.getUniqueId());
                    if (fresh == null)
                        fresh = requireRepository().getRefuel(item.getId(), item.getLocalId());
                    if (fresh == null || !fresh.isLocalModified()) continue;
                    if (!isWritableCurrentTruckItem(fresh, ownTruck, ownTruckId)) continue;
                    item = fresh;
                    itemData = fresh.toRefuelItemData();
                }

                HttpClient.RefuelPostResult postResult =
                        requireHttpClient().postRefuelWithStatus(itemData);
                RefuelItemData newData = postResult == null ? null : postResult.item;
                // Log trước mọi nhánh xử lý — các nhánh bên dưới có continue sớm.
                logPostExchange("BACKGROUND_SYNC", itemData, newData);

                if (newData == null) {
                    handleRejectedRefuelPost(itemData, postResult);
                    continue;
                }

                PostConflictPolicy.shared().clear(itemData.getUniqueId());

                String responseIdentityError = validatePostResponseIdentity(
                        itemData, newData, ownTruck, ownTruckId);
                if (responseIdentityError != null) {
                    // Giữ nguyên row dirty trong hàng đợi; response nhầm không được chạm
                    // identity, revision, giờ hay trạng thái của bản local.
                    logRejectedPostResponse(
                            "BACKGROUND", itemData, newData, responseIdentityError);
                    continue;
                }

                if (newData.getStatus() == REFUEL_ITEM_STATUS.DONE
                        && itemData.getStatus() != REFUEL_ITEM_STATUS.DONE
                        && finalRefuelValuesChanged(newData, itemData)) {
                    logFinalRefuelChange("BACKGROUND_SYNC", newData, itemData,
                            "SERVER_REJECTED_DOWNGRADE");
                }

                synchronized (REFUEL_WRITE_LOCK) {
                    // Re-read after HTTP: an Activity may have saved a newer
                    // snapshot while this request was in flight.
                    RefuelItem newestLocal = requireRepository().getRefuel(itemData.getUniqueId());
                    if (newestLocal == null)
                        newestLocal = requireRepository().getRefuel(
                                item.getId(), item.getLocalId());
                    if (newestLocal == null) {
                        newestLocal = item;
                    }

                    // Server pull có thể đổi quyền sở hữu trong lúc POST bay. Response của
                    // request cũ không được sửa metadata/dirty flag của replica vừa thay thế.
                    if (!isWritableCurrentTruckItem(newestLocal, ownTruck, ownTruckId)) {
                        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                                "event=REFUEL_POST_RESPONSE_DROPPED uid=%s source=BACKGROUND reason=ROLE_CHANGED thread=%s",
                                itemData.getUniqueId(), Thread.currentThread().getName()));
                        continue;
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

    /**
     * Server trả mã TỪ CHỐI (409 conflict / 412 precondition / 423 locked) thay vì dữ liệu.
     *
     * <p>Trước đây mọi mã khác 200 đều rơi vào {@code newData == null} rồi {@code continue};
     * row vẫn dirty nên vòng đồng bộ POST LẠI MÃI mà không ai biết vì sao.
     *
     * <p><b>KHÔNG bao giờ đặt {@code postStatus = ERROR}</b> — row phải ở lại hàng đợi. Ở đây
     * chỉ ghi vết, hoãn theo backoff, và đối chiếu một lần bằng GET (chỉ đọc) để biết dữ liệu
     * thực ra đã lên server hay chưa. Sau {@link PostConflictPolicy#MAX_AUTO_RETRIES} lượt cho
     * cùng một phiên bản, việc thử lại tự động DỪNG hẳn (điểm hội tụ) và chờ người dùng; người
     * dùng sửa tiếp hoặc khởi động lại app là bắt đầu lại.
     */
    private static void handleRejectedRefuelPost(RefuelItemData itemData,
                                                 HttpClient.RefuelPostResult result) {
        if (itemData == null || result == null) return;
        if (!PostConflictPolicy.isConflictCode(result.responseCode)) return;

        String uid = itemData.getUniqueId();
        PostConflictPolicy policy = PostConflictPolicy.shared();
        PostConflictPolicy.Action action = policy.onConflict(
                uid, itemData.getClientSeq(), result.responseCode, System.currentTimeMillis());

        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=REFUEL_POST_REJECTED uid=%s http=%d seq=%d attempts=%d action=%s body=%s",
                uid, result.responseCode, itemData.getClientSeq(), policy.attempts(uid),
                action, result.errorBody == null ? "" : result.errorBody));

        if (action == PostConflictPolicy.Action.VERIFY_AND_BACKOFF)
            verifyInconclusivePost(uid, itemData, null, itemData.getClientSeq());
    }

    public static void postRefuels(List<RefuelItemData> refuels) {
        postRefuels(refuels, false);
    }

    /**
     * @return true khi MỌI phiếu trong danh sách đã vào được Room. False nghĩa là có ít nhất
     * một phiếu bị chặn hoặc lỗi — màn hình gọi phải báo cho người dùng, không được coi như
     * đã lưu xong.
     */
    public static boolean postRefuels(List<RefuelItemData> refuels, boolean remotePost) {
        boolean allCommitted = true;
        try {
            String ownTruck = currentTruckNoSafe();
            int ownTruckId = currentTruckIdSafe();
            for (RefuelItemData item : refuels) {
                // allItems của Preview chứa cả xe hiện tại và xe khác để ghép phiếu. Xe khác
                // là server replica read-only: bỏ qua im lặng ở đường batch bình thường.
                RefuelWriteAccess access = classifyWriteAccess(item, ownTruck, ownTruckId);
                if (access == RefuelWriteAccess.REMOTE_REPLICA) continue;
                if (access != RefuelWriteAccess.CURRENT) {
                    allCommitted = false;
                    Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                            "event=REFUEL_WRITE_OWNERSHIP_INVALID uid=%s source=BATCH thread=%s",
                            item == null ? "null" : item.getUniqueId(),
                            Thread.currentThread().getName()));
                    continue;
                }
                if (!RefuelItemData.isCommitted(postRefuel(item, remotePost))) {
                    allCommitted = false;
                    Logger.appendLog("DTH", "postRefuels: chưa lưu được uid=" + item.getUniqueId());
                }
            }
        } catch (Exception ex) {
            allCommitted = false;
            Logger.appendLog("DTH", ex.getMessage());
        }
        Synchronize();
        return allCommitted;
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
    /**
     * Số phiếu bị ghi lại jsonData trong lượt pull đang chạy. Chỉ đọc/ghi trong luồng sync
     * core, dưới {@link #REFUEL_WRITE_LOCK}.
     */
    private static int rewrittenRowsInBatch = 0;

    /** Mở một lượt pull mới: đặt lại bộ đếm. */
    private static void beginPullLogBatch() {
        rewrittenRowsInBatch = 0;
    }

    /** Đóng lượt pull: một dòng tổng thay cho hàng trăm dòng từng phiếu. */
    private static void endPullLogBatch(String source) {
        if (rewrittenRowsInBatch > 0)
            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s JSON_REWRITTEN %d phiếu", source, rewrittenRowsInBatch));
        rewrittenRowsInBatch = 0;
    }

    private static CurrentMergeResult applyRemoteToLocal(
            String source, RefuelItem localItem, RefuelItemData remote) {
        return applyRemoteToLocal(source, localItem, remote, false);
    }

    /**
     * @param adoptMeasuredTimes nhận cả giờ bắt đầu/kết thúc của bản server. Chỉ đúng cho mẻ do
     *                           XE KHÁC thực hiện và đã chốt: máy này không đo mẻ đó nên không
     *                           có gì để bảo vệ, còn giá trị local chỉ là giờ lúc phân xe.
     */
    private static CurrentMergeResult applyRemoteToLocal(
            String source, RefuelItem localItem, RefuelItemData remote,
            boolean adoptMeasuredTimes) {
        if (localItem == null || remote == null) return CurrentMergeResult.FAILED;

        RefuelItemData before = localItem.toRefuelItemData();

        boolean localDirty = localItem.isLocalModified();

        // MẺ ĐANG BƠM TRÊN CHÍNH MÁY NÀY. Đồng hồ là nguồn chuẩn duy nhất của số liệu mẻ lúc
        // này; bản server chỉ là ảnh chụp của một lần POST trước đó nên luôn CŨ HƠN.
        //
        // Cờ dirty không đủ để chặn: mỗi autosave đặt dirty, rồi POST nền ACK xong lại xoá.
        // Giữa hai nhịp đó có khe row SẠCH, và một lượt pull rơi đúng khe ấy sẽ phủ TOÀN BỘ
        // khoá server gửi lên row — gồm cả nhóm client sở hữu (RealAmount, Gallon, StartNumber,
        // EndNumber, Density...). Đó chính là hình dạng sự cố "1110 bị 862 ghi đè".
        //
        // Hậu quả nặng hơn mất số: jsonData đổi ở nhóm client sở hữu ⇒ vân tay nghiệp vụ đổi
        // ⇒ nền của màn hình không còn khớp row ⇒ MỌI lần lưu sau đều CONFLICT, và
        // rebaseScreenOnStored từ chối vì đúng là "xung đột thật". Màn hình kẹt vĩnh viễn,
        // autosave hỏng mỗi giây một lần và KHÔNG CHỐT ĐƯỢC MẺ.
        // Đo trên xe thật 27-08-2026 18:28: chuyến CX 048, uid 4f9de888, 2359 GL không ghi được.
        //
        // Màn hình xem trước tự khoá đồng bộ khi mở; màn tra nạp thì không, nên phải chặn ở
        // đây. Chặn theo TRẠNG THÁI chứ không khoá cả lượt sync: mẻ vẫn cần được đẩy lên server
        // trong lúc bơm để điều độ nhìn thấy.
        boolean batchRunningHere = localItem.getStatus()
                == RefuelItem.REFUEL_ITEM_STATUS.PROCESSING;

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
        boolean higherServerRevision = remote.getServerRevision() > 0
                && remote.getServerRevision() > localItem.getServerRevision();
        boolean remoteDateIsBehind = !higherServerRevision
                && localItem.getDateUpdated() != null
                && remote.getDateUpdated() != null
                && remote.getDateUpdated().before(localItem.getDateUpdated());
        boolean remoteSeqIsBehind = remote.getClientSeq() > 0
                && remote.getClientSeq() < localItem.getClientSeq();
        boolean remoteBehindLocal = remoteSeqIsBehind
                || remote.getServerRevision() < localItem.getServerRevision()
                || remoteDateIsBehind;
        boolean adoptClientOwned =
                !localDirty && !statusDowngrade && !remoteBehindLocal && !batchRunningHere;

        String jsonBeforeMerge = localItem.getJsonData();

        RefuelItemData merged = RefuelSyncGuard.applyRemote(localItem, remote, adoptClientOwned,
                adoptMeasuredTimes, keepLocalKeysFor(localItem.getUniqueId()));
        if (merged == null) {
            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s MERGE_FAILED uid=%s — giữ nguyên bản local",
                    source, localItem.getUniqueId()));
            return CurrentMergeResult.FAILED;
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

        // Ghi Ở MỨC KHOÁ mọi thay đổi mà lượt nhận này gây ra cho jsonData. Chính chỗ này
        // trước đây im lặng: bản pull ghi đè jsonData mỗi 30 giây, làm baseline của các màn
        // hình đang mở hết hiệu lực, mà log chỉ nói "KEEP_LOCAL_VALUES" nên không ai lần ra.
        String rewroteServerKeys = RefuelSyncGuard.describeJsonDiff(
                jsonBeforeMerge, localItem.getJsonData(), true);
        String rewroteClientKeys = RefuelSyncGuard.describeJsonDiff(
                jsonBeforeMerge, localItem.getJsonData(), false);
        if (rewroteServerKeys != null || rewroteClientKeys != null) {
            // Một lượt pull chạm cả trăm phiếu; in một dòng cho mỗi phiếu làm log không đọc
            // được nữa và đẩy phần đáng chú ý ra khỏi tầm nhìn (đo trên máy thật 18-08).
            // Row sạch nhận bản server là đường đi BÌNH THƯỜNG — chỉ đếm. Chỉ in chi tiết
            // khi lượt nhận này KHÔNG nhận nhóm client sở hữu, tức là có thay đổi local
            // đang bị giữ lại: đó mới là ca cần lần ra khi mất dữ liệu.
            rewrittenRowsInBatch++;
            if (!adoptClientOwned)
                Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                        "%s JSON_REWRITTEN uid=%s adoptClientOwned=false server=[%s] client=[%s]",
                        source, localItem.getUniqueId(),
                        rewroteServerKeys == null ? "" : rewroteServerKeys,
                        rewroteClientKeys == null ? "" : rewroteClientKeys));
        }

        // Mọi lần KHÔNG nhận nhóm trường client sở hữu đều phải để lại dấu vết. Chính vì
        // nhánh bỏ qua trước đây im lặng mà lỗi mất cập nhật sống được rất lâu.
        if (!adoptClientOwned) {
            if (statusDowngrade)
                logFinalRefuelChange(source, before, remote, "BLOCK_DOWNGRADE");

            Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                    "%s KEEP_LOCAL_VALUES uid=%s reason=%s localSeq=%d localRev=%d remoteRev=%d",
                    source, localItem.getUniqueId(),
                    statusDowngrade ? "LOCAL_DONE_REMOTE_NOT_DONE"
                            : localDirty ? "LOCAL_MODIFIED"
                            : batchRunningHere ? "BATCH_RUNNING_HERE"
                            : "REMOTE_BEHIND_LOCAL_SEQ",
                    // Giá trị TRƯỚC khi trộn: sau khi trộn seq/rev đã lấy max nên in ra
                    // sẽ không còn nói lên được vì sao quyết định như vậy.
                    before.getClientSeq(), before.getServerRevision(),
                    remote.getServerRevision()));
        }
        // KEPT_NEWER nói về quyền dùng payload root cho Flight/Others. Các trường
        // server-owned an toàn ở trên vẫn đã được trộn vào row hiện tại.
        return remoteBehindLocal
                ? CurrentMergeResult.KEPT_NEWER : CurrentMergeResult.APPLIED;
    }

    /** Số lần POST liên tiếp không xác nhận được, theo uniqueId — chặn vòng lặp retry vô hạn. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> conflictStreak =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int MAX_UNCONFIRMED_POSTS = 3;

    /**
     * Hoãn gửi cho các row POST mãi không xác nhận được, theo uniqueId:
     * {attempts, nextAttemptAt, clientSeq}.
     *
     * <p>Thay cho cách cũ là đánh {@code postStatus = ERROR}. Row vẫn nằm trong hàng đợi —
     * dữ liệu người dùng không bị bỏ rơi — nhưng không quay vòng liên tục với server.
     * Chỉ nằm trong RAM: khởi động lại app thì thử lại ngay, đúng thứ ta muốn.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> unconfirmedBackoff =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final long[] UNCONFIRMED_BACKOFF_MS = {
            60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L};

    private static void noteUnconfirmedBackoff(String uniqueId, long clientSeq, int streak) {
        if (uniqueId == null || uniqueId.isEmpty()) return;

        int idx = Math.min(Math.max(streak - MAX_UNCONFIRMED_POSTS, 0),
                UNCONFIRMED_BACKOFF_MS.length - 1);
        unconfirmedBackoff.put(uniqueId, new long[]{
                streak, System.currentTimeMillis() + UNCONFIRMED_BACKOFF_MS[idx], clientSeq});

        Logger.appendLog("SYNC", String.format(java.util.Locale.US,
                "UNCONFIRMED_BACKOFF uid=%s streak=%d hoãn=%dphút seq=%d",
                uniqueId, streak, UNCONFIRMED_BACKOFF_MS[idx] / 60_000L, clientSeq));
    }

    /**
     * Row đang trong thời gian hoãn thì bỏ qua lượt này — TRỪ KHI người dùng đã sửa tiếp:
     * phiên bản mới chưa hề thất bại lần nào nên phải được gửi ngay.
     */
    @androidx.annotation.VisibleForTesting
    static boolean shouldDeferUnconfirmed(String uniqueId, long currentClientSeq, long now) {
        if (uniqueId == null || uniqueId.isEmpty()) return false;

        long[] state = unconfirmedBackoff.get(uniqueId);
        if (state == null) return false;
        if (state[2] != currentClientSeq) {
            unconfirmedBackoff.remove(uniqueId);
            return false;
        }
        return now < state[1];
    }

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
        String identityError = validatePostResponseIdentity(
                request, verification, currentTruckNoSafe(), currentTruckIdSafe());
        if (identityError != null) {
            logRejectedPostResponse("VERIFY_GET", request, verification, identityError);
        }

        String outcome;
        if (verification == null) {
            outcome = "GET_FAILED";
        } else if (identityError != null) {
            outcome = "IDENTITY_INVALID " + identityError;
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
            if (!isWritableCurrentTruckItem(
                    row, currentTruckNoSafe(), currentTruckIdSafe())) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=REFUEL_VERIFY_DROPPED uid=%s reason=ROLE_CHANGED thread=%s",
                        uniqueId, Thread.currentThread().getName()));
                verificationState.remove(uniqueId);
                unconfirmedBackoff.remove(uniqueId);
                return;
            }

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
     * Hết lượt xác thực mà vẫn không kết luận được.
     *
     * <p>Trước đây chỗ này đánh {@code postStatus = ERROR} để row rời hàng đợi. Đó là mất
     * dữ liệu có hệ thống: trạng thái nằm trong DB nên khởi động lại app cũng không cứu, và
     * người dùng không có cách nào biết phiếu của mình chưa lên server. Nay row Ở LẠI hàng
     * đợi, chỉ bị hoãn theo backoff luỹ tiến.
     */
    private static void markExhaustedVerification(String uniqueId) {
        long[] state = verificationState.get(uniqueId);
        if (state == null || state[0] < MAX_VERIFICATION_ATTEMPTS) return;

        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem row = requireRepository().getRefuel(uniqueId);
            if (row == null) return;
            if (!isWritableCurrentTruckItem(
                    row, currentTruckNoSafe(), currentTruckIdSafe())) {
                verificationState.remove(uniqueId);
                unconfirmedBackoff.remove(uniqueId);
                return;
            }

            // Người dùng đã sửa tiếp sau khi POST: phiên bản mới chưa hề thất bại lần nào,
            // không được hoãn nó.
            if (row.getClientSeq() != state[2]) {
                verificationState.remove(uniqueId);
                unconfirmedBackoff.remove(uniqueId);
                Logger.appendLog("SYNC", "Bỏ verification state cũ, row đã có phiên bản mới uid="
                        + uniqueId);
                return;
            }

            row.setPostStatus(RefuelItem.ITEM_POST_STATUS.NONE);
            row.setLocalModified(true);
            requireRepository().insertRefuel(row);
            noteUnconfirmedBackoff(uniqueId, row.getClientSeq(), (int) state[0]
                    + MAX_UNCONFIRMED_POSTS);

            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=VERIFICATION_EXHAUSTED uid=%s attempts=%d localId=%d "
                            + "action=GIU_TRONG_HANG_DOI_CO_BACKOFF",
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

        // Thế kẹt giờ: nhường cho server rồi rời hàng đợi, thay vì gửi lại vô hạn.
        if (adoptServerMeasuredTime(localItem, request, response)) return;

        String uid = localItem.getUniqueId();
        // Khoá nào server đã trả về đúng giá trị ta gửi thì không còn gì phải giữ.
        forgetConfirmedUserFieldEdits(request, response);
        String reason = RefuelSyncGuard.describeAck(request, response);

        // Giới hạn projection của server (phiếu cũ trả RealAmount = 0): không kết luận được
        // là đã ghi hay chưa. Giữ row ở hàng đợi để lượt sau thử lại, nhưng KHÔNG tính vào
        // streak — nếu tính, toàn bộ phiếu cũ sẽ bị đẩy vào ERROR hàng loạt.
        boolean inconclusive = RefuelSyncGuard.isInconclusive(reason);
        int streak = inconclusive || uid == null
                ? 0
                : conflictStreak.merge(uid, 1, Integer::sum);

        localItem.setLocalModified(true);

        // KHÔNG đẩy sang ERROR. postStatus = ERROR loại row khỏi hàng đợi tự động và trạng
        // thái đó nằm trong DB nên khởi động lại app cũng không cứu được: dữ liệu người dùng
        // đứng lại vĩnh viễn ở máy mà không ai biết. Thay vào đó row ở lại hàng đợi dưới
        // dạng CHỜ, có backoff luỹ tiến để không quay vòng với server, và postStatus giữ
        // NONE nên màn hình hiển thị đúng là "chưa gửi được" thay vì báo thành công.
        localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.NONE);
        if (streak >= MAX_UNCONFIRMED_POSTS)
            noteUnconfirmedBackoff(uid, localItem.getClientSeq(), streak);

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
     * Các khoá NGƯỜI DÙNG đã sửa mà server chưa xác nhận, theo uniqueId.
     *
     * <p>Nguyên tắc: thứ vừa sửa trên máy là MỚI NHẤT. Bản server cho đúng những khoá này
     * chắc chắn cũ hơn — chính lượt lưu đó làm row thành dirty — nên lượt đọc lại không được
     * phủ chúng lên. Chỉ chặn ĐÚNG các khoá đang chờ, nên thay đổi của điều độ ở mọi khoá
     * khác vẫn về xe bình thường.
     *
     * <p>Chỉ xét nhóm server/shared: nhóm client sở hữu vốn đã được nhánh giữ-local bảo vệ.
     *
     * <p>Chỉ nằm trong RAM; mất dấu sau khi khởi động lại app chỉ đưa hành vi về như trước
     * bản vá, không tạo hỏng mới.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>>
            pendingUserFieldEdits = new java.util.concurrent.ConcurrentHashMap<>();

    /** Ghi nhận những khoá server-sở-hữu mà gói gửi này đang mang giá trị khác nền. */
    private static void rememberUserFieldEdits(RefuelItemData request) {
        if (request == null || request.getUniqueId() == null) return;
        String baseJson = request.getBaseJson();
        if (baseJson == null || baseJson.trim().isEmpty()) return;
        try {
            com.google.gson.JsonObject base =
                    com.google.gson.JsonParser.parseString(baseJson).getAsJsonObject();
            com.google.gson.JsonObject mine =
                    com.google.gson.JsonParser.parseString(request.toJson()).getAsJsonObject();
            java.util.Set<String> changed = new java.util.HashSet<>();
            for (String key : mine.keySet()) {
                if (!RefuelFieldOwnership.isServerOwned(key)) continue;
                if (!RefuelValues.equal(key, base.get(key), mine.get(key))) changed.add(key);
            }
            if (changed.isEmpty()) pendingUserFieldEdits.remove(request.getUniqueId());
            else pendingUserFieldEdits.put(request.getUniqueId(), changed);
        } catch (RuntimeException ignored) {
        }
    }

    /** Bỏ dấu cho những khoá mà bản server đã mang đúng giá trị ta gửi. */
    private static void forgetConfirmedUserFieldEdits(RefuelItemData request,
                                                      RefuelItemData response) {
        if (request == null || response == null || request.getUniqueId() == null) return;
        java.util.Set<String> pending = pendingUserFieldEdits.get(request.getUniqueId());
        if (pending == null || pending.isEmpty()) return;
        try {
            com.google.gson.JsonObject mine =
                    com.google.gson.JsonParser.parseString(request.toJson()).getAsJsonObject();
            com.google.gson.JsonObject theirs =
                    com.google.gson.JsonParser.parseString(response.toJson()).getAsJsonObject();
            pending.removeIf(key -> RefuelValues.equal(key, mine.get(key), theirs.get(key)));
            if (pending.isEmpty()) pendingUserFieldEdits.remove(request.getUniqueId());
        } catch (RuntimeException ignored) {
        }
    }

    @androidx.annotation.VisibleForTesting
    static java.util.Set<String> keepLocalKeysForTesting(String uniqueId) {
        return keepLocalKeysFor(uniqueId);
    }

    private static java.util.Set<String> keepLocalKeysFor(String uniqueId) {
        if (uniqueId == null) return null;
        java.util.Set<String> pending = pendingUserFieldEdits.get(uniqueId);
        return pending == null || pending.isEmpty() ? null : pending;
    }

    /**
     * Mốc giờ NGƯỜI DÙNG đã sửa mà server chưa nhận, theo uniqueId: {startMillis, endMillis}.
     *
     * <p>Chỉ nằm trong RAM. Khởi động lại app thì dấu mất và lượt gửi sau có thể nhường giờ
     * cho server — chấp nhận được: mất dấu chỉ đưa hành vi về đúng như trước bản vá, không
     * tạo ra hỏng mới.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]>
            pendingUserTimeEdit = new java.util.concurrent.ConcurrentHashMap<>();

    private static long millisOf(java.util.Date value) {
        return value == null ? Long.MIN_VALUE : value.getTime();
    }

    /**
     * Gói gửi có mang một mốc giờ do NGƯỜI DÙNG vừa sửa hay không.
     *
     * <p>So với bản nền mà màn hình đọc lúc mở. Khác nền nghĩa là thao tác của người dùng;
     * bằng nền nghĩa là ta chỉ đang gửi lại giá trị cũ và server mới là bên có thay đổi.
     *
     * @return false khi không có nền để so — giữ nguyên hành vi cũ thay vì đoán.
     */
    private static boolean requestChangedMeasuredTime(RefuelItemData request) {
        if (request == null) return false;
        String baseJson = request.getBaseJson();
        if (baseJson == null || baseJson.trim().isEmpty()) return false;
        try {
            com.google.gson.JsonObject base =
                    com.google.gson.JsonParser.parseString(baseJson).getAsJsonObject();
            com.google.gson.JsonObject mine =
                    com.google.gson.JsonParser.parseString(request.toJson()).getAsJsonObject();
            for (String key : new String[]{"StartTime", "EndTime"})
                if (!RefuelValues.equal(key, base.get(key), mine.get(key))) return true;
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    /**
     * Nhận giờ của server khi đó là khác biệt duy nhất còn lại, rồi cho row rời hàng đợi.
     *
     * <p>Chỉ áp dụng cho mẻ ĐÃ CHỐT ở cả hai phía. Mọi trường chốt khác đã khớp nghĩa là dữ
     * liệu ta cần gửi đã nằm trên server; giữ row lại chỉ để tranh chấp một mốc giờ mà server
     * sẽ không bao giờ nhận là quay vòng vô ích — và chặn luôn đường sửa giờ từ web về xe.
     *
     * @return true nếu đã xử lý xong, tầng trên không cần đánh dấu conflict nữa.
     */
    private static boolean adoptServerMeasuredTime(RefuelItem localItem, RefuelItemData request,
                                                   RefuelItemData response) {
        if (request == null || response == null) return false;
        if (response.getStatus() != REFUEL_ITEM_STATUS.DONE) return false;
        if (request.getStatus() != REFUEL_ITEM_STATUS.DONE) return false;
        if (!RefuelSyncGuard.isOnlyMeasuredTimeDiff(request, response)) return false;

        String uid = localItem.getUniqueId();

        // NGƯỜI DÙNG vừa sửa mốc giờ thì bản của họ là ý định mới nhất, và server đang không
        // nhận. Nhường cho server ở đây là âm thầm xoá thứ vừa nhập: đo trên máy thật
        // 27-08-2026, sửa giờ kết thúc 13:44 -> 13:50, server trả lại 13:44, app tự đè về
        // 13:44 rồi xoá cờ dirty — bấm bao nhiêu lần cũng không đổi được mà không báo gì.
        //
        // Hàm này sinh ra cho chiều NGƯỢC LẠI: web sửa giờ, xe nhận về. Chiều đó vẫn chạy vì
        // lúc ấy giờ người dùng không có gì đang chờ.
        //
        // Dấu phải BỀN qua các lần gửi lại. Chỉ so với baseJson là không đủ: ngay sau lần lưu
        // đầu, nền của row đã bao gồm giá trị người dùng nên lần retry từ hàng đợi không còn
        // nhận ra đó là thao tác của họ — đo lúc 14:42:24 chặn được, 14:42:54 retry lại nhường.
        // Phiếu bị server khoá giờ theo thiết kế: giữ bản local ở đây chỉ tạo phân kỳ vĩnh
        // viễn với hệ thống gốc và gửi lại vô ích. Nhận giờ server, nhưng để lại dấu vết
        // riêng để còn phân biệt với một lần từ chối tạm thời.
        if (request.isMeasuredTimeLockedOnServer()) {
            pendingUserTimeEdit.remove(uid);
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=TIME_LOCKED_BY_SERVER uid=%s id=%d localId=%d"
                            + " sentEnd=%s serverEnd=%s status=%s printed=%b receipt=%s",
                    uid, localItem.getId(), localItem.getLocalId(),
                    request.getEndTime(), response.getEndTime(),
                    request.getStatus(), request.isPrinted(), request.getReceiptNumber()));
        }

        long[] wanted = request.isMeasuredTimeLockedOnServer()
                ? null : pendingUserTimeEdit.get(uid);
        if (!request.isMeasuredTimeLockedOnServer() && requestChangedMeasuredTime(request)) {
            wanted = new long[]{millisOf(request.getStartTime()), millisOf(request.getEndTime())};
            pendingUserTimeEdit.put(uid, wanted);
        }
        if (wanted != null) {
            if (wanted[0] == millisOf(response.getStartTime())
                    && wanted[1] == millisOf(response.getEndTime())) {
                // Server đã nhận đúng giờ người dùng muốn: không còn gì để giữ.
                pendingUserTimeEdit.remove(uid);
            } else if (wanted[0] == millisOf(request.getStartTime())
                    && wanted[1] == millisOf(request.getEndTime())) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=SERVER_REJECTED_TIME_EDIT uid=%s id=%d localId=%d"
                                + " sentStart=%s sentEnd=%s serverStart=%s serverEnd=%s",
                        uid, localItem.getId(), localItem.getLocalId(),
                        request.getStartTime(), request.getEndTime(),
                        response.getStartTime(), response.getEndTime()));
                return false;
            } else {
                // Giờ trong gói gửi không còn là thứ người dùng đặt (thiết bị ghi đè, hoặc
                // chính họ đổi tiếp): dấu cũ hết hiệu lực.
                pendingUserTimeEdit.remove(uid);
            }
        }

        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=ADOPT_SERVER_TIME uid=%s id=%d localId=%d"
                        + " startTime(%s->%s) endTime(%s->%s) seq=%d rev=%d",
                uid, localItem.getId(), localItem.getLocalId(),
                request.getStartTime(), response.getStartTime(),
                request.getEndTime(), response.getEndTime(),
                request.getClientSeq(), response.getServerRevision()));

        // Đi qua đúng đường trộn dùng chung, có bật nhận mốc giờ. Row đang dirty nên nhánh
        // giữ-nhóm-client vẫn chạy: chỉ trường server sở hữu và hai mốc giờ được phủ lên.
        if (applyRemoteToLocal("ADOPT_SERVER_TIME", localItem, response, true)
                == CurrentMergeResult.FAILED)
            return false;

        localItem.setLocalModified(false);
        localItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
        requireRepository().insertRefuel(localItem);
        clearConflictStreak(uid);

        return true;
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

    /**
     * Lần cuối đã ghi một conflict GIỐNG HỆT, theo uniqueId — chống lặp log.
     *
     * <p>Timer đọc đồng hồ lưu mỗi giây, nên một conflict kéo dài sinh ra hàng trăm dòng
     * anomaly y hệt nhau trong vài phút. Dòng này rất dài (có cả danh sách khoá lệch) và
     * được upload lên server, nên lặp lại như vậy vừa che mất các sự kiện khác vừa tốn băng
     * thông. Chữ ký gồm cả nội dung nên conflict ĐỔI KIỂU vẫn được ghi ngay.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String[]>
            lastConflictSignature = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long CONFLICT_LOG_INTERVAL_MS = 30_000L;

    private static boolean shouldLogConflict(String uniqueId, String signature) {
        if (uniqueId == null) return true;

        long now = System.currentTimeMillis();
        String[] previous = lastConflictSignature.get(uniqueId);   // {signature, at, suppressed}
        if (previous != null && previous[0].equals(signature)
                && now - Long.parseLong(previous[1]) < CONFLICT_LOG_INTERVAL_MS) {
            previous[2] = String.valueOf(Long.parseLong(previous[2]) + 1);
            return false;
        }

        // Bao nhiêu lần đã bị nén — phải nói ra, nếu không việc rate-limit lại thành mất
        // bằng chứng: nhìn log sẽ tưởng conflict chỉ xảy ra vài lần.
        if (previous != null && Long.parseLong(previous[2]) > 0)
            Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                    "event=CONFLICT_LOG_SUPPRESSED uid=%s lặp=%s lần trong %ds",
                    uniqueId, previous[2], CONFLICT_LOG_INTERVAL_MS / 1000));

        lastConflictSignature.put(uniqueId, new String[]{signature, String.valueOf(now), "0"});
        return true;
    }

    private static void logVersionConflict(String source, RefuelItem stored, RefuelItemData incoming,
                                           RefuelSyncGuard.SaveDecision decision) {
        // Khoá nào lệch mới là thứ trả lời được câu "ai sửa?". Thiếu nó thì một lần chặn do
        // chính app tự ghi đè trông y hệt một lần chặn do người khác sửa thật.
        String clientDiff = RefuelSyncGuard.describeJsonDiff(
                stored.getJsonData(), incoming.toJson(), false);
        String serverDiff = RefuelSyncGuard.describeJsonDiff(
                stored.getJsonData(), incoming.toJson(), true);

        if (!shouldLogConflict(stored.getUniqueId(),
                decision + "|" + clientDiff + "|" + serverDiff))
            return;

        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                "event=VERSION_CONFLICT reason=%s source=%s id=%d localId=%d uid=%s "
                        + "storedSeq=%d storedRev=%d baseSeq=%d baseRev=%d "
                        + "storedAmount=%.0f storedEnd=%.0f incomingAmount=%.0f incomingEnd=%.0f "
                        + "clientDiff=[%s] serverDiff=[%s] thread=%s",
                decision, source, stored.getId(), stored.getLocalId(), stored.getUniqueId(),
                stored.getClientSeq(), stored.getServerRevision(),
                incoming.getBaseClientSeq(), incoming.getBaseServerRevision(),
                stored.toRefuelItemData().getRealAmount(), stored.getEndNumber(),
                incoming.getRealAmount(), incoming.getEndNumber(),
                clientDiff == null ? "" : clientDiff,
                serverDiff == null ? "" : serverDiff,
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
            caller.setBaseJson(stored.getJsonData());
        }
        RefuelItemData result = stored.toRefuelItemData();
        result.setSaveOutcome(RefuelItemData.SAVE_OUTCOME.COMMITTED);
        return result;
    }

    /**
     * Đánh dấu kết quả lưu là lần chuyển mẻ sang DONE.
     *
     * <p>Chỉ đúng khi lần ghi NÀY tạo ra chuyển trạng thái. Nhờ vậy caller cập nhật tồn xe
     * được đúng một lần, kể cả khi nút End hay callback thiết bị gọi lưu nhiều lần.
     */
    private static RefuelItemData markTransition(RefuelItemData result, boolean transitioned) {
        if (result != null && transitioned
                && result.getSaveOutcome() == RefuelItemData.SAVE_OUTCOME.COMMITTED)
            result.setTransitionedToDone(true);
        return result;
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
        return finishConflict(stored, false);
    }

    /**
     * Đưa đối tượng của MÀN HÌNH đứng lại lên row hiện tại, sau khi một lần lưu bị chặn.
     *
     * <p>Không có bước này, một conflict duy nhất đầu độc cả mẻ: màn hình giữ mãi baseline
     * cũ nên mọi autosave sau đó đều hỏng, mỗi giây một lần, cho tới khi rời màn hình. Đo
     * trên xe HAN3-20-7006 ngày 25-08-2026: chuyến VN 263 hỏng liên tục 9 phút (~430 lần
     * lưu trượt), chỉ thoát được nhờ đường EndFieldsPatch lúc chốt mẻ.
     *
     * <p>Nguyên nhân là {@link RefuelSyncGuard.SaveDecision#CONFLICT_CLIENT_MOVED}: lượt
     * ghi nền nhích {@code ClientSeq} của row trong khi màn hình đang mở. Seq chỉ là biến
     * đếm — nó nhích cả khi không ai đụng vào số liệu nghiệp vụ.
     *
     * <p>ĐIỀU KIỆN AN TOÀN, không được nới: chỉ rebase khi vân tay payload nghiệp vụ của
     * row vẫn ĐÚNG BẰNG vân tay màn hình đã chốt lần trước. Bằng nhau nghĩa là chưa ai sửa
     * số liệu kể từ đó, nên đứng lên phiên bản mới không ghi đè việc của ai. Khác nhau là
     * xung đột thật — giữ nguyên chặn, đúng như {@link #finishConflict} đã cảnh báo: nâng
     * baseline vô điều kiện sẽ cho chính gói vừa bị từ chối vượt precondition ở lần sau.
     *
     * @return true nếu đã rebase; false nếu là xung đột thật hoặc không tìm thấy row
     */
    public static boolean rebaseScreenOnStored(RefuelItemData screen) {
        if (screen == null) return false;

        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem latest = requireRepository().getRefuel(screen.getId(), screen.getLocalId());
            if (latest == null) return false;

            String storedFingerprint =
                    RefuelSyncGuard.businessFingerprintOfJson(latest.getJsonData());
            String baseFingerprint = screen.getBaseBusinessFingerprint();

            if (baseFingerprint == null || !baseFingerprint.equals(storedFingerprint)) {
                Logger.appendLog("DTH", String.format(java.util.Locale.US,
                        "REBASE_SCREEN_REFUSED uid=%s: payload nghiệp vụ đã đổi, xung đột thật",
                        screen.getUniqueId()));
                return false;
            }

            long oldBaseSeq = screen.getBaseClientSeq();
            int oldBaseRev = screen.getBaseServerRevision();

            screen.setId(latest.getId());
            if (latest.getUniqueId() != null && !latest.getUniqueId().isEmpty())
                screen.setUniqueId(latest.getUniqueId());
            if (latest.getLocalId() > 0) screen.setLocalId(latest.getLocalId());
            screen.setClientSeq(Math.max(screen.getClientSeq(), latest.getClientSeq()));
            screen.setServerRevision(
                    Math.max(screen.getServerRevision(), latest.getServerRevision()));

            screen.setBaseClientSeq(latest.getClientSeq());
            screen.setBaseServerRevision(latest.getServerRevision());
            screen.setBaseBusinessFingerprint(storedFingerprint);
            screen.setBaseJson(latest.getJsonData());

            Logger.appendLog("DTH", String.format(java.util.Locale.US,
                    "REBASE_SCREEN_AFTER_CONFLICT uid=%s baseSeq=%d->%d baseRev=%d->%d",
                    screen.getUniqueId(), oldBaseSeq, latest.getClientSeq(),
                    oldBaseRev, latest.getServerRevision()));
            return true;
        }
    }

    /**
     * @param rejectedLocalSave true khi chính LẦN LƯU LOCAL bị chặn — dữ liệu người dùng
     *                          không vào được Room. Màn hình phải giữ nguyên những gì đang
     *                          nhập và báo lỗi, tuyệt đối không gán đè bản ghi trả về lên đó.
     *                          False cho các nhánh chỉ có POST không xác nhận được: dữ liệu
     *                          local đã lưu an toàn và vẫn nằm trong hàng đợi đồng bộ.
     */
    private static RefuelItemData finishConflict(RefuelItem stored, boolean rejectedLocalSave) {
        if (stored == null) return null;
        RefuelItemData result = stored.toRefuelItemData();
        result.setSaveOutcome(rejectedLocalSave
                ? RefuelItemData.SAVE_OUTCOME.CONFLICT
                : RefuelItemData.SAVE_OUTCOME.COMMITTED);
        return result;
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

    /**
     * Lưu dữ liệu MÀN HÌNH XÁC NHẬN theo kiểu patch, dùng khi lần lưu thường bị precondition
     * từ chối.
     *
     * <p>Đọc row mới nhất DƯỚI KHOÁ GHI rồi mới quyết định, nên không có khe cho một lượt
     * đồng bộ chen vào giữa lúc kiểm tra và lúc ghi. Chỉ những trường người dùng thực sự
     * nhập mới được đắp lên row mới nhất; trường nào cả hai phía cùng đổi thì DỪNG và trả
     * CONFLICT cho màn hình hiển thị — tuyệt đối không lấy "ours wins" làm mặc định.
     *
     * @return kết quả có {@code saveOutcome} tường minh; {@code null} nghĩa là thất bại
     */
    public static RefuelItemData saveConfirmFields(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.CONFIRM, ours, false);
    }

    /**
     * Đường patch của MÀN HÌNH XÁC NHẬN cho mẻ vừa tra nạp hộ (chuyến chưa phân công).
     *
     * <p>Đối xứng với {@link #postRefuelFromRefuelScreen}: người vừa đứng bơm đã quyết định
     * tiếp quản ở màn tra nạp, nhưng bước xác nhận lại đi đường fail-closed nên mẻ có thật
     * KẸT đúng ở bước cuối. Không mở đường này thì lần lưu thường bị chặn xong, lần patch
     * dự phòng cũng bị chặn — mất trắng số liệu.
     *
     * <p>CHỈ nới cho hai lượt {@code postRefuel} bên trong đường patch này. Batch, ACK,
     * {@link #saveEndFields} và {@link #savePreviewFields} giữ nguyên fail-closed.
     */
    public static RefuelItemData saveConfirmFieldsFromRefuelScreen(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.CONFIRM, ours, true);
    }

    /**
     * Đường phục hồi của LUỒNG KẾT THÚC MẺ, đối xứng với {@link #saveConfirmFields}.
     *
     * <p>Không có nó thì một conflict thật lúc End là ngõ cụt: nút "Thử lại" gửi lại đúng
     * baseline cũ nên hỏng mãi mãi, và số liệu mẻ nằm lại trên màn hình cho tới khi mất.
     */
    public static RefuelItemData saveEndFields(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.END, ours, false);
    }

    /**
     * Đường phục hồi của LUỒNG KẾT THÚC MẺ khi lời gọi đến TỪ MÀN TRA NẠP.
     *
     * <p>Nguyên nhân gốc của ngõ cụt "mẻ chưa được lưu": nhịp một của chuỗi chốt mẻ đi
     * {@link #postRefuelFromRefuelScreen} nên CHO PHÉP tiếp quản chuyến chưa phân công, nhưng
     * nhịp hai (đường cứu khi CONFLICT) lại đi {@link #saveEndFields} fail-closed. Mẻ tra nạp
     * hộ gặp xung đột lúc chốt vì thế bị từ chối VĨNH VIỄN — nút "Thử lại" gửi lại đúng lối
     * bị chặn nên hỏng y hệt mọi lần (đo trên xe thật 30-08-2026: 3/3 lần hỏng).
     *
     * <p>Đối xứng với {@link #saveConfirmFieldsFromRefuelScreen}. CHỈ nới cho lời gọi từ màn
     * tra nạp; {@link #saveEndFields}, batch, ACK và {@link #savePreviewFields} giữ nguyên
     * fail-closed.
     */
    public static RefuelItemData saveEndFieldsFromRefuelScreen(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.END, ours, true);
    }

    /**
     * Đường phục hồi của MÀN HÌNH XEM TRƯỚC, đối xứng với {@link #saveConfirmFields}.
     *
     * <p>Màn hình đó lưu bằng full snapshot, nên chỉ cần một lượt pull nền hoặc một hộp thoại
     * khác vừa ghi xong là baseline đã dịch và lần lưu bị trả CONFLICT. Không có đường này
     * thì giá trị người dùng vừa nhập mất hẳn và phải gõ lại.
     */
    public static RefuelItemData savePreviewFields(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.PREVIEW, ours, false);
    }

    /**
     * Đường phục hồi của MÀN HÌNH TRA NẠP, cho các hộp thoại nhập tay.
     *
     * <p>Trong lúc đồng hồ chạy, autosave thiết bị tăng ClientSeq mỗi giây và lượt pull nền
     * ghi lại jsonData mỗi 30 giây. Một hộp thoại mở vài giây là baseline đã dịch, và lần
     * lưu bị trả CONFLICT trong khi hộp thoại đã đóng — giá trị vừa gõ mất hẳn.
     */
    public static RefuelItemData saveDetailFields(RefuelItemData ours) {
        return saveScopedFields(RefuelFieldPatch.Scope.DETAIL, ours, false);
    }

    /**
     * @param refuellingTruckOverride true chỉ khi lời gọi đến TỪ NGƯỜI ĐANG ĐỨNG BƠM. Khi đó
     *                                cả hai lượt {@code postRefuel} bên trong đều dùng lối
     *                                vào tiếp quản, vì chặn ở lượt sau cũng đủ làm mất số
     *                                liệu y như chặn ở lượt trước.
     */
    private static RefuelItemData saveScopedFields(RefuelFieldPatch.Scope scope,
                                                   RefuelItemData ours,
                                                   boolean refuellingTruckOverride) {
        if (ours == null) return null;

        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem latest = requireRepository().getRefuel(ours.getId(), ours.getLocalId());
            if (latest == null) {
                // Chưa có row nào để mà xung đột: đi đường lưu thường.
                return postRefuel(ours, false, refuellingTruckOverride);
            }

            RefuelFieldPatch.Result patch = RefuelFieldPatch.apply(
                    scope, ours.getBaseJson(), ours, latest.getJsonData(),
                    ours.getBaseClientSeq(), latest.getClientSeq());

            Logger.appendLog("DTH", String.format(java.util.Locale.US,
                    "%s_PATCH uid=%s result=%s changed=%s storedSeq=%d baseSeq=%d",
                    scope, latest.getUniqueId(), patch.describe(),
                    RefuelFieldPatch.changedKeys(scope, ours.getBaseJson(), ours),
                    latest.getClientSeq(), ours.getBaseClientSeq()));

            if (!patch.isApplied()) {
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=PATCH_BLOCKED scope=" + scope + " reason=%s uid=%s id=%d localId=%d "
                                + "storedStatus=%s storedAmount=%.0f storedEnd=%.0f "
                                + "incomingAmount=%.0f incomingEnd=%.0f",
                        patch.describe(), latest.getUniqueId(), latest.getId(),
                        latest.getLocalId(), latest.getStatus(),
                        latest.toRefuelItemData().getRealAmount(), latest.getEndNumber(),
                        ours.getRealAmount(), ours.getEndNumber()));

                RefuelItemData blocked = latest.toRefuelItemData();
                blocked.setSaveOutcome(RefuelItemData.SAVE_OUTCOME.CONFLICT);
                return blocked;
            }

            // Payload đã ghép đứng trên đúng row mới nhất, nên baseline của nó là row đó.
            RefuelItemData merged = patch.getMerged();
            merged.setLocalId(latest.getLocalId());
            merged.setBaseClientSeq(latest.getClientSeq());
            merged.setBaseServerRevision(latest.getServerRevision());
            merged.setBaseBusinessFingerprint(
                    RefuelSyncGuard.businessFingerprintOfJson(latest.getJsonData()));
            merged.setBaseJson(latest.getJsonData());

            RefuelItemData saved = postRefuel(merged, false, refuellingTruckOverride);

            // Màn hình phải nhìn thấy đúng thứ đã vào Room, kể cả phần nó không sửa.
            if (RefuelItemData.isCommitted(saved)) {
                if (merged.getId() != null && merged.getId() > 0)
                    ours.setId(merged.getId());
                ours.setLocalId(merged.getLocalId());
                ours.setClientSeq(merged.getClientSeq());
                ours.setServerRevision(merged.getServerRevision());
                ours.setBaseClientSeq(merged.getBaseClientSeq());
                ours.setBaseServerRevision(merged.getBaseServerRevision());
                ours.setBaseBusinessFingerprint(merged.getBaseBusinessFingerprint());
                ours.setBaseJson(merged.getBaseJson());
            }
            return saved;
        }
    }

    public static RefuelItemData postRefuel(RefuelItemData refuelData) {
        RefuelItemData postedItem = postRefuel(refuelData, false);
        // call synchronize to update remote database
        if (postedItem != null && postedItem.getStatus() == REFUEL_ITEM_STATUS.DONE)
            Synchronize();
        return postedItem;
    }

    public static RefuelItemData postRefuel(RefuelItemData refuelData, boolean remotePost) {
        return postRefuel(refuelData, remotePost, false);
    }

    /**
     * Ghi mẻ TỪ MÀN HÌNH TRA NẠP, nơi người dùng đang trực tiếp đứng bơm.
     *
     * <p>Hiện trường có quy trình thật: chuyến điều cho xe A nhưng xe B mở đúng phiếu đó ra
     * và bơm. Guard quyền sở hữu chặn thẳng đường này, và mẻ có thật KHÔNG ghi được — đo trên
     * xe HAN3-20-7002 lúc 27-08-2026 21:39 (3029 GL nhập tay vì LCR chết) và HAN3-20-7012 kẹt
     * liên tục 19:59–20:24.
     *
     * <p>Quyết định nghiệp vụ: ở màn hình này KHÔNG chặn. Người đang đứng tại tàu bay là bên
     * biết rõ nhất mẻ nào vừa được bơm; mất số liệu tệ hơn nhiều so với ghi nhầm chủ sở hữu.
     * Việc bất thường được ghi lại đầy đủ để đối soát sau ca.
     *
     * <p>Lối vào RIÊNG chứ không nới {@link #postRefuel(RefuelItemData, boolean)}: mọi đường
     * nền, batch và ACK vẫn giữ nguyên guard fail-closed, nên snapshot cũ còn sót lại sau khi
     * server đổi xe vẫn không thể gỡ cờ replica.
     */
    public static RefuelItemData postRefuelFromRefuelScreen(
            RefuelItemData refuelData, boolean remotePost) {
        return postRefuel(refuelData, remotePost, true);
    }

    /**
     * Mẻ này có thuộc xe hiện tại hay không, xét cả snapshot đang cầm lẫn row đang lưu.
     *
     * <p>Dùng để màn hình HIỆN CẢNH BÁO KHÔNG CHẶN trước khi ghi bằng lối vào tiếp quản —
     * người dùng cần biết mình đang ghi lên chuyến chưa phân công cho xe. Không dùng để
     * quyết định chặn: quyết định đó nằm trong {@code postRefuel}.
     */
    public static boolean isForeignTruckRefuel(RefuelItemData refuelData) {
        if (refuelData == null) return false;
        synchronized (REFUEL_WRITE_LOCK) {
            return classifyWriteAccess(refuelData, currentTruckNoSafe(), currentTruckIdSafe())
                    != RefuelWriteAccess.CURRENT;
        }
    }

    private static RefuelItemData postRefuel(RefuelItemData refuelData, boolean remotePost,
                                             boolean refuellingTruckOverride) {
        if (refuelData == null) return null;

        String ownTruckForWrite = currentTruckNoSafe();
        int ownTruckIdForWrite = currentTruckIdSafe();
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelWriteAccess access = classifyWriteAccess(
                    refuelData, ownTruckForWrite, ownTruckIdForWrite);
            if (access != RefuelWriteAccess.CURRENT && refuellingTruckOverride) {
                // Không chặn, nhưng phải để lại dấu vết đầy đủ để đối soát sau ca.
                RefuelItem stored = findStoredRefuel(refuelData);
                Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                        "event=REFUEL_TAKEOVER_UNASSIGNED_FLIGHT uid=%s reason=%s"
                                + " thisTruck=%s(%d) storedTruck=%s payloadTruck=%s"
                                + " flight=%s amount=%.0f start=%.0f end=%.0f thread=%s",
                        refuelData.getUniqueId(), access,
                        ownTruckForWrite, ownTruckIdForWrite,
                        stored == null ? "null" : stored.getTruckNo(),
                        refuelData.getTruckNo(), refuelData.getFlightCode(),
                        refuelData.getRealAmount(), refuelData.getStartNumber(),
                        refuelData.getEndNumber(), Thread.currentThread().getName()));
                Logger.appendLog("DTH", "Tra nạp chuyến không được phân công cho xe này —"
                        + " vẫn ghi nhận, uid=" + refuelData.getUniqueId());

                // Xe đang bơm là xe sở hữu mẻ. Đóng dấu lại để các lần ghi sau không vấp
                // guard nữa, và để gói gửi lên server mang đúng xe đã thực hiện.
                refuelData.setTruckId(ownTruckIdForWrite);
                refuelData.setTruckNo(ownTruckForWrite);
                if (stored != null && stored.isRemoteReplica()) {
                    stored.setRemoteReplica(false);
                    stored.setTruckId(ownTruckIdForWrite);
                    stored.setTruckNo(ownTruckForWrite);
                    requireRepository().insertRefuel(stored);
                }
            } else if (access != RefuelWriteAccess.CURRENT) {
                // Đường batch bình thường đã lọc replica im lặng. Chạm direct guard là lỗi
                // lập trình thật nên mới ghi anomaly — không log mỗi lần skip hợp lệ.
                return rejectRefuelWrite(refuelData, access, "DIRECT_ENTRY");
            }
        }

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
            String requestUniqueId = null;
            int requestServerId = 0;
            int requestLocalId = 0;

            // Lần lưu này có phải chính là lần chuyển mẻ sang DONE hay không. Tồn xe chỉ
            // được trừ đúng tại đây, một lần: nút End bấm lại hay callback thiết bị lặp
            // đều đi qua nhánh "row đã DONE" nên không tạo chuyển trạng thái lần hai.
            boolean transitionToDone = false;

            synchronized (REFUEL_WRITE_LOCK) {
                // Recheck trong chính critical section ghi. Giữa entry guard và đây, pull
                // server có thể đã đổi cùng UID thành replica của xe khác.
                RefuelWriteAccess access = classifyWriteAccess(
                        refuelData, ownTruckForWrite, ownTruckIdForWrite);
                // Màn hình tra nạp đã quyết định tiếp quản ở entry guard; recheck này không
                // được lật lại quyết định đó, nếu không mẻ có thật vẫn không ghi được.
                if (access != RefuelWriteAccess.CURRENT && !refuellingTruckOverride)
                    return rejectRefuelWrite(refuelData, access, "DIRECT_PREWRITE");

                localItem = requireRepository().getRefuel(refuelData.getId(), refuelData.getLocalId());
                jsonBeforeRequest = localItem == null ? null : localItem.getJsonData();
                transitionToDone = refuelData.getStatus() == REFUEL_ITEM_STATUS.DONE
                        && (localItem == null
                        || localItem.getStatus() != RefuelItem.REFUEL_ITEM_STATUS.DONE);
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
                        return finishConflict(localItem, true);
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
                        return finishConflict(localItem, true);
                    }

                    // Trong lúc màn hình mở, lượt pull nền (30 giây/lần) có thể đã mang về
                    // thay đổi ở nhóm trường SERVER sở hữu — trạng thái chuyến, bãi đỗ, giờ
                    // dự kiến. Nhận chúng vào payload sắp ghi, nếu không snapshot của màn
                    // hình sẽ đẩy các trường đó lùi về lúc mở màn hình rồi POST lên server.
                    RefuelSyncGuard.AdoptResult adopted = RefuelSyncGuard.adoptServerOwned(
                            refuelData, localItem.getJsonData(), refuelData.getBaseJson());
                    if (adopted.diff != null)
                        Logger.appendLog("DTH", String.format(java.util.Locale.US,
                                "ADOPT_SERVER_FIELDS uid=%s %s",
                                localItem.getUniqueId(), adopted.diff));

                    // Trường SHARED bị cả hai phía sửa khác nhau: không bên nào được im lặng
                    // thắng. Chặn và để màn hình báo cho người dùng đối chiếu.
                    if (adopted.hasConflict()) {
                        Logger.appendRefuelAnomaly(String.format(java.util.Locale.US,
                                "event=SHARED_FIELD_CONFLICT uid=%s keys=%s source=%s",
                                localItem.getUniqueId(), adopted.conflictKeys,
                                remotePost ? "DIRECT_POST" : "LOCAL_SAVE"));
                        return finishConflict(localItem, true);
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
                        refuelData.setBaseJson(localItem.getJsonData());

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
                    // Thứ người dùng vừa sửa là MỚI NHẤT: đánh dấu để lượt đọc lại không phủ
                    // bản server cũ hơn lên đúng những khoá đó.
                    rememberUserFieldEdits(refuelData);
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
                requestUniqueId = localItem.getUniqueId();
                requestServerId = localItem.getId();
                requestLocalId = localItem.getLocalId();

                if (!remotePost) {
                    return markTransition(finishLocalSave(refuelData, localItem), transitionToDone);
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
                String responseIdentityError = validatePostResponseIdentity(
                        refuelData, postedItem, ownTruckForWrite, ownTruckIdForWrite);
                if (responseIdentityError != null) {
                    logRejectedPostResponse(
                            "DIRECT", refuelData, postedItem, responseIdentityError);
                }
                synchronized (REFUEL_WRITE_LOCK) {
                    // Đọc theo identity đã chốt TRƯỚC HTTP. Không dùng Id trên object request:
                    // client HTTP cũ từng gán Id từ response vào chính request, khiến response
                    // nhầm có thể lái lookup sang một row hoàn toàn khác.
                    RefuelItem newestLocal = requestUniqueId == null ? null
                            : requireRepository().getRefuel(requestUniqueId);
                    if (newestLocal == null)
                        newestLocal = requireRepository().getRefuel(
                                requestServerId, requestLocalId);
                    if (newestLocal != null) {
                        localItem = newestLocal;
                    }

                    RefuelWriteAccess responseAccess = classifyWriteAccess(
                            refuelData, ownTruckForWrite, ownTruckIdForWrite);
                    if (responseAccess != RefuelWriteAccess.CURRENT) {
                        // Request đã rời máy trước khi role đổi; chỉ bỏ response, tuyệt đối
                        // không merge ACK hay xoá dirty flag trên remote replica mới.
                        return rejectRefuelWrite(
                                refuelData, responseAccess, "DIRECT_POST_RESPONSE");
                    }
                    if (responseIdentityError != null) {
                        // Local save đã an toàn trong Room và vẫn dirty; chỉ response bị bỏ.
                        return finishConflict(localItem);
                    }

                    // Row đã bị ghi tiếp trong lúc request đang bay ⇒ bản trong Room mới hơn
                    // payload vừa gửi. Giữ nguyên row, chỉ nhận metadata server xác nhận và
                    // để nó tiếp tục nằm trong hàng đợi.
                    if (localItem.getClientSeq() != requestSeq) {
                        if (postedItem != null
                                && RefuelSyncGuard.looksLikeServerAck(refuelData, postedItem)) {
                            RefuelItemData keptBeforeMerge = localItem.toRefuelItemData();
                            RefuelSyncGuard.mergeServerMetadata(localItem, postedItem);
                            if (keptBeforeMerge.getStatus() == REFUEL_ITEM_STATUS.DONE
                                    && finalRefuelValuesChanged(keptBeforeMerge, postedItem)) {
                                logFinalRefuelChange("DIRECT_POST", keptBeforeMerge,
                                        postedItem, "PRESERVE_NEWER_LOCAL");
                            }
                            requireRepository().insertRefuel(localItem);
                        } else if (postedItem != null) {
                            Logger.appendLog("SYNC", "DIRECT_POST row moved on, bỏ response "
                                    + "non-ACK uid=" + requestUniqueId);
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
                        return markTransition(finishLocalSave(refuelData, localItem), transitionToDone);
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
        // insertInvoice ghi ngược localId Room vào localModel. Nếu không có bước này, lượt ghi
        // thứ hai bên dưới (id = idServer) không khớp hàng vừa tạo (id = 0) nên ĐẺ THÊM MỘT
        // HÀNG, còn hàng cũ vẫn isLocalModified ⇒ vòng Synchronize POST lại ⇒ trùng hoá đơn.
        requireRepository().insertInvoice(localModel);
        InvoiceModel postInv = new InvoiceAPI().post(model);
        if (postInv != null) {
            model.setId(postInv.getId());
            model.setLocalId(localModel.getLocalId());
            localModel.setId(postInv.getId());
            // Giữ jsonData khớp với id server vừa cấp, như đường sync của các bảng khác.
            localModel.setJsonData(model.toJson());
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

    /**
     * Số phiếu hoá nghiệm cho các chuyến tiếp theo, tính lại từ các phiếu 2502 còn trong máy.
     * Chạm Room nên phải gọi ngoài luồng giao diện. {@code null} nghĩa là giữ nguyên số hiện hành.
     */
    public static String qcNoForNextFlights(int truckId) {
        return TruckFuelQcPolicy.qcNoForNextFlights(requireRepository().getAllTruckFuels(), truckId);
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

    /**
     * Phiếu của xe khác không mở và lưu được từ máy này (lưu sẽ gửi đè phiếu xe khác), nên
     * danh sách chỉ lấy phiếu của xe đang cài trên máy. Phiếu chưa có xe vẫn hiện.
     */
    private static boolean isCurrentTruck(Integer truckId) {
        int current = FMSApplication.getApplication().getTruckId();
        return current <= 0 || truckId == null || truckId <= 0 || truckId == current;
    }

    public static List<BM2505Model> getBM2505List(Date date) {
        List<BM2505Model> lst = new ArrayList<>();
        for (BM2505Model model : requireRepository().getBM2505List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }

    public static List<BM2508Model> getBM2508List(Date date) {
        List<BM2508Model> lst = new ArrayList<>();
        for (BM2508Model model : requireRepository().getBM2508List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }

    public static List<CheckTrucksModel> getCheckTrucksList(Date date) {
        List<CheckTrucksModel> lst = new ArrayList<>();
        for (CheckTrucksModel model : requireRepository().getCheckTrucksList(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
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

    // ===== BM2506 / BM2509 =====

    // 7 ngày gần nhất (BM2506_BM2509_ANDROID.md mục 4.4)
    private static Date[] formSyncRange() {
        Calendar cal = Calendar.getInstance();
        Date to = cal.getTime();
        cal.add(Calendar.DATE, -7);
        return new Date[]{cal.getTime(), to};
    }

    private static String formErrorMessage(HttpResponse r) {
        String message = null;
        try {
            message = new JSONObject(r.getData()).optString("Message", null);
        } catch (Exception ignored) {
        }
        if (r.getResponseCode() == 404 && (message == null || message.startsWith("No HTTP resource")))
            return "Server chưa hỗ trợ biểu mẫu này";
        return message != null ? message : "Lỗi HTTP " + r.getResponseCode();
    }

    /** Gợi ý [2], [4], [6] cho phiếu BM2509 mới: ưu tiên server, offline thì lấy [2] từ phiếu local gần nhất. */
    public static void fillBM2509Suggestions(BM2509Model model) {
        int truckId = model.getTruckId() != null ? model.getTruckId() : 0;
        JSONObject info = requireHttpClient().getBM2509TruckInfo(truckId, model.getId());
        if (info != null) {
            if (model.getLastDensity15() == null && !info.isNull("LastDensity15"))
                model.setLastDensity15(info.optDouble("LastDensity15"));
            if (model.getReleaseCertNo() == null && !info.isNull("ReleaseCertNo"))
                model.setReleaseCertNo(info.optString("ReleaseCertNo"));
            if (model.getLoadQuantity() == null && !info.isNull("LoadQuantity"))
                model.setLoadQuantity(info.optDouble("LoadQuantity"));
        }
        if (model.getLastDensity15() == null) {
            BM2509 latest = requireRepository().getLatestBM2509(truckId);
            if (latest != null)
                model.setLastDensity15(latest.toModel().getAverageDensity());
        }
        model.calculate();
    }

    public static List<BM2506Model> getBM2506List(Date date) {
        List<BM2506Model> lst = new ArrayList<>();
        for (BM2506Model model : requireRepository().getBM2506List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }

    public static List<BM2509Model> getBM2509List(Date date) {
        List<BM2509Model> lst = new ArrayList<>();
        for (BM2509Model model : requireRepository().getBM2509List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }

    public static void postBM2506(BM2506Model model) {
        model.setSyncError(null); // lưu lại sau khi sửa -> gửi lại
        BM2506 localModel = BM2506.fromModel(model);
        localModel.setLocalModified(true);
        // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
        model.setLocalId(requireRepository().insertBM2506(localModel));
        Synchronize();
    }

    public static void postBM2509(BM2509Model model) {
        model.setSyncError(null); // lưu lại sau khi sửa -> gửi lại
        BM2509 localModel = BM2509.fromModel(model);
        localModel.setLocalModified(true);
        model.setLocalId(requireRepository().insertBM2509(localModel));
        Synchronize();
    }

    public static void deleteBM2506(int[] ids) {
        requireRepository().deleteBM2506(ids);
        Synchronize();
    }

    public static void deleteBM2509(int[] ids) {
        requireRepository().deleteBM2509(ids);
        Synchronize();
    }

    // ===== BM2503 =====
    public static List<BM2503Model> getBM2503List(Date date) {
        List<BM2503Model> lst = new ArrayList<>();
        for (BM2503Model model : requireRepository().getBM2503List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }
    public static void postBM2503(BM2503Model model) {

        BM2503 localModel = BM2503.fromModel(model);
        localModel.setLocalModified(true);

        // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
        model.setLocalId(requireRepository().insertBM2503(localModel));

        // gọi sync giống các nghiệp vụ khác
        Synchronize();
    }
    public static void deleteBM2503(int[] ids) {
        requireRepository().deleteBM2503(ids);
        Synchronize();
    }
    // ===== BM2504 =====

    public static List<BM2504Model> getBM2504List(Date date) {
        List<BM2504Model> lst = new ArrayList<>();
        for (BM2504Model model : requireRepository().getBM2504List(date))
            if (isCurrentTruck(model.getTruckId()))
                lst.add(model);
        return lst;
    }

    public static void postBM2504(BM2504Model model) {

        BM2504 localModel = BM2504.fromModel(model);
        localModel.setLocalModified(true);

        // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
        model.setLocalId(requireRepository().insertBM2504(localModel));

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
        // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
        model.setLocalId(requireRepository().insertBM2508(localModel));
        // call synchronize to update remote database
        Synchronize();
    }

    public static void postCheckTrucks(CheckTrucksModel model) {

        CheckTrucks localModel = CheckTrucks.fromModel(model);
        localModel.setLocalModified(true);
        // trả localId về model để lần lưu sau là update chứ không tạo bản ghi mới
        model.setLocalId(requireRepository().insertCheckTrucks(localModel));
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

        // Chứng từ là kết quả cuối cùng của mẻ tra nạp — đẩy ngay khi vừa lưu xong, không
        // đợi lượt sync định kỳ và không đợi người dùng rời màn hình xem trước.
        pushPendingInBackground();
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

    /** Phiếu máy này đã lưu, tra theo số phiếu. Chạm DB, phải gọi ở luồng nền. */
    public static ReceiptModel getReceiptByNumber(String number) {
        Receipt receipt = requireRepository().getReceiptByNumber(number);
        return receipt == null ? null : receipt.toModel();
    }

    public static List<BM2505ContainerModel> getBM2505ContainerList() {

        return requireRepository().getBM2505ContainerList();
    }

    public static Double getLatestDensityFromLocal() {
        return requireRepository().getLatestDensityFromLocal();
    }

    /**
     * Đẩy phiếu BM 75.01 còn chờ lên server.
     *
     * <p>Gửi trọn phiếu mỗi lần và idempotent theo {@code UniqueId} (hợp đồng API
     * `docs/API-BM7501-PHAN-HOI-BACKEND.md`), nên gửi lại khi mất mạng là an toàn. Phiếu bị
     * từ chối bằng 409/400 được đánh {@code FAILED} để thôi quay vòng — nhân viên sửa phiếu
     * là nó tự quay lại hàng đợi.
     */
    private static void syncBM7501() {
        BM7501Repository repository = new BM7501Repository(
                AppDatabase.getInstance(FMSApplication.getApplication().getApplicationContext())
                        .bm7501Dao());

        for (BM7501Model model : repository.getPendingSync()) {
            if (model == null || model.getUniqueId() == null) continue;

            BM7501PostResult result = requireHttpClient().postBM7501Post2(model);
            BM7501SyncPolicy.Action action =
                    BM7501SyncPolicy.decide(result.getHttpCode(), result.isSuccess());

            if (BM7501SyncPolicy.hasWarning(result.getHttpCode(), result.isSuccess(), result.getMessage())) {
                // Cảnh báo đối soát (lệch xe, lệch số phiếu...): phiếu VẪN đã lưu trên server.
                Logger.appendLog("BM7501_SYNC",
                        "canh bao " + model.getUniqueId() + ": " + result.getMessage());
            }

            switch (action) {
                case SYNCED:
                    repository.applySyncResult(model.getUniqueId(), result.getId(),
                            result.getServerNumber());
                    break;
                case STOP:
                    repository.markSyncFailed(model.getUniqueId());
                    Logger.appendLog("BM7501_SYNC", "dung gui " + model.getUniqueId()
                            + " code=" + result.getHttpCode() + " msg=" + result.getMessage());
                    break;
                case REAUTH:
                    // Token hỏng: các phiếu sau cũng hỏng như vậy, dừng cả lượt cho đỡ vô ích.
                    Logger.appendLog("BM7501_SYNC", "token khong hop le, dung luot dong bo");
                    return;
                case RETRY:
                default:
                    // Mất mạng / 5xx / endpoint chưa deploy: giữ nguyên hàng đợi, lượt sau gửi lại.
                    Logger.appendLog("BM7501_SYNC", "hoan " + model.getUniqueId()
                            + " code=" + result.getHttpCode());
                    break;
            }
        }
    }
}
