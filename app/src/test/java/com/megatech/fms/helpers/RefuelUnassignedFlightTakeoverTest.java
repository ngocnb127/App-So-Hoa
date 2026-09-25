package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * Màn hình tra nạp KHÔNG chặn mẻ của chuyến chưa phân công cho xe này.
 *
 * <p>Quy trình hiện trường có thật: chuyến điều cho xe A nhưng xe B mở đúng phiếu đó ra và
 * bơm. Guard quyền sở hữu từng chặn thẳng, và mẻ có thật không ghi được — xe HAN3-20-7002
 * lúc 27-08-2026 21:39 (3029 GL nhập tay vì LCR chết), xe HAN3-20-7012 kẹt 19:59–20:24.
 *
 * <p>Quyết định nghiệp vụ: người đang đứng tại tàu bay biết rõ nhất mẻ nào vừa bơm; mất số
 * liệu tệ hơn nhiều so với ghi nhầm chủ sở hữu. Ghi nhận, và để lại dấu vết để đối soát.
 *
 * <p>Điều KHÔNG được nới: các đường nền/batch/ACK vẫn giữ guard fail-closed — xem test cuối.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelUnassignedFlightTakeoverTest {

    private static final String UID = "7c9a1b2e-51d4-4a08-9f3c-2b6ee0d1a774";
    private static final String OWN_TRUCK = "HAN3-20-7002";
    private static final int OWN_TRUCK_ID = 7002;

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(OWN_TRUCK_ID);
        setting.setTruckNo(OWN_TRUCK);
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

    /** Phiếu của XE KHÁC, đã kéo về máy này dưới dạng bản sao chỉ đọc. */
    private void seedForeignReplica() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2121816);
        data.setUniqueId(UID);
        data.setFlightUniqueId("ffffffff-1111-2222-3333-444444444444");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1400001);
        data.setFlightCode("VN 219");
        data.setTruckId(7012);
        data.setTruckNo("HAN3-20-7012");
        data.setStartNumber(56636400);
        data.setEndNumber(56636400);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_100_000L));
        data.setRawJson("{\"UniqueId\":\"" + UID + "\",\"Id\":2121816,"
                + "\"TruckNo\":\"HAN3-20-7012\",\"TruckId\":7012,\"Status\":\"1\"}");

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(true);
        repo.insertRefuel(row);
    }

    /** Đúng thao tác của người dùng: nhập tay số đồng hồ rồi chốt mẻ. */
    private RefuelItemData manualEndOnThisTruck() {
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setEndNumber(56639429);
        screen.setRealAmount(3029);
        screen.setGallon(3029);
        return screen;
    }

    /** Mẻ có thật phải được ghi, dù chuyến chưa phân công cho xe này. */
    @Test
    public void refuelScreenRecordsABatchForAnUnassignedFlight() {
        seedForeignReplica();

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(
                manualEndOnThisTruck(), false);

        assertTrue("mẻ có thật phải được ghi", RefuelItemData.isCommitted(saved));
        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(3029d, stored.getRealAmount(), 0d);
        assertEquals(56639429d, stored.getEndNumber(), 0d);
    }

    /** Xe đang bơm trở thành xe sở hữu, và cờ chỉ-đọc được gỡ để lần ghi sau không kẹt. */
    @Test
    public void takeoverStampsThisTruckAndClearsTheReadOnlyMarker() {
        seedForeignReplica();

        DataHelper.postRefuelFromRefuelScreen(manualEndOnThisTruck(), false);

        RefuelItem row = repo.getRefuel(UID);
        assertFalse("cờ replica phải được gỡ", row.isRemoteReplica());
        assertEquals(OWN_TRUCK, row.getTruckNo());
        assertEquals(OWN_TRUCK_ID, row.getTruckId());
    }

    /** Ghi được lần đầu thì lần sau cũng phải ghi được — không quay lại trạng thái kẹt. */
    @Test
    public void aSecondSaveAfterTakeoverStillWorks() {
        seedForeignReplica();
        DataHelper.postRefuelFromRefuelScreen(manualEndOnThisTruck(), false);

        RefuelItemData again = repo.getRefuel(UID).toRefuelItemData();
        again.setQualityNo("QC-77");

        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(again, false)));
        assertEquals("QC-77", repo.getRefuel(UID).toRefuelItemData().getQualityNo());
    }

    /**
     * Điều KHÔNG được nới: đường ghi thường vẫn chặn.
     *
     * <p>Nếu nới ở {@code postRefuel} chung thì một snapshot cũ còn sót lại sau khi server đổi
     * xe cũng gỡ được cờ replica — đúng lỗ hổng mà bản vá toàn vẹn dữ liệu sinh ra để bịt.
     */
    @Test
    public void ordinaryWritePathStillRefusesAForeignReplica() {
        seedForeignReplica();

        RefuelItemData rejected = DataHelper.postRefuel(manualEndOnThisTruck(), false);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
        assertEquals("HAN3-20-7012", repo.getRefuel(UID).getTruckNo());
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
