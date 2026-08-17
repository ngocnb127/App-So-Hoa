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
 * MỌI trường người dùng nhập ở màn hình xác nhận phải nằm trong Room sau khi bấm Xác nhận —
 * kể cả khi lượt đồng bộ nền vừa đổi dữ liệu chuyến bay của chính phiếu đó.
 *
 * <p>Sự cố ngày 17-08-2026: pull nền đổi {@code FlightStatus} ASSIGNED→REFUELING giữa mẻ,
 * lần lưu bị chặn {@code CONFLICT_PAYLOAD_CHANGED}, phiếu về 0 GL.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelConfirmFieldsPersistTest {

    private static final String UID = "be8b7194-9337-478b-8f18-00461db11d5f";

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repo = DataRepository.forTesting(db);
        DataHelper.installTestDependencies(repo, new OfflineHttpClient());
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    /** Phiếu đang tra nạp, giống row trên xe lúc mở màn hình. */
    private RefuelItem seedRow() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2111601);
        data.setUniqueId(UID);
        data.setFlightUniqueId("5bf31c6d-522a-427f-b89b-b73128aa572b");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1265515);
        data.setFlightCode("VN 1237-01");
        data.setFlightStatus(RefuelItemData.FLIGHT_STATUS.ASSIGNED);
        data.setParkingLot("A1");
        data.setAircraftCode("VN-A123");
        data.setStartNumber(817751);
        data.setEndNumber(817751);
        data.setRefuelTime(new Date(1_787_000_000_000L));
        data.setEndTime(new Date(1_787_000_000_000L));
        data.setClientSeq(2);
        data.setServerRevision(1);

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(true);
        repo.insertRefuel(row);
        return repo.getRefuel(UID);
    }

    /** Lượt pull nền đổi trạng thái chuyến, đúng như log của thiết bị. */
    private void backgroundPullChangesFlightStatus() {
        RefuelItem stored = repo.getRefuel(UID);
        RefuelItemData remote = new RefuelItemData();
        remote.setRawJson("{\"Id\":2111601,\"FlightId\":1265515,\"FlightCode\":\"VN 1237-01\","
                + "\"FlightStatus\":2,\"ParkingLot\":\"B7\",\"ServerRevision\":3}");
        RefuelSyncGuard.applyRemote(stored, remote, false);
        stored.setServerRevision(3);
        repo.insertRefuel(stored);
    }

    /** Đúng những trường màn hình xác nhận cho nhập. */
    private static void fillConfirmScreenFields(RefuelItemData item) {
        item.setStatus(REFUEL_ITEM_STATUS.DONE);
        item.setRealAmount(399);
        item.setGallon(399);
        item.setVolume(1510);
        item.setStartNumber(817457);
        item.setEndNumber(817856);
        item.setManualTemperature(30);
        item.setDensity(0.789);
        item.setQualityNo("D");
        item.setWeightNote("ghi chú khối lượng");
        item.setReturnAmount(12);
        item.setStartTime(new Date(1_787_000_100_000L));
        item.setEndTime(new Date(1_787_000_900_000L));
        item.setReceiptNumber("HD-0001");
        item.setTruckId(173);
        item.setTruckNo("XT-01");
        item.setDriverId(11);
        item.setDriverName("Nguyễn Văn A");
        item.setOperatorId(22);
        item.setOperatorName("Trần Thị B");
    }

    private static void assertConfirmScreenFields(RefuelItemData saved) {
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(399d, saved.getGallon(), 0d);
        assertEquals(1510d, saved.getVolume(), 0d);
        assertEquals(817457d, saved.getStartNumber(), 0d);
        assertEquals(817856d, saved.getEndNumber(), 0d);
        assertEquals(30d, saved.getManualTemperature(), 0d);
        assertEquals(0.789d, saved.getDensity(), 0d);
        assertEquals("D", saved.getQualityNo());
        assertEquals("ghi chú khối lượng", saved.getWeightNote());
        assertEquals(12d, saved.getReturnAmount(), 0d);
        assertEquals(1_787_000_100_000L, saved.getStartTime().getTime());
        assertEquals(1_787_000_900_000L, saved.getEndTime().getTime());
        assertEquals("HD-0001", saved.getReceiptNumber());
        assertEquals(173, saved.getTruckId());
        assertEquals("XT-01", saved.getTruckNo());
        assertEquals(11, saved.getDriverId());
        assertEquals("Nguyễn Văn A", saved.getDriverName());
        assertEquals(22, saved.getOperatorId());
        assertEquals("Trần Thị B", saved.getOperatorName());
    }

    /** Nền: không có gì xen giữa thì mọi trường phải vào Room. */
    @Test
    public void everyConfirmFieldIsPersisted() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);

        DataHelper.postRefuel(snapshot, false);

        assertConfirmScreenFields(repo.getRefuel(UID).toRefuelItemData());
    }

    /** Ca sự cố: pull nền chen vào giữa lúc màn hình xác nhận đang mở. */
    @Test
    public void everyConfirmFieldIsPersistedDespiteBackgroundPull() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();   // màn hình mở
        backgroundPullChangesFlightStatus();                                // pull nền
        fillConfirmScreenFields(snapshot);                                  // người dùng nhập

        RefuelItemData result = DataHelper.postRefuel(snapshot, false);

        assertNotNull(result);
        assertTrue("lần lưu phải được ghi nhận", RefuelItemData.isCommitted(result));
        assertConfirmScreenFields(repo.getRefuel(UID).toRefuelItemData());
    }

    /** Trường server đổi trong lúc đó vẫn phải được nhận, không bị snapshot đẩy lùi. */
    @Test
    public void serverFieldsFromPullSurviveTheSave() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        backgroundPullChangesFlightStatus();
        fillConfirmScreenFields(snapshot);

        DataHelper.postRefuel(snapshot, false);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(RefuelItemData.FLIGHT_STATUS.REFUELING, saved.getFlightStatus());
        assertEquals("B7", saved.getParkingLot());
    }

    /**
     * Trường SHARED mà CHỈ người dùng sửa: bản của người dùng thắng, lưu bình thường.
     *
     * <p>Bãi đỗ và số hiệu tàu bay sửa được ở cả web lẫn app, nên chúng là SHARED chứ không
     * phải server sở hữu.
     */
    @Test
    public void userEditOfSharedFieldWinsWhenServerDidNotTouchIt() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);
        snapshot.setParkingLot("C9");
        snapshot.setAircraftCode("VN-A999");

        RefuelItemData result = DataHelper.postRefuel(snapshot, false);

        assertTrue(RefuelItemData.isCommitted(result));
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals("C9", saved.getParkingLot());
        assertEquals("VN-A999", saved.getAircraftCode());
        assertConfirmScreenFields(saved);
    }

    /**
     * Trường SHARED mà CẢ HAI phía cùng đổi sang giá trị khác nhau: xung đột THẬT, phải chặn.
     *
     * <p>Lấy bản người dùng làm mặc định ở đây là âm thầm xoá thay đổi của điều độ — mất dữ
     * liệu theo chiều ngược lại với sự cố gốc, và không ai biết.
     */
    @Test
    public void sharedFieldChangedByBothSidesIsConflict() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        backgroundPullChangesFlightStatus();          // điều độ đổi bãi đỗ A1 -> B7

        fillConfirmScreenFields(snapshot);
        snapshot.setParkingLot("C9");                 // lái xe đổi sang C9

        RefuelItemData result = DataHelper.postRefuel(snapshot, false);

        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT, result.getSaveOutcome());
        assertEquals("bản của điều độ phải còn nguyên", "B7",
                repo.getRefuel(UID).toRefuelItemData().getParkingLot());
    }

    /**
     * Nhưng mẻ vẫn phải chốt được: patch của màn hình xác nhận nhận bãi đỗ của điều độ và
     * giữ nguyên số liệu người dùng nhập. Xung đột bãi đỗ không được làm mất cả mẻ.
     */
    @Test
    public void sharedFieldConflictStillLetsTheBatchBeSavedByPatch() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        backgroundPullChangesFlightStatus();
        fillConfirmScreenFields(snapshot);
        snapshot.setParkingLot("C9");

        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT,
                DataHelper.postRefuel(snapshot, false).getSaveOutcome());

        RefuelItemData recovered = DataHelper.saveConfirmFields(snapshot);

        assertTrue(RefuelItemData.isCommitted(recovered));
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertConfirmScreenFields(saved);
        assertEquals("bãi đỗ theo điều độ", "B7", saved.getParkingLot());
    }

    /** Lưu tiếp lần hai từ cùng màn hình (sửa lại rồi bấm Xác nhận lần nữa). */
    @Test
    public void secondSaveFromSameScreenStillPersists() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        backgroundPullChangesFlightStatus();
        fillConfirmScreenFields(snapshot);
        DataHelper.postRefuel(snapshot, false);

        snapshot.setManualTemperature(31.5);
        snapshot.setDensity(0.7912);
        RefuelItemData result = DataHelper.postRefuel(snapshot, false);

        assertTrue(RefuelItemData.isCommitted(result));
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(31.5d, saved.getManualTemperature(), 0d);
        assertEquals(0.7912d, saved.getDensity(), 0d);
        assertEquals(399d, saved.getRealAmount(), 0d);
    }


    // =====================================================================
    // Kết quả lưu tường minh, tồn xe, và trạng thái hàng đợi nhìn thấy được
    // =====================================================================

    /** null luôn là thất bại — không màn hình nào được điều hướng tiếp. */
    @Test
    public void nullResultIsNeverCommitted() {
        assertFalse(RefuelItemData.isCommitted(null));
    }

    /** Lưu bị chặn phải mang CONFLICT, để màn hình giữ nguyên dữ liệu đang nhập. */
    @Test
    public void blockedSaveReportsConflict() {
        seedRow();

        // Snapshot cũ, đứng trên nền đã lỗi thời và mang số liệu mẻ khác.
        RefuelItemData stale = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(stale);
        stale.setBaseClientSeq(stale.getBaseClientSeq() - 1);

        RefuelItemData result = DataHelper.postRefuel(stale, false);

        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT, result.getSaveOutcome());
        assertFalse(RefuelItemData.isCommitted(result));
        assertEquals("dữ liệu cũ trong Room phải nguyên vẹn",
                0d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    /** Cờ chuyển trạng thái chỉ bật ở ĐÚNG lần lưu đưa mẻ sang DONE. */
    @Test
    public void transitionToDoneIsReportedExactlyOnce() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);

        RefuelItemData first = DataHelper.postRefuel(snapshot, false);
        assertTrue("lần đầu chốt mẻ phải báo chuyển trạng thái",
                first.isTransitionedToDone());

        // Bấm End lại / callback thiết bị lặp: row đã DONE nên không có chuyển trạng thái.
        snapshot.setManualTemperature(31);
        RefuelItemData second = DataHelper.postRefuel(snapshot, false);
        assertTrue(RefuelItemData.isCommitted(second));
        assertFalse("lưu lần hai không được trừ tồn thêm lần nữa",
                second.isTransitionedToDone());

        RefuelItemData third = DataHelper.postRefuel(snapshot, false);
        assertFalse(third.isTransitionedToDone());
    }

    /** Row chưa gửi được không bao giờ được hiển thị là đã đồng bộ. */
    @Test
    public void queueStatusIsVisibleOnTheModel() {
        RefuelItem row = seedRow();
        row.setPostStatus(RefuelItem.ITEM_POST_STATUS.ERROR);
        repo.insertRefuel(row);

        assertEquals(RefuelItemData.ITEM_POST_STATUS.ERROR,
                repo.getRefuel(UID).toRefuelItemData().getPostStatus());
    }

    // =====================================================================
    // ConfirmFieldsPatch qua DataHelper
    // =====================================================================

    /** Patch đọc row mới nhất rồi đắp: dữ liệu người dùng vào Room dù snapshot đã lỗi thời. */
    @Test
    public void confirmPatchSavesUserFieldsOntoLatestRow() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        // Row tiến lên do một đường ghi khác trong lúc màn hình đang mở.
        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));

        fillConfirmScreenFields(snapshot);
        RefuelItemData result = DataHelper.saveConfirmFields(snapshot);

        assertTrue(RefuelItemData.isCommitted(result));
        assertConfirmScreenFields(repo.getRefuel(UID).toRefuelItemData());
        assertNotNull("thay đổi của đường ghi kia phải còn nguyên",
                repo.getRefuel(UID).toRefuelItemData().getLeaveTime());
    }

    /** Mẻ đã chốt bằng bộ số khác thì patch phải chặn, không ghi đè. */
    @Test
    public void confirmPatchRefusesToOverwriteFinalizedRow() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);

        // Mẻ được chốt ở nơi khác với số liệu khác hẳn.
        RefuelItemData finalized = repo.getRefuel(UID).toRefuelItemData();
        finalized.setStatus(REFUEL_ITEM_STATUS.DONE);
        finalized.setRealAmount(1110);
        finalized.setEndNumber(60166240);
        DataHelper.postRefuel(finalized, false);

        RefuelItemData result = DataHelper.saveConfirmFields(snapshot);

        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT, result.getSaveOutcome());
        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
        assertEquals(60166240d, repo.getRefuel(UID).toRefuelItemData().getEndNumber(), 0d);
    }


    // =====================================================================
    // Hồi lưu: 398 -> 399 -> 400 -> ngừng bơm, đồng hồ lùi về 399 -> End
    // =====================================================================

    private RefuelItemData saveMeter(RefuelItemData snapshot, double endNumber, double amount) {
        snapshot.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        snapshot.setEndNumber(endNumber);
        snapshot.setRealAmount(amount);
        snapshot.setStartNumber(endNumber - amount);
        return DataHelper.postRefuel(snapshot, false);
    }

    /**
     * Số chốt là số CUỐI, không phải số lớn nhất. Hồi lưu làm đồng hồ lùi 400 → 399 là
     * nghiệp vụ hợp lệ; tuyệt đối không được có luật "chỉ nhận số lớn nhất".
     */
    @Test
    public void meterRollbackFromRefluxIsTheCommittedValue() {
        seedRow();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        assertTrue(RefuelItemData.isCommitted(saveMeter(snapshot, 818149, 398)));
        assertTrue(RefuelItemData.isCommitted(saveMeter(snapshot, 818150, 399)));
        assertTrue(RefuelItemData.isCommitted(saveMeter(snapshot, 818151, 400)));

        // Ngừng bơm, hồi lưu: đồng hồ lùi về 399 rồi người dùng bấm End.
        snapshot.setEndNumber(818150);
        snapshot.setRealAmount(399);
        snapshot.setStartNumber(817751);
        snapshot.setStatus(REFUEL_ITEM_STATUS.DONE);

        RefuelItemData ended = DataHelper.postRefuel(snapshot, false);

        assertTrue("End phải commit được", RefuelItemData.isCommitted(ended));
        assertTrue("chỉ lần End mới chuyển trạng thái", ended.isTransitionedToDone());

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
    }

    /** Hồi lưu vẫn phải đúng khi lượt pull nền chen vào giữa chuỗi số đo. */
    @Test
    public void meterRollbackSurvivesBackgroundPull() {
        seedRow();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        saveMeter(snapshot, 818149, 398);
        backgroundPullChangesFlightStatus();
        saveMeter(snapshot, 818151, 400);

        snapshot.setEndNumber(818150);
        snapshot.setRealAmount(399);
        snapshot.setStatus(REFUEL_ITEM_STATUS.DONE);

        RefuelItemData ended = DataHelper.postRefuel(snapshot, false);

        assertTrue(RefuelItemData.isCommitted(ended));
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(RefuelItemData.FLIGHT_STATUS.REFUELING, saved.getFlightStatus());
    }

    /**
     * Màn hình xác nhận mở sau End: patch không được coi số lùi là "row đã chốt bằng bộ số
     * khác" rồi chặn — nền của nó chính là bộ số 399 vừa chốt.
     */
    @Test
    public void confirmAfterRollbackStillSaves() {
        seedRow();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        saveMeter(snapshot, 818151, 400);
        snapshot.setEndNumber(818150);
        snapshot.setRealAmount(399);
        snapshot.setStatus(REFUEL_ITEM_STATUS.DONE);
        DataHelper.postRefuel(snapshot, false);

        // Màn hình xác nhận nhận đúng snapshot đó, nhập nốt nhiệt độ/tỉ trọng.
        snapshot.setManualTemperature(30);
        snapshot.setDensity(0.789);
        snapshot.setQualityNo("D");

        RefuelItemData result = DataHelper.postRefuel(snapshot, false);

        assertTrue(RefuelItemData.isCommitted(result));
        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(30d, saved.getManualTemperature(), 0d);
        assertEquals(0.789d, saved.getDensity(), 0d);
    }


    // =====================================================================
    // Hàng đợi ghi: bản sao lúc xếp hàng, và chỉ End được chốt mẻ
    // =====================================================================

    /**
     * Lỗi P0 của bản sửa executor đầu tiên: task xếp hàng giữ THAM CHIẾU tới đối tượng của
     * màn hình. End đặt DONE lên chính đối tượng đó trước khi autosave "400" kịp chạy, nên
     * autosave tự thực hiện chuyển trạng thái; cờ transitionedToDone rơi vào kết quả bị bỏ
     * đi, lần ghi của End thấy row đã DONE nên báo false, và TỒN XE KHÔNG ĐƯỢC TRỪ.
     */
    @Test
    public void queuedSnapshotIsIndependentOfLaterEdits() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setEndNumber(818151);
        screen.setRealAmount(400);

        RefuelItemData queued = screen.snapshotForSave();

        // Người dùng bấm End: đối tượng của màn hình đổi sang DONE với số hồi lưu.
        screen.setEndNumber(818150);
        screen.setRealAmount(399);
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);

        assertEquals("bản đã xếp hàng không được đổi theo",
                818151d, queued.getEndNumber(), 0d);
        assertEquals(400d, queued.getRealAmount(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.PROCESSING, queued.getStatus());
    }

    /** Bản sao phải mang theo baseline, nếu không lần ghi đầu tiên đã là conflict. */
    @Test
    public void queuedSnapshotCarriesBaseline() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        RefuelItemData queued = screen.snapshotForSave();

        assertEquals(screen.getBaseClientSeq(), queued.getBaseClientSeq());
        assertEquals(screen.getBaseServerRevision(), queued.getBaseServerRevision());
        assertEquals(screen.getBaseBusinessFingerprint(), queued.getBaseBusinessFingerprint());
        assertEquals(screen.getBaseJson(), queued.getBaseJson());
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(queued, false)));
    }

    /**
     * Chuỗi đầy đủ của ca hồi lưu qua hàng đợi ghi: autosave 400 (đã xếp hàng trước) chạy
     * trước, rồi tới lần ghi của End với 399 + DONE. Chỉ lần ghi của End được báo chuyển
     * trạng thái — đó là điều kiện để tồn xe được trừ đúng một lần, đúng số 399.
     */
    @Test
    public void onlyTheEndSaveReportsTransitionAfterReflux() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        screen.setEndNumber(818151);
        screen.setRealAmount(400);
        RefuelItemData autosave = screen.snapshotForSave();     // xếp hàng lúc đồng hồ ở 400

        screen.setEndNumber(818150);
        screen.setRealAmount(399);
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        RefuelItemData endSave = screen.snapshotForSave();      // xếp hàng khi bấm End

        // Hàng đợi đơn luồng chạy đúng thứ tự xếp hàng.
        RefuelItemData autosaveResult = DataHelper.postRefuel(autosave, false);
        screen.adoptSaveState(autosave);
        endSave.adoptSaveState(autosave);
        RefuelItemData endResult = DataHelper.postRefuel(endSave, false);

        assertTrue(RefuelItemData.isCommitted(autosaveResult));
        assertFalse("autosave không được chốt mẻ", autosaveResult.isTransitionedToDone());
        assertTrue(RefuelItemData.isCommitted(endResult));
        assertTrue("chỉ lần ghi của End mới chuyển trạng thái",
                endResult.isTransitionedToDone());

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
    }

    /** Sau khi ghi bản sao, màn hình phải nhận lại phiên bản mới để còn lưu tiếp được. */
    @Test
    public void screenAdoptsVersionFromQueuedSave() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        RefuelItemData first = screen.snapshotForSave();
        first.setRealAmount(398);
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(first, false)));
        screen.adoptSaveState(first);

        // Lần lưu kế tiếp đứng trên baseline vừa nhận, không được thành conflict.
        RefuelItemData second = screen.snapshotForSave();
        second.setRealAmount(399);
        RefuelItemData result = DataHelper.postRefuel(second, false);

        assertTrue("không nhận lại baseline thì lần lưu sau luôn conflict",
                RefuelItemData.isCommitted(result));
        assertEquals(399d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }


    /**
     * Chạy trên HÀNG ĐỢI THẬT, với latch giữ task autosave lại đúng lúc End xen vào.
     *
     * <p>Đây là ca đã đánh sập bản sửa executor đầu tiên: autosave bị giữ trong hàng đợi,
     * End sửa đối tượng của màn hình sang DONE, rồi autosave mới chạy. Nếu task cầm tham
     * chiếu thay vì bản sao, chính autosave sẽ chốt mẻ và tồn xe không được trừ.
     */
    @Test
    public void queuedAutosaveCannotFinalizeWhileEndIsPending() throws Exception {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        java.util.concurrent.ExecutorService queue =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch hold = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<RefuelItemData> autosaveResult =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<RefuelItemData> endResult =
                new java.util.concurrent.atomic.AtomicReference<>();

        // Đồng hồ ở 400: autosave xếp hàng, chụp bản sao NGAY tại đây.
        screen.setEndNumber(818151);
        screen.setRealAmount(400);
        final RefuelItemData autosaveSnapshot = screen.snapshotForSave();
        queue.execute(() -> {
            try {
                hold.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            autosaveResult.set(DataHelper.postRefuel(autosaveSnapshot, false));
            screen.adoptSaveState(autosaveSnapshot);
        });

        // Hồi lưu + End xảy ra TRONG LÚC task trên còn bị giữ.
        screen.setEndNumber(818150);
        screen.setRealAmount(399);
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        // Production chụp dữ liệu End ngay lúc enqueue, khi autosave phía trước chưa chạy.
        final RefuelItemData endSnapshot = screen.snapshotForSave();
        queue.execute(() -> {
            // Khi tới lượt chạy, chỉ refresh identity/baseline từ task đứng trước; dữ liệu
            // nghiệp vụ 399 + DONE trong snapshot phải giữ nguyên.
            endSnapshot.adoptSaveState(screen);
            endResult.set(DataHelper.postRefuel(endSnapshot, false));
            screen.adoptSaveState(endSnapshot);
        });

        hold.countDown();
        queue.shutdown();
        assertTrue("hàng đợi phải chạy xong",
                queue.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));

        assertFalse("autosave đã xếp hàng không được chốt mẻ",
                autosaveResult.get().isTransitionedToDone());
        assertTrue("chỉ lần ghi của End mới chuyển trạng thái",
                endResult.get().isTransitionedToDone());

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
    }


    // =====================================================================
    // EndFieldsPatch: conflict thật lúc End phải có đường ra
    // =====================================================================

    /**
     * Trước khi có {@code saveEndFields}, một conflict thật lúc End là ngõ cụt: nút "Thử
     * lại" gửi lại đúng baseline cũ nên hỏng mãi mãi.
     */
    @Test
    public void endConflictIsRecoverableByPatch() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        // Một đường ghi khác tiến row lên trong lúc mẻ đang chạy.
        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));

        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setRealAmount(399);
        screen.setEndNumber(818150);
        screen.setStartNumber(817751);

        // Lưu thường: snapshot đứng trên baseline cũ ⇒ bị chặn.
        RefuelItemData blocked = DataHelper.postRefuel(screen, false);
        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT, blocked.getSaveOutcome());

        // Đường phục hồi.
        RefuelItemData recovered = DataHelper.saveEndFields(screen);

        assertTrue("End phải có đường ra khỏi conflict",
                RefuelItemData.isCommitted(recovered));
        assertTrue("tồn xe vẫn phải được trừ đúng lần chốt này",
                recovered.isTransitionedToDone());

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(399d, saved.getRealAmount(), 0d);
        assertEquals(818150d, saved.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, saved.getStatus());
        assertNotNull("thay đổi của đường ghi kia phải còn", saved.getLeaveTime());
    }

    /** Nhưng mẻ đã chốt bằng bộ số khác thì End vẫn phải chặn. */
    @Test
    public void endPatchRefusesToOverwriteOtherFinalNumbers() {
        seedRow();
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();

        RefuelItemData other = repo.getRefuel(UID).toRefuelItemData();
        other.setStatus(REFUEL_ITEM_STATUS.DONE);
        other.setRealAmount(1110);
        other.setEndNumber(60166240);
        DataHelper.postRefuel(other, false);

        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setRealAmount(399);
        screen.setEndNumber(818150);

        RefuelItemData result = DataHelper.saveEndFields(screen);

        assertEquals(RefuelItemData.SAVE_OUTCOME.CONFLICT, result.getSaveOutcome());
        assertFalse("bị chặn thì không được trừ tồn", result.isTransitionedToDone());
        assertEquals(1110d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }


    // =====================================================================
    // Cột Room phải khớp jsonData — không màn hình nào được thấy mẻ 0 GL
    // =====================================================================

    private void assertColumnsMatchJson(String where) {
        RefuelItem row = repo.getRefuel(UID);
        RefuelItemData json = row.toRefuelItemData();

        assertEquals(where + ": realAmount", json.getRealAmount(), row.getRealAmount(), 0d);
        assertEquals(where + ": startNumber", json.getStartNumber(), row.getStartNumber(), 0d);
        assertEquals(where + ": endNumber", json.getEndNumber(), row.getEndNumber(), 0d);
        assertEquals(where + ": density", json.getDensity(), row.getDensity(), 0d);
        assertEquals(where + ": manualTemperature",
                json.getManualTemperature(), row.getManualTemperature(), 0d);
        assertEquals(where + ": qualityNo", json.getQualityNo(), row.getQualityNo());
        assertEquals(where + ": flightCode", json.getFlightCode(), row.getFlightCode());
        assertEquals(where + ": parkingLot", json.getParkingLot(), row.getParkingLot());
        assertEquals(where + ": truckNo", json.getTruckNo(), row.getTruckNo());
    }

    /**
     * Đo trên máy thật 17-08, mẻ 843 GL: jsonData đúng hoàn toàn nhưng cột realAmount,
     * startNumber, endNumber, density, qualityNo, flightCode, parkingLot đều 0/null — vì
     * hai đường ghi chỉ gán vài cột. Màn hình hay truy vấn nào đọc theo cột sẽ báo 0 GL.
     */
    @Test
    public void columnsMatchJsonAfterNormalSave() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);
        DataHelper.postRefuel(snapshot, false);

        assertColumnsMatchJson("lưu thường");
        assertEquals(399d, repo.getRefuel(UID).getRealAmount(), 0d);
        assertEquals(817856d, repo.getRefuel(UID).getEndNumber(), 0d);
    }

    @Test
    public void columnsMatchJsonAfterConfirmPatch() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));
        fillConfirmScreenFields(snapshot);

        assertTrue(RefuelItemData.isCommitted(DataHelper.saveConfirmFields(snapshot)));
        assertColumnsMatchJson("ConfirmFieldsPatch");
    }

    @Test
    public void columnsMatchJsonAfterEndPatch() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));
        fillConfirmScreenFields(snapshot);

        assertTrue(RefuelItemData.isCommitted(DataHelper.saveEndFields(snapshot)));
        assertColumnsMatchJson("EndFieldsPatch");
    }

    /** Lượt pull nền cũng phải giữ cột khớp json. */
    @Test
    public void columnsMatchJsonAfterBackgroundPull() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);
        DataHelper.postRefuel(snapshot, false);

        backgroundPullChangesFlightStatus();

        assertColumnsMatchJson("sau REMOTE_PULL");
    }

    /** Và sau khi server ACK. */
    @Test
    public void columnsMatchJsonAfterServerAck() {
        seedRow();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        fillConfirmScreenFields(snapshot);
        DataHelper.postRefuel(snapshot, false);

        RefuelItem row = repo.getRefuel(UID);
        RefuelItemData ack = row.toRefuelItemData();
        ack.setServerRevision(9);
        RefuelSyncGuard.applyServerAck(row, ack);
        repo.insertRefuel(row);

        assertColumnsMatchJson("sau applyServerAck");
        assertEquals(399d, repo.getRefuel(UID).getRealAmount(), 0d);
    }


    /**
     * Dữ liệu CŨ trên xe do các bản trước ghi: jsonData đúng nhưng cột 0/null. Sau khi nâng
     * cấp, bảo trì một lần phải chiếu lại cột — nếu không mọi màn hình đọc theo cột vẫn hiện
     * 0 GL cho toàn bộ lịch sử.
     */
    @Test
    public void upgradeBackfillFixesLegacyZeroColumns() {
        seedRow();

        // Mô phỏng row do bản cũ ghi: json đủ, cột trống.
        RefuelItem legacy = repo.getRefuel(UID);
        RefuelItemData data = legacy.toRefuelItemData();
        fillConfirmScreenFields(data);
        legacy.setJsonData(data.toJson());
        legacy.setRealAmount(0);
        legacy.setStartNumber(0);
        legacy.setEndNumber(0);
        legacy.setDensity(0);
        legacy.setQualityNo(null);
        legacy.setLocalModified(false);
        legacy.setPostStatus(RefuelItem.ITEM_POST_STATUS.SUCCESS);
        repo.insertRefuel(legacy);

        assertEquals(1, DataHelper.backfillColumnsFromJson());

        RefuelItem fixed = repo.getRefuel(UID);
        assertEquals(399d, fixed.getRealAmount(), 0d);
        assertEquals(817856d, fixed.getEndNumber(), 0d);
        assertEquals(0.789d, fixed.getDensity(), 0d);
        assertEquals("D", fixed.getQualityNo());

        // Chiếu lại cột KHÔNG phải sửa dữ liệu: không được đẩy phiếu đã gửi vào hàng đợi.
        assertFalse("không được đánh dấu chờ gửi lại", fixed.isLocalModified());
        assertEquals(RefuelItem.ITEM_POST_STATUS.SUCCESS, fixed.getPostStatus());
    }



    /** Converter không được ném khi model mang giá trị enum mà entity không có. */
    @Test
    public void cancelledFlightStatusDoesNotBreakTheRow() {
        seedRow();
        RefuelItemData data = repo.getRefuel(UID).toRefuelItemData();
        data.setFlightStatus(RefuelItemData.FLIGHT_STATUS.CANCELLED);

        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(data, false)));
        assertNotNull(repo.getRefuel(UID).getFlightStatus());
        assertNotNull(RefuelItem.FLIGHT_STATUS.getInt(null));
    }

    /** Không có mạng: dữ liệu vẫn phải nằm trong Room và còn trong hàng đợi đồng bộ. */
    private static class OfflineHttpClient extends HttpClient {
        OfflineHttpClient() {
            super("test-token");
        }

        @Override
        public RefuelItemData postRefuel(RefuelItemData refuelData) {
            return null;
        }

        @Override
        public RefuelItemData getRefuelItem(String uniqueId) {
            return null;
        }
    }
}
