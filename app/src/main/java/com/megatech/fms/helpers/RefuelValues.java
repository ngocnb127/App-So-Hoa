package com.megatech.fms.helpers;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * So sánh giá trị của phiếu theo NGHĨA, dùng chung cho mọi nơi cần quyết định "có đổi không".
 *
 * <p>Sinh ra vì đã đo được trên máy thật: cùng một giá trị nhưng khác biểu diễn JSON đủ để
 * chặn mọi lần lưu — server trả {@code "Status":0} (số) trong khi Gson của app ghi
 * {@code "Status":"0"} (chuỗi, do enum dùng {@code @SerializedName}). Nếu vân tay chuẩn hoá
 * mà {@link RefuelFieldPatch} lại so bằng {@code Objects.equals} trên {@code JsonElement} thì
 * lỗi cũ quay lại ở đúng đường patch — nên mọi nơi phải dùng CHUNG một comparator:
 * vân tay, patch, diff log.
 *
 * <p>Ba lớp giá trị, ba luật khác nhau:
 * <ul>
 *   <li>ĐỊNH LƯỢNG và enum: so theo giá trị số. {@code 0}, {@code "0"}, {@code 0.0} là một.</li>
 *   <li>THỜI GIAN: so theo giây. Backend lưu theo giây và có thể trả kèm phần lẻ.</li>
 *   <li>ĐỊNH DANH: so chuỗi CHÍNH XÁC. {@code "001"} khác {@code "1"} — số phiếu, số hoá đơn,
 *       số ticket là chứng từ, không phải số đo. Không trim, không đổi hoa thường.</li>
 * </ul>
 */
public final class RefuelValues {

    private RefuelValues() {
    }

    /** Enum và số đo — so theo giá trị số. */
    private static final Set<String> NUMERIC_KEYS = new HashSet<>(Arrays.asList(
            "Status", "PrintStatus", "PostStatus", "FlightStatus", "RefuelItemType",
            "ReturnUnit", "PrintTemplate", "Currency", "Unit",
            "RealAmount", "Gallon", "Volume", "EstimateAmount",
            "StartNumber", "EndNumber", "OriginalEndMeter", "WaterSensor",
            "Temperature", "ManualTemperature", "Density",
            "Price", "TaxRate", "ReturnAmount",
            "ProjectedCapacity", "ActualCapacity",
            "TruckId", "UserId", "DriverId", "OperatorId", "ProductId",
            "AirlineId", "FlightId", "SortOrder",
            "ChangeFlag", "InvoiceFormId", "BM2508Result", "ReceiptCount"));

    /** Thời gian — so theo giây. */
    private static final Set<String> DATE_KEYS = new HashSet<>(Arrays.asList(
            "StartTime", "EndTime", "DeviceStartTime", "DeviceEndTime",
            "ArrivalTime", "DepartureTime", "RefuelTime",
            "ApproachTime", "LeaveTime", "DateUpdated"));

    /**
     * ĐỊNH DANH — so chuỗi chính xác, tuyệt đối không parse số.
     *
     * <p>Liệt kê tường minh để người đọc thấy ngay ràng buộc nghiệp vụ, dù mặc định của
     * {@link #normalize} với khoá lạ cũng đã là so chuỗi chính xác.
     */
    private static final Set<String> IDENTIFIER_KEYS = new HashSet<>(Arrays.asList(
            "ReceiptNumber", "ReceiptUniqueId", "InvoiceNumber", "ReturnInvoiceNumber",
            "TicketNumber", "SaleNumber", "FormNo", "QualityNo", "Sign",
            "UniqueId", "FlightUniqueId", "FlightCode", "AircraftCode", "ParkingLot",
            "TruckNo", "DriverName", "OperatorName", "InvoiceNameCharter"));

    /** Hai giá trị của cùng một khoá có bằng nhau về nghĩa hay không. */
    public static boolean equal(String key, JsonElement a, JsonElement b) {
        return Objects.equals(normalize(key, a), normalize(key, b));
    }

    /**
     * Dạng chuẩn hoá dùng để so sánh và để băm.
     *
     * @return null khi giá trị vắng mặt hoặc là JSON null — hai thứ này coi như một ở tầng
     * so sánh; phân biệt "vắng mặt" với "null tường minh" là việc của
     * {@link RefuelSyncGuard#mergePreservingUnknown}, không phải của comparator.
     */
    public static JsonElement normalize(String key, JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive()) return value;

        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive;

        String text = primitive.getAsString();

        if (DATE_KEYS.contains(key)) {
            Long seconds = toEpochSeconds(text);
            return seconds == null ? new JsonPrimitive(text) : new JsonPrimitive(seconds);
        }

        if (NUMERIC_KEYS.contains(key)) {
            try {
                return new JsonPrimitive(String.valueOf(Double.parseDouble(text)));
            } catch (NumberFormatException ex) {
                return new JsonPrimitive(text);
            }
        }

        // Mặc định — kể cả khoá lạ — là so chuỗi CHÍNH XÁC. Chiều an toàn: thà báo khác
        // nhau rồi bị chặn, còn hơn coi hai chứng từ khác nhau là một.
        return new JsonPrimitive(text);
    }

    /**
     * Giờ về epoch giây. Chấp nhận cả dạng ISO của Gson lẫn số epoch, và bỏ phần lẻ giây —
     * backend lưu theo giây nên so bằng millisecond sẽ báo lệch giả cho mọi phiếu.
     */
    private static Long toEpochSeconds(String text) {
        if (text == null || text.isEmpty()) return null;

        try {
            return Long.parseLong(text) / 1000L;
        } catch (NumberFormatException ignored) {
            // không phải epoch, thử ISO bên dưới
        }

        String iso = text.length() > 19 ? text.substring(0, 19) : text;
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
        format.setLenient(false);
        try {
            return format.parse(iso).getTime() / 1000L;
        } catch (java.text.ParseException ex) {
            return null;
        }
    }
}
