package com.megatech.fms.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.FMSApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Server trả "Kiểm tra ngoại quan" kèm dấu cách thừa ("C&amp;B       ", cột độ dài cố định).
 *
 * <p>Đo trên máy ảo 2026-09-28: phiếu 25.02 lưu là C&amp;B, sau lượt tải về mở lại thì màn hình
 * chọn "Khác" và hiện ô chữ "C&amp;B" — vì layout so {@code appearanceCheck.equals("C&B")}.
 * Getter gọt dấu cách; JSON gửi lên server vẫn giữ nguyên giá trị gốc.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = com.megatech.fms.FMSApplication.class)
public class AppearanceCheckPaddingTest {

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    @Before
    public void setUp() {
        // TruckFuelModel đọc xe hiện tại trong constructor
        TruckModel setting = new TruckModel();
        setting.setTruckId(34);
        ((FMSApplication) ApplicationProvider.getApplicationContext()).saveSetting(setting, false);
    }

    @Test
    public void phieu2502TuServerCoDauCachVanLaCB() {
        TruckFuelModel model = gson.fromJson("{\"AppearanceCheck\":\"C&B       \"}", TruckFuelModel.class);
        assertEquals("màn hình phải thấy C&B, không phải Khác", "C&B", model.getAppearanceCheck());
        assertTrue("không được đổi giá trị gửi lên server",
                model.toJson().contains("C\\u0026B       "));
    }

    @Test
    public void phieu2505TuServerCoDauCachVanLaCB() {
        BM2505Model model = gson.fromJson("{\"AppearanceCheck\":\"C&B       \"}", BM2505Model.class);
        assertEquals("màn hình phải thấy C&B, không phải Khác", "C&B", model.getAppearanceCheck());
    }
}
