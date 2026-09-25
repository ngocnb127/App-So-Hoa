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
 * MÀN HÌNH XÁC NHẬN cũng phải ghi được mẻ của chuyến chưa phân công cho xe.
 *
 * <p>Màn tra nạp đã được nới từ trước, nhưng bước xác nhận vẫn đi đường fail-closed: xe B mở
 * chuyến của xe A, bơm xong, vào màn xác nhận rồi bấm Xác nhận → bị từ chối, mẻ có thật KẸT
 * đúng ở bước cuối. Đường dự phòng {@code saveConfirmFields} cũng chặn y hệt, nên sửa mỗi
 * lượt lưu đầu tiên chỉ dời lỗi sang lần thử lại.
 *
 * <p>Điều KHÔNG được nới: batch / ACK / {@code saveEndFields} / {@code savePreviewFields}
 * vẫn giữ nguyên fail-closed — xem hai test cuối.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelConfirmTakeoverTest {

    private static final String UID = "1d5b7c31-9a02-4f66-8d10-3e77a5b41c92";
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
        data.setId(2131900);
        data.setUniqueId(UID);
        data.setFlightUniqueId("aaaaaaaa-1111-2222-3333-555555555555");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1400777);
        data.setFlightCode("VN 245");
        data.setTruckId(7012);
        data.setTruckNo("HAN3-20-7012");
        data.setStartNumber(56636400);
        data.setEndNumber(56636400);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_100_000L));
        data.setRawJson("{\"UniqueId\":\"" + UID + "\",\"Id\":2131900,"
                + "\"TruckNo\":\"HAN3-20-7012\",\"TruckId\":7012,\"Status\":\"1\"}");

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setRemoteReplica(true);
        repo.insertRefuel(row);
    }

    /** Đúng thứ màn xác nhận cầm: mẻ đã chốt số, người dùng nhập thêm nhiệt độ / tỉ trọng. */
    private RefuelItemData confirmScreenItem() {
        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        screen.setStatus(REFUEL_ITEM_STATUS.DONE);
        screen.setEndNumber(56639429);
        screen.setRealAmount(3029);
        screen.setGallon(3029);
        screen.setManualTemperature(31.5);
        screen.setDensity(0.7942);
        return screen;
    }

    /** Màn hình biết được rằng đây là mẻ của chuyến chưa phân công, để còn cảnh báo. */
    @Test
    public void manHinhNhanBietDuocMeChuaPhanCong() {
        seedForeignReplica();

        assertTrue(DataHelper.isForeignTruckRefuel(confirmScreenItem()));
    }

    /** Mẻ tra nạp hộ phải XÁC NHẬN ĐƯỢC. */
    @Test
    public void meChuyenChuaPhanCongVanXacNhanDuoc() {
        seedForeignReplica();

        RefuelItemData saved = DataHelper.postRefuelFromRefuelScreen(confirmScreenItem(), false);

        assertTrue("mẻ có thật phải được ghi", RefuelItemData.isCommitted(saved));
        RefuelItemData stored = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(3029d, stored.getRealAmount(), 0d);
        assertEquals(31.5d, stored.getManualTemperature(), 0.0001);
    }

    /**
     * Đường dự phòng của màn xác nhận cũng phải đi lối tiếp quản.
     *
     * <p>Nếu chỉ nới lượt lưu đầu tiên thì lần thử lại bằng patch vẫn bị chặn, và người dùng
     * gặp đúng ngõ cụt cũ — chỉ muộn hơn một nhịp.
     */
    @Test
    public void duongPatchDuPhongCungGhiDuoc() {
        seedForeignReplica();

        RefuelItemData saved = DataHelper.saveConfirmFieldsFromRefuelScreen(confirmScreenItem());

        assertTrue("patch tiếp quản phải ghi được", RefuelItemData.isCommitted(saved));
        assertEquals(3029d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
        assertFalse("cờ replica phải được gỡ", repo.getRefuel(UID).isRemoteReplica());
    }

    /** Đường patch THƯỜNG của màn xác nhận vẫn fail-closed. */
    @Test
    public void duongPatchThuongVanFailClosed() {
        seedForeignReplica();

        RefuelItemData rejected = DataHelper.saveConfirmFields(confirmScreenItem());

        assertFalse("không được coi là đã lưu", RefuelItemData.isCommitted(rejected));
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
        assertEquals("HAN3-20-7012", repo.getRefuel(UID).getTruckNo());
    }

    /** saveEndFields / savePreviewFields KHÔNG được nới theo. */
    @Test
    public void duongEndVaPreviewVanFailClosed() {
        seedForeignReplica();

        assertFalse(RefuelItemData.isCommitted(DataHelper.saveEndFields(confirmScreenItem())));
        assertFalse(RefuelItemData.isCommitted(DataHelper.savePreviewFields(confirmScreenItem())));
        assertTrue("cờ replica phải còn nguyên", repo.getRefuel(UID).isRemoteReplica());
    }

    /** Đường ghi thường (batch / ACK dùng chung lối này) vẫn chặn. */
    @Test
    public void duongGhiThuongVanFailClosed() {
        seedForeignReplica();

        RefuelItemData rejected = DataHelper.postRefuel(confirmScreenItem(), false);

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
