package com.megatech.fms.rut;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BÀI DÒ, KHÔNG PHẢI TEST NGHIỆM THU.
 *
 * <p>Chạy trên chính tablet đang nối Wi-Fi của router để chụp lại response THÔ của RutOS.
 * Lý do phải làm việc này thay vì đọc tài liệu: tên field và hình dạng response khác nhau
 * giữa model và firmware, và chiếc router ngoài hiện trường là RUT955 — đời cũ hơn hẳn các
 * ví dụ trong tài liệu. Mọi suy đoán về schema đều phải đối chiếu với cái máy thật trả về.
 *
 * <p>Bài dò này KHÔNG nằm trong APK phát hành: nó ở source set androidTest. Nó cố ý bỏ qua
 * kiểm tra chứng thư — điều tuyệt đối cấm trong mã sản phẩm (xem {@link RutTls}) nhưng chấp
 * nhận được ở một phép đo dùng một lần trong LAN, khi mục tiêu chính là đọc được nội dung
 * response để biết phải viết parser thế nào.
 *
 * <p>Chạy: {@code ./gradlew :app:connectedDebugAndroidTest --tests '*RutProbe*'}
 * rồi đọc logcat theo tag {@code RUT_PROBE}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RutProbeInstrumentedTest {

    private static final String TAG = "RUT_PROBE";
    private static final String BASE = "https://192.168.1.1";
    private static final String USER = "admin";
    private static final String PASS = "Abcd1234";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Test
    public void dumpEveryEndpointTheStatusPopupDependsOn() throws Exception {
        OkHttpClient client = probeClient();

        Log.i(TAG, "===== BẮT ĐẦU DÒ " + BASE + " =====");

        // 1. Router có phải RutOS đời có REST API hay không: thử trang gốc trước.
        dump(client, "GET", "/", null);

        // 2. Đăng nhập. Nếu bước này hỏng thì mọi endpoint bên dưới đều vô nghĩa.
        String token = null;
        String loginBody = "{\"username\":\"" + USER + "\",\"password\":\"" + PASS + "\"}";
        String loginResponse = dump(client, "POST", "/api/login", loginBody);
        if (loginResponse != null) token = extractToken(loginResponse);
        Log.i(TAG, "TOKEN: " + (token == null ? "KHÔNG LẤY ĐƯỢC" : "đã có (không in ra)"));

        // 3. Toàn bộ endpoint popup đang phụ thuộc, kèm vài biến thể đường dẫn để biết
        //    firmware này thực sự phục vụ đường nào.
        String[] paths = {
                "/api/system/device/status",
                "/api/system/status",
                "/api/devices/status",
                "/api/modems/status",
                "/api/sim_cards/status",
                "/api/interfaces/status",
                "/api/ip_routes/ipv4/status",
                // Tên Wi-Fi hỏi thẳng router: Android 13+ che SSID nếu thiếu quyền
                // NEARBY_WIFI_DEVICES, mà router thì biết chính xác tên nó phát.
                "/api/wireless/interfaces/status",
                "/api/wireless/devices/status",
                "/api/wireless/interfaces/config",
        };
        for (String path : paths) dumpAuthed(client, path, token);

        // 4. modemId đọc được từ /api/modems/status quyết định hai endpoint còn lại. In ra
        //    để đối chiếu bằng mắt xem id nằm ở field hay ở KHOÁ của object.
        String modems = dumpAuthed(client, "/api/modems/status", token);
        for (String id : guessModemIds(modems)) {
            dumpAuthed(client, "/api/modems/signal/status/" + id, token);
            dumpAuthed(client, "/api/sim_switch/status/" + id, token);
            dumpAuthed(client, "/api/modems/status/" + id, token);
        }

        Log.i(TAG, "===== KẾT THÚC DÒ =====");
    }

    private String dumpAuthed(OkHttpClient client, String path, String token) {
        Request.Builder builder = new Request.Builder().url(BASE + path).get();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return execute(client, builder, path);
    }

    private String dump(OkHttpClient client, String method, String path, String body) {
        Request.Builder builder = new Request.Builder().url(BASE + path);
        if ("POST".equals(method)) builder.post(RequestBody.create(body, JSON));
        else builder.get();
        return execute(client, builder, path);
    }

    private String execute(OkHttpClient client, Request.Builder builder, String path) {
        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            Log.i(TAG, path + " -> HTTP " + response.code()
                    + " server=" + response.header("Server")
                    + " len=" + body.length());
            // Logcat cắt dòng dài, nên chia khúc để không mất phần đuôi.
            for (int i = 0; i < body.length() && i < 4000; i += 900)
                Log.i(TAG, path + " | " + body.substring(i, Math.min(body.length(), i + 900)));
            return body;
        } catch (Exception ex) {
            Log.i(TAG, path + " -> LỖI " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    /** Bóc token thủ công để bài dò không phụ thuộc vào parser đang được sửa song song. */
    private String extractToken(String json) {
        int at = json.indexOf("\"token\"");
        if (at < 0) return null;
        int start = json.indexOf('"', json.indexOf(':', at) + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        return start < 0 || end < 0 ? null : json.substring(start + 1, end);
    }

    /** Vài id khả dĩ, để biết chắc phải lấy id ở field hay ở khoá object. */
    private String[] guessModemIds(String modemsJson) {
        if (modemsJson == null) return new String[]{"1-1"};
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher field = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(modemsJson);
        while (field.find()) ids.add(field.group(1));
        java.util.regex.Matcher key = java.util.regex.Pattern
                .compile("\"([0-9]+-[0-9]+(\\.[0-9]+)*)\"\\s*:\\s*\\{").matcher(modemsJson);
        while (key.find()) ids.add(key.group(1));
        if (ids.isEmpty()) ids.add("1-1");
        Log.i(TAG, "modemId ứng viên: " + ids);
        return ids.toArray(new String[0]);
    }

    private OkHttpClient probeClient() throws Exception {
        TrustManager[] acceptAll = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                for (X509Certificate certificate : chain)
                    Log.i(TAG, "chứng thư router: subject=" + certificate.getSubjectDN());
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, acceptAll, new java.security.SecureRandom());
        SSLSocketFactory factory = context.getSocketFactory();

        return new OkHttpClient.Builder()
                .sslSocketFactory(factory, (X509TrustManager) acceptAll[0])
                .hostnameVerifier((hostname, session) -> true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }
}
