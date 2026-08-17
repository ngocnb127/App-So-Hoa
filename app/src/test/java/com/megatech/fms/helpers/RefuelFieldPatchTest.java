package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Date;

/**
 * Patch của màn hình xác nhận: đắp đúng phần người dùng nhập, và DỪNG khi đắp lên sẽ
 * ghi đè dữ liệu của phía khác. Đây là chỗ dễ vô tình mở lại sự cố mẻ 1110 GL bị 862 ghi đè
 * nhất, nên các ca chặn được kiểm kỹ hơn ca thành công.
 */
public class RefuelFieldPatchTest {

    private static RefuelItemData row(double amount, double endNumber, REFUEL_ITEM_STATUS status) {
        RefuelItemData data = new RefuelItemData();
        data.setId(2111601);
        data.setUniqueId("uid-1");
        data.setFlightUniqueId("flight-uid");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(status);
        data.setRealAmount(amount);
        data.setStartNumber(817457);
        data.setEndNumber(endNumber);
        data.setStartTime(new Date(1_787_000_100_000L));
        data.setEndTime(new Date(1_787_000_900_000L));
        data.setRefuelTime(new Date(1_787_000_000_000L));
        data.setParkingLot("A1");
        return data;
    }

    private static String json(RefuelItemData data) {
        return RefuelItem.fromRefuelItemData(data).getJsonData();
    }

    /** Người dùng nhập nhiệt độ/tỉ trọng/QC, row không đổi ⇒ đắp bình thường. */
    @Test
    public void appliesUserFieldsWhenRowUnchanged() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setManualTemperature(30);
        ours.setDensity(0.789);
        ours.setQualityNo("D");

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, base);

        assertTrue(result.isApplied());
        assertEquals(30d, result.getMerged().getManualTemperature(), 0d);
        assertEquals(0.789d, result.getMerged().getDensity(), 0d);
        assertEquals("D", result.getMerged().getQualityNo());
        assertEquals(REFUEL_ITEM_STATUS.DONE, result.getMerged().getStatus());
    }

    /** Row đổi ở trường người dùng KHÔNG đụng ⇒ giữ bản của row, vẫn đắp được. */
    @Test
    public void keepsRowValueOnFieldsUserDidNotTouch() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setManualTemperature(30);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setReceiptNumber("HD-9999");        // web/màn hình khác gán số hoá đơn

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertTrue(result.isApplied());
        assertEquals(30d, result.getMerged().getManualTemperature(), 0d);
        assertEquals("HD-9999", result.getMerged().getReceiptNumber());
    }

    /** CÙNG một trường, hai phía đổi khác nhau ⇒ CHẶN, không có "ours wins". */
    @Test
    public void blocksWhenSameFieldChangedOnBothSides() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setEndNumber(817900);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setEndNumber(818000);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertFalse(result.isApplied());
        assertTrue(result.getConflictKeys().contains("EndNumber"));
    }

    /** Hai phía cùng đổi nhưng ra CÙNG một giá trị ⇒ không phải xung đột. */
    @Test
    public void sameValueOnBothSidesIsNotConflict() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setStatus(REFUEL_ITEM_STATUS.DONE);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertTrue(result.isApplied());
        assertEquals(REFUEL_ITEM_STATUS.DONE, result.getMerged().getStatus());
    }

    /**
     * Ca 1110/862: mẻ đã được chốt DONE bằng bộ số khác, snapshot đang cầm là bản cũ.
     * Phải chặn hẳn, không đắp gì cả.
     */
    @Test
    public void blocksWhenRowAlreadyFinalizedWithDifferentNumbers() {
        String base = json(row(862, 60165992, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setManualTemperature(30);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setStatus(REFUEL_ITEM_STATUS.DONE);
        latest.setRealAmount(1110);
        latest.setEndNumber(60166240);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertFalse(result.isApplied());
        assertTrue(result.isBlockedByFinalizedRow());
        assertEquals("ROW_ALREADY_FINALIZED", result.describe());
    }

    /** Không có baseline thì không kiểm chứng được ⇒ không đắp. */
    @Test
    public void refusesWithoutBaseline() {
        String latest = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));
        RefuelItemData ours = RefuelItemData.fromJson(latest);

        assertFalse(RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, null, ours, latest).isApplied());
    }

    /** Patch không được đụng tới trường ngoài phạm vi màn hình xác nhận. */
    @Test
    public void neverTouchesFieldsOutsideItsScope() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setManualTemperature(30);
        ours.setParkingLot("SAI");                 // ngoài phạm vi, phải bị bỏ qua

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setParkingLot("B7");

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertTrue(result.isApplied());
        assertEquals("B7", result.getMerged().getParkingLot());
    }

    @Test
    public void changedKeysListsWhatUserEdited() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setDensity(0.789);
        ours.setQualityNo("D");

        assertEquals(2, RefuelFieldPatch.changedKeys(RefuelFieldPatch.Scope.CONFIRM, base, ours).size());
        assertTrue(RefuelFieldPatch.changedKeys(RefuelFieldPatch.Scope.CONFIRM, base, ours).contains("Density"));
        assertTrue(RefuelFieldPatch.changedKeys(RefuelFieldPatch.Scope.CONFIRM, base, ours).contains("QualityNo"));
    }

    @Test
    public void describeReportsFieldConflict() {
        String base = json(row(399, 817856, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setDensity(0.789);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setDensity(0.801);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(RefuelFieldPatch.Scope.CONFIRM, base, ours, json(latest));

        assertNull(result.getMerged());
        assertNotNull(result.describe());
        assertTrue(result.describe().startsWith("FIELD_CONFLICT"));
    }

    // =========================================================================
    // Phạm vi END — đường phục hồi của luồng kết thúc mẻ
    // =========================================================================

    /** End đắp được số liệu mẻ lên row đã tiến lên vì lý do khác. */
    @Test
    public void endScopeAppliesMeterDataOntoMovedRow() {
        String base = json(row(0, 817751, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setRealAmount(399);
        ours.setEndNumber(818150);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setReceiptNumber("HD-1234");   // đường ghi khác, ngoài số liệu mẻ

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, json(latest));

        assertTrue(result.isApplied());
        assertEquals(399d, result.getMerged().getRealAmount(), 0d);
        assertEquals(818150d, result.getMerged().getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, result.getMerged().getStatus());
        assertEquals("HD-1234", result.getMerged().getReceiptNumber());
    }

    /** Mẻ đã được chốt bằng bộ số khác ⇒ End phải chặn, không ghi đè. */
    @Test
    public void endScopeRefusesRowFinalizedWithOtherNumbers() {
        String base = json(row(0, 817751, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setRealAmount(399);
        ours.setEndNumber(818150);

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setStatus(REFUEL_ITEM_STATUS.DONE);
        latest.setRealAmount(1110);
        latest.setEndNumber(60166240);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, json(latest));

        assertFalse(result.isApplied());
        assertTrue(result.isBlockedByFinalizedRow());
    }

    /** Hồi lưu: End đắp số nhỏ hơn vẫn phải được, không có luật monotonic. */
    @Test
    public void endScopeAcceptsMeterRollback() {
        String base = json(row(400, 818151, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setRealAmount(399);
        ours.setEndNumber(818150);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, base);

        assertTrue(result.isApplied());
        assertEquals(399d, result.getMerged().getRealAmount(), 0d);
        assertEquals(818150d, result.getMerged().getEndNumber(), 0d);
    }

    /** End không được đụng nhiệt độ tay/tỉ trọng/QC — đó là phạm vi màn hình xác nhận. */
    @Test
    public void endScopeNeverTouchesConfirmOnlyFields() {
        String base = json(row(0, 817751, REFUEL_ITEM_STATUS.PROCESSING));

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setRealAmount(399);
        ours.setManualTemperature(99);   // ngoài phạm vi END
        ours.setDensity(0.111);
        ours.setQualityNo("SAI");

        RefuelItemData latest = RefuelItemData.fromJson(base);
        latest.setManualTemperature(30);
        latest.setDensity(0.789);
        latest.setQualityNo("D");

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, json(latest));

        assertTrue(result.isApplied());
        assertEquals(30d, result.getMerged().getManualTemperature(), 0d);
        assertEquals(0.789d, result.getMerged().getDensity(), 0d);
        assertEquals("D", result.getMerged().getQualityNo());
        assertEquals(399d, result.getMerged().getRealAmount(), 0d);
    }

    // =========================================================================
    // So sánh theo NGHĨA trong patch — cùng comparator với vân tay
    // =========================================================================

    /** Chỉ khác kiểu JSON của enum thì không được thành conflict trong patch. */
    @Test
    public void enumTypeMismatchIsNotPatchConflict() {
        String base = "{\"Status\":1,\"RealAmount\":399.0,\"EndNumber\":818150.0}";
        String latest = "{\"Status\":1,\"RealAmount\":399.0,\"EndNumber\":818150.0}";

        RefuelItemData ours = RefuelItemData.fromJson(
                "{\"Status\":\"1\",\"RealAmount\":399.0,\"EndNumber\":818150.0}");
        ours.setManualTemperature(30);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest);

        assertTrue(result.describe(), result.isApplied());
        assertEquals(30d, result.getMerged().getManualTemperature(), 0d);
    }

    /** 0 và 0.0 ở số liệu định lượng cũng vậy. */
    @Test
    public void numericFormattingIsNotPatchConflict() {
        String base = "{\"RealAmount\":0}";
        String latest = "{\"RealAmount\":0.0}";

        RefuelItemData ours = RefuelItemData.fromJson("{\"RealAmount\":0.0}");
        ours.setQualityNo("D");

        assertTrue(RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest).isApplied());
    }

    /** Nhưng số chứng từ có số 0 đầu là ĐỊNH DANH: "001" khác "1" ⇒ phải là conflict. */
    @Test
    public void identifierWithLeadingZeroIsStillAConflict() {
        String base = "{\"ReceiptNumber\":\"000\"}";
        String latest = "{\"ReceiptNumber\":\"001\"}";

        RefuelItemData ours = RefuelItemData.fromJson("{\"ReceiptNumber\":\"1\"}");

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest);

        assertFalse(result.isApplied());
        assertTrue(result.getConflictKeys().contains("ReceiptNumber"));
    }

    /** Thời gian lệch phần lẻ giây không phải là thay đổi. */
    @Test
    public void subSecondTimeDifferenceIsNotAConflict() {
        String base = "{\"EndTime\":\"2026-08-17T11:17:36\"}";
        String latest = "{\"EndTime\":\"2026-08-17T11:17:36.4370000\"}";

        RefuelItemData ours = RefuelItemData.fromJson("{\"EndTime\":\"2026-08-17T11:17:36\"}");
        ours.setDensity(0.789);

        assertTrue(RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest).isApplied());
    }

    /**
     * Ca PHÂN BIỆT thật sự cho comparator: server đổi giá trị (1 → 2) trong khi app chỉ ghi
     * lại đúng giá trị cũ dưới dạng chuỗi ("1"). So thô thì cả hai phía đều "đã đổi" và
     * ours != latest ⇒ conflict giả. So theo nghĩa thì người dùng không đổi gì ⇒ nhận bản
     * của server.
     */
    @Test
    public void appRewritingSameValueAsStringDoesNotFightServerChange() {
        // Dùng 3 (DONE) chứ không phải 2: enum REFUEL_ITEM_STATUS đang có HAI hằng số cùng
        // @SerializedName("2") — PAUSED và ERROR — nên "2" parse ra ERROR. Lỗi có sẵn của
        // model, ghi lại ở đây để test không dựa vào hành vi nhập nhằng đó.
        String base = "{\"Status\":1,\"RealAmount\":100.0}";
        String latest = "{\"Status\":3,\"RealAmount\":100.0}";

        RefuelItemData ours = RefuelItemData.fromJson(
                "{\"Status\":\"1\",\"RealAmount\":100.0}");
        ours.setDensity(0.789);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest);

        assertTrue(result.describe(), result.isApplied());
        assertEquals("phải nhận trạng thái mới của server",
                REFUEL_ITEM_STATUS.DONE, result.getMerged().getStatus());
    }

    /**
     * Guard "mẻ đã chốt bằng bộ số khác" cũng phải so theo nghĩa: row DONE mà chỉ khác kiểu
     * số (399 vs 399.0) thì KHÔNG được coi là đã chốt bằng bộ số khác rồi chặn oan.
     */
    @Test
    public void finalizedGuardDoesNotTriggerOnNumericFormattingAlone() {
        String base = "{\"Status\":3,\"RealAmount\":399,\"StartNumber\":817457,"
                + "\"EndNumber\":818150,\"EndTime\":\"2026-08-17T11:17:36\"}";
        String latest = "{\"Status\":3,\"RealAmount\":399.0,\"StartNumber\":817457.0,"
                + "\"EndNumber\":818150.0,\"EndTime\":\"2026-08-17T11:17:36.437\"}";

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setManualTemperature(30);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.CONFIRM, base, ours, latest);

        assertFalse(result.describe(), result.isBlockedByFinalizedRow());
        assertTrue(result.describe(), result.isApplied());
        assertEquals(30d, result.getMerged().getManualTemperature(), 0d);
    }

    /** changedKeys cũng phải so theo nghĩa, không kê khống trường "đã sửa". */
    @Test
    public void changedKeysIgnoresPureFormattingDifferences() {
        String base = "{\"Status\":1,\"RealAmount\":399,\"EndTime\":\"2026-08-17T11:17:36\"}";
        RefuelItemData ours = RefuelItemData.fromJson(
                "{\"Status\":\"1\",\"RealAmount\":399.0,\"EndTime\":\"2026-08-17T11:17:36\"}");

        // Chỉ khẳng định BA khoá này không bị kê khống. Không khẳng định danh sách rỗng:
        // model đặt mặc định new Date() cho StartTime/EndTime khi JSON thiếu, nên khoá vắng
        // mặt trong base vẫn hợp lệ xuất hiện ở đây.
        java.util.List<String> changed = RefuelFieldPatch.changedKeys(
                RefuelFieldPatch.Scope.CONFIRM, base, ours);
        assertFalse(changed.toString(), changed.contains("Status"));
        assertFalse(changed.toString(), changed.contains("RealAmount"));
        assertFalse(changed.toString(), changed.contains("EndTime"));
    }

    // =========================================================================
    // Server echo KHÔNG phải là một lần sửa cạnh tranh
    // =========================================================================

    /**
     * Kẹt cứng đo trên máy thật 17-08 15:26: mẻ 837 GL không chốt được, bấm Thử lại bao
     * nhiêu lần cũng {@code FIELD_CONFLICT [OriginalEndMeter, EndTime]}.
     *
     * <p>Server trả {@code EndTime} kèm 7 chữ số lẻ giây và {@code OriginalEndMeter = 0};
     * mỗi lượt pull ghi đè hai trường đó lên row sạch. ClientSeq KHÔNG đổi vì không màn hình
     * nào ghi — đó là dấu hiệu phân biệt "server echo" với "người khác sửa".
     */
    @Test
    public void serverEchoOnClientOwnedFieldDoesNotBlockEnd() {
        String base = "{\"Status\":1,\"RealAmount\":464.0,\"EndNumber\":819495.0,"
                + "\"OriginalEndMeter\":819495.0,\"EndTime\":\"2026-08-17T15:20:00\"}";
        // Lượt pull ghi đè EndTime bằng giờ của server và OriginalEndMeter về 0.
        String latest = "{\"Status\":1,\"RealAmount\":464.0,\"EndNumber\":819495.0,"
                + "\"OriginalEndMeter\":0,\"EndTime\":\"2026-08-17T15:28:24.9676527\"}";

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);
        ours.setRealAmount(837);
        ours.setEndNumber(819593);
        ours.setOriginalEndMeter(819593);
        ours.setEndTime(new Date(1_787_000_900_000L));

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, latest, 27, 27);

        assertTrue(result.describe(), result.isApplied());
        assertEquals(837d, result.getMerged().getRealAmount(), 0d);
        assertEquals(819593d, result.getMerged().getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, result.getMerged().getStatus());
    }

    /** Nhưng nếu MÀN HÌNH KHÁC đã ghi (ClientSeq tăng) thì vẫn phải chặn. */
    @Test
    public void anotherLocalWriterOnSameFieldStillBlocks() {
        String base = "{\"Status\":1,\"EndNumber\":819495.0}";
        String latest = "{\"Status\":1,\"EndNumber\":818000.0}";

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setEndNumber(819593);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, latest, 27, 28);

        assertFalse(result.isApplied());
        assertTrue(result.getConflictKeys().contains("EndNumber"));
    }

    /** Ca 1110/862 vẫn phải chặn: màn hình khác đã chốt mẻ bằng bộ số khác. */
    @Test
    public void finalizedByAnotherWriterStillBlocks() {
        String base = "{\"Status\":1,\"RealAmount\":862.0,\"EndNumber\":60165992.0}";
        String latest = "{\"Status\":3,\"RealAmount\":1110.0,\"EndNumber\":60166240.0}";

        RefuelItemData ours = RefuelItemData.fromJson(base);
        ours.setStatus(REFUEL_ITEM_STATUS.DONE);

        RefuelFieldPatch.Result result = RefuelFieldPatch.apply(
                RefuelFieldPatch.Scope.END, base, ours, latest, 27, 31);

        assertFalse(result.isApplied());
        assertTrue(result.isBlockedByFinalizedRow());
    }
}
