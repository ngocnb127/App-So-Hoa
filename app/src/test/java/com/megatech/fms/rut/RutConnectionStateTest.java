package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * VÌ SAO đáng test: {@code allowsReboot()} là chốt chặn cuối trước một hành động phá
 * hoại được — khởi động lại một thiết bị mạng. Nếu nó nới ra cho một state "gần đúng"
 * (ví dụ PHONE_USING_CELLULAR, nơi ta chưa chứng minh được đang nói chuyện với router của
 * xe), người dùng sẽ reboot nhầm thiết bị của người khác trong cùng dải mạng. Vì vậy test
 * duyệt HẾT các giá trị enum thay vì liệt kê tay, để một state mới thêm sau này mà mặc
 * định cho phép reboot sẽ làm đỏ ngay.
 */
public class RutConnectionStateTest {

    @Test
    public void allows_reboot_only_when_the_router_has_actually_answered() {
        assertTrue(RutConnectionState.VERIFIED_THROUGH_RUT.allowsReboot());
        assertTrue(RutConnectionState.CONNECTED_TO_RUT_NO_INTERNET.allowsReboot());
    }

    @Test
    public void forbids_reboot_in_every_other_state() {
        int allowed = 0;
        for (RutConnectionState state : RutConnectionState.values()) {
            if (state == RutConnectionState.VERIFIED_THROUGH_RUT
                    || state == RutConnectionState.CONNECTED_TO_RUT_NO_INTERNET) {
                allowed++;
                continue;
            }
            assertFalse("State " + state + " không được phép reboot", state.allowsReboot());
        }
        assertEquals("Chỉ đúng hai state được phép reboot", 2, allowed);
    }
}
