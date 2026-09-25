package com.megatech.fms.helpers;

import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.RefuelItemData;

/**
 * Điền sẵn phiếu BM 75.01 từ dữ liệu đã ghi nhận.
 *
 * <p>Mục đích: nhân viên chỉ phải nhập phần biểu mẫu yêu cầu mà app chưa biết
 * (mục A khách hàng khai, mục B kết quả KTCL, phương án xử lý ở mục C).
 *
 * <p>Hai nguồn kế thừa, theo thứ tự:
 * <ol>
 *   <li>Mẻ hút ({@link #fillMissing}) — chạy cả khi mở lại phiếu nháp, vì phiếu có thể được
 *       lập trước khi mẻ hút có đủ giờ kết thúc, số lượng, tỷ trọng.</li>
 *   <li>Phiên đăng nhập ({@link #fillSession}) — sân bay, họ tên đại diện SKYPEC.</li>
 *   <li>Phiếu trước của cùng hãng ({@link #fillCustomerContact}) — đại diện, chức danh,
 *       điện thoại, fax.</li>
 * </ol>
 *
 * <p>Mọi hàm chỉ điền vào ô còn trống: không bao giờ đè lên thứ nhân viên đã nhập.
 * Chỉ điền, KHÔNG lưu và KHÔNG đổi trạng thái — việc đó thuộc {@code BM7501Repository}.
 *
 * <p>Lưu ý đơn vị: mẻ hút lưu tỷ trọng theo <b>kg/l</b>, biểu mẫu yêu cầu <b>kg/m³</b>.
 * Quy đổi đặt tập trung ở {@link #densityToKgM3(double)} để không rải phép nhân 1000 khắp nơi.
 */
public final class BM7501Prefill {

    private BM7501Prefill() {
    }

    public static final double KG_PER_L_TO_KG_PER_M3 = 1000d;

    /** Loại nhiên liệu duy nhất cho tàu bay. */
    public static final String JET_A1 = "JET A-1";

    /** Quy đổi kg/l → kg/m³. Trả về null nếu chưa có tỷ trọng, để bản in ra dấu chấm. */
    public static Double densityToKgM3(double densityKgPerL) {
        if (densityKgPerL <= 0) return null;
        return densityKgPerL * KG_PER_L_TO_KG_PER_M3;
    }

    /**
     * Dựng phiếu mới từ một mẻ hút. Các trường của mục A/B mà mẻ hút không có sẽ để trống,
     * validator sẽ đòi khi nhân viên nhập.
     */
    public static BM7501Model fromRefuelItem(RefuelItemData item) {
        BM7501Model m = new BM7501Model();
        if (item == null) return m;

        m.setRefuelItemUniqueId(item.getUniqueId());

        // Số phiếu 75.01 dùng luôn số phiếu của mẻ hút: một mẻ một phiếu nên hai số trùng
        // nhau là đúng nghiệp vụ, và tránh phải cấp một dãy số thứ hai ngoài hiện trường.
        if (!isBlank(item.getReceiptNumber())) {
            m.setLocalNumber(item.getReceiptNumber().trim());
        }

        fillMissing(m, item);

        // Biểu mẫu ghi rõ ưu tiên bơm của tàu bay -> đặt sẵn, nhân viên vẫn sửa được.
        m.setMethod(BM7501Model.DefuelMethod.AIRCRAFT_PUMP);
        // Hai tín hiệu in sẵn trên biểu mẫu là tín hiệu chuẩn của quy trình.
        m.setSignalThumbUp(true);
        m.setSignalCrossArms(true);

        // Trường hợp bình thường chiếm gần hết số phiếu: đã xả mẫu, VAC/CWD đạt. Đặt sẵn để
        // nhân viên chỉ phải sửa khi có bất thường — vẫn sửa được (chốt 2026-09-23).
        m.setTankDrainSampled(Boolean.TRUE);
        m.setVac(BM7501Model.QcCheck.SATISFY);
        m.setCwd(BM7501Model.QcCheck.SATISFY);

        // SKYPEC chỉ cấp một loại nhiên liệu cho tàu bay nên hai sân bay trước cũng là JET A-1.
        m.setPrevGrade1(JET_A1);
        m.setPrevGrade2(JET_A1);
        return m;
    }

    /**
     * Điền bù từ mẻ hút vào những ô còn trống. Gọi mỗi lần mở phiếu còn sửa được.
     *
     * @return true nếu có ô được điền thêm (tầng gọi nên lưu lại)
     */
    public static boolean fillMissing(BM7501Model m, RefuelItemData item) {
        if (m == null || item == null) return false;
        boolean changed = false;

        if (m.getDate() == null) {
            m.setDate(item.getStartTime() != null ? item.getStartTime() : item.getRefuelTime());
            changed |= m.getDate() != null;
        }

        // Khoá liên kết: điền cả khi mở lại phiếu cũ, vì mẻ hút có thể vừa được server cấp id.
        if (m.getRefuelItemId() <= 0 && item.getId() != null && item.getId() > 0) {
            m.setRefuelItemId(item.getId());
            changed = true;
        }
        if (m.getFlightId() <= 0 && item.getFlightId() > 0) {
            m.setFlightId(item.getFlightId());
            changed = true;
        }
        if (isBlank(m.getFlightUniqueId()) && !isBlank(item.getFlightUniqueId())) {
            m.setFlightUniqueId(item.getFlightUniqueId());
            changed = true;
        }
        if (isBlank(m.getFlightCode()) && !isBlank(item.getFlightCode())) {
            m.setFlightCode(item.getFlightCode());
            changed = true;
        }

        if (m.getAirlineId() <= 0 && item.getAirlineId() > 0) {
            m.setAirlineId(item.getAirlineId());
            changed = true;
        }
        if (isBlank(m.getAirlineName()) && item.getAirlineModel() != null
                && !isBlank(item.getAirlineModel().getName())) {
            m.setAirlineName(item.getAirlineModel().getName());
            changed = true;
        }

        if (isBlank(m.getAircraftType()) && !isBlank(item.getAircraftType())) {
            m.setAircraftType(item.getAircraftType());
            changed = true;
        }
        if (isBlank(m.getAircraftReg()) && !isBlank(item.getAircraftCode())) {
            m.setAircraftReg(item.getAircraftCode());
            changed = true;
        }

        if (m.getTruckId() <= 0 && item.getTruckId() > 0) {
            m.setTruckId(item.getTruckId());
            changed = true;
        }
        if (isBlank(m.getDefuellerTruckNo()) && !isBlank(item.getTruckNo())) {
            m.setDefuellerTruckNo(item.getTruckNo());
            changed = true;
        }
        if (m.getStartTime() == null && item.getStartTime() != null) {
            m.setStartTime(item.getStartTime());
            changed = true;
        }
        if (m.getEndTime() == null && item.getEndTime() != null) {
            m.setEndTime(item.getEndTime());
            changed = true;
        }

        // Số liệu đo được của mẻ hút. Nhiệt độ: ưu tiên số nhập tay, rồi số đồng hồ.
        if (m.getActualTempC() == null) {
            double t = item.getManualTemperature() != 0 ? item.getManualTemperature() : item.getTemperature();
            if (t != 0) {
                m.setActualTempC(t);
                changed = true;
            }
        }
        if (m.getActualDensityKgM3() == null) {
            m.setActualDensityKgM3(densityToKgM3(item.getDensity()));
            changed |= m.getActualDensityKgM3() != null;
        }
        if (m.getGallon() == null && item.getRealAmount() > 0) {
            m.setGallon(item.getRealAmount());
            changed = true;
        }
        if (m.getLiter() == null && item.getVolume() > 0) {
            m.setLiter(item.getVolume());
            changed = true;
        }
        if (m.getActualKg() == null && item.getWeight() > 0) {
            m.setActualKg(item.getWeight());
            changed = true;
        }

        // Lượng dự kiến của mẻ hút tính theo USG, biểu mẫu hỏi theo kg -> chỉ quy đổi khi
        // đã có tỷ trọng, tránh điền một con số sai đơn vị vào chứng từ.
        Double densityKgM3 = m.getActualDensityKgM3();
        if (m.getExpectedKg() == null && item.getEstimateAmount() > 0 && densityKgM3 != null) {
            double liters = item.getEstimateAmount() * RefuelItemData.GALLON_TO_LITTER;
            m.setExpectedKg(liters * densityKgM3 / 1000d);
            changed = true;
        }
        return changed;
    }

    /** Điền sân bay và họ tên đại diện SKYPEC theo người đang đăng nhập. */
    public static boolean fillSession(BM7501Model m, int airportId, String airportName, String userName) {
        if (m == null) return false;
        boolean changed = false;
        if (m.getAirportId() <= 0 && airportId > 0) {
            m.setAirportId(airportId);
            changed = true;
        }
        if (isBlank(m.getAirportName()) && !isBlank(airportName)) {
            m.setAirportName(airportName.trim());
            changed = true;
        }
        if (isBlank(m.getSkypecRepName()) && !isBlank(userName)) {
            m.setSkypecRepName(userName.trim());
            changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
