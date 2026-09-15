package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Parser response RutOS.
 *
 * <p>VÌ SAO đáng test: cùng một thông tin đổi tên giữa các model và các bản firmware. Một
 * parser cứng theo một tên sẽ IM LẶNG trả null sau khi router được nâng firmware — popup
 * vẫn mở, vẫn đẹp, chỉ toàn dấu gạch ngang, và không ai biết vì sao. Test này khoá danh
 * sách alias và khoá quy tắc "không đọc được thì null, không đoán 0/false".
 */
public class RutJsonTest {

    private static JsonObject obj(String json) {
        JsonObject parsed = RutJson.parseObject(json);
        assertNotNull("JSON mẫu phải hợp lệ", parsed);
        return parsed;
    }

    // ------------------------------------------------------------------ string

    @Test
    public void reads_a_string_from_any_alias_in_the_list() {
        assertEquals("Viettel", RutJson.string(
                obj("{\"operator\":\"Viettel\"}"), "operator", "operator_name", "carrier"));
        assertEquals("Viettel", RutJson.string(
                obj("{\"operator_name\":\"Viettel\"}"), "operator", "operator_name", "carrier"));
        assertEquals("Viettel", RutJson.string(
                obj("{\"carrier\":\"Viettel\"}"), "operator", "operator_name", "carrier"));
    }

    /**
     * Alias đứng trước thắng: thứ tự trong danh sách là thứ tự ưu tiên, không phải ngẫu
     * nhiên. Đảo thứ tự nghĩa là đọc trúng field phụ khi cả hai cùng có.
     */
    @Test
    public void prefers_the_alias_listed_first_when_several_are_present() {
        JsonObject both = obj("{\"operator\":\"Viettel\",\"carrier\":\"Mobifone\"}");
        assertEquals("Viettel", RutJson.string(both, "operator", "carrier"));
        assertEquals("Mobifone", RutJson.string(both, "carrier", "operator"));
    }

    /**
     * Thiếu field, field null hoặc chuỗi rỗng đều là "không có dữ liệu". Trả chuỗi rỗng
     * lên UI tạo ra một ô trắng trông như đã đọc được — khác hẳn dấu "—" của trường thiếu.
     */
    @Test
    public void returns_null_for_a_missing_null_or_blank_string() {
        assertNull(RutJson.string(obj("{\"other\":\"x\"}"), "operator"));
        assertNull(RutJson.string(obj("{\"operator\":null}"), "operator"));
        assertNull(RutJson.string(obj("{\"operator\":\"   \"}"), "operator"));
        assertNull(RutJson.string(null, "operator"));
    }

    /**
     * "N/A" là DẤU THIẾU DỮ LIỆU của RutOS, không phải một giá trị.
     *
     * <p>Bản ghi thật từ RUT955 (firmware RUT9_R_00.07.06.21) khi khay SIM rỗng trả nguyên
     * chuỗi đó cho {@code provider}, {@code oper}, {@code imsi}, {@code iccid}, {@code rsrp}…
     * Trả nó nguyên về UI nghĩa là in lên màn hình người vận hành dòng "Nhà mạng: N/A" —
     * một chỗ trống được trình bày như một phép đo đã đọc được, khác hẳn dấu "—" của trường
     * thiếu.
     */
    @Test
    public void treats_the_rutos_not_available_markers_as_missing_data() {
        assertNull(RutJson.string(obj("{\"provider\":\"N/A\"}"), "provider"));
        assertNull(RutJson.string(obj("{\"provider\":\"n/a\"}"), "provider"));
        assertNull(RutJson.string(obj("{\"provider\":\" N/A \"}"), "provider"));
        assertNull(RutJson.string(obj("{\"provider\":\"na\"}"), "provider"));
        assertNull(RutJson.string(obj("{\"provider\":\"-\"}"), "provider"));
        assertNull(RutJson.string(obj("{\"provider\":\"--\"}"), "provider"));
    }

    /**
     * Chỉ dấu thiếu dữ liệu mới bị loại, không phải mọi chuỗi có gạch chéo: nhà mạng thật
     * cũng có tên chứa "/" hoặc chứa chữ "na" (Vinaphone). Loại nhầm là mất tên nhà mạng.
     */
    @Test
    public void keeps_real_values_that_merely_look_like_a_missing_marker() {
        assertEquals("Vinaphone", RutJson.string(obj("{\"oper\":\"Vinaphone\"}"), "oper"));
        assertEquals("VN/Viettel", RutJson.string(obj("{\"oper\":\"VN/Viettel\"}"), "oper"));
    }

    /**
     * Cùng dấu đó cho số: {@code rsrp:"N/A"} phải ra null. Bóc chữ số một cách máy móc từ
     * "N/A" không cho ra gì, nhưng nếu ai đó thêm giá trị mặc định 0 thì popup sẽ hiện
     * "0 dBm" — một cường độ sóng mạnh bất thường ở đúng chỗ không có sóng.
     */
    @Test
    public void reads_no_number_out_of_a_not_available_marker() {
        assertNull(RutJson.integer(obj("{\"rsrp\":\"N/A\"}"), "rsrp"));
        assertNull(RutJson.integer(obj("{\"sinr\":\"N/A\"}"), "sinr"));
        assertNull(RutJson.integer(obj("{\"signal\":\"N/A\"}"), "signal"));
        assertNull(RutJson.integer(obj("{\"rsrq\":\"--\"}"), "rsrq"));
    }

    @Test
    public void trims_surrounding_whitespace_from_strings() {
        assertEquals("RUTX50", RutJson.string(obj("{\"model\":\"  RUTX50 \"}"), "model"));
    }

    // ------------------------------------------------------------------ integer

    @Test
    public void reads_an_integer_from_any_alias_in_the_list() {
        assertEquals(Integer.valueOf(-82), RutJson.integer(
                obj("{\"rsrp\":-82}"), "rsrp", "lte_rsrp"));
        assertEquals(Integer.valueOf(-82), RutJson.integer(
                obj("{\"lte_rsrp\":-82}"), "rsrp", "lte_rsrp"));
    }

    /**
     * RutOS trả số đo dưới dạng chuỗi, có khi kèm đơn vị. Không bóc được số nghĩa là mất
     * cả khối cường độ sóng.
     */
    @Test
    public void reads_a_negative_integer_from_a_plain_string_and_from_one_with_a_unit() {
        assertEquals(Integer.valueOf(-82), RutJson.integer(obj("{\"rsrp\":\"-82\"}"), "rsrp"));
        assertEquals(Integer.valueOf(-82), RutJson.integer(obj("{\"rsrp\":\"-82 dBm\"}"), "rsrp"));
        assertEquals(Integer.valueOf(15), RutJson.integer(obj("{\"sinr\":\"15 dB\"}"), "sinr"));
    }

    /**
     * Thiếu số đo PHẢI ra null chứ không phải 0. RSRP 0 dBm là một tín hiệu mạnh bất
     * thường; hiển thị nó lên popup là báo "sóng tuyệt vời" ở đúng chỗ không có sóng.
     */
    @Test
    public void returns_null_rather_than_zero_when_the_integer_is_missing_or_unreadable() {
        assertNull(RutJson.integer(obj("{\"other\":1}"), "rsrp"));
        assertNull(RutJson.integer(obj("{\"rsrp\":null}"), "rsrp"));
        assertNull(RutJson.integer(obj("{\"rsrp\":\"\"}"), "rsrp"));
        assertNull(RutJson.integer(obj("{\"rsrp\":\"n/a\"}"), "rsrp"));
        assertNull(RutJson.integer(obj("{\"rsrp\":\"-\"}"), "rsrp"));
        assertNull(RutJson.integer(null, "rsrp"));
    }

    // ------------------------------------------------------------------ bool

    @Test
    public void reads_true_from_a_real_json_boolean() {
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"connected\":true}"), "connected"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"connected\":\"true\"}"), "connected"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"connected\":\"TRUE\"}"), "connected"));
    }

    /**
     * RutOS trả trạng thái bằng CHỮ chứ không bằng boolean JSON. Bản đầu gọi thẳng
     * {@code getAsBoolean()} rồi bắt ngoại lệ để "rơi xuống đọc chuỗi" — nhưng Gson không
     * ném cho chuỗi, nó trả {@code Boolean.parseBoolean()}, nên cả nhánh đọc chuỗi là mã
     * chết và modem đang chạy bị báo mất kết nối. Test này giữ cho lỗi đó không quay lại.
     */
    @Test
    public void reads_true_from_the_status_words_rutos_actually_sends() {
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"connected\":\"1\"}"), "connected"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"state\":\"up\"}"), "state"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"state\":\"yes\"}"), "state"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"state\":\"connected\"}"), "state"));
        assertEquals(Boolean.TRUE, RutJson.bool(obj("{\"state\":\"CONNECTED\"}"), "state"));
    }

    /**
     * Ca nguy hiểm nhất của cả file: "disconnected" và "not connected" đều CHỨA chuỗi con
     * "connected". Một phép so khớp bằng contains() ngây thơ sẽ báo modem đang online
     * trong khi nó vừa rớt mạng — người dùng ngồi chờ dữ liệu lên trong khi không có kết
     * nối nào cả.
     */
    // Ghi chú: các ca này phải
    // tiếp tục xanh SAU KHI sửa bug đó; đó chính là lý do giữ chúng lại.
    @Test
    public void reads_disconnected_and_not_connected_as_false_not_true() {
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"disconnected\"}"), "state"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"Disconnected\"}"), "state"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"not connected\"}"), "state"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"NOT CONNECTED\"}"), "state"));
    }

    @Test
    public void reads_false_from_the_negative_status_words() {
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"connected\":false}"), "connected"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"connected\":\"0\"}"), "connected"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"down\"}"), "state"));
        assertEquals(Boolean.FALSE, RutJson.bool(obj("{\"state\":\"no\"}"), "state"));
    }

    /** Thiếu cờ là "chưa biết", không phải "false" — hai thứ hiện lên UI khác nhau. */
    @Test
    public void returns_null_when_the_boolean_field_is_absent_or_json_null() {
        assertNull(RutJson.bool(obj("{\"other\":true}"), "connected"));
        assertNull(RutJson.bool(obj("{\"connected\":null}"), "connected"));
        assertNull(RutJson.bool(null, "connected"));
    }

    /**
     * Một chuỗi rỗng hoặc một từ trạng thái lạ (firmware mới đặt tên khác) phải cho ra
     * null = "chưa biết". Nếu chúng bị quy thành false, popup khẳng định "modem KHÔNG kết
     * nối" trong khi ta chưa hề đo được điều đó.
     */
    @Test
    public void returns_null_for_a_blank_or_unrecognised_boolean_string() {
        assertNull(RutJson.bool(obj("{\"connected\":\"   \"}"), "connected"));
        assertNull(RutJson.bool(obj("{\"connected\":\"maybe\"}"), "connected"));
    }

    // ------------------------------------------------------------------ data / asArray

    /** Một số endpoint bọc trong {"success":..,"data":..}, số khác trả object trần. */
    @Test
    public void unwraps_both_the_data_envelope_and_a_bare_object() {
        JsonObject wrapped = obj("{\"success\":true,\"data\":{\"model\":\"RUTX50\"}}");
        assertEquals("RUTX50",
                RutJson.string(RutJson.firstObject(RutJson.data(wrapped)), "model"));

        JsonObject bare = obj("{\"model\":\"RUT956\"}");
        assertEquals("RUT956",
                RutJson.string(RutJson.firstObject(RutJson.data(bare)), "model"));

        assertNull(RutJson.data(null));
    }

    @Test
    public void unwraps_a_data_envelope_that_holds_an_array() {
        JsonObject wrapped = obj("{\"success\":true,\"data\":[{\"id\":\"1-1\"}]}");
        JsonArray array = RutJson.asArray(RutJson.data(wrapped));
        assertNotNull(array);
        assertEquals(1, array.size());
        assertEquals("1-1", RutJson.string(RutJson.firstObject(array.get(0)), "id"));
    }

    /**
     * Firmware này trả mảng modem, firmware kia trả một object đơn. Nơi gọi chỉ có một
     * vòng lặp, nên asArray phải bọc object đơn lại thành mảng một phần tử — nếu không,
     * đúng những router trả object đơn sẽ mất sạch phần modem và sóng.
     */
    @Test
    public void wraps_a_single_object_into_an_array_so_callers_need_only_one_loop() {
        JsonArray fromObject = RutJson.asArray(obj("{\"id\":\"1-1\"}"));
        assertNotNull(fromObject);
        assertEquals(1, fromObject.size());

        assertNull(RutJson.asArray(null));
        assertNull(RutJson.asArray(com.google.gson.JsonParser.parseString("\"scalar\"")));
    }

    @Test
    public void firstObject_takes_the_object_itself_or_the_first_object_of_an_array() {
        assertEquals("RUTX50", RutJson.string(
                RutJson.firstObject(com.google.gson.JsonParser.parseString(
                        "{\"model\":\"RUTX50\"}")), "model"));
        assertEquals("RUTX50", RutJson.string(
                RutJson.firstObject(com.google.gson.JsonParser.parseString(
                        "[{\"model\":\"RUTX50\"},{\"model\":\"RUT956\"}]")), "model"));
        assertNull(RutJson.firstObject(null));
        assertNull(RutJson.firstObject(com.google.gson.JsonParser.parseString("[1,2,3]")));
    }

    /** Router có thể trả HTML của trang đăng nhập thay vì JSON. Không được ném ra ngoài. */
    @Test
    public void parseObject_returns_null_instead_of_throwing_on_junk_input() {
        assertNull(RutJson.parseObject(null));
        assertNull(RutJson.parseObject("   "));
        assertNull(RutJson.parseObject("<html>login</html>"));
        assertNull(RutJson.parseObject("[1,2]"));
        assertTrue(RutJson.parseObject("{\"a\":1}").has("a"));
        assertFalse(RutJson.parseObject("{\"a\":1}").has("b"));
    }
}
