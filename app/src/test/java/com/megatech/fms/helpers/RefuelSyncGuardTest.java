package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RefuelSyncGuardTest {

    @Test
    public void preservesModifiedLocalWhenJsonChangedDuringRequest() {
        assertTrue(RefuelSyncGuard.shouldPreserveLocal("before", "new edit", true));
    }

    @Test
    public void appliesServerResponseWhenLocalDidNotChange() {
        assertFalse(RefuelSyncGuard.shouldPreserveLocal("same", "same", true));
    }

    @Test
    public void doesNotPreserveUnmodifiedServerRefresh() {
        assertFalse(RefuelSyncGuard.shouldPreserveLocal("before", "remote refresh", false));
    }

    @Test
    public void detectsNewLocalRowCreatedDuringRequest() {
        assertTrue(RefuelSyncGuard.shouldPreserveLocal(null, "new local row", true));
    }

    /**
     * The incident case: a brand new row whose JSON still carries Id=0. If the merge
     * only wrote the entity column, the next payload built by toRefuelItemData()
     * would post Id=0 again and the server would create a duplicate refuel.
     */
    @Test
    public void mergeWritesServerIdIntoJsonPayload() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        assertEquals(2087328, local.getId());
        assertEquals(Integer.valueOf(2087328), local.toRefuelItemData().getId());
    }

    @Test
    public void mergeKeepsLocalPayloadAndAdoptsServerVersion() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        RefuelItemData merged = local.toRefuelItemData();
        assertEquals(1110d, merged.getRealAmount(), 0d);
        assertEquals(60166240d, merged.getEndNumber(), 0d);
        assertEquals(REFUEL_ITEM_STATUS.DONE, merged.getStatus());
        assertEquals(6, merged.getServerRevision());
        assertEquals(6, local.getServerRevision());
    }

    @Test
    public void mergeKeepsRowQueuedForTheNextSync() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        assertTrue(local.isLocalModified());
        assertTrue(local.toRefuelItemData().isLocalModified());
    }

    @Test
    public void mergeKeepsLocalUniqueIdWhenServerReturnsNull() {
        RefuelItem local = localRow(0, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, null, 862, 6));

        assertEquals("uid-local", local.getUniqueId());
        assertEquals("uid-local", local.toRefuelItemData().getUniqueId());
    }

    @Test
    public void mergeKeepsLocalIdWhenServerReturnsZero() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(0, "uid-local", 862, 6));

        assertEquals(2087328, local.getId());
        assertEquals(Integer.valueOf(2087328), local.toRefuelItemData().getId());
    }

    /**
     * A POST the server accepted: the business payload just sent is what the server
     * applied, so a response missing FlightId/SortOrder must not wipe it locally.
     */
    @Test
    public void ackKeepsBusinessPayloadWhenResponseDropsFields() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelItemData response = serverResponse(2087328, "uid-local", 1110, 7);
        response.setFlightId(0);
        response.setSortOrder(0);

        RefuelSyncGuard.applyServerAck(local, response);

        RefuelItemData merged = local.toRefuelItemData();
        assertEquals(1265515, merged.getFlightId());
        assertEquals(1000, merged.getSortOrder());
        assertEquals(1265515, local.getFlightId());
        assertEquals(7, merged.getServerRevision());
    }

    @Test
    public void ackMarksRowSynchronized() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        RefuelSyncGuard.applyServerAck(local, serverResponse(2087328, "uid-local", 1110, 7));

        assertFalse(local.isLocalModified());
        assertFalse(local.toRefuelItemData().isLocalModified());
    }

    @Test
    public void versionsNeverGoBackwardsWhenServerReturnsDefaults() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        local.setClientSeq(4);

        RefuelItemData response = serverResponse(2087328, "uid-local", 1110, 0);
        response.setClientSeq(0);

        RefuelSyncGuard.applyServerAck(local, response);

        assertEquals(4, local.getClientSeq());
        assertEquals(4, local.toRefuelItemData().getClientSeq());
        assertEquals(5, local.getServerRevision());
    }

    /**
     * Entity columns are authoritative: a payload rebuilt from a stale jsonData must
     * not post an old ClientSeq/ServerRevision back to the server.
     */
    @Test
    public void payloadTakesVersionFromEntityColumns() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        local.setClientSeq(9);
        local.setServerRevision(7);

        RefuelItemData payload = local.toRefuelItemData();

        assertEquals(9, payload.getClientSeq());
        assertEquals(7, payload.getServerRevision());
    }

    /**
     * Thay cho {@code restoreMissingLinkage} cũ: luật "0 không xoá được liên kết đang có"
     * nay nằm ngay trong tầng trộn nên áp cho MỌI đường nhận dữ liệu server, không riêng
     * một nhánh.
     */
    @Test
    public void zeroLinkValueFromRemoteNeverErasesExistingLink() {
        String local = "{\"FlightId\":1265515,\"FlightCode\":\"VU684\",\"SortOrder\":1000}";
        String remote = "{\"FlightId\":0,\"SortOrder\":0}";

        RefuelItemData data = fromJson(RefuelSyncGuard.mergeByOwnership(local, remote, true));

        assertEquals(1265515, (int) data.getFlightId());
        assertEquals("VU684", data.getFlightCode());
        assertEquals(1000, data.getSortOrder());
    }

    /** Nhưng server đổi sang chuyến KHÁC thì vẫn phải thắng. */
    @Test
    public void remoteFlightChangeStillWins() {
        String local = "{\"FlightId\":1265515,\"FlightCode\":\"VU684\"}";
        String remote = "{\"FlightId\":999999,\"FlightCode\":\"VN123\"}";

        RefuelItemData data = fromJson(RefuelSyncGuard.mergeByOwnership(local, remote, false));

        assertEquals(999999, (int) data.getFlightId());
        assertEquals("VN123", data.getFlightCode());
    }

    // =========================================================================
    // ACK heuristic — sự cố 1110 bị 862 ghi đè
    // =========================================================================

    /**
     * Ca đánh sập luật cũ {@code response.ClientSeq >= request.ClientSeq}: app cũ gửi
     * seq=0, response từ chối cũng mang seq=0 nên {@code >=} vẫn đúng và một lời từ chối
     * bị hiểu nhầm thành xác nhận.
     */
    @Test
    public void doneRequestAnsweredWithOlderDoneRecordIsNotAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 0, 5);
        RefuelItemData response = payload(862, 60165992, REFUEL_ITEM_STATUS.DONE, 0, 5);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /**
     * Luật cũ đòi {@code ServerRevision} phải tăng thì mới coi là đã ghi. Sai: server có
     * những đường ghi hợp lệ không tăng revision, nên mọi POST đều bị xếp "chưa xác nhận",
     * row đọng ERROR và rơi khỏi hàng đợi đồng bộ.
     *
     * <p>Luật mới lấy bằng chứng ở kết quả: response mang đúng giá trị chốt ta gửi nghĩa là
     * trạng thái trên server đã bằng thứ ta muốn.
     */
    @Test
    public void sameFinalValuesWithoutRevisionBumpIsAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);

        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /** Cờ tường minh của server là nguồn chuẩn, không cần suy đoán. */
    @Test
    public void explicitAppliedFlagWins() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);

        // Giá trị chốt lệch nhau, nhưng server khẳng định ĐÃ ghi ⇒ tin server.
        RefuelItemData appliedResponse = payload(862, 60165992, REFUEL_ITEM_STATUS.DONE, 9, 5);
        appliedResponse.setApplied(true);
        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, appliedResponse));

        // Giá trị chốt khớp nhau, nhưng server khẳng định KHÔNG ghi ⇒ vẫn là conflict.
        RefuelItemData rejected = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);
        rejected.setApplied(false);
        rejected.setRejectReason("DONE_STATUS_REGRESSION");
        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, rejected));
        assertTrue(RefuelSyncGuard.describeAck(request, rejected).contains("DONE_STATUS_REGRESSION"));
    }

    /** Server chưa hỗ trợ cờ: {@code null} phải rơi về suy đoán, tuyệt đối không coi là từ chối. */
    @Test
    public void missingAppliedFlagFallsBackToValueComparison() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);

        assertNull(response.getApplied());
        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    @Test
    public void statusRegressionRejectionIsNotAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.PROCESSING, 9, 5);
        RefuelItemData response = payload(862, 60165992, REFUEL_ITEM_STATUS.DONE, 9, 6);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /**
     * Web sửa trong lúc POST đang bay, nhưng KHÔNG đụng số liệu chốt: server vẫn đang giữ
     * đúng các giá trị ta gửi nên gói của ta đã vào. Phần web sửa (chuyến bay, bãi đỗ) thuộc
     * nhóm server sở hữu và sẽ tới ở lượt pull kế tiếp.
     */
    @Test
    public void concurrentWebEditKeepingOurFinalValuesIsAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 12, 8);

        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /** Cùng ca trên nhưng số liệu chốt đã khác ⇒ gói của ta KHÔNG phải thứ server đang giữ. */
    @Test
    public void concurrentWebEditChangingFinalValuesIsNotAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60165992, REFUEL_ITEM_STATUS.DONE, 12, 8);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
        assertTrue(RefuelSyncGuard.describeAck(request, response).startsWith("PAYLOAD_MISMATCH"));
    }

    @Test
    public void appliedResponseIsAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);

        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    // =========================================================================
    // Precondition phiên bản
    // =========================================================================

    private static final String FP = "fingerprint-payload-nen";
    private static final String FP_KHAC = "fingerprint-da-doi";

    @Test
    public void clientSeqMovedIsConflict() {
        // Row có một sửa đổi local chưa sync: serverRevision không đổi, chỉ clientSeq đổi.
        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_CLIENT_MOVED,
                RefuelSyncGuard.decideSave(8, 5, FP, 7, 5, FP));
    }

    @Test
    public void exactMatchIsAllowed() {
        assertEquals(RefuelSyncGuard.SaveDecision.ALLOW_EXACT,
                RefuelSyncGuard.decideSave(8, 5, FP, 8, 5, FP));
    }

    /**
     * Sau khi một lượt sync ACK nâng revision, màn hình đang mở PHẢI còn lưu được —
     * payload nền không đổi thì đây chỉ là metadata của server.
     */
    @Test
    public void revisionOnlyAdvanceWithSamePayloadIsRebase() {
        assertEquals(RefuelSyncGuard.SaveDecision.ALLOW_REBASE_SERVER_METADATA,
                RefuelSyncGuard.decideSave(8, 6, FP, 8, 5, FP));
    }

    /** Web sửa payload và tăng revision nhưng không đụng ClientSeq ⇒ không được ghi đè. */
    @Test
    public void revisionAdvanceWithChangedPayloadIsConflict() {
        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_PAYLOAD_CHANGED,
                RefuelSyncGuard.decideSave(8, 6, FP_KHAC, 8, 5, FP));
    }

    @Test
    public void revisionRegressionIsConflict() {
        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_REVISION_REGRESSED,
                RefuelSyncGuard.decideSave(8, 4, FP, 8, 5, FP));
    }

    /** Row đã tồn tại nhưng snapshot không mang baseline ⇒ không kiểm chứng được. */
    @Test
    public void missingFingerprintWithRevisionGapIsConflict() {
        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_BASE_MISSING,
                RefuelSyncGuard.decideSave(8, 6, FP, 8, 5, null));
    }

    @Test
    public void fingerprintChangesOnlyWithBusinessFields() {
        RefuelItemData a = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 8, 5);
        RefuelItemData b = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 99, 42);
        b.setLocalModified(true);
        b.setLocalId(777);

        assertEquals(RefuelSyncGuard.businessFingerprint(a), RefuelSyncGuard.businessFingerprint(b));

        b.setRealAmount(1111);
        assertNotEquals(RefuelSyncGuard.businessFingerprint(a), RefuelSyncGuard.businessFingerprint(b));
    }

    /** Fingerprint là trạng thái đọc: không được lọt vào jsonData hay payload HTTP. */
    @Test
    public void fingerprintIsNotSerialized() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        RefuelItemData snapshot = local.toRefuelItemData();

        assertNotNull(snapshot.getBaseBusinessFingerprint());
        assertFalse(snapshot.toJson().contains("BaseBusinessFingerprint"));
        assertFalse(snapshot.toJson().contains(snapshot.getBaseBusinessFingerprint()));
        assertFalse(RefuelItem.fromRefuelItemData(snapshot).getJsonData()
                .contains("BaseBusinessFingerprint"));
    }

    /**
     * fromJson KHÔNG được tự suy ra baseline: vân tay tính lúc parse là vân tay của payload
     * người dùng đang sửa, không phải của bản nền. Baseline phải đi kèm riêng qua Intent.
     */
    @Test
    public void fromJsonDoesNotInventBaseline() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        RefuelItemData original = local.toRefuelItemData();

        RefuelItemData restored = RefuelItemData.fromJson(original.toJson());

        assertNull(restored.getBaseBusinessFingerprint());
        assertEquals(RefuelItemData.VERSION_UNKNOWN, restored.getBaseClientSeq());
    }

    /** Vân tay của một JSON legacy (thiếu trường) phải ổn định qua nhiều lần đọc. */
    @Test
    public void fingerprintOfLegacyJsonIsStable() {
        String legacyJson = "{\"FlightCode\":\"VN1268\",\"RealAmount\":1110.0,"
                + "\"StartNumber\":60165130.0,\"EndNumber\":60166240.0,\"Status\":3}";

        String first = RefuelSyncGuard.businessFingerprintOfJson(legacyJson);
        String second = RefuelSyncGuard.businessFingerprintOfJson(legacyJson);

        assertNotNull(first);
        assertEquals(first, second);
    }

    /** Khoá đảo thứ tự nhưng cùng nội dung ⇒ cùng vân tay. */
    @Test
    public void fingerprintIgnoresKeyOrderAndNonBusinessKeys() {
        String a = "{\"RealAmount\":1110.0,\"FlightCode\":\"VN1268\",\"ClientSeq\":8,\"ServerRevision\":5}";
        String b = "{\"FlightCode\":\"VN1268\",\"ServerRevision\":42,\"RealAmount\":1110.0,\"ClientSeq\":99}";

        assertEquals(RefuelSyncGuard.businessFingerprintOfJson(a),
                RefuelSyncGuard.businessFingerprintOfJson(b));
    }

    @Test
    public void rowReadStampsBaseVersionOnSnapshot() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        local.setClientSeq(8);
        local.setServerRevision(5);

        RefuelItemData snapshot = local.toRefuelItemData();

        assertTrue(snapshot.hasBaseVersion());
        assertEquals(8, snapshot.getBaseClientSeq());
        assertEquals(5, snapshot.getBaseServerRevision());
    }

    /** Base version là trạng thái đọc, không được lọt vào jsonData hay payload HTTP. */
    @Test
    public void baseVersionIsNotSerialized() {
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);
        local.setClientSeq(8);

        RefuelItemData snapshot = local.toRefuelItemData();

        assertFalse(snapshot.toJson().contains("BaseClientSeq"));
        assertFalse(snapshot.toJson().contains("BaseServerRevision"));
    }

    /**
     * Timeline sự cố: POST 862 đang bay → người dùng confirm 1110 vào Room →
     * response 862 về sau. Bản local phải sống sót và vẫn ở hàng đợi đồng bộ.
     */
    @Test
    public void inFlightResponseDoesNotOverwriteConfirmedLocalValue() {
        String jsonBeforeRequest = "{\"RealAmount\":862.0}";
        RefuelItem local = localRow(2087328, "uid-local", 1110, REFUEL_ITEM_STATUS.DONE);

        assertTrue(RefuelSyncGuard.shouldPreserveLocal(jsonBeforeRequest,
                local.getJsonData(), local.isLocalModified()));

        RefuelSyncGuard.mergeServerMetadata(local, serverResponse(2087328, "uid-local", 862, 6));

        RefuelItemData merged = local.toRefuelItemData();
        assertEquals(1110d, merged.getRealAmount(), 0d);
        assertEquals(60166240d, merged.getEndNumber(), 0d);
        assertTrue(local.isLocalModified());
    }

    /**
     * Server tăng revision nhưng chỉ apply một phần (ví dụ Printed=true khoá nhóm lượng
     * dầu trong khi EndNumber vẫn được ghi): KHÔNG được coi là xác nhận.
     */
    @Test
    public void partiallyAppliedResponseIsNotAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(862, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
        assertTrue(RefuelSyncGuard.describeAck(request, response).startsWith("PAYLOAD_MISMATCH"));
    }

    @Test
    public void ackReasonNamesTheMismatchedField() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(1110, 60165992, REFUEL_ITEM_STATUS.DONE, 9, 6);

        String reason = RefuelSyncGuard.describeAck(request, response);
        assertTrue(reason.contains("end(60166240->60165992)"));
    }

    @Test
    public void finalValueDiffIsNullWhenPayloadsMatch() {
        RefuelItemData a = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData b = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);

        assertNull(RefuelSyncGuard.describeFinalValueDiff(a, b));
    }

    /**
     * Conflict không được nâng base của snapshot: nếu nâng, chính snapshot vừa bị từ chối
     * sẽ vượt precondition ở lần lưu kế tiếp.
     */
    @Test
    public void rejectedSnapshotStaysInConflictOnSecondSave() {
        RefuelItemData staleSnapshot = payload(862, 60165992, REFUEL_ITEM_STATUS.DONE, 7, 5);
        staleSnapshot.setBaseClientSeq(7);
        staleSnapshot.setBaseServerRevision(5);

        long storedSeq = 8;
        int storedRev = 5;

        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_CLIENT_MOVED,
                RefuelSyncGuard.decideSave(storedSeq, storedRev, FP,
                        staleSnapshot.getBaseClientSeq(), staleSnapshot.getBaseServerRevision(), FP));

        // Sau lần từ chối, base của snapshot phải giữ nguyên ⇒ vẫn conflict.
        assertEquals(RefuelSyncGuard.SaveDecision.CONFLICT_CLIENT_MOVED,
                RefuelSyncGuard.decideSave(storedSeq, storedRev, FP,
                        staleSnapshot.getBaseClientSeq(), staleSnapshot.getBaseServerRevision(), FP));
    }

    // =========================================================================
    // ACK theo trạng thái và độ chính xác thời gian
    // =========================================================================

    /** Lệch phần lẻ giây không phải là bằng chứng bị từ chối. */
    @Test
    public void subSecondEndTimeDifferenceIsStillAck() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        request.setEndTime(new Date(1_769_000_000_123L));

        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);
        response.setEndTime(new Date(1_769_000_000_987L));

        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    @Test
    public void wholeSecondEndTimeDifferenceIsNotAckWhenDone() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        request.setEndTime(new Date(1_769_000_000_000L));

        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);
        response.setEndTime(new Date(1_769_000_060_000L));

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /** Mẻ chưa chốt: EndTime chưa phải giá trị cuối nên không đưa vào so sánh. */
    @Test
    public void endTimeIsIgnoredWhileProcessing() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.PROCESSING, 9, 5);
        request.setEndTime(new Date(1_769_000_000_000L));

        RefuelItemData response = payload(1110, 60166240, REFUEL_ITEM_STATUS.PROCESSING, 9, 6);
        response.setEndTime(null);

        assertTrue(RefuelSyncGuard.looksLikeServerAck(request, response));
    }

    /**
     * Phiếu cũ (OriginalGallon = null) khiến POST response trả RealAmount = 0 dù đã ghi:
     * không kết luận được, và không được để nó đẩy hàng loạt phiếu vào ERROR.
     */
    @Test
    public void legacyZeroAmountProjectionIsInconclusiveNotConflict() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(0, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 6);

        String reason = RefuelSyncGuard.describeAck(request, response);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
        assertEquals(RefuelSyncGuard.LEGACY_PROJECTION_UNKNOWN, reason);
        assertTrue(RefuelSyncGuard.isInconclusive(reason));
    }

    /**
     * Ca nguy hiểm nhất: response vừa có Amount=0 (giới hạn projection) VỪA có EndNumber
     * khác — đây chính là hình dạng của sự cố 1110 bị 862 ghi đè. Nếu kiểm tra
     * legacy-projection chạy trước thì conflict thật sẽ bị che bởi nhãn "không kết luận được"
     * và row retry vô hạn mà không ai biết.
     */
    @Test
    public void zeroAmountWithDifferentEndNumberIsConflictNotInconclusive() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(0, 60165992, REFUEL_ITEM_STATUS.DONE, 9, 6);

        String reason = RefuelSyncGuard.describeAck(request, response);

        assertFalse(RefuelSyncGuard.looksLikeServerAck(request, response));
        assertFalse(RefuelSyncGuard.isInconclusive(reason));
        assertTrue(reason.startsWith("PAYLOAD_MISMATCH"));
        assertTrue(reason.contains("end(60166240->60165992)"));
    }

    @Test
    public void zeroAmountWithDifferentStatusIsConflictNotInconclusive() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(0, 60166240, REFUEL_ITEM_STATUS.PAUSED, 9, 6);

        assertFalse(RefuelSyncGuard.isInconclusive(
                RefuelSyncGuard.describeAck(request, response)));
    }

    @Test
    public void realMismatchIsNotTreatedAsInconclusive() {
        RefuelItemData request = payload(1110, 60166240, REFUEL_ITEM_STATUS.DONE, 9, 5);
        RefuelItemData response = payload(862, 60165992, REFUEL_ITEM_STATUS.DONE, 9, 6);

        assertFalse(RefuelSyncGuard.isInconclusive(
                RefuelSyncGuard.describeAck(request, response)));
    }

    // =========================================================================
    // Trộn theo quyền sở hữu trường
    // =========================================================================

    /** Chuyến bay bị điều động lại trong lúc lái xe đang nhập số: chuyến phải đổi, số phải giữ. */
    @Test
    public void serverOwnedFieldsWinWhileClientOwnedValuesAreKept() {
        String local = "{\"FlightId\":1265515,\"FlightCode\":\"VN1268\",\"ParkingLot\":\"A12\","
                + "\"RealAmount\":1110.0,\"EndNumber\":60166240.0,\"ClientSeq\":9,\"ServerRevision\":5}";
        String remote = "{\"FlightId\":1265999,\"FlightCode\":\"VN1270\",\"ParkingLot\":\"B03\","
                + "\"RealAmount\":0.0,\"EndNumber\":0.0,\"ClientSeq\":7,\"ServerRevision\":5}";

        String merged = RefuelSyncGuard.mergeByOwnership(local, remote, false);
        RefuelItemData data = fromJson(merged);

        assertEquals(1265999, (int) data.getFlightId());
        assertEquals("VN1270", data.getFlightCode());
        assertEquals("B03", data.getParkingLot());
        assertEquals(1110.0, data.getRealAmount(), 0d);
        assertEquals(60166240.0, data.getEndNumber(), 0d);
        // Phiên bản không bao giờ lùi, kể cả khi bản server mang số nhỏ hơn.
        assertEquals(9, data.getClientSeq());
    }

    /**
     * Projection còn thiếu trường: khoá VẮNG MẶT nghĩa là "server không nói gì", không phải
     * "hãy xoá". Đây chính là cơ chế đã làm phiếu rơi khỏi chuyến bay khi FlightId về 0.
     */
    @Test
    public void keysAbsentFromRemoteNeverEraseLocalValues() {
        String local = "{\"FlightId\":1265515,\"FlightCode\":\"VN1268\",\"SortOrder\":3,"
                + "\"RealAmount\":1110.0}";
        String remote = "{\"ParkingLot\":\"B03\"}";

        RefuelItemData data = fromJson(RefuelSyncGuard.mergeByOwnership(local, remote, true));

        assertEquals(1265515, (int) data.getFlightId());
        assertEquals("VN1268", data.getFlightCode());
        assertEquals(3, data.getSortOrder());
        assertEquals(1110.0, data.getRealAmount(), 0d);
        assertEquals("B03", data.getParkingLot());
    }

    /** Ngược lại: khoá CÓ MẶT mang null là lệnh xoá thật, phải tôn trọng. */
    @Test
    public void explicitNullFromRemoteClearsServerOwnedField() {
        String local = "{\"ParkingLot\":\"A12\",\"RealAmount\":1110.0}";
        String remote = "{\"ParkingLot\":null}";

        RefuelItemData data = fromJson(RefuelSyncGuard.mergeByOwnership(local, remote, false));

        assertNull(data.getParkingLot());
        assertEquals(1110.0, data.getRealAmount(), 0d);
    }

    /** Huỷ chuyến là trường server sở hữu ⇒ phải tới nơi kể cả khi row còn thay đổi chưa gửi. */
    @Test
    public void flightCancellationReachesDirtyRow() {
        String local = "{\"IsDeleted\":false,\"RealAmount\":1110.0,\"ClientSeq\":9}";
        String remote = "{\"IsDeleted\":true,\"RealAmount\":0.0,\"ClientSeq\":0}";

        RefuelItemData data = fromJson(RefuelSyncGuard.mergeByOwnership(local, remote, false));

        assertTrue(data.isDeleted());
        assertEquals(1110.0, data.getRealAmount(), 0d);
    }

    /** Cờ của phiên POST không bao giờ được lưu xuống Room. */
    @Test
    public void postFlagsAreStrippedFromMergedJson() {
        String local = "{\"RealAmount\":1110.0}";
        String remote = "{\"Applied\":true,\"RejectReason\":null,\"ParkingLot\":\"B03\"}";

        String merged = RefuelSyncGuard.mergeByOwnership(local, remote, true);

        assertFalse(merged.contains("Applied"));
        assertFalse(merged.contains("RejectReason"));
    }

    private static RefuelItemData fromJson(String json) {
        return new com.google.gson.GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create()
                .fromJson(json, RefuelItemData.class);
    }

    private static RefuelItemData payload(double realAmount, double endNumber,
                                          REFUEL_ITEM_STATUS status,
                                          long clientSeq, int serverRevision) {
        RefuelItemData data = new RefuelItemData();
        data.setUniqueId("uid-local");
        // flightUniqueId mặc định là UUID ngẫu nhiên cho MỖI instance — phải cố định
        // thì hai payload dựng độc lập mới so sánh được.
        data.setFlightUniqueId("5bf31c6d-522a-427f-b89b-b73128aa572b");
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(status);
        data.setRealAmount(realAmount);
        data.setStartNumber(60165130);
        data.setEndNumber(endNumber);
        data.setClientSeq(clientSeq);
        data.setServerRevision(serverRevision);
        data.setBaseClientSeq(clientSeq);
        data.setBaseServerRevision(serverRevision);
        return data;
    }

    private static RefuelItem localRow(int id, String uniqueId, double realAmount,
                                       REFUEL_ITEM_STATUS status) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uniqueId);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(status);
        data.setRealAmount(realAmount);
        data.setStartNumber(60165130);
        data.setEndNumber(60166240);
        data.setEndTime(new Date(1_769_000_000_000L));
        data.setServerRevision(5);
        data.setFlightId(1265515);
        data.setFlightCode("VU684");
        data.setSortOrder(1000);

        RefuelItem item = RefuelItem.fromRefuelItemData(data);
        item.setLocalModified(true);
        return item;
    }

    private static RefuelItemData serverResponse(int id, String uniqueId, double realAmount,
                                                 int serverRevision) {
        RefuelItemData data = new RefuelItemData();
        data.setId(id);
        data.setUniqueId(uniqueId);
        data.setRefuelItemType(RefuelItemData.REFUEL_ITEM_TYPE.REFUEL);
        data.setStatus(REFUEL_ITEM_STATUS.PROCESSING);
        data.setRealAmount(realAmount);
        data.setStartNumber(60165130);
        data.setEndNumber(60165992);
        data.setServerRevision(serverRevision);
        return data;
    }
}
