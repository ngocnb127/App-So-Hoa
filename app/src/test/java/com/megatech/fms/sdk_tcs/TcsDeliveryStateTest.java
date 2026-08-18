package com.megatech.fms.sdk_tcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.sdk_tcs.sdk_tcs.tcs.TcsDevice;

import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Tiến độ mẻ phải sống lâu hơn luồng đọc.
 *
 * <p>Đo trên xe thật 18-08: mất kết nối giữa mẻ, đồng hồ bơm xong tới 2155 rồi về IDLE,
 * app nối lại nhưng không bao giờ nhận được tín hiệu kết thúc — số chốt nằm lại trên đồng
 * hồ, app đứng ở 1832. Nguyên nhân: {@code deliveryStarted} là biến CỤC BỘ của luồng đọc,
 * mất kết nối làm luồng chết và mang theo tiến độ mẻ.
 */
public class TcsDeliveryStateTest {

    /**
     * Ba biến tiến độ mẻ phải là TRƯỜNG của thiết bị, không phải biến cục bộ trong runTask.
     *
     * <p>Kiểm bằng phản chiếu vì đây là bất biến về NƠI ĐẶT dữ liệu — thứ mà một bài test
     * hành vi không nhìn thấy được, trong khi chính nó là nguyên nhân mất số liệu.
     */
    @Test
    public void deliveryProgressLivesOnTheDeviceNotThePollingThread() throws Exception {
        assertFieldExists("deliveryStarted");
        assertFieldExists("endingNotified");
        assertFieldExists("endingLoops");
    }

    /**
     * Ba biến đó bị đọc/ghi từ luồng đọc và từ luồng giao diện, nên phải volatile — nếu
     * không, luồng đọc mới có thể thấy giá trị cũ trong cache và bỏ qua pha kết thúc.
     */
    @Test
    public void deliveryProgressIsVisibleAcrossThreads() throws Exception {
        for (String name : new String[]{"deliveryStarted", "endingNotified", "endingLoops"}) {
            Field field = TcsDevice.class.getDeclaredField(name);
            assertTrue(name + " phải volatile",
                    java.lang.reflect.Modifier.isVolatile(field.getModifiers()));
        }
    }

    /** Mất kết nối KHÔNG được coi là mẻ đã kết thúc. */
    @Test
    public void disconnectDoesNotClearDeliveryProgress() throws Exception {
        TcsDevice device = new TcsDevice("127.0.0.1", 10001,
                null, null, null, null, null, null);

        setBoolean(device, "deliveryStarted", true);
        setBoolean(device, "endingNotified", true);

        // runTask thoát ngay vì chưa hề kết nối; đây đúng là đường đi khi mất kết nối.
        device.runTask();
        Thread.sleep(200);

        assertTrue("mẻ chưa kết thúc thì mất kết nối không được xoá tiến độ",
                getBoolean(device, "deliveryStarted"));
        assertTrue(getBoolean(device, "endingNotified"));
    }

    /**
     * Nút "Kết nối lại" gọi thẳng runTask. Không chặn thì bấm vài lần là vài luồng cùng đọc
     * một thiết bị và cùng bắn callback kết thúc mẻ.
     */
    @Test
    public void onlyOnePollingThreadCanRun() throws Exception {
        TcsDevice device = new TcsDevice("127.0.0.1", 10001,
                null, null, null, null, null, null);

        Field flag = TcsDevice.class.getDeclaredField("taskRunning");
        flag.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean running =
                (java.util.concurrent.atomic.AtomicBoolean) flag.get(device);

        running.set(true);          // giả lập luồng đọc đang chạy
        device.runTask();           // phải không làm gì

        assertTrue("cờ phải giữ nguyên, không có luồng thứ hai", running.get());
    }

    /** Chưa nhận gói nào thì mốc dữ liệu phải là 0, không được đoán là "vừa mới nhận". */
    @Test
    public void lastDataStartsAtZero() {
        TcsDevice device = new TcsDevice("127.0.0.1", 10001,
                null, null, null, null, null, null);

        assertEquals(0, device.getLastDataAt());
    }

    private void assertFieldExists(String name) throws Exception {
        Field field = TcsDevice.class.getDeclaredField(name);
        assertFalse(name + " không được là static",
                java.lang.reflect.Modifier.isStatic(field.getModifiers()));
    }

    private void setBoolean(TcsDevice device, String name, boolean value) throws Exception {
        Field field = TcsDevice.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(device, value);
    }

    private boolean getBoolean(TcsDevice device, String name) throws Exception {
        Field field = TcsDevice.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(device);
    }
}
