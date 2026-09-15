package com.megatech.fms.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Người đang đăng nhập phải là người được ghi vào biểu mẫu (người tạo, người thực hiện).
 *
 * <p>Lỗi thật (2026-09-15): biểu mẫu 25 ghi đúng người ở ca đầu, nhưng hết ca đăng xuất và
 * đăng nhập tài khoản khác thì vẫn ghi người ca trước. {@code FMSApplication} giữ UserInfo
 * trong biến static và chỉ nạp lại khi rỗng; đăng nhập chỉ ghi SharedPreferences nên cache
 * không bao giờ đổi cho tới khi app bị tắt hẳn. Token gửi API cũng là của người ca trước.
 */
public class UserSessionCacheTest {

    private static UserInfo user(int id, String token) {
        return new UserInfo(id, "user" + id, token, 0, "", id);
    }

    @Test
    public void doiTaiKhoanThiCachePhaiNapLai() {
        UserInfo caTruoc = user(7, "token-a");

        assertFalse("đăng nhập tài khoản khác mà vẫn dùng người ca trước",
                UserInfo.isSameSession(caTruoc, 9, "token-b"));
    }

    @Test
    public void cungNguoiDangNhapLaiThiLayTokenMoi() {
        UserInfo phienCu = user(7, "token-a");

        assertFalse("cùng người đăng nhập lại phải dùng token mới, không giữ token cũ",
                UserInfo.isSameSession(phienCu, 7, "token-a2"));
    }

    @Test
    public void daDangXuatThiKhongGiuNguoiCu() {
        UserInfo caTruoc = user(7, "token-a");

        assertFalse("đăng xuất rồi (USER_ID/TOKEN đã xoá) mà vẫn trả về người ca trước",
                UserInfo.isSameSession(caTruoc, 0, ""));
    }

    @Test
    public void cungPhienThiDungCache() {
        assertTrue(UserInfo.isSameSession(user(7, "token-a"), 7, "token-a"));
    }

    @Test
    public void chuaCoCacheThiNapLai() {
        assertFalse(UserInfo.isSameSession(null, 7, "token-a"));
        assertFalse(UserInfo.isSameSession(new UserInfo(), 0, ""));
    }

    /** getUser() phải so cache với SharedPreferences, logout() phải xoá cache. */
    @Test
    public void getUserLuonSoVoiPhienDangLuu() throws IOException {
        String src = source("src/main/java/com/megatech/fms/FMSApplication.java");

        assertTrue("getUser() phải kiểm tra cache bằng UserInfo.isSameSession",
                src.contains("UserInfo.isSameSession("));
        assertFalse("không quay lại kiểu chỉ nạp khi cache rỗng",
                src.contains("if (_user == null || _user.getUserId() <=0)"));

        int logout = src.indexOf("public void logout()");
        assertTrue("không tìm thấy logout()", logout >= 0);
        int end = src.indexOf("}", logout);
        assertTrue("logout() phải xoá cache người dùng",
                src.substring(logout, end).contains("_user = null"));
    }

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
