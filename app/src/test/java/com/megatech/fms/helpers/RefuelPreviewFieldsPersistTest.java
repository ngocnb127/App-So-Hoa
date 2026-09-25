package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * Giá trị người dùng nhập ở MÀN HÌNH XEM TRƯỚC phải nằm trong Room, kể cả khi baseline đã
 * dịch trong lúc hộp thoại còn mở.
 *
 * <p>Phản ánh hiện trường: "nhiệt độ, tỉ trọng đôi khi phải nhập mấy lần mới ăn". Màn hình
 * xem trước lưu bằng FULL SNAPSHOT và trước bản vá còn spawn một {@code Thread} riêng cho
 * mỗi hộp thoại, nên hai lần sửa liên tiếp chạy song song trên cùng một baseline: lần sau
 * đứng trên {@code ClientSeq} đã cũ và bị chặn, giá trị vừa gõ biến mất.
 *
 * <p>Sau bản vá: các lần lưu được xếp hàng một luồng, và lần nào bị chặn thì đắp lại đúng
 * nhóm trường màn hình này được phép nhập lên row mới nhất
 * ({@link DataHelper#savePreviewFields}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelPreviewFieldsPersistTest {

    private static final String UID = "3c1f9d84-6b2a-4d77-9e15-8a0c4b7e2f30";

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(173);
        setting.setTruckNo("XT-01");
        ((FMSApplication) context).saveSetting(setting, false);
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

    /** Mẻ đã chốt của chính xe này, đúng như row màn hình xem trước đang hiển thị. */
    private void seedRow() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2121361);
        data.setUniqueId(UID);
        data.setFlightUniqueId("9f2b1a55-33c7-4e10-b0d6-71c9e5a2b884");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setFlightId(1265515);
        data.setFlightCode("VJ718");
        data.setTruckId(173);
        data.setTruckNo("XT-01");
        data.setParkingLot("A1");
        data.setAircraftCode("VN-A630");
        data.setRealAmount(1416);
        data.setStartNumber(10955554);
        data.setEndNumber(10956970);
        data.setManualTemperature(27);
        data.setDensity(0.786);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_900_000L));
        data.setClientSeq(2);
        data.setServerRevision(1);

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(true);
        repo.insertRefuel(row);
    }

    /** Một lần ghi khác đã commit xong, làm ClientSeq của row tiến lên. */
    private void anotherWriterCommits(double temperature) {
        RefuelItemData other = repo.getRefuel(UID).toRefuelItemData();
        other.setManualTemperature(temperature);
        DataHelper.postRefuel(other, true);
    }

    /**
     * Đúng ca người dùng báo: gõ nhiệt độ, rồi gõ tỉ trọng ngay sau đó. Hộp thoại thứ hai
     * vẫn cầm baseline từ lúc mở màn hình.
     */
    @Test
    public void densityTypedAfterTemperatureIsNotLost() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        previewSnapshot.setDensity(0.801);
        RefuelItemData result = DataHelper.postRefuel(previewSnapshot, true);
        if (!RefuelItemData.isCommitted(result))
            result = DataHelper.savePreviewFields(previewSnapshot);

        assertTrue("tỉ trọng vừa nhập phải được ghi", RefuelItemData.isCommitted(result));
        assertEquals(0.801d, repo.getRefuel(UID).toRefuelItemData().getDensity(), 0d);
    }

    /** Giá trị của lần ghi trước không được bản vá làm mất ngược lại. */
    @Test
    public void recoveringDensityKeepsTheTemperatureAlreadyCommitted() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        previewSnapshot.setDensity(0.801);
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(previewSnapshot, true)))
            DataHelper.savePreviewFields(previewSnapshot);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals("nhiệt độ của lần ghi trước phải còn nguyên",
                31d, saved.getManualTemperature(), 0d);
        assertEquals(0.801d, saved.getDensity(), 0d);
    }

    /** Đường phục hồi không được đụng số liệu chốt của mẻ. */
    @Test
    public void previewRecoveryDoesNotTouchMeterNumbers() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        previewSnapshot.setManualTemperature(29);
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(previewSnapshot, true)))
            DataHelper.savePreviewFields(previewSnapshot);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(10955554d, saved.getStartNumber(), 0d);
        assertEquals(10956970d, saved.getEndNumber(), 0d);
        assertEquals(1416d, saved.getRealAmount(), 0d);
    }

    /** Sau khi ghi, row vẫn phải nằm trong hàng đợi đẩy lên server. */
    @Test
    public void savedPreviewEditStaysQueuedForSync() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        previewSnapshot.setDensity(0.801);
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(previewSnapshot, true)))
            DataHelper.savePreviewFields(previewSnapshot);

        assertTrue("phải còn dirty để lượt đồng bộ sau đẩy lên",
                repo.getRefuel(UID).isLocalModified());
    }

    /**
     * Ca trong log xe PQC ngày 27-08-2026 lúc 11:55:00.
     *
     * <p>In phiếu xong, bấm Back về màn xem trước, sửa số hiệu tàu bay VNA513 → VNA514 và
     * nhận "Sửa phiếu chưa lưu được". Đường in vừa đắp metadata phiếu lên row nên ClientSeq
     * đã tiến, còn đối tượng trên màn hình vẫn giữ baseline từ trước lúc in.
     *
     * <p>Số hiệu tàu bay đi qua {@code setAll()} nên rơi vào nhánh ghi hàng loạt — nhánh mà
     * lần vá đầu chưa phủ.
     */
    @Test
    public void aircraftCodeEditedRightAfterPrintingIsNotLost() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        printFlowStampsReceiptMetadata();

        previewSnapshot.setAircraftCode("VNA514");
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(previewSnapshot, true)))
            DataHelper.savePreviewFields(previewSnapshot);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals("số hiệu tàu bay vừa sửa phải được ghi",
                "VNA514", saved.getAircraftCode());
        assertEquals("metadata phiếu in không được mất ngược lại",
                "HD-9001", saved.getReceiptNumber());
    }

    /** Đường in đắp metadata lên row mới nhất, làm ClientSeq tiến lên. */
    private void printFlowStampsReceiptMetadata() {
        DataHelper.patchRefuel(UID, latest -> latest.setReceiptNumber("HD-9001"));
    }

    /**
     * Ca người dùng báo 27-08-2026: vừa sửa GIỜ KẾT THÚC thì load lại mất.
     *
     * <p>{@code StartTime}/{@code EndTime} đi qua {@code showTimeDialog()} — một đường riêng,
     * không nằm trong switch của hộp thoại nhập chữ. Lần đầu dựng {@code Scope.PREVIEW} tôi
     * liệt kê trường theo switch đó nên bỏ sót hai khoá này, và patch phục hồi ĐÃ BỎ IM LẶNG
     * đúng giá trị vừa gõ — tệ hơn cả trước khi có patch, vì trước đó ít nhất còn báo lỗi.
     */
    @Test
    public void endTimeEditedOnPreviewIsNotSilentlyDropped() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        Date newEnd = new Date(1_787_001_500_000L);
        previewSnapshot.setEndTime(newEnd);
        RefuelItemData result = DataHelper.postRefuel(previewSnapshot, true);
        if (!RefuelItemData.isCommitted(result))
            result = DataHelper.savePreviewFields(previewSnapshot);

        assertTrue("giờ kết thúc vừa sửa phải được ghi", RefuelItemData.isCommitted(result));
        assertEquals(newEnd.getTime(),
                repo.getRefuel(UID).toRefuelItemData().getEndTime().getTime());
    }

    /** Giờ bắt đầu đi cùng đường, khoá luôn để khỏi sót một nửa. */
    @Test
    public void startTimeEditedOnPreviewIsNotSilentlyDropped() {
        seedRow();

        RefuelItemData previewSnapshot = repo.getRefuel(UID).toRefuelItemData();
        anotherWriterCommits(31);

        Date newStart = new Date(1_787_000_050_000L);
        previewSnapshot.setStartTime(newStart);
        RefuelItemData result = DataHelper.postRefuel(previewSnapshot, true);
        if (!RefuelItemData.isCommitted(result))
            result = DataHelper.savePreviewFields(previewSnapshot);

        assertTrue(RefuelItemData.isCommitted(result));
        assertEquals(newStart.getTime(),
                repo.getRefuel(UID).toRefuelItemData().getStartTime().getTime());
    }

    /**
     * Nguyên tắc: thứ sửa trên màn xem trước là MỚI NHẤT, lượt đọc lại không được kéo bản
     * server cũ đè lên.
     *
     * <p>{@code ParkingLot} thuộc nhóm SHARED nên nằm trong {@code SERVER_OWNED_KEYS} — trước
     * bản vá, mỗi lần load lại là nó quay về giá trị cũ của server.
     */
    @Test
    public void serverPullCannotRevertAFieldTheUserJustEdited() {
        seedRow();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setParkingLot("B7");
        DataHelper.postRefuel(screen, true);
        assertEquals("B7", repo.getRefuel(UID).toRefuelItemData().getParkingLot());

        // Lượt đọc lại: server vẫn còn bãi đỗ cũ.
        serverPullReturns("{\"UniqueId\":\"" + UID + "\",\"ParkingLot\":\"A1\"}");

        assertEquals("bãi đỗ vừa sửa không được bị bản server cũ đè lên",
                "B7", repo.getRefuel(UID).toRefuelItemData().getParkingLot());
    }

    /** Nhưng thay đổi của điều độ ở khoá người dùng KHÔNG sửa thì vẫn phải về xe. */
    @Test
    public void dispatchChangeStillArrivesForFieldsTheUserDidNotEdit() {
        seedRow();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setParkingLot("B7");
        DataHelper.postRefuel(screen, true);

        // Điều độ đổi số hiệu tàu bay trên web; người dùng không đụng tới trường này.
        serverPullReturns("{\"UniqueId\":\"" + UID + "\",\"ParkingLot\":\"A1\","
                + "\"AircraftCode\":\"VN-A999\"}");

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals("thay đổi của điều độ vẫn phải nhận", "VN-A999", saved.getAircraftCode());
        assertEquals("còn bãi đỗ đang chờ gửi thì vẫn giữ", "B7", saved.getParkingLot());
    }

    /** Mô phỏng một lượt pull mang payload server về. */
    private void serverPullReturns(String rawJson) {
        RefuelItem stored = repo.getRefuel(UID);
        RefuelItemData remote = new RefuelItemData();
        remote.setRawJson(rawJson);
        RefuelSyncGuard.applyRemote(stored, remote, false, false,
                DataHelper.keepLocalKeysForTesting(UID));
        repo.insertRefuel(stored);
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
