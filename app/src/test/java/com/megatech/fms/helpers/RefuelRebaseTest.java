package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/**
 * Hồi quy cho precondition khi lưu: màn hình đang mở phải còn lưu được sau khi một lượt
 * sync ACK nâng ServerRevision, nhưng KHÔNG được ghi đè khi payload nền đã bị đổi.
 *
 * <p>Dependency của DataHelper là static toàn cục nên các test ở đây không chạy song song.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelRebaseTest {

    private static final String UID = "rebase-uid";

    private AppDatabase db;
    private DataRepository repo;
    private Fake http;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        repo = DataRepository.forTesting(db);
        http = new Fake();
        DataHelper.installTestDependencies(repo, http);
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    /**
     * Ca 1 — hồi quy chính: (9,5) → ACK làm row thành (9,6) với payload không đổi
     * → người dùng sửa tiếp trên cùng màn hình vẫn phải lưu được, thành (10,6).
     */
    @Test
    public void screenCanStillSaveAfterAckBumpedRevision() {
        seedRow(1110, 9, 5);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        http.postResponse = payload(1110, 9, 6);
        DataHelper.syncModifiedRefuels();
        assertEquals(6, repo.getRefuel(UID).getServerRevision());

        screenSnapshot.setRealAmount(1234);
        DataHelper.postRefuel(screenSnapshot, false);

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(1234d, row.toRefuelItemData().getRealAmount(), 0d);
        assertEquals(10, row.getClientSeq());
        assertEquals(6, row.getServerRevision());
    }

    /** Ca 2: Web đổi payload ở rev6 ⇒ snapshot đứng trên rev5 bị từ chối. */
    @Test
    public void webEditAtHigherRevisionBlocksStaleSnapshot() {
        seedRow(1110, 9, 5);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        // Web sửa 1110 → 999, revision lên 6, ClientSeq giữ nguyên.
        RefuelItem webRow = repo.getRefuel(UID);
        RefuelItemData webData = webRow.toRefuelItemData();
        webData.setRealAmount(999);
        webData.setServerRevision(6);
        RefuelItem updated = RefuelItem.fromRefuelItemData(webData);
        updated.setLocalId(webRow.getLocalId());
        updated.setLocalModified(false);
        repo.insertRefuel(updated);

        screenSnapshot.setRealAmount(1234);
        DataHelper.postRefuel(screenSnapshot, false);

        assertEquals("dữ liệu Web không được ghi đè",
                999d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Ca 3: ClientSeq lệch vẫn là conflict dù payload giống hệt. */
    @Test
    public void clientSeqMovedIsStillConflict() {
        seedRow(1110, 9, 5);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        // Một màn hình khác lưu trước.
        RefuelItemData other = repo.getRefuel(UID).toRefuelItemData();
        other.setRealAmount(1111);
        DataHelper.postRefuel(other, false);

        screenSnapshot.setRealAmount(1234);
        DataHelper.postRefuel(screenSnapshot, false);

        assertEquals(1111d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Ca 4: revision của row lùi so với baseline ⇒ từ chối. */
    @Test
    public void revisionRegressionIsRejected() {
        seedRow(1110, 9, 7);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        RefuelItem row = repo.getRefuel(UID);
        row.setServerRevision(5);
        repo.insertRefuel(row);

        screenSnapshot.setRealAmount(1234);
        DataHelper.postRefuel(screenSnapshot, false);

        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Ca 6: row đã tồn tại nhưng snapshot không mang baseline ⇒ từ chối. */
    @Test
    public void existingRowWithoutBaselineIsRejected() {
        seedRow(1110, 9, 7);

        RefuelItemData noBaseline = repo.getRefuel(UID).toRefuelItemData();
        noBaseline.setBaseClientSeq(9);
        noBaseline.setBaseServerRevision(5);          // lệch revision
        noBaseline.setBaseBusinessFingerprint(null);  // nhưng không có gì để kiểm chứng
        noBaseline.setRealAmount(1234);

        DataHelper.postRefuel(noBaseline, false);

        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Ca 7: sau rebase, request kế tiếp mang baseRev mới và ACK ở rev cao hơn được nhận. */
    @Test
    public void requestAfterRebaseCarriesNewBaseRevision() {
        seedRow(1110, 9, 5);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        http.postResponse = payload(1110, 9, 6);
        DataHelper.syncModifiedRefuels();

        screenSnapshot.setRealAmount(1234);
        DataHelper.postRefuel(screenSnapshot, false);

        assertEquals("snapshot phải được rebase lên revision hiện tại",
                6, screenSnapshot.getBaseServerRevision());

        http.postResponse = payload(1234, 10, 7);
        DataHelper.syncModifiedRefuels();

        RefuelItem row = repo.getRefuel(UID);
        assertEquals(7, row.getServerRevision());
        assertFalse("ACK sau rebase phải được nhận", row.isLocalModified());
    }

    /** Ca 8: snapshot đi qua Intent (toJson → fromJson) vẫn giữ baseline và lưu được. */
    @Test
    public void snapshotThroughIntentJsonKeepsBaselineAndCanSave() {
        seedRow(1110, 9, 5);
        RefuelItemData screenSnapshot = repo.getRefuel(UID).toRefuelItemData();

        // Người dùng sửa 1110 → 1234 TRƯỚC khi chuyển màn hình.
        screenSnapshot.setRealAmount(1234);

        // Detail → Intent → Confirm: baseline đi kèm extras, không suy từ payload.
        android.os.Bundle extras = new android.os.Bundle();
        android.content.Intent intent = new android.content.Intent();
        com.megatech.fms.helpers.RefuelIntent.putRefuel(intent, screenSnapshot);
        extras.putAll(intent.getExtras());

        RefuelItemData confirmSnapshot = com.megatech.fms.helpers.RefuelIntent.readRefuel(extras);
        assertNotNull(confirmSnapshot.getBaseBusinessFingerprint());
        assertEquals(9, confirmSnapshot.getBaseClientSeq());
        assertEquals(1234d, confirmSnapshot.getRealAmount(), 0d);
        DataHelper.postRefuel(confirmSnapshot, false);

        assertEquals(1234d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Snapshot cũ đi qua Intent KHÔNG được hợp thức hoá bằng version hiện tại của Room. */
    @Test
    public void staleSnapshotThroughIntentJsonIsStillRejected() {
        seedRow(1110, 9, 5);
        RefuelItemData staleJsonSnapshot = RefuelItemData.fromJson(
                repo.getRefuel(UID).toRefuelItemData().toJson());

        // Row tiến lên bằng một lần lưu khác.
        RefuelItemData other = repo.getRefuel(UID).toRefuelItemData();
        other.setRealAmount(1111);
        DataHelper.postRefuel(other, false);

        staleJsonSnapshot.setRealAmount(862);
        DataHelper.postRefuel(staleJsonSnapshot, false);

        assertEquals(1111d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Ca 9: fingerprint không được lọt vào jsonData của Room. */
    @Test
    public void fingerprintNeverReachesStoredJson() {
        seedRow(1110, 9, 5);

        RefuelItem row = repo.getRefuel(UID);
        assertFalse(row.getJsonData().contains("BaseBusinessFingerprint"));
        assertFalse(row.getJsonData().contains(
                row.toRefuelItemData().getBaseBusinessFingerprint()));
    }

    /**
     * Tái hiện log sản xuất 29/07 (phiếu VN440): mẻ chốt 4940, nhưng màn hình Preview còn
     * giữ bản 4922 do server trả về lúc mở màn hình. Khi in receipt xong, luồng cũ POST lại
     * cả snapshot 4922 và kéo phiếu về số cũ.
     *
     * <p>Nay việc ghi số receipt phải đi qua patch trên bản ghi mới nhất: số đồng hồ giữ
     * nguyên 4940 VÀ số receipt vẫn được lưu.
     */
    @Test
    public void receiptPatchKeepsConfirmedMeterValue() {
        // Room: mẻ đã chốt 4940
        seedRow(4940, 9, 5);

        // Màn hình Preview còn giữ bản cũ 4922 (đến từ server, không có baseline)
        RefuelItemData staleScreenCopy = payload(4922, 9, 5);
        staleScreenCopy.setLocalId(0);
        staleScreenCopy.setBaseBusinessFingerprint(null);
        staleScreenCopy.setBaseClientSeq(RefuelItemData.VERSION_UNKNOWN);

        // Luồng cũ: POST cả snapshot ⇒ phải bị từ chối
        DataHelper.postRefuel(staleScreenCopy, false);
        assertEquals("snapshot cũ không được kéo số về 4922",
                4940d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);

        // Luồng mới: patch đúng trường receipt trên row mới nhất
        DataHelper.PatchResult result = DataHelper.patchRefuel(UID, latest -> {
            latest.setReceiptNumber("2618T4Y");
            latest.setReceiptCount(latest.getReceiptCount() + 1);
        });

        assertTrue("số receipt phải lưu được", result.applied);
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(4940d, saved.getRealAmount(), 0d);
        assertEquals("2618T4Y", saved.getReceiptNumber());
        assertEquals(1, saved.getReceiptCount());
    }

    // =====================================================================

    private void seedRow(double amount, long seq, int rev) {
        RefuelItem row = RefuelItem.fromRefuelItemData(payload(amount, seq, rev));
        row.setLocalModified(true);
        repo.insertRefuel(row);
    }

    private RefuelItemData payload(double amount, long seq, int rev) {
        RefuelItemData data = new RefuelItemData();
        data.setId(5001);
        data.setUniqueId(UID);
        data.setFlightUniqueId("flight-uid");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setRealAmount(amount);
        data.setStartNumber(1000);
        data.setEndNumber(2000);
        data.setEndTime(new Date(1_769_000_000_000L));
        data.setClientSeq(seq);
        data.setServerRevision(rev);
        return data;
    }

    private static class Fake extends HttpClient {
        RefuelItemData postResponse;

        Fake() {
            super("test-token");
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            if (postResponse != null) postResponse.setClientSeq(refuelData.getClientSeq());
            return postResponse;
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            return null;
        }
    }
}
