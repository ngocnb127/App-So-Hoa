package com.megatech.fms.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;


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

    private static final Context context = FMSApplication.getApplication();
    private static final HttpClient httpClient = new HttpClient();
    private static final DataRepository repo = DataRepository.getInstance(AppDatabase.getInstance(context));

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
            Intent intent = new Intent(UserBaseActivity.SYNC_BROADCAST);
            intent.putExtra("Name", "SYNC");
            FMSApplication.getApplication().sendBroadcast(intent);
            if (!syncLocked.get() && syncPending.getAndSet(false)) {
                Synchronize();
            }
        }
    }


    public static List<TruckModel> getTrucks() {
        List<TruckModel> remoteTrucks = httpClient.getTrucks();
        if (remoteTrucks != null && !remoteTrucks.isEmpty()) {
            for (TruckModel model : remoteTrucks) {
                repo.insertTruck(Truck.fromTruckModel(model));
            }
            return remoteTrucks;
        }

        // Cài mới sẽ ưu tiên API; khi mất mạng mới dùng dữ liệu đã lưu local.
        return repo.getTrucks();
    }

    public static List<TruckModel> getFHSTrucks() {

        return repo.getFHSTrucks();


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
                ShiftModel model = httpClient.getShift();
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

                return repo.getRefuelList(FMSApplication.getApplication().getTruckNo(), FMSApplication.getApplication().getTruckId(), self, type, start, end);
            }
            return repo.getRefuelList(FMSApplication.getApplication().getTruckNo(), FMSApplication.getApplication().getTruckId(), self, type);
        } else
            return httpClient.getRefuelList(self);
    }

    public static RefuelItemData getRefuelItem(String uniqueId) {
        return getRefuelItem(uniqueId, false);
    }

    public static RefuelItemData getRefuelItem(String uniqueId, boolean locked) {
        RefuelItemData remoteItem = httpClient.getRefuelItem(uniqueId);

        // Read/check/create must be atomic: two threads opening the same refuel
        // would otherwise both see null and insert a duplicate row.
        synchronized (REFUEL_WRITE_LOCK) {
            RefuelItem localItem = repo.getRefuel(uniqueId);

            if (localItem == null) {
                if (remoteItem == null) {
                    return null;
                }
                localItem = RefuelItem.fromRefuelItemData(remoteItem);
                repo.insertRefuel(localItem);
                remoteItem.setLocalId(localItem.getLocalId());
                return remoteItem;
            }

            if (localItem.isLocalModified() || remoteItem == null) {
                remoteItem = localItem.toRefuelItemData();
                List<RefuelItem> others = repo.getOthers(uniqueId);
                remoteItem.setOthers(new ArrayList<>());
                for (RefuelItem item : others) {
                    remoteItem.getOthers().add(item.toRefuelItemData());
                }
            }
        }

        return remoteItem;
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
        RefuelItem localItem = repo.getRefuelByFlightAndTruck(flightId, FMSApplication.getApplication().getTruckId());
        if (localItem != null)
            return localItem.toRefuelItemData();
        else
            return null;

    }

    public static RefuelItemData getRefuelItem(Integer id, Integer localId) {
        RefuelItemData remoteItem;

        RefuelItem localItem = repo.getRefuel(id, localId);
        if (id == 0 && localItem != null)
            id = localItem.getId();
        Logger.appendLog("DTH", "Start remote loading item " + id + " - " + localId);

        remoteItem = httpClient.getRefuelItem(id);
        Logger.appendLog("DTH", "End remote loading item " + id + " - " + localId);

        // Re-read inside the lock: the row may have been created or modified while
        // the HTTP request above was running.
        synchronized (REFUEL_WRITE_LOCK) {
            localItem = repo.getRefuel(id, localId);

            if ((localItem != null && localItem.isLocalModified()) || remoteItem == null) {
                if (localItem == null) {
                    return null;
                }
                remoteItem = localItem.toRefuelItemData();
                List<RefuelItem> others = repo.getOthers(localId);
                remoteItem.setOthers(new ArrayList<>());
                for (RefuelItem item : others) {
                    remoteItem.getOthers().add(item.toRefuelItemData());
                }
            }

            if (remoteItem != null && localItem == null) {

                localItem = RefuelItem.fromRefuelItemData(remoteItem);
                repo.insertRefuel(localItem);
                remoteItem.setLocalId(localItem.getLocalId());

            }
        }

        return remoteItem;

    }

    public static boolean checkLocalModified() {

        return repo.getLocalModified();
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
                    List<RefuelItem> modified = repo.getModifiedRefuel();
                    if (modified.size() > 0) {
                        for (RefuelItem item : modified) {
                            String jsonBeforeRequest = item.getJsonData();
                            RefuelItemData itemData = item.toRefuelItemData();
                            RefuelItemData newData = httpClient.postRefuel(itemData);
                            if (newData != null) {
                                if (newData.getStatus() == REFUEL_ITEM_STATUS.DONE
                                        && itemData.getStatus() != REFUEL_ITEM_STATUS.DONE
                                        && finalRefuelValuesChanged(newData, itemData)) {
                                    logFinalRefuelChange("BACKGROUND_SYNC", newData, itemData,
                                            "SERVER_REJECTED_DOWNGRADE");
                                }
                                synchronized (REFUEL_WRITE_LOCK) {
                                    // Re-read after HTTP: an Activity may have saved a newer
                                    // snapshot while this request was in flight.
                                    RefuelItem newestLocal = repo.getRefuel(item.getId(), item.getLocalId());
                                    if (newestLocal == null) {
                                        newestLocal = item;
                                    }

                                    if (RefuelSyncGuard.shouldPreserveLocal(jsonBeforeRequest,
                                            newestLocal.getJsonData(), newestLocal.isLocalModified())) {
                                        // Snapshot before the merge: it carries the local
                                        // ClientSeq/ServerRevision the log needs to tell the
                                        // two snapshots apart.
                                        RefuelItemData keptBeforeMerge = newestLocal.toRefuelItemData();
                                        RefuelSyncGuard.mergeServerMetadata(newestLocal, newData);
                                        if (keptBeforeMerge.getStatus() == REFUEL_ITEM_STATUS.DONE
                                                && finalRefuelValuesChanged(keptBeforeMerge, newData)) {
                                            logFinalRefuelChange("BACKGROUND_SYNC",
                                                    keptBeforeMerge, newData,
                                                    "PRESERVE_NEWER_LOCAL");
                                        }
                                        repo.insertRefuel(newestLocal);
                                        continue;
                                    }

                                    // Never let an old PROCESSING response downgrade a row
                                    // which is already known locally as DONE.
                                    if (newestLocal.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                                            && newData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                                        logFinalRefuelChange("BACKGROUND_SYNC",
                                                newestLocal.toRefuelItemData(), newData,
                                                "BLOCK_OLD_RESPONSE");
                                        Logger.appendLog("SYNC", "Ignore non-DONE response for DONE refuel "
                                                + newestLocal.getId());
                                        continue;
                                    }

                                    // HTTP 200 returns the authoritative server object. This
                                    // also handles the API blocking DONE -> PROCESSING: the
                                    // returned DONE object replaces the stale local payload and
                                    // must not be retried.
                                    RefuelItem serverItem = RefuelItem.fromRefuelItemData(newData);
                                    serverItem.setLocalId(newestLocal.getLocalId());
                                    serverItem.setLocalModified(false);
                                    serverItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                                    repo.insertRefuel(serverItem);
                                }
                            }
                        }
                    }

                    Date d = repo.getLastModifiedRefuel();
                    List<RefuelItemData> remoteList = httpClient.getModifiedRefuels(0, d);
                    if (remoteList != null) {
                        int[] ids = new int[remoteList.size()];
                        int i = 0;
                        for (RefuelItemData model : remoteList) {
                            if (!model.isDeleted()) {
                                RefuelItem remoteItem = RefuelItem.fromRefuelItemData(model);
                                synchronized (REFUEL_WRITE_LOCK) {
                                    RefuelItem localItem = repo.getRefuel(remoteItem.getUniqueId());
                                    if (localItem == null) {
                                        localItem = repo.getRefuel(remoteItem.getId(), remoteItem.getLocalId());
                                    }

                                    if (localItem != null
                                            && localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                                            && model.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                                        logFinalRefuelChange("REMOTE_PULL", localItem.toRefuelItemData(),
                                                model, "BLOCK_DOWNGRADE");
                                    } else if (canApplyRemote(localItem, model)) {
                                        if (localItem != null) {
                                            remoteItem.setLocalId(localItem.getLocalId());
                                        }
                                        repo.insertRefuel(remoteItem);
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
                                repo.insertFlight(flight);
                            } else if (model.getId() > 0)
                                ids[i++] = model.getId();
                        }
                        repo.removeDeletedRefuels(ids);
                    }


                    //Delete old records
                    repo.deleteOldRefuels(10);
                    //processing = false;

                    List<Review> modifiedReview = repo.getModifiedReview();

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
                                repo.postReview(item);
                            }

                        }
                    }

                    List<Receipt> modifiedReceipt = repo.getModifiedReceipt();
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
                                repo.insertReceipt(item);
                            }
                        }
                    }
                    List<Invoice> modifiedInvoice = repo.getModifiedInvoice();
                    InvoiceAPI invoiceAPI = new InvoiceAPI();
                    if (modifiedInvoice.size() > 0) {
                        for (Invoice item : modifiedInvoice) {
                            InvoiceModel itemData = item.toModel();
                            InvoiceModel newData = invoiceAPI.post(itemData);

                            if (newData != null) {


                                item.setLocalModified(false);

                                item.setId(newData.getId());
                                item.setJsonData(newData.toJson());
                                repo.insertInvoice(item);
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

                List<TruckFuel> modified = repo.getModifiedTruckFuel();
                if (modified.size() > 0) {
                    for (TruckFuel item : modified) {
                        TruckFuelModel itemData = item.toTruckFuelModel();
                        Logger.appendLog("B2502", "Sync localId=" + itemData.getLocalId()
                                + ", requestId=" + itemData.getId());
                        TruckFuelModel newData = httpClient.postTruckFuel(itemData);
                        if (newData != null) {
                            // Giữ payload local đầy đủ; response POST của API có thể chỉ trả một phần field.
                            item.setId(newData.getId());
                            itemData.setId(newData.getId());
                            item.setJsonData(itemData.toJson());
                            item.setLocalModified(false);
                            repo.insertTruckFuel(item);
                            Logger.appendLog("B2502", "Synced localId=" + item.getLocalId()
                                    + ", responseId=" + newData.getId());
                        }
                    }
                }
                List<TruckFuelModel> lstModel = httpClient.getTruckFuels();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (TruckFuelModel model : lstModel) {
                        repo.mergeRemoteTruckFuel(TruckFuel.fromTruckFuelModel(model));

                    }

                }

            });

            //sync BM2505
            startSyncTask("bm2505", () -> {

                List<BM2505> modified = repo.getModifiedBM2505();
                if (modified.size() > 0) {
                    for (BM2505 item : modified) {
                        BM2505Model itemData = item.toModel();
                        BM2505Model newData = httpClient.postBM2505(itemData);
                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());
                            repo.insertBM2505(item);
                        }
                    }
                }
                List<BM2505Model> lstModel = httpClient.getBM2505List();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (BM2505Model model : lstModel) {
                        repo.mergeRemoteBM2505(BM2505.fromModel(model));

                    }

                }

                List<BM2505ContainerModel> lstContainer = httpClient.getBM2505ContainerList();

                if (lstContainer != null) {
                    int i = 0;
                    for (BM2505ContainerModel model : lstContainer) {
                        repo.insertBM2505Container(BM2505Container.fromModel(model));

                    }

                }

            });

            // =====================
            // SYNC BM2503
            // =====================
            startSyncTask("bm2503", () -> {

                List<BM2503> modified = repo.getModifiedBM2503();
                if (modified.size() > 0) {
                    for (BM2503 item : modified) {

                        BM2503Model itemData = item.toModel();
                        BM2503Model newData = httpClient.postBM2503(itemData);

                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());

                            repo.insertBM2503(item);
                        }
                    }
                }

                // pull từ server về
                List<BM2503Model> lstModel = httpClient.getBM2503List();
                if (lstModel != null) {
                    for (BM2503Model model : lstModel) {
                        repo.mergeRemoteBM2503(BM2503.fromModel(model));
                    }
                }

            });

            // =====================
// SYNC BM2504
// =====================
            startSyncTask("bm2504", () -> {

                // ===== PUSH LOCAL MODIFIED =====
                List<BM2504> modified = repo.getModifiedBM2504();
                if (modified != null && modified.size() > 0) {

                    for (BM2504 item : modified) {

                        BM2504Model itemData = item.toModel();
                        BM2504Model newData = httpClient.postBM2504(itemData);

                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());

                            repo.insertBM2504(item);
                        }
                    }
                }

                // ===== PULL FROM SERVER =====
                List<BM2504Model> lstModel = httpClient.getBM2504List();
                if (lstModel != null && lstModel.size() > 0) {

                    for (BM2504Model model : lstModel) {
                        repo.mergeRemoteBM2504(BM2504.fromModel(model));
                    }
                }

            });


            //sync BM2307A
            startSyncTask("check-trucks", () -> {

                List<CheckTrucks> modified = repo.getModifiedCheckTrucks();
                if (modified.size() > 0) {
                    for (CheckTrucks item : modified) {
                        CheckTrucksModel itemData = item.toModel();
                        CheckTrucksModel newData = httpClient.postCheckTrucks(itemData);
                        if (newData != null) {
                            item.setLocalModified(false);
                            item.setId(newData.getId());
                            repo.insertCheckTrucks(item);
                        }
                    }
                }
                List<CheckTrucksModel> lstModel = httpClient.getCheckTrucksList();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (CheckTrucksModel model : lstModel) {
                        repo.mergeRemoteCheckTrucks(CheckTrucks.fromModel(model));
                    }

                }
            });
            //sync BM2508
            startSyncTask("bm2508", () -> {

                List<BM2508> modified = repo.getModifiedBM2508();
                if (modified.size() > 0) {
                    for (BM2508 item : modified) {
                        BM2508Model itemData = item.toModel();
                        BM2508Model newData = httpClient.postBM2508Post2(itemData);
                        if (newData != null) {
                            item.setId(newData.getId());
                            itemData.setId(newData.getId());
                            item.setJsonData(itemData.toJson());
                            // Dữ liệu chính đã thành công: không POST lại, dù ảnh có thể chưa gửi được.
                            item.setLocalModified(false);
                            item.setAttachmentPending(hasCompleteBM2508Attachments(itemData));
                            repo.insertBM2508(item);
                        }
                    }
                }

                // Ảnh/chữ ký có hàng đợi riêng; lỗi chỉ retry ảnh theo Id đã có trên server.
                List<BM2508> pendingAttachments = repo.getPendingBM2508Attachments();
                ReceiptAPI attachmentApi = new ReceiptAPI();
                for (BM2508 item : pendingAttachments) {
                    BM2508Model itemData = item.toModel();
                    if (!hasCompleteBM2508Attachments(itemData)) {
                        // Thiếu ảnh/chữ ký hoặc file đã bị xóa: dừng retry, giữ nguyên dữ liệu phiếu.
                        item.setAttachmentPending(false);
                        repo.insertBM2508(item);
                        Logger.appendLog("BM2508_ATTACHMENT", "Stop retry, missing local attachment. id=" + item.getId());
                    } else if (attachmentApi.postMultipartBM2508(itemData) != null) {
                        item.setAttachmentPending(false);
                        repo.insertBM2508(item);
                    }
                }
                List<BM2508Model> lstModel = httpClient.getBM2508List();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (BM2508Model model : lstModel) {
                        repo.mergeRemoteBM2508(BM2508.fromModel(model));
                        ReceiptAPI client = new ReceiptAPI();
                        // Gửi file ảnh lên API
                        //client.postMultipartBM2508(model);
                    }

                }
            });
            //update airlines, users from another thread
            startSyncTask("master-data", () -> {
                List<AirlineModel> lstModel = httpClient.getAirlines();

                if (lstModel != null) {
                    int[] ids = new int[lstModel.size()];
                    int i = 0;
                    for (AirlineModel model : lstModel) {
                        repo.insertAirline(Airline.fromAirlineModel(model));

                        //ids[i++] = model.getId();
                    }
                    //repo.deleteOudateTrucks(ids);
                }

                List<AirportsModel> lstAirports = httpClient.getAirports();
                if (lstAirports != null) {
                    for (AirportsModel model : lstAirports) {
                        repo.insertAirports(Airports.fromAirportsModel(model));
                    }
                }

                // sync Product
                startSyncTask("products", () -> {


                    List<ProductModel> lstproduct = httpClient.getProductList();

                    if (lstproduct != null && lstproduct.size() > 0) {
                        for (ProductModel model : lstproduct) {
                            repo.insertProduct(Product.fromModel(model));
                        }
                    }

                });

                //Users

                List<UserModel> lstUser = httpClient.getUsers();

                if (lstUser != null) {
                    int[] ids = new int[lstUser.size()];
                    int i = 0;
                    for (UserModel model : lstUser) {

                        repo.insertUser(User.fromUserModel(model));

                        //ids[i++] = model.getId();
                    }
                    //repo.deleteOudateTrucks(ids);
                }

                InvoiceFormModel[] invoiceForms = getInvoiceForms();
                if (invoiceForms != null)
                    FMSApplication.getApplication().saveInvoiceForms(invoiceForms);

                List<TruckModel> lstTrucks = httpClient.getTrucks();

                if (lstTrucks != null) {
                    int[] ids = new int[lstTrucks.size()];
                    int i = 0;
                    for (TruckModel model : lstTrucks) {
                        repo.insertTruck(Truck.fromTruckModel(model));

                        ids[i++] = model.getId();
                    }
                    //repo.deleteOudateTrucks(ids);
                }

            });

            startSyncTask("logs", () -> {
                Logger.sendLog();
            });

            startSyncTask("screenshots", () -> {
                ScreenshotAPI api = new ScreenshotAPI();
                try {
                    File folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
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

    private static boolean canApplyRemote(RefuelItem localItem, RefuelItemData remote) {
        if (localItem == null) return true;

        if (localItem.isLocalModified()) {
            return false;
        }

        RefuelItemData local = localItem.toRefuelItemData();
        boolean localFinal = local.getStatus() == REFUEL_ITEM_STATUS.DONE || local.isPrinted();

        if (localFinal) {
            boolean accept = remote.getServerRevision() > local.getServerRevision();
            return accept;
        }

        return true;
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
    private static RefuelItemData finishPostRefuel(RefuelItemData caller, RefuelItem stored) {
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
        }
        return stored.toRefuelItemData();
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
            synchronized (REFUEL_WRITE_LOCK) {
                localItem = repo.getRefuel(refuelData.getId(), refuelData.getLocalId());
                jsonBeforeRequest = localItem == null ? null : localItem.getJsonData();
                if (localItem == null) {
                    /// if item not exists in local database
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
                        return finishPostRefuel(refuelData, localItem);
                    }

                    localItem.updateData(refuelData);
                }

                // Local-only changes must be persisted atomically with the stale
                // status check above.
                if (!remotePost) {
                    localItem.setLocalModified(true);
                    repo.insertRefuel(localItem);
                    return finishPostRefuel(refuelData, localItem);
                }
            }

            if (remotePost) {
                RefuelItemData postedItem = processing.get() ? null : httpClient.postRefuel(refuelData);
                synchronized (REFUEL_WRITE_LOCK) {
                    RefuelItem newestLocal = repo.getRefuel(refuelData.getId(), refuelData.getLocalId());
                    if (newestLocal != null) {
                        localItem = newestLocal;
                    }

                    if (postedItem != null) {
                        if (RefuelSyncGuard.shouldPreserveLocal(jsonBeforeRequest,
                                localItem.getJsonData(), localItem.isLocalModified())) {
                            // Snapshot before the merge: it carries the local
                            // ClientSeq/ServerRevision the log needs to tell the
                            // two snapshots apart.
                            RefuelItemData keptBeforeMerge = localItem.toRefuelItemData();
                            RefuelSyncGuard.mergeServerMetadata(localItem, postedItem);
                            if (keptBeforeMerge.getStatus() == REFUEL_ITEM_STATUS.DONE
                                    && finalRefuelValuesChanged(keptBeforeMerge, postedItem)) {
                                logFinalRefuelChange("DIRECT_POST", keptBeforeMerge,
                                        postedItem, "PRESERVE_NEWER_LOCAL");
                            }
                            repo.insertRefuel(localItem);
                            return finishPostRefuel(refuelData, localItem);
                        }

                        // Preserve a newer local DONE if an older non-DONE response
                        // happens to complete afterwards.
                        if (localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                                && postedItem.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                            logFinalRefuelChange("DIRECT_POST", localItem.toRefuelItemData(),
                                    postedItem, "BLOCK_OLD_RESPONSE");
                            return finishPostRefuel(refuelData, localItem);
                        }

                        RefuelItem serverItem = RefuelItem.fromRefuelItemData(postedItem);
                        serverItem.setLocalId(localItem.getLocalId());
                        serverItem.setLocalModified(false);
                        serverItem.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
                        localItem = serverItem;
                    } else {
                        // HTTP was unavailable/skipped. Queue only if this request
                        // has not become stale while waiting.
                        if (localItem.getStatus() == RefuelItem.REFUEL_ITEM_STATUS.DONE
                                && refuelData.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                            logFinalRefuelChange("DIRECT_POST", localItem.toRefuelItemData(),
                                    refuelData, "BLOCK_ENQUEUE_DOWNGRADE");
                            return finishPostRefuel(refuelData, localItem);
                        }
                        localItem.updateData(refuelData);
                        localItem.setLocalModified(true);
                    }

                    repo.insertRefuel(localItem);
                    return finishPostRefuel(refuelData, localItem);
                }
            }
        } else
            return null;

        return null;
    }


    public static List<AirlineModel> getAirlines() {
        if (isDebug) {


            List<AirlineModel> localList = repo.getAirlines();

            return localList;
        }
        return httpClient.getAirlines();

    }

    public static List<UserModel> getUsers() {
        if (isDebug) {
            return repo.getUsers();
        }
        return httpClient.getUsers();
    }

    public static List<ProductModel> getProducts() {
        if (isDebug) {


            List<ProductModel> localList = repo.getProductList();

            return localList;
        }
        return httpClient.getProductList();

    }

    public static InvoiceFormModel[] getInvoiceForms() {

        return httpClient.getInvoiceForms();
    }

    public static void postInvoice(InvoiceModel model) {
        Invoice localModel = Invoice.fromModel(model);
        localModel.setLocalModified(true);
        repo.insertInvoice(localModel);
        InvoiceModel postInv = new InvoiceAPI().post(model);
        if (postInv != null) {
            localModel.setId(postInv.getId());
            localModel.setLocalModified(false);
            repo.insertInvoice(localModel);
        }
        Synchronize();
    }

    public static RefuelItemData getImcomplete() {
        RefuelItemData item = repo.getIncomplete(FMSApplication.getApplication().getTruckNo());
        return item;
    }

    public static List<TruckFuelModel> getTruckFuels() {
        return getTruckFuels(new Date());
    }

    public static List<TruckFuelModel> getTruckFuels(Date date) {
        return repo.getTruckFuels(date);
    }

    public static void postTruckFuel(TruckFuelModel model) {
        TruckFuel localModel = TruckFuel.fromTruckFuelModel(model);
        localModel.setLocalModified(true);
        repo.insertTruckFuel(localModel);
        // call synchronize to update remote database
        Synchronize();

    }

    public static void deleteTruckFuels(int[] ids) {
        repo.deleteTruckFuels(ids);
        // call synchronize to update remote database
        Synchronize();

    }

    public static List<BM2505Model> getBM2505List(Date date) {
        return repo.getBM2505List(date);
    }

    public static List<BM2508Model> getBM2508List(Date date) {
        return repo.getBM2508List(date);
    }

    public static List<CheckTrucksModel> getCheckTrucksList(Date date) {
        return repo.getCheckTrucksList(date);
    }

    public static void postBM2505(BM2505Model model) {

        BM2505 localModel = BM2505.fromModel(model);
        localModel.setLocalModified(true);
        repo.insertBM2505(localModel);
        // call synchronize to update remote database
        Synchronize();
    }

    // ===== BM2503 =====
    public static List<BM2503Model> getBM2503List(Date date) {
        return repo.getBM2503List(date);
    }
    public static void postBM2503(BM2503Model model) {

        BM2503 localModel = BM2503.fromModel(model);
        localModel.setLocalModified(true);

        repo.insertBM2503(localModel);

        // gọi sync giống các nghiệp vụ khác
        Synchronize();
    }
    public static void deleteBM2503(int[] ids) {
        repo.deleteBM2503(ids);
        Synchronize();
    }
    // ===== BM2504 =====

    public static List<BM2504Model> getBM2504List(Date date) {
        return repo.getBM2504List(date);
    }

    public static void postBM2504(BM2504Model model) {

        BM2504 localModel = BM2504.fromModel(model);
        localModel.setLocalModified(true);

        repo.insertBM2504(localModel);

        // gọi sync giống các nghiệp vụ khác
        Synchronize();
    }

    public static void deleteBM2504(int[] ids) {
        repo.deleteBM2504(ids);
        Synchronize();
    }




    public static void postBM2508(BM2508Model model) {

        BM2508 localModel = BM2508.fromModel(model);
        localModel.setLocalModified(true);
        localModel.setAttachmentPending(hasCompleteBM2508Attachments(model));
        repo.insertBM2508(localModel);
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
        repo.insertCheckTrucks(localModel);
        // call synchronize to update remote database
        Synchronize();
    }

    public static List<FlightModel> getFlights() {
        return getFlights(new Date());
    }


    public static List<FlightModel> getFlights(Date date) {
        return repo.getFlights(date);
    }

    public static List<AirportsModel> getAirports() {
        if (isDebug) {
            return repo.getAirports();
        }
        return httpClient.getAirports();

    }
    public static List<ShiftModel> getShifts() {
        if (isDebug) {
            List<ShiftModel> lstModel = httpClient.getShifts();

            if (lstModel != null) {
                int[] ids = new int[lstModel.size()];
                int i = 0;
                for (ShiftModel model : lstModel) {
                    repo.insertShift(Shift.fromShiftModel(model));
                    ids[i++] = model.getId();
                }
                repo.deleteOudateShift(ids);
            }
            List<ShiftModel> localList = repo.getShifts();
            return localList;
        }
        return httpClient.getShifts();

    }
    public static void deleteBM2505(int[] ids) {
        repo.deleteBM2505(ids);
        Synchronize();
    }

    public static void deleteBM2508(int[] ids) {
        repo.deleteBM2508(ids);
        Synchronize();
    }

    public static void deleteCheckTrucks(int[] ids) {
        repo.deleteCheckTrucks(ids);
        Synchronize();
    }

    public static void postReceipt(ReceiptModel model) {
        Logger.appendLog("DTH", "postReceipt num=" + model.getNumber()
                + " uniqueId=" + model.getUniqueId()
                + " thread=" + Thread.currentThread().getName());

        Receipt localModel = Receipt.fromModel(model);
        localModel.setLocalModified(true);
        long rowId = repo.insertReceipt(localModel);
        Logger.appendLog("DTH", "postReceipt INSERT rowId=" + rowId + " num=" + model.getNumber());
        if (rowId > 0) {
            Synchronize();
        }
    }

    public static void cancelReceipts(String[] printedItems, String reason) {
        repo.cancelReceipts(printedItems, reason);
        Synchronize();
    }

    public static List<ReceiptModel> getReceiptList(Date date) {
        List<Receipt> modified = repo.getReceiptList(date);
        List<ReceiptModel> lst = new ArrayList<>();
        for (Receipt local : modified) {
            lst.add(local.toModel());
        }
        return lst;
    }

    public static void postLog(LogEntryModel.LOG_TYPE tag, String logText, String activity) {
        new Thread(()-> {
            repo.postLog(new LogEntryModel(tag, logText, activity));
        }).start();
    }

    public static List<LogEntryModel> getLogList(int limit) {
        List<LogEntry> modified = repo.getLogList(limit);
        List<LogEntryModel> lst = new ArrayList<>();
        for (LogEntry local : modified) {
            lst.add(local.toModel());
        }
        return lst;
    }

    public static void deleteLogs(int[] ids) {
        new Thread(() -> {
            repo.deleteLogs(ids);
        }).start();
    }

    public static ReviewModel getReview(int id) {
        return null;
    }

    public static void postReview(ReviewModel model) {
        repo.postReview(Review.fromModel(model));
    }

    public static ReviewModel getReviewByFlight(int flightId) {
        return repo.getReviewByFlight(flightId);
    }
    public static ReviewModel getReviewByFlight(String flightId) {
        return repo.getReviewByFlight(flightId);
    }
    public static boolean checkReview(int flightId, String flightUniqueId) {
        return repo.checkReview(flightId,flightUniqueId);
    }

    public static ReceiptModel getReceipt(String uniqueId) {
        Receipt receipt =  repo.getReceipt(uniqueId);
        if (receipt!=null)
            return receipt.toModel();
        else return null;
    }

    public static List<BM2505ContainerModel> getBM2505ContainerList() {

        return repo.getBM2505ContainerList();
    }

    public static Double getLatestDensityFromLocal() {
        return repo.getLatestDensityFromLocal();
    }





}
