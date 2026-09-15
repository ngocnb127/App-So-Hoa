package com.megatech.fms.rut;

import android.net.Network;

import androidx.annotation.Nullable;

import com.megatech.fms.BuildConfig;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Kiểm tra Internet có thực sự đi qua Wi-Fi của router hay không.
 *
 * <p>Điểm mấu chốt: request PHẢI bind vào {@code Network} của Wi-Fi. Dùng OkHttp mặc định
 * thì hệ điều hành tự chọn đường ra — và khi Wi-Fi bị đánh giá là không có Internet, nó
 * chọn 4G của điện thoại. Khi đó phép thử luôn thành công và kết luận "Internet đi qua
 * router" là sai.
 *
 * <p>Đích thử là chính máy chủ của ứng dụng: đó là đường mà nghiệp vụ thật sự cần đi được,
 * chứ không phải một máy chủ công cộng nào đó. Nonce chống việc một tầng cache trả lời thay
 * cho mạng.
 */
public final class InternetVerifier {

    private static final int TIMEOUT_SECONDS = 6;

    private InternetVerifier() {
    }

    /**
     * @return true khi máy chủ TRẢ LỜI qua đúng Wi-Fi. Mã HTTP nào cũng được tính là ra
     * được Internet — kể cả 404: điều cần chứng minh là gói tin đi tới nơi và quay về, chứ
     * không phải endpoint đó tồn tại.
     */
    public static boolean canReachInternet(@Nullable Network wifiNetwork,
                                           @Nullable OkHttpClient baseClient) {
        if (wifiNetwork == null) return false;

        OkHttpClient.Builder builder = baseClient == null
                ? new OkHttpClient.Builder() : baseClient.newBuilder();
        OkHttpClient client = builder
                .socketFactory(wifiNetwork.getSocketFactory())
                // Bind CẢ phân giải tên miền, không chỉ socket. socketFactory chỉ ràng buộc
                // kết nối TCP; việc tra tên miền vẫn chạy trên mạng mặc định của hệ thống —
                // tức là 4G, đúng cái ta đang muốn loại trừ. Thiếu dòng này thì phép xác
                // minh vẫn có một nửa đi qua đường cellular và kết luận không đáng tin.
                .dns(hostname -> java.util.Arrays.asList(wifiNetwork.getAllByName(hostname)))
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();

        Request request = new Request.Builder()
                .url(checkUrl())
                .header("Cache-Control", "no-store")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    static String checkUrl() {
        String base = BuildConfig.API_BASE_URL;
        if (!base.endsWith("/")) base = base + "/";
        return base + "api/network-check?nonce=" + UUID.randomUUID();
    }
}
