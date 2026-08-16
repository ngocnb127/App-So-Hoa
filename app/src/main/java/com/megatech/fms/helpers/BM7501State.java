package com.megatech.fms.helpers;

import com.megatech.fms.model.BM7501Model.BusinessStatus;

/**
 * Luật chuyển trạng thái nghiệp vụ của BM 75.01.
 *
 * <p>Theo quyết định đã chốt (docs/PLAN-BM7501-HUT-NHIEN-LIEU.md §14.1):
 * <pre>
 *   DRAFT / A_DONE / B_DONE / C_DONE  →  CANCELLED   (hủy TRƯỚC khi ký)
 *   SIGNED / PRINTED                  →  VOIDED      (vô hiệu SAU khi ký/in)
 * </pre>
 *
 * <p>{@code CANCELLED} không bao giờ quay lại {@code SIGNED} — muốn làm lại thì tạo
 * revision mới. {@code VOIDED} là trạng thái cuối.
 *
 * <p>Lưu ý: {@code A_DONE}/{@code B_DONE}/{@code C_DONE} chỉ được đặt khi người dùng xác nhận
 * hoàn tất bước và validation của bước đó đạt — autosave KHÔNG đổi trạng thái.
 */
public final class BM7501State {

    private BM7501State() {
    }

    /**
     * Cho phép chuyển trạng thái hay không.
     *
     * <p>Trong nhóm còn sửa được (DRAFT..C_DONE) cho phép đi tới và quay lui, vì nhân viên
     * hiện trường thường phải mở lại bước trước để sửa số liệu.
     */
    public static boolean canTransition(BusinessStatus from, BusinessStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;

        // Trạng thái cuối: không đi đâu được nữa.
        if (from.isTerminal()) return false;

        switch (to) {
            case DRAFT:
            case A_DONE:
            case B_DONE:
            case C_DONE:
                // Chỉ quanh quẩn trong nhóm còn sửa được; đã ký/in thì không quay lại.
                return from.isEditable();

            case SIGNED:
                return from == BusinessStatus.C_DONE;

            case PRINTED:
                return from == BusinessStatus.SIGNED;

            case CANCELLED:
                return from.isEditable();

            case VOIDED:
                return from == BusinessStatus.SIGNED || from == BusinessStatus.PRINTED;

            default:
                return false;
        }
    }

    /**
     * Bước tiếp theo trong luồng thuận, null nếu không có.
     * Dùng cho wizard 3 bước trên UI.
     */
    public static BusinessStatus next(BusinessStatus current) {
        if (current == null) return BusinessStatus.DRAFT;
        switch (current) {
            case DRAFT:  return BusinessStatus.A_DONE;
            case A_DONE: return BusinessStatus.B_DONE;
            case B_DONE: return BusinessStatus.C_DONE;
            case C_DONE: return BusinessStatus.SIGNED;
            case SIGNED: return BusinessStatus.PRINTED;
            default:     return null;
        }
    }

    /** Trạng thái hủy đúng ngữ nghĩa cho một phiếu đang ở {@code current}. */
    public static BusinessStatus cancellationTargetFor(BusinessStatus current) {
        if (current == null) return null;
        if (current.isEditable()) return BusinessStatus.CANCELLED;
        if (current == BusinessStatus.SIGNED || current == BusinessStatus.PRINTED) {
            return BusinessStatus.VOIDED;
        }
        return null;
    }
}
