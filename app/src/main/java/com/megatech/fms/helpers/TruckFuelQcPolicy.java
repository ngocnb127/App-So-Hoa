package com.megatech.fms.helpers;

import com.megatech.fms.model.TruckFuelModel;

import java.util.Date;
import java.util.List;

/**
 * Số phiếu hoá nghiệm điền sẵn cho các chuyến chưa có số.
 *
 * <p>Luật chủ dự án chốt ngày 2026-09-16: số dùng cho các chuyến tiếp theo LUÔN là số của
 * phiếu 2502 <b>mới nhất còn lại</b> của xe, không phải số của phiếu vừa được lưu. Trước đây
 * app ghi số sau mọi lần lưu, nên sửa một phiếu cũ (8h) sau khi đã có phiếu mới hơn (14h) làm
 * các chuyến sau nhận lại số của phiếu 8h. Cùng một luật cũng trả lời việc xoá: xoá phiếu mới
 * nhất thì số quay về phiếu còn lại mới nhất.
 *
 * <p>Không có phiếu nào dùng được thì trả {@code null} — bên gọi GIỮ NGUYÊN số hiện hành, không
 * xoá. Xoá số sẽ chặn màn hình xem trước (số phiếu hoá nghiệm là trường bắt buộc) trong khi
 * trong máy có thể chỉ còn thiếu dữ liệu, không phải thiếu phiếu thật.
 */
public final class TruckFuelQcPolicy {

    private TruckFuelQcPolicy() {
    }

    /**
     * @param forms  toàn bộ phiếu 2502 đang có trong máy
     * @param truckId xe hiện tại; {@code <= 0} thì không lọc theo xe
     * @return số phiếu hoá nghiệm cho các chuyến tiếp theo, hoặc {@code null} nếu không đổi
     */
    public static String qcNoForNextFlights(List<TruckFuelModel> forms, int truckId) {
        if (forms == null)
            return null;

        TruckFuelModel latest = null;
        for (TruckFuelModel form : forms) {
            if (form == null || form.isDeleted())
                continue;
            if (truckId > 0 && form.getTruckId() != truckId)
                continue;
            Date time = form.getTime();
            if (time == null)
                continue;
            if (latest == null || time.after(latest.getTime()))
                latest = form;
        }

        if (latest == null)
            return null;
        String qcNo = latest.getQcNo();
        if (qcNo == null || qcNo.trim().isEmpty())
            return null;
        return qcNo.trim();
    }
}
