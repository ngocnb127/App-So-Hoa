package com.megatech.fms.helpers.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.BuildConfig;

import org.junit.Test;

/**
 * Chạy cho TỪNG build type: ./gradlew testReleaseUnitTest testThermalUnitTest
 * testDemoUnitTest testDemo_thermalUnitTest testDebugUnitTest
 *
 * Mục đích chính là chặn hồi quy trên các biến thể không được phép tự cập nhật.
 */
public class UpdateChannelTest {

    /**
     * demo và demo_thermal dùng chung applicationId với bản production, nên nếu chúng bật
     * updater thì máy test sẽ được mời cài đè APK production và đổi luôn tệp cơ sở dữ liệu.
     */
    @Test
    public void chiReleaseVaThermalDuocBatUpdater() {
        String channel = BuildConfig.UPDATE_CHANNEL;
        if (channel == null) {
            assertFalse("Kênh null thì updater phải tắt", UpdateChannel.isEnabled());
        } else {
            assertTrue("Kênh lạ không được bật updater: " + channel,
                    UpdateChannel.RELEASE.equals(channel) || UpdateChannel.THERMAL.equals(channel));
            assertTrue(UpdateChannel.isEnabled());
        }
    }

    /**
     * THERMAL_PRINTER mô tả loại máy in, không phải kênh phát hành. Bản debug đặt cờ này
     * bằng true nhưng vẫn phải tắt updater — nếu quyết định kênh còn suy ra từ nó thì
     * khẳng định dưới đây sẽ hỏng.
     */
    @Test
    public void khongSuyRaKenhTuLoaiMayIn() {
        if (BuildConfig.UPDATE_CHANNEL == null) {
            assertFalse(UpdateChannel.isEnabled());
        }
    }

    @Test
    public void urlApkDungTienToTheoKenh() {
        if (!UpdateChannel.isEnabled()) return;

        String url = UpdateChannel.apkUrl("104-20260812.1");
        if (UpdateChannel.THERMAL.equals(UpdateChannel.current())) {
            // Giữ tiền tố legacy "thermal-" mà các bản đã phát hành đang dùng.
            assertTrue(url, url.endsWith("/files/thermal-104-20260812.1.apk"));
        } else {
            assertTrue(url, url.endsWith("/files/fms-release-104-20260812.1.apk"));
        }
        assertFalse("URL không được có // ở phần đường dẫn", url.contains(".vn//"));
    }

    @Test
    public void urlMetadataDungKenh() {
        if (!UpdateChannel.isEnabled()) return;

        String url = UpdateChannel.metadataUrl();
        if (UpdateChannel.THERMAL.equals(UpdateChannel.current())) {
            assertEquals("https://fmsapi.skypec.com.vn/files/thermalUpdate.txt", url);
        } else {
            assertEquals("https://fmsapi.skypec.com.vn/files/versionUpdate.txt", url);
        }
    }
}
