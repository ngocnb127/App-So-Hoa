package com.megatech.fms.rut;

import androidx.annotation.Nullable;

/**
 * Ba sự cố khác nhau, ba cách xử lý khác nhau — nên là ba trạng thái, không phải một cờ.
 *
 * <p>Một cờ nhị phân "có SIM / không SIM" gộp mất đúng cái người dùng ngoài hiện trường cần
 * biết: có phải việc của họ hay không.
 * <ul>
 *   <li>{@link #NO_SIM} — router KHÔNG NHẬN thẻ: khay rỗng, hoặc có thẻ nhưng router không
 *       đọc được (cắm sai chiều, chân bẩn, thẻ hỏng). Việc tại chỗ: rút ra, lau, cắm lại.</li>
 *   <li>{@link #PIN_LOCKED} — router ĐỌC ĐƯỢC thẻ nhưng thẻ đang khoá PIN. Việc của quản
 *       trị, không phải việc rút cắm.</li>
 *   <li>{@link #SIM_PRESENT_NOT_REGISTERED} — thẻ tốt, modem không vào được mạng: hết tiền,
 *       hết dung lượng, ngoài vùng phủ. Rút cắm lại KHÔNG giải quyết được gì.</li>
 *   <li>{@link #SIM_REGISTERED} — ca bình thường.</li>
 *   <li>{@link #UNKNOWN} — router không nói. KHÔNG được hiển thị như một trong bốn ca trên;
 *       đoán ở đây là chỉ sai chỗ cho người đang đứng cạnh xe.</li>
 * </ul>
 */
public enum RutSimState {

    NO_SIM,
    PIN_LOCKED,
    SIM_PRESENT_NOT_REGISTERED,
    SIM_REGISTERED,
    UNKNOWN;

    /** Ca người dùng tự xử lý được ngay tại xe. */
    public boolean isFixableOnSite() {
        return this == NO_SIM;
    }

    /** Router đọc được thẻ (dù mạng ra sao). */
    public boolean hasSim() {
        return this == PIN_LOCKED || this == SIM_PRESENT_NOT_REGISTERED
                || this == SIM_REGISTERED;
    }

    /**
     * Ghép các mẩu router nói được thành một kết luận.
     *
     * <p>Mọi tham số đều {@code Boolean} nullable vì null = "router không nói" và null KHÔNG
     * được hiểu thành false: một firmware im lặng về đăng ký mạng không phải là một chiếc
     * SIM chưa đăng ký được.
     *
     * @param simPresent    router có đọc được thẻ hay không
     * @param pinLocked     thẻ đang chờ PIN/PUK
     * @param registered    modem đã đăng ký được vào mạng nhà mạng
     * @param dataConnected modem đã có phiên dữ liệu (bằng chứng mạnh nhất của "đã có mạng")
     */
    public static RutSimState classify(@Nullable Boolean simPresent,
                                       @Nullable Boolean pinLocked,
                                       @Nullable Boolean registered,
                                       @Nullable Boolean dataConnected) {
        // Có phiên dữ liệu thì mọi thứ bên dưới đã đúng, kể cả khi các field kia im lặng.
        if (Boolean.TRUE.equals(dataConnected)) return SIM_REGISTERED;
        if (Boolean.FALSE.equals(simPresent)) return NO_SIM;
        if (Boolean.TRUE.equals(pinLocked)) return PIN_LOCKED;
        if (Boolean.TRUE.equals(registered)) return SIM_REGISTERED;

        if (Boolean.TRUE.equals(simPresent)) {
            // Chỉ dám nói "chưa vào được mạng" khi router thực sự phủ định, không phải khi
            // router im lặng.
            if (Boolean.FALSE.equals(registered) || Boolean.FALSE.equals(dataConnected))
                return SIM_PRESENT_NOT_REGISTERED;
        }
        return UNKNOWN;
    }

    // ------------------------------------------------------------------ gắn vào model

    public static void attach(RutStatusUiModel model, RutSimState state) {
        if (model != null && state != null) model.simState = state;
    }

    public static RutSimState of(@Nullable RutStatusUiModel model) {
        return model == null || model.simState == null ? UNKNOWN : model.simState;
    }
}
