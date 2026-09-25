package com.megatech.fms.model;

/**
 * Kết quả một lần POST phiếu BM 75.01 lên server.
 *
 * <p>Khác các API cũ (trả thẳng model hoặc null): ở đây cần giữ lại <b>mã HTTP</b> vì cách xử lý
 * khác hẳn nhau giữa 409 (dừng, chờ nhân viên), 401 (đăng nhập lại) và 5xx/404 (thử lại sau) —
 * xem {@code BM7501SyncPolicy}.
 */
public class BM7501PostResult {

    /** 0 nghĩa là không gọi được server (mất mạng, timeout). */
    private final int httpCode;
    private final boolean success;
    private final String message;
    private final int id;
    private final String serverNumber;

    public BM7501PostResult(int httpCode, boolean success, String message, int id, String serverNumber) {
        this.httpCode = httpCode;
        this.success = success;
        this.message = message;
        this.id = id;
        this.serverNumber = serverNumber;
    }

    public static BM7501PostResult noNetwork(String message) {
        return new BM7501PostResult(0, false, message, 0, null);
    }

    public int getHttpCode() { return httpCode; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getId() { return id; }
    public String getServerNumber() { return serverNumber; }
}
