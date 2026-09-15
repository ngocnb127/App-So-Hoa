package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Kết luận cuối cùng về SIM.
 *
 * <p>VÌ SAO đáng test: bốn trạng thái ở đây in ra bốn hướng dẫn KHÁC NHAU cho người đang
 * đứng cạnh xe bồn — rút thẻ ra cắm lại, gọi quản trị mở PIN, kiểm tra tài khoản/vùng phủ,
 * hoặc không phải làm gì cả. Xếp nhầm trạng thái không làm ứng dụng đỏ ở đâu; nó chỉ khiến
 * một người mất buổi sáng để làm sai việc. Điểm tinh tế nhất: mọi tham số đều nullable và
 * null = "router KHÔNG NÓI" — null không bao giờ được hiểu thành false.
 */
public class RutSimStateTest {

    /**
     * Ca đo thật trên RUT955 khay rỗng: simstate "Not inserted", pinstate "SIM not
     * inserted", netstate "Searching", state "Disconnected".
     */
    @Test
    public void reports_no_sim_when_the_router_says_the_tray_is_empty() {
        assertEquals(RutSimState.NO_SIM,
                RutSimState.classify(false, false, false, false));
        assertEquals(RutSimState.NO_SIM,
                RutSimState.classify(false, null, null, null));
    }

    /**
     * Thẻ đọc được nhưng chờ PIN: việc của quản trị, KHÔNG phải việc rút cắm. Gộp ca này
     * vào NO_SIM là bảo người ngoài bãi đi làm một việc chắc chắn không giải quyết gì.
     */
    @Test
    public void reports_a_pin_lock_separately_from_a_missing_card() {
        RutSimState state = RutSimState.classify(true, true, false, false);

        assertEquals(RutSimState.PIN_LOCKED, state);
        assertTrue("Khoá PIN vẫn là có thẻ trong máy", state.hasSim());
        assertFalse("Không phải ca tự xử lý được tại xe", state.isFixableOnSite());
    }

    /**
     * Thẻ tốt, modem không vào được mạng: hết tiền, hết dung lượng, hoặc ngoài vùng phủ.
     * Rút cắm lại không giải quyết được gì — nên phải là một trạng thái riêng.
     */
    @Test
    public void reports_a_present_card_that_could_not_register() {
        RutSimState state = RutSimState.classify(true, false, false, false);

        assertEquals(RutSimState.SIM_PRESENT_NOT_REGISTERED, state);
        assertTrue(state.hasSim());
        assertFalse(state.isFixableOnSite());
    }

    @Test
    public void reports_a_registered_card_in_the_normal_case() {
        assertEquals(RutSimState.SIM_REGISTERED,
                RutSimState.classify(true, false, true, true));
        assertEquals(RutSimState.SIM_REGISTERED,
                RutSimState.classify(true, false, true, false));
    }

    /**
     * Có PHIÊN DỮ LIỆU thì thắng mọi thứ khác: modem đang truyền được dữ liệu nghĩa là thẻ
     * có, PIN đã mở và mạng đã đăng ký, dù các field kia im lặng hay mâu thuẫn. Bằng chứng
     * mạnh nhất không được để một field phụ lỗi thời lật ngược.
     */
    @Test
    public void lets_a_live_data_session_outweigh_every_other_field() {
        assertEquals(RutSimState.SIM_REGISTERED,
                RutSimState.classify(null, null, null, true));
        assertEquals(RutSimState.SIM_REGISTERED,
                RutSimState.classify(false, true, false, true));
    }

    /**
     * Ca quan trọng nhất: ROUTER IM LẶNG. Firmware không khai field nào về SIM thì kết luận
     * phải là "không biết", KHÔNG phải "không có SIM" — nếu không, mỗi bản firmware không
     * hỗ trợ mấy field này sẽ khiến ứng dụng bảo mọi người đi rút thẻ ra cắm lại.
     */
    @Test
    public void stays_unknown_when_the_router_says_nothing_at_all() {
        RutSimState state = RutSimState.classify(null, null, null, null);

        assertEquals(RutSimState.UNKNOWN, state);
        assertFalse("UNKNOWN không được kéo theo lời khuyên rút cắm thẻ",
                state.isFixableOnSite());
        assertFalse(state.hasSim());
    }

    /**
     * Có thẻ nhưng router im lặng về đăng ký mạng: chỉ dám nói "chưa vào được mạng" khi
     * router thực sự PHỦ ĐỊNH. Im lặng mà bị đọc thành phủ định là dựng ra một sự cố không
     * tồn tại và đẩy người dùng đi kiểm tra tài khoản nhà mạng vô ích.
     */
    @Test
    public void stays_unknown_when_a_card_is_present_but_registration_was_never_reported() {
        assertEquals(RutSimState.UNKNOWN, RutSimState.classify(true, null, null, null));
        assertEquals(RutSimState.UNKNOWN, RutSimState.classify(true, false, null, null));
    }

    /** {@code of()} phải nói "không biết" cho model rỗng, không mượn tạm một ca cụ thể. */
    @Test
    public void reports_unknown_for_a_model_that_was_never_filled_in() {
        assertEquals(RutSimState.UNKNOWN, RutSimState.of(null));
        assertEquals(RutSimState.UNKNOWN, RutSimState.of(new RutStatusUiModel()));

        RutStatusUiModel model = new RutStatusUiModel();
        RutSimState.attach(model, RutSimState.NO_SIM);
        assertEquals(RutSimState.NO_SIM, RutSimState.of(model));
    }
}
