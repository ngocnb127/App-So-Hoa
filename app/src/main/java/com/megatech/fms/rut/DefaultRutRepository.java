package com.megatech.fms.rut;

import android.content.Context;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megatech.fms.R;
import com.megatech.fms.helpers.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ghép mọi nguồn lại thành một kết luận cho popup.
 *
 * <p>Thứ tự đọc có chủ đích: nhận dạng router TRƯỚC, vì mọi số liệu bên dưới chỉ có nghĩa
 * khi biết chắc chúng đến từ đúng chiếc router của xe này. Một router lạ cùng dải địa chỉ
 * cũng trả lời được và cũng có sóng — hiển thị số của nó là nói dối người dùng.
 */
public final class DefaultRutRepository implements RutRepository {

    private static final String LOG_TAG = "RUT";

    /**
     * Các đường nhận dạng router, thử lần lượt.
     *
     * <p>Một đường duy nhất là điểm gãy duy nhất: đúng một endpoint đổi tên hoặc không có
     * trên bản firmware đang chạy là ứng dụng kết luận "không kết nối được router" trong khi
     * router vẫn đang trả lời mọi thứ khác. Chỉ khi TẤT CẢ đều không tới được mới được coi
     * là router không tới được.
     */
    private static final String[] IDENTITY_ENDPOINTS = {
            "/api/system/device/status",
            "/api/system/device/info",
            "/api/system/device/mnfinfo/status",
            "/api/system/device/board/status",
    };

    private static final String[] MODEM_ENDPOINTS = {
            "/api/modems/status",
            "/api/modem/status",
    };

    private static final String[] SIM_ENDPOINTS = {
            "/api/sim_cards/status",
            "/api/sims/status",
    };

    /** Khoá cho biết "đây là MỘT bản ghi modem", không phải map chứa nhiều modem. */
    private static final String[] MODEM_LEAF_KEYS = {
            "id", "modem_id", "index", "connected", "connection_state", "state", "status",
            "net_state", "operator", "operator_name", "network_type", "net_type", "builtin",
            "sim", "imei", "manuf",
    };

    private static final String[] SIM_LEAF_KEYS = {
            "sim", "id", "slot", "index", "state", "sim_state", "status", "sim_status",
            "present", "inserted", "active", "in_use", "selected", "iccid", "imsi", "pin_state",
    };

    /**
     * Tên "thùng chứa" hay gặp ở khoá bọc ngoài. Nhận nhầm chúng làm modem id nghĩa là gọi
     * {@code /api/modems/signal/status/modems} và mọi số liệu sóng im lặng biến mất.
     */
    private static final List<String> CONTAINER_KEYS = Arrays.asList(
            "modems", "modem", "data", "result", "results", "status", "items", "list", "sims",
            "sim_cards");

    private final Context context;
    private final AndroidNetworkInspector inspector;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * Kho cấu hình dựng LƯỜI, và chỉ dựng trên luồng nền.
     *
     * <p>{@code EncryptedSharedPreferences} sinh khoá trong Android Keystore ở lần đầu —
     * việc đó mất từ vài trăm mili giây tới vài giây trên máy cũ. Dựng nó trong constructor
     * của ViewModel là chạy ngay trên luồng giao diện, đúng lúc người dùng vừa chạm nút.
     */
    @Nullable
    private volatile RutConfigStore store;

    /**
     * Một client dùng chung cho cả phiên popup, gắn với đúng mạng Wi-Fi đã tạo ra nó.
     *
     * <p>Dựng client mới cho mỗi lần gọi nghĩa là đăng nhập lại mỗi lần: riêng vòng theo dõi
     * sau reboot đã là 12 lượt đăng nhập trong 4 phút, đủ để RutOS coi là bất thường.
     */
    @Nullable
    private RutApiClient cachedApi;
    @Nullable
    private Network cachedApiNetwork;

    public DefaultRutRepository(Context context) {
        this.context = context.getApplicationContext();
        this.inspector = new AndroidNetworkInspector(this.context);
    }

    private RutConfigStore store() {
        RutConfigStore local = store;
        if (local == null) {
            local = new RutConfigStore(context);
            store = local;
        }
        return local;
    }

    /** Client cho mạng Wi-Fi hiện tại; đổi mạng thì dựng lại vì phiên cũ đã vô nghĩa. */
    private RutApiClient api(Network wifi) {
        if (cachedApi == null || cachedApiNetwork == null || !cachedApiNetwork.equals(wifi)) {
            cachedApi = new RutApiClient(store().load(), store(), wifi.getSocketFactory());
            cachedApiNetwork = wifi;
        }
        return cachedApi;
    }

    @Override
    public void loadStatus(Callback<RutStatusUiModel> callback) {
        executor.execute(() -> {
            RutStatusUiModel model;
            try {
                model = readStatus();
            } catch (Throwable ex) {
                // KHÔNG được để một ngoại lệ lạ nuốt mất callback: popup sẽ quay vòng mãi
                // mãi với nút "Làm mới" bị khoá, và lối thoát duy nhất là đóng popup.
                Logger.appendLog(LOG_TAG, "Đọc trạng thái router lỗi: " + Logger.describe(ex));
                main.post(() -> callback.onError(string(R.string.rut_error_unreachable)));
                return;
            }
            RutStatusUiModel result = model;
            main.post(() -> callback.onResult(result));
        });
    }

    @Override
    public void rebootRouter(Callback<Void> callback) {
        executor.execute(() -> {
            Network wifi = inspector.findWifiNetwork();
            if (wifi == null) {
                main.post(() -> callback.onError(string(R.string.rut_error_not_connected)));
                return;
            }

            try {
                RutApiClient api = api(wifi);

                // Xác minh LẠI ngay trước khi gửi. Trạng thái trên màn hình có thể đã vài
                // phút tuổi: điện thoại kịp nhảy sang một Wi-Fi khác cũng có thiết bị ở
                // 192.168.1.1 (modem gia dụng nào cũng vậy), và lệnh reboot sẽ đi tới thiết
                // bị của người khác.
                RutStatusUiModel identity = new RutStatusUiModel();
                readIdentity(api, identity);
                if (!isExpectedRouter(store().load(), identity)) {
                    Logger.appendLog(LOG_TAG, "Không reboot: thiết bị trả lời không phải "
                            + "router đã ghép cặp");
                    main.post(() -> callback.onError(string(R.string.rut_error_wrong_router)));
                    return;
                }

                api.post("/api/system/actions/reboot");
                Logger.appendLog(LOG_TAG, "Đã gửi lệnh khởi động lại router");
            } catch (RutApiClient.RutApiException ex) {
                // Sai tài khoản hay sai chứng thư là lỗi THẬT, phải nói ra. Chỉ lỗi kết nối
                // mới được coi là biểu hiện bình thường của reboot (router ngắt mạng ngay
                // khi nhận lệnh) — xem catch bên dưới.
                if (ex.httpCode == 401 || ex.httpCode == 403 || ex.httpCode == 0) {
                    main.post(() -> callback.onError(string(R.string.rut_error_auth)));
                    return;
                }
                Logger.appendLog(LOG_TAG, "Lệnh reboot không có phản hồi: " + ex.getMessage());
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Lệnh reboot mất kết nối sau khi gửi: "
                        + Logger.describe(ex));
            }

            // Phiên cũ chết theo router; lượt theo dõi phải đăng nhập lại từ đầu.
            cachedApi = null;
            cachedApiNetwork = null;
            main.post(() -> callback.onResult(null));
        });
    }

    @Override
    public void restartMobileConnection(Callback<Void> callback) {
        executor.execute(() -> {
            Network wifi = inspector.findWifiNetwork();
            if (wifi == null) {
                main.post(() -> callback.onError(string(R.string.rut_error_not_connected)));
                return;
            }

            try {
                RutApiClient api = api(wifi);

                // Xác minh lại danh tính như lệnh reboot, cùng lý do: trạng thái trên màn hình
                // có thể đã cũ và điện thoại có thể đã sang một mạng khác.
                RutStatusUiModel fresh = new RutStatusUiModel();
                readIdentity(api, fresh);
                if (!isExpectedRouter(store().load(), fresh)) {
                    Logger.appendLog(LOG_TAG, "Không khởi động lại 4G: thiết bị trả lời không "
                            + "phải router đã ghép cặp");
                    main.post(() -> callback.onError(string(R.string.rut_error_wrong_router)));
                    return;
                }

                // modemId đọc MỚI, không lấy từ lần đọc trước: sau khi modem khởi động lại, id
                // USB của nó có thể đổi.
                readModem(api, fresh, new Extras());
                if (fresh.modemId == null) {
                    main.post(() -> callback.onError(string(R.string.rut_error_no_modem)));
                    return;
                }

                api.post("/api/modems/" + fresh.modemId + "/actions/restart_connection");
                Logger.appendLog(LOG_TAG, "Đã gửi lệnh khởi động lại kết nối 4G, modem="
                        + fresh.modemId);
                main.post(() -> callback.onResult(null));
            } catch (RutApiClient.RutApiException ex) {
                // Khác reboot: lệnh này KHÔNG cắt Wi-Fi, nên router phải trả lời được. Mọi
                // lỗi ở đây đều là lỗi thật.
                Logger.appendLog(LOG_TAG, "Lệnh khởi động lại 4G bị từ chối: HTTP "
                        + ex.httpCode);
                int message = ex.httpCode == 401 || ex.httpCode == 403 || ex.httpCode == 0
                        ? R.string.rut_error_auth : R.string.rut_error_restart_failed;
                main.post(() -> callback.onError(string(message)));
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Lệnh khởi động lại 4G lỗi: " + Logger.describe(ex));
                main.post(() -> callback.onError(string(R.string.rut_error_unreachable)));
            }
        });
    }

    @Override
    public void pingRouter(Callback<Boolean> callback) {
        executor.execute(() -> {
            boolean online = false;
            try {
                Network wifi = inspector.findWifiNetwork();
                if (wifi != null) online = respondsAtAll(api(wifi));
            } catch (Exception ex) {
                online = false;
            }
            if (!online) {
                cachedApi = null;   // token cũ đã chết cùng router
                cachedApiNetwork = null;
            }
            boolean result = online;
            main.post(() -> callback.onResult(result));
        });
    }

    /**
     * Router đã sống lại chưa — dùng cho vòng theo dõi sau reboot.
     *
     * <p>Bất kỳ mã HTTP nào cũng là bằng chứng router đã lên: web server chỉ trả lời sau khi
     * hệ thống khởi động xong. Đòi đúng một endpoint phải thành công là để cả vòng theo dõi
     * hết giờ chỉ vì bản firmware này không có đường đó.
     */
    private boolean respondsAtAll(RutApiClient api) {
        for (String endpoint : IDENTITY_ENDPOINTS) {
            try {
                if (api.get(endpoint) != null) return true;
            } catch (RutApiClient.RutApiException ex) {
                if (ex.httpCode > 0) return true;
            } catch (Exception ignored) {
                // thử endpoint kế tiếp
            }
        }
        return false;
    }

    /** Quên router đã ghép cặp — dùng khi thay router hoặc router bị reset. */
    public void forgetPairing() {
        executor.execute(() -> {
            store().clearPairing();
            cachedApi = null;
            cachedApiNetwork = null;
            Logger.appendLog(LOG_TAG, "Đã xoá ghép cặp router");
        });
    }

    /** Đóng luồng nền khi popup bị huỷ. Không gọi thì mỗi lần mở popup rò một luồng. */
    public void close() {
        executor.shutdownNow();
    }

    // --------------------------------------------------------------- đọc trạng thái

    private RutStatusUiModel readStatus() {
        RutStatusUiModel model = new RutStatusUiModel();
        model.lastUpdatedEpochMs = System.currentTimeMillis();

        RutConnectionConfig config = store().load();
        if (!config.isUsable()) {
            model.connectionState = RutConnectionState.AUTHENTICATION_FAILED;
            return model;
        }

        Network wifi = inspector.findWifiNetwork();
        if (wifi == null) {
            model.connectionState = RutConnectionState.NOT_CONNECTED_TO_RUT;
            return model;
        }

        RutApiClient api = api(wifi);

        // Tên Wi-Fi lấy TRƯỚC mọi phép gọi API và giữ lại cho mọi nhánh thoát sớm: khi
        // router không trả lời, đây là mẩu thông tin duy nhất giúp người dùng biết mình
        // đang nối nhầm mạng nào.
        model.wifiSsid = inspector.currentWifiSsid(wifi);

        try {
            readIdentity(api, model);
            // Chỉ tới đây mới được nói "đã kết nối Wi-Fi của RUT": có một Wi-Fi nào đó
            // không chứng minh được gì cả — Wi-Fi văn phòng cũng là một Wi-Fi.
            model.wifiConnectedToRut = true;
        } catch (RutApiClient.RutApiException ex) {
            // Router TRẢ LỜI nhưng từ chối ta: vẫn là đang ở trong mạng của nó.
            model.wifiConnectedToRut = ex.httpCode == 401 || ex.httpCode == 403;
            model.connectionState = model.wifiConnectedToRut
                    ? RutConnectionState.AUTHENTICATION_FAILED
                    : RutConnectionState.ROUTER_UNREACHABLE;
            return model;
        } catch (IOException ex) {
            model.connectionState = RutConnectionState.ROUTER_UNREACHABLE;
            return model;
        }

        if (!isExpectedRouter(config, model)) {
            model.connectionState = RutConnectionState.ROUTER_UNREACHABLE;
            model.routerIdentityVerified = false;
            model.wrongRouter = true;
            return model;
        }
        model.routerIdentityVerified = true;

        // Ghim danh tính CHỈ sau khi đã xác thực được. Ghim ngay ở lần bắt tay đầu nghĩa là
        // bất kỳ thiết bị nào trả lời tại địa chỉ đó — gateway văn phòng chẳng hạn — cũng
        // được ghim vĩnh viễn, và router thật sau đó bị chính bản ghim ấy từ chối.
        if (!config.isPaired()) store().rememberSerial(model.routerSerial);

        // Số liệu mạng: một phần hỏng không được làm hỏng cả popup. Thiếu thì để null và
        // UI hiện "—", đúng hơn là hiện số 0 như một phép đo có thật.
        Extras extras = new Extras();
        // Tên Wi-Fi hỏi CHÍNH ROUTER, không hỏi Android. Đo trên Galaxy Tab Active5 chạy
        // Android 15: dù đã cấp ACCESS_FINE_LOCATION, WifiManager vẫn trả "<unknown ssid>"
        // và BSSID bị che thành 02:00:00:00:00:00 — từ Android 13 tên mạng còn đòi quyền
        // NEARBY_WIFI_DEVICES. Router thì biết chính xác tên nó đang phát, và lấy đường này
        // thì không phải xin thêm một quyền nhạy cảm chỉ để hiển thị một dòng chữ.
        safely(() -> readWifiName(api, model));
        safely(() -> readModem(api, model, extras));
        safely(() -> readSignal(api, model));
        safely(() -> readSim(api, model, extras));

        // Ba ca SIM khác nhau cần ba cách xử lý khác nhau ngoài hiện trường; gộp chúng vào
        // một cờ là bắt người dùng đoán xem có nên rút SIM ra cắm lại hay không.
        RutSimState simState = RutSimState.classify(
                model.simPresent, extras.pinLocked, extras.registered, model.mobileConnected);
        RutSimState.attach(model, simState);
        Logger.appendLog(LOG_TAG, "Trạng thái SIM: " + simState
                + " (simPresent=" + model.simPresent + ", pin=" + extras.pinLocked
                + ", registered=" + extras.registered
                + ", data=" + model.mobileConnected + ")");

        model.internetThroughRouter = InternetVerifier.canReachInternet(wifi, null);

        if (inspector.isActiveNetworkCellular()) {
            model.connectionState = RutConnectionState.PHONE_USING_CELLULAR;
        } else if (model.internetThroughRouter) {
            model.connectionState = RutConnectionState.VERIFIED_THROUGH_RUT;
        } else {
            model.connectionState = RutConnectionState.CONNECTED_TO_RUT_NO_INTERNET;
            // Chỉ chẩn đoán khi có sự cố: ping trên router mất vài giây, không đáng bắt mọi
            // lần mở popup phải chờ.
            model.diagnosis = diagnoseNoInternet(api, model, extras);
        }

        return model;
    }

    // ------------------------------------------------------- chẩn đoán mất Internet

    /** Đích ping: chỉ cần một địa chỉ IP công cộng luôn trả lời, không cần DNS. */
    private static final String PING_TARGET = "8.8.8.8";

    /** Ping hỏng là ping chạy lâu nhất: mỗi gói chờ hết giờ rồi mới tới gói sau. */
    private static final int DIAGNOSTIC_TIMEOUT_SECONDS = 25;

    /**
     * Tìm ra VÌ SAO router không đưa được máy này ra Internet.
     *
     * <p>Mỗi phép đọc hỏng chỉ để trống đúng số đo đó — thiếu một số đo thì kết luận kém cụ
     * thể hơn, chứ không được làm hỏng cả popup.
     */
    private RutInternetDiagnosis diagnoseNoInternet(RutApiClient api, RutStatusUiModel model,
                                                    Extras extras) {
        RutInternetDiagnosis diagnosis = new RutInternetDiagnosis();
        diagnosis.dataOff = extras.dataOff;
        diagnosis.mobileStage = extras.mobileStage;

        diagnosis.dataLimitReached = readQuietly("hạn mức data", () ->
                RutInternetDiagnosis.dataLimitReached(
                        RutJson.data(api.get("/api/data_limit/status")), model.activeSimSlot));
        diagnosis.mobileHasIp = readQuietly("interface 4G", () ->
                RutInternetDiagnosis.mobileHasIp(
                        RutJson.data(api.get("/api/interfaces/status"))));

        diagnosis.routerReachesIp = readQuietly("ping", () -> {
            JsonObject data = new JsonObject();
            data.addProperty("proto", "ipv4");
            data.addProperty("host", PING_TARGET);
            return RutInternetDiagnosis.pingReached(
                    diagnosticOutput(api, "/api/diagnostics/actions/ping", data));
        });

        // DNS chỉ hỏi khi đã ra được bằng IP: không ra được thì DNS hỏng hay không cũng
        // chẳng đổi được kết luận, mà lại thêm một lượt chờ.
        if (Boolean.TRUE.equals(diagnosis.routerReachesIp)) {
            String host = appServerHost();
            diagnosis.routerResolvesDns = host == null ? null : readQuietly("DNS", () -> {
                JsonObject data = new JsonObject();
                data.addProperty("host", host);
                return RutInternetDiagnosis.nslookupResolved(
                        diagnosticOutput(api, "/api/diagnostics/actions/nslookup", data));
            });
        }

        diagnosis.cause = RutInternetDiagnosis.classify(model.simState, model.mobileConnected,
                diagnosis.dataOff, diagnosis.mobileStage, diagnosis.dataLimitReached,
                diagnosis.mobileHasIp, diagnosis.routerReachesIp, diagnosis.routerResolvesDns);

        Logger.appendLog(LOG_TAG, "Chẩn đoán mất Internet: " + diagnosis.cause
                + " (dataOff=" + diagnosis.dataOff + ", stage=" + diagnosis.mobileStage
                + ", hạn mức=" + diagnosis.dataLimitReached + ", IP 4G=" + diagnosis.mobileHasIp
                + ", ping=" + diagnosis.routerReachesIp + ", DNS=" + diagnosis.routerResolvesDns
                + ", SIM=" + model.simState + ", data=" + model.mobileConnected + ")");
        return diagnosis;
    }

    @Nullable
    private static String diagnosticOutput(RutApiClient api, String path, JsonObject data)
            throws IOException {
        JsonObject result = RutJson.firstObject(RutJson.data(
                api.postWithData(path, data, DIAGNOSTIC_TIMEOUT_SECONDS)));
        String output = RutJson.string(result, "response", "output", "result");
        if (output != null) {
            // Ghi dòng cuối của đầu ra — dòng thống kê — để người hỗ trợ đọc được từ log
            // gửi về. Đầu ra ping/nslookup chỉ có địa chỉ công cộng, không có bí mật.
            String trimmed = output.trim();
            int lastBreak = trimmed.lastIndexOf('\n');
            String tail = lastBreak < 0 ? trimmed : trimmed.substring(lastBreak + 1);
            Logger.appendLog(LOG_TAG, path + " → " + tail);
        }
        return output;
    }

    /** Tên miền máy chủ FMS; null nếu cấu hình là địa chỉ IP (không có gì để tra DNS). */
    @Nullable
    private static String appServerHost() {
        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse(com.megatech.fms.BuildConfig.API_BASE_URL);
        if (url == null) return null;
        String host = url.host();
        return host.matches("[0-9.]+") || host.contains(":") ? null : host;
    }

    private interface Reading<T> {
        T read() throws IOException;
    }

    @Nullable
    private static <T> T readQuietly(String what, Reading<T> reading) {
        try {
            return reading.read();
        } catch (RutApiClient.RutApiException ex) {
            // Firmware cũ có thể không có endpoint này: đó là "không biết", không phải lỗi.
            Logger.appendLog(LOG_TAG, "Chẩn đoán: " + what + " trả HTTP " + ex.httpCode);
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Chẩn đoán: " + what + " lỗi: " + Logger.describe(ex));
        }
        return null;
    }

    /**
     * Đọc model + serial, thử lần lượt mọi đường nhận dạng.
     *
     * <p>Chỉ ném {@link IOException} khi KHÔNG endpoint nào trả lời được. Router trả lời mà
     * không khai model/serial là chuyện khác hẳn với router không tới được, và lớp trên phân
     * biệt hai ca đó bằng chính chỗ này.
     */
    private void readIdentity(RutApiClient api, RutStatusUiModel model) throws IOException {
        IOException lastFailure = null;
        boolean answered = false;

        for (String endpoint : IDENTITY_ENDPOINTS) {
            JsonObject response;
            try {
                response = api.get(endpoint);
            } catch (RutApiClient.RutApiException ex) {
                Logger.appendLog(LOG_TAG, "Nhận dạng: " + endpoint + " trả HTTP " + ex.httpCode);
                // Sai tài khoản hoặc sai chứng thư thì mọi endpoint khác cũng hỏng y hệt;
                // thử tiếp chỉ tạo thêm lượt đăng nhập hỏng.
                if (ex.httpCode == 401 || ex.httpCode == 403 || ex.httpCode == 0) throw ex;
                lastFailure = ex;
                continue;
            } catch (IOException ex) {
                Logger.appendLog(LOG_TAG, "Nhận dạng: " + endpoint + " không tới được: "
                        + Logger.describe(ex));
                lastFailure = ex;
                continue;
            }

            answered = true;
            JsonElement data = RutJson.data(response);

            // Hai lượt tìm: khoá nói rõ là model trước, rồi mới tới "name" — "name" có thể là
            // tên đặt cho thiết bị chứ không phải model, lấy nhầm là hỏng bước so tiền tố RUT.
            // "device_name" đứng đầu: RUT955 trả device_name="RUT955" nhưng model=
            // "Teltonika RUT9XX" — cái sau là tên DÒNG sản phẩm, người dùng đứng cạnh xe
            // không nhận ra đó là thiết bị nào.
            String routerModel = RutJson.deepString(data,
                    "device_name", "model", "device_model", "board_name", "product",
                    "product_code", "mnf_code");
            if (routerModel == null) routerModel = RutJson.deepString(data, "name", "hostname");
            String serial = RutJson.deepString(data,
                    "serial", "serial_number", "serial_no", "sn", "mnf_serial");

            if (routerModel != null || serial != null) {
                model.routerModel = routerModel;
                model.routerSerial = serial;
                Logger.appendLog(LOG_TAG, "Nhận dạng qua " + endpoint + ": model="
                        + routerModel + ", serial=" + serial);
                return;
            }
            Logger.appendLog(LOG_TAG, "Nhận dạng: " + endpoint
                    + " có trả lời nhưng không có model/serial");
        }

        if (answered) return;   // router SỐNG, chỉ là không khai danh tính
        if (lastFailure != null) throw lastFailure;
        throw new IOException("Không endpoint nhận dạng nào trả lời");
    }

    /**
     * Đúng router hay không.
     *
     * <p>Đã ghép cặp thì serial phải khớp — đó là bằng chứng duy nhất không giả được bằng
     * cách đặt trùng tên Wi-Fi. Chưa ghép cặp thì tối thiểu model phải mang tiền tố RUT.
     */
    static boolean isExpectedRouter(RutConnectionConfig config, RutStatusUiModel model) {
        if (config.isPaired())
            return config.expectedSerial.equalsIgnoreCase(model.routerSerial);
        return hasRutModelPrefix(model.routerModel);
    }

    /**
     * Model có mang tiền tố RUT ở ĐẦU MỘT TỪ hay không.
     *
     * <p>{@code startsWith} thuần trượt trên chính những chuỗi firmware hay trả:
     * {@code "Teltonika RUTX50"}, {@code "RUTX50 Mobile Router"}. Nới tới mức "chứa RUT ở bất
     * kỳ đâu" thì lại nhận cả {@code "MYRUTX50"} — một thiết bị khác hẳn. Ranh giới từ là chỗ
     * cân bằng đúng.
     */
    static boolean hasRutModelPrefix(@Nullable String routerModel) {
        if (routerModel == null) return false;
        String upper = routerModel.toUpperCase(Locale.US);
        String prefix = RutConnectionConfig.EXPECTED_MODEL_PREFIX;

        int from = 0;
        while (true) {
            int at = upper.indexOf(prefix, from);
            if (at < 0) return false;
            if (at == 0 || !Character.isLetterOrDigit(upper.charAt(at - 1))) return true;
            from = at + 1;
        }
    }

    private void readModem(RutApiClient api, RutStatusUiModel model, Extras extras) {
        JsonElement data = firstAnswer(api, MODEM_ENDPOINTS, "modem");
        if (data == null) return;

        // Ba hình dạng đều gặp ngoài thực tế: mảng, một object, và map lấy CHÍNH modem id
        // làm khoá ({"1-1":{...}}). Ở dạng map, id không nằm trong object.
        JsonObject modem = null;
        String idFromKey = null;
        if (data.isJsonArray()) {
            modem = RutJson.firstObject(data);
        } else if (data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            if (RutJson.hasAny(object, MODEM_LEAF_KEYS)) {
                modem = object;
            } else {
                RutJson.Keyed keyed = RutJson.firstKeyedObject(object);
                if (keyed != null) {
                    modem = keyed.object;
                    if (!CONTAINER_KEYS.contains(keyed.key.toLowerCase(Locale.US)))
                        idFromKey = keyed.key;
                } else {
                    for (JsonObject candidate : RutJson.objects(object, MODEM_LEAF_KEYS)) {
                        modem = candidate;
                        break;
                    }
                }
            }
        }
        if (modem == null) {
            Logger.appendLog(LOG_TAG, "Modem: không đọc được bản ghi nào từ response");
            return;
        }

        // KHÔNG nhận "builtin" làm id: trong RutOS đó là cờ true/false, lấy nhầm sẽ gọi
        // /api/modems/signal/status/true và mọi số liệu sóng im lặng biến mất.
        model.modemId = RutJson.string(modem, "id", "modem_id", "index");
        if (model.modemId == null) model.modemId = idFromKey;
        Logger.appendLog(LOG_TAG, "Modem: id=" + model.modemId
                + (idFromKey != null && idFromKey.equals(model.modemId) ? " (lấy từ khoá)" : ""));

        model.mobileConnected = RutJson.bool(modem,
                "connected", "connection_state", "state", "status", "net_state");
        model.networkType = RutJson.string(modem,
                "network_type", "net_type", "service_mode", "conntype", "technology");
        // "oper" và "provider" là tên thật RUT955 dùng; hai tên trong tài liệu ("operator",
        // "operator_name") không tồn tại trên firmware này.
        model.operatorName = RutJson.string(modem,
                "oper", "provider", "operator", "operator_name", "opname", "carrier");

        // Đăng ký mạng là thứ tách ca "SIM hỏng" khỏi ca "SIM tốt nhưng hết tiền / ngoài
        // vùng phủ" — hai sự cố cần hai cách xử lý khác nhau.
        extras.registered = registrationOf(modem);
        extras.pinLocked = pinLocked(modem);
        // Hai field chỉ có ở bản đầy đủ của modem status; RUT955 7.06 đo thật không trả —
        // khi đó để null và chẩn đoán dựa vào các số đo khác.
        extras.dataOff = RutJson.bool(modem, "data_off");
        extras.mobileStage = RutJson.integer(modem, "mobile_stage");
        if (model.simPresent == null) model.simPresent = simPresence(modem);

        // Khay SIM đang dùng đọc THẲNG ở đây. Đo trên RUT955: /api/sim_switch/status/{id}
        // trả HTTP 501 "Endpoint for 'status' not implemented", nên chờ endpoint đó là mất
        // hẳn thông tin khay — trong khi chính modem status đã có sẵn "active_sim".
        if (model.activeSimSlot == null)
            model.activeSimSlot = RutJson.integer(modem, "active_sim", "sim", "sim_id");

        // Nhiều firmware đã kèm sẵn số liệu sóng trong chính modem status; dùng được thì
        // không phụ thuộc vào một endpoint sóng riêng nữa.
        readSignalInto(modem, model);
    }

    /**
     * Modem đã đăng ký được vào mạng nhà mạng hay chưa.
     *
     * <p>Không dùng {@link RutJson#bool} được: {@code netstate} là một TỪ TRẠNG THÁI
     * ("Registered", "Searching", "Denied"), không phải cờ đúng/sai. Đo trên RUT955 khi
     * không có SIM: {@code netstate} = {@code "Searching"} — một câu trả lời rõ ràng là CHƯA
     * đăng ký, mà cách đọc cũ lại biến thành "không biết".
     */
    @Nullable
    static Boolean registrationOf(@Nullable JsonObject modem) {
        Boolean flag = RutJson.bool(modem,
                "registration", "reg_status", "registration_status", "network_state");
        if (flag != null) return flag;

        String state = RutJson.string(modem, "netstate", "reg_state", "network_registration");
        if (state == null) return null;

        String value = state.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        if (value.contains("denied") || value.contains("searching")
                || value.contains("not_registered") || value.contains("unregistered")
                || value.equals("unknown")) return false;
        if (value.contains("registered") || value.contains("home")
                || value.contains("roaming")) return true;
        return null;
    }

    /**
     * Tên Wi-Fi router đang phát.
     *
     * <p>CHỈ đọc endpoint {@code status}. Bản {@code config} của cùng nhóm này trả kèm
     * {@code key} — mật khẩu Wi-Fi — và không có lý do gì để một màn hình trạng thái kéo
     * mật khẩu về máy, càng không có lý do để nó đi qua chỗ nào có thể bị ghi log.
     */
    private void readWifiName(RutApiClient api, RutStatusUiModel model) throws IOException {
        if (model.wifiSsid != null) return;   // Android đọc được rồi thì thôi

        JsonElement data = RutJson.data(api.get("/api/wireless/interfaces/status"));
        String firstEnabled = null;
        String firstAny = null;
        for (JsonObject iface : RutJson.objects(data, new String[]{"ssid"})) {
            String ssid = RutJson.string(iface, "ssid");
            if (ssid == null) continue;
            if (firstAny == null) firstAny = ssid;
            if (firstEnabled == null && isInterfaceUp(iface)) firstEnabled = ssid;
        }
        // Mạng ĐẦU TIÊN đang bật, không phải mạng cuối cùng: router hay phát thêm mạng
        // khách sau mạng chính, và ghi đè theo vòng lặp sẽ hiện tên mạng khách như thể đó
        // là mạng của xe.
        model.wifiSsid = firstEnabled != null ? firstEnabled : firstAny;
        Logger.appendLog(LOG_TAG, "Tên Wi-Fi từ router: " + model.wifiSsid);
    }

    /**
     * Giao diện Wi-Fi có đang bật hay không.
     *
     * <p>{@code disabled} KHÔNG được xếp chung danh sách với {@code up}/{@code enabled}: nó
     * mang nghĩa NGƯỢC LẠI. Trên firmware chỉ trả {@code disabled}, cách đọc gộp biến
     * {@code disabled:true} thành "mạng đang bật" và popup hiện tên một mạng đã tắt.
     */
    private static boolean isInterfaceUp(JsonObject iface) {
        Boolean up = RutJson.bool(iface, "up", "enabled");
        if (up != null) return up;
        Boolean disabled = RutJson.bool(iface, "disabled");
        return disabled == null || !disabled;
    }

    private void readSignal(RutApiClient api, RutStatusUiModel model) throws IOException {
        // Modem status của RUT955 đã mang sẵn RSRP/RSRQ/SINR. Đo thật: endpoint riêng bên
        // dưới trả HTTP 501 trên firmware này, nên gọi khi đã đủ số liệu chỉ là một lượt
        // chờ mạng không đổi lại được gì.
        if (model.rsrpDbm != null && model.rsrqDb != null && model.sinrDb != null) return;
        if (model.modemId == null) {
            Logger.appendLog(LOG_TAG, "Sóng: bỏ qua vì không đọc được modemId");
            return;
        }
        JsonObject signal = RutJson.firstObject(RutJson.data(
                api.get("/api/modems/signal/status/" + model.modemId)));
        if (signal == null) return;
        readSignalInto(signal, model);
    }

    /** Chỉ điền chỗ còn trống: nguồn đọc được trước không bị nguồn sau ghi đè bằng null. */
    private void readSignalInto(JsonObject source, RutStatusUiModel model) {
        if (model.rssiDbm == null)
            model.rssiDbm = RutJson.integer(source, "rssi", "signal", "signal_strength");
        if (model.rsrpDbm == null)
            model.rsrpDbm = RutJson.integer(source, "rsrp", "lte_rsrp");
        if (model.rsrqDb == null)
            model.rsrqDb = RutJson.integer(source, "rsrq", "lte_rsrq");
        if (model.sinrDb == null)
            model.sinrDb = RutJson.integer(source, "sinr", "lte_sinr", "snr");
        if (model.networkType == null)
            model.networkType = RutJson.string(source, "network_type", "net_type", "technology");
    }

    private void readSim(RutApiClient api, RutStatusUiModel model, Extras extras) {
        // active_sim đọc được từ modem status rồi thì thôi: /api/sim_switch/status/{id} trả
        // HTTP 501 trên RUT955 và chỉ tồn tại trên máy có chuyển khay tự động.
        if (model.modemId != null && model.activeSimSlot == null) {
            try {
                JsonObject slot = RutJson.firstObject(RutJson.data(
                        api.get("/api/sim_switch/status/" + model.modemId)));
                Integer active = RutJson.integer(slot,
                        "sim", "active_sim", "sim_id", "current");
                if (active != null) model.activeSimSlot = active;
            } catch (IOException ex) {
                // Không có khay SIM đôi thì endpoint này không tồn tại — đó là chuyện bình
                // thường, không phải lý do để bỏ luôn phần đọc thẻ bên dưới.
                Logger.appendLog(LOG_TAG, "SIM: không đọc được khay đang dùng: "
                        + Logger.describe(ex));
            }
        }

        JsonElement data = firstAnswer(api, SIM_ENDPOINTS, "SIM");
        if (data == null) return;

        List<JsonObject> sims = RutJson.objects(data, SIM_LEAF_KEYS);
        if (sims.isEmpty()) {
            // Router trả lời và danh sách thẻ RỖNG: đó là một câu trả lời, không phải một
            // chỗ thiếu dữ liệu — router không thấy thẻ nào.
            model.simPresent = false;
            Logger.appendLog(LOG_TAG, "SIM: router trả danh sách rỗng → không có SIM");
            return;
        }

        boolean anyKnown = false;
        boolean anyPresent = false;
        for (JsonObject sim : sims) {
            Boolean present = simPresence(sim);
            if (present != null) {
                anyKnown = true;
                if (present) anyPresent = true;
            }
            if (Boolean.TRUE.equals(pinLocked(sim))) extras.pinLocked = Boolean.TRUE;

            Boolean active = RutJson.bool(sim, "active", "in_use", "selected");
            if (model.activeSimSlot == null && Boolean.TRUE.equals(active))
                model.activeSimSlot = RutJson.integer(sim, "sim", "id", "slot", "index");
        }
        if (anyKnown) model.simPresent = anyPresent;

        Logger.appendLog(LOG_TAG, "SIM: " + sims.size() + " bản ghi, có thẻ="
                + model.simPresent + ", khay=" + model.activeSimSlot);
    }

    /**
     * Router có ĐỌC ĐƯỢC thẻ hay không.
     *
     * <p>Chú ý thứ tự: chuỗi phủ định của RutOS chứa nguyên chữ khẳng định
     * ({@code "not_inserted"} chứa {@code "inserted"}), nên phải loại phủ định TRƯỚC.
     * Không nhận ra chuỗi nào thì trả null — "router không nói" chứ không phải "không có SIM".
     */
    @Nullable
    static Boolean simPresence(@Nullable JsonObject source) {
        Boolean flag = RutJson.bool(source,
                "sim_present", "present", "inserted", "sim_inserted", "detected");
        if (flag != null) return flag;

        // "simstate" trước "state": trên RUT955, "state" là trạng thái KẾT NỐI
        // ("Disconnected") chứ không phải trạng thái thẻ ("Not inserted"). Hỏi nhầm khoá là
        // đọc nhầm hẳn một đại lượng khác.
        String state = RutJson.string(source,
                "simstate", "sim_state", "sim_status", "card_state", "state", "status", "sim");
        if (state == null) return null;

        String value = state.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        if (value.contains("not_inserted") || value.contains("not_present")
                || value.contains("no_sim") || value.contains("nosim")
                || value.contains("absent") || value.contains("missing")
                || value.contains("removed") || value.equals("none") || value.equals("empty"))
            return false;
        if (value.contains("inserted") || value.contains("present")
                || value.contains("ready") || value.contains("pin") || value.contains("puk")
                || value.contains("locked"))
            return true;
        return null;
    }

    /** Thẻ đang chờ PIN/PUK — router đọc được thẻ, chỉ là chưa mở khoá được. */
    @Nullable
    static Boolean pinLocked(@Nullable JsonObject source) {
        String state = RutJson.string(source,
                "pinstate", "pin_state", "pin_status", "sim_pin_state", "lock_state");
        if (state == null) state = RutJson.string(source, "simstate", "sim_state", "sim_status");
        if (state == null) return null;

        String value = state.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        if (value.contains("puk") || value.contains("pin_required")
                || value.contains("pin_locked") || value.contains("sim_pin")
                || value.contains("locked") || value.equals("pin"))
            return true;
        if (value.contains("ready") || value.contains("inserted") || value.contains("unlocked")
                || value.contains("disabled") || value.equals("ok"))
            return false;
        return null;
    }

    /** Dữ liệu của endpoint đầu tiên trả lời được; null nếu tất cả đều hỏng. */
    @Nullable
    private JsonElement firstAnswer(RutApiClient api, String[] endpoints, String what) {
        for (String endpoint : endpoints) {
            try {
                JsonElement data = RutJson.data(api.get(endpoint));
                if (data != null) return data;
                Logger.appendLog(LOG_TAG, what + ": " + endpoint + " trả body rỗng");
            } catch (RutApiClient.RutApiException ex) {
                Logger.appendLog(LOG_TAG, what + ": " + endpoint + " trả HTTP " + ex.httpCode);
            } catch (IOException ex) {
                Logger.appendLog(LOG_TAG, what + ": " + endpoint + " lỗi: "
                        + Logger.describe(ex));
            }
        }
        return null;
    }

    /**
     * Những mẩu router nói ra mà {@link RutStatusUiModel} chưa có chỗ chứa.
     *
     * <p>Tạm thời để ở đây vì file model đang do người khác giữ. Xem
     * {@link RutSimState#attach}.
     */
    private static final class Extras {
        @Nullable
        Boolean pinLocked;
        @Nullable
        Boolean registered;
        /** Data bị tắt bằng SMS "mobileoff". */
        @Nullable
        Boolean dataOff;
        /** Bước dựng kết nối của modem; 19 = đã xong. */
        @Nullable
        Integer mobileStage;
    }

    private String string(int resId) {
        return context.getString(resId);
    }

    private interface Step {
        void run() throws IOException;
    }

    /** Một phép đọc phụ hỏng chỉ mất đúng phần đó, không làm sập cả lần đọc. */
    private void safely(Step step) {
        try {
            step.run();
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Bỏ qua một phần dữ liệu router: "
                    + Logger.describe(ex));
        }
    }
}
