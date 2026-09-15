package com.megatech.fms.rut;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.megatech.fms.R;
import com.megatech.fms.helpers.DateUtils;

import java.util.Date;

/**
 * Popup trạng thái router RUT.
 *
 * <p>Mục đích nghiệp vụ: trả lời đúng một câu hỏi mà trước đây không ai trả lời được ngoài
 * hiện trường — "máy này đang chạy Internet của xe hay đang tiêu 4G cá nhân?". Gọi được API
 * router mới chỉ chứng minh vào được LAN của nó; dòng "Đang sử dụng / Không sử dụng" trong
 * khối "Xác minh" mới là câu trả lời đầy đủ.
 *
 * <p>Bản rút gọn: mỗi khối chỉ còn MỘT dòng đọc là hiểu — số liệu kỹ thuật chi tiết
 * (RSRP/RSRQ/SINR, ba dòng dấu tích) đã bỏ theo yêu cầu người dùng.
 *
 * <p>Mở bằng {@link #show(androidx.fragment.app.FragmentManager)}.
 */
public final class RutStatusBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "RutStatusBottomSheet";

    private RutStatusViewModel viewModel;

    private ProgressBar progress;
    private View content;
    private TextView statusPill;
    private TextView message;
    private TextView infoValue;
    private TextView signalValue;
    private TextView signalRating;
    private TextView verifyValue;
    private TextView updatedAt;
    private MaterialButton refreshButton;
    private MaterialButton restartMobileButton;
    private MaterialButton rebootButton;
    private MaterialButton repairButton;
    private View[] signalBars;

    public static RutStatusBottomSheet newInstance() {
        return new RutStatusBottomSheet();
    }

    public void show(androidx.fragment.app.FragmentManager manager) {
        show(manager, TAG);
    }

    /**
     * Theme riêng cho popup: app đang chạy Theme.AppCompat, mà MaterialButton/MaterialCardView
     * đòi theme Material. Cấp theme ở đây thay vì đổi theme gốc của app — đổi theme gốc là
     * chạm vào mọi màn hình đã ổn định để phục vụ đúng một popup.
     */
    @Override
    public int getTheme() {
        return R.style.RutBottomSheetTheme;
    }

    /**
     * Mở sẵn ở trạng thái đã bung.
     *
     * <p>Mặc định bottom sheet mở ở ~50% chiều cao; trên máy thấp, ba nút cuối nằm dưới mép
     * và người dùng phải đoán ra là kéo lên được. Popup này là một bản báo cáo đọc từ trên
     * xuống, không phải một tấm chọn nhanh.
     */
    @Override
    public void onStart() {
        super.onStart();
        View sheet = getDialog() == null ? null
                : getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_rut_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);

        viewModel = new ViewModelProvider(this).get(RutStatusViewModel.class);

        refreshButton.setOnClickListener(v -> viewModel.refresh());
        restartMobileButton.setOnClickListener(v -> viewModel.restartMobileConnection());
        rebootButton.setOnClickListener(v -> showRebootConfirmation());
        repairButton.setOnClickListener(v -> showRepairConfirmation());
        view.findViewById(R.id.rut_button_close).setOnClickListener(v -> dismiss());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        if (savedInstanceState == null) viewModel.refresh();
    }

    private void bindViews(View view) {
        progress = view.findViewById(R.id.rut_progress);
        content = view.findViewById(R.id.rut_content);
        statusPill = view.findViewById(R.id.rut_status_pill);
        message = view.findViewById(R.id.rut_message);
        infoValue = view.findViewById(R.id.rut_info_value);
        signalValue = view.findViewById(R.id.rut_signal_value);
        signalRating = view.findViewById(R.id.rut_signal_rating);
        verifyValue = view.findViewById(R.id.rut_verify_value);
        updatedAt = view.findViewById(R.id.rut_updated_at);
        refreshButton = view.findViewById(R.id.rut_button_refresh);
        restartMobileButton = view.findViewById(R.id.rut_button_restart_mobile);
        rebootButton = view.findViewById(R.id.rut_button_reboot);
        repairButton = view.findViewById(R.id.rut_button_repair);
        signalBars = new View[]{
                view.findViewById(R.id.rut_bar_1),
                view.findViewById(R.id.rut_bar_2),
                view.findViewById(R.id.rut_bar_3),
                view.findViewById(R.id.rut_bar_4)};
    }

    // ------------------------------------------------------------------ hiển thị

    private void render(RutStatusUiState state) {
        boolean busy = state.isBusy();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        content.setVisibility(state.content != null ? View.VISIBLE : View.GONE);

        refreshButton.setEnabled(!busy);
        restartMobileButton.setVisibility(
                viewModel.canRestartMobileConnection() ? View.VISIBLE : View.GONE);
        rebootButton.setEnabled(!busy && viewModel.canReboot());
        repairButton.setVisibility(viewModel.needsRepair() ? View.VISIBLE : View.GONE);
        repairButton.setEnabled(!busy);

        switch (state.type) {
            case LOADING:
                setPill(R.string.rut_state_checking, R.color.rutNeutral, R.color.rutNeutralSurface);
                showMessage(null);
                break;
            case REBOOTING:
                setPill(R.string.rut_state_rebooting, R.color.rutWarn, R.color.rutWarnSurface);
                // Nội dung phía trên vẫn là ảnh chụp TRƯỚC khi reboot — dấu tích xanh trong
                // khi router đang tắt sẽ đọc như số liệu đang sống. Nói rõ nó là cũ.
                showMessage(getString(R.string.rut_reboot_in_progress)
                        + (state.content == null ? ""
                        : "\n" + getString(R.string.rut_stale_data)));
                break;
            case RESTARTING_MOBILE:
                setPill(R.string.rut_state_restarting_mobile, R.color.rutWarn,
                        R.color.rutWarnSurface);
                showMessage(getString(R.string.rut_restart_mobile_in_progress)
                        + (state.content == null ? ""
                        : "\n" + getString(R.string.rut_stale_data_restart)));
                break;
            case ERROR:
                setPill(R.string.rut_state_unreachable, R.color.rutDanger, R.color.rutDangerSurface);
                showMessage(state.errorMessage);
                break;
            case CONTENT:
                renderContent(state.content);
                break;
        }
    }

    private void renderContent(RutStatusUiModel model) {
        renderPill(model);

        infoValue.setText(describeInfo(model));

        renderSignal(model);
        renderVerification(model);

        updatedAt.setText(describeUpdatedAt(model.lastUpdatedEpochMs));
        showMessage(guidanceFor(model));
    }

    private void renderPill(RutStatusUiModel model) {
        switch (model.connectionState) {
            case VERIFIED_THROUGH_RUT:
                // Người dùng không quan tâm "qua RUT" nghĩa là gì; điều họ cần biết là máy
                // đang bám vào xe nào — nên hiện luôn tên Wi-Fi khi đọc được.
                setPill(model.wifiSsid == null
                                ? getString(R.string.rut_connected_to_truck)
                                : getString(R.string.rut_connected_to_truck_named, model.wifiSsid),
                        R.color.rutOk, R.color.rutOkSurface);
                break;
            case CONNECTED_TO_RUT_NO_INTERNET:
                // Router vẫn ra mạng thì câu "Router mất Internet" là sai — và dẫn người
                // dùng tới việc khởi động lại một router đang chạy tốt.
                setPill(causeOf(model) == RutInternetDiagnosis.Cause.APP_SERVER_UNREACHABLE
                                ? R.string.rut_state_server_unreachable
                                : R.string.rut_state_no_internet,
                        R.color.rutWarn, R.color.rutWarnSurface);
                break;
            case PHONE_USING_CELLULAR:
                setPill(R.string.rut_state_phone_cellular, R.color.rutWarn, R.color.rutWarnSurface);
                break;
            case NOT_CONNECTED_TO_RUT:
                setPill(R.string.rut_state_not_connected, R.color.rutDanger, R.color.rutDangerSurface);
                break;
            case AUTHENTICATION_FAILED:
                setPill(R.string.rut_state_auth_failed, R.color.rutDanger, R.color.rutDangerSurface);
                break;
            case ROUTER_UNREACHABLE:
                setPill(R.string.rut_state_unreachable, R.color.rutDanger, R.color.rutDangerSurface);
                break;
            case CHECKING:
                setPill(R.string.rut_state_checking, R.color.rutNeutral, R.color.rutNeutralSurface);
                break;
            default:
                setPill(R.string.rut_state_unknown, R.color.rutNeutral, R.color.rutNeutralSurface);
                break;
        }
    }

    /**
     * Một dòng duy nhất cho cả khối "Thông tin RUT": "Đã kết nối : 4G - Viettel - SIM 1".
     *
     * <p>Thành phần nào router không trả lời thì BỎ HẲN khỏi câu. Chèn "—" vào giữa dòng
     * biến một câu đọc được thành một bảng số liệu khuyết chỗ, mà thứ khuyết đó cũng chẳng
     * nói thêm được gì cho người đang đứng cạnh xe.
     */
    private String describeInfo(RutStatusUiModel model) {
        // Router không nói gì thì hiện "—". Ghi "Chưa kết nối" ở đây là khẳng định một điều
        // ta chưa đo được, và người dùng sẽ đi tìm một sự cố mạng không có thật.
        if (model.mobileConnected == null) return getString(R.string.rut_no_value);

        String status = getString(model.mobileConnected
                ? R.string.rut_connected : R.string.rut_disconnected);

        StringBuilder details = new StringBuilder();
        append(details, model.networkType);
        append(details, model.operatorName);
        append(details, describeSim(model));

        return details.length() == 0 ? status
                : getString(R.string.rut_labeled_value, status, details.toString());
    }

    /**
     * Phần SIM của dòng thông tin.
     *
     * <p>Ba sự cố, ba câu khác nhau — vì ba cách xử lý ngoài hiện trường khác nhau: không
     * nhận thẻ thì rút ra cắm lại, khoá PIN thì gọi quản trị, có thẻ mà chưa vào được mạng
     * thì rút cắm bao nhiêu lần cũng vô ích. Gộp cả ba thành "SIM lỗi" là đẩy người dùng vào
     * việc thử mò.
     */
    @Nullable
    private String describeSim(RutStatusUiModel model) {
        switch (model.simState) {
            case NO_SIM:
                return getString(R.string.rut_no_sim);
            case PIN_LOCKED:
                return getString(R.string.rut_sim_pin_locked);
            case SIM_PRESENT_NOT_REGISTERED:
                return model.activeSimSlot == null
                        ? getString(R.string.rut_sim_no_network)
                        : getString(R.string.rut_sim_slot, model.activeSimSlot)
                        + getString(R.string.rut_value_separator)
                        + getString(R.string.rut_sim_no_network);
            default:
                return model.activeSimSlot == null ? null
                        : getString(R.string.rut_sim_slot, model.activeSimSlot);
        }
    }

    private void append(StringBuilder builder, @Nullable String part) {
        if (part == null || part.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append(getString(R.string.rut_value_separator));
        builder.append(part.trim());
    }

    /**
     * Cường độ sóng gọn còn một dòng chữ + vạch sóng + số dBm. RSRP/RSRQ/SINR đã bỏ: đó là
     * số liệu để kỹ sư viễn thông đọc, không phải để quyết định có tra nạp được hay không.
     */
    private void renderSignal(RutStatusUiModel model) {
        Integer primary = model.primarySignalDbm();
        SignalRating rating = model.signalRating();

        if (primary == null) {
            signalRating.setText(getString(R.string.rut_no_signal));
            signalValue.setText("");
            signalValue.setVisibility(View.GONE);
        } else {
            signalRating.setText(getString(R.string.rut_signal_prefix, labelOf(rating)));
            signalValue.setText(getString(R.string.rut_dbm, primary));
            signalValue.setVisibility(View.VISIBLE);
        }

        int filled = primary == null ? 0 : rating.bars();
        int active = ContextCompat.getColor(requireContext(), colorOf(rating));
        int empty = ContextCompat.getColor(requireContext(), R.color.rutBarEmpty);
        for (int i = 0; i < signalBars.length; i++)
            signalBars[i].setBackgroundColor(i < filled ? active : empty);
    }

    /**
     * Khối xác minh gọn còn một dòng: "Thiết bị kết nối : RUTX50 - Đang sử dụng".
     *
     * <p>"Đang sử dụng" chỉ đúng khi Internet thực sự đi qua router VÀ điện thoại không lén
     * chạy 4G riêng — gọi được API router mới chỉ chứng minh vào được LAN của nó.
     */
    private void renderVerification(RutStatusUiModel model) {
        boolean inUse = model.internetThroughRouter
                && model.connectionState != RutConnectionState.PHONE_USING_CELLULAR;

        StringBuilder details = new StringBuilder();
        append(details, model.routerModel);
        append(details, getString(inUse ? R.string.rut_in_use : R.string.rut_not_in_use));

        String text = getString(R.string.rut_labeled_value,
                getString(R.string.rut_device_connected), details.toString());
        setCheck(verifyValue, inUse, text);
    }

    /**
     * Một dòng xác minh. Dấu tích/dấu chéo đi kèm mô tả nội dung cho TalkBack: người dùng
     * đọc màn hình phải nghe được "đạt/không đạt", không chỉ nghe tên điều kiện.
     */
    private void setCheck(TextView view, boolean passed, String text) {
        view.setText(text);
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                passed ? R.drawable.ic_rut_check : R.drawable.ic_rut_cross, 0, 0, 0);
        // "Đúng thiết bị RUT: Chưa kết nối" là câu vô nghĩa. Điều kiện xác minh chỉ có hai
        // kết quả: đạt hoặc không đạt.
        view.setContentDescription(text + ": " + getString(passed
                ? R.string.rut_check_pass : R.string.rut_check_fail));
    }

    @Nullable
    private String guidanceFor(RutStatusUiModel model) {
        if (model.wrongRouter) return getString(R.string.rut_error_wrong_router);

        switch (model.connectionState) {
            case PHONE_USING_CELLULAR:
                return getString(R.string.rut_error_phone_cellular);
            case CONNECTED_TO_RUT_NO_INTERNET:
                return getString(causeGuidance(causeOf(model), model.simState));
            case NOT_CONNECTED_TO_RUT:
                return getString(R.string.rut_error_not_connected);
            case AUTHENTICATION_FAILED:
                return getString(R.string.rut_error_auth);
            case ROUTER_UNREACHABLE:
                return getString(R.string.rut_error_unreachable);
            case VERIFIED_THROUGH_RUT:
                return model.primarySignalDbm() == null
                        ? getString(R.string.rut_error_no_signal_data) : null;
            default:
                return null;
        }
    }

    private static RutInternetDiagnosis.Cause causeOf(RutStatusUiModel model) {
        return model.diagnosis == null
                ? RutInternetDiagnosis.Cause.UNKNOWN : model.diagnosis.cause;
    }

    /**
     * Mỗi nguyên nhân một việc cụ thể. Câu chung "Router chưa có Internet" không nói người
     * đứng cạnh xe phải làm gì, nên họ khởi động lại router — việc tốn nhất và hầu như
     * không sửa được ca nào trong số này ngoài phiên 4G bị treo.
     */
    @StringRes
    static int causeGuidance(RutInternetDiagnosis.Cause cause, @Nullable RutSimState sim) {
        switch (cause) {
            case DATA_OFF:
                return R.string.rut_cause_data_off;
            case SIM_NOT_READY:
                if (sim == RutSimState.NO_SIM) return R.string.rut_cause_no_sim;
                if (sim == RutSimState.PIN_LOCKED) return R.string.rut_cause_sim_pin;
                if (sim == RutSimState.SIM_PRESENT_NOT_REGISTERED)
                    return R.string.rut_cause_sim_not_registered;
                return R.string.rut_cause_sim_other;
            case DATA_LIMIT_REACHED:
                return R.string.rut_cause_data_limit;
            case CONNECTION_NOT_READY:
                return R.string.rut_cause_connection_not_ready;
            case MOBILE_NO_TRAFFIC:
                return R.string.rut_cause_mobile_no_traffic;
            case DNS_FAILURE:
                return R.string.rut_cause_dns;
            case APP_SERVER_UNREACHABLE:
                return R.string.rut_cause_app_server;
            default:
                return R.string.rut_cause_unknown;
        }
    }

    private void setPill(@StringRes int text, @ColorRes int textColor, @ColorRes int background) {
        setPill(getString(text), textColor, background);
    }

    private void setPill(CharSequence text, @ColorRes int textColor, @ColorRes int background) {
        statusPill.setText(text);
        statusPill.setTextColor(ContextCompat.getColor(requireContext(), textColor));

        // Nền viên thuốc dùng chung một drawable; đổi màu trên bản sao đã mutate để không
        // ảnh hưởng mọi View khác dùng cùng drawable đó.
        GradientDrawable shape = (GradientDrawable) ContextCompat
                .getDrawable(requireContext(), R.drawable.bg_rut_pill);
        if (shape != null) {
            shape = (GradientDrawable) shape.mutate();
            shape.setColor(ContextCompat.getColor(requireContext(), background));
            statusPill.setBackground(shape);
        }
    }

    private void showMessage(@Nullable String text) {
        message.setText(text == null ? "" : text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private String describeUpdatedAt(@Nullable Long epochMs) {
        if (epochMs == null) return "";
        long ageMs = System.currentTimeMillis() - epochMs;
        if (ageMs < 60_000) return getString(R.string.rut_updated_just_now);
        return getString(R.string.rut_updated_at,
                DateUtils.formatDate(new Date(epochMs), "HH:mm:ss"));
    }

    private String labelOf(SignalRating rating) {
        switch (rating) {
            case EXCELLENT: return getString(R.string.rut_signal_excellent);
            case GOOD: return getString(R.string.rut_signal_good);
            case FAIR: return getString(R.string.rut_signal_fair);
            case WEAK: return getString(R.string.rut_signal_weak);
            case VERY_WEAK: return getString(R.string.rut_signal_very_weak);
            default: return getString(R.string.rut_signal_unknown);
        }
    }

    @ColorRes
    private int colorOf(SignalRating rating) {
        switch (rating) {
            case EXCELLENT:
            case GOOD:
                return R.color.rutOk;
            case FAIR:
                return R.color.rutWarn;
            case WEAK:
            case VERY_WEAK:
                return R.color.rutDanger;
            default:
                return R.color.rutBarEmpty;
        }
    }

    /**
     * Reboot KHÔNG bao giờ chạy thẳng từ nút. Đây là thao tác cắt Internet của cả xe trong
     * vài phút; một cú chạm nhầm giữa lúc đang tra nạp là mất kết nối đúng lúc cần nhất.
     */
    /**
     * Ghép cặp lại cũng là thao tác đặc quyền: nó cho phép một thiết bị KHÁC thay chỗ router
     * đã ghi nhớ. Phải hỏi, và phải nói rõ khi nào mới đúng là lúc dùng.
     */
    private void showRepairConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.rut_repair_confirm_title)
                .setMessage(R.string.rut_repair_confirm_message)
                .setNegativeButton(R.string.rut_reboot_confirm_cancel, null)
                .setPositiveButton(R.string.rut_repair, (dialog, which) -> viewModel.repair())
                .show();
    }

    private void showRebootConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.rut_reboot_confirm_title)
                .setMessage(R.string.rut_reboot_confirm_message)
                .setNegativeButton(R.string.rut_reboot_confirm_cancel, null)
                .setPositiveButton(R.string.rut_reboot_confirm_ok,
                        (dialog, which) -> viewModel.reboot())
                .show();
    }
}
