package com.megatech.fms.rut;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.megatech.fms.helpers.Logger;

/**
 * Điều phối popup trạng thái router.
 *
 * <p>Ba việc lớp này chịu trách nhiệm, đều là những chỗ đã hỏng thật ở các màn hình khác:
 * chống bấm trùng, bỏ kết quả của lần đọc đã cũ, và không giữ tham chiếu tới View.
 */
public final class RutStatusViewModel extends AndroidViewModel {

    private static final String LOG_TAG = "RUT";

    /** Theo dõi router lên lại tối đa 4 phút, giãn dần 5s → 10s → 15s. */
    @VisibleForTesting
    static final long[] REBOOT_POLL_DELAYS_MS = {5_000, 10_000, 15_000, 15_000, 15_000,
            20_000, 20_000, 30_000, 30_000, 30_000, 30_000, 30_000};

    /**
     * Đọc lại trạng thái sau khi dựng lại 4G, tổng khoảng 1 phút. Dừng ngay khi Internet đã
     * về; hết lượt thì hiện lần đọc cuối — chẩn đoán trong đó nói tiếp phải làm gì.
     */
    @VisibleForTesting
    static final long[] RESTART_POLL_DELAYS_MS = {10_000, 10_000, 10_000, 15_000, 15_000};

    private final MutableLiveData<RutStatusUiState> uiState = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RutRepository repository;
    @Nullable
    private DefaultRutRepository ownedRepository;

    /** Số thứ tự lần đọc; kết quả của lần cũ hơn bị bỏ, không cho ghi đè lần mới. */
    private int loadGeneration = 0;
    private boolean rebootInFlight = false;
    private int rebootPollIndex = 0;
    private boolean restartInFlight = false;
    private int restartPollIndex = 0;
    @Nullable
    private RutStatusUiModel lastKnown;

    public RutStatusViewModel(@NonNull Application application) {
        super(application);
        DefaultRutRepository owned = new DefaultRutRepository(application);
        this.repository = owned;
        this.ownedRepository = owned;
        uiState.setValue(RutStatusUiState.loading());
    }

    @VisibleForTesting
    public void setRepositoryForTesting(RutRepository repository) {
        if (ownedRepository != null) ownedRepository.close();
        ownedRepository = null;
        this.repository = repository;
    }

    /**
     * Quên router đã ghi nhớ rồi đo lại.
     *
     * <p>Lối thoát BẮT BUỘC phải có: ghép cặp nhầm một thiết bị khác, hoặc router bị cài
     * lại và đổi danh tính, đều khiến mọi lần đo sau đó bị từ chối vĩnh viễn. Không có nút
     * này thì cách duy nhất là xoá dữ liệu ứng dụng.
     */
    public void repair() {
        if (rebootInFlight || restartInFlight) return;
        if (ownedRepository != null) ownedRepository.forgetPairing();
        refresh();
    }

    /** Có đang ở ca "router trả lời nhưng không phải router đã ghép cặp" hay không. */
    public boolean needsRepair() {
        RutStatusUiState state = uiState.getValue();
        return state != null && state.content != null && state.content.wrongRouter;
    }

    public LiveData<RutStatusUiState> getUiState() {
        return uiState;
    }

    public void refresh() {
        if (rebootInFlight || restartInFlight) return;

        final int generation = ++loadGeneration;
        uiState.setValue(RutStatusUiState.loading());

        repository.loadStatus(new RutRepository.Callback<RutStatusUiModel>() {
            @Override
            public void onResult(RutStatusUiModel value) {
                if (generation != loadGeneration) return;   // lần đọc đã bị thay thế
                lastKnown = value;
                uiState.setValue(RutStatusUiState.content(value));
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                uiState.setValue(RutStatusUiState.error(message));
            }
        });
    }

    /** Chỉ gọi sau khi người dùng đã xác nhận trong hộp thoại. */
    public void reboot() {
        if (rebootInFlight || restartInFlight) return;   // chống bấm trùng
        if (!canReboot()) return;                   // chống gọi nhầm router lạ

        rebootInFlight = true;
        rebootPollIndex = 0;
        loadGeneration++;                           // bỏ mọi kết quả đọc đang bay
        uiState.setValue(RutStatusUiState.rebooting(lastKnown));

        repository.rebootRouter(new RutRepository.Callback<Void>() {
            @Override
            public void onResult(Void value) {
                Logger.appendLog(LOG_TAG, "Bắt đầu theo dõi router lên lại");
                scheduleRebootPoll();
            }

            @Override
            public void onError(String message) {
                // Mất kết nối ngay sau khi gửi là biểu hiện BÌNH THƯỜNG của reboot: router
                // tắt cổng mạng trước khi kịp trả lời. Không gửi lại lệnh, chỉ theo dõi.
                scheduleRebootPoll();
            }
        });
    }

    public boolean canReboot() {
        RutStatusUiState state = uiState.getValue();
        if (state == null || state.content == null) return false;
        return state.type == RutStatusUiState.Type.CONTENT
                && state.content.routerIdentityVerified
                && state.content.connectionState.allowsReboot();
    }

    /**
     * Dựng lại kết nối 4G mà không khởi động lại router.
     *
     * <p>Không hỏi xác nhận như reboot: nút chỉ hiện khi router ĐÃ mất Internet, nên không
     * còn gì để cắt; còn Wi-Fi của router thì lệnh này không đụng tới.
     */
    public void restartMobileConnection() {
        if (rebootInFlight || restartInFlight) return;   // chống bấm trùng
        if (!canRestartMobileConnection()) return;

        restartInFlight = true;
        restartPollIndex = 0;
        loadGeneration++;                                // bỏ mọi kết quả đọc đang bay
        uiState.setValue(RutStatusUiState.restartingMobile(lastKnown));

        repository.restartMobileConnection(new RutRepository.Callback<Void>() {
            @Override
            public void onResult(Void value) {
                if (!restartInFlight) return;
                scheduleRestartPoll();
            }

            @Override
            public void onError(String message) {
                // Lệnh không tới router thì không có gì để chờ; nói ngay.
                if (!restartInFlight) return;
                restartInFlight = false;
                uiState.setValue(RutStatusUiState.error(message));
            }
        });
    }

    /**
     * Chỉ mở khi đúng router, đang mất Internet, và chẩn đoán cho thấy dựng lại 4G có thể
     * giúp. Ca "router vẫn có Internet, chỉ máy chủ FMS không trả lời" tuyệt đối không mở:
     * cắt 4G lúc đó là tự làm mất mạng của cả xe mà không sửa được gì.
     */
    public boolean canRestartMobileConnection() {
        RutStatusUiState state = uiState.getValue();
        if (state == null || state.content == null) return false;
        RutStatusUiModel model = state.content;
        return state.type == RutStatusUiState.Type.CONTENT
                && model.routerIdentityVerified
                && model.connectionState == RutConnectionState.CONNECTED_TO_RUT_NO_INTERNET
                && model.modemId != null
                && (model.diagnosis == null || model.diagnosis.cause.restartMayHelp());
    }

    private void scheduleRestartPoll() {
        long delay = RESTART_POLL_DELAYS_MS[restartPollIndex++];
        handler.postDelayed(() -> repository.loadStatus(
                new RutRepository.Callback<RutStatusUiModel>() {
                    @Override
                    public void onResult(RutStatusUiModel value) {
                        if (!restartInFlight) return;
                        lastKnown = value;
                        boolean recovered =
                                value.connectionState == RutConnectionState.VERIFIED_THROUGH_RUT;
                        if (recovered || restartPollIndex >= RESTART_POLL_DELAYS_MS.length) {
                            restartInFlight = false;
                            uiState.setValue(RutStatusUiState.content(value));
                        } else {
                            uiState.setValue(RutStatusUiState.restartingMobile(value));
                            scheduleRestartPoll();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!restartInFlight) return;
                        if (restartPollIndex >= RESTART_POLL_DELAYS_MS.length) {
                            restartInFlight = false;
                            uiState.setValue(RutStatusUiState.error(message));
                        } else {
                            scheduleRestartPoll();
                        }
                    }
                }), delay);
    }

    private void scheduleRebootPoll() {
        if (rebootPollIndex >= REBOOT_POLL_DELAYS_MS.length) {
            // Hết thời gian theo dõi. KHÔNG gửi lại lệnh reboot: lệnh có thể đã chạy và
            // gửi lần hai là khởi động lại một router vừa mới lên.
            rebootInFlight = false;
            uiState.setValue(RutStatusUiState.error(
                    getApplication().getString(
                            com.megatech.fms.R.string.rut_error_reboot_timeout)));
            return;
        }

        long delay = REBOOT_POLL_DELAYS_MS[rebootPollIndex++];
        handler.postDelayed(() -> repository.pingRouter(new RutRepository.Callback<Boolean>() {
            @Override
            public void onResult(Boolean online) {
                if (!rebootInFlight) return;
                if (Boolean.TRUE.equals(online)) {
                    rebootInFlight = false;
                    refresh();                      // token cũ đã chết, refresh đăng nhập lại
                } else {
                    scheduleRebootPoll();
                }
            }

            @Override
            public void onError(String message) {
                if (rebootInFlight) scheduleRebootPoll();
            }
        }), delay);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacksAndMessages(null);
        loadGeneration++;
        rebootInFlight = false;
        restartInFlight = false;
        // Không đóng thì mỗi lần mở popup để lại một luồng nền sống mãi.
        if (ownedRepository != null) ownedRepository.close();
    }
}
