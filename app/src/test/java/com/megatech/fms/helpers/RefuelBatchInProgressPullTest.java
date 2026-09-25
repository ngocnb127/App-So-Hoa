package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

import java.util.Collections;
import java.util.Date;

/**
 * Mẻ ĐANG BƠM trên máy này thì lượt pull không được đụng vào số liệu đồng hồ.
 *
 * <p>Sự cố xe thật 27-08-2026 18:28, chuyến CX 048 (`uid 4f9de888`): màn hình tra nạp kẹt hoàn
 * toàn, mỗi giây một dòng
 * {@code REBASE_SCREEN_REFUSED ... payload nghiệp vụ đã đổi, xung đột thật}, và 2359 GL không
 * chốt được — xe đứng tại tàu bay.
 *
 * <p>Chuỗi nhân quả: autosave đặt dirty → POST nền ACK xong xoá dirty → khe row SẠCH → lượt
 * pull rơi đúng khe đó phủ toàn bộ khoá server gửi lên row, gồm cả nhóm client sở hữu → vân tay
 * nghiệp vụ đổi → nền màn hình không còn khớp row → mọi lần lưu sau đều CONFLICT, và
 * {@code rebaseScreenOnStored} từ chối vì đúng là xung đột thật. Không có đường thoát.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class RefuelBatchInProgressPullTest {

    private static final String UID = "4f9de888-152d-4000-9afb-70c956c1e677";

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

    /** Mẻ đang bơm, row SẠCH (POST nền vừa ACK xong) — đúng khe mà lượt pull lọt vào. */
    private void seedRunningBatchAlreadySynced() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2121817);
        data.setUniqueId(UID);
        data.setFlightUniqueId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1300001);
        data.setFlightCode("CX 048");
        data.setTruckId(34);
        data.setTruckNo("DEMO-03");
        data.setRealAmount(2359);
        data.setGallon(2359);
        data.setStartNumber(49196161);
        data.setEndNumber(49198520);
        data.setDensity(0.785);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_900_000L));
        data.setClientSeq(40);
        data.setServerRevision(12);

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        row.setLocalModified(false);          // ← POST nền vừa ACK: row SẠCH
        repo.insertRefuel(row);
    }

    /**
     * Lượt pull nền mang bản server CŨ của chính mẻ đang bơm.
     *
     * <p>Đi qua {@link DataHelper#applyModifiedRefuelBatch} — ĐÚNG đường mà lượt đồng bộ nền
     * dùng thật. Test phải kiểm chính quyết định của tầng đồng bộ, không phải một bản chép lại
     * luật bên trong test.
     */
    private void backgroundPullReturnsStaleMeter() {
        RefuelItemData remote = new RefuelItemData();
        remote.setUniqueId(UID);
        remote.setId(2121817);
        remote.setTruckId(34);
        remote.setTruckNo("DEMO-03");
        remote.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        remote.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        remote.setRealAmount(862);
        remote.setGallon(862);
        remote.setStartNumber(49196161);
        remote.setEndNumber(49197023);
        remote.setServerRevision(12);
        remote.setRawJson("{\"UniqueId\":\"" + UID + "\",\"Id\":2121817,"
                + "\"TruckNo\":\"DEMO-03\",\"TruckId\":34,\"Status\":\"1\","
                + "\"RealAmount\":862,\"Gallon\":862,"
                + "\"StartNumber\":49196161,\"EndNumber\":49197023,"
                + "\"ServerRevision\":12}");

        DataHelper.applyModifiedRefuelBatch(
                Collections.singletonList(remote), DataHelper.beginUnscopedRefuelRead());
    }

    /** Số đồng hồ của mẻ đang bơm không được bản server cũ phủ lên. */
    @Test
    public void pullCannotOverwriteMeterOfABatchRunningOnThisTruck() {
        seedRunningBatchAlreadySynced();

        backgroundPullReturnsStaleMeter();

        RefuelItemData saved = repo.getRefuel(UID).toRefuelItemData();
        assertEquals("sản lượng của mẻ đang bơm phải giữ nguyên",
                2359d, saved.getRealAmount(), 0d);
        assertEquals(49198520d, saved.getEndNumber(), 0d);
    }

    /**
     * Hệ quả then chốt: vân tay nghiệp vụ không đổi, nên màn hình còn rebase được và mẻ chốt
     * được. Đây mới là thứ làm xe kẹt tại tàu bay, chứ không chỉ là sai số.
     */
    @Test
    public void screenCanStillRebaseAfterAPullDuringRefuelling() {
        seedRunningBatchAlreadySynced();

        RefuelItemData screen = repo.getRefuel(UID).toRefuelItemData();
        String fingerprintBefore = RefuelSyncGuard.businessFingerprintOfJson(
                repo.getRefuel(UID).getJsonData());

        backgroundPullReturnsStaleMeter();

        String fingerprintAfter = RefuelSyncGuard.businessFingerprintOfJson(
                repo.getRefuel(UID).getJsonData());
        assertNotNull(fingerprintBefore);
        assertEquals("vân tay nghiệp vụ không được đổi trong lúc mẻ đang bơm",
                fingerprintBefore, fingerprintAfter);
        assertEquals("nền của màn hình vẫn khớp row, nên rebase được và chốt được mẻ",
                fingerprintBefore, screen.getBaseBusinessFingerprint());
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
