package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * Khoá server gửi xuống mà model app không biết phải SỐNG SÓT qua mọi đường ghi.
 *
 * <p>Đo được trên máy thật: một lượt pull đắp vào jsonData hơn 30 khoá ngoài model
 * ({@code TechLog}, {@code Weight}, {@code Invoice}, {@code BondingCable}...). Cách ghi cũ
 * thay nguyên jsonData bằng bản serialize từ model, tức mỗi lần lưu local là một lần app âm
 * thầm xoá dữ liệu của server rồi đẩy bản thiếu đó lên ở lần POST kế tiếp.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class UnknownKeyPreservationTest {

    private static final String UID = "unknown-key-uid";

    /** Đủ các dạng: chuỗi, số, boolean, object lồng, array, và một khoá lạ mang null. */
    private static final String SERVER_EXTRAS =
            "\"TechLog\":\"abc\",\"Weight\":100.5,\"BondingCable\":true,"
                    + "\"Invoice\":{\"Id\":9,\"No\":\"HD-1\"},"
                    + "\"AirportId\":77,\"FlightType\":[1,2,3],\"AirlineType\":null";

    private AppDatabase db;
    private DataRepository repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repo = DataRepository.forTesting(db);
        DataHelper.installTestDependencies(repo, new OfflineHttpClient());
        DataHelper.lockSync();
    }

    @After
    public void tearDown() {
        DataHelper.resetTestDependencies();
        db.close();
    }

    private RefuelItem seedRowWithServerExtras() {
        RefuelItemData data = new RefuelItemData();
        data.setId(555);
        data.setUniqueId(UID);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setEndTime(new Date(1_787_000_000_000L));

        RefuelItem row = RefuelItem.fromRefuelItemData(data);
        // Chèn nhóm khoá server vào jsonData, đúng như lượt pull vẫn làm.
        String json = row.getJsonData();
        row.setJsonData(json.substring(0, json.length() - 1) + "," + SERVER_EXTRAS + "}");
        row.setLocalModified(true);
        repo.insertRefuel(row);
        return repo.getRefuel(UID);
    }

    private void assertExtrasIntact(String where) {
        com.google.gson.JsonObject json = com.google.gson.JsonParser
                .parseString(repo.getRefuel(UID).getJsonData()).getAsJsonObject();

        assertEquals(where, "abc", json.get("TechLog").getAsString());
        assertEquals(where, 100.5, json.get("Weight").getAsDouble(), 0d);
        assertTrue(where, json.get("BondingCable").getAsBoolean());
        assertEquals(where, 9, json.getAsJsonObject("Invoice").get("Id").getAsInt());
        assertEquals(where, "HD-1", json.getAsJsonObject("Invoice").get("No").getAsString());
        assertEquals(where, 77, json.get("AirportId").getAsInt());
        assertEquals(where, 3, json.getAsJsonArray("FlightType").size());
        assertTrue(where + ": khoá lạ mang null phải còn", json.has("AirlineType"));
    }

    @Test
    public void localSavePreservesUnknownKeys() {
        seedRowWithServerExtras();

        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();
        snapshot.setRealAmount(399);
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(snapshot, false)));

        assertExtrasIntact("lưu local");
        assertEquals(399d, repo.getRefuel(UID).toRefuelItemData().getRealAmount(), 0d);
    }

    @Test
    public void repeatedAutosavesPreserveUnknownKeys() {
        seedRowWithServerExtras();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        for (int i = 1; i <= 5; i++) {
            snapshot.setRealAmount(i * 100);
            DataHelper.postRefuel(snapshot, false);
        }

        assertExtrasIntact("autosave lặp");
    }

    @Test
    public void endFieldsPatchPreservesUnknownKeys() {
        seedRowWithServerExtras();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));

        snapshot.setStatus(REFUEL_ITEM_STATUS.DONE);
        snapshot.setRealAmount(399);
        assertTrue(RefuelItemData.isCommitted(DataHelper.saveEndFields(snapshot)));

        assertExtrasIntact("EndFieldsPatch");
    }

    @Test
    public void confirmFieldsPatchPreservesUnknownKeys() {
        seedRowWithServerExtras();
        RefuelItemData snapshot = repo.getRefuel(UID).toRefuelItemData();

        DataHelper.patchRefuel(UID, latest -> latest.setLeaveTime(new Date(1_787_000_500_000L)));

        snapshot.setManualTemperature(30);
        snapshot.setDensity(0.789);
        assertTrue(RefuelItemData.isCommitted(DataHelper.saveConfirmFields(snapshot)));

        assertExtrasIntact("ConfirmFieldsPatch");
        assertEquals(0.789d, repo.getRefuel(UID).toRefuelItemData().getDensity(), 0d);
    }

    @Test
    public void serverAckPreservesUnknownKeys() {
        RefuelItem row = seedRowWithServerExtras();

        RefuelItemData ack = new RefuelItemData();
        ack.setId(555);
        ack.setUniqueId(UID);
        ack.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        ack.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        ack.setServerRevision(9);

        RefuelSyncGuard.applyServerAck(row, ack);
        repo.insertRefuel(row);

        assertExtrasIntact("applyServerAck");
        assertEquals(9, repo.getRefuel(UID).getServerRevision());
    }

    @Test
    public void backgroundPullOnDirtyRowPreservesUnknownKeys() {
        RefuelItem row = seedRowWithServerExtras();

        RefuelItemData remote = new RefuelItemData();
        remote.setRawJson("{\"Id\":555,\"FlightCode\":\"VN123\",\"ServerRevision\":4}");
        RefuelSyncGuard.applyRemote(row, remote, false);
        repo.insertRefuel(row);

        assertExtrasIntact("REMOTE_PULL khi row dirty");
    }

    /** Đọc lại từ Room rồi lưu lại — mô phỏng khởi động lại app. */
    @Test
    public void restartReadThenSavePreservesUnknownKeys() {
        seedRowWithServerExtras();

        DataRepository afterRestart = DataRepository.forTesting(db);
        DataHelper.installTestDependencies(afterRestart, new OfflineHttpClient());

        RefuelItemData snapshot = afterRestart.getRefuel(UID).toRefuelItemData();
        snapshot.setRealAmount(123);
        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(snapshot, false)));

        assertExtrasIntact("sau khởi động lại");
    }

    /** Row mới chưa có jsonData cũ: không có gì để giữ, không được lỗi. */
    @Test
    public void brandNewRowWithoutPreviousJsonIsFine() {
        RefuelItemData data = new RefuelItemData();
        data.setUniqueId("brand-new");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.NONE);
        data.setRealAmount(50);

        assertTrue(RefuelItemData.isCommitted(DataHelper.postRefuel(data, false)));
        assertEquals(50d, repo.getRefuel("brand-new").toRefuelItemData().getRealAmount(), 0d);
    }

    // ---------------------------------------------------------------
    // Ngữ nghĩa vắng mặt: "người dùng xoá" vs "bản này không nói gì"
    // ---------------------------------------------------------------

    /** Model ĐẦY ĐỦ: trường model vắng mặt nghĩa là người dùng đã xoá ⇒ xoá thật. */
    @Test
    public void completeModelAbsenceMeansUserCleared() {
        String previous = "{\"ReceiptNumber\":\"HD-1\",\"TechLog\":\"abc\"}";
        String model = "{\"RealAmount\":399.0}";

        com.google.gson.JsonObject merged = com.google.gson.JsonParser.parseString(
                        RefuelSyncGuard.mergePreservingUnknown(previous, model,
                                RefuelSyncGuard.ModelPayloadSource.COMPLETE_MODEL))
                .getAsJsonObject();

        assertFalse(merged.has("ReceiptNumber"));
        assertEquals("abc", merged.get("TechLog").getAsString());
    }

    /**
     * Bản MỘT PHẦN: trường vắng mặt là "không nói gì" ⇒ giữ nguyên.
     *
     * <p>Gson bỏ null nên riêng "vắng mặt" không đủ để kết luận người dùng đã xoá. Hiểu nhầm
     * projection thiếu trường thành "đã xoá" chính là kiểu mất dữ liệu đang đi chữa.
     */
    @Test
    public void partialPayloadAbsenceMeansNoOpinion() {
        String previous = "{\"ReceiptNumber\":\"HD-1\",\"TechLog\":\"abc\"}";
        String projection = "{\"RealAmount\":399.0}";

        com.google.gson.JsonObject merged = com.google.gson.JsonParser.parseString(
                        RefuelSyncGuard.mergePreservingUnknown(previous, projection,
                                RefuelSyncGuard.ModelPayloadSource.PARTIAL))
                .getAsJsonObject();

        assertEquals("HD-1", merged.get("ReceiptNumber").getAsString());
        assertEquals(399.0, merged.get("RealAmount").getAsDouble(), 0d);
        assertEquals("abc", merged.get("TechLog").getAsString());
    }

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
