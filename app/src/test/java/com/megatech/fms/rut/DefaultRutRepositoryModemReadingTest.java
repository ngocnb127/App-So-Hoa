package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Đọc trạng thái SIM / đăng ký mạng từ bản ghi modem THẬT của RUT955.
 *
 * <p>VÌ SAO đáng test: mọi hằng số ở đây đến từ một lần dò trực tiếp trên router
 * (RUT955, firmware RUT9_R_00.07.06.21, khay SIM rỗng, WAN đi bằng cáp). Tài liệu của
 * Teltonika không mô tả đúng những tên field này, nên chúng chỉ được giữ đúng bằng cách
 * ghim nguyên payload đo được vào test. Đọc nhầm một khoá ở đây là chỉ sai việc cho người
 * đang đứng cạnh xe bồn: bảo họ rút SIM ra cắm lại khi thật ra SIM không liên quan.
 */
public class DefaultRutRepositoryModemReadingTest {

    /**
     * Nguyên văn phần tử duy nhất của {@code GET /api/modems/status} trên máy thật.
     * KHÔNG rút gọn: chính những field "N/A" và cặp {@code state}/{@code simstate} nằm cạnh
     * nhau mới là thứ làm hỏng bản đọc cũ.
     */
    private static final String REAL_MODEM_STATUS = "{\"success\":true,\"data\":[{"
            + "\"pinstate\":\"SIM not inserted\",\"provider\":\"N/A\",\"oper\":\"N/A\","
            + "\"sinr\":\"N/A\",\"state_id\":2,\"conntype\":\"No service\","
            + "\"state\":\"Disconnected\",\"imei\":\"865546042351110\",\"active_sim\":1,"
            + "\"imsi\":\"N/A\",\"sim_count\":2,\"pinstate_id\":10,\"iccid\":\"N/A\","
            + "\"ntype\":\"No service\",\"id\":\"1-1.4\",\"primary\":true,"
            + "\"netstate\":\"Searching\",\"rsrq\":\"N/A\",\"rsrp\":\"N/A\","
            + "\"signal\":\"N/A\",\"simstate_id\":1,\"netstate_id\":0,"
            + "\"simstate\":\"Not inserted\"}]}";

    /** Nguyên văn {@code GET /api/sim_cards/status}: đây là DANH SÁCH CẤU HÌNH khay SIM. */
    private static final String REAL_SIM_CARDS_STATUS = "{\"success\":true,\"data\":["
            + "{\"sms_limit_enabled\":\"0\",\"modem\":\"1-1.4\",\"sim\":\"1\","
            + "\"section_name\":\"cfg02aa0e\"},"
            + "{\"sms_limit_enabled\":\"0\",\"modem\":\"1-1.4\",\"sim\":\"2\","
            + "\"section_name\":\"cfg03aa0e\"}]}";

    private static JsonObject realModem() {
        JsonObject modem = RutJson.firstObject(
                RutJson.data(RutJson.parseObject(REAL_MODEM_STATUS)));
        assertNotNull("Payload mẫu phải đọc được", modem);
        return modem;
    }

    private static JsonObject obj(String json) {
        JsonObject parsed = RutJson.parseObject(json);
        assertNotNull("JSON mẫu phải hợp lệ", parsed);
        return parsed;
    }

    // ------------------------------------------------------------------ simPresence

    /**
     * Ca dễ sai nhất của cả tính năng, và là lỗi đã xảy ra thật.
     *
     * <p>Bản ghi modem có ĐỒNG THỜI {@code "state":"Disconnected"} (trạng thái KẾT NỐI) và
     * {@code "simstate":"Not inserted"} (trạng thái THẺ). Hỏi {@code state} là đọc nhầm hẳn
     * một đại lượng khác: một chiếc SIM tốt trong lúc mất sóng sẽ bị báo là "không có SIM",
     * và người vận hành đi rút thẻ ra cắm lại cho một sự cố không nằm ở thẻ.
     */
    @Test
    public void reads_sim_presence_from_simstate_not_from_the_connection_state() {
        assertEquals(Boolean.FALSE, DefaultRutRepository.simPresence(realModem()));

        // SIM tốt nhưng mất kết nối: state nói "Disconnected", simstate nói "Inserted".
        assertEquals(Boolean.TRUE, DefaultRutRepository.simPresence(obj(
                "{\"state\":\"Disconnected\",\"simstate\":\"Inserted\"}")));
    }

    /**
     * Chỉ có {@code state} mà không có {@code simstate} là "router KHÔNG NÓI GÌ về thẻ".
     * Trả false ở đây là biến một chỗ chưa đo được thành lời khẳng định "khay rỗng" — sai
     * hướng xử lý ngay từ dòng đầu tiên người dùng đọc.
     */
    @Test
    public void says_it_does_not_know_when_only_a_connection_state_is_reported() {
        assertNull(DefaultRutRepository.simPresence(obj("{\"state\":\"Disconnected\"}")));
        assertNull(DefaultRutRepository.simPresence(obj("{\"state\":\"Connected\"}")));
        assertNull(DefaultRutRepository.simPresence(null));
        assertNull(DefaultRutRepository.simPresence(obj("{\"imei\":\"865546042351110\"}")));
    }

    /**
     * Chuỗi phủ định của RutOS CHỨA nguyên chữ khẳng định ("Not inserted" chứa "inserted").
     * Khớp khẳng định trước là đảo ngược kết luận đúng chỗ nguy hiểm nhất.
     */
    @Test
    public void matches_the_negative_sim_words_before_the_positive_ones() {
        assertEquals(Boolean.FALSE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"Not inserted\"}")));
        assertEquals(Boolean.FALSE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"not_present\"}")));
        assertEquals(Boolean.FALSE, DefaultRutRepository.simPresence(
                obj("{\"sim_state\":\"No SIM\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"Inserted\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"Ready\"}")));
    }

    /**
     * SIM khoá PIN VẪN là "router đọc được thẻ". Coi nó là không có thẻ là đẩy người dùng
     * đi rút cắm trong khi việc cần làm là mở khoá PIN — việc của quản trị, không ai làm
     * được ở ngoài bãi.
     */
    @Test
    public void counts_a_pin_locked_card_as_a_card_the_router_can_see() {
        assertEquals(Boolean.TRUE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"PIN required\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.simPresence(
                obj("{\"simstate\":\"PUK required\"}")));
    }

    /**
     * {@code /api/sim_cards/status} chỉ là danh sách CẤU HÌNH hai khay, không nói gì về việc
     * có thẻ hay không — bản ghi đo được không hề có {@code simstate}. Suy ra "có SIM" chỉ
     * vì danh sách có hai phần tử là kết luận từ hư không: máy thật đang không cắm thẻ nào.
     */
    @Test
    public void draws_no_sim_presence_conclusion_from_the_sim_slot_configuration_list() {
        JsonElement data = RutJson.data(RutJson.parseObject(REAL_SIM_CARDS_STATUS));
        int examined = 0;
        for (JsonObject slot : RutJson.objects(data, "sim", "id", "slot", "state")) {
            examined++;
            assertNull("Bản ghi cấu hình khay không được coi là bằng chứng có thẻ",
                    DefaultRutRepository.simPresence(slot));
        }
        assertEquals("Payload thật có đúng hai bản ghi khay", 2, examined);
    }

    // ------------------------------------------------------------------ pinLocked

    /**
     * Trên máy thật {@code pinstate} là "SIM not inserted" — không có thẻ thì không có
     * chuyện khoá PIN. Chuỗi này lại CHỨA chữ "inserted", nên nếu ai đó nới hàm ra để bắt
     * mọi trạng thái lạ thành "khoá" thì popup sẽ đòi người dùng nhập PIN cho một khay rỗng.
     */
    @Test
    public void does_not_report_a_pin_lock_when_there_is_no_card_at_all() {
        assertEquals(Boolean.FALSE, DefaultRutRepository.pinLocked(realModem()));
    }

    @Test
    public void reports_a_pin_lock_only_for_the_states_that_really_wait_for_a_code() {
        assertEquals(Boolean.TRUE, DefaultRutRepository.pinLocked(
                obj("{\"pinstate\":\"PIN required\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.pinLocked(
                obj("{\"pinstate\":\"PUK required\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.pinLocked(
                obj("{\"pin_state\":\"SIM locked\"}")));

        assertEquals(Boolean.FALSE, DefaultRutRepository.pinLocked(
                obj("{\"pinstate\":\"Ready\"}")));
        assertEquals(Boolean.FALSE, DefaultRutRepository.pinLocked(
                obj("{\"pinstate\":\"Disabled\"}")));
    }

    /** Không có field nào nói về PIN thì là "chưa biết", không phải "đã mở khoá". */
    @Test
    public void says_it_does_not_know_about_the_pin_when_no_field_mentions_it() {
        assertNull(DefaultRutRepository.pinLocked(null));
        assertNull(DefaultRutRepository.pinLocked(obj("{\"state\":\"Disconnected\"}")));
        assertNull(DefaultRutRepository.pinLocked(obj("{\"pinstate\":\"N/A\"}")));
    }

    // ------------------------------------------------------------------ registrationOf

    /**
     * {@code netstate} là một TỪ trạng thái, không phải cờ đúng/sai — {@code RutJson.bool}
     * không đọc được nó. Máy thật trả "Searching": một câu trả lời rõ ràng là CHƯA đăng ký.
     * Đọc thành null là mất đúng thông tin tách ca "SIM hỏng" khỏi ca "SIM tốt, hết tiền".
     */
    @Test
    public void reads_searching_as_a_definite_not_registered_answer() {
        assertEquals(Boolean.FALSE, DefaultRutRepository.registrationOf(realModem()));
    }

    @Test
    public void maps_each_registration_word_rutos_uses_to_the_right_answer() {
        assertEquals(Boolean.FALSE, DefaultRutRepository.registrationOf(
                obj("{\"netstate\":\"Denied\"}")));
        assertEquals(Boolean.FALSE, DefaultRutRepository.registrationOf(
                obj("{\"netstate\":\"Not registered\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.registrationOf(
                obj("{\"netstate\":\"Registered (home)\"}")));
        assertEquals(Boolean.TRUE, DefaultRutRepository.registrationOf(
                obj("{\"netstate\":\"Roaming\"}")));
    }

    /**
     * Firmware im lặng hoặc dùng một từ chưa gặp thì phải trả null. Quy nó thành false là
     * khẳng định "modem chưa vào được mạng" trong khi ta chưa hề đo được điều đó.
     */
    @Test
    public void says_it_does_not_know_the_registration_for_an_unseen_word() {
        assertNull(DefaultRutRepository.registrationOf(null));
        assertNull(DefaultRutRepository.registrationOf(obj("{\"imei\":\"8655\"}")));
        assertNull(DefaultRutRepository.registrationOf(obj("{\"netstate\":\"N/A\"}")));
        assertNull(DefaultRutRepository.registrationOf(obj("{\"netstate\":\"limited\"}")));
    }

    // ------------------------------------------------- những gì bản ghi thật nói ra

    /**
     * Ghim nguyên bộ kết luận đọc được từ payload thật: đây là ảnh chụp của một chiếc
     * RUT955 không cắm SIM, đi mạng bằng cáp WAN. Mỗi khẳng định là một dòng người vận hành
     * sẽ đọc trên popup.
     */
    @Test
    public void reads_the_whole_real_no_sim_modem_record_the_way_the_popup_needs_it() {
        JsonObject modem = realModem();

        assertEquals("1-1.4", RutJson.string(modem, "id", "modem_id", "index"));
        assertEquals("Chuỗi trạng thái kết nối, không phải trạng thái thẻ",
                Boolean.FALSE, RutJson.bool(modem,
                        "connected", "connection_state", "state", "status", "net_state"));
        assertNull("Nhà mạng \"N/A\" phải thành trống, không được in lên màn hình",
                RutJson.string(modem, "oper", "provider", "operator", "operator_name"));
        assertEquals("Khay đang dùng đọc thẳng từ modem status vì sim_switch trả HTTP 501",
                Integer.valueOf(1), RutJson.integer(modem, "active_sim", "sim", "sim_id"));

        assertNull(RutJson.integer(modem, "rsrp", "lte_rsrp"));
        assertNull(RutJson.integer(modem, "rsrq", "lte_rsrq"));
        assertNull(RutJson.integer(modem, "sinr", "lte_sinr", "snr"));
        assertNull(RutJson.integer(modem, "rssi", "signal", "signal_strength"));
    }

    /**
     * Kết luận cuối cùng của cả chuỗi đọc trên đúng chiếc máy đã dò: không có thẻ. Không
     * phải "chưa đăng ký", không phải "không biết" — ba dòng hướng dẫn khác nhau.
     */
    @Test
    public void classifies_the_real_no_sim_router_as_no_sim() {
        JsonObject modem = realModem();

        RutSimState state = RutSimState.classify(
                DefaultRutRepository.simPresence(modem),
                DefaultRutRepository.pinLocked(modem),
                DefaultRutRepository.registrationOf(modem),
                RutJson.bool(modem, "connected", "connection_state", "state"));

        assertEquals(RutSimState.NO_SIM, state);
        assertTrue("Đây đúng là ca người dùng tự xử lý được tại xe", state.isFixableOnSite());
        assertFalse(state.hasSim());
    }

    // ------------------------------------------------------------ nhận dạng router

    /**
     * Máy thật khai {@code static.device_name} = "RUT955" trong khi {@code static.model} =
     * "Teltonika RUT9XX". Cả hai đều phải qua được bước so tiền tố — "RUT9XX" là tên DÒNG
     * sản phẩm và vẫn mang tiền tố ở đầu một từ.
     */
    @Test
    public void accepts_both_names_the_real_router_reports_for_itself() {
        assertTrue(DefaultRutRepository.hasRutModelPrefix("RUT955"));
        assertTrue(DefaultRutRepository.hasRutModelPrefix("Teltonika RUT9XX"));
        assertTrue(DefaultRutRepository.hasRutModelPrefix("teltonika,rut9xx"));
    }

    /**
     * Ranh giới TỪ, không phải "chứa ở bất kỳ đâu": {@code MYRUTX50} là thiết bị khác hẳn.
     * Nới ra là chấp nhận một thiết bị lạ làm router của xe.
     */
    @Test
    public void requires_the_rut_prefix_at_a_word_boundary() {
        assertFalse(DefaultRutRepository.hasRutModelPrefix("MYRUTX50"));
        assertFalse(DefaultRutRepository.hasRutModelPrefix("TP-Link Archer"));
        assertFalse(DefaultRutRepository.hasRutModelPrefix(null));
        assertFalse(DefaultRutRepository.hasRutModelPrefix(""));
    }

    /**
     * Đã ghép cặp thì serial mới là bằng chứng. Serial của máy thật là "1106343995" (đọc từ
     * {@code mnfinfo.serial}); một chiếc RUT955 khác cùng bãi cũng khai model "RUT955" nên
     * chỉ so model là mở đường cho việc reboot nhầm thiết bị của người khác.
     */
    @Test
    public void identifies_the_real_router_by_serial_once_it_has_been_paired() {
        RutStatusUiModel measured = new RutStatusUiModel();
        measured.routerModel = "RUT955";
        measured.routerSerial = "1106343995";

        RutConnectionConfig paired = new RutConnectionConfig(
                "https://192.168.1.1", "admin", "pw", "1106343995", null);
        RutConnectionConfig pairedToAnother = new RutConnectionConfig(
                "https://192.168.1.1", "admin", "pw", "1106340000", null);
        RutConnectionConfig unpaired = new RutConnectionConfig(
                "https://192.168.1.1", "admin", "pw", null, null);

        assertTrue(DefaultRutRepository.isExpectedRouter(paired, measured));
        assertFalse("Cùng model RUT955 nhưng khác serial vẫn là router lạ",
                DefaultRutRepository.isExpectedRouter(pairedToAnother, measured));
        assertTrue(DefaultRutRepository.isExpectedRouter(unpaired, measured));
    }
}
