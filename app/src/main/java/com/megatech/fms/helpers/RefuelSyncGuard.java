package com.megatech.fms.helpers;

import com.megatech.fms.data.entity.RefuelItem;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import java.util.Objects;

/** Pure decisions used by Refuel synchronization; kept Android-free for unit tests. */
public final class RefuelSyncGuard {
    private RefuelSyncGuard() {
    }

    /**
     * Lần lưu này có mang thay đổi nghiệp vụ thật, hay chỉ là gửi lại nguyên trạng?
     *
     * <p>Phân biệt USER CHANGE với NO-OP/RETRY: chỉ USER CHANGE mới được cấp
     * {@code ClientSeq} mới, được xoá verification backoff và được phép vượt backoff.
     * Gửi lại cùng payload phải giữ nguyên sequence — tạo sequence mới chỉ để thử lại
     * sẽ thổi phồng version và làm hỏng chính cơ chế chống stale.
     *
     * <p>So sánh trên payload ĐÃ CHUẨN HOÁ: mọi trường phiên bản/cờ runtime bị loại,
     * vì đổi version hay đổi cờ local không phải là người dùng sửa dữ liệu.
     */
    public static boolean hasBusinessPayloadChanged(RefuelItemData stored, RefuelItemData incoming) {
        if (stored == null || incoming == null) return true;
        return !canonicalBusinessJson(stored.toJson()).equals(canonicalBusinessJson(incoming.toJson()));
    }

    /**
     * Vân tay của payload nghiệp vụ, dùng làm baseline cho precondition khi lưu.
     *
     * <p>SHA-256 trên canonical JSON — không dùng {@code String.hashCode()} vì 32 bit và
     * đụng độ dễ tạo được.
     */
    public static String businessFingerprint(RefuelItemData data) {
        return data == null ? null : businessFingerprintOfJson(data.toJson());
    }

    /**
     * Vân tay tính TRỰC TIẾP từ chuỗi JSON — dạng dùng cho dữ liệu đã lưu.
     *
     * <p>Quan trọng: không đi qua bước parse thành model. Model có nhiều giá trị mặc định
     * không xác định (`new Date()` cho startTime/endTime/refuelTime...), nên JSON cũ thiếu
     * trường sẽ cho object khác nhau ở mỗi lần đọc và vân tay mất ổn định — cùng một row
     * bị nhận nhầm thành CONFLICT_PAYLOAD_CHANGED. Băm thẳng JSON thì chỉ những khoá THỰC SỰ
     * có mặt mới tham gia, nên kết quả ổn định tuyệt đối với mọi dữ liệu legacy.
     */
    public static String businessFingerprintOfJson(String json) {
        if (json == null || json.isEmpty()) return null;
        return sha256(canonicalBusinessJson(json));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            // SHA-256 là thuật toán bắt buộc của mọi JVM/Android runtime.
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    /** Các khoá không đại diện cho dữ liệu nghiệp vụ, bị loại trước khi băm/so sánh. */
    private static final String[] NON_BUSINESS_KEYS = {
            "Id", "UniqueId", "LocalId", "ClientSeq", "ServerRevision",
            "IsLocalModified", "LocalModified", "PostStatus", "DateUpdated", "Others",
            "Applied", "RejectReason"};

    /**
     * Các trường do SERVER sở hữu: kế hoạch bay và thông tin chuyến. App chỉ hiển thị,
     * không bao giờ là nguồn chuẩn của chúng.
     *
     * <p>Bản server luôn thắng ở nhóm này — kể cả khi row đang có thay đổi local chưa gửi.
     * Đây là điểm mấu chốt của phương án: điều động lại chuyến, đổi bãi đỗ hay huỷ chuyến
     * phải tới được thiết bị ngay, trong khi số liệu đồng hồ mà lái xe vừa nhập vẫn nguyên vẹn.
     */
    private static final String[] SERVER_OWNED_KEYS = {
            "FlightId", "FlightUniqueId", "FlightCode", "FlightStatus",
            "ParkingLot", "RouteName", "ArrivalTime", "DepartureTime", "RefuelTime",
            "AircraftCode", "AircraftType", "AirlineId", "AirlineModel", "AirportId",
            "IsInternational", "IsDeleted", "SortOrder", "EstimateAmount", "DateUpdated"};

    /**
     * Trộn bản server vào bản local theo quyền sở hữu trường.
     *
     * <p>Nền là JSON local; chỉ những khoá server THỰC SỰ gửi mới được phủ lên. Nhờ vậy một
     * projection còn thiếu trường không thể xoá dữ liệu: khoá vắng mặt là "không nói gì",
     * khác hẳn khoá có mặt mang giá trị null nghĩa là "đã xoá". Phân biệt này chỉ tồn tại ở
     * chuỗi thô — đi qua model thì trường vắng mặt biến thành 0/null và ghi đè mất bản local.
     *
     * @param adoptClientOwned có nhận cả nhóm trường client sở hữu (số đồng hồ, trạng thái,
     *                         cờ đã in...) hay không. False khi row còn thay đổi chưa gửi.
     * @return JSON đã trộn, hoặc {@code localJson} nếu không có gì để trộn.
     */
    public static String mergeByOwnership(String localJson, String remoteJson,
                                          boolean adoptClientOwned) {
        if (remoteJson == null || remoteJson.isEmpty()) return localJson;
        if (localJson == null || localJson.isEmpty()) return remoteJson;

        com.google.gson.JsonElement localParsed = com.google.gson.JsonParser.parseString(localJson);
        com.google.gson.JsonElement remoteParsed = com.google.gson.JsonParser.parseString(remoteJson);
        if (!localParsed.isJsonObject() || !remoteParsed.isJsonObject()) return localJson;

        com.google.gson.JsonObject merged = localParsed.getAsJsonObject().deepCopy();
        com.google.gson.JsonObject remote = remoteParsed.getAsJsonObject();

        if (adoptClientOwned) {
            // Không còn thay đổi local nào để bảo vệ: mọi khoá server gửi đều được nhận.
            for (String key : remote.keySet())
                overlay(merged, remote, key);
        } else {
            for (String key : SERVER_OWNED_KEYS)
                overlay(merged, remote, key);
        }

        // Nhóm định danh/phiên bản không theo quyền sở hữu mà theo luật riêng: định danh đã có
        // thì không được xoá, phiên bản thì không được lùi.
        mergeIdentity(merged, localParsed.getAsJsonObject(), remote);

        // Cờ runtime của phiên POST không bao giờ được lưu xuống Room.
        merged.remove("Applied");
        merged.remove("RejectReason");

        return merged.toString();
    }

    /**
     * Các khoá LIÊN KẾT: giá trị 0 ở bản server không bao giờ được phép xoá một liên kết
     * đang có. Response/projection thiếu trường trả về 0 là lỗi đã gặp thật — nó biến phiếu
     * thành không thuộc chuyến bay nào. Muốn gỡ liên kết thì server gửi null, không gửi 0.
     */
    private static final java.util.Set<String> LINK_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("FlightId", "AirlineId", "AirportId", "SortOrder"));

    private static void overlay(com.google.gson.JsonObject merged,
                                com.google.gson.JsonObject remote, String key) {
        if (!remote.has(key)) return;

        if (LINK_KEYS.contains(key)
                && longOf(remote, key) == 0
                && longOf(merged, key) != 0)
            return;

        merged.add(key, remote.get(key));
    }

    private static void mergeIdentity(com.google.gson.JsonObject merged,
                                      com.google.gson.JsonObject local,
                                      com.google.gson.JsonObject remote) {
        int mergedId = Math.max(intOf(local, "Id"), intOf(remote, "Id"));
        if (mergedId > 0) merged.addProperty("Id", mergedId);
        else merged.remove("Id");

        // LocalId thuần tuý là khoá của Room, server không biết gì về nó.
        if (local.has("LocalId")) merged.add("LocalId", local.get("LocalId"));
        else merged.remove("LocalId");

        String localUid = stringOf(local, "UniqueId");
        if (isBlank(stringOf(remote, "UniqueId")) && !isBlank(localUid))
            merged.addProperty("UniqueId", localUid);

        merged.addProperty("ClientSeq",
                Math.max(longOf(local, "ClientSeq"), longOf(remote, "ClientSeq")));
        merged.addProperty("ServerRevision",
                Math.max(intOf(local, "ServerRevision"), intOf(remote, "ServerRevision")));
    }

    private static int intOf(com.google.gson.JsonObject obj, String key) {
        return (int) longOf(obj, key);
    }

    private static long longOf(com.google.gson.JsonObject obj, String key) {
        try {
            com.google.gson.JsonElement el = obj.get(key);
            return el == null || el.isJsonNull() ? 0 : el.getAsLong();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String stringOf(com.google.gson.JsonObject obj, String key) {
        try {
            com.google.gson.JsonElement el = obj.get(key);
            return el == null || el.isJsonNull() ? null : el.getAsString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Cùng cấu hình với Gson của {@code BaseModel} — khoá JSON phải khớp tuyệt đối. */
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE)
            .create();

    /**
     * Áp bản server lên một row đã có, theo quyền sở hữu trường.
     *
     * <p>Ghi CẢ jsonData lẫn các cột của entity. {@link RefuelItem#toRefuelItemData()} dựng lại
     * model từ jsonData nên chỉ ghi cột là chưa đủ; ngược lại chỉ ghi json thì các truy vấn
     * theo cột (lọc theo chuyến, theo giờ) vẫn thấy dữ liệu cũ.
     *
     * <p>KHÔNG đụng vào {@code isLocalModified} và {@code postStatus}: trạng thái hàng đợi
     * đồng bộ do phía gửi quyết định, không phải phía nhận.
     *
     * @return model sau khi trộn, hoặc null nếu không trộn được.
     */
    public static RefuelItemData applyRemote(RefuelItem localItem, RefuelItemData remote,
                                             boolean adoptClientOwned) {
        if (localItem == null || remote == null) return null;

        String remoteJson = isBlank(remote.getRawJson()) ? remote.toJson() : remote.getRawJson();
        String mergedJson = mergeByOwnership(localItem.getJsonData(), remoteJson, adoptClientOwned);

        RefuelItemData merged;
        try {
            merged = GSON.fromJson(mergedJson, RefuelItemData.class);
        } catch (RuntimeException ex) {
            return null;
        }
        if (merged == null) return null;

        merged.setLocalId(localItem.getLocalId());
        merged.setLocalModified(localItem.isLocalModified());

        localItem.setJsonData(mergedJson);
        if (merged.getId() != null && merged.getId() > 0)
            localItem.setId(merged.getId());
        if (!isBlank(merged.getUniqueId()))
            localItem.setUniqueId(merged.getUniqueId());
        if (!isBlank(merged.getTruckNo()))
            localItem.setTruckNo(merged.getTruckNo());
        if (merged.getTruckId() != 0)
            localItem.setTruckId(merged.getTruckId());
        localItem.setFlightId(merged.getFlightId());
        if (merged.getRefuelTime() != null)
            localItem.setRefuelTime(merged.getRefuelTime());
        if (merged.getStatus() != null)
            localItem.setStatus(RefuelItem.REFUEL_ITEM_STATUS.getStatus(merged.getStatus().getValue()));
        if (merged.getRefuelItemType() != null)
            localItem.setRefuelItemType(
                    RefuelItem.REFUEL_ITEM_TYPE.getValue(merged.getRefuelItemType().ordinal()));
        if (merged.getDateUpdated() != null)
            localItem.setDateUpdated(merged.getDateUpdated());
        localItem.setClientSeq(merged.getClientSeq());
        localItem.setServerRevision(merged.getServerRevision());

        return merged;
    }

    /**
     * Những trường server sở hữu mà bản đến làm thay đổi — dùng cho log, để lần sau còn
     * nhìn ra "phiếu bị đổi chuyến lúc mấy giờ" thay vì phải đoán.
     *
     * @return mô tả ngắn gọn, hoặc null nếu không có gì đổi.
     */
    public static String describeServerOwnedDiff(RefuelItemData before, RefuelItemData after) {
        if (before == null || after == null) return null;

        StringBuilder diff = new StringBuilder();
        if (before.getFlightId() != after.getFlightId())
            diff.append(String.format(java.util.Locale.US, "flightId(%d->%d) ",
                    before.getFlightId(), after.getFlightId()));
        if (!Objects.equals(before.getFlightCode(), after.getFlightCode()))
            diff.append(String.format("flightCode(%s->%s) ",
                    before.getFlightCode(), after.getFlightCode()));
        if (!Objects.equals(before.getParkingLot(), after.getParkingLot()))
            diff.append(String.format("parking(%s->%s) ",
                    before.getParkingLot(), after.getParkingLot()));
        if (before.getFlightStatus() != after.getFlightStatus())
            diff.append(String.format("flightStatus(%s->%s) ",
                    before.getFlightStatus(), after.getFlightStatus()));
        if (before.isDeleted() != after.isDeleted())
            diff.append(String.format("deleted(%s->%s) ",
                    before.isDeleted(), after.isDeleted()));
        if (truncateToSecond(before.getRefuelTime()) != truncateToSecond(after.getRefuelTime()))
            diff.append(String.format("refuelTime(%s->%s) ",
                    before.getRefuelTime(), after.getRefuelTime()));

        return diff.length() == 0 ? null : diff.toString().trim();
    }

    /**
     * Canonical JSON: bỏ các khoá không phải nghiệp vụ và sắp xếp khoá theo thứ tự ổn định.
     */
    private static String canonicalBusinessJson(String json) {
        com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return json;

        com.google.gson.JsonObject source = parsed.getAsJsonObject();
        for (String key : NON_BUSINESS_KEYS) source.remove(key);

        return canonicalize(source).toString();
    }

    private static com.google.gson.JsonElement canonicalize(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) {
            com.google.gson.JsonObject sorted = new com.google.gson.JsonObject();
            java.util.List<String> keys = new java.util.ArrayList<>(
                    element.getAsJsonObject().keySet());
            java.util.Collections.sort(keys);
            for (String key : keys)
                sorted.add(key, canonicalize(element.getAsJsonObject().get(key)));
            return sorted;
        }
        if (element.isJsonArray()) {
            com.google.gson.JsonArray sorted = new com.google.gson.JsonArray();
            for (com.google.gson.JsonElement child : element.getAsJsonArray())
                sorted.add(canonicalize(child));
            return sorted;
        }
        return element;
    }

    /** Kết quả kiểm tra precondition khi lưu một snapshot lên row đang có. */
    public enum SaveDecision {
        /** Row y nguyên như lúc màn hình đọc ra. */
        ALLOW_EXACT,
        /** Server chỉ cấp thêm metadata (revision tăng), payload nền không đổi ⇒ rebase. */
        ALLOW_REBASE_SERVER_METADATA,
        /** Đã có lần ghi local khác sau khi snapshot được đọc. */
        CONFLICT_CLIENT_MOVED,
        /** Payload nền đã bị đổi (Web/GET) ⇒ không được ghi đè. */
        CONFLICT_PAYLOAD_CHANGED,
        /** Revision của row lùi so với baseline — dữ liệu không nhất quán. */
        CONFLICT_REVISION_REGRESSED,
        /** Snapshot của một row đã tồn tại nhưng không mang baseline ⇒ không thể kiểm chứng. */
        CONFLICT_BASE_MISSING;

        public boolean isAllowed() {
            return this == ALLOW_EXACT || this == ALLOW_REBASE_SERVER_METADATA;
        }
    }

    /**
     * Quyết định cho phép lưu hay không, dựa trên phiên bản VÀ vân tay payload nền.
     *
     * <p>Chỉ so version là không đủ theo cả hai chiều:
     * <ul>
     *   <li>Chặn theo cặp (seq, revision) làm mọi màn hình mất quyền lưu ngay sau khi
     *       một lượt sync ACK nâng revision — mất dữ liệu người dùng, im lặng.</li>
     *   <li>Bỏ qua lệch revision khi seq trùng lại cho phép snapshot cũ ghi đè dữ liệu
     *       Web vừa sửa (Web đổi payload, tăng revision, không đụng ClientSeq).</li>
     * </ul>
     *
     * @param storedFingerprint vân tay payload hiện tại của row
     * @param baseFingerprint   vân tay payload lúc snapshot được đọc; null nếu không có
     */
    public static SaveDecision decideSave(long storedClientSeq, int storedServerRevision,
                                          String storedFingerprint,
                                          long baseClientSeq, int baseServerRevision,
                                          String baseFingerprint) {

        // Row đã tồn tại mà snapshot không mang baseline ⇒ không có gì để kiểm chứng.
        // Không hạ guard để chiều caller cũ: caller phải được sửa để mang baseline.
        if (baseFingerprint == null)
            return SaveDecision.CONFLICT_BASE_MISSING;

        if (storedClientSeq != baseClientSeq)
            return SaveDecision.CONFLICT_CLIENT_MOVED;

        if (storedServerRevision < baseServerRevision)
            return SaveDecision.CONFLICT_REVISION_REGRESSED;

        if (!baseFingerprint.equals(storedFingerprint))
            return SaveDecision.CONFLICT_PAYLOAD_CHANGED;

        if (storedServerRevision == baseServerRevision)
            return SaveDecision.ALLOW_EXACT;

        // Revision tăng nhưng payload nền không đổi ⇒ server chỉ cấp thêm metadata.
        return SaveDecision.ALLOW_REBASE_SERVER_METADATA;
    }

    /**
     * Server đã ghi gói vừa POST hay chưa.
     *
     * <p>Ưu tiên cờ tường minh {@code Applied} khi server hỗ trợ. Với server chưa có cờ đó,
     * bằng chứng là ĐỐI CHIẾU GIÁ TRỊ CHỐT: response mang đúng các giá trị ta gửi nghĩa là
     * trạng thái trên server đã bằng thứ ta muốn — bất kể đường nào dẫn tới đó.
     *
     * <p>Trước đây chỗ này còn đòi {@code ServerRevision} tăng. Đó là một điều kiện SAI:
     * server có những đường ghi hợp lệ không tăng revision, nên mọi POST đều bị xếp vào
     * "chưa xác nhận", row đọng lại {@code postStatus = ERROR} và bị loại khỏi hàng đợi
     * đồng bộ. Revision nay chỉ còn là tín hiệu phụ để ghi log.
     */
    public static boolean looksLikeServerAck(RefuelItemData request, RefuelItemData response) {
        return describeAck(request, response) == null;
    }

    /**
     * @return null nếu response coi như đã xác nhận; ngược lại là lý do KHÔNG xác nhận,
     * dùng cho log conflict.
     */
    public static String describeAck(RefuelItemData request, RefuelItemData response) {
        if (request == null || response == null) return "NO_RESPONSE";

        // Cờ tường minh của server là nguồn chuẩn, không cần suy đoán gì thêm.
        if (response.getApplied() != null) {
            if (response.getApplied()) return null;
            return isBlank(response.getRejectReason())
                    ? "SERVER_REJECTED" : "SERVER_REJECTED " + response.getRejectReason();
        }

        // Server giữ DONE trong khi gói gửi lên không DONE ⇒ đây là từ chối hạ trạng thái.
        if (response.getStatus() == REFUEL_ITEM_STATUS.DONE
                && request.getStatus() != REFUEL_ITEM_STATUS.DONE)
            return "STATUS_REGRESSION_REJECT";

        // Revision tăng KHÔNG có nghĩa là payload của ta được ghi: server có thể apply
        // một phần (ví dụ Printed=true thì nhóm lượng dầu bị khoá nhưng EndNumber vẫn
        // được cập nhật). Phải đối chiếu chính các trường chốt của mẻ.
        String diff = describeFinalValueDiff(request, response, request.getStatus());
        if (diff == null)
            return null;

        // Giới hạn đã biết của server: với phiếu cũ (OriginalGallon = null) projection của
        // POST response trả RealAmount = 0 dù dữ liệu ĐÃ được ghi.
        //
        // Kiểm tra này phải nằm SAU khi đã đối chiếu các trường còn lại: nếu đặt trước, một
        // response vừa có Amount=0 vừa có EndNumber khác (đúng ca 1110 bị 862 ghi đè) sẽ bị
        // gắn nhãn "không kết luận được" và conflict thật bị che mất.
        if (Double.compare(response.getRealAmount(), 0d) == 0
                && request.getRealAmount() > 0
                && describeFinalValueDiff(request, response, request.getStatus(), true) == null)
            return LEGACY_PROJECTION_UNKNOWN;

        return "PAYLOAD_MISMATCH " + diff;
    }

    /** Server đã ghi hay chưa thì không kết luận được — không phải conflict thật. */
    public static final String LEGACY_PROJECTION_UNKNOWN = "LEGACY_PROJECTION_UNKNOWN";

    public static boolean isInconclusive(String ackReason) {
        return LEGACY_PROJECTION_UNKNOWN.equals(ackReason);
    }

    /**
     * @return null nếu các trường chốt của mẻ khớp nhau, ngược lại là mô tả chênh lệch.
     */
    public static String describeFinalValueDiff(RefuelItemData expected, RefuelItemData actual) {
        return describeFinalValueDiff(expected, actual,
                expected == null ? null : expected.getStatus());
    }

    /**
     * @param requestStatus trạng thái của gói gửi lên. Chỉ mẻ đã DONE mới đối chiếu
     *                      {@code EndTime}: khi còn PROCESSING, giờ kết thúc chưa phải giá
     *                      trị chốt nên lệch là bình thường, không phải dấu hiệu bị từ chối.
     */
    public static String describeFinalValueDiff(RefuelItemData expected, RefuelItemData actual,
                                                REFUEL_ITEM_STATUS requestStatus) {
        return describeFinalValueDiff(expected, actual, requestStatus, false);
    }

    /**
     * @param ignoreAmount bỏ {@code RealAmount} khỏi so sánh — chỉ dùng để nhận diện
     *                     trường hợp KHÁC BIỆT DUY NHẤT nằm ở Amount.
     */
    public static String describeFinalValueDiff(RefuelItemData expected, RefuelItemData actual,
                                                REFUEL_ITEM_STATUS requestStatus,
                                                boolean ignoreAmount) {
        if (expected == null || actual == null) return "null-operand";

        StringBuilder diff = new StringBuilder();
        if (expected.getStatus() != actual.getStatus())
            diff.append(String.format(java.util.Locale.US, "status(%s->%s) ",
                    expected.getStatus(), actual.getStatus()));
        if (!ignoreAmount
                && Double.compare(expected.getRealAmount(), actual.getRealAmount()) != 0)
            diff.append(String.format(java.util.Locale.US, "amount(%.0f->%.0f) ",
                    expected.getRealAmount(), actual.getRealAmount()));
        if (Double.compare(expected.getStartNumber(), actual.getStartNumber()) != 0)
            diff.append(String.format(java.util.Locale.US, "start(%.0f->%.0f) ",
                    expected.getStartNumber(), actual.getStartNumber()));
        if (Double.compare(expected.getEndNumber(), actual.getEndNumber()) != 0)
            diff.append(String.format(java.util.Locale.US, "end(%.0f->%.0f) ",
                    expected.getEndNumber(), actual.getEndNumber()));
        if (requestStatus == REFUEL_ITEM_STATUS.DONE
                && truncateToSecond(expected.getEndTime()) != truncateToSecond(actual.getEndTime()))
            diff.append(String.format(java.util.Locale.US, "endTime(%s->%s) ",
                    expected.getEndTime(), actual.getEndTime()));

        return diff.length() == 0 ? null : diff.toString().trim();
    }

    /**
     * Server lưu datetime theo giây (và có thể trả kèm phần lẻ giây tuỳ kiểu cột), nên so
     * bằng millisecond sẽ báo lệch giả cho mọi phiếu.
     */
    private static long truncateToSecond(java.util.Date value) {
        return value == null ? Long.MIN_VALUE : value.getTime() / 1000L;
    }

    public static boolean shouldPreserveLocal(String jsonBeforeRequest,
                                              String newestLocalJson,
                                              boolean newestLocalModified) {
        return newestLocalModified && !Objects.equals(jsonBeforeRequest, newestLocalJson);
    }

    /**
     * Adopt the identity/version fields the server just confirmed while keeping the
     * refuel payload the user edited while the request was in flight.
     *
     * <p>{@link RefuelItem#toRefuelItemData()} rebuilds the model from {@code jsonData}
     * and only overrides localId/localModified/uniqueId, so writing the server values
     * to the entity columns alone would leave the next POST payload carrying the old
     * {@code Id} — for a brand new row that means the server creates a duplicate
     * refuel. Both the columns and the JSON must be updated together.
     *
     * <p>The row stays {@code localModified} so the next sync pushes the newer local
     * payload.
     */
    public static void mergeServerMetadata(RefuelItem localItem, RefuelItemData serverData) {
        mergeServerMetadata(localItem, serverData, true);
    }

    /**
     * Ghi nhận một POST đã được server chấp nhận: payload nghiệp vụ vừa gửi chính là
     * thứ server đã áp dụng, nên giữ nguyên payload local và chỉ nhận metadata server
     * xác nhận. Nhờ vậy một response thiếu trường (FlightId, SortOrder...) không thể
     * xoá dữ liệu nghiệp vụ của bản ghi local.
     */
    public static void applyServerAck(RefuelItem localItem, RefuelItemData serverData) {
        mergeServerMetadata(localItem, serverData, false);
    }

    /**
     * @param keepLocalModified true khi bản ghi local còn thay đổi chưa gửi (phải sync tiếp),
     *                          false khi server vừa xác nhận đúng payload đang có ở local.
     */
    private static void mergeServerMetadata(RefuelItem localItem, RefuelItemData serverData,
                                            boolean keepLocalModified) {
        if (localItem == null || serverData == null) return;

        // An abnormal response must never erase an identity the row already has.
        int mergedId = serverData.getId() != null && serverData.getId() > 0
                ? serverData.getId() : localItem.getId();
        String mergedUniqueId = isBlank(serverData.getUniqueId())
                ? localItem.getUniqueId() : serverData.getUniqueId();

        RefuelItemData preserved = localItem.toRefuelItemData();

        // Version chỉ được tăng: response trả về giá trị mặc định 0 không được phép
        // kéo lùi seq/revision đang có ở local.
        long mergedClientSeq = Math.max(localItem.getClientSeq(),
                Math.max(preserved.getClientSeq(), serverData.getClientSeq()));
        int mergedRevision = Math.max(localItem.getServerRevision(),
                Math.max(preserved.getServerRevision(), serverData.getServerRevision()));

        preserved.setId(mergedId);
        if (!isBlank(mergedUniqueId))
            preserved.setUniqueId(mergedUniqueId);
        preserved.setClientSeq(mergedClientSeq);
        preserved.setServerRevision(mergedRevision);
        if (serverData.getDateUpdated() != null)
            preserved.setDateUpdated(serverData.getDateUpdated());

        localItem.setId(mergedId);
        if (!isBlank(mergedUniqueId))
            localItem.setUniqueId(mergedUniqueId);
        localItem.setClientSeq(mergedClientSeq);
        localItem.setServerRevision(mergedRevision);
        if (serverData.getDateUpdated() != null)
            localItem.setDateUpdated(serverData.getDateUpdated());
        localItem.setJsonData(preserved.toJson());
        localItem.setLocalModified(keepLocalModified);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
