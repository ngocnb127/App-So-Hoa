package com.megatech.fms.rut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * Chẩn đoán "router có SIM, có sóng mà không có mạng".
 *
 * <p>VÌ SAO đáng test: trước đây popup chỉ nói "Router mất Internet" cho mọi ca, kể cả khi
 * router vẫn ra mạng bình thường và chỉ máy chủ FMS không trả lời. Người đứng cạnh xe khi đó
 * khởi động lại router — mất 2–4 phút mạng của cả xe mà không sửa được gì. Mỗi nhánh dưới
 * đây là một việc khác nhau phải làm ngoài hiện trường.
 *
 * <p>Tên field lấy từ đặc tả API chính thức của RutOS 7.06 (bản chạy trên RUT955) và 7.25.
 * Đầu ra ping/nslookup là dạng của busybox — CHƯA đo trên router thật; đo được thì ghim
 * nguyên văn vào đây như {@link DefaultRutRepositoryModemReadingTest}.
 */
public class RutInternetDiagnosisTest {

    private static final String PING_OK = "PING 8.8.8.8 (8.8.8.8): 56 data bytes\n"
            + "64 bytes from 8.8.8.8: seq=0 ttl=117 time=31.207 ms\n"
            + "64 bytes from 8.8.8.8: seq=1 ttl=117 time=30.884 ms\n\n"
            + "--- 8.8.8.8 ping statistics ---\n"
            + "5 packets transmitted, 5 packets received, 0% packet loss\n"
            + "round-trip min/avg/max = 30.1/31.0/32.2 ms";

    private static final String PING_ALL_LOST = "PING 8.8.8.8 (8.8.8.8): 56 data bytes\n\n"
            + "--- 8.8.8.8 ping statistics ---\n"
            + "5 packets transmitted, 0 packets received, 100% packet loss";

    private static final String PING_NO_ROUTE = "PING 8.8.8.8 (8.8.8.8): 56 data bytes\n"
            + "ping: sendto: Network unreachable";

    private static final String NSLOOKUP_OK = "Server:\t\t127.0.0.1\n"
            + "Address:\t127.0.0.1:53\n\n"
            + "Non-authoritative answer:\n"
            + "Name:\tfmsapi.skypec.com.vn\n"
            + "Address: 203.0.113.10\n";

    private static final String NSLOOKUP_NXDOMAIN = "Server:\t\t127.0.0.1\n"
            + "Address:\t127.0.0.1:53\n\n"
            + "** server can't find fmsapi.skypec.com.vn: NXDOMAIN\n";

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    // ------------------------------------------------------------------ classify

    /**
     * Đúng ca trong ảnh chụp ngoài hiện trường: SIM 2 đã vào mạng Viettel, sóng tốt, modem
     * báo "Connected", interface 4G có IP — nhưng router ping không ra ngoài. Đây là ca hết
     * tiền/hết gói hoặc phiên 4G bị treo, KHÔNG phải ca SIM.
     */
    @Test
    public void simTotSongTotMaPingKhongRaLaMatLuuLuong4G() {
        RutInternetDiagnosis.Cause cause = RutInternetDiagnosis.classify(
                RutSimState.SIM_REGISTERED, true, null, null, false, true, false, null);

        assertEquals(RutInternetDiagnosis.Cause.MOBILE_NO_TRAFFIC, cause);
        assertTrue("Ca phiên 4G treo phải cho thử dựng lại kết nối",
                cause.restartMayHelp());
    }

    /**
     * Router ra được Internet thì lỗi không nằm ở router — kể cả khi khay SIM rỗng (router
     * có thể đang đi bằng cáp WAN, như chính chiếc RUT955 đã đo).
     */
    @Test
    public void routerRaDuocInternetThiKhongDoLoiChoSim() {
        RutInternetDiagnosis.Cause cause = RutInternetDiagnosis.classify(
                RutSimState.NO_SIM, false, null, null, null, false, true, true);

        assertEquals(RutInternetDiagnosis.Cause.APP_SERVER_UNREACHABLE, cause);
    }

    /**
     * Máy chủ FMS sập: cắt 4G của router chỉ làm mất mạng cả xe. Nút dựng lại 4G tuyệt đối
     * không được mở ở ca này.
     */
    @Test
    public void mayChuFmsSapThiKhongMoNutDungLai4G() {
        assertFalse(RutInternetDiagnosis.Cause.APP_SERVER_UNREACHABLE.restartMayHelp());
    }

    @Test
    public void raDuocBangIpMaKhongTraDuocTenLaLoiDns() {
        assertEquals(RutInternetDiagnosis.Cause.DNS_FAILURE, RutInternetDiagnosis.classify(
                RutSimState.SIM_REGISTERED, true, null, 19, false, true, true, false));
    }

    /** Ra được bằng IP mà chưa biết DNS: chưa đủ bằng chứng để đổ cho máy chủ FMS. */
    @Test
    public void raDuocBangIpMaChuaBietDnsThiChuaKetLuan() {
        assertEquals(RutInternetDiagnosis.Cause.UNKNOWN, RutInternetDiagnosis.classify(
                RutSimState.SIM_REGISTERED, true, null, 19, false, true, true, null));
    }

    @Test
    public void dataBiTatBangSmsDungTruocMoiThuKhac() {
        assertEquals(RutInternetDiagnosis.Cause.DATA_OFF, RutInternetDiagnosis.classify(
                RutSimState.SIM_REGISTERED, false, true, 16, true, false, false, null));
    }

    @Test
    public void hetHanMucDataTrenRouterKhongChoDungLai4G() {
        RutInternetDiagnosis.Cause cause = RutInternetDiagnosis.classify(
                RutSimState.SIM_REGISTERED, true, false, 19, true, true, false, null);

        assertEquals(RutInternetDiagnosis.Cause.DATA_LIMIT_REACHED, cause);
        assertFalse("Dựng lại 4G bao nhiêu lần thì router vẫn chặn vì hạn mức",
                cause.restartMayHelp());
    }

    @Test
    public void simChuaSanSangThiLaCaSim() {
        assertEquals(RutInternetDiagnosis.Cause.SIM_NOT_READY, RutInternetDiagnosis.classify(
                RutSimState.PIN_LOCKED, null, null, null, null, null, false, null));
        // Bước dựng kết nối đang chờ PIN (5) — firmware không trả pinstate vẫn nhận ra.
        assertEquals(RutInternetDiagnosis.Cause.SIM_NOT_READY, RutInternetDiagnosis.classify(
                RutSimState.UNKNOWN, null, null, 5, null, null, false, null));
    }

    @Test
    public void chuaCoIpHoacKetGiuaCacBuocLaChuaDungXongKetNoi() {
        assertEquals(RutInternetDiagnosis.Cause.CONNECTION_NOT_READY,
                RutInternetDiagnosis.classify(
                        RutSimState.SIM_REGISTERED, true, null, null, false, false, false, null));
        // 17 = "Clearing PDP context": modem kẹt khi dọn phiên cũ.
        assertEquals(RutInternetDiagnosis.Cause.CONNECTION_NOT_READY,
                RutInternetDiagnosis.classify(
                        RutSimState.SIM_REGISTERED, true, null, 17, false, true, null, null));
        assertEquals(RutInternetDiagnosis.Cause.CONNECTION_NOT_READY,
                RutInternetDiagnosis.classify(
                        RutSimState.SIM_REGISTERED, false, null, null, null, null, null, null));
    }

    /** Router không nói gì thì không được bịa ra một nguyên nhân. */
    @Test
    public void khongCoSoDoNaoThiKhongBiaNguyenNhan() {
        assertEquals(RutInternetDiagnosis.Cause.UNKNOWN, RutInternetDiagnosis.classify(
                null, null, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ ping

    @Test
    public void docKetQuaPing() {
        assertEquals(Boolean.TRUE, RutInternetDiagnosis.pingReached(PING_OK));
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.pingReached(PING_ALL_LOST));
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.pingReached(PING_NO_ROUTE));
        assertEquals("Mất vài gói vẫn là ra được Internet", Boolean.TRUE,
                RutInternetDiagnosis.pingReached(
                        "5 packets transmitted, 2 packets received, 60% packet loss"));
        // Đầu ra kiểu iputils: "5 received", không có chữ "packets".
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.pingReached(
                "5 packets transmitted, 0 received, 100% packet loss, time 4005ms"));
    }

    /** Đầu ra lạ không phải là bằng chứng mất mạng. */
    @Test
    public void dauRaPingLaThiKhongKetLuan() {
        assertNull(RutInternetDiagnosis.pingReached(null));
        assertNull(RutInternetDiagnosis.pingReached(""));
        assertNull(RutInternetDiagnosis.pingReached("PING 8.8.8.8 (8.8.8.8): 56 data bytes"));
    }

    // ------------------------------------------------------------------ nslookup

    @Test
    public void docKetQuaNslookup() {
        assertEquals(Boolean.TRUE, RutInternetDiagnosis.nslookupResolved(NSLOOKUP_OK));
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.nslookupResolved(NSLOOKUP_NXDOMAIN));
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.nslookupResolved(
                ";; connection timed out; no servers could be reached\n"));
    }

    /**
     * Dòng Address ĐẦU TIÊN là của máy chủ DNS. Chỉ có dòng đó thì chưa có gì được phân
     * giải — đọc nhầm là kết luận "DNS chạy tốt" trong khi nó đang hỏng.
     */
    @Test
    public void diaChiCuaMayChuDnsKhongPhaiLaKetQua() {
        assertNull(RutInternetDiagnosis.nslookupResolved(
                "Server:\t\t127.0.0.1\nAddress:\t127.0.0.1:53\n"));
    }

    // ------------------------------------------------------------------ hạn mức data

    private static final String LIMIT_SIM1_REACHED = "[{\"id\":\"cfg01\","
            + "\"interface\":\"mob1s1a1\",\"data_limit\":1073741824,"
            + "\"data_used\":1073741900,\"due_reset_time\":\"0\","
            + "\"data_warning_enabled\":\"0\",\"data_warning_limit\":0,\"enabled\":\"1\"}]";

    @Test
    public void hetHanMucCuaSimDangDungLaBiChan() {
        assertEquals(Boolean.TRUE,
                RutInternetDiagnosis.dataLimitReached(json(LIMIT_SIM1_REACHED), 1));
    }

    /**
     * SIM 1 hết hạn mức nên router chuyển sang SIM 2 — SIM 2 vẫn chạy tốt. Đổ lỗi cho hạn
     * mức ở đây là bảo quản trị đi sửa một chỗ không hỏng.
     */
    @Test
    public void hetHanMucCuaSimKiaKhongAnhHuongSimDangDung() {
        assertEquals(Boolean.FALSE,
                RutInternetDiagnosis.dataLimitReached(json(LIMIT_SIM1_REACHED), 2));
    }

    @Test
    public void hanMucDaTatThiKhongChan() {
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.dataLimitReached(
                json(LIMIT_SIM1_REACHED.replace("\"enabled\":\"1\"", "\"enabled\":\"0\"")), 1));
        assertEquals(Boolean.FALSE, RutInternetDiagnosis.dataLimitReached(json("[]"), 1));
        assertNull(RutInternetDiagnosis.dataLimitReached(null, 1));
    }

    /** 7.25 trả data_used là CHUỖI, và số byte vượt quá giới hạn của int. */
    @Test
    public void hanMucTrenHaiGigabyteKhongBiTranSo() {
        String rutx50 = "[{\"interface\":\"mob1s1a1\",\"data_limit\":5368709120,"
                + "\"data_used\":\"5368709121\",\"enabled\":\"1\"}]";

        assertEquals(Boolean.TRUE, RutInternetDiagnosis.dataLimitReached(json(rutx50), 1));
    }

    // ------------------------------------------------------------------ interface 4G

    @Test
    public void interface4GCoIpLaDaCoPhienDuLieu() {
        String interfaces = "[{\"interface\":\"lan\",\"network_type\":\"wired\","
                + "\"is_up\":true,\"ipv4-address\":[{\"mask\":24,\"address\":\"192.168.1.1\"}]},"
                + "{\"interface\":\"mob1s1a1\",\"network_type\":\"mobile\",\"is_up\":false,"
                + "\"ipv4-address\":[]},"
                + "{\"interface\":\"mob1s2a1\",\"network_type\":\"mobile\",\"is_up\":true,"
                + "\"ipv4-address\":[{\"mask\":32,\"address\":\"10.64.1.2\"}]}]";

        assertEquals(Boolean.TRUE, RutInternetDiagnosis.mobileHasIp(json(interfaces)));
    }

    /** IP của LAN không được tính là IP của 4G. */
    @Test
    public void interface4GLenMaChuaCoIpLaChuaCoPhien() {
        String interfaces = "[{\"interface\":\"lan\",\"network_type\":\"wired\","
                + "\"is_up\":true,\"ipv4-address\":[{\"mask\":24,\"address\":\"192.168.1.1\"}]},"
                + "{\"interface\":\"mob1s2a1\",\"is_up\":true,\"ipv4-address\":[]}]";

        assertEquals(Boolean.FALSE, RutInternetDiagnosis.mobileHasIp(json(interfaces)));
    }

    @Test
    public void khongCoInterface4GNaoThiKhongKetLuan() {
        String interfaces = "[{\"interface\":\"lan\",\"network_type\":\"wired\","
                + "\"is_up\":true,\"ipv4-address\":[{\"mask\":24,\"address\":\"192.168.1.1\"}]}]";

        assertNull(RutInternetDiagnosis.mobileHasIp(json(interfaces)));
    }

    @Test
    public void docKhaySimTuTenInterface() {
        assertEquals(Integer.valueOf(2), RutInternetDiagnosis.simSlotOfInterface("mob1s2a1"));
        assertEquals(Integer.valueOf(1), RutInternetDiagnosis.simSlotOfInterface("mob1s1a1_4"));
        assertNull(RutInternetDiagnosis.simSlotOfInterface("wan"));
        assertNull(RutInternetDiagnosis.simSlotOfInterface(null));
    }
}
