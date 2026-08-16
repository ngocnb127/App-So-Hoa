package com.megatech.fms.helpers;

import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.RefuelItemData;

/**
 * Điền sẵn phiếu BM 75.01 từ mẻ hút.
 *
 * <p>Mục đích: nhân viên chỉ phải nhập phần biểu mẫu yêu cầu mà mẻ hút không có
 * (mục A khách hàng khai, mục B kết quả KTCL, phương án xử lý ở mục C).
 *
 * <p>Chỉ điền, KHÔNG lưu và KHÔNG đổi trạng thái — việc đó thuộc tầng repository (bước 2).
 *
 * <p>Lưu ý đơn vị: mẻ hút lưu tỷ trọng theo <b>kg/l</b>, biểu mẫu yêu cầu <b>kg/m³</b>.
 * Quy đổi đặt tập trung ở {@link #densityToKgM3(double)} để không rải phép nhân 1000 khắp nơi.
 */
public final class BM7501Prefill {

    private BM7501Prefill() {
    }

    public static final double KG_PER_L_TO_KG_PER_M3 = 1000d;

    /** Quy đổi kg/l → kg/m³. Trả về null nếu chưa có tỷ trọng, để bản in ra dấu chấm. */
    public static Double densityToKgM3(double densityKgPerL) {
        if (densityKgPerL <= 0) return null;
        return densityKgPerL * KG_PER_L_TO_KG_PER_M3;
    }

    /**
     * Dựng phiếu từ một mẻ hút. Các trường của mục A/B mà mẻ hút không có sẽ để trống,
     * validator sẽ đòi khi nhân viên nhập.
     */
    public static BM7501Model fromRefuelItem(RefuelItemData item) {
        BM7501Model m = new BM7501Model();
        if (item == null) return m;

        m.setRefuelItemUniqueId(item.getUniqueId());
        m.setDate(item.getStartTime() != null ? item.getStartTime() : item.getRefuelTime());

        // Số phiếu 75.01 dùng luôn số phiếu của mẻ hút: một mẻ một phiếu nên hai số trùng
        // nhau là đúng nghiệp vụ, và tránh phải cấp một dãy số thứ hai ngoài hiện trường.
        if (item.getReceiptNumber() != null && !item.getReceiptNumber().trim().isEmpty()) {
            m.setLocalNumber(item.getReceiptNumber().trim());
        }

        m.setAirlineId(item.getAirlineId());
        if (item.getAirlineModel() != null) {
            m.setAirlineName(item.getAirlineModel().getName());
        }

        m.setAircraftType(item.getAircraftType());
        m.setAircraftReg(item.getAircraftCode());

        m.setTruckId(item.getTruckId());
        m.setDefuellerTruckNo(item.getTruckNo());
        m.setStartTime(item.getStartTime());
        m.setEndTime(item.getEndTime());

        // Số liệu đo được của mẻ hút.
        m.setActualTempC(item.getManualTemperature() != 0 ? item.getManualTemperature() : null);
        m.setActualDensityKgM3(densityToKgM3(item.getDensity()));
        m.setGallon(item.getRealAmount() > 0 ? item.getRealAmount() : null);
        m.setLiter(item.getVolume() > 0 ? item.getVolume() : null);
        m.setActualKg(item.getWeight() > 0 ? item.getWeight() : null);

        // Lượng dự kiến của mẻ hút tính theo USG, biểu mẫu hỏi theo kg -> chỉ quy đổi khi
        // đã có tỷ trọng, tránh điền một con số sai đơn vị vào chứng từ.
        Double densityKgM3 = m.getActualDensityKgM3();
        if (item.getEstimateAmount() > 0 && densityKgM3 != null) {
            double liters = item.getEstimateAmount() * RefuelItemData.GALLON_TO_LITTER;
            m.setExpectedKg(liters * densityKgM3 / 1000d);
        }

        // Biểu mẫu ghi rõ ưu tiên bơm của tàu bay -> đặt sẵn, nhân viên vẫn sửa được.
        m.setMethod(BM7501Model.DefuelMethod.AIRCRAFT_PUMP);

        return m;
    }
}
