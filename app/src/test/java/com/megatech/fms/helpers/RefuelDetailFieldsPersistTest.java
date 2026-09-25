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
 * Giá trị người dùng nhập ở MÀN HÌNH TRA NẠP phải vào Room, kể cả khi đồng hồ đang chạy.
 *
 * <p>Trong lúc tra nạp, autosave thiết bị tăng {@code ClientSeq} mỗi giây và lượt pull nền ghi
 * lại jsonData mỗi 30 giây. Một hộp thoại mở vài giây là baseline đã dịch. Đo trên máy thật
 * 27-08-2026 13:43:34: sửa bãi đỗ trả {@code CONFLICT} kèm {@code REBASE_SCREEN_REFUSED},
 * giá trị mất hẳn mà hộp thoại đã đóng.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelDetailFieldsPersistTest {

    private static final String UID = "e5afa312-407e-4d05-8d59-391916938c50";

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TruckModel setting = new TruckModel();
        setting.setTruckId(34);
        setting.setTruckNo("DEMO-03");
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

    /** Mẻ đang tra nạp của chính xe này. */
    private void seedProcessingRow() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2122136);
        data.setUniqueId(UID);
        data.setFlightUniqueId("11111111-2222-3333-4444-555555555555");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1265515);
        data.setFlightCode("VU 635-01");
        data.setTruckId(34);
        data.setTruckNo("DEMO-03");
        data.setParkingLot("A1");
        data.setManualTemperature(27);
        data.setDensity(0.786);
        data.setStartNumber(44685105);
        data.setEndNumber(44685105);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_100_000L));
        data.setClientSeq(2);
        data.setServerRevision(1);

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(true);
        repo.insertRefuel(row);
    }

    /** Autosave của đồng hồ vừa ghi số mới, làm ClientSeq tiến lên. */
    private void deviceAutosaveAdvancesMeter(double endNumber) {
        RefuelItemData device = repo.getRefuel(UID).toRefuelItemData();
        device.setEndNumber(endNumber);
        device.setRealAmount(endNumber - device.getStartNumber());
        DataHelper.postRefuel(device, false);
    }

    /** Ca trong log máy thật: sửa bãi đỗ trong lúc đồng hồ đang chạy. */
    @Test
    public void parkingLotTypedWhileMeterRunningIsNotLost() {
        seedProcessingRow();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        deviceAutosaveAdvancesMeter(44685500);

        screen.setParkingLot("B7");
        RefuelItemData result = DataHelper.postRefuel(screen, false);
        if (!RefuelItemData.isCommitted(result))
            result = DataHelper.saveDetailFields(screen);

        assertTrue("bãi đỗ vừa nhập phải được ghi", RefuelItemData.isCommitted(result));
        assertEquals("B7", repo.getRefuel(UID).toRefuelItemData().getParkingLot());
    }

    /** Đường phục hồi không được kéo lùi số đồng hồ mà thiết bị vừa ghi. */
    @Test
    public void recoveringUserEditKeepsTheMeterNumbersFromDevice() {
        seedProcessingRow();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        deviceAutosaveAdvancesMeter(44685500);

        screen.setDensity(0.801);
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(screen, false)))
            DataHelper.saveDetailFields(screen);

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals(0.801d, saved.getDensity(), 0d);
        assertEquals("số đồng hồ của thiết bị phải còn nguyên",
                44685500d, saved.getEndNumber(), 0d);
        assertEquals(395d, saved.getRealAmount(), 0d);
    }

    /** Row vẫn phải nằm trong hàng đợi đẩy lên server sau khi phục hồi. */
    @Test
    public void recoveredDetailEditStaysQueuedForSync() {
        seedProcessingRow();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        deviceAutosaveAdvancesMeter(44685500);

        screen.setQualityNo("QC-9");
        if (!RefuelItemData.isCommitted(DataHelper.postRefuel(screen, false)))
            DataHelper.saveDetailFields(screen);

        assertEquals("QC-9", repo.getRefuel(UID).toRefuelItemData().getQualityNo());
        assertTrue(repo.getRefuel(UID).isLocalModified());
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
