package com.megatech.fms;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Ràng buộc của màn XÁC NHẬN mà không unit test nào khác giữ hộ.
 *
 * <p>Room chặn cứng mọi truy vấn trên luồng giao diện ({@code assertNotMainThread}). Chốt đó
 * là của Room, KHÔNG phải của StrictMode — nên {@code StrictMode.permitAll()} ở
 * {@code BaseActivity} không che được. Gọi một hàm đọc Room từ trong {@code onClick} là văng
 * app, không phải chậm máy.
 *
 * <p>Đo trên xe thật 06-09-2026: bốn lần liên tiếp cùng một stack, mỗi lần bấm Xác nhận đều
 * văng, cả hai cách kết thúc mẻ. Không ai chốt được mẻ nào.
 */
public class RefuelConfirmScreenGuardTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String confirmScreen() throws IOException {
        return source("src/main/java/com/megatech/fms/RefuelDetailConfirmActivity.java");
    }

    /**
     * Câu hỏi "mẻ này có mang dấu xe khác không" đọc Room, nên phải nằm TRONG luồng nền.
     *
     * <p>Canh bằng vị trí tương đối thay vì bằng tên hàm bao: nó phải đứng SAU
     * {@code doInBackground} của {@code postData()}. Đặt lại lên trên là tái hiện đúng lỗi cũ.
     */
    @Test
    public void hoiQuyenGhiPhaiNamTrongLuongNen() throws IOException {
        String src = confirmScreen();

        int postData = src.indexOf("private void postData()");
        assertTrue("không tìm thấy postData()", postData > 0);

        int background = src.indexOf("doInBackground", postData);
        assertTrue("postData() phải chạy phần lưu trong luồng nền", background > postData);

        int classify = src.indexOf("DataHelper.isForeignTruckRefuel", postData);
        assertTrue("không tìm thấy lượt phân loại quyền ghi trong postData()", classify > 0);
        assertTrue("isForeignTruckRefuel() đọc Room nên PHẢI gọi trong doInBackground —"
                        + " gọi trên luồng giao diện là văng app ngay khi bấm Xác nhận",
                classify > background);
    }

    /**
     * Phải hỏi quyền ghi TRƯỚC khi đóng dấu số xe hiện tại lên mẻ.
     *
     * <p>Đóng dấu trước thì mọi mẻ đều trông như của xe này, và cảnh báo tiếp quản chuyến
     * chưa phân công không bao giờ hiện. Chuyển lượt hỏi xuống luồng nền rất dễ làm hỏng
     * đúng thứ tự này, nên canh luôn.
     */
    @Test
    public void hoiQuyenGhiTruocKhiDongDauSoXe() throws IOException {
        String src = confirmScreen();

        int postData = src.indexOf("private void postData()");
        int classify = src.indexOf("DataHelper.isForeignTruckRefuel", postData);
        int stamp = src.indexOf("mItem.setTruckNo(currentApp.getTruckNo())", postData);

        assertTrue("không tìm thấy lượt đóng dấu số xe", stamp > 0);
        assertTrue("phải phân loại quyền ghi TRƯỚC khi đóng dấu số xe hiện tại lên mẻ",
                classify < stamp);
    }
}
