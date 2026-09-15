package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Điều phối popup router.
 *
 * <p>VÌ SAO đáng test: ba lỗi ở đây đều đã xảy ra thật ở màn hình khác của ứng dụng —
 * kết quả của một lần đọc cũ ghi đè lên lần đọc mới (người dùng thấy dữ liệu quá hạn mà
 * tưởng là mới), bấm nút hai lần gửi hai lệnh, và vòng theo dõi tự gửi lại lệnh sau khi
 * hết giờ. Riêng lệnh reboot thì mỗi lần gửi thừa là một lần cắt mạng của cả xe.
 *
 * <p>Không dùng InstantTaskExecutorRule (androidx.arch.core:core-testing không có trong
 * build): Robolectric đã chạy test trên chính main looper nên LiveData.setValue và
 * observeForever hoạt động bình thường; các tác vụ postDelayed được tua bằng shadow looper.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class RutStatusViewModelTest {

    /**
     * Repository giả: giữ lại callback thay vì trả lời ngay, để test dựng được đúng cảnh
     * "hai lần đọc chồng nhau" mà ngoài hiện trường mới gặp.
     */
    private static final class FakeRepository implements RutRepository {

        final List<Callback<RutStatusUiModel>> pendingLoads = new ArrayList<>();
        int loadCount;
        int rebootCount;
        int pingCount;
        int restartMobileCount;

        /** Lệnh dựng lại 4G bị router từ chối với thông điệp này; null = router nhận lệnh. */
        @Nullable
        String restartMobileError;

        /** Trả lời loadStatus ngay lập tức bằng model này; null nghĩa là giữ lại callback. */
        @Nullable
        RutStatusUiModel immediateResult;
        @Nullable
        String immediateError;

        /** pingRouter luôn trả về giá trị này — false mô phỏng router không lên lại. */
        boolean pingResult = false;

        @Override
        public void loadStatus(Callback<RutStatusUiModel> callback) {
            loadCount++;
            if (immediateError != null) callback.onError(immediateError);
            else if (immediateResult != null) callback.onResult(immediateResult);
            else pendingLoads.add(callback);
        }

        @Override
        public void rebootRouter(Callback<Void> callback) {
            rebootCount++;
            callback.onResult(null);
        }

        @Override
        public void restartMobileConnection(Callback<Void> callback) {
            restartMobileCount++;
            if (restartMobileError != null) callback.onError(restartMobileError);
            else callback.onResult(null);
        }

        @Override
        public void pingRouter(Callback<Boolean> callback) {
            pingCount++;
            callback.onResult(pingResult);
        }
    }

    /** Đúng ca ngoài hiện trường: SIM tốt, sóng tốt, router không ra được Internet. */
    private static RutStatusUiModel routerWithoutInternet(RutInternetDiagnosis.Cause cause) {
        RutStatusUiModel model = verifiedRouter();
        model.connectionState = RutConnectionState.CONNECTED_TO_RUT_NO_INTERNET;
        model.modemId = "1-1.4";
        model.diagnosis = new RutInternetDiagnosis();
        model.diagnosis.cause = cause;
        return model;
    }

    private static RutStatusUiModel verifiedRouter() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.connectionState = RutConnectionState.VERIFIED_THROUGH_RUT;
        model.routerIdentityVerified = true;
        model.routerModel = "RUTX50";
        model.routerSerial = "1122334455";
        return model;
    }

    private RutStatusViewModel viewModel;
    private FakeRepository repository;
    private final List<RutStatusUiState> seen = new ArrayList<>();

    @Before
    public void setUp() {
        viewModel = new RutStatusViewModel(
                ApplicationProvider.<FMSApplication>getApplicationContext());
        repository = new FakeRepository();
        viewModel.setRepositoryForTesting(repository);

        seen.clear();
        viewModel.getUiState().observeForever(seen::add);
        seen.clear();   // bỏ trạng thái khởi tạo, chỉ quan tâm cái xảy ra sau đó
    }

    private void drain() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    // --------------------------------------------------------------------- refresh

    @Test
    public void refresh_shows_loading_first_then_the_content_it_read() {
        RutStatusUiModel model = verifiedRouter();
        repository.immediateResult = model;

        viewModel.refresh();
        drain();

        assertEquals(2, seen.size());
        assertEquals(RutStatusUiState.Type.LOADING, seen.get(0).type);
        assertTrue("LOADING phải được coi là đang bận", seen.get(0).isBusy());
        assertEquals(RutStatusUiState.Type.CONTENT, seen.get(1).type);
        assertSame(model, seen.get(1).content);
        assertFalse(seen.get(1).isBusy());
    }

    @Test
    public void refresh_surfaces_the_error_message_from_the_repository() {
        repository.immediateError = "Không gọi được router";

        viewModel.refresh();
        drain();

        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.ERROR, last.type);
        assertEquals("Không gọi được router", last.errorMessage);
    }

    /**
     * Người dùng bấm "Làm mới" hai lần. Lần đọc thứ nhất về SAU lần thứ hai — chuyện bình
     * thường khi router chậm. Nếu kết quả cũ được nhận, popup hiện số liệu quá hạn nhưng
     * đóng dấu thời gian như vừa đo xong: người dùng tin vào một trạng thái không còn đúng.
     */
    @Test
    public void refresh_discards_the_result_of_a_read_that_a_newer_read_replaced() {
        viewModel.refresh();                     // lần cũ, callback bị giữ lại
        viewModel.refresh();                     // lần mới, callback bị giữ lại
        assertEquals(2, repository.pendingLoads.size());

        RutStatusUiModel fresh = verifiedRouter();
        fresh.operatorName = "MỚI";
        RutStatusUiModel stale = verifiedRouter();
        stale.operatorName = "CŨ";

        repository.pendingLoads.get(1).onResult(fresh);   // lần mới về trước
        repository.pendingLoads.get(0).onResult(stale);   // lần cũ về sau, phải bị bỏ
        drain();

        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.CONTENT, last.type);
        assertNotNull(last.content);
        assertEquals("Kết quả của lần đọc cũ không được ghi đè lần mới",
                "MỚI", last.content.operatorName);
    }

    /** Lỗi của lần đọc cũ cũng không được đẩy popup sang màn hình lỗi. */
    @Test
    public void refresh_discards_the_error_of_a_read_that_a_newer_read_replaced() {
        viewModel.refresh();
        viewModel.refresh();

        repository.pendingLoads.get(1).onResult(verifiedRouter());
        repository.pendingLoads.get(0).onError("lỗi của lần đọc đã cũ");
        drain();

        assertEquals(RutStatusUiState.Type.CONTENT, seen.get(seen.size() - 1).type);
    }

    // ---------------------------------------------------------------------- reboot

    @Test
    public void cannot_reboot_while_still_loading() {
        assertFalse(viewModel.canReboot());
    }

    /**
     * Router trả lời nhưng KHÔNG phải router đã ghép cặp. Cho reboot ở đây là khởi động
     * lại thiết bị mạng của người khác.
     */
    @Test
    public void cannot_reboot_when_the_router_identity_was_not_verified() {
        RutStatusUiModel unverified = verifiedRouter();
        unverified.routerIdentityVerified = false;
        repository.immediateResult = unverified;

        viewModel.refresh();
        drain();

        assertFalse(viewModel.canReboot());
    }

    @Test
    public void cannot_reboot_when_the_connection_state_does_not_allow_it() {
        for (RutConnectionState state : RutConnectionState.values()) {
            if (state.allowsReboot()) continue;

            RutStatusViewModel vm = new RutStatusViewModel(
                    ApplicationProvider.<FMSApplication>getApplicationContext());
            FakeRepository fake = new FakeRepository();
            RutStatusUiModel model = verifiedRouter();
            model.connectionState = state;
            fake.immediateResult = model;
            vm.setRepositoryForTesting(fake);

            vm.refresh();
            drain();

            assertFalse("State " + state + " không được mở nút khởi động lại",
                    vm.canReboot());
        }
    }

    @Test
    public void can_reboot_once_the_router_is_verified_and_reachable() {
        repository.immediateResult = verifiedRouter();
        viewModel.refresh();
        drain();

        assertTrue(viewModel.canReboot());
    }

    /** Không đủ điều kiện thì tuyệt đối không được chạm tới repository. */
    @Test
    public void reboot_sends_nothing_when_it_is_not_allowed() {
        RutStatusUiModel unverified = verifiedRouter();
        unverified.routerIdentityVerified = false;
        repository.immediateResult = unverified;
        viewModel.refresh();
        drain();

        viewModel.reboot();
        drain();

        assertEquals(0, repository.rebootCount);
    }

    /**
     * Hai lần chạm liên tiếp vào nút khởi động lại là chuyện thường với găng tay và màn
     * hình ngoài trời. Gửi hai lệnh nghĩa là router vừa lên lại đã bị tắt tiếp một lần
     * nữa, mất thêm vài phút mạng giữa ca bơm.
     */
    @Test
    public void reboot_sends_only_one_command_when_the_button_is_tapped_twice() {
        repository.immediateResult = verifiedRouter();
        viewModel.refresh();
        drain();

        viewModel.reboot();
        viewModel.reboot();
        drain();

        assertEquals(1, repository.rebootCount);
    }

    @Test
    public void reboot_keeps_the_last_known_data_on_screen_while_it_waits() {
        RutStatusUiModel model = verifiedRouter();
        repository.immediateResult = model;
        viewModel.refresh();
        drain();

        viewModel.reboot();

        RutStatusUiState state = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.REBOOTING, state.type);
        assertSame("Popup không được nhảy về trống trơn khi đang chờ router",
                model, state.content);
        assertTrue(state.isBusy());
    }

    /** Đang chờ router lên lại thì một lần refresh không được xoá trạng thái REBOOTING. */
    @Test
    public void refresh_is_ignored_while_a_reboot_is_being_watched() {
        repository.immediateResult = verifiedRouter();
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        viewModel.reboot();
        viewModel.refresh();

        assertEquals(loadsBefore, repository.loadCount);
        assertEquals(RutStatusUiState.Type.REBOOTING, seen.get(seen.size() - 1).type);
    }

    /**
     * Ca quan trọng nhất của lớp này: router KHÔNG bao giờ lên lại. Vòng theo dõi phải
     * kết thúc bằng một thông báo, chứ tuyệt đối không được gửi lại lệnh reboot — lệnh
     * đầu có thể đã chạy, và lệnh thứ hai sẽ tắt một router vừa mới khởi động xong.
     */
    @Test
    public void never_resends_the_reboot_command_after_the_watch_window_expires() {
        repository.immediateResult = verifiedRouter();
        viewModel.refresh();
        drain();

        repository.pingResult = false;
        viewModel.reboot();

        // Tua quá toàn bộ cửa sổ theo dõi (~4 phút) để chắc chắn vòng lặp đã kết thúc.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));

        assertEquals("Lệnh reboot chỉ được gửi đúng một lần", 1, repository.rebootCount);
        assertEquals("Phải thử ping đủ số lần đã định",
                RutStatusViewModel.REBOOT_POLL_DELAYS_MS.length, repository.pingCount);

        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.ERROR, last.type);
        assertNotNull(last.errorMessage);
    }

    /** Router lên lại thì dừng theo dõi và đọc lại trạng thái bằng phiên đăng nhập mới. */
    @Test
    public void stops_watching_and_reloads_once_the_router_answers_again() {
        repository.immediateResult = verifiedRouter();
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        repository.pingResult = true;
        viewModel.reboot();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));

        assertEquals(1, repository.rebootCount);
        assertEquals("Ping thành công phải dừng vòng theo dõi ngay", 1, repository.pingCount);
        assertEquals("Phải đọc lại trạng thái sau khi router lên",
                loadsBefore + 1, repository.loadCount);
        assertEquals(RutStatusUiState.Type.CONTENT, seen.get(seen.size() - 1).type);
    }

    // --------------------------------------------------------- dựng lại kết nối 4G

    @Test
    public void restart_mobile_is_offered_when_the_router_lost_internet_and_it_may_help() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();

        assertTrue(viewModel.canRestartMobileConnection());
    }

    /** Router đang có Internet thì không có gì để sửa; cắt 4G lúc đó là tự gây sự cố. */
    @Test
    public void restart_mobile_is_not_offered_while_the_router_has_internet() {
        RutStatusUiModel model = verifiedRouter();
        model.modemId = "1-1.4";
        repository.immediateResult = model;
        viewModel.refresh();
        drain();

        assertFalse(viewModel.canRestartMobileConnection());
    }

    /**
     * Router vẫn ra mạng, chỉ máy chủ FMS không trả lời. Mở nút ở đây là mời người dùng cắt
     * mạng của cả xe để sửa một sự cố không nằm ở xe.
     */
    @Test
    public void restart_mobile_is_not_offered_when_only_the_app_server_is_down() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.APP_SERVER_UNREACHABLE);
        viewModel.refresh();
        drain();

        assertFalse(viewModel.canRestartMobileConnection());
        viewModel.restartMobileConnection();
        assertEquals(0, repository.restartMobileCount);
    }

    @Test
    public void restart_mobile_is_not_offered_without_a_modem_id_or_verified_identity() {
        RutStatusUiModel noModem =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        noModem.modemId = null;
        repository.immediateResult = noModem;
        viewModel.refresh();
        drain();
        assertFalse("Không có modemId thì không có lệnh nào để gửi",
                viewModel.canRestartMobileConnection());

        RutStatusUiModel unverified =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        unverified.routerIdentityVerified = false;
        repository.immediateResult = unverified;
        viewModel.refresh();
        drain();
        assertFalse("Router chưa xác minh thì không được gửi lệnh",
                viewModel.canRestartMobileConnection());
    }

    @Test
    public void restart_mobile_sends_only_one_command_when_the_button_is_tapped_twice() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();

        viewModel.restartMobileConnection();
        viewModel.restartMobileConnection();
        viewModel.reboot();                         // cũng không được chen ngang
        drain();

        assertEquals(1, repository.restartMobileCount);
        assertEquals(0, repository.rebootCount);
        RutStatusUiState state = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.RESTARTING_MOBILE, state.type);
        assertTrue(state.isBusy());
        assertNotNull("Popup giữ số liệu cũ thay vì nhảy về trống trơn", state.content);
    }

    @Test
    public void refresh_is_ignored_while_the_mobile_connection_is_restarting() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        viewModel.restartMobileConnection();
        viewModel.refresh();

        assertEquals(loadsBefore, repository.loadCount);
        assertEquals(RutStatusUiState.Type.RESTARTING_MOBILE, seen.get(seen.size() - 1).type);
    }

    /** Internet về thì dừng đọc lại ngay, không chờ hết vòng. */
    @Test
    public void restart_mobile_stops_watching_as_soon_as_internet_is_back() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        viewModel.restartMobileConnection();
        repository.immediateResult = verifiedRouter();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5));

        assertEquals(1, repository.restartMobileCount);
        assertEquals("Internet về ở lần đọc đầu thì chỉ đọc đúng một lần",
                loadsBefore + 1, repository.loadCount);
        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.CONTENT, last.type);
        assertNotNull(last.content);
        assertEquals(RutConnectionState.VERIFIED_THROUGH_RUT, last.content.connectionState);
    }

    /**
     * Internet không về. Vòng đọc phải kết thúc bằng lần đọc cuối — chẩn đoán trong đó nói
     * tiếp phải làm gì — và KHÔNG được tự gửi lại lệnh.
     */
    @Test
    public void restart_mobile_never_resends_the_command_and_shows_the_last_reading() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        viewModel.restartMobileConnection();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));

        assertEquals("Lệnh chỉ được gửi đúng một lần", 1, repository.restartMobileCount);
        assertEquals("Phải đọc lại đủ số lần đã định",
                loadsBefore + RutStatusViewModel.RESTART_POLL_DELAYS_MS.length,
                repository.loadCount);
        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.CONTENT, last.type);
        assertFalse(last.isBusy());
    }

    /** Router không nhận lệnh thì không có gì để chờ: báo ngay, không đọc lại vô ích. */
    @Test
    public void restart_mobile_reports_a_rejected_command_without_watching() {
        repository.immediateResult =
                routerWithoutInternet(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC);
        viewModel.refresh();
        drain();
        int loadsBefore = repository.loadCount;

        repository.restartMobileError = "Router không nhận lệnh";
        viewModel.restartMobileConnection();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5));

        assertEquals(loadsBefore, repository.loadCount);
        RutStatusUiState last = seen.get(seen.size() - 1);
        assertEquals(RutStatusUiState.Type.ERROR, last.type);
        assertEquals("Router không nhận lệnh", last.errorMessage);
    }
}
