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
 * NGUYÊN NHÂN GỐC của ngõ cụt "mẻ chưa được lưu" (FA-1).
 *
 * <p>Chuỗi chốt mẻ ở màn tra nạp có hai nhịp: nhịp một đi
 * {@code postRefuelFromRefuelScreen} nên CHO PHÉP tiếp quản chuyến chưa phân công, nhịp hai
 * (đường cứu khi CONFLICT) lại đi {@code saveEndFields} fail-closed. Mẻ tra nạp hộ gặp xung
 * đột lúc chốt vì thế bị từ chối VĨNH VIỄN: nút "Thử lại" gửi lại đúng lối bị chặn nên hỏng
 * y hệt mọi lần.
 *
 * <p>Điều KHÔNG được nới theo: {@code saveEndFields}, {@code savePreviewFields}, và đường
 * ghi thường mà batch / ACK dùng chung — xem ba test cuối.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelEndTakeoverTest {

    private static final String UID = "7c41f0aa-2ee1-4b70-9c33-06d1f2a94e18";
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

    /** Phiếu của XE KHÁC đã kéo về máy này dưới dạng bản sao chỉ đọc. */
    private void seedForeignReplica() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2141900);
        data.setUniqueId(UID);
        data.setFlightUniqueId("bbbbbbbb-1111-2222-3333-555555555555");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1400888);
        data.setFlightCode("VN 247");
        data.setTruckId(7012);
        data.setTruckNo("HAN3-20-7012");
        data.setStartNumber(56636400);
        data.setEndNumber(56636400);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_100_000L));
        data.setRawJson("{\"UniqueId\":\"" + UID + "\",\"Id\":2141900,"
                + "\"TruckNo\":\"HAN3-20-7012\",\"TruckId\":7012,\"Status\":\"1\"}");

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(true);
        repo.insertRefuel(row);
    }

    /** Đúng thứ màn tra nạp cầm lúc bấm kết thúc: mẻ đã chốt số đồng hồ. */
    private RefuelItemData endScreenItem() {
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setEndNumber(56639429);
        screen.setRealAmount(3029);
        screen.setGallon(3029);
        screen.setEndTime(new Date(1_787_000_900_000L));
        return screen;
    }

    /** FA-1: nhịp hai của chuỗi chốt mẻ phải ghi được mẻ tra nạp hộ. */
    @Test
    public void nhipHaiCuaChotMeGhiDuocMeTraNapHo() {
        seedForeignReplica();

        RefuelItemData saved = DataHelper.saveEndFieldsFromRefuelScreen(endScreenItem());

        assertTrue("patch tiếp quản của luồng End phải ghi được",
                RefuelItemData.isCommitted(saved));
        assertEquals(3029d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
        assertFalse("cờ replica phải được gỡ", repo.getRefuel(UID).isRemoteReplica());
    }

    /** CHỐNG HỒI QUY: đường End THƯỜNG vẫn fail-closed. */
    @Test
    public void duongEndThuongVanFailClosed() {
        seedForeignReplica();

        assertFalse("saveEndFields không được nới theo",
                RefuelItemData.isCommitted(DataHelper.saveEndFields(endScreenItem())));
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
        assertEquals("HAN3-20-7012", repo.getRefuel(UID).getTruckNo());
    }

    /** CHỐNG HỒI QUY: màn xem trước vẫn fail-closed. */
    @Test
    public void duongPreviewVanFailClosed() {
        seedForeignReplica();

        assertFalse(RefuelItemData.isCommitted(DataHelper.savePreviewFields(endScreenItem())));
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
    }

    /** CHỐNG HỒI QUY: đường ghi thường (batch / ACK dùng chung) vẫn chặn. */
    @Test
    public void duongGhiThuongVanFailClosed() {
        seedForeignReplica();

        RefuelItemData rejected = DataHelper.postRefuel(endScreenItem(), false);

        assertEquals(RefuelItemData.SAVE_OUTCOME.FAILED, rejected.getSaveOutcome());
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
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
