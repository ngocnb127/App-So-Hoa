package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Hồi quy cho ranh giới dữ liệu: xe hiện tại sở hữu local, xe khác là server replica. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class RefuelRemoteReplicaIntegrationTest {

    private static final String OWN_TRUCK = "HAN3-20-7002";
    private static final int OWN_TRUCK_ID = 7002;
    private static final int FLIGHT_ID = 621;
    private static final String FLIGHT_UID = "flight-3s621";

    private AppDatabase db;
    private DataRepository repo;
    private FakeHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FMSApplication app = (FMSApplication) context;
        TruckModel setting = new TruckModel();
        setting.setTruckNo(OWN_TRUCK);
        setting.setTruckId(OWN_TRUCK_ID);
        app.saveSetting(setting, false);

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new FakeHttpClient();
        DataHelper.installTestDependencies(repo, http);
        DataHelper.clearRefuelPullCursorForTesting();
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    @Test
    public void parserKeepsRawJsonForEveryOtherAndDistinguishesMissingCollection()
            throws Exception {
        JSONObject child = new JSONObject(rawPayload(
                payload("foreign", "HAN3-20-7012", 7012, REFUEL_ITEM_STATUS.PROCESSING,
                        3, 3069, new Date(1_777_300_248_000L), null)));
        child.put("ServerOnlyObject", new JSONObject().put("Code", "TCS"));

        JSONObject root = new JSONObject(rawPayload(
                payload("own", OWN_TRUCK, OWN_TRUCK_ID, REFUEL_ITEM_STATUS.DONE,
                        4, 8099, new Date(1_777_300_000_000L),
                        new Date(1_777_300_600_000L))));
        root.put("Others", new JSONArray().put(child));

        RefuelItemData parsed = http.parseRefuelItemResponse(root.toString());

        assertTrue(parsed.hasCompleteOthersSnapshot());
        assertEquals(1, parsed.getOthers().size());
        assertTrue(parsed.getOthers().get(0).getRawJson().contains("ServerOnlyObject"));
        assertEquals(new Date(1_777_300_248_000L), parsed.getOthers().get(0).getStartTime());
        assertNull("EndTime vắng mặt không được biến thành giờ hiện tại",
                parsed.getOthers().get(0).getEndTime());
        assertFalse("POST xe hiện tại không được mang nested replica lên server",
                new JSONObject(http.buildRefuelPostPayload(parsed)).has("Others"));
        assertEquals("serialize POST không được làm mất Others đang dùng trên UI",
                1, parsed.getOthers().size());

        root.remove("Others");
        RefuelItemData withoutOthers = http.parseRefuelItemResponse(root.toString());
        assertFalse(withoutOthers.hasCompleteOthersSnapshot());
    }

    @Test
    public void completeOthersDiscoversNewTruckAndStoresWholeProcessingSnapshot() throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData foreign = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 3, 3069,
                new Date(1_777_300_248_000L), null);
        JSONObject foreignRaw = new JSONObject(rawPayload(foreign));
        foreignRaw.put("ServerOnlyArray", new JSONArray().put(1).put(2));
        foreign.setRawJson(foreignRaw.toString());

        own.setOthers(Collections.singletonList(foreign));
        own.setCompleteOthersSnapshot(true);
        own.setRawJson(rawPayload(own));
        http.remoteItem = own;

        RefuelItemData loaded = DataHelper.getRefuelItem("own");

        assertNotNull(loaded);
        assertEquals(1, loaded.getOthers().size());
        RefuelItemData displayed = loaded.getOthers().get(0);
        assertEquals(REFUEL_ITEM_STATUS.PROCESSING, displayed.getStatus());
        assertEquals(new Date(1_777_300_248_000L), displayed.getStartTime());
        assertNull(displayed.getEndTime());

        RefuelItem stored = repo.getRefuel("foreign");
        assertNotNull("xe chưa có trong Room phải được phát hiện từ Others server", stored);
        assertTrue(stored.isRemoteReplica());
        assertFalse(stored.isLocalModified());
        assertTrue(stored.getJsonData().contains("ServerOnlyArray"));
    }

    @Test
    public void previewUsesOneRootGetAndExactServerMembershipInServerOrder() throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData staleCached = payload("stale", "HAN3-20-7099", 7099,
                REFUEL_ITEM_STATUS.DONE, 1, 999,
                new Date(1000), new Date(2000));
        staleCached.setRawJson(rawPayload(staleCached));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(staleCached));

        RefuelItemData second = payload("foreign-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.PROCESSING, 5, 5061,
                new Date(1_777_300_248_000L), null);
        RefuelItemData first = payload("foreign-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 6, 3069,
                new Date(1_777_300_200_000L), new Date(1_777_300_500_000L));
        second.setRawJson(rawPayload(second));
        first.setRawJson(rawPayload(first));
        own.setRawJson(rawPayload(own));
        own.setOthers(Arrays.asList(second, first));
        own.setCompleteOthersSnapshot(true);
        http.responses.put("own", own);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own");

        assertNotNull(loaded.item);
        assertTrue(loaded.others.collectionComplete);
        assertFalse(loaded.others.hasFailure());
        assertEquals(Collections.singletonList("own"), http.getUids);
        assertEquals(2, loaded.item.getOthers().size());
        assertEquals("foreign-2", loaded.item.getOthers().get(0).getUniqueId());
        assertEquals("foreign-1", loaded.item.getOthers().get(1).getUniqueId());
        assertTrue("row cache cũ không được cộng lại vào membership server",
                loaded.item.getOthers().stream()
                        .noneMatch(item -> "stale".equals(item.getUniqueId())));
    }

    @Test
    public void olderPreviewResponseCannotDowngradeNewerMembership() throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData first = payload("foreign-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 5, 100, new Date(1000), new Date(2000));
        RefuelItemData second = payload("foreign-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 5, 200, new Date(1000), new Date(2000));
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));

        RefuelItemData olderRoot = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        olderRoot.setRawJson(rawPayload(olderRoot));
        olderRoot.setOthers(Collections.singletonList(first));
        olderRoot.setCompleteOthersSnapshot(true);

        RefuelItemData newerRoot = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        newerRoot.setRawJson(rawPayload(newerRoot));
        newerRoot.setOthers(Arrays.asList(first, second));
        newerRoot.setCompleteOthersSnapshot(true);

        CountDownLatch olderStarted = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        http.getHandler = uid -> {
            if (calls.incrementAndGet() == 1) {
                olderStarted.countDown();
                try {
                    releaseOlder.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return olderRoot;
            }
            return newerRoot;
        };

        DataHelper.PreviewLoadResult[] oldResult = new DataHelper.PreviewLoadResult[1];
        Thread oldThread = new Thread(
                () -> oldResult[0] = DataHelper.loadRefuelForPreview("own"));
        oldThread.start();
        assertTrue(olderStarted.await(2, TimeUnit.SECONDS));

        DataHelper.PreviewLoadResult newest = DataHelper.loadRefuelForPreview("own");
        releaseOlder.countDown();
        oldThread.join(2000);

        assertTrue(newest.others.collectionComplete);
        assertNotNull(oldResult[0]);
        assertTrue(oldResult[0].superseded);
        assertEquals(Arrays.asList("foreign-1", "foreign-2"),
                repo.getRemoteOthersMembership("own"));
    }

    @Test
    public void explicitEmptyOthersDoesNotFallBackToStaleRoomRows() throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);
        RefuelItemData staleCached = payload("stale", "HAN3-20-7099", 7099,
                REFUEL_ITEM_STATUS.DONE, 1, 999, new Date(1000), new Date(2000));
        staleCached.setRawJson(rawPayload(staleCached));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(staleCached));

        own.setRawJson(rawPayload(own));
        own.setOthers(Collections.emptyList());
        own.setCompleteOthersSnapshot(true);
        http.responses.put("own", own);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own");

        assertTrue(loaded.others.collectionComplete);
        assertTrue(loaded.item.hasCompleteOthersSnapshot());
        assertTrue(loaded.item.getOthers().isEmpty());
        assertEquals(Collections.singletonList("own"), http.getUids);
    }

    @Test
    public void invalidRootOwnershipCannotReplaceOtherwiseValidOthers() throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData cached = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 5, 100, new Date(1000), new Date(2000));
        cached.setRawJson(new JSONObject(rawPayload(cached))
                .put("Marker", "trusted-before-malformed-root").toString());
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(cached));
        String trusted = repo.getRefuel("foreign").getJsonData();

        RefuelItemData malformedRoot = payload("own", OWN_TRUCK, 7012,
                REFUEL_ITEM_STATUS.DONE, 6, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        RefuelItemData attemptedChild = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 10, 999, new Date(3000), new Date(4000));
        attemptedChild.setRawJson(new JSONObject(rawPayload(attemptedChild))
                .put("Marker", "must-not-apply").toString());
        malformedRoot.setRawJson(rawPayload(malformedRoot));
        malformedRoot.setOthers(Collections.singletonList(attemptedChild));
        malformedRoot.setCompleteOthersSnapshot(true);
        http.responses.put("own", malformedRoot);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own");

        assertNotNull(loaded.item);
        assertFalse(loaded.others.collectionComplete);
        assertEquals(trusted, repo.getRefuel("foreign").getJsonData());
    }

    @Test
    public void nextOfflineLoadUsesLastServerMembershipAndDoesNotResurrectStaleRows()
            throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);
        RefuelItemData stale = payload("stale", "HAN3-20-7099", 7099,
                REFUEL_ITEM_STATUS.DONE, 1, 999, new Date(1000), new Date(2000));
        stale.setRawJson(rawPayload(stale));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(stale));

        RefuelItemData authoritative = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 2, 3069, new Date(3000), new Date(4000));
        authoritative.setRawJson(rawPayload(authoritative));
        own.setRawJson(rawPayload(own));
        own.setOthers(Collections.singletonList(authoritative));
        own.setCompleteOthersSnapshot(true);
        http.responses.put("own", own);
        DataHelper.PreviewLoadResult first = DataHelper.loadRefuelForPreview("own");
        assertEquals("foreign", first.item.getOthers().get(0).getUniqueId());

        http.getUids.clear();
        http.responses.put("own", null);
        http.responses.put("foreign", null);
        DataHelper.PreviewLoadResult offline = DataHelper.loadRefuelForPreview("own");

        assertFalse(offline.others.collectionComplete);
        assertEquals(Arrays.asList("own", "foreign"), http.getUids);
        assertEquals(1, offline.item.getOthers().size());
        assertEquals("foreign", offline.item.getOthers().get(0).getUniqueId());
    }

    @Test
    public void legacyFallbackRefreshesKnownRowsButNeverClaimsCompleteMembership()
            throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData cached = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 1, 100,
                new Date(1000), null);
        cached.setRawJson(rawPayload(cached));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(cached));

        own.setRawJson(rawPayload(own)); // endpoint cũ: không có complete Others
        RefuelItemData updated = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 3069,
                new Date(3000), null);
        updated.setRawJson(rawPayload(updated));
        http.responses.put("own", own);
        http.responses.put("foreign", updated);

        DataHelper.PreviewLoadResult loaded = DataHelper.loadRefuelForPreview("own");

        assertNotNull(loaded.item);
        assertFalse(loaded.others.collectionComplete);
        assertTrue("fallback phải cảnh báo vì không phát hiện được UID mới",
                loaded.others.hasFailure());
        assertEquals(0, loaded.others.failed);
        assertEquals(Arrays.asList("own", "foreign"), http.getUids);
        assertEquals(3069d, loaded.item.getOthers().get(0).getRealAmount(), 0d);
    }

    @Test
    public void mixedPreviewBatchSavesOnlyOwnTruckAndLeavesReplicaByteForByteUntouched()
            throws Exception {
        RefuelItemData own = payload("own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 4, 8099,
                new Date(1_777_300_000_000L), new Date(1_777_300_600_000L));
        seedOwn(own);

        RefuelItemData foreign = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 7, 6685,
                new Date(1_777_300_248_000L), new Date(1_777_301_000_000L));
        foreign.setRawJson(new JSONObject(rawPayload(foreign))
                .put("ServerOnly", "must-survive").toString());
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(foreign));
        String before = repo.getRefuel("foreign").getJsonData();

        RefuelItemData ownEdit = repo.getRefuel("own").toRefuelItemData();
        ownEdit.setWeightNote("own-change");
        RefuelItemData attemptedForeignEdit = repo.getRefuel("foreign").toRefuelItemData();
        attemptedForeignEdit.setRealAmount(1);
        attemptedForeignEdit.setStartTime(new Date(0));

        assertTrue(DataHelper.postRefuels(
                Arrays.asList(ownEdit, attemptedForeignEdit), false));

        assertEquals("own-change", repo.getRefuel("own").toRefuelItemData().getWeightNote());
        assertEquals(before, repo.getRefuel("foreign").getJsonData());
        assertFalse(repo.getRefuel("foreign").isLocalModified());
    }

    @Test
    public void remoteReplicaNeverEntersBackgroundQueueEvenIfLegacyCodeMarksItDirty()
            throws Exception {
        RefuelItemData foreign = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 7, 6685,
                new Date(1_777_300_248_000L), new Date(1_777_301_000_000L));
        foreign.setRawJson(rawPayload(foreign));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(foreign));

        RefuelItem row = repo.getRefuel("foreign");
        row.setLocalModified(true); // mô phỏng dữ liệu để lại từ phiên bản cũ
        repo.insertRefuel(row);

        assertTrue(repo.getModifiedRefuel().isEmpty());
        DataHelper.syncModifiedRefuels();
        assertEquals(0, http.postCount);
    }

    @Test
    public void legacyRowWhoseColumnsAndJsonDisagreeFailsClosedOnEveryWritePath()
            throws Exception {
        RefuelItemData own = payload("identity-disagreement", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);

        RefuelItem row = repo.getRefuel("identity-disagreement");
        // Mô phỏng row legacy: payload còn nhận là own nhưng cột Room đã mang xe khác.
        row.setTruckNo("HAN3-20-7012");
        row.setTruckId(7012);
        row.setLocalModified(true);
        repo.insertRefuel(row);

        DataHelper.syncModifiedRefuels();
        assertEquals(0, http.postCount);

        RefuelItemData staleScreen = row.toRefuelItemData();
        staleScreen.setRealAmount(999);
        RefuelItemData rejected = DataHelper.postRefuel(staleScreen, true);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertEquals(0, http.postCount);
    }

    @Test
    public void directPostAndPatchCannotMutateRemoteReplica() throws Exception {
        RefuelItemData foreign = payload("foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 7, 6685,
                new Date(1_777_300_248_000L), new Date(1_777_301_000_000L));
        foreign.setRawJson(new JSONObject(rawPayload(foreign))
                .put("ServerOnly", "must-survive").toString());
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(foreign));
        String before = repo.getRefuel("foreign").getJsonData();

        RefuelItemData attempted = repo.getRefuel("foreign").toRefuelItemData();
        attempted.setRealAmount(1);
        RefuelItemData rejected = DataHelper.postRefuel(attempted, true);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertEquals(0, http.postCount);
        assertEquals(before, repo.getRefuel("foreign").getJsonData());

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        DataHelper.PatchResult patch = DataHelper.patchRefuel("foreign", latest -> {
            callbackRan.set(true);
            latest.setWeightNote("must-not-write");
        });
        assertFalse(patch.applied);
        assertEquals("FOREIGN_READ_ONLY", patch.reason);
        assertFalse(callbackRan.get());
        assertEquals(before, repo.getRefuel("foreign").getJsonData());
    }

    /** IN HỘ: số phiếu ghi được lên mẻ xe khác, nhưng chỉ sau khi server đã nhận. */
    @Test
    public void documentPatchWritesReceiptOnForeignRowOnlyAfterServerAccepts() throws Exception {
        RefuelItemData foreign = seedForeignPrintable("foreign-print");
        http.postResponse = repo.getRefuel("foreign-print").toRefuelItemData();

        DataHelper.PatchResult patch = DataHelper.patchRefuelDocument("foreign-print", latest -> {
            latest.setReceiptNumber("0000ABCD");
            latest.setReceiptUniqueId("receipt-uid");
            latest.setReceiptCount(latest.getReceiptCount() + 1);
            latest.setPrintStatus(RefuelItemData.ITEM_PRINT_STATUS.SUCCESS);
        });

        assertTrue(patch.applied);
        assertEquals(1, http.postCount);

        RefuelItem stored = repo.getRefuel("foreign-print");
        assertEquals("0000ABCD", stored.toRefuelItemData().getReceiptNumber());
        assertTrue("vẫn là replica của xe khác", stored.isRemoteReplica());
        assertFalse("server đã có bản này, không đưa vào hàng đợi gửi nền",
                stored.isLocalModified());
        assertEquals(foreign.getRealAmount(),
                stored.toRefuelItemData().getRealAmount(), 0d);
    }

    /** Server từ chối thì KHÔNG được để lại dấu đã in trong máy — lượt pull sẽ xoá nó. */
    @Test
    public void documentPatchLeavesForeignRowUntouchedWhenServerRejects() throws Exception {
        seedForeignPrintable("foreign-reject");
        String before = repo.getRefuel("foreign-reject").getJsonData();
        http.postResponse = null;

        DataHelper.PatchResult patch = DataHelper.patchRefuelDocument("foreign-reject",
                latest -> latest.setReceiptNumber("0000ABCD"));

        assertFalse(patch.applied);
        assertEquals("REMOTE_PUSH_FAILED", patch.reason);
        assertEquals(before, repo.getRefuel("foreign-reject").getJsonData());
    }

    /** Cửa chứng từ KHÔNG được biến thành đường ghi số liệu mẻ của xe khác. */
    @Test
    public void documentPatchRejectsNonDocumentFieldOnForeignRow() throws Exception {
        seedForeignPrintable("foreign-guard");
        String before = repo.getRefuel("foreign-guard").getJsonData();

        DataHelper.PatchResult patch = DataHelper.patchRefuelDocument("foreign-guard", latest -> {
            latest.setReceiptNumber("0000ABCD");
            latest.setParkingLot("SÂN 12");
        });

        assertFalse(patch.applied);
        assertEquals("FOREIGN_NON_DOCUMENT_FIELD", patch.reason);
        assertEquals(0, http.postCount);
        assertEquals(before, repo.getRefuel("foreign-guard").getJsonData());
    }

    private RefuelItemData seedForeignPrintable(String uid) throws Exception {
        RefuelItemData foreign = payload(uid, "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 7, 6685,
                new Date(1_777_300_248_000L), new Date(1_777_301_000_000L));
        foreign.setRawJson(rawPayload(foreign));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(foreign));
        return foreign;
    }

    @Test
    public void staleCurrentSnapshotCannotClearReplicaAfterServerReassignment() throws Exception {
        RefuelItemData own = payload("reassigned", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100,
                new Date(1000), null);
        seedOwn(own);
        RefuelItemData staleScreen = repo.getRefuel("reassigned").toRefuelItemData();

        RefuelItemData reassigned = payload("reassigned", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 3, 200,
                new Date(2000), null);
        reassigned.setRawJson(rawPayload(reassigned));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(reassigned));
        String serverCopy = repo.getRefuel("reassigned").getJsonData();

        staleScreen.setRealAmount(999);
        RefuelItemData rejected = DataHelper.postRefuel(staleScreen, false);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertEquals(0, http.postCount);
        assertTrue(repo.getRefuel("reassigned").isRemoteReplica());
        assertEquals(serverCopy, repo.getRefuel("reassigned").getJsonData());
    }

    @Test
    public void batchStaleCurrentAfterReassignmentReturnsFailureInsteadOfSilentSkip()
            throws Exception {
        RefuelItemData own = payload("batch-reassigned", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);
        RefuelItemData staleScreen = repo.getRefuel("batch-reassigned").toRefuelItemData();

        RefuelItemData reassigned = payload("batch-reassigned", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 3, 200, new Date(2000), null);
        reassigned.setRawJson(rawPayload(reassigned));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(reassigned));
        String authoritative = repo.getRefuel("batch-reassigned").getJsonData();

        staleScreen.setRealAmount(999);
        assertFalse(DataHelper.postRefuels(Collections.singletonList(staleScreen), false));
        assertEquals(authoritative, repo.getRefuel("batch-reassigned").getJsonData());
        assertEquals(0, http.postCount);
    }

    @Test
    public void postResponseCannotTouchReplicaReassignedWhileRequestWasInFlight()
            throws Exception {
        RefuelItemData own = payload("race", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);
        RefuelItemData edit = repo.getRefuel("race").toRefuelItemData();
        edit.setRealAmount(150);

        RefuelItemData reassigned = payload("race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 10, 200, new Date(2000), null);
        reassigned.setRawJson(new JSONObject(rawPayload(reassigned))
                .put("ServerRole", "foreign-authoritative").toString());
        String authoritative = reassigned.getRawJson();
        http.onPostRefuel = () -> repo.replaceRemoteRefuelSnapshots(
                Collections.singletonList(reassigned));
        http.postResponse = payload("race", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 3, 150, new Date(1000), null);

        RefuelItemData result = DataHelper.postRefuel(edit, true);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, result.getSaveOutcome());
        RefuelItem stored = repo.getRefuel("race");
        assertTrue(stored.isRemoteReplica());
        assertFalse(stored.isLocalModified());
        assertEquals(authoritative, stored.getJsonData());
    }

    @Test
    public void directPostRejectsAckForDifferentUidWithoutChangingIdentity() throws Exception {
        RefuelItemData own = payload("direct-own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);
        int originalId = repo.getRefuel("direct-own").getId();

        RefuelItemData edit = repo.getRefuel("direct-own").toRefuelItemData();
        edit.setRealAmount(150);
        RefuelItemData wrongAck = payload("another-current-row", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 9, 150, new Date(1000), null);
        wrongAck.setApplied(true);
        http.postResponse = wrongAck;

        DataHelper.postRefuel(edit, true);

        RefuelItem stored = repo.getRefuel("direct-own");
        assertNotNull(stored);
        assertEquals(originalId, stored.getId());
        assertEquals("direct-own", stored.getUniqueId());
        assertTrue(stored.isLocalModified());
        assertNull(repo.getRefuel("another-current-row"));
    }

    @Test
    public void backgroundPostResponseCannotTouchReplicaReassignedWhileRequestWasInFlight()
            throws Exception {
        RefuelItemData own = payload("background-race", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);
        RefuelItem dirty = repo.getRefuel("background-race");
        dirty.setLocalModified(true);
        repo.insertRefuel(dirty);

        RefuelItemData reassigned = payload("background-race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 10, 200, new Date(2000), null);
        reassigned.setRawJson(new JSONObject(rawPayload(reassigned))
                .put("ServerRole", "foreign-authoritative-background").toString());
        String authoritative = reassigned.getRawJson();
        http.onPostRefuel = () -> repo.replaceRemoteRefuelSnapshots(
                Collections.singletonList(reassigned));
        http.postResponse = payload("background-race", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 3, 100, new Date(1000), null);

        DataHelper.syncModifiedRefuels();

        assertEquals(1, http.postCount);
        RefuelItem stored = repo.getRefuel("background-race");
        assertTrue(stored.isRemoteReplica());
        assertFalse(stored.isLocalModified());
        assertEquals(authoritative, stored.getJsonData());
    }

    @Test
    public void backgroundPostRejectsAckForDifferentUidAndKeepsQueueDirty() throws Exception {
        RefuelItemData own = payload("background-own", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 2, 100, new Date(1000), null);
        seedOwn(own);
        RefuelItem dirty = repo.getRefuel("background-own");
        int originalId = dirty.getId();
        dirty.setLocalModified(true);
        repo.insertRefuel(dirty);

        RefuelItemData wrongAck = payload("background-other", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.PROCESSING, 9, 100, new Date(1000), null);
        wrongAck.setApplied(true);
        http.postResponse = wrongAck;

        DataHelper.syncModifiedRefuels();

        RefuelItem stored = repo.getRefuel("background-own");
        assertEquals(originalId, stored.getId());
        assertEquals("background-own", stored.getUniqueId());
        assertTrue(stored.isLocalModified());
        assertEquals(1, repo.getModifiedRefuel().size());
    }

    @Test
    public void unknownOwnershipIsFailClosedForPostAndPatch() throws Exception {
        RefuelItemData unknownNew = payload("unknown-new", null, 0,
                REFUEL_ITEM_STATUS.PROCESSING, 1, 10, new Date(1000), null);
        RefuelItemData rejected = DataHelper.postRefuel(unknownNew, false);
        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertNull(repo.getRefuel("unknown-new"));
        assertEquals(0, http.postCount);

        RefuelItemData unknownStored = payload("unknown-stored", null, 0,
                REFUEL_ITEM_STATUS.PROCESSING, 1, 20, new Date(1000), null);
        RefuelItem row = RefuelItem.fromRefuelItemData(unknownStored);
        row.setLocalModified(false);
        repo.insertRefuel(row);
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        DataHelper.PatchResult patch = DataHelper.patchRefuel("unknown-stored", latest -> {
            callbackRan.set(true);
            latest.setWeightNote("must-not-write");
        });
        assertFalse(patch.applied);
        assertEquals("OWNERSHIP_UNKNOWN", patch.reason);
        assertFalse(callbackRan.get());
    }

    @Test
    public void staleMemberIsKeptWhileOtherMembersAdvanceInSameTransaction() throws Exception {
        RefuelItemData first = payload("foreign-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 5, 100,
                new Date(1000), new Date(2000));
        RefuelItemData second = payload("foreign-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 5, 200,
                new Date(1000), new Date(2000));
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));
        assertEquals(2, repo.replaceRemoteRefuelSnapshots(Arrays.asList(first, second)));

        RefuelItemData firstNew = payload("foreign-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 6, 101,
                new Date(3000), new Date(4000));
        RefuelItemData secondStale = payload("foreign-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.PROCESSING, 4, 1,
                new Date(3000), null);
        firstNew.setRawJson(rawPayload(firstNew));
        secondStale.setRawJson(rawPayload(secondStale));

        assertEquals(2, repo.replaceRemoteRefuelSnapshots(Arrays.asList(firstNew, secondStale)));
        assertEquals(101d, repo.getRefuel("foreign-1").toRefuelItemData().getRealAmount(), 0d);
        assertEquals(200d, repo.getRefuel("foreign-2").toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void modifiedPullStartedBeforeDetailCannotOverwriteNewerDetailWithoutRevision()
            throws Exception {
        int pullToken = DataHelper.beginUnscopedRefuelRead();

        RefuelItemData newest = payload("foreign-race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 500, new Date(3000), new Date(4000));
        newest.setClientSeq(0);
        newest.setDateUpdated(new Date(20_000));
        newest.setRawJson(rawPayload(newest));
        http.responses.put("foreign-race", newest);
        assertNotNull(DataHelper.getRefuelItem("foreign-race"));

        RefuelItemData stale = payload("foreign-race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 100, new Date(1000), new Date(2000));
        stale.setClientSeq(0);
        stale.setDateUpdated(new Date(10_000));
        stale.setRawJson(rawPayload(stale));

        assertFalse(DataHelper.applyModifiedRefuelBatch(
                Collections.singletonList(stale), pullToken));
        assertEquals(500d,
                repo.getRefuel("foreign-race").toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void olderRootCannotDowngradeSharedChildCommittedByNewerDifferentRoot()
            throws Exception {
        RefuelItemData rootA = payload("root-a", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 1, 10, new Date(1000), new Date(2000));
        RefuelItemData rootB = payload("root-b", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 1, 20, new Date(1000), new Date(2000));
        seedOwn(rootA);
        seedOwn(rootB);

        RefuelItemData oldChild = payload("shared-child", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 100, new Date(1000), new Date(2000));
        oldChild.setClientSeq(0);
        oldChild.setDateUpdated(new Date(10_000));
        oldChild.setRawJson(rawPayload(oldChild));
        rootA.setRawJson(rawPayload(rootA));
        rootA.setOthers(Collections.singletonList(oldChild));
        rootA.setCompleteOthersSnapshot(true);

        RefuelItemData newChild = payload("shared-child", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 200, new Date(3000), new Date(4000));
        newChild.setClientSeq(0);
        newChild.setDateUpdated(new Date(20_000));
        newChild.setRawJson(rawPayload(newChild));
        rootB.setRawJson(rawPayload(rootB));
        rootB.setOthers(Collections.singletonList(newChild));
        rootB.setCompleteOthersSnapshot(true);

        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        http.getHandler = uid -> {
            if ("root-a".equals(uid)) {
                oldStarted.countDown();
                try {
                    releaseOld.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return rootA;
            }
            return rootB;
        };

        Thread older = new Thread(() -> DataHelper.getRefuelItem("root-a"));
        older.start();
        assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
        assertNotNull(DataHelper.getRefuelItem("root-b"));
        releaseOld.countDown();
        older.join(2000);

        assertEquals(200d,
                repo.getRefuel("shared-child").toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void oldPullTombstoneCannotDeleteDetailCommittedAfterPullStarted()
            throws Exception {
        int pullToken = DataHelper.beginUnscopedRefuelRead();
        RefuelItemData newest = payload("delete-race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 500, new Date(3000), new Date(4000));
        newest.setClientSeq(0);
        newest.setDateUpdated(new Date(20_000));
        newest.setRawJson(rawPayload(newest));
        http.responses.put("delete-race", newest);
        assertNotNull(DataHelper.getRefuelItem("delete-race"));

        RefuelItemData oldDelete = payload("delete-race", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 0, null, null);
        oldDelete.setClientSeq(0);
        oldDelete.setDeleted(true);
        oldDelete.setDateUpdated(new Date(10_000));

        assertFalse(DataHelper.applyModifiedRefuelBatch(
                Collections.singletonList(oldDelete), pullToken));
        assertNotNull(repo.getRefuel("delete-race"));
    }

    @Test
    public void olderDateUpdatedIsKeptWhenLegacyServerHasNoRevision() throws Exception {
        RefuelItemData newest = payload("foreign-date", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 500, new Date(3000), new Date(4000));
        newest.setClientSeq(0);
        newest.setDateUpdated(new Date(20_000));
        newest.setRawJson(rawPayload(newest));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(newest));

        RefuelItemData stale = payload("foreign-date", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 100, new Date(1000), new Date(2000));
        stale.setClientSeq(0);
        stale.setDateUpdated(new Date(10_000));
        stale.setRawJson(rawPayload(stale));
        DataRepository.RemoteSnapshotResult result = repo
                .replaceRemoteRefuelSnapshotsDetailed(Collections.singletonList(stale));

        assertEquals(0, result.applied);
        assertEquals(1, result.keptNewer);
        assertEquals(500d,
                repo.getRefuel("foreign-date").toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void failedRemotePullDoesNotCreateFlight() throws Exception {
        RefuelItemData malformed = payload("foreign-malformed", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        malformed.setRawJson("{not-json");

        assertFalse(DataHelper.applyModifiedRefuelBatch(
                Collections.singletonList(malformed),
                DataHelper.beginUnscopedRefuelRead()));
        assertNull(repo.getRefuel("foreign-malformed"));
        assertTrue(db.flightDao().getAll().isEmpty());
    }

    @Test
    public void invalidChildRollsBackRootAndMembershipAsOneSnapshot() throws Exception {
        RefuelItemData own = payload("atomic-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 5, 500, new Date(1000), new Date(2000));
        seedOwn(own);
        RefuelItemData child = payload("atomic-child", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 5, 100, new Date(1000), new Date(2000));
        child.setRawJson(rawPayload(child));
        own.setRawJson(rawPayload(own));
        own.setOthers(Collections.singletonList(child));
        own.setCompleteOthersSnapshot(true);
        http.responses.put("atomic-root", own);
        assertNotNull(DataHelper.getRefuelItem("atomic-root"));

        String rootBefore = repo.getRefuel("atomic-root").getJsonData();
        String childBefore = repo.getRefuel("atomic-child").getJsonData();
        List<String> membershipBefore = repo.getRemoteOthersMembership("atomic-root");

        RefuelItemData changedRoot = payload("atomic-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 6, 999, new Date(3000), new Date(4000));
        changedRoot.setRawJson(rawPayload(changedRoot));
        RefuelItemData invalidChild = payload("atomic-child", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 6, 999, new Date(3000), new Date(4000));
        invalidChild.setRawJson("{not-json");
        changedRoot.setOthers(Collections.singletonList(invalidChild));
        changedRoot.setCompleteOthersSnapshot(true);
        http.responses.put("atomic-root", changedRoot);

        RefuelItemData fallback = DataHelper.getRefuelItem("atomic-root");

        assertNotNull(fallback);
        assertEquals(rootBefore, repo.getRefuel("atomic-root").getJsonData());
        assertEquals(childBefore, repo.getRefuel("atomic-child").getJsonData());
        assertEquals(membershipBefore, repo.getRemoteOthersMembership("atomic-root"));
    }

    @Test
    public void serverLocalIdCannotReplaceUnrelatedTabletRow() throws Exception {
        RefuelItemData own = payload("unrelated-local", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 1, 50, new Date(1000), new Date(2000));
        seedOwn(own);
        int occupiedLocalId = repo.getRefuel("unrelated-local").getLocalId();

        RefuelItemData foreign = payload("foreign-localid", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        foreign.setLocalId(occupiedLocalId);
        foreign.setRawJson(rawPayload(foreign));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(foreign));

        assertNotNull(repo.getRefuel("unrelated-local"));
        assertNotNull(repo.getRefuel("foreign-localid"));
        assertTrue(repo.getRefuel("foreign-localid").getLocalId() != occupiedLocalId);
    }

    @Test
    public void duplicateServerIdsInsideOneReplicaBatchAreRejectedAtomically()
            throws Exception {
        RefuelItemData first = payload("duplicate-id-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        RefuelItemData second = payload("duplicate-id-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 1, 200, new Date(1000), new Date(2000));
        first.setId(12345);
        second.setId(12345);
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));

        boolean rejected = false;
        try {
            repo.replaceRemoteRefuelSnapshots(Arrays.asList(first, second));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        assertTrue(rejected);
        assertNull(repo.getRefuel("duplicate-id-1"));
        assertNull(repo.getRefuel("duplicate-id-2"));
    }

    @Test
    public void modifiedCursorIsIndependentFromRoomMaxDateUpdated() throws Exception {
        RefuelItemData pulled = payload("cursor-server", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        pulled.setDateUpdated(new Date(1_000_000));
        assertTrue(DataHelper.commitRefuelPullCursor(Collections.singletonList(pulled)));
        Date expectedQueryCursor = new Date(700_000);
        assertEquals(expectedQueryCursor, DataHelper.readRefuelPullCursor());

        RefuelItemData local = payload("cursor-local", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        local.setDateUpdated(new Date(9_000_000));
        seedOwn(local);

        assertEquals("DateUpdated của Room không được đẩy cursor endpoint",
                expectedQueryCursor, DataHelper.readRefuelPullCursor());
    }

    @Test
    public void fullReplaceRejectsUidWhoseServerIdBelongsToAnotherRow() throws Exception {
        RefuelItemData first = payload("collision-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 5, 100, new Date(1000), new Date(2000));
        RefuelItemData second = payload("collision-2", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 5, 200, new Date(1000), new Date(2000));
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));
        repo.replaceRemoteRefuelSnapshots(Arrays.asList(first, second));
        String firstRaw = repo.getRefuel("collision-1").getJsonData();
        String secondRaw = repo.getRefuel("collision-2").getJsonData();

        RefuelItemData collision = payload("collision-1", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 6, 999, new Date(3000), new Date(4000));
        collision.setId(second.getId());
        collision.setRawJson(rawPayload(collision));
        boolean rejected = false;
        try {
            repo.replaceRemoteRefuelSnapshots(Collections.singletonList(collision));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        assertTrue(rejected);
        assertEquals(firstRaw, repo.getRefuel("collision-1").getJsonData());
        assertEquals(secondRaw, repo.getRefuel("collision-2").getJsonData());
    }

    @Test
    public void legacyForeignRowMissingTimesIsQuarantinedWithoutInventingCurrentTime()
            throws Exception {
        RefuelItemData legacy = payload("legacy-foreign", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.PROCESSING, 0, 100, null, null);
        RefuelItem row = RefuelItem.fromRefuelItemData(legacy);
        row.setJsonData(rawPayload(legacy));
        row.setStartTime(null);
        row.setEndTime(null);
        row.setRemoteReplica(false); // trạng thái ngay sau migration 13→14
        row.setLocalModified(true);
        row.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);
        repo.insertRefuel(row);

        RefuelItemData beforeMaintenance = repo.getRefuel("legacy-foreign").toRefuelItemData();
        assertNull(beforeMaintenance.getStartTime());
        assertNull(beforeMaintenance.getEndTime());
        assertEquals(1, DataHelper.quarantineLegacyForeignRefuels());

        RefuelItem quarantined = repo.getRefuel("legacy-foreign");
        assertTrue(quarantined.isRemoteReplica());
        assertFalse(quarantined.isLocalModified());
        assertNull(quarantined.toRefuelItemData().getStartTime());
        assertNull(quarantined.toRefuelItemData().getEndTime());
    }

    @Test
    public void staleCurrentRootCannotDowngradeCompleteOthersMembership() throws Exception {
        RefuelItemData root = payload("own-stale-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 10, 100, new Date(1000), new Date(2000));
        RefuelItemData first = payload("member-a", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 10, 200, new Date(1000), new Date(2000));
        RefuelItemData second = payload("member-b", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 10, 300, new Date(1000), new Date(2000));
        root.setDateUpdated(new Date(20_000));
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));
        root.setRawJson(rawPayload(root));
        root.setOthers(Arrays.asList(first, second));
        root.setCompleteOthersSnapshot(true);
        http.responses.put(root.getUniqueId(), root);
        assertTrue(DataHelper.loadRefuelForPreview(root.getUniqueId())
                .others.collectionComplete);
        assertEquals(Arrays.asList("member-a", "member-b"),
                repo.getRemoteOthersMembership(root.getUniqueId()));

        RefuelItemData stale = payload("own-stale-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 9, 1, new Date(1000), new Date(2000));
        stale.setDateUpdated(new Date(10_000));
        stale.setRawJson(rawPayload(stale));
        stale.setOthers(Collections.singletonList(first));
        stale.setCompleteOthersSnapshot(true);
        http.responses.put(stale.getUniqueId(), stale);

        DataHelper.PreviewLoadResult result = DataHelper.loadRefuelForPreview(stale.getUniqueId());

        assertFalse("root cũ không được xác nhận collection là snapshot mới",
                result.others.collectionComplete);
        assertEquals(Arrays.asList("member-a", "member-b"),
                repo.getRemoteOthersMembership(stale.getUniqueId()));
    }

    @Test
    public void staleCurrentModifiedPullCannotDowngradeMembershipOrFlight() throws Exception {
        RefuelItemData root = payload("own-pull-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 10, 100, new Date(1000), new Date(2000));
        RefuelItemData first = payload("pull-member-a", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 10, 200, new Date(1000), new Date(2000));
        RefuelItemData second = payload("pull-member-b", "HAN3-20-7009", 7009,
                REFUEL_ITEM_STATUS.DONE, 10, 300, new Date(1000), new Date(2000));
        first.setRawJson(rawPayload(first));
        second.setRawJson(rawPayload(second));
        root.setParkingLot("NEW");
        root.setDateUpdated(new Date(20_000));
        root.setRawJson(rawPayload(root));
        root.setOthers(Arrays.asList(first, second));
        root.setCompleteOthersSnapshot(true);
        assertTrue(DataHelper.applyModifiedRefuelBatch(Collections.singletonList(root),
                DataHelper.beginUnscopedRefuelRead()));
        assertEquals("NEW", db.flightDao().get(FLIGHT_ID, 0).getParkingLot());

        RefuelItemData stale = payload("own-pull-root", OWN_TRUCK, OWN_TRUCK_ID,
                REFUEL_ITEM_STATUS.DONE, 9, 1, new Date(1000), new Date(2000));
        stale.setParkingLot("OLD");
        stale.setDateUpdated(new Date(10_000));
        stale.setRawJson(rawPayload(stale));
        stale.setOthers(Collections.singletonList(first));
        stale.setCompleteOthersSnapshot(true);
        assertTrue(DataHelper.applyModifiedRefuelBatch(Collections.singletonList(stale),
                DataHelper.beginUnscopedRefuelRead()));

        assertEquals(Arrays.asList("pull-member-a", "pull-member-b"),
                repo.getRemoteOthersMembership(root.getUniqueId()));
        assertEquals("NEW", db.flightDao().get(FLIGHT_ID, 0).getParkingLot());
    }

    @Test
    public void tombstoneWithMismatchedServerIdCannotDeleteUidRow() throws Exception {
        RefuelItemData stored = payload("delete-id-guard", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 100, new Date(1000), new Date(2000));
        stored.setId(555);
        stored.setDateUpdated(new Date(10_000));
        stored.setRawJson(rawPayload(stored));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(stored));

        RefuelItemData tombstone = payload("delete-id-guard", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 2, 0, null, null);
        tombstone.setId(999);
        tombstone.setDeleted(true);
        tombstone.setDateUpdated(new Date(20_000));

        assertFalse(DataHelper.applyModifiedRefuelBatch(Collections.singletonList(tombstone),
                DataHelper.beginUnscopedRefuelRead()));
        assertEquals(555, repo.getRefuel("delete-id-guard").getId());
    }

    @Test
    public void positiveRevisionSnapshotWinsLegacyZeroDespiteOlderDate() throws Exception {
        RefuelItemData legacy = payload("legacy-revision", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 100, new Date(1000), new Date(2000));
        legacy.setClientSeq(0);
        legacy.setDateUpdated(new Date(20_000));
        legacy.setRawJson(rawPayload(legacy));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(legacy));

        RefuelItemData versioned = payload("legacy-revision", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 200, new Date(3000), new Date(4000));
        versioned.setDateUpdated(new Date(10_000));
        versioned.setRawJson(rawPayload(versioned));

        assertEquals(1, repo.replaceRemoteRefuelSnapshots(Collections.singletonList(versioned)));
        assertEquals(200d, repo.getRefuel("legacy-revision")
                .toRefuelItemData().getRealAmount(), 0d);
        assertEquals(1, repo.getRefuel("legacy-revision").getServerRevision());
    }

    @Test
    public void positiveRevisionTombstoneWinsLegacyZeroDespiteOlderDate() throws Exception {
        RefuelItemData legacy = payload("legacy-delete", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 0, 100, new Date(1000), new Date(2000));
        legacy.setClientSeq(0);
        legacy.setDateUpdated(new Date(20_000));
        legacy.setRawJson(rawPayload(legacy));
        repo.replaceRemoteRefuelSnapshots(Collections.singletonList(legacy));

        RefuelItemData tombstone = payload("legacy-delete", "HAN3-20-7012", 7012,
                REFUEL_ITEM_STATUS.DONE, 1, 0, null, null);
        tombstone.setId(legacy.getId());
        tombstone.setDeleted(true);
        tombstone.setDateUpdated(new Date(10_000));

        assertTrue(DataHelper.applyModifiedRefuelBatch(Collections.singletonList(tombstone),
                DataHelper.beginUnscopedRefuelRead()));
        assertNull(repo.getRefuel("legacy-delete"));
    }

    private void seedOwn(RefuelItemData data) {
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(false);
        row.setLocalModified(false);
        repo.insertRefuel(row);
    }

    private static RefuelItemData payload(String uid, String truckNo, int truckId,
                                          REFUEL_ITEM_STATUS status, int revision,
                                          double amount, Date start, Date end) {
        RefuelItemData item = new RefuelItemData();
        item.setId(Math.abs(uid.hashCode()) + 1);
        item.setUniqueId(uid);
        item.setFlightId(FLIGHT_ID);
        item.setFlightUniqueId(FLIGHT_UID);
        item.setFlightCode("3S 621");
        item.setTruckNo(truckNo);
        item.setTruckId(truckId);
        item.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        item.setStatus(status);
        item.setServerRevision(revision);
        item.setClientSeq(revision);
        item.setRealAmount(amount);
        item.setStartNumber(1000);
        item.setEndNumber(1000 + amount);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setRefuelTime(new Date(1_777_300_000_000L));
        return item;
    }

    /** Raw không mang Others; test tự gắn collection khi cần. */
    private static String rawPayload(RefuelItemData item) throws Exception {
        JSONObject raw = new JSONObject(item.toJson());
        raw.remove("Others");
        if (item.getStartTime() == null) raw.remove("StartTime");
        if (item.getEndTime() == null) raw.remove("EndTime");
        return raw.toString();
    }

    private static final class FakeHttpClient extends HttpClient {
        interface GetHandler {
            RefuelItemData get(String uniqueId);
        }

        RefuelItemData remoteItem;
        RefuelItemData postResponse;
        Runnable onPostRefuel;
        GetHandler getHandler;
        int postCount;
        final Map<String, RefuelItemData> responses = new HashMap<>();
        final List<String> getUids = new ArrayList<>();

        FakeHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            getUids.add(uniqueId);
            if (getHandler != null) return getHandler.get(uniqueId);
            if (responses.containsKey(uniqueId)) return responses.get(uniqueId);
            return remoteItem;
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            postCount++;
            if (onPostRefuel != null) {
                Runnable callback = onPostRefuel;
                onPostRefuel = null;
                callback.run();
            }
            if (postResponse != null)
                postResponse.setClientSeq(refuelData.getClientSeq());
            return postResponse;
        }

        @Override
        public List<RefuelItemData> getModifiedRefuels(Integer type, Date lastModified) {
            return null;
        }
    }
}
