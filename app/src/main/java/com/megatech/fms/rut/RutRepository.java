package com.megatech.fms.rut;

/** Nguồn dữ liệu của popup. Tách interface để ViewModel kiểm chứng được bằng bản giả. */
public interface RutRepository {

    interface Callback<T> {
        void onResult(T value);

        void onError(String message);
    }

    /** Đọc toàn bộ trạng thái. Luôn chạy nền, callback trả về trên luồng giao diện. */
    void loadStatus(Callback<RutStatusUiModel> callback);

    /** Gửi lệnh khởi động lại router. */
    void rebootRouter(Callback<Void> callback);

    /**
     * Chỉ dựng lại phiên 4G của modem — Wi-Fi và LAN của router vẫn chạy. Trả về khi router
     * đã NHẬN lệnh; kết nối mới có hay chưa phải đọc lại trạng thái mới biết.
     */
    void restartMobileConnection(Callback<Void> callback);

    /**
     * Router đã trả lời lại được chưa — dùng để theo dõi sau khi reboot.
     * Trả true nghĩa là router đã lên và đăng nhập lại thành công.
     */
    void pingRouter(Callback<Boolean> callback);
}
