package com.megatech.fms.helpers;

import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.AdditivePresence;
import com.megatech.fms.model.BM7501Model.DefuelReason;
import com.megatech.fms.model.BM7501Model.HandlingOption;
import com.megatech.fms.model.BM7501Model.TriState;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation cho BM 75.01, bám theo đúng biểu mẫu giấy.
 *
 * <p>Chia theo bước nhập thực tế ngoài hiện trường, không theo thứ tự trang giấy:
 * mục A trước khi hút → mục B khi có kết quả KTCL → mục C sau khi hút → ký.
 *
 * <p>Những điểm cố ý KHÁC bản phương án v1 (đã bị review bác):
 * <ul>
 *   <li>Vi sinh mục A chỉ bắt buộc khi hãng đã thực hiện kiểm tra ({@code YES}).</li>
 *   <li>Vi sinh mục B lấy điều kiện VAC/CWD không đạt · nghi ngờ · khách yêu cầu —
 *       KHÔNG suy từ A10.</li>
 *   <li>C10 bắt buộc khi không nạp lại ngay được HOẶC có vấn đề chất lượng —
 *       không chỉ khi C9 = NO.</li>
 *   <li>Nhiệt độ/KLR không có ngưỡng chặn cứng; ngoài khoảng chỉ là {@link Severity#WARNING}.</li>
 *   <li>Ràng buộc thời gian mẻ 3–180 phút KHÔNG sao chép vào đây — đó là rule của mẻ hút.</li>
 * </ul>
 */
public final class BM7501Validator {

    private BM7501Validator() {
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    /** Một phát hiện của validator, gắn với tên trường để UI focus đúng ô. */
    public static class Finding {
        private final Severity severity;
        private final String field;
        private final String message;

        public Finding(Severity severity, String field, String message) {
            this.severity = severity;
            this.field = field;
            this.message = message;
        }

        public Severity getSeverity() { return severity; }
        public String getField() { return field; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return severity + "/" + field + ": " + message;
        }
    }

    public static boolean hasError(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.getSeverity() == Severity.ERROR) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ MỤC A

    public static List<Finding> validateSectionA(BM7501Model m) {
        List<Finding> out = new ArrayList<>();
        if (m == null) return out;

        if (isBlank(m.getCustomerRepName())) {
            error(out, "customerRepName", "Chưa nhập họ tên đại diện hãng hàng không");
        }
        if (isBlank(m.getAircraftType())) {
            error(out, "aircraftType", "Chưa nhập loại tàu bay");
        }
        if (isBlank(m.getAircraftReg())) {
            error(out, "aircraftReg", "Chưa nhập số hiệu tàu bay");
        }

        // A9 — lý do hút
        if (m.getReason() == null) {
            error(out, "reason", "Chưa chọn lý do hút");
        } else if (m.getReason() == DefuelReason.OTHER && isBlank(m.getReasonOther())) {
            error(out, "reasonOther", "Chọn lý do khác thì phải ghi rõ");
        }

        // A10 — đã xả tất cả thùng lấy mẫu KTCL
        if (m.getTankDrainSampled() == null) {
            error(out, "tankDrainSampled",
                    "Chưa trả lời: trước khi hút đã xả tất cả thùng để lấy mẫu KTCL chưa");
        }

        // A11/A12 — vi sinh chỉ bắt buộc khi hãng ĐÃ thực hiện kiểm tra
        if (m.getCustomerMicrobialTestPerformed() == null) {
            error(out, "customerMicrobialTestPerformed",
                    "Chưa trả lời: hãng đã kiểm tra vi sinh chưa");
        } else if (m.getCustomerMicrobialTestPerformed() == TriState.YES) {
            if (m.getCustomerMicrobialKit() == null) {
                error(out, "customerMicrobialKit", "Chưa chọn thiết bị kiểm tra vi sinh");
            } else if (m.getCustomerMicrobialKit() == BM7501Model.MicrobialKit.OTHER
                    && isBlank(m.getCustomerMicrobialKitOther())) {
                error(out, "customerMicrobialKitOther", "Chọn thiết bị khác thì phải ghi rõ");
            }
            if (m.getCustomerMicrobialResult() == null) {
                error(out, "customerMicrobialResult", "Chưa chọn kết quả kiểm tra vi sinh");
            }
        }

        // A13 — NONE/UNDETERMINED loại trừ mọi phụ gia cụ thể
        if (m.getAdditivePresence() == null) {
            error(out, "additivePresence", "Chưa chọn thông tin phụ gia");
        } else if (m.getAdditivePresence() == AdditivePresence.PRESENT) {
            if (m.getAdditives() == null || m.getAdditives().isEmpty()) {
                error(out, "additives", "Đã chọn có phụ gia nhưng chưa chọn loại nào");
            }
        } else if (m.getAdditives() != null && !m.getAdditives().isEmpty()) {
            error(out, "additives",
                    "Đã chọn \"không sử dụng\"/\"không xác định được\" thì không được chọn phụ gia cụ thể");
        }

        // A14 — 2 sân bay nạp trước đó; cho phép ghi "Không xác định được"
        if (isBlank(m.getPrevLocation1())) error(out, "prevLocation1", "Chưa nhập sân bay thứ 1");
        if (isBlank(m.getPrevGrade1())) error(out, "prevGrade1", "Chưa nhập loại nhiên liệu sân bay thứ 1");
        if (isBlank(m.getPrevLocation2())) error(out, "prevLocation2", "Chưa nhập sân bay thứ 2");
        if (isBlank(m.getPrevGrade2())) error(out, "prevGrade2", "Chưa nhập loại nhiên liệu sân bay thứ 2");

        return out;
    }

    // ------------------------------------------------------------------ MỤC B

    public static List<Finding> validateSectionB(BM7501Model m) {
        List<Finding> out = new ArrayList<>();
        if (m == null) return out;

        if (m.getVac() == null) error(out, "vac", "Chưa chọn kết quả kiểm tra ngoại quan (VAC)");
        if (m.getCwd() == null) error(out, "cwd", "Chưa chọn kết quả viên thử nước (CWD)");

        if (m.getDensityKgM3() == null) {
            error(out, "densityKgM3", "Chưa nhập kết quả kiểm tra KLR (kg/m3)");
        } else {
            checkDensity(out, "densityKgM3", m.getDensityKgM3());
        }

        if (m.isConductivityRequired() && m.getConductivityPsM() == null) {
            error(out, "conductivityPsM", "Có yêu cầu đo độ dẫn điện nhưng chưa nhập kết quả (pS/m)");
        }

        // Vi sinh mục B: điều kiện đúng theo biểu mẫu (VAC/CWD không đạt · nghi ngờ · khách yêu cầu)
        if (m.isSkypecMicrobialRequired()) {
            if (m.getSkypecMicrobialKit() == null) {
                error(out, "skypecMicrobialKit",
                        "Trường hợp này bắt buộc kiểm tra vi sinh: chưa chọn thiết bị");
            }
            if (m.getSkypecMicrobialResult() == null) {
                error(out, "skypecMicrobialResult",
                        "Trường hợp này bắt buộc kiểm tra vi sinh: chưa chọn kết quả");
            }
            if (isBlank(m.getMicrobialReason())) {
                error(out, "microbialReason", "Chưa ghi lý do phải kiểm tra vi sinh");
            }
        }

        return out;
    }

    // ------------------------------------------------------------------ MỤC C

    public static List<Finding> validateSectionC(BM7501Model m) {
        List<Finding> out = new ArrayList<>();
        if (m == null) return out;

        if (isBlank(m.getDefuellerTruckNo())) {
            error(out, "defuellerTruckNo", "Chưa có phương tiện hút");
        }
        if (m.getStartTime() == null) error(out, "startTime", "Chưa có giờ bắt đầu hút");
        if (m.getEndTime() == null) error(out, "endTime", "Chưa có giờ kết thúc hút");
        if (m.getStartTime() != null && m.getEndTime() != null
                && m.getEndTime().before(m.getStartTime())) {
            error(out, "endTime", "Giờ kết thúc phải sau giờ bắt đầu");
        }

        if (m.getMethod() == null) error(out, "method", "Chưa chọn phương thức hút");

        // C4 — hai tín hiệu chuẩn là nội dung hướng dẫn; chỉ cần xác nhận đã phổ biến.
        if (!m.isSignalsBriefed() && isBlank(m.getOtherSignal())) {
            error(out, "signalsBriefed",
                    "Chưa xác nhận đã phổ biến/thống nhất phương thức ra tín hiệu");
        }

        if (m.getExpectedKg() == null) error(out, "expectedKg", "Chưa nhập lượng hút dự kiến (kg)");
        if (m.getActualKg() == null) error(out, "actualKg", "Chưa nhập lượng hút thực tế (kg)");

        if (m.getActualTempC() == null) {
            error(out, "actualTempC", "Chưa nhập nhiệt độ nhiên liệu đo thực tế");
        } else {
            checkTemp(out, "actualTempC", m.getActualTempC());
        }

        if (m.getActualDensityKgM3() == null) {
            error(out, "actualDensityKgM3", "Chưa nhập KLR đo thực tế (kg/m3)");
        } else {
            checkDensity(out, "actualDensityKgM3", m.getActualDensityKgM3());
        }

        if (m.getGallon() == null) error(out, "gallon", "Chưa có quy đổi Gal");
        if (m.getLiter() == null) error(out, "liter", "Chưa có quy đổi Lít");

        // C9
        if (m.getRefuellableWithoutTest() == null) {
            error(out, "refuellableWithoutTest",
                    "Chưa trả lời: nhiên liệu hút ra có nạp lại được cho tàu bay cùng hãng không");
        }

        // C10 — bắt buộc khi KHÔNG nạp lại ngay được HOẶC có vấn đề chất lượng
        if (m.isHandlingRequired()) {
            if (m.getHandling() == null) {
                error(out, "handling", "Phải chọn phương án xử lý lượng nhiên liệu đã hút");
            } else if (m.getHandling() == HandlingOption.STORAGE) {
                if (m.getStorageFrom() == null) error(out, "storageFrom", "Chưa nhập thời điểm lưu trữ từ");
                if (m.getStorageTo() == null) error(out, "storageTo", "Chưa nhập thời điểm lưu trữ đến");
                if (m.getStorageFrom() != null && m.getStorageTo() != null
                        && m.getStorageTo().before(m.getStorageFrom())) {
                    error(out, "storageTo", "Thời điểm \"đến\" phải sau \"từ\"");
                }
            } else if (m.getHandling() == HandlingOption.REFUEL_DESPITE_ISSUE
                    && isBlank(m.getHandlingNote())) {
                error(out, "handlingNote",
                        "Xác nhận nạp lại dù nhiên liệu có vấn đề thì phải ghi rõ vấn đề");
            }
        }

        return out;
    }

    // ------------------------------------------------------------- trước khi ký

    /**
     * Kiểm tra toàn phiếu trước khi ký: A + B + C + ba chữ ký + họ tên hai bên.
     *
     * <p>Ba chữ ký theo quyết định đã chốt: khách hàng xác nhận mục A, SKYPEC ký cuối,
     * khách hàng ký cuối.
     */
    public static List<Finding> validateForSigning(BM7501Model m) {
        List<Finding> out = new ArrayList<>();
        if (m == null) return out;

        out.addAll(validateSectionA(m));
        out.addAll(validateSectionB(m));
        out.addAll(validateSectionC(m));

        if (isBlank(m.getCustomerSectionASignaturePath())) {
            error(out, "customerSectionASignaturePath",
                    "Thiếu chữ ký đại diện hãng xác nhận mục A");
        }
        if (isBlank(m.getSkypecSignaturePath())) {
            error(out, "skypecSignaturePath", "Thiếu chữ ký đại diện SKYPEC");
        }
        if (isBlank(m.getCustomerFinalSignaturePath())) {
            error(out, "customerFinalSignaturePath", "Thiếu chữ ký xác nhận cuối của khách hàng");
        }

        if (isBlank(m.getSkypecRepName())) {
            error(out, "skypecRepName", "Chưa ghi rõ họ tên đại diện SKYPEC");
        }
        if (isBlank(m.getCustomerRepFinalName())) {
            error(out, "customerRepFinalName", "Chưa ghi rõ họ tên đại diện khách hàng");
        }

        return out;
    }

    // ------------------------------------------------------------------ nội bộ

    private static void checkTemp(List<Finding> out, String field, double tempC) {
        if (BM7501Thresholds.isTempInRange(tempC)) return;
        String msg = String.format(
                "Nhiệt độ %.1f °C nằm ngoài khoảng tham chiếu %.0f..%.0f °C, kiểm tra lại",
                tempC, BM7501Thresholds.getMinTempC(), BM7501Thresholds.getMaxTempC());
        out.add(new Finding(
                BM7501Thresholds.isBlocking() ? Severity.ERROR : Severity.WARNING, field, msg));
    }

    private static void checkDensity(List<Finding> out, String field, double densityKgM3) {
        if (densityKgM3 <= 0) {
            error(out, field, "KLR phải là số dương");
            return;
        }
        if (BM7501Thresholds.isDensityInRange(densityKgM3)) return;
        String msg = String.format(
                "KLR %.1f kg/m3 nằm ngoài khoảng tham chiếu %.0f..%.0f kg/m3, kiểm tra lại",
                densityKgM3, BM7501Thresholds.getMinDensityKgM3(), BM7501Thresholds.getMaxDensityKgM3());
        out.add(new Finding(
                BM7501Thresholds.isBlocking() ? Severity.ERROR : Severity.WARNING, field, msg));
    }

    private static void error(List<Finding> out, String field, String message) {
        out.add(new Finding(Severity.ERROR, field, message));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Tiện ích cho UI: bỏ qua cảnh báo, chỉ lấy lỗi chặn. */
    public static List<Finding> errorsOnly(List<Finding> findings) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) {
            if (f.getSeverity() == Severity.ERROR) out.add(f);
        }
        return out;
    }
}
