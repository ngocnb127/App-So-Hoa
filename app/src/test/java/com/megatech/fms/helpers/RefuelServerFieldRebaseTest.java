package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * Sự cố ngày 17-08-2026 trên xe: sau khi kết thúc mẻ, số liệu đồng hồ, nhiệt độ và tỉ trọng
 * nhập ở màn hình xác nhận không được ghi, phiếu hiện về 0 GL.
 *
 * <pre>
 * POST_EXCHANGE  BACKGROUND_SYNC ... seq=2 ... res rev=3 | result=ACK
 * SYNC           REMOTE_PULL SERVER_FIELDS flightStatus(ASSIGNED-&gt;REFUELING)
 * REFUEL_ANOMALY event=VERSION_CONFLICT reason=CONFLICT_PAYLOAD_CHANGED source=LOCAL_SAVE
 *                storedSeq=2 storedRev=3 baseSeq=2 baseRev=1
 *                storedAmount=0 storedEnd=0 incomingAmount=399 incomingEnd=817856
 * </pre>
 *
 * <p>Lượt pull nền chạy 30 giây một lần và đổi {@code FlightStatus} ngay khi mẻ bắt đầu.
 * Vân tay payload nền khi đó tính cả nhóm trường SERVER sở hữu, nên thay đổi bình thường
 * của server bị hiểu thành xung đột với người dùng và mọi lần lưu sau đó bị bỏ đi.
 */
public class RefuelServerFieldRebaseTest {

    private static RefuelItem row() {
        RefuelItemData data = new RefuelItemData();
        data.setId(2111601);
        data.setUniqueId("be8b7194-9337-478b-8f18-00461db11d5f");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setFlightId(1265515);
        data.setFlightCode("VN 1237-01");
        data.setFlightStatus(RefuelItemData.FLIGHT_STATUS.ASSIGNED);
        data.setParkingLot("A1");
        data.setStartNumber(817751);
        data.setEndNumber(817751);
        data.setEndTime(new Date(1_787_000_000_000L));
        data.setClientSeq(2);
        data.setServerRevision(1);

        RefuelItem item = RefuelItem.fromRefuelItemData(data);
        item.setLocalModified(true);
        return item;
    }

    private static RefuelSyncGuard.SaveDecision decide(RefuelItem stored, RefuelItemData snapshot) {
        return RefuelSyncGuard.decideSave(
                stored.getClientSeq(), stored.getServerRevision(),
                RefuelSyncGuard.businessFingerprintOfJson(stored.getJsonData()),
                snapshot.getBaseClientSeq(), snapshot.getBaseServerRevision(),
                snapshot.getBaseBusinessFingerprint());
    }

    /** Bản pull đổi trạng thái chuyến, đúng như log của thiết bị. */
    private static void pullFlightStatusRefueling(RefuelItem stored) {
        RefuelItemData remote = new RefuelItemData();
        remote.setRawJson("{\"Id\":2111601,\"FlightId\":1265515,\"FlightCode\":\"VN 1237-01\","
                + "\"FlightStatus\":2,\"ParkingLot\":\"A1\",\"ServerRevision\":3}");
        RefuelSyncGuard.applyRemote(stored, remote, false);
    }

    /**
     * Trọng tâm: thay đổi ở trường server sở hữu KHÔNG được biến thành xung đột — người
     * dùng vẫn phải lưu được số liệu mẻ của mình.
     */
    @Test
    public void serverOwnedChangeDoesNotBlockUserSave() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();   // màn hình mở, baseRev = 1

        pullFlightStatusRefueling(stored);                     // pull nền, storedRev = 3

        assertEquals(RefuelSyncGuard.SaveDecision.ALLOW_REBASE_SERVER_METADATA,
                decide(stored, snapshot));
    }

    /** Người dùng sửa số liệu mẻ trên snapshot cũ vẫn lưu được sau lượt pull. */
    @Test
    public void userMeterDataStillSavableAfterPull() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();

        snapshot.setRealAmount(399);
        snapshot.setStartNumber(817457);
        snapshot.setEndNumber(817856);
        snapshot.setStatus(REFUEL_ITEM_STATUS.DONE);

        pullFlightStatusRefueling(stored);

        assertEquals(RefuelSyncGuard.SaveDecision.ALLOW_REBASE_SERVER_METADATA,
                decide(stored, snapshot));
    }

    /** Nhưng WEB sửa đúng số liệu của mẻ thì vẫn phải là xung đột thật. */
    @Test
    public void clientOwnedChangeFromWebIsStillConflict() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();

        RefuelItemData remote = stored.toRefuelItemData();
        remote.setEndNumber(999999);
        remote.setServerRevision(3);
        remote.setRawJson(remote.toJson());
        RefuelSyncGuard.applyRemote(stored, remote, true);

        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_PAYLOAD_CHANGED,
                decide(stored, snapshot));
    }

    /** Snapshot cũ không được đẩy trạng thái chuyến lùi về giá trị lúc mở màn hình. */
    @Test
    public void snapshotAdoptsServerFieldsInsteadOfOverwritingThem() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();
        snapshot.setRealAmount(399);

        pullFlightStatusRefueling(stored);

        String diff = RefuelSyncGuard.adoptServerOwned(snapshot, stored.getJsonData(),
                snapshot.getBaseJson()).diff;

        assertNotNull(diff);
        assertEquals(RefuelItemData.FLIGHT_STATUS.REFUELING, snapshot.getFlightStatus());
        assertEquals(399d, snapshot.getRealAmount(), 0d);   // dữ liệu người dùng còn nguyên
    }

    @Test
    public void adoptIsNoOpWhenServerFieldsUnchanged() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();

        assertNull(RefuelSyncGuard.adoptServerOwned(snapshot, stored.getJsonData(),
                snapshot.getBaseJson()).diff);
    }

    /** Liên kết chuyến bay không bị xoá bởi một bản pull thiếu trường. */
    @Test
    public void adoptNeverErasesFlightLink() {
        RefuelItem stored = row();
        RefuelItemData snapshot = stored.toRefuelItemData();

        RefuelItemData remote = new RefuelItemData();
        remote.setRawJson("{\"Id\":2111601,\"FlightId\":0,\"ServerRevision\":3}");
        RefuelSyncGuard.applyRemote(stored, remote, false);

        RefuelSyncGuard.adoptServerOwned(snapshot, stored.getJsonData(),
                snapshot.getBaseJson());

        assertEquals(1265515, (int) snapshot.getFlightId());
    }

    // =========================================================================
    // Biên của dung sai khi đối chiếu ACK
    // =========================================================================

    private static RefuelItemData done(double temperature, double density) {
        RefuelItemData data = new RefuelItemData();
        data.setUniqueId("uid-1");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);
        data.setRealAmount(399);
        data.setStartNumber(817457);
        data.setEndNumber(817856);
        data.setEndTime(new Date(1_787_000_900_000L));
        data.setManualTemperature(temperature);
        data.setDensity(density);
        return data;
    }

    /** Chênh ĐÚNG BẰNG dung sai vẫn là khớp — đó là làm tròn của backend. */
    @Test
    public void differenceExactlyAtToleranceIsStillAck() {
        assertNull(RefuelSyncGuard.describeFinalValueDiff(
                done(30.00, 0.7890), done(30.01, 0.7891), REFUEL_ITEM_STATUS.DONE));
    }

    /** Vượt dung sai thì phải báo lệch. */
    @Test
    public void differenceBeyondToleranceIsReported() {
        assertNotNull(RefuelSyncGuard.describeFinalValueDiff(
                done(30.00, 0.7890), done(30.02, 0.7890), REFUEL_ITEM_STATUS.DONE));
        assertNotNull(RefuelSyncGuard.describeFinalValueDiff(
                done(30.00, 0.7890), done(30.00, 0.7892), REFUEL_ITEM_STATUS.DONE));
    }

    /** Phiếu chưa nhập nhiệt độ/tỉ trọng thì không có gì để đối chiếu, không được bắt bẻ. */
    @Test
    public void emptyTemperatureAndDensityAreNotCompared() {
        assertNull(RefuelSyncGuard.describeFinalValueDiff(
                done(0, 0), done(31.5, 0.8), REFUEL_ITEM_STATUS.DONE));
    }

    // =========================================================================
    // Đo được trên máy thật 17-08 14:34 — bản vá đợt 1 vẫn conflict
    // =========================================================================

    /**
     * Log thiết bị:
     * <pre>
     * VERSION_CONFLICT CONFLICT_PAYLOAD_CHANGED storedSeq=1 storedRev=2 baseSeq=1 baseRev=1
     *   clientDiff=[... ManualTemperature(0->30.0) ... Status(0->"0") TechLog Weight]
     *   serverDiff=[AirlineModel AirportId FlightStatus(1->"1")]
     * </pre>
     * Hai nguyên nhân, đều KHÔNG phải người dùng sửa: kiểu JSON của enum khác nhau
     * (số vs chuỗi), và hàng chục khoá server-only mà model app không có.
     */
    @Test
    public void enumTypeMismatchIsNotAConflict() {
        String stored = "{\"Status\":0,\"RealAmount\":0.0,\"EndNumber\":0.0}";
        String appWritten = "{\"Status\":\"0\",\"RealAmount\":0.0,\"EndNumber\":0.0}";

        assertEquals(RefuelSyncGuard.businessFingerprintOfJson(stored),
                RefuelSyncGuard.businessFingerprintOfJson(appWritten));
    }

    /** Khoá server-only mà model không có không được tính là người dùng sửa dữ liệu. */
    @Test
    public void serverOnlyKeysDoNotAffectFingerprint() {
        String stored = "{\"Status\":\"1\",\"RealAmount\":399.0,"
                + "\"AirlineType\":2,\"TechLog\":\"x\",\"Weight\":100,"
                + "\"BondingCable\":true,\"Invoice\":{\"Id\":9},\"InvoiceForm\":null}";
        String appWritten = "{\"Status\":\"1\",\"RealAmount\":399.0}";

        assertEquals(RefuelSyncGuard.businessFingerprintOfJson(stored),
                RefuelSyncGuard.businessFingerprintOfJson(appWritten));
    }

    /** 0 và 0.0 cùng một giá trị. */
    @Test
    public void numericFormattingDoesNotAffectFingerprint() {
        assertEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"RealAmount\":0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"RealAmount\":0.0}"));
    }

    /** Nhưng số liệu mẻ đổi thật thì vân tay PHẢI đổi. */
    @Test
    public void realMeterChangeStillChangesFingerprint() {
        assertNotEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"EndNumber\":818150.0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"EndNumber\":818151.0}"));
        assertNotEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"ManualTemperature\":0.0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"ManualTemperature\":30.0}"));
    }

    // =========================================================================
    // Ownership suy từ model, chuẩn hoá theo từng khoá, giữ khoá lạ
    // =========================================================================

    /** Ownership phải đọc từ model, không phải danh sách chép tay hay thiếu. */
    @Test
    public void modelKeysCoverQuantitativeFieldsThatAHandListWouldMiss() {
        java.util.Set<String> keys = RefuelSyncGuard.modelKeys();
        for (String key : new String[]{"Price", "TaxRate", "Unit", "ProductId", "ProductName",
                "ProjectedCapacity", "ActualCapacity", "RefuelItemType", "Currency", "Exported"})
            assertTrue("model phải có " + key, keys.contains(key));
    }

    /**
     * Trường ít dùng nhưng app CÓ ghi phải được phân loại, không rơi vào mặc định.
     *
     * <p>Giá/thuế/đơn vị/sản phẩm là SHARED: server là danh mục gốc nhưng app cũng ghi
     * (setPrice 18 chỗ, setProductName 13, setTaxRate 10). Chúng không vào vân tay client —
     * xung đột của chúng được bắt bằng trộn ba chiều, xem sharedFieldChangedByBothSidesIsConflict.
     */
    @Test
    public void rarelyUsedButAppWrittenFieldsAreClassifiedShared() {
        for (String key : new String[]{"Price", "TaxRate", "Unit", "Currency",
                "ProductId", "ProductName"})
            assertEquals(key + " phải là SHARED",
                    RefuelFieldOwnership.Ownership.SHARED, RefuelFieldOwnership.of(key));
    }

    /** Số liệu mẻ do client sở hữu thì vẫn phải vào vân tay. */
    @Test
    public void clientOwnedChangeStillChangesFingerprint() {
        assertNotEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"Density\":0.0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"Density\":0.789}"));
        assertNotEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"DriverId\":0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"DriverId\":7}"));
    }

    /**
     * Số chứng từ là ĐỊNH DANH: "001" khác "1". Chuẩn hoá số tất tay sẽ làm hai chứng từ
     * khác nhau băm ra như nhau và che mất đúng loại xung đột cần bắt.
     */
    @Test
    public void documentNumbersAreComparedAsIdentifiersNotNumbers() {
        for (String key : new String[]{"ReceiptNumber", "InvoiceNumber", "TicketNumber",
                "SaleNumber", "ReturnInvoiceNumber", "FormNo", "QualityNo"})
            assertNotEquals(key + " phải so như định danh",
                    RefuelSyncGuard.businessFingerprintOfJson("{\"" + key + "\":\"001\"}"),
                    RefuelSyncGuard.businessFingerprintOfJson("{\"" + key + "\":\"1\"}"));
    }

    /** Nhưng enum và số liệu định lượng thì vẫn so theo giá trị. */
    @Test
    public void quantitativeKeysStillNormalizeAcrossJsonTypes() {
        assertEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"Status\":0}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"Status\":\"0\"}"));
        assertEquals(RefuelSyncGuard.businessFingerprintOfJson("{\"RealAmount\":399}"),
                RefuelSyncGuard.businessFingerprintOfJson("{\"RealAmount\":399.0}"));
    }

    /** Ghi payload của model KHÔNG được xoá khoá server ngoài model. */
    @Test
    public void writingModelPayloadKeepsUnknownServerKeys() {
        String previous = "{\"RealAmount\":0.0,\"TechLog\":\"abc\",\"Weight\":100,"
                + "\"Invoice\":{\"Id\":9},\"BondingCable\":true}";
        String model = "{\"RealAmount\":399.0}";

        String merged = RefuelSyncGuard.mergePreservingUnknown(previous, model);
        com.google.gson.JsonObject obj =
                com.google.gson.JsonParser.parseString(merged).getAsJsonObject();

        assertEquals(399.0, obj.get("RealAmount").getAsDouble(), 0d);
        assertEquals("abc", obj.get("TechLog").getAsString());
        assertEquals(100, obj.get("Weight").getAsInt());
        assertTrue(obj.has("Invoice"));
        assertTrue(obj.get("BondingCable").getAsBoolean());
    }

    /** Nhưng người dùng XOÁ một trường của model thì phải xoá thật, không được giữ bản cũ. */
    @Test
    public void clearingAModelFieldIsNotResurrectedByTheMerge() {
        String previous = "{\"ReceiptNumber\":\"HD-1\",\"TechLog\":\"abc\"}";
        String model = "{\"RealAmount\":399.0}";

        com.google.gson.JsonObject obj = com.google.gson.JsonParser
                .parseString(RefuelSyncGuard.mergePreservingUnknown(previous, model))
                .getAsJsonObject();

        assertFalse("trường model đã bị xoá thì không được sống lại",
                obj.has("ReceiptNumber"));
        assertEquals("abc", obj.get("TechLog").getAsString());
    }

    /**
     * Chốt chặn cho phép suy ownership bằng reflection: mọi khoá Gson THỰC SỰ sinh ra phải
     * nằm trong {@code modelKeys()}. Nếu quy ước đặt tên của Gson đổi, hoặc có trường dùng
     * {@code @SerializedName} lạ, test này đổ ngay thay vì để guard âm thầm bỏ sót.
     */
    @Test
    public void modelKeysMatchWhatGsonActuallyWrites() {
        RefuelItemData data = new RefuelItemData();
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.DONE);

        com.google.gson.JsonObject written =
                com.google.gson.JsonParser.parseString(data.toJson()).getAsJsonObject();

        for (String key : written.keySet())
            assertTrue("modelKeys() thiếu khoá Gson sinh ra: " + key,
                    RefuelSyncGuard.modelKeys().contains(key));
    }

    /**
     * Đo trên máy thật 17-08 15:10, mẻ 843 GL: dòng VERSION_CONFLICT duy nhất còn lại.
     *
     * <p>Server gửi {@code "ReceiptCount":null} và {@code "WaterSensor":null}. Đường ACK cũ
     * dựng lại model rồi serialize, biến null thành {@code 0.0}, làm vân tay của row đổi và
     * màn hình xác nhận đang mở mất quyền lưu. ACK chỉ được cập nhật metadata, tuyệt đối
     * không đi vòng qua giá trị mặc định của model.
     */
    @Test
    public void serverAckDoesNotDisturbFingerprintOnNullValuedKeys() {
        String stored = "{\"Status\":\"3\",\"RealAmount\":843.0,\"StartNumber\":818224.0,"
                + "\"EndNumber\":819067.0,\"Density\":0.789,\"QualityNo\":\"H\","
                + "\"ReceiptCount\":null,\"WaterSensor\":null,"
                + "\"TechLog\":0,\"Weight\":2518,\"AirportId\":118,"
                + "\"Id\":2111786,\"ClientSeq\":48,\"ServerRevision\":4}";

        RefuelItem row = new RefuelItem();
        row.setJsonData(stored);
        row.setUniqueId("uid-843");
        row.setStatus(RefuelItem.REFUEL_ITEM_STATUS.DONE);
        row.setClientSeq(48);
        row.setServerRevision(4);

        String before = RefuelSyncGuard.businessFingerprintOfJson(row.getJsonData());

        RefuelItemData ack = new RefuelItemData();
        ack.setId(2111786);
        ack.setUniqueId("uid-843");
        ack.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        ack.setStatus(REFUEL_ITEM_STATUS.DONE);
        ack.setServerRevision(5);
        RefuelSyncGuard.applyServerAck(row, ack);

        assertEquals("ACK không được làm đổi vân tay của row",
                before, RefuelSyncGuard.businessFingerprintOfJson(row.getJsonData()));
        assertEquals("nhưng vẫn phải nhận revision mới", 5, row.getServerRevision());

        com.google.gson.JsonObject after = com.google.gson.JsonParser
                .parseString(row.getJsonData()).getAsJsonObject();
        assertTrue("null phải giữ nguyên là null", after.get("ReceiptCount").isJsonNull());
        assertTrue(after.get("WaterSensor").isJsonNull());
        assertEquals("khoá lạ phải còn", 2518, after.get("Weight").getAsInt());
        assertEquals(843.0, after.get("RealAmount").getAsDouble(), 0d);
    }

    /**
     * Đo trên xe thật 17-08: mỗi lượt pull (30 giây/lần) ghi lại ~45 phiếu chỉ vì lượt
     * trộn chép đè vô điều kiện, kể cả khoá không đổi. Row bị viết lại ⇒ vân tay tính lại
     * ⇒ nền của màn hình đang mở thành cũ ⇒ mọi lần lưu sau đó CONFLICT.
     */
    @Test
    public void pullDoesNotRewriteKeysThatDidNotChange() {
        String local = "{\"RealAmount\":2121.0,\"EndTime\":\"2026-08-17T20:37:47\","
                + "\"StartTime\":\"2026-08-17T20:16:16\",\"FlightStatus\":1}";
        // Server trả cùng giá trị, chỉ khác cách định dạng phần lẻ giây.
        String remote = "{\"RealAmount\":2121.0,\"EndTime\":\"2026-08-17T20:37:47.4120000\","
                + "\"StartTime\":\"2026-08-17T20:16:16.9000000\",\"FlightStatus\":1}";

        String merged = RefuelSyncGuard.mergeByOwnership(local, remote, true);
        com.google.gson.JsonObject after =
                com.google.gson.JsonParser.parseString(merged).getAsJsonObject();

        // Giữ nguyên chuỗi giờ của máy, không nhận bản định dạng khác của server.
        assertEquals("2026-08-17T20:37:47", after.get("EndTime").getAsString());
        assertEquals("2026-08-17T20:16:16", after.get("StartTime").getAsString());
        // Điều quan trọng nhất: vân tay KHÔNG đổi, nên nền của màn hình đang mở còn hiệu lực.
        assertEquals(RefuelSyncGuard.businessFingerprintOfJson(local),
                RefuelSyncGuard.businessFingerprintOfJson(merged));
    }

    /** Nhưng thay đổi THẬT từ server vẫn phải tới được thiết bị. */
    @Test
    public void pullStillDeliversRealServerChanges() {
        String local = "{\"RealAmount\":1110.0,\"FlightStatus\":1}";
        String remote = "{\"RealAmount\":1200.0,\"FlightStatus\":3}";

        com.google.gson.JsonObject merged = com.google.gson.JsonParser
                .parseString(RefuelSyncGuard.mergeByOwnership(local, remote, true))
                .getAsJsonObject();

        assertEquals(1200.0, merged.get("RealAmount").getAsDouble(), 0d);
        assertEquals(3, merged.get("FlightStatus").getAsInt());
    }

    /**
     * Đo trên xe thật 17-08 23:13: server trả EndTime/StartTime bằng ĐÚNG thời điểm của
     * lượt pull, giống hệt nhau cho hàng chục phiếu chưa tra nạp. Nhận vào là ghi lại cả
     * danh sách ở mỗi lượt và làm hỏng nền của màn hình đang mở.
     */
    @Test
    public void pullDoesNotTakeServerClockAsDeviceMeasuredTimes() {
        String local = "{\"EndTime\":\"2026-08-17T23:10:27\","
                + "\"StartTime\":\"2026-08-17T23:10:27\",\"FlightStatus\":1}";
        String remote = "{\"EndTime\":\"2026-08-17T23:13:42.5072233\","
                + "\"StartTime\":\"2026-08-17T23:13:42.5072233\",\"FlightStatus\":3}";

        com.google.gson.JsonObject after = com.google.gson.JsonParser
                .parseString(RefuelSyncGuard.mergeByOwnership(local, remote, true))
                .getAsJsonObject();

        assertEquals("2026-08-17T23:10:27", after.get("EndTime").getAsString());
        assertEquals("2026-08-17T23:10:27", after.get("StartTime").getAsString());
        assertEquals("thay đổi thật của server vẫn phải tới", 3, after.get("FlightStatus").getAsInt());
    }


    /**
     * Đo trên máy thật 18-08: 12/15 mẻ đã chốt có SaleNumber = "" dù log ghi rõ đồng hồ đã
     * trả số. Server không có hai trường này nên trả chuỗi rỗng, và nhánh nhận-tất-cả coi
     * "" là giá trị hợp lệ rồi ghi đè lên số thật — phiếu in ra mất số đối chiếu.
     */
    @Test
    public void blankServerValueDoesNotEraseTheMeterIssuedNumbers() {
        String local = "{\"SaleNumber\":\"100758\",\"TicketNumber\":\"100758\"}";
        String remote = "{\"SaleNumber\":\"\",\"TicketNumber\":\"\"}";

        com.google.gson.JsonObject after = com.google.gson.JsonParser
                .parseString(RefuelSyncGuard.mergeByOwnership(local, remote, true))
                .getAsJsonObject();

        assertEquals("100758", after.get("SaleNumber").getAsString());
        assertEquals("100758", after.get("TicketNumber").getAsString());
    }

    /** Không phải "thiết bị luôn thắng": server gửi số THẬT thì vẫn phải nhận. */
    @Test
    public void realServerValueStillReplacesTheMeterNumber() {
        String local = "{\"SaleNumber\":\"100758\"}";
        String remote = "{\"SaleNumber\":\"100999\"}";

        com.google.gson.JsonObject after = com.google.gson.JsonParser
                .parseString(RefuelSyncGuard.mergeByOwnership(local, remote, true))
                .getAsJsonObject();

        assertEquals("100999", after.get("SaleNumber").getAsString());
    }

    /** Máy chưa có số thì server gửi rỗng cũng không sao — không được ném lỗi. */
    @Test
    public void blankOnBothSidesIsHarmless() {
        String after = RefuelSyncGuard.mergeByOwnership(
                "{\"SaleNumber\":\"\"}", "{\"SaleNumber\":\"\"}", true);

        assertEquals("", com.google.gson.JsonParser.parseString(after)
                .getAsJsonObject().get("SaleNumber").getAsString());
    }
}
