package com.megatech.fms.rut;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.megatech.fms.helpers.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Nói chuyện với RutOS Web API.
 *
 * <p>Ba luật của lớp này:
 * <ul>
 *   <li><b>Không log bí mật</b>: username, password và token không bao giờ được xuống
 *       {@code fms.log} — file đó được gửi lên máy chủ khi người dùng bấm "Gửi log".</li>
 *   <li><b>Refresh token đúng MỘT lần</b> cho mỗi request gặp 401. Đăng nhập lại trong
 *       vòng lặp là cách tự tạo ra bão đăng nhập và bị router khoá.</li>
 *   <li><b>Mọi request đi qua đúng Wi-Fi của router</b>: socket factory được bind vào
 *       {@code Network} của Wi-Fi, nếu không hệ điều hành có thể định tuyến sang 4G và cả
 *       phép xác minh trở nên vô nghĩa.</li>
 * </ul>
 */
public final class RutApiClient {

    private static final String LOG_TAG = "RUT";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Body chuẩn của mọi lệnh hành động trong RutOS. */
    private static final String ACTION_BODY = "{\"data\":{}}";

    /** Router trong LAN phải trả lời nhanh; chờ lâu chỉ làm popup treo. */
    private static final int TIMEOUT_SECONDS = 8;

    /** Ngoại lệ mang theo mã HTTP để lớp trên phân biệt sai mật khẩu với mất kết nối. */
    public static final class RutApiException extends IOException {
        public final int httpCode;

        public RutApiException(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }
    }

    private final RutConnectionConfig config;
    private final RutConfigStore store;
    private final OkHttpClient client;

    private final Object tokenLock = new Object();
    @Nullable
    private String token;

    public RutApiClient(RutConnectionConfig config, RutConfigStore store,
                        @Nullable SocketFactory boundSocketFactory) {
        this.config = config;
        this.store = store;
        this.client = buildClient(config, store, boundSocketFactory);
    }

    private static OkHttpClient buildClient(RutConnectionConfig config, RutConfigStore store,
                                            @Nullable SocketFactory boundSocketFactory) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false);

        if (boundSocketFactory != null) builder.socketFactory(boundSocketFactory);

        if (config.baseUrl.startsWith("https://")) {
            try {
                X509TrustManager trustManager = RutTls.trustManager(
                        config.pinnedCertificateSha256, store::rememberCertificate);
                builder.sslSocketFactory(
                        RutTls.socketFactory(config.pinnedCertificateSha256,
                                store::rememberCertificate),
                        trustManager);
                // Chứng thư tự ký của router mang CN không khớp địa chỉ IP. Danh tính đã
                // được bảo đảm bằng vân tay ghim ở trên, nên chỉ cần chặn đúng phạm vi:
                // duy nhất host của router đã cấu hình được bỏ qua bước so tên.
                String routerHost = host(config.baseUrl);
                builder.hostnameVerifier((hostname, session) ->
                        routerHost != null && routerHost.equalsIgnoreCase(hostname));
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Không dựng được TLS cho router: "
                        + Logger.describe(ex));
            }
        }
        return builder.build();
    }

    @Nullable
    private static String host(String baseUrl) {
        try {
            return okhttp3.HttpUrl.parse(baseUrl) == null
                    ? null : okhttp3.HttpUrl.parse(baseUrl).host();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ đăng nhập

    /**
     * Lấy token, đăng nhập nếu chưa có. Token của RutOS sống khoảng 5 phút nên không cố
     * giữ lâu: hết hạn thì đường 401 bên dưới tự lấy token mới.
     */
    private String token() throws IOException {
        synchronized (tokenLock) {
            if (token != null) return token;
            token = login();
            return token;
        }
    }

    /** Buộc lấy token mới. Trả về token vừa nhận. */
    private String refreshToken() throws IOException {
        synchronized (tokenLock) {
            token = null;
            token = login();
            return token;
        }
    }

    private String login() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", config.username);
        body.addProperty("password", config.password);

        Request request = new Request.Builder()
                .url(config.url("/api/login"))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String payload = response.body() == null ? null : response.body().string();
            if (response.code() == 401 || response.code() == 403)
                throw new RutApiException("Sai tài khoản quản trị router", response.code());
            if (!response.isSuccessful())
                throw new RutApiException("Router từ chối đăng nhập", response.code());

            JsonObject parsed = RutJson.parseObject(payload);
            JsonObject data = RutJson.firstObject(RutJson.data(parsed));
            String value = RutJson.string(data, "token", "session_id", "sessionId", "ubus_rpc_session");
            if (value == null)
                throw new RutApiException("Router không trả token đăng nhập", response.code());
            // KHÔNG log token.
            Logger.appendLog(LOG_TAG, "Đăng nhập router thành công");
            return value;
        }
    }

    // ------------------------------------------------------------------ gọi API

    @Nullable
    public JsonObject get(String path) throws IOException {
        return execute(client, new Request.Builder().url(config.url(path)).get(), true);
    }

    @Nullable
    public JsonObject post(String path) throws IOException {
        return execute(client, new Request.Builder().url(config.url(path))
                .post(RequestBody.create(ACTION_BODY, JSON)), true);
    }

    /**
     * Lệnh chẩn đoán chạy trên router (ping, nslookup), chờ lâu hơn mức thường.
     *
     * <p>Router chỉ trả lời SAU KHI lệnh ping chạy xong, và ca cần đo nhất — mạng hỏng, mọi
     * gói đều hết giờ — cũng là ca ping chạy lâu nhất. Giữ nguyên 8 giây thì đúng ca đó
     * thành "không đọc được" thay vì "không ra được Internet".
     */
    @Nullable
    public JsonObject postWithData(String path, JsonObject data, int timeoutSeconds)
            throws IOException {
        JsonObject body = new JsonObject();
        body.add("data", data);
        OkHttpClient slow = client.newBuilder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
        return execute(slow, new Request.Builder().url(config.url(path))
                .post(RequestBody.create(body.toString(), JSON)), true);
    }

    /**
     * @param allowRetry còn được phép làm mới token hay không. Chỉ đúng ở lần gọi đầu, nên
     *                   một request hỏng vì token chỉ sinh ra đúng một lần đăng nhập lại.
     */
    @Nullable
    private JsonObject execute(OkHttpClient http, Request.Builder builder, boolean allowRetry)
            throws IOException {
        Request request = builder.header("Authorization", "Bearer " + token()).build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 401 && allowRetry) {
                refreshToken();
                return execute(http, builder, false);
            }
            if (response.code() == 401 || response.code() == 403)
                throw new RutApiException("Router từ chối phiên làm việc", response.code());
            if (!response.isSuccessful())
                throw new RutApiException("Router trả lỗi", response.code());

            return RutJson.parseObject(response.body() == null ? null : response.body().string());
        } catch (SSLHandshakeException ex) {
            throw new RutApiException(
                    "Chứng thư bảo mật của router không khớp lần ghép cặp", 0);
        }
    }
}
