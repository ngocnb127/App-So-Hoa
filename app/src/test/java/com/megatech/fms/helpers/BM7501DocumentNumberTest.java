package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Số chứng từ pháp lý {@code localNumber} do app sinh offline. */
public class BM7501DocumentNumberTest {

    private static Date date(String yyyyMMdd) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(yyyyMMdd);
    }

    @Test
    public void build_dungDinhDang() throws Exception {
        String n = BM7501DocumentNumber.build("NBA", "51F-123.45", "SERIAL-1", date("2026-08-10"), 1);

        assertTrue(n, BM7501DocumentNumber.isValid(n));
        assertTrue(n.startsWith("75-NBA-51F12345-"));
        assertTrue(n.endsWith("-260810-001"));
    }

    @Test
    public void build_seqDuocDemDu3ChuSo() throws Exception {
        assertTrue(BM7501DocumentNumber.build("NBA", "51F1", "S", date("2026-08-10"), 7)
                .endsWith("-007"));
        assertTrue(BM7501DocumentNumber.build("NBA", "51F1", "S", date("2026-08-10"), 42)
                .endsWith("-042"));
        // Vượt 999 vẫn sinh được số hợp lệ, không bị cắt.
        String n = BM7501DocumentNumber.build("NBA", "51F1", "S", date("2026-08-10"), 1234);
        assertTrue(n, n.endsWith("-1234"));
        assertTrue(BM7501DocumentNumber.isValid(n));
    }

    @Test
    public void deviceCode_onDinhTheoSerial() {
        String a = BM7501DocumentNumber.deviceCode("SERIAL-1");
        assertEquals(a, BM7501DocumentNumber.deviceCode("SERIAL-1"));
        assertEquals(4, a.length());
        assertTrue(a.matches("^[A-F0-9]{4}$"));
    }

    @Test
    public void deviceCode_hai_tabletKhacNhau_choMaKhacNhau() {
        // Đây là lý do giữ mã thiết bị trong số phiếu: một xe có thể dùng nhiều tablet.
        assertNotEquals(BM7501DocumentNumber.deviceCode("TABLET-A"),
                BM7501DocumentNumber.deviceCode("TABLET-B"));
    }

    @Test
    public void deviceCode_serialRong_vanSinhDuoc() {
        assertEquals("0000", BM7501DocumentNumber.deviceCode(null));
        assertEquals("0000", BM7501DocumentNumber.deviceCode("   "));
    }

    @Test
    public void haiTabletCungXeCungNgay_khongTrungSo() throws Exception {
        String a = BM7501DocumentNumber.build("NBA", "51F-123.45", "TABLET-A", date("2026-08-10"), 1);
        String b = BM7501DocumentNumber.build("NBA", "51F-123.45", "TABLET-B", date("2026-08-10"), 1);
        assertNotEquals(a, b);
    }

    @Test
    public void counterScopeKey_tachTheoThietBi() throws Exception {
        String a = BM7501DocumentNumber.counterScopeKey("51F-123.45", "TABLET-A", date("2026-08-10"));
        String b = BM7501DocumentNumber.counterScopeKey("51F-123.45", "TABLET-B", date("2026-08-10"));
        String c = BM7501DocumentNumber.counterScopeKey("51F-123.45", "TABLET-A", date("2026-08-11"));

        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a, BM7501DocumentNumber.counterScopeKey("51F123.45", "TABLET-A", date("2026-08-10")));
    }

    @Test
    public void build_thieuChiNhanhHoacSoXe_neLoi() throws Exception {
        try {
            BM7501DocumentNumber.build("", "51F1", "S", date("2026-08-10"), 1);
            fail("Thiếu mã chi nhánh phải ném lỗi");
        } catch (IllegalArgumentException expected) {
            // đúng như mong đợi
        }
        try {
            BM7501DocumentNumber.build("NBA", "---", "S", date("2026-08-10"), 1);
            fail("Số xe rỗng sau khi chuẩn hóa phải ném lỗi");
        } catch (IllegalArgumentException expected) {
            // đúng như mong đợi
        }
    }

    @Test
    public void build_seqKhongHopLe_neLoi() throws Exception {
        try {
            BM7501DocumentNumber.build("NBA", "51F1", "S", date("2026-08-10"), 0);
            fail("seq = 0 phải ném lỗi");
        } catch (IllegalArgumentException expected) {
            // đúng như mong đợi
        }
    }

    @Test
    public void isValid_batDuocSoSai() {
        assertFalse(BM7501DocumentNumber.isValid(null));
        assertFalse(BM7501DocumentNumber.isValid(""));
        assertFalse(BM7501DocumentNumber.isValid("75-NBA-51F1-260810-001"));      // thiếu mã thiết bị
        assertFalse(BM7501DocumentNumber.isValid("76-NBA-51F1-AB12-260810-001")); // sai tiền tố
        assertFalse(BM7501DocumentNumber.isValid("75-NBA-51F1-AB12-260810-01"));  // seq thiếu chữ số
    }
}
