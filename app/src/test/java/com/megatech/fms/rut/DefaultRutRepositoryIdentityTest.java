package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * VÌ SAO đáng test: {@code isExpectedRouter} là thứ duy nhất ngăn ứng dụng hiển thị — và
 * cho phép reboot — một chiếc router LẠ trong cùng dải địa chỉ. Tên Wi-Fi và địa chỉ LAN
 * đều đặt trùng được; serial thì không. Nới lỏng hàm này một chút là mở đường cho việc
 * đọc số liệu của thiết bị người khác rồi khởi động lại nó.
 */
public class DefaultRutRepositoryIdentityTest {

    private static RutConnectionConfig paired(String serial) {
        return new RutConnectionConfig("https://192.168.1.1", "admin", "pw", serial, null);
    }

    private static RutConnectionConfig unpaired() {
        return new RutConnectionConfig("https://192.168.1.1", "admin", "pw", null, null);
    }

    private static RutStatusUiModel router(String model, String serial) {
        RutStatusUiModel value = new RutStatusUiModel();
        value.routerModel = model;
        value.routerSerial = serial;
        return value;
    }

    @Test
    public void accepts_a_paired_router_whose_serial_matches_ignoring_case() {
        assertTrue(DefaultRutRepository.isExpectedRouter(
                paired("1122334455"), router("RUTX50", "1122334455")));
        assertTrue(DefaultRutRepository.isExpectedRouter(
                paired("ab12cd"), router("RUTX50", "AB12CD")));
    }

    /**
     * Ca then chốt: đúng dòng RUT, đúng mạng, nhưng SAI serial. Chỉ dựa vào model là chấp
     * nhận bất kỳ router Teltonika nào của đơn vị khác đỗ cạnh xe.
     */
    @Test
    public void rejects_a_different_serial_even_when_the_model_is_a_genuine_rut() {
        assertFalse(DefaultRutRepository.isExpectedRouter(
                paired("1122334455"), router("RUTX50", "9988776655")));
    }

    /** Đã ghép cặp mà router không khai serial thì không có bằng chứng gì để tin. */
    @Test
    public void rejects_a_paired_router_that_reports_no_serial() {
        assertFalse(DefaultRutRepository.isExpectedRouter(
                paired("1122334455"), router("RUTX50", null)));
    }

    @Test
    public void accepts_an_unpaired_router_whose_model_carries_the_rut_prefix_in_any_case() {
        assertTrue(DefaultRutRepository.isExpectedRouter(unpaired(), router("RUTX50", "1")));
        assertTrue(DefaultRutRepository.isExpectedRouter(unpaired(), router("RUT956", "1")));
        assertTrue(DefaultRutRepository.isExpectedRouter(unpaired(), router("rutx50", "1")));
        assertTrue(DefaultRutRepository.isExpectedRouter(unpaired(), router("RuT241", "1")));
    }

    @Test
    public void rejects_an_unpaired_device_that_is_not_a_rut_at_all() {
        assertFalse(DefaultRutRepository.isExpectedRouter(unpaired(), router("TP-Link", "1")));
        assertFalse(DefaultRutRepository.isExpectedRouter(unpaired(), router(null, "1")));
        assertFalse("Tiền tố phải ở ĐẦU MỘT TỪ; MYRUTX50 là thiết bị khác hẳn",
                DefaultRutRepository.isExpectedRouter(unpaired(), router("MYRUTX50", "1")));
    }

    /**
     * Ranh giới TỪ chứ không phải đầu chuỗi: máy thật khai model "Teltonika RUT9XX" và
     * device_name "RUT955". Nếu chỗ này siết lại thành {@code startsWith} thuần thì chính
     * chiếc router đã dò được sẽ bị ứng dụng từ chối là "router lạ".
     */
    @Test
    public void accepts_the_model_string_the_real_rut955_reports() {
        assertTrue(DefaultRutRepository.isExpectedRouter(
                unpaired(), router("RUT955", "1106343995")));
        assertTrue(DefaultRutRepository.isExpectedRouter(
                unpaired(), router("Teltonika RUT9XX", "1106343995")));
    }

    /**
     * Serial rỗng/khoảng trắng trong cấu hình phải được coi là CHƯA ghép cặp; nếu không,
     * so sánh "" với serial thật luôn sai và popup vĩnh viễn báo sai router.
     */
    @Test
    public void treats_a_blank_configured_serial_as_not_yet_paired() {
        RutConnectionConfig blank =
                new RutConnectionConfig("https://192.168.1.1", "admin", "pw", "   ", null);

        assertFalse(blank.isPaired());
        assertTrue(DefaultRutRepository.isExpectedRouter(blank, router("RUTX50", "1122334455")));
    }

    /** url() không được sinh ra hai dấu gạch chéo liền nhau — RutOS trả 404 cho đường đó. */
    @Test
    public void builds_api_urls_without_doubling_the_slash() {
        RutConnectionConfig trailing =
                new RutConnectionConfig("https://192.168.1.1///", "admin", "pw", null, null);

        assertEquals("https://192.168.1.1/api/login", trailing.url("/api/login"));
        assertEquals("https://192.168.1.1/api/login", trailing.url("api/login"));
    }

    @Test
    public void is_unusable_until_address_and_credentials_are_all_present() {
        assertTrue(new RutConnectionConfig("https://192.168.1.1", "admin", "pw", null, null)
                .isUsable());
        assertFalse(new RutConnectionConfig("", "admin", "pw", null, null).isUsable());
        assertFalse(new RutConnectionConfig("https://192.168.1.1", null, "pw", null, null)
                .isUsable());
        assertFalse(new RutConnectionConfig("https://192.168.1.1", "admin", null, null, null)
                .isUsable());
    }
}
