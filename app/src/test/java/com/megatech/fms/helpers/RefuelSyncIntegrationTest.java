package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.DataRepository;
import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/**
 * Test tích hợp Room cho luồng đồng bộ phiếu tra nạp.
 *
 * <p>Dependency của {@link DataHelper} là static toàn cục nên các test ở đây
 * <b>không được chạy song song</b>; mỗi test cài dependency giả rồi reset trong
 * {@code @After}.
 */
@RunWith(RobolectricTestRunner.class)
// Chỉ định thẳng Application: DataHelper khởi tạo dependency mặc định ngay khi class load
// và cần FMSApplication.getApplication() khác null.
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelSyncIntegrationTest {

    private static final String UID = "37906193-346a-448f-91ed-4f5cf9dae271";

    private AppDatabase db;
    private DataRepository repo;
    private FakeHttpClient http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        http = new FakeHttpClient();

        DataHelper.installTestDependencies(repo, http);
        // Chặn sync nền để test chỉ kiểm chứng phần ghi/đọc local.
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    /**
     * Ca 2: khi bản server mới hơn được chấp nhận thì Room phải mang đúng revision đó,
     * nếu không snapshot trên màn hình sẽ đứng trên một phiên bản không tồn tại ở local.
     */
    @Test
    public void newerRemoteRevisionIsPersistedIntoRoom() {
        seedRow(1110, 60166240, 5, 8, false);

        http.remoteItem = payload(1110, 60166240, 9, 7);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertNotNull(loaded);
        assertEquals(7, loaded.getServerRevision());

        RefuelItem stored = repo.getRefuel(UID);
        assertEquals(7, stored.getServerRevision());
        assertEquals(stored.getLocalId(), loaded.getLocalId());
        assertTrue(loaded.getLocalId() > 0);
    }

    /** Bản server cũ hơn không được thay bản local. */
    @Test
    public void olderRemoteRevisionKeepsLocalRow() {
        seedRow(1110, 60166240, 5, 8, false);

        http.remoteItem = payload(862, 60165992, 9, 4);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals(1110d, loaded.getRealAmount(), 0d);
        assertEquals(60166240d, loaded.getEndNumber(), 0d);
        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /**
     * Ca 3 + 6: row đang giữ conflict (postStatus = ERROR) không nằm trong hàng đợi
     * đồng bộ tự động, và vì trạng thái nằm trong DB nên khởi động lại app cũng không
     * làm nó tự retry.
     */
    @Test
    public void conflictRowIsExcludedFromAutoSyncQueueAcrossRestart() {
        RefuelItem row = seedRow(1110, 60166240, 5, 8, true);
        row.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);
        repo.insertRefuel(row);

        assertTrue(repo.getModifiedRefuel().isEmpty());
        assertEquals(1, repo.getAllModifiedRefuel().size());

        // "Khởi động lại": repository mới trên cùng database.
        DataRepository afterRestart = DataRepository.forTesting(db);
        assertTrue(afterRestart.getModifiedRefuel().isEmpty());
        assertEquals(1, afterRestart.getAllModifiedRefuel().size());
    }

    /** Ca 7: người dùng lưu lại thành công thì row quay lại hàng đợi. */
    @Test
    public void savingAgainReturnsConflictRowToTheQueue() {
        RefuelItem row = seedRow(1110, 60166240, 5, 8, true);
        row.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);
        repo.insertRefuel(row);
        assertTrue(repo.getModifiedRefuel().isEmpty());

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID,
                latest -> latest.setLeaveTime(new Date(1_769_000_100_000L)));

        assertTrue(result.applied);
        assertEquals(1, repo.getModifiedRefuel().size());
        assertEquals(RefuelItem.ITEM_POST_STATUS.NONE, repo.getRefuel(UID).getPostStatus());
    }

    /**
     * Ca 4: patch metadata chỉ được đụng đúng trường của nó — số đồng hồ, lượng,
     * FlightId, density và số hoá đơn phải nguyên vẹn.
     */
    @Test
    public void patchLeaveTimeDoesNotTouchBusinessFields() {
        seedRow(1110, 60166240, 5, 8, false);
        Date leaveTime = new Date(1_769_000_100_000L);

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID,
                latest -> latest.setLeaveTime(leaveTime));

        assertTrue(result.applied);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(leaveTime, saved.getLeaveTime());
        assertEquals(1110d, saved.getRealAmount(), 0d);
        assertEquals(60166240d, saved.getEndNumber(), 0d);
        assertEquals(60165130d, saved.getStartNumber(), 0d);
        assertEquals(1265515, saved.getFlightId());
        assertEquals(0.781d, saved.getDensity(), 0d);
        assertEquals("24411", saved.getInvoiceNumber());
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
        // Ghi thành công ⇒ seq tăng đúng 1 so với row đang lưu.
        assertEquals(9, saved.getClientSeq());
    }

    /** Patch không được phép đụng vào số liệu chốt của mẻ. */
    @Test
    public void patchTouchingFinalValuesIsRejected() {
        seedRow(1110, 60166240, 5, 8, false);

        DataHelper.PatchResult result = DataHelper.patchRefuel(UID,
                latest -> latest.setEndNumber(60165992));

        assertFalse(result.applied);
        assertEquals("FINAL_VALUES_TOUCHED", result.reason);
        assertEquals(60166240d, repo.getRefuel(UID).toRefuelItemData().getEndNumber(), 0d);
    }

    /** Snapshot cũ không được ghi đè số liệu vừa chốt. */
    @Test
    public void staleSnapshotCannotOverwriteConfirmedMeterValues() {
        seedRow(1110, 60166240, 5, 8, false);

        // Màn hình khác còn giữ bản đọc ở seq 7 với số liệu cũ.
        RefuelItemData stale = payload(862, 60165992, 7, 5);
        stale.setBaseClientSeq(7);
        stale.setBaseServerRevision(5);
        stale.setUniqueId(UID);
        stale.setId(2087328);

        DataHelper.postRefuel(stale, false);

        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(1110d, stored.getRealAmount(), 0d);
        assertEquals(60166240d, stored.getEndNumber(), 0d);
    }

    // =====================================================================
    // Offline-first: payload phải nằm trong Room TRƯỚC khi HTTP được gọi
    // =====================================================================

    /** Trong lúc request đang bay, Room đã phải chứa payload mới và ở trạng thái chờ gửi. */
    @Test
    public void payloadIsPersistedBeforeHttpCall() {
        seedRow(862, 60165992, 5, 8, false);

        final double[] amountDuringFlight = {-1};
        final boolean[] dirtyDuringFlight = {false};
        http.onPostRefuel = () -> {
            RefuelItem inFlight = repo.getRefuel(UID);
            amountDuringFlight[0] = inFlight.toRefuelItemData().getRealAmount();
            dirtyDuringFlight[0] = inFlight.isLocalModified();
        };
        http.postResponse = payload(1110, 60166240, 9, 6);

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        assertEquals("Room phải có 1110 ngay khi request đang bay", 1110d, amountDuringFlight[0], 0d);
        assertTrue("row phải đang chờ gửi trong lúc request bay", dirtyDuringFlight[0]);
    }

    /** ACK về ⇒ Room giữ payload đã gửi và được đánh dấu đã đồng bộ. */
    @Test
    public void ackKeepsPersistedPayloadAndClearsDirty() {
        seedRow(862, 60165992, 5, 8, false);
        http.postResponse = payload(1110, 60166240, 9, 6);

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(1110d, row.toRefuelItemData().getRealAmount(), 0d);
        assertEquals(60166240d, row.toRefuelItemData().getEndNumber(), 0d);
        assertFalse(row.isLocalModified());
    }

    /** Không có response (mất mạng) ⇒ payload vẫn nằm trong Room và còn trong hàng đợi. */
    @Test
    public void nullResponseKeepsPayloadQueued() {
        seedRow(862, 60165992, 5, 8, false);
        http.postResponse = null;

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(1110d, row.toRefuelItemData().getRealAmount(), 0d);
        assertTrue(row.isLocalModified());
        assertEquals(1, repo.getModifiedRefuel().size());
    }

    /** Response không xác nhận được ⇒ giữ payload local, vẫn dirty. */
    @Test
    public void nonAckResponseKeepsPayloadDirty() {
        seedRow(862, 60165992, 5, 8, false);
        // Server trả về bản cũ 862 — đúng hình dạng sự cố đang xử lý.
        http.postResponse = payload(862, 60165992, 9, 6);

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals("payload local không được thay bằng bản cũ của server",
                1110d, row.toRefuelItemData().getRealAmount(), 0d);
        assertEquals(60166240d, row.toRefuelItemData().getEndNumber(), 0d);
        assertTrue(row.isLocalModified());
    }

    /**
     * Người dùng sửa tiếp thành 1111 trong lúc request 1110 đang bay: response của
     * request cũ không được xoá dirty và cũng không được kéo dữ liệu về 1110.
     */
    @Test
    public void responseOfOlderRequestDoesNotOverwriteNewerLocalEdit() {
        seedRow(862, 60165992, 5, 8, false);

        http.onPostRefuel = () -> DataHelper.postRefuel(newAmount(1111, 60166241), false);
        http.postResponse = payload(1110, 60166240, 9, 6);

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(1111d, row.toRefuelItemData().getRealAmount(), 0d);
        assertEquals(60166241d, row.toRefuelItemData().getEndNumber(), 0d);
        assertTrue("bản sửa mới hơn vẫn phải nằm trong hàng đợi", row.isLocalModified());
    }

    // =====================================================================
    // getRefuelItem(id, localId) — cùng quy tắc revision với biến thể uniqueId
    // =====================================================================

    @Test
    public void byIdNewerRemoteRevisionIsPersistedIntoRoom() {
        RefuelItem seeded = seedRow(1110, 60166240, 5, 8, false);
        http.remoteItem = payload(1110, 60166240, 9, 7);

        RefuelItemData loaded = DataHelper.getRefuelItem(2087328, seeded.getLocalId());

        assertEquals(7, loaded.getServerRevision());
        assertEquals(7, repo.getRefuel(UID).getServerRevision());
    }

    @Test
    public void byIdOlderRemoteRevisionKeepsLocalRow() {
        RefuelItem seeded = seedRow(1110, 60166240, 5, 8, false);
        http.remoteItem = payload(862, 60165992, 9, 4);

        RefuelItemData loaded = DataHelper.getRefuelItem(2087328, seeded.getLocalId());

        assertEquals(1110d, loaded.getRealAmount(), 0d);
        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void byIdResultCarriesLocalIdAndMatchingBaseVersion() {
        RefuelItem seeded = seedRow(1110, 60166240, 5, 8, false);
        http.remoteItem = payload(1110, 60166240, 9, 7);

        RefuelItemData loaded = DataHelper.getRefuelItem(2087328, seeded.getLocalId());

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(row.getLocalId(), loaded.getLocalId());
        assertEquals(row.getClientSeq(), loaded.getBaseClientSeq());
        assertEquals(row.getServerRevision(), loaded.getBaseServerRevision());
    }

    // =====================================================================
    // In-flight: không bao giờ có hai POST cùng một phiếu
    // =====================================================================

    /** Background sync chạy trong lúc direct POST đang bay ⇒ chỉ một request được gửi. */
    @Test
    public void backgroundSyncDoesNotPostWhileDirectRequestInFlight() {
        seedRow(862, 60165992, 5, 8, false);
        http.postResponse = payload(1110, 60166240, 9, 6);
        http.onPostRefuel = DataHelper::syncModifiedRefuels;   // background chen vào giữa

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        assertEquals("chỉ được có đúng một POST cho phiếu này", 1, http.postCount);
    }

    /**
     * Direct save thứ hai trong lúc request đầu đang bay: payload mới vẫn được ghi Room,
     * nhưng không gửi song song; response của request cũ không xoá dirty.
     */
    @Test
    public void secondDirectSaveIsPersistedButNotSentInParallel() {
        seedRow(862, 60165992, 5, 8, false);
        http.postResponse = payload(1110, 60166240, 9, 6);
        http.onPostRefuel = () -> DataHelper.postRefuel(newAmount(1111, 60166241), true);

        DataHelper.postRefuel(newAmount(1110, 60166240), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(1, http.postCount);
        assertEquals(1111d, row.toRefuelItemData().getRealAmount(), 0d);
        assertTrue(row.isLocalModified());

        // Lượt sync kế tiếp mới gửi version mới.
        http.postResponse = payload(1111, 60166241, 10, 7);
        DataHelper.syncModifiedRefuels();
        assertEquals(2, http.postCount);
        assertFalse(repo.getRefuel(UID).isLocalModified());
    }

    /** Hai phiếu khác nhau không chặn nhau. */
    @Test
    public void differentRefuelsDoNotBlockEachOther() {
        seedRow(862, 60165992, 5, 8, false);
        assertTrue(DataHelper.tryBeginRefuelPost(UID));
        try {
            assertTrue("phiếu khác phải giành được slot riêng",
                    DataHelper.tryBeginRefuelPost("uid-khac"));
            DataHelper.endRefuelPost("uid-khac");
            assertFalse("cùng phiếu thì không", DataHelper.tryBeginRefuelPost(UID));
        } finally {
            DataHelper.endRefuelPost(UID);
        }
        assertTrue(DataHelper.tryBeginRefuelPost(UID));
        DataHelper.endRefuelPost(UID);
    }

    /** HTTP null và HTTP ném lỗi đều phải trả lại marker. */
    @Test
    public void inFlightMarkerIsReleasedOnNullAndOnException() {
        seedRow(862, 60165992, 5, 8, false);

        http.postResponse = null;
        DataHelper.postRefuel(newAmount(1110, 60166240), true);
        assertTrue("HTTP null vẫn phải trả marker", DataHelper.tryBeginRefuelPost(UID));
        DataHelper.endRefuelPost(UID);

        http.throwOnPost = true;
        try {
            DataHelper.postRefuel(newAmount(1112, 60166242), true);
        } catch (RuntimeException ignored) {
            // mong đợi: lỗi lan ra ngoài, nhưng marker phải đã được dọn
        }
        assertTrue("HTTP exception vẫn phải trả marker", DataHelper.tryBeginRefuelPost(UID));
        DataHelper.endRefuelPost(UID);
    }

    /** Seam reset dọn marker; row dirty vẫn gửi lại được. */
    @Test
    public void resetClearsInFlightMarkers() {
        assertTrue(DataHelper.tryBeginRefuelPost(UID));

        DataHelper.resetTestDependencies();
        DataHelper.installTestDependencies(repo, http);
        DataHelper.lockSync();

        assertTrue("sau reset phải giành lại được slot", DataHelper.tryBeginRefuelPost(UID));
        DataHelper.endRefuelPost(UID);
    }

    // =====================================================================
    // No-op vs user change, và backoff chặn cả POST
    // =====================================================================

    /** Lưu lại đúng payload cũ không được cấp sequence mới. */
    @Test
    public void noOpSaveDoesNotBumpClientSeq() {
        seedRow(1110, 60166240, 5, 8, false);

        DataHelper.postRefuel(snapshotFromRow(), false);

        assertEquals(8, repo.getRefuel(UID).getClientSeq());
    }

    @Test
    public void userEditBumpsClientSeq() {
        seedRow(1110, 60166240, 5, 8, false);

        DataHelper.postRefuel(newAmount(1111, 60166241), false);

        assertEquals(9, repo.getRefuel(UID).getClientSeq());
    }

    /** Chỉ đổi cờ local/version không phải là thay đổi nghiệp vụ. */
    @Test
    public void versionAndLocalFlagsAreNotBusinessChange() {
        RefuelItemData stored = payload(1110, 60166240, 8, 5);
        RefuelItemData incoming = payload(1110, 60166240, 99, 42);
        incoming.setLocalModified(true);
        incoming.setLocalId(777);

        assertFalse(RefuelSyncGuard.hasBusinessPayloadChanged(stored, incoming));

        incoming.setRealAmount(1111);
        assertTrue(RefuelSyncGuard.hasBusinessPayloadChanged(stored, incoming));
    }

    /** Trong thời gian backoff, background không được POST lại. */
    @Test
    public void backgroundDoesNotPostDuringVerificationBackoff() {
        seedRow(1110, 60166240, 5, 8, true);
        http.postResponse = payload(0, 60166240, 9, 6);   // inconclusive
        http.remoteItem = null;                            // GET thất bại

        DataHelper.syncModifiedRefuels();
        int afterFirst = http.postCount;

        DataHelper.syncModifiedRefuels();
        DataHelper.syncModifiedRefuels();

        assertEquals("đang trong backoff thì không POST thêm", afterFirst, http.postCount);
    }

    /** Hết backoff ⇒ đúng một POST và một GET cho lượt kế tiếp. */
    @Test
    public void oneMorePostAndGetAfterBackoffExpires() {
        seedRow(1110, 60166240, 5, 8, true);
        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = null;

        DataHelper.syncModifiedRefuels();
        int posts = http.postCount, gets = http.getCount;

        DataHelper.expireVerificationBackoff(UID);
        DataHelper.syncModifiedRefuels();

        assertEquals(posts + 1, http.postCount);
        assertEquals(gets + 1, http.getCount);
    }

    /**
     * Ba lượt thất bại ⇒ row Ở LẠI hàng đợi, chỉ bị hoãn.
     *
     * <p>Trước đây chỗ này đánh {@code postStatus = ERROR} để row rời hàng đợi. Trạng thái
     * đó nằm trong DB nên khởi động lại app cũng không cứu: dữ liệu người dùng đứng lại
     * vĩnh viễn ở máy mà không ai biết. Nay row vẫn chờ gửi, và không bao giờ mang nhãn
     * SUCCESS trong lúc chưa lên được server.
     */
    @Test
    public void rowStaysQueuedAfterRepeatedUnconfirmedPosts() {
        seedRow(1110, 60166240, 5, 8, true);
        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = null;

        for (int i = 0; i < 3; i++) {
            DataHelper.syncModifiedRefuels();
            DataHelper.expireVerificationBackoff(UID);
        }

        RefuelItem row = repo.getRefuel(UID);
        assertNotEquals(RefuelItem.ITEM_POST_STATUS.ERROR, row.getPostStatus());
        assertNotEquals("chưa lên được server thì không được báo thành công",
                RefuelItem.ITEM_POST_STATUS.SUCCESS, row.getPostStatus());
        assertTrue("row phải còn trong hàng đợi đồng bộ", row.isLocalModified());
        assertEquals(1110d, row.toRefuelItemData().getRealAmount(), 0d);
    }

    /** No-op direct call trong thời gian backoff không được phát POST. */
    @Test
    public void noOpDirectCallDoesNotBypassBackoff() {
        seedRow(1110, 60166240, 5, 8, true);
        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = null;

        DataHelper.syncModifiedRefuels();
        int posts = http.postCount;

        DataHelper.postRefuel(snapshotFromRow(), true);

        assertEquals("gửi lại nguyên trạng không được vượt backoff", posts, http.postCount);
    }

    /** Sửa thật trong thời gian backoff ⇒ xoá state cũ, gửi version mới. */
    @Test
    public void realEditDuringBackoffResumesSync() {
        seedRow(1110, 60166240, 5, 8, true);
        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = null;

        DataHelper.syncModifiedRefuels();
        int posts = http.postCount;

        http.postResponse = payload(1111, 60166241, 10, 7);
        DataHelper.postRefuel(newAmount(1111, 60166241), true);

        assertEquals("user edit được phép gửi ngay", posts + 1, http.postCount);
        assertFalse(DataHelper.shouldDeferVerificationSync(UID, System.currentTimeMillis()));
        assertFalse(repo.getRefuel(UID).isLocalModified());
    }

    /** Phiếu A đang backoff không chặn phiếu B. */
    @Test
    public void backoffOfOneRefuelDoesNotBlockAnother() {
        assertFalse(DataHelper.shouldDeferVerificationSync("uid-khac", System.currentTimeMillis()));
    }

    // =====================================================================
    // GET xác thực cho nhánh LEGACY_PROJECTION_UNKNOWN
    // =====================================================================

    /**
     * Ca 1: trong lúc GET xác thực đang bay, người dùng patch LeaveTime (ClientSeq tăng).
     * Thay đổi đó chưa được gửi nên kết quả GET không được xoá dirty.
     */
    @Test
    public void verificationDoesNotClearDirtyWhenRowChangedDuringGet() {
        seedRow(1110, 60166240, 5, 8, true);

        // Response POST rơi vào nhánh không kết luận được: Amount = 0, mọi thứ khác khớp.
        http.postResponse = payload(0, 60166240, 9, 6);
        // GET xác nhận trạng thái server đúng mục tiêu...
        http.remoteItem = payload(1110, 60166240, 9, 7);
        // ...nhưng ngay trước đó row bị patch LeaveTime nên seq đã nhảy.
        http.onGetRefuelItem = () -> DataHelper.patchRefuel(UID,
                latest -> latest.setLeaveTime(new Date(1_769_000_100_000L)));

        DataHelper.postRefuel(snapshotFromRow(), true);

        RefuelItem row = repo.getRefuel(UID);
        assertTrue("row còn thay đổi chưa gửi thì không được đánh dấu sạch",
                row.isLocalModified());
    }

    /**
     * Ca 2: hết ba lượt GET không xác nhận ⇒ row bị HOÃN, không bị loại khỏi hàng đợi.
     *
     * <p>Điểm phải giữ: dữ liệu local nguyên vẹn và row vẫn còn cơ hội gửi lại. Điểm phải
     * tránh: quay vòng POST + GET liên tục với server — nên lượt sync ngay sau đó không
     * được phát thêm request nào.
     */
    @Test
    public void rowIsBackedOffButStaysQueuedAfterVerificationExhausted() {
        seedRow(1110, 60166240, 5, 8, true);

        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = null;   // GET luôn thất bại

        for (int i = 0; i < 3; i++) {
            DataHelper.postRefuel(snapshotFromRow(), true);
            // Mô phỏng hết thời gian chờ giữa các lượt (giữ nguyên số lần đã thử).
            DataHelper.expireVerificationBackoff(UID);
        }

        RefuelItem row = repo.getRefuel(UID);
        assertNotEquals(RefuelItem.ITEM_POST_STATUS.ERROR, row.getPostStatus());
        assertTrue("dữ liệu local phải nguyên vẹn", row.isLocalModified());
        assertEquals(1110d, row.toRefuelItemData().getRealAmount(), 0d);
        assertEquals(1, repo.getAllModifiedRefuel().size());

        int postsBefore = http.postCount;
        DataHelper.syncModifiedRefuels();
        assertEquals("đang trong thời gian hoãn thì không được gửi tiếp",
                postsBefore, http.postCount);
    }

    /** Ca 3: GET có revision cao hơn response POST ⇒ row lưu revision của GET. */
    @Test
    public void verificationStoresHigherRevisionFromGet() {
        seedRow(1110, 60166240, 5, 8, true);

        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = payload(1110, 60166240, 9, 11);

        DataHelper.postRefuel(snapshotFromRow(), true);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(11, row.getServerRevision());
        assertFalse(row.isLocalModified());
        assertEquals(RefuelItem.ITEM_POST_STATUS.SUCCESS, row.getPostStatus());
    }

    /** GET trả revision thấp hơn response POST ⇒ có thể là bản đọc cũ, không xác nhận. */
    @Test
    public void verificationRejectsStaleRead() {
        seedRow(1110, 60166240, 5, 8, true);

        http.postResponse = payload(0, 60166240, 9, 6);
        http.remoteItem = payload(1110, 60166240, 9, 4);

        DataHelper.postRefuel(snapshotFromRow(), true);

        assertTrue(repo.getRefuel(UID).isLocalModified());
    }

    /**
     * Ca 4: khởi tạo dependency production thất bại thì phải báo lỗi ngay tại chỗ khởi tạo,
     * không trả null để rồi NPE ở một lời gọi bất kỳ về sau.
     */
    @Test
    public void dependencyInitialisationFailsFast() {
        try {
            DataHelper.createRepository(null);
            throw new AssertionError("phải ném IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("FMSApplication"));
        }

        try {
            DataHelper.createHttpClient(null);
            throw new AssertionError("phải ném IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("FMSApplication"));
        }
    }

    // =====================================================================

    // =========================================================================
    // Tách theo quyền sở hữu trường
    // =========================================================================

    /**
     * Lỗi gốc: web sửa dữ liệu nhưng app không ghi nhận, phải sửa tay lại trên máy.
     *
     * <p>Thay đổi từ web KHÔNG làm tăng ServerRevision của phiếu, nên cổng cũ
     * ({@code remote.ServerRevision > local.ServerRevision}) chặn đứng mọi cập nhật loại này
     * mà không để lại dòng log nào.
     */
    @Test
    public void webCorrectionLandsOnCleanRowWithoutRevisionBump() {
        seedRow(1110, 60166240, 5, 9, false);

        // Cùng revision, cùng seq — đúng hình dạng một lần sửa trên web.
        http.remoteItem = payload(1200, 60166300, 9, 5);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals(1200d, loaded.getRealAmount(), 0d);
        assertEquals(60166300d, repo.getRefuel(UID).toRefuelItemData().getEndNumber(), 0d);
    }

    /**
     * Điều động lại chuyến trong lúc lái xe đang nhập số: thông tin chuyến phải tới nơi
     * NGAY, kể cả khi row còn thay đổi chưa gửi — còn số đồng hồ thì không được đụng tới.
     */
    @Test
    public void flightChangeReachesDirtyRowWithoutTouchingMeterValues() {
        seedRow(1110, 60166240, 5, 9, true);

        RefuelItemData remote = payload(0, 0, 7, 5);
        remote.setFlightId(1265999);
        remote.setFlightCode("VN1270");
        remote.setParkingLot("B03");
        http.remoteItem = remote;

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals("VN1270", loaded.getFlightCode());
        assertEquals(1265999, (int) loaded.getFlightId());
        assertEquals(1110d, loaded.getRealAmount(), 0d);
        assertEquals(60166240d, loaded.getEndNumber(), 0d);

        RefuelItem stored = repo.getRefuel(UID);
        // Cột của entity phải đổi theo, nếu không các truy vấn lọc theo chuyến vẫn thấy bản cũ.
        assertEquals(1265999, stored.getFlightId());
        assertEquals(1110d, stored.toRefuelItemData().getRealAmount(), 0d);
        // Vẫn còn thay đổi chưa gửi ⇒ phải nằm nguyên trong hàng đợi đồng bộ.
        assertTrue(stored.isLocalModified());
    }

    /**
     * Câu hỏi quan trọng nhất khi bỏ cổng revision: đồng hồ đang chạy, một bản đọc CŨ của
     * server về giữa chừng thì số liệu có bị đẩy ngược lại không.
     *
     * <p>Row đã sạch (POST vừa thành công) nên cờ dirty không bảo vệ được nữa — thứ chặn ở
     * đây là "bản server đi lùi phiên bản thì không phải nguồn tin cậy cho số liệu".
     */
    @Test
    public void staleRemoteCannotPushMeterValuesBackOnCleanRow() {
        seedRow(1110, 60166240, 5, 9, false);

        // Bản đọc cũ: seq lùi về 8 và mang số liệu của lần đo trước.
        http.remoteItem = payload(862, 60165992, 8, 5);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals(1110d, loaded.getRealAmount(), 0d);
        assertEquals(60166240d, loaded.getEndNumber(), 0d);
        assertEquals(60166240d, repo.getRefuel(UID).toRefuelItemData().getEndNumber(), 0d);
    }

    /**
     * Hình dạng dữ liệu THẬT trên server: {@code ClientSeq = 0} trong khi
     * {@code ServerRevision = 19} — cột ClientSeq không được server ghi.
     *
     * <p>Nếu coi 0 là "phiên bản 0" thì mọi phiếu mà máy từng sửa (seq > 0) sẽ không bao giờ
     * nhận được sửa đổi từ web, tức là tái lập đúng lỗi đang đi chữa. 0 phải hiểu là
     * "server không có thông tin này".
     */
    @Test
    public void serverNotPersistingClientSeqStillDeliversWebEdits() {
        seedRow(1110, 60166240, 19, 9, false);

        RefuelItemData remote = payload(1200, 60166300, 0, 19);
        http.remoteItem = remote;

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals(1200d, loaded.getRealAmount(), 0d);
        // ClientSeq của máy không được phép bị bản server kéo lùi về 0.
        assertEquals(9, repo.getRefuel(UID).getClientSeq());
    }

    /** Cùng ca trên nhưng bản đọc cũ theo chiều revision. */
    @Test
    public void remoteWithLowerRevisionCannotPushMeterValuesBack() {
        seedRow(1110, 60166240, 5, 9, false);

        http.remoteItem = payload(862, 60165992, 9, 4);

        RefuelItemData loaded = DataHelper.getRefuelItem(UID);

        assertEquals(1110d, loaded.getRealAmount(), 0d);
        assertEquals(60166240d, loaded.getEndNumber(), 0d);
    }

    /** Snapshot đúng phiên bản đang lưu, như một màn hình vừa đọc row ra. */
    private RefuelItemData snapshotFromRow() {
        return repo.getRefuel(UID).toRefuelItemData();
    }

    /** Snapshot hợp lệ mang số liệu mới, như người dùng vừa chốt mẻ. */
    private RefuelItemData newAmount(double amount, double endNumber) {
        RefuelItemData snapshot = snapshotFromRow();
        snapshot.setRealAmount(amount);
        snapshot.setEndNumber(endNumber);
        return snapshot;
    }

    private RefuelItem seedRow(double amount, double endNumber, int serverRevision,
                               long clientSeq, boolean localModified) {
        RefuelItemData data = payload(amount, endNumber, clientSeq, serverRevision);
        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(localModified);
        repo.insertRefuel(row);
        return repo.getRefuel(UID);
    }

    private RefuelItemData payload(double amount, double endNumber,
                                   long clientSeq, int serverRevision) {
        RefuelItemData data = new RefuelItemData();
        data.setId(2087328);
        data.setUniqueId(UID);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setFlightId(1265515);
        data.setFlightCode("VN1268");
        // flightUniqueId mặc định là UUID ngẫu nhiên cho MỖI instance, phải cố định lại
        // thì hai payload mới so sánh được với nhau.
        data.setFlightUniqueId("5bf31c6d-522a-427f-b89b-b73128aa572b");
        data.setTruckId(173);
        data.setRealAmount(amount);
        data.setStartNumber(60165130);
        data.setEndNumber(endNumber);
        data.setDensity(0.781);
        data.setInvoiceNumber("24411");
        data.setEndTime(new Date(1_769_000_000_000L));
        data.setRefuelTime(new Date(1_769_000_000_000L));
        data.setClientSeq(clientSeq);
        data.setServerRevision(serverRevision);
        return data;
    }

    /** HttpClient giả: không có request thật nào rời khỏi test. */
    private static class FakeHttpClient extends HttpClient {
        RefuelItemData remoteItem;
        RefuelItemData postResponse;
        boolean throwOnPost;
        int postCount;
        int getCount;

        /** Cho phép test chen một thao tác vào đúng lúc GET xác thực đang chạy. */
        Runnable onGetRefuelItem;

        FakeHttpClient() {
            // Constructor rỗng của HttpClient đọc token/setting từ FMSApplication, thứ
            // không tồn tại trong môi trường test — dùng constructor nhận token.
            super("test-token");
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            getCount++;
            if (onGetRefuelItem != null) {
                Runnable hook = onGetRefuelItem;
                onGetRefuelItem = null;
                hook.run();
            }
            return remoteItem;
        }

        @Override
        public RefuelItemData getRefuelItem(Integer id) {
            return remoteItem;
        }

        /** Chạy khi request POST "đang bay", để test soi/chen thao tác vào đúng lúc đó. */
        Runnable onPostRefuel;

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            postCount++;
            if (throwOnPost) throw new RuntimeException("HTTP lỗi giả lập");
            if (onPostRefuel != null) {
                Runnable hook = onPostRefuel;
                onPostRefuel = null;
                hook.run();
            }
            // Server chép ClientSeq của request vào bản ghi rồi trả lại (tài liệu API mục 2.2).
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
