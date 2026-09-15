package com.megatech.fms.rut;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Đọc response của RutOS mà không phụ thuộc vào một tên field cố định.
 *
 * <p>Cùng một thông tin mang tên khác nhau giữa các model và các bản firmware: nhà mạng là
 * {@code operator} ở máy này và {@code operator_name} ở máy kia; SIM đang dùng là
 * {@code sim}, {@code sim_id} hay {@code active_sim}. Parser cứng theo một tên sẽ im lặng
 * trả null sau mỗi lần nâng firmware — hỏng không có dấu hiệu, đúng loại khó lần nhất.
 *
 * <p>Nguyên tắc: tra theo DANH SÁCH ALIAS, không tìm thấy thì trả null chứ không đoán.
 */
public final class RutJson {

    private RutJson() {
    }

    @Nullable
    public static JsonObject parseObject(@Nullable String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Bóc phần {@code data} của response RutOS.
     *
     * <p>Response chuẩn là {@code {"success":true,"data":{...}}}, nhưng một số endpoint trả
     * thẳng object. Nhận cả hai dạng.
     */
    @Nullable
    public static JsonElement data(@Nullable JsonObject response) {
        if (response == null) return null;
        if (response.has("data")) return response.get("data");
        return response;
    }

    /** Phần tử đầu tiên nếu là mảng; chính nó nếu là object. */
    @Nullable
    public static JsonObject firstObject(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonObject()) return element.getAsJsonObject();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array)
                if (item != null && item.isJsonObject()) return item.getAsJsonObject();
        }
        return null;
    }

    @Nullable
    public static JsonArray asArray(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (element.isJsonObject()) {
            JsonArray wrapped = new JsonArray();
            wrapped.add(element.getAsJsonObject());
            return wrapped;
        }
        return null;
    }

    /**
     * Giá trị nguyên thuỷ đầu tiên tìm được theo danh sách alias.
     *
     * <p>{@code "N/A"} bị coi là KHÔNG CÓ GIÁ TRỊ. Đo trên RUT955 thật (firmware
     * RUT9_R_00.07.06.21) khi khay SIM rỗng: {@code oper}, {@code provider}, {@code rsrp},
     * {@code band}, {@code imsi}… đều trả đúng chuỗi đó. Trả nguyên về UI nghĩa là in ra
     * dòng "Nhà mạng: N/A" — một chỗ trống được trình bày như một giá trị.
     */
    @Nullable
    public static String string(@Nullable JsonObject object, String... aliases) {
        JsonElement found = find(object, aliases);
        if (found == null || !found.isJsonPrimitive()) return null;
        String value = found.getAsString().trim();
        if (value.isEmpty() || isNotAvailable(value)) return null;
        return value;
    }

    /** Dấu "không có số liệu" của RutOS. */
    static boolean isNotAvailable(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.US);
        return normalized.equals("n/a") || normalized.equals("na")
                || normalized.equals("-") || normalized.equals("--");
    }

    /**
     * Số nguyên có dấu, chấp nhận cả chuỗi vì router hay trả {@code "-82"} hoặc
     * {@code "-82 dBm"}. Không đọc được thì trả null — số 0 mặc định sẽ hiện lên phiếu như
     * một phép đo có thật.
     */
    @Nullable
    public static Integer integer(@Nullable JsonObject object, String... aliases) {
        JsonElement found = find(object, aliases);
        if (found == null || !found.isJsonPrimitive()) return null;
        try {
            return found.getAsInt();
        } catch (RuntimeException ignored) {
            // rơi xuống đọc theo chuỗi
        }
        String raw = found.getAsString();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c) || (c == '-' && digits.length() == 0)) digits.append(c);
            else if (digits.length() > 0) break;
        }
        try {
            return digits.length() == 0 || "-".equals(digits.toString())
                    ? null : Integer.valueOf(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Cờ đúng/sai, chấp nhận cả chuỗi trạng thái của RutOS: {@code connected},
     * {@code "1"}, {@code up}, {@code yes}.
     *
     * <p>PHẢI hỏi {@code isBoolean()} trước chứ không được gọi thẳng {@code getAsBoolean()}
     * rồi bắt ngoại lệ: Gson KHÔNG ném lỗi cho chuỗi, nó lặng lẽ trả
     * {@code Boolean.parseBoolean()} — nghĩa là mọi chuỗi khác đúng chữ "true" đều thành
     * false. Viết theo lối bắt ngoại lệ thì cả khối đọc chuỗi bên dưới là mã chết, và
     * modem đang chạy với {@code "connected"} bị báo là mất kết nối.
     */
    @Nullable
    public static Boolean bool(@Nullable JsonObject object, String... aliases) {
        JsonElement found = find(object, aliases);
        if (found == null || !found.isJsonPrimitive()) return null;
        if (found.getAsJsonPrimitive().isBoolean()) return found.getAsBoolean();

        String value = found.getAsString().trim().toLowerCase(java.util.Locale.US);
        if (value.isEmpty()) return null;
        if (value.equals("1") || value.equals("true") || value.equals("yes")
                || value.equals("up") || value.contains("connected")) {
            // "disconnected"/"not connected" chứa "connected" nên phải loại trước.
            return !value.contains("dis") && !value.contains("not");
        }
        if (value.equals("0") || value.equals("false") || value.equals("no")
                || value.equals("down")) return false;
        return null;
    }

    // ------------------------------------------------------- hình dạng response lạ

    /**
     * Số node tối đa được duyệt khi tìm sâu. Response của router nhỏ; giới hạn này chỉ để
     * một payload dị thường không biến việc đọc trạng thái thành vòng lặp dài trên luồng nền.
     */
    private static final int DEEP_SEARCH_MAX_NODES = 256;

    /** Object có ít nhất một trong các khoá này với giá trị khác null. */
    public static boolean hasAny(@Nullable JsonObject object, String... aliases) {
        return find(object, aliases) != null;
    }

    /**
     * Một object con kèm KHOÁ của nó.
     *
     * <p>RutOS trả danh sách modem dưới dạng map lấy chính modem id làm khoá
     * ({@code {"1-1":{...}}}). Khi đó id KHÔNG nằm trong object mà nằm ở khoá — bỏ qua nó là
     * mất modemId, và mất modemId là mất sạch cường độ sóng lẫn SIM slot.
     */
    public static final class Keyed {
        public final String key;
        public final JsonObject object;

        Keyed(String key, JsonObject object) {
            this.key = key;
            this.object = object;
        }
    }

    /** Cặp (khoá, object con) đầu tiên — chỉ có nghĩa với dạng map kể trên. */
    @Nullable
    public static Keyed firstKeyedObject(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject())
                return new Keyed(entry.getKey(), value.getAsJsonObject());
        }
        return null;
    }

    /**
     * Danh sách object theo MỌI hình dạng RutOS từng trả cho một tập bản ghi: mảng, một
     * object đơn, hoặc map lấy id làm khoá.
     *
     * @param leafAliases các khoá chỉ dấu "đây là MỘT bản ghi, không phải map chứa nhiều bản
     *                    ghi". Thiếu tham số này thì không phân biệt được
     *                    {@code {"state":"inserted"}} với {@code {"1":{...}}}.
     */
    public static List<JsonObject> objects(@Nullable JsonElement element, String... leafAliases) {
        List<JsonObject> result = new ArrayList<>();
        if (element == null || element.isJsonNull()) return result;

        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray())
                if (item != null && item.isJsonObject()) result.add(item.getAsJsonObject());
            return result;
        }
        if (!element.isJsonObject()) return result;

        JsonObject object = element.getAsJsonObject();
        if (hasAny(object, leafAliases)) {
            result.add(object);
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) result.add(value.getAsJsonObject());
            else if (value != null && value.isJsonArray())
                for (JsonElement item : value.getAsJsonArray())
                    if (item != null && item.isJsonObject()) result.add(item.getAsJsonObject());
        }
        // Object đơn không khớp alias nào: vẫn trả về chính nó, trừ khi rỗng — object rỗng là
        // "router không có bản ghi nào", không phải "một bản ghi trống".
        if (result.isEmpty() && object.size() > 0) result.add(object);
        return result;
    }

    /**
     * Tìm giá trị nguyên thuỷ theo alias ở BẤT KỲ độ sâu nào.
     *
     * <p>Cùng một endpoint bọc dữ liệu khác nhau giữa các bản firmware:
     * {@code {"data":{...}}}, {@code {"data":{"device":{...}}}}, hay {@code {"data":[{...}]}}.
     * Đọc cứng một cấp thì mọi trường thành null và ứng dụng kết luận "không phải router
     * đã ghép cặp" trong khi router vẫn đang trả lời bình thường.
     */
    @Nullable
    public static JsonElement deepFind(@Nullable JsonElement root, String... aliases) {
        if (root == null || root.isJsonNull()) return null;

        Deque<JsonElement> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < DEEP_SEARCH_MAX_NODES) {
            JsonElement current = queue.poll();
            if (current == null || current.isJsonNull()) continue;

            if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                JsonElement direct = find(object, aliases);
                if (direct != null && direct.isJsonPrimitive()) return direct;
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && (value.isJsonObject() || value.isJsonArray()))
                        queue.add(value);
                }
            } else if (current.isJsonArray()) {
                for (JsonElement item : current.getAsJsonArray()) queue.add(item);
            }
        }
        return null;
    }

    /** {@link #deepFind} trả về chuỗi; rỗng cũng coi như không có. */
    @Nullable
    public static String deepString(@Nullable JsonElement root, String... aliases) {
        JsonElement found = deepFind(root, aliases);
        if (found == null || !found.isJsonPrimitive()) return null;
        String value = found.getAsString().trim();
        // Cùng luật loại dấu thiếu dữ liệu như string(). Bất đối xứng ở đây là một cái bẫy
        // nặng: serial đọc ra "N/A" sẽ được GHIM làm danh tính ghép cặp, và từ đó chính
        // chiếc router thật bị từ chối vĩnh viễn vì serial thật không khớp "N/A".
        if (value.isEmpty() || isNotAvailable(value)) return null;
        return value;
    }

    @Nullable
    private static JsonElement find(@Nullable JsonObject object, String... aliases) {
        if (object == null) return null;
        for (String alias : aliases) {
            JsonElement value = object.get(alias);
            if (value != null && !value.isJsonNull()) return value;
        }
        return null;
    }
}
