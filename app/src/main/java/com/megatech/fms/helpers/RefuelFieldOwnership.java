package com.megatech.fms.helpers;

import com.megatech.fms.model.RefuelItemData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * NGUỒN DUY NHẤT về quyền sở hữu từng trường của phiếu tra nạp.
 *
 * <p>Trước đây quyền sở hữu được SUY RA: "model biết trường, trừ nhóm không nghiệp vụ, trừ
 * nhóm server ⇒ client sở hữu". Suy luận đó sai ở cả hai đầu — model biết nhiều trường server
 * trả về chỉ để hiển thị, và danh sách nhóm server là danh sách tay nên luôn có thể thiếu.
 * Mỗi trường phân loại nhầm là một xung đột thật không được phát hiện, hoặc một xung đột giả
 * chặn đứng việc lưu.
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>{@link Ownership#CLIENT} — thiết bị là nguồn chuẩn. Vào vân tay precondition.</li>
 *   <li>{@link Ownership#SERVER} — server là nguồn chuẩn. KHÔNG vào vân tay client.</li>
 *   <li>{@link Ownership#SHARED} — cả hai phía sửa được ⇒ phải trộn ba chiều, hai phía cùng
 *       đổi khác nhau là xung đột thật.</li>
 *   <li>{@link Ownership#LOCAL_ONLY} — chỉ có nghĩa ở máy (khoá Room, cờ hàng đợi, cờ runtime
 *       của phiên POST). Không phải dữ liệu nghiệp vụ.</li>
 * </ul>
 *
 * <p>Khoá KHÔNG có trong bảng này và model cũng không biết ⇒ của server, giữ nguyên khi ghi,
 * không bao giờ tính là dữ liệu người dùng. Trường nghiệp vụ của model mà thiếu phân loại sẽ
 * làm {@code RefuelFieldOwnershipTest} đổ — cố ý, để không có trường nào rơi vào mặc định.
 */
public final class RefuelFieldOwnership {

    private RefuelFieldOwnership() {
    }

    public enum Ownership {
        CLIENT,
        SERVER,
        SHARED,
        LOCAL_ONLY
    }

    private static final Map<String, Ownership> REGISTRY = buildRegistry();

    private static Map<String, Ownership> buildRegistry() {
        Map<String, Ownership> map = new HashMap<>();

        // ---- Số liệu của mẻ: thiết bị và người vận hành là nguồn chuẩn ----
        for (String key : new String[]{
                "Status", "RealAmount", "Gallon", "Volume",
                "StartNumber", "EndNumber", "OriginalEndMeter",
                "StartTime", "EndTime", "DeviceStartTime", "DeviceEndTime",
                "Temperature", "ManualTemperature", "Density", "WaterSensor",
                "QualityNo", "WeightNote", "ReturnAmount", "ReturnUnit",
                "SaleNumber", "TicketNumber",
                "ApproachTime", "LeaveTime", "Completed",
                "Printed", "PrintStatus", "PrintTemplate", "IsSplit", "IsAlert",
                "ReceiptNumber", "ReceiptUniqueId", "ReceiptCount", "HasReview",
                "InvoiceNumber", "InvoiceNameCharter", "ReturnInvoiceNumber",
                "InvoiceFormId", "FormNo", "Sign", "ChangeFlag",
                "DriverId", "DriverName", "OperatorId", "OperatorName", "UserId",
                "BM2508Result", "BM2508BondingCable", "BM2508FuelingHose",
                "BM2508FuelingCap", "BM2508Ladder",
                "RefuelItemType"})
            map.put(key, Ownership.CLIENT);

        // ---- Kế hoạch bay: server sở hữu, app chỉ hiển thị ----
        for (String key : new String[]{
                "FlightId", "FlightUniqueId", "FlightCode", "FlightStatus",
                "ArrivalTime", "DepartureTime", "RefuelTime",
                "IsInternational", "IsDeleted", "SortOrder", "EstimateAmount",
                "ProjectedCapacity", "ActualCapacity", "Exported"})
            map.put(key, Ownership.SERVER);

        // ---- Hai phía cùng sửa được: phải trộn ba chiều ----
        // Bãi đỗ, số hiệu/loại tàu bay, hãng, chặng: điều độ sửa trên web, lái xe cũng sửa
        // được ngay trên RefuelDetailActivity và RefuelPreviewActivity.
        // Số xe: web điều động lại, app gán theo xe đang đăng nhập lúc chốt mẻ.
        // Giá, thuế, đơn vị, sản phẩm: server là danh mục gốc, nhưng app CÓ ghi
        // (setPrice 18 chỗ, setProductName 13, setTaxRate 10, setUnit 9, setCurrency 5).
        // Xếp SERVER thì hai phía cùng sửa sẽ không ai phát hiện.
        for (String key : new String[]{
                "ParkingLot", "AircraftCode", "AircraftType", "RouteName",
                "AirlineId", "AirlineModel", "TruckId", "TruckNo",
                "Price", "TaxRate", "Unit", "Currency",
                "ProductId", "ProductName", "ProductModel", "PCode", "PName"})
            map.put(key, Ownership.SHARED);

        // ---- Không phải dữ liệu nghiệp vụ ----
        for (String key : new String[]{
                "Id", "LocalId", "UniqueId", "ClientSeq", "ServerRevision",
                "IsLocalModified", "PostStatus", "DateUpdated", "TabletSerial",
                "Others", "Applied", "RejectReason"})
            map.put(key, Ownership.LOCAL_ONLY);

        return Collections.unmodifiableMap(map);
    }

    /** @return phân loại của khoá, hoặc null nếu chưa phân loại (kể cả khoá lạ của server). */
    public static Ownership of(String key) {
        return REGISTRY.get(key);
    }

    /** Vào vân tay precondition: nhóm client, và nhóm shared ở phần người dùng đã sửa. */
    public static boolean isClientFingerprintKey(String key) {
        Ownership ownership = REGISTRY.get(key);
        return ownership == Ownership.CLIENT;
    }

    public static boolean isServerOwned(String key) {
        Ownership ownership = REGISTRY.get(key);
        return ownership == Ownership.SERVER || ownership == Ownership.SHARED;
    }

    /** Nhóm phải trộn ba chiều khi lưu. */
    public static boolean isShared(String key) {
        return REGISTRY.get(key) == Ownership.SHARED;
    }

    public static Set<String> classifiedKeys() {
        return REGISTRY.keySet();
    }

    /**
     * Trường nghiệp vụ của model chưa được phân loại — phải RỖNG.
     *
     * <p>Dùng cho test chặn: thêm trường mới vào {@link RefuelItemData} mà quên phân loại thì
     * test đổ ngay, thay vì trường đó âm thầm được coi là của client (sinh conflict giả) hay
     * của server (bỏ lọt xung đột thật).
     */
    public static Set<String> unclassifiedModelKeys() {
        Set<String> missing = new HashSet<>(RefuelSyncGuard.modelKeys());
        missing.removeAll(REGISTRY.keySet());
        return missing;
    }
}
