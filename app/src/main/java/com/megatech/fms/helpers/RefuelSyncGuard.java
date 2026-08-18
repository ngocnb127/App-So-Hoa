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
     * Vân tay của phần payload DO CLIENT SỞ HỮU, dùng làm baseline cho precondition khi lưu.
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
     *
     * <p>CHỈ tính trên nhóm trường CLIENT sở hữu. Nhóm server sở hữu bị loại vì lượt pull
     * nền chạy 30 giây một lần và thường xuyên đổi chúng (flightStatus ASSIGNED→REFUELING
     * ngay khi mẻ bắt đầu là ca gặp mỗi lần tra nạp). Nếu tính cả nhóm đó thì mọi màn hình
     * đang mở mất quyền lưu ngay giữa mẻ, và toàn bộ số liệu đồng hồ, nhiệt độ, tỉ trọng
     * người dùng nhập sau đó bị bỏ đi không một lời báo. Thay đổi của server ở nhóm này
     * không phải xung đột với người dùng — nó được RE-BASE vào payload sắp ghi, xem
     * {@link #adoptServerOwned(RefuelItemData, String)}.
     */
    public static String businessFingerprintOfJson(String json) {
        if (json == null || json.isEmpty()) return null;
        return sha256(canonicalClientOwnedJson(json));
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
    /** Mốc giờ do thiết bị đo — server chỉ echo lại giờ của chính nó, không phải dữ liệu. */
    private static final java.util.Set<String> DEVICE_MEASURED_TIME_KEYS =
            new java.util.HashSet<>(java.util.Arrays.asList("StartTime", "EndTime"));

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
            // Không còn thay đổi local nào để bảo vệ: nhận mọi khoá server gửi, TRỪ hai mốc
            // giờ do thiết bị đo.
            //
            // Đo trên xe thật 17-08 23:13: server trả EndTime/StartTime = ĐÚNG THỜI ĐIỂM
            // CỦA LƯỢT PULL ("2026-08-17T23:13:42.5072233"), giống hệt nhau cho hàng chục
            // phiếu chưa hề tra nạp. Nhận vào là ghi lại toàn bộ danh sách ở mỗi lượt, vân
            // tay đổi liên tục, và nền của màn hình đang mở thành cũ.
            //
            // Hai mốc này chỉ có nghĩa khi thiết bị thật sự bơm; giờ của server không phải
            // là dữ liệu, chỉ là dấu vết của lần sinh phản hồi.
            for (String key : remote.keySet()) {
                if (DEVICE_MEASURED_TIME_KEYS.contains(key)) continue;
                overlay(merged, remote, key);
            }
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

        // Không ghi lại khoá KHÔNG đổi. Ghi đè vô điều kiện làm row bị viết lại ở mỗi lượt
        // pull dù nội dung y hệt, kéo theo vân tay tính lại và nền của màn hình đang mở
        // thành cũ. So theo NGHĨA (RefuelValues) chứ không so chuỗi, nên khác cách định
        // dạng cùng một giá trị cũng không sinh ra lần ghi thừa.
        if (RefuelValues.equal(key, merged.get(key), remote.get(key))) return;

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

        // Chiếu XUỐNG CỘT bằng đúng một đường dùng chung. Gán tay từng trường ở đây đã bỏ
        // sót parkingLot, flightCode và toàn bộ số liệu mẻ, nên cột nói một đằng jsonData
        // nói một nẻo — màn hình đọc theo cột sẽ thấy mẻ 0 GL.
        localItem.projectColumnsFrom(merged);

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

    /**
     * Liệt kê những khoá NGHIỆP VỤ khác nhau giữa hai bản JSON.
     *
     * <p>Chỗ này sinh ra vì sự cố 17-08-2026 không tra được nguyên nhân từ log: nhánh chặn
     * chỉ ghi "payload đã đổi" mà không nói đổi ở đâu, nên không phân biệt được thay đổi
     * thật của người dùng với thay đổi do chính lượt đồng bộ nền ghi đè lên jsonData.
     *
     * @param serverOwnedOnly true thì chỉ xét nhóm server sở hữu, false thì chỉ xét phần
     *                        client sở hữu — hai nhóm này có ý nghĩa chẩn đoán khác hẳn nhau
     */
    public static String describeJsonDiff(String jsonA, String jsonB, boolean serverOwnedOnly) {
        if (isBlank(jsonA) || isBlank(jsonB)) return null;

        com.google.gson.JsonObject a;
        com.google.gson.JsonObject b;
        try {
            a = com.google.gson.JsonParser.parseString(jsonA).getAsJsonObject();
            b = com.google.gson.JsonParser.parseString(jsonB).getAsJsonObject();
        } catch (RuntimeException ex) {
            return null;
        }

        java.util.TreeSet<String> keys = new java.util.TreeSet<>(a.keySet());
        keys.addAll(b.keySet());

        StringBuilder diff = new StringBuilder();
        for (String key : keys) {
            if (RefuelFieldOwnership.of(key) == RefuelFieldOwnership.Ownership.LOCAL_ONLY)
                continue;
            if (RefuelFieldOwnership.isServerOwned(key) != serverOwnedOnly) continue;
            if (RefuelValues.equal(key, a.get(key), b.get(key))) continue;

            diff.append(key);
            // Chỉ vài trường số liệu được in kèm GIÁ TRỊ. Phần còn lại chỉ ghi tên khoá:
            // log này chạy mỗi lượt đồng bộ cho mọi phiếu, in hết giá trị vừa phình log
            // vừa rải thông tin chuyến bay/khách hàng ra file gửi lên server.
            if (LOGGABLE_VALUE_KEYS.contains(key))
                diff.append('(').append(a.get(key)).append("->").append(b.get(key)).append(')');
            diff.append(' ');
        }

        return diff.length() == 0 ? null : diff.toString().trim();
    }

    /** Chỉ những khoá thực sự cần con số để chẩn đoán mới được in giá trị ra log. */
    private static final java.util.Set<String> LOGGABLE_VALUE_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("EndTime", "StartTime", "Status", "RealAmount", "StartNumber", "EndNumber",
                    "ManualTemperature", "Density", "FlightStatus", "ClientSeq",
                    "ServerRevision"));

    /**
     * Mọi khoá JSON mà {@link RefuelItemData} biết, suy ra TỪ CHÍNH MODEL.
     *
     * <p>Cố ý không duy trì bằng tay. Một danh sách chép tay chắc chắn sẽ thiếu trường
     * ({@code Price}, {@code TaxRate}, {@code Unit}, {@code ProductId}...) và mỗi trường
     * thiếu là một xung đột thật không được phát hiện. Đọc thẳng từ model thì thêm trường
     * mới vào model là guard tự biết.
     *
     * <p>Đọc theo đúng quy ước của Gson đang dùng: {@code @SerializedName} nếu có, còn lại
     * là {@code UPPER_CAMEL_CASE}. Bỏ {@code transient}/{@code static} vì chúng không bao
     * giờ nằm trong JSON.
     */
    private static final java.util.Set<String> MODEL_KEYS = buildModelKeys();

    private static java.util.Set<String> buildModelKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Class<?> type = RefuelItemData.class; type != null; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers)
                        || java.lang.reflect.Modifier.isTransient(modifiers)) continue;

                com.google.gson.annotations.SerializedName named =
                        field.getAnnotation(com.google.gson.annotations.SerializedName.class);
                if (named != null) {
                    keys.add(named.value());
                    continue;
                }
                String name = field.getName();
                keys.add(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
        }
        return java.util.Collections.unmodifiableSet(keys);
    }

    /** Khoá JSON mà model app biết — dùng cho cả vân tay lẫn lúc ghi giữ khoá lạ. */
    public static java.util.Set<String> modelKeys() {
        return MODEL_KEYS;
    }

    /** Chỉ giữ nhóm client sở hữu, CHUẨN HOÁ giá trị rồi mới băm. */
    private static String canonicalClientOwnedJson(String json) {
        com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return json;

        com.google.gson.JsonObject source = parsed.getAsJsonObject();
        com.google.gson.JsonObject owned = new com.google.gson.JsonObject();
        for (String key : source.keySet()) {
            if (!RefuelFieldOwnership.isClientFingerprintKey(key)) continue;
            com.google.gson.JsonElement normalized = RefuelValues.normalize(key, source.get(key));
            if (normalized != null) owned.add(key, normalized);
        }

        return canonicalize(owned).toString();
    }

    /**
     * Ghi payload của model lên JSON đang có mà GIỮ NGUYÊN các khoá model không biết.
     *
     * <p>Server gửi xuống nhiều trường ngoài model ({@code TechLog}, {@code Weight},
     * {@code Invoice}...). Cách ghi cũ — thay toàn bộ {@code jsonData} bằng
     * {@code gson.toJson(model)} — xoá sạch chúng ở mỗi lần lưu local, tức app âm thầm làm
     * mất dữ liệu của server và đẩy bản thiếu đó lên trong lần POST kế tiếp.
     *
     * <p>Khoá model BIẾT thì lấy theo model, kể cả khi vắng mặt (Gson bỏ qua null, nên
     * vắng mặt nghĩa là người dùng vừa xoá giá trị — phải xoá thật). Khoá model KHÔNG biết
     * thì giữ nguyên.
     */
    /**
     * Nguồn của {@code modelJson} — quyết định NGHĨA của một khoá vắng mặt.
     *
     * <p>Gson bỏ qua field null, nên "khoá vắng mặt" một mình KHÔNG phân biệt được
     * "người dùng vừa xoá giá trị" với "bản này vốn không nói gì về trường đó". Người gọi
     * phải khai báo, không được đoán.
     */
    public enum ModelPayloadSource {
        /**
         * Model ĐẦY ĐỦ, dựng từ chính row (toRefuelItemData) hoặc từ màn hình đang giữ cả
         * phiếu. Ở đây vắng mặt = null = người dùng đã xoá ⇒ xoá thật.
         */
        COMPLETE_MODEL,
        /**
         * Bản MỘT PHẦN: response/projection của server, patch chỉ mang vài trường. Vắng mặt
         * là "không nói gì" ⇒ giữ nguyên giá trị đang có. Đây là chiều an toàn duy nhất:
         * hiểu nhầm projection thiếu trường thành "người dùng xoá" chính là kiểu mất dữ liệu
         * mà toàn bộ tài liệu này đang đi chữa.
         */
        PARTIAL
    }

    public static String mergePreservingUnknown(String previousJson, String modelJson) {
        return mergePreservingUnknown(previousJson, modelJson,
                ModelPayloadSource.COMPLETE_MODEL);
    }

    public static String mergePreservingUnknown(String previousJson, String modelJson,
                                                ModelPayloadSource source) {
        if (isBlank(previousJson)) return modelJson;
        if (isBlank(modelJson)) return previousJson;

        com.google.gson.JsonObject previous;
        com.google.gson.JsonObject model;
        try {
            previous = com.google.gson.JsonParser.parseString(previousJson).getAsJsonObject();
            model = com.google.gson.JsonParser.parseString(modelJson).getAsJsonObject();
        } catch (RuntimeException ex) {
            return modelJson;
        }

        com.google.gson.JsonObject merged = previous.deepCopy();

        // Khoá model BIẾT: theo model. Vắng mặt chỉ được coi là "đã xoá" khi bản này là
        // model đầy đủ; với bản một phần thì giữ nguyên giá trị cũ.
        for (String key : MODEL_KEYS) {
            if (model.has(key)) merged.add(key, model.get(key));
            else if (source == ModelPayloadSource.COMPLETE_MODEL) merged.remove(key);
        }

        // Khoá model KHÔNG biết: luôn giữ nguyên bản đang có. Chỉ nhận thêm nếu chính
        // modelJson mang tới (trường hợp payload đi kèm khoá ngoài model).
        for (String key : model.keySet())
            if (!MODEL_KEYS.contains(key)) merged.add(key, model.get(key));

        return merged.toString();
    }

    /**
     * Ảnh chụp riêng nhóm trường SERVER sở hữu, dùng làm CHIỀU THỨ BA khi lưu.
     *
     * <p>Chỉ giữ những khoá THỰC SỰ có trong nguồn: khoá vắng mặt phải giữ nguyên là vắng
     * mặt, nếu không lúc so sẽ thành "người dùng vừa xoá trường này".
     */
    public static String serverOwnedProjection(String json) {
        if (isBlank(json)) return null;

        com.google.gson.JsonElement parsed;
        try {
            parsed = com.google.gson.JsonParser.parseString(json);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!parsed.isJsonObject()) return null;

        com.google.gson.JsonObject source = parsed.getAsJsonObject();
        com.google.gson.JsonObject projection = new com.google.gson.JsonObject();
        for (String key : SERVER_OWNED_KEYS)
            if (source.has(key)) projection.add(key, source.get(key));

        return projection.toString();
    }

    /**
     * Nhận vào snapshot đang chuẩn bị lưu những thay đổi mà SERVER đã gửi xuống trong lúc
     * màn hình mở (đổi chuyến, đổi bãi đỗ, trạng thái chuyến, giờ dự kiến...).
     *
     * <p>Không có bước này thì snapshot của màn hình ghi đè ngược các trường đó về giá trị
     * lúc mở màn hình, và POST kế tiếp đẩy luôn giá trị cũ lên server.
     *
     * <p>TRỘN BA CHIỀU, KHÔNG phải phủ đè. Người dùng sửa được chính vài trường trong nhóm
     * này (bãi đỗ, số hiệu/loại tàu bay, hãng, chặng — xem RefuelDetailActivity và
     * RefuelPreviewActivity), nên luật là:
     * <ul>
     *   <li>trường snapshot KHÔNG đụng tới ⇒ lấy giá trị mới của server;</li>
     *   <li>trường người dùng ĐÃ sửa ⇒ giữ nguyên bản của người dùng.</li>
     * </ul>
     *
     * <p>Không có baseline để đối chiếu thì KHÔNG nhận gì cả: khi đó không phân biệt được
     * "người dùng vừa sửa" với "server vừa đổi", và giữ dữ liệu người dùng luôn là chiều
     * an toàn hơn.
     *
     * @param storedJson  jsonData của row đang có trong Room
     * @param baseServerOwnedJson giá trị nhóm trường server tại thời điểm màn hình đọc row
     * @return mô tả các trường server vừa được nhận, hoặc null nếu không nhận gì
     */
    /** Kết quả của một lần nhận trường server: mô tả thay đổi, kèm các xung đột SHARED thật. */
    public static final class AdoptResult {
        public final String diff;
        public final java.util.List<String> conflictKeys;

        AdoptResult(String diff, java.util.List<String> conflictKeys) {
            this.diff = diff;
            this.conflictKeys = conflictKeys;
        }

        public boolean hasConflict() {
            return !conflictKeys.isEmpty();
        }
    }

    public static AdoptResult adoptServerOwned(RefuelItemData target, String storedJson,
                                               String baseServerOwnedJson) {
        java.util.List<String> conflicts = new java.util.ArrayList<>();
        if (target == null || isBlank(storedJson) || isBlank(baseServerOwnedJson))
            return new AdoptResult(null, conflicts);

        com.google.gson.JsonObject base;
        com.google.gson.JsonObject stored;
        com.google.gson.JsonObject ours;
        try {
            base = com.google.gson.JsonParser.parseString(baseServerOwnedJson).getAsJsonObject();
            stored = com.google.gson.JsonParser.parseString(storedJson).getAsJsonObject();
            ours = com.google.gson.JsonParser.parseString(target.toJson()).getAsJsonObject();
        } catch (RuntimeException ex) {
            return new AdoptResult(null, conflicts);
        }

        com.google.gson.JsonObject adoptable = new com.google.gson.JsonObject();
        for (String key : SERVER_OWNED_KEYS) {
            if (!stored.has(key)) continue;

            boolean userChanged = !RefuelValues.equal(key, base.get(key), ours.get(key));
            boolean rowChanged = !RefuelValues.equal(key, base.get(key), stored.get(key));

            // SHARED mà CẢ HAI phía cùng đổi sang giá trị khác nhau là xung đột THẬT. Lấy
            // bản người dùng làm mặc định ở đây nghĩa là âm thầm xoá thay đổi của điều độ
            // (đổi bãi đỗ, đổi số hiệu tàu bay, đổi giá) — đúng kiểu mất dữ liệu ngược chiều.
            if (RefuelFieldOwnership.isShared(key) && userChanged && rowChanged
                    && !RefuelValues.equal(key, ours.get(key), stored.get(key))) {
                conflicts.add(key);
                continue;
            }

            if (userChanged) continue;             // người dùng đã sửa ⇒ giữ bản người dùng
            adoptable.add(key, stored.get(key));
        }
        if (!conflicts.isEmpty()) return new AdoptResult(null, conflicts);
        if (adoptable.size() == 0) return new AdoptResult(null, conflicts);

        RefuelItemData merged;
        try {
            merged = GSON.fromJson(
                    mergeByOwnership(target.toJson(), adoptable.toString(), false),
                    RefuelItemData.class);
        } catch (RuntimeException ex) {
            return new AdoptResult(null, conflicts);
        }
        if (merged == null) return new AdoptResult(null, conflicts);

        String diff = describeServerOwnedDiff(target, merged);

        target.setFlightId(merged.getFlightId());
        target.setFlightUniqueId(merged.getFlightUniqueId());
        target.setFlightCode(merged.getFlightCode());
        target.setFlightStatus(merged.getFlightStatus());
        target.setParkingLot(merged.getParkingLot());
        target.setRouteName(merged.getRouteName());
        target.setArrivalTime(merged.getArrivalTime());
        target.setDepartureTime(merged.getDepartureTime());
        target.setRefuelTime(merged.getRefuelTime());
        target.setAircraftCode(merged.getAircraftCode());
        target.setAircraftType(merged.getAircraftType());
        target.setAirlineId(merged.getAirlineId());
        target.setAirlineModel(merged.getAirlineModel());
        target.setInternational(merged.isInternational());
        target.setDeleted(merged.isDeleted());
        target.setSortOrder(merged.getSortOrder());
        target.setEstimateAmount(merged.getEstimateAmount());

        return new AdoptResult(diff, conflicts);
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

    /**
     * Dung sai khi đối chiếu nhiệt độ và tỉ trọng của response.
     *
     * <p>BIÊN: điều kiện báo lệch là {@code |a - b| > tolerance}, nên chênh lệch ĐÚNG BẰNG
     * dung sai vẫn được coi là khớp. Chọn chiều này có chủ ý — hai giá trị chỉ khác nhau
     * đúng một đơn vị cuối là kết quả làm tròn của backend, không phải dữ liệu bị mất.
     * Nhiệt độ hiển thị 2 số lẻ, tỉ trọng 4 số lẻ, dung sai bằng đúng một đơn vị cuối đó.
     */
    private static final double TEMPERATURE_TOLERANCE = 0.01d;

    private static final double DENSITY_TOLERANCE = 0.0001d;

    /**
     * So sánh có dung sai, ổn định với biểu diễn nhị phân của {@code double}.
     *
     * <p>{@code 30.01 - 30.00} ra {@code 0.0100000000000016}, tức là so thẳng
     * {@code > 0.01} sẽ báo lệch cho đúng cái ca mà dung sai sinh ra để bỏ qua. Nới thêm một
     * lượng nhỏ hơn mọi sai số biểu diễn ở dải giá trị này (nhiệt độ, tỉ trọng đều nhỏ hơn
     * 1000) để biên "chênh đúng bằng dung sai ⇒ vẫn khớp" thành đúng thật.
     */
    private static boolean differsBeyond(double a, double b, double tolerance) {
        return Math.abs(a - b) > tolerance + 1e-9d;
    }

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

        // Với mẻ đã chốt, số đồng hồ không phải toàn bộ hợp đồng. Nhiệt độ, tỉ trọng và số
        // hoá nghiệm là dữ liệu người dùng nhập ở màn hình xác nhận; nếu không đối chiếu
        // chúng thì một response chỉ echo lại nhóm số đồng hồ vẫn được coi là ACK, row bị
        // xoá cờ dirty và ba trường kia không bao giờ lên tới server — mất im lặng.
        //
        // Chỉ đòi khi gói gửi lên THỰC SỰ có giá trị: phiếu chưa nhập thì không có gì để đối
        // chiếu, và bắt bẻ một trường rỗng chỉ tạo ra non-ACK giả.
        // So bằng DUNG SAI, không so tuyệt đối: backend lưu các trường này ở kiểu có độ
        // chính xác khác (decimal(x,y)) nên chênh lệch làm tròn là bình thường. So tuyệt đối
        // sẽ biến mọi phiếu thành non-ACK và giữ chúng ở trạng thái chờ vĩnh viễn — đổi một
        // kiểu hỏng lấy một kiểu hỏng khác.
        if (requestStatus == REFUEL_ITEM_STATUS.DONE) {
            if (expected.getManualTemperature() > 0
                    && differsBeyond(expected.getManualTemperature(),
                    actual.getManualTemperature(), TEMPERATURE_TOLERANCE))
                diff.append(String.format(java.util.Locale.US, "temp(%.2f->%.2f) ",
                        expected.getManualTemperature(), actual.getManualTemperature()));

            if (expected.getDensity() > 0
                    && differsBeyond(expected.getDensity(), actual.getDensity(),
                    DENSITY_TOLERANCE))
                diff.append(String.format(java.util.Locale.US, "density(%.4f->%.4f) ",
                        expected.getDensity(), actual.getDensity()));

            if (!isBlank(expected.getQualityNo())
                    && !Objects.equals(expected.getQualityNo(), actual.getQualityNo()))
                diff.append(String.format("qc(%s->%s) ",
                        expected.getQualityNo(), actual.getQualityNo()));
        }

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
        // KHÔNG serialize lại toàn bộ model vào jsonData. Đường ACK chỉ cần cập nhật
        // metadata, nhưng đi qua model là đi qua các giá trị mặc định của nó: đo trên máy
        // thật 17-08, server gửi "ReceiptCount":null và "WaterSensor":null, model biến chúng
        // thành 0.0, vân tay của row đổi, và mọi màn hình đang mở mất quyền lưu — đúng dòng
        // VERSION_CONFLICT duy nhất còn lại của mẻ 843 GL.
        //
        // Chỉ đắp đúng nhóm metadata lên JSON THÔ, giữ nguyên mọi thứ còn lại từng byte.
        localItem.setJsonData(overlayMetadata(localItem.getJsonData(), mergedId, mergedUniqueId,
                mergedClientSeq, mergedRevision, serverData.getDateUpdated()));
        localItem.setLocalModified(keepLocalModified);
    }

    /**
     * Đắp nhóm metadata lên JSON thô, KHÔNG đụng tới bất kỳ khoá nghiệp vụ nào.
     *
     * <p>Đây là toàn bộ những gì đường ACK được phép ghi vào payload. Đi vòng qua model để
     * làm việc này là đổi luôn những khoá mà model có giá trị mặc định khác JSON đang lưu —
     * lỗi đã đo được với {@code null → 0.0}.
     */
    private static String overlayMetadata(String json, int id, String uniqueId,
                                          long clientSeq, int serverRevision,
                                          java.util.Date dateUpdated) {
        if (isBlank(json)) return json;

        com.google.gson.JsonObject obj;
        try {
            obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ex) {
            return json;
        }

        if (id > 0) obj.addProperty("Id", id);
        if (!isBlank(uniqueId)) obj.addProperty("UniqueId", uniqueId);
        obj.addProperty("ClientSeq", clientSeq);
        obj.addProperty("ServerRevision", serverRevision);
        if (dateUpdated != null)
            obj.addProperty("DateUpdated", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(dateUpdated));

        // Cờ runtime của phiên POST không bao giờ được lưu xuống Room.
        obj.remove("Applied");
        obj.remove("RejectReason");

        return obj.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
