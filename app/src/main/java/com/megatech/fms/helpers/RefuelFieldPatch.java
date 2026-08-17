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
                "TruckId", "TruckNo", "ReceiptNumber", "Completed"});

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
