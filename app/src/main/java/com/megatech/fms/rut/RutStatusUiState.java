package com.megatech.fms.rut;

import androidx.annotation.Nullable;

/** Trạng thái màn hình popup. */
public final class RutStatusUiState {

    public enum Type { LOADING, CONTENT, ERROR, REBOOTING, RESTARTING_MOBILE }

    public final Type type;
    @Nullable public final RutStatusUiModel content;
    @Nullable public final String errorMessage;

    private RutStatusUiState(Type type, @Nullable RutStatusUiModel content,
                             @Nullable String errorMessage) {
        this.type = type;
        this.content = content;
        this.errorMessage = errorMessage;
    }

    public static RutStatusUiState loading() {
        return new RutStatusUiState(Type.LOADING, null, null);
    }

    public static RutStatusUiState content(RutStatusUiModel value) {
        return new RutStatusUiState(Type.CONTENT, value, null);
    }

    public static RutStatusUiState error(String message) {
        return new RutStatusUiState(Type.ERROR, null, message);
    }

    /** Đang khởi động lại: giữ lại dữ liệu cũ để popup không nhảy về trống trơn. */
    public static RutStatusUiState rebooting(@Nullable RutStatusUiModel lastKnown) {
        return new RutStatusUiState(Type.REBOOTING, lastKnown, null);
    }

    /** Đang dựng lại kết nối 4G: cũng giữ dữ liệu cũ như khi reboot. */
    public static RutStatusUiState restartingMobile(@Nullable RutStatusUiModel lastKnown) {
        return new RutStatusUiState(Type.RESTARTING_MOBILE, lastKnown, null);
    }

    public boolean isBusy() {
        return type == Type.LOADING || type == Type.REBOOTING
                || type == Type.RESTARTING_MOBILE;
    }
}
