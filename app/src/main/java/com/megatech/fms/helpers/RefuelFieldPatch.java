package com.megatech.fms.helpers;

import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Thay đổi do MỘT MÀN HÌNH tạo ra, dưới dạng patch có phạm vi rõ ràng.
 *
 * <p>Sinh ra để thay cách lưu cũ: phủ toàn bộ snapshot JSON lên row. Phủ toàn bộ nghĩa là
 * một snapshot đứng trên nền cũ sẽ kéo lùi mọi trường mà nó không hề đụng tới — đúng hình
 * dạng sự cố mẻ 1110 GL bị bản 862 GL ghi đè. Patch chỉ mang những trường người dùng THỰC SỰ
 * sửa trên màn hình đó, nên phần còn lại của row không bao giờ bị chạm vào.
 *
 * <p>KHÔNG được đắp mù. Luật, theo đúng thứ tự:
 * <ol>
 *   <li>row mới nhất đã {@code DONE} với số liệu chốt khác nền ⇒ CHẶN, không đắp gì cả;</li>
 *   <li>một trường mà CẢ HAI phía cùng đổi khỏi nền, sang giá trị khác nhau ⇒ CHẶN;</li>
 *   <li>còn lại: chỉ những trường người dùng đổi mới được ghi đè lên row mới nhất.</li>
 * </ol>
 */
public final class RefuelFieldPatch {

    private RefuelFieldPatch() {
    }

    /** Phạm vi trường mà một màn hình được phép ghi. */
    public enum Scope {
        /**
         * Đúng tập trường {@code RefuelDetailConfirmActivity} cho phép nhập. Thêm ô nhập mới
         * trên màn hình đó thì phải thêm khoá ở đây, nếu không thay đổi sẽ bị bỏ im lặng.
         */
        CONFIRM(new String[]{
                "Status", "RealAmount", "Gallon", "Volume",
                "StartNumber", "EndNumber",
                "ManualTemperature", "Density", "QualityNo", "WeightNote",
                "ReturnAmount", "ReturnUnit",
                "StartTime", "EndTime",
                "ReceiptNumber", "TruckId", "TruckNo",
                "DriverId", "DriverName", "OperatorId", "OperatorName"}),

        /**
         * Đúng tập trường vòng đời mẻ ở {@code RefuelDetailActivity}: số liệu đồng hồ, thời
         * gian, và các trường thiết bị đọc được. KHÔNG gồm nhiệt độ tay, tỉ trọng, số hoá
         * nghiệm — những thứ đó thuộc màn hình xác nhận.
         */
        END(new String[]{
                "Status", "RealAmount", "Gallon",
                "StartNumber", "EndNumber", "OriginalEndMeter",
                "StartTime", "EndTime", "DeviceStartTime", "DeviceEndTime",
                "Temperature", "WaterSensor", "SaleNumber", "TicketNumber",
                "TruckId", "TruckNo", "ReceiptNumber", "Completed"}),

        /**
         * Đúng tập trường các hộp thoại của {@code RefuelPreviewActivity} cho phép nhập.
         *
         * <p>Màn hình xem trước đang lưu bằng FULL SNAPSHOT bất đồng bộ, mỗi hộp thoại một
         * luồng riêng. Sửa nhiệt độ rồi tỉ trọng liên tiếp là hai luồng chạy song song trên
         * cùng một baseline: luồng về sau đứng trên ClientSeq đã cũ nên bị chặn CONFLICT, và
         * người dùng phải nhập lại — đúng phản ánh "nhiệt độ, tỉ trọng nhập mấy lần mới ăn".
         * Có scope này thì lần bị chặn còn đường đắp lại đúng trường vừa nhập lên row mới
         * nhất, thay vì bắt người dùng gõ lại.
         *
         * <p>Thêm ô nhập mới trên màn hình đó thì PHẢI thêm khoá ở đây, nếu không thay đổi
         * sẽ bị bỏ im lặng.
         */
        PREVIEW(new String[]{
                // CHỈ những trường màn hình xem trước thực sự ghi qua updateBinding().
                //
                // KHÔNG thêm khoá có giá trị là OBJECT (ví dụ AirlineModel): RefuelValues
                // .normalize() trả nguyên JsonElement cho thứ không phải primitive, nên object
                // lồng bị so bằng JSON sâu. Bản model round-trip đổi thứ tự khoá và mất khoá
                // server chưa biết, nên nó LUÔN khác nền — patch thấy "cả hai phía cùng đổi"
                // và chặn mọi lần sửa. Đo trên máy thật 27-08-2026 14:22:17:
                // result=FIELD_CONFLICT [AirlineModel] trong khi người dùng chỉ sửa giờ.
                //
                // Cũng KHÔNG thêm metadata chứng từ (ReceiptNumber/ReceiptCount/PrintStatus):
                // chúng đi đường patchAllPrintItems() chứ không qua đây, mà mặc định của model
                // khác "vắng mặt trong JSON" nên cũng sinh xung đột giả.

                // setAll(...) — áp cho MỌI mẻ của xe hiện tại trong chuyến
                "AircraftCode", "AircraftType", "RouteName", "ParkingLot",
                "InvoiceNameCharter", "Price", "TaxRate", "IsInternational",
                // hộp thoại sửa đúng một mẻ
                "ManualTemperature", "Density", "QualityNo", "WeightNote",
                "RealAmount", "Gallon", "StartNumber", "Volume", "ChangeFlag",
                "ReturnAmount", "ReturnUnit",
                "InvoiceNumber", "ReturnInvoiceNumber",
                // sửa giờ tay — showTimeDialog(), một đường riêng ngoài switch hộp thoại
                "StartTime", "EndTime",
                // chọn nhân viên
                "DriverId", "DriverName", "OperatorId", "OperatorName",
                // chọn hãng bay — setAirline(). AirlineId là định danh vô hướng; AirlineModel
                // cố ý để ngoài, xem ghi chú đầu khối.
                "AirlineId", "Currency", "ProductName", "Unit",
                // chọn mẫu hoá đơn — setInvoiceForm()
                "InvoiceFormId", "FormNo", "PCode", "PName", "PrintTemplate", "Sign"}),

        /**
         * Đúng tập trường các hộp thoại của {@code RefuelDetailActivity} cho phép nhập.
         *
         * <p>KHÔNG gồm số đồng hồ, sản lượng, trạng thái hay giờ bắt đầu/kết thúc: những thứ
         * đó thuộc thiết bị và đường End ({@link Scope#END}), người dùng không gõ ở đây.
         *
         * <p>Thêm ô nhập mới trên màn hình đó thì PHẢI thêm khoá ở đây, nếu không thay đổi
         * sẽ bị bỏ im lặng.
         */
        DETAIL(new String[]{
                "AircraftCode", "AircraftType", "ParkingLot", "InvoiceNameCharter",
                "ManualTemperature", "Temperature", "Density", "QualityNo",
                "DriverId", "DriverName", "OperatorId", "OperatorName"});

        private final String[] keys;

        Scope(String[] keys) {
            this.keys = keys;
        }
    }

    /**
     * Các trường CHỐT của một mẻ. Row đã {@code DONE} mà nhóm này khác nền thì có nghĩa mẻ
     * đã được chốt bằng một bộ số khác — snapshot đang cầm là bản cũ, không được đắp lên.
     */
    private static final String[] FINAL_KEYS = {
            "RealAmount", "StartNumber", "EndNumber", "EndTime"};

    /** Kết quả của một lần thử đắp patch. */
    public static final class Result {
        private final RefuelItemData merged;
        private final List<String> conflictKeys;
        private final boolean blockedByFinalizedRow;

        private Result(RefuelItemData merged, List<String> conflictKeys,
                       boolean blockedByFinalizedRow) {
            this.merged = merged;
            this.conflictKeys = conflictKeys;
            this.blockedByFinalizedRow = blockedByFinalizedRow;
        }

        /** Payload đã ghép, chỉ có khi {@link #isApplied()}. */
        public RefuelItemData getMerged() {
            return merged;
        }

        public boolean isApplied() {
            return merged != null;
        }

        /** Những trường cả hai phía cùng đổi — thứ người dùng cần được nhìn thấy. */
        public List<String> getConflictKeys() {
            return conflictKeys;
        }

        public boolean isBlockedByFinalizedRow() {
            return blockedByFinalizedRow;
        }

        public String describe() {
            if (blockedByFinalizedRow) return "ROW_ALREADY_FINALIZED";
            if (!conflictKeys.isEmpty()) return "FIELD_CONFLICT " + conflictKeys;
            return "APPLIED";
        }
    }

    /**
     * @param baseJson   nguyên văn row tại thời điểm màn hình đọc nó
     * @param ours       snapshot người dùng đang cầm, đã nhập liệu
     * @param latestJson nguyên văn row mới nhất trong Room, đọc dưới khoá ghi
     */
    public static Result apply(Scope scope, String baseJson, RefuelItemData ours,
                               String latestJson) {
        // Không biết version ⇒ giả định CÓ màn hình khác đã ghi, tức luật chặt nhất.
        // Mặc định phải nghiêng về phía chặn, không phải phía cho qua.
        return apply(scope, baseJson, ours, latestJson, 0, 1);
    }

    /**
     * @param baseClientSeq   ClientSeq của row lúc màn hình đọc
     * @param latestClientSeq ClientSeq của row hiện tại
     *
     * <p>Hai tham số này phân biệt được điều mà so JSON KHÔNG phân biệt nổi: row đổi vì một
     * MÀN HÌNH KHÁC ghi (ClientSeq tăng — xung đột thật), hay đổi vì lượt pull nhận lại bản
     * của server (ClientSeq đứng yên — server echo, không phải người khác sửa).
     *
     * <p>Đo trên máy thật 17-08 15:26: server trả {@code EndTime} kèm 7 chữ số lẻ giây và
     * {@code OriginalEndMeter = 0}. Mỗi lượt pull ghi đè hai trường đó lên row sạch, patch
     * thấy "cả hai phía cùng đổi" nên chặn — mẻ 837 GL không thể chốt, bấm Thử lại bao
     * nhiêu lần cũng chặn. Với nhóm CLIENT sở hữu, thiết bị là nguồn chuẩn: bản server echo
     * không được phép chặn việc chốt mẻ.
     */
    public static Result apply(Scope scope, String baseJson, RefuelItemData ours,
                               String latestJson, long baseClientSeq, long latestClientSeq) {
        if (ours == null || isBlank(baseJson) || isBlank(latestJson))
            return new Result(null, single("NO_BASELINE"), false);

        com.google.gson.JsonObject base;
        com.google.gson.JsonObject latest;
        com.google.gson.JsonObject mine;
        try {
            base = com.google.gson.JsonParser.parseString(baseJson).getAsJsonObject();
            latest = com.google.gson.JsonParser.parseString(latestJson).getAsJsonObject();
            mine = com.google.gson.JsonParser.parseString(ours.toJson()).getAsJsonObject();
        } catch (RuntimeException ex) {
            return new Result(null, single("UNPARSEABLE"), false);
        }

        // Row có bị một màn hình KHÁC ghi hay không. Chỉ khi đó "row đã đổi" mới là một
        // lần sửa cạnh tranh; ngược lại đó là bản server echo.
        boolean anotherWriterMoved = latestClientSeq != baseClientSeq;

        // (1) Mẻ đã được chốt bằng một bộ số khác ⇒ dừng hẳn.
        if (anotherWriterMoved && isDone(latest) && differsOnAny(base, latest, FINAL_KEYS)
                && differsOnAny(mine, latest, FINAL_KEYS))
            return new Result(null, new ArrayList<>(), true);

        // (2) Cùng một trường, hai phía đổi sang hai giá trị khác nhau.
        List<String> conflicts = new ArrayList<>();
        List<String> changedByUser = new ArrayList<>();
        for (String key : scope.keys) {
            // So theo NGHĨA, không theo kiểu JSON. Vân tay đã chuẩn hoá mà patch lại so
            // bằng Objects.equals trên JsonElement thì lỗi cũ quay lại ở đúng đường patch:
            // server trả Status:0 (số), app ghi Status:"0" (chuỗi) ⇒ conflict giả.
            boolean userChanged = !RefuelValues.equal(key, base.get(key), mine.get(key));
            boolean rowChanged = !RefuelValues.equal(key, base.get(key), latest.get(key));

            // Trường CLIENT sở hữu mà không màn hình nào khác ghi: thay đổi trên row chỉ có
            // thể đến từ lượt pull. Thiết bị là nguồn chuẩn của số liệu mẻ, nên bản echo đó
            // không được biến thành xung đột.
            if (rowChanged && !anotherWriterMoved
                    && RefuelFieldOwnership.of(key) == RefuelFieldOwnership.Ownership.CLIENT)
                rowChanged = false;

            if (userChanged && rowChanged
                    && !RefuelValues.equal(key, mine.get(key), latest.get(key)))
                conflicts.add(key);
            else if (userChanged)
                changedByUser.add(key);
        }
        if (!conflicts.isEmpty())
            return new Result(null, conflicts, false);

        // (3) Đắp đúng phần người dùng đổi lên row mới nhất.
        com.google.gson.JsonObject merged = latest.deepCopy();
        for (String key : changedByUser) {
            if (mine.has(key)) merged.add(key, mine.get(key));
            else merged.remove(key);
        }

        RefuelItemData result;
        try {
            result = RefuelItemData.fromJson(merged.toString());
        } catch (RuntimeException ex) {
            return new Result(null, single("UNPARSEABLE_MERGE"), false);
        }
        if (result == null)
            return new Result(null, single("UNPARSEABLE_MERGE"), false);

        return new Result(result, new ArrayList<>(), false);
    }

    /** Danh sách trường người dùng đã đổi so với nền — dùng cho log. */
    public static List<String> changedKeys(Scope scope, String baseJson, RefuelItemData ours) {
        List<String> changed = new ArrayList<>();
        if (ours == null || isBlank(baseJson)) return changed;

        try {
            com.google.gson.JsonObject base =
                    com.google.gson.JsonParser.parseString(baseJson).getAsJsonObject();
            com.google.gson.JsonObject mine =
                    com.google.gson.JsonParser.parseString(ours.toJson()).getAsJsonObject();
            for (String key : scope.keys)
                if (!RefuelValues.equal(key, base.get(key), mine.get(key))) changed.add(key);
        } catch (RuntimeException ignored) {
        }
        return changed;
    }

    /**
     * Các trường người dùng đã đổi nhưng NẰM NGOÀI scope — tức là chúng sẽ bị đường patch
     * BỎ IM LẶNG.
     *
     * <p>Vì sao cần: khi lưu cả gói bị chặn, máy tự thử lại bằng patch theo scope. Patch báo
     * thành công, nhưng những trường ngoài scope không hề được ghi — người dùng không nhận
     * được tín hiệu nào và chỉ phát hiện khi mở lại thấy giá trị cũ. Đúng hình dạng mất mát
     * của tỉ trọng / nhiệt độ đo được ngày 06-09-2026, chỉ khác cơ chế.
     *
     * <p>Chủ dự án chốt 06-09-2026: dùng để CẢNH BÁO, tuyệt đối không dùng để chặn. Thời gian
     * trên sân rất gấp; chặn ở đây là làm dở dang cả nghiệp vụ vì một trường phụ.
     *
     * <p>Chỉ xét các khoá vô hướng có mặt ở ÍT NHẤT một trong hai bản: khoá vắng cả hai bên
     * là không có gì để nói, còn object lồng thì round-trip của model đã làm nó khác nền sẵn
     * nên so ở đây chỉ sinh báo động giả (xem ghi chú ở {@link Scope#PREVIEW}).
     */
    public static List<String> droppedKeysOutsideScope(Scope scope, String baseJson,
                                                       RefuelItemData ours) {
        List<String> dropped = new ArrayList<>();
        if (ours == null || scope == null || isBlank(baseJson)) return dropped;

        java.util.Set<String> inScope = new java.util.HashSet<>(java.util.Arrays.asList(scope.keys));
        try {
            com.google.gson.JsonObject base =
                    com.google.gson.JsonParser.parseString(baseJson).getAsJsonObject();
            com.google.gson.JsonObject mine =
                    com.google.gson.JsonParser.parseString(ours.toJson()).getAsJsonObject();

            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            for (String k : base.keySet()) keys.add(k);
            for (String k : mine.keySet()) keys.add(k);

            for (String key : keys) {
                if (inScope.contains(key)) continue;
                com.google.gson.JsonElement a = base.get(key);
                com.google.gson.JsonElement b = mine.get(key);
                // Object/array lồng nhau: bỏ qua, xem javadoc.
                if ((a != null && (a.isJsonObject() || a.isJsonArray()))
                        || (b != null && (b.isJsonObject() || b.isJsonArray()))) continue;
                if (!RefuelValues.equal(key, a, b)) dropped.add(key);
            }
        } catch (RuntimeException ignored) {
        }
        return dropped;
    }

    private static boolean isDone(com.google.gson.JsonObject row) {
        com.google.gson.JsonElement status = row.get("Status");
        if (status == null || status.isJsonNull()) return false;
        try {
            return String.valueOf(REFUEL_ITEM_STATUS.DONE.getValue())
                    .equals(status.getAsString());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean differsOnAny(com.google.gson.JsonObject a,
                                        com.google.gson.JsonObject b, String[] keys) {
        for (String key : keys)
            if (!RefuelValues.equal(key, a.get(key), b.get(key))) return true;
        return false;
    }

    private static List<String> single(String value) {
        return new ArrayList<>(Arrays.asList(value));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
