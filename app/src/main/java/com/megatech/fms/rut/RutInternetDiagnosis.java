package com.megatech.fms.rut;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vì sao router "có SIM, có sóng mà không có mạng".
 *
 * <p>Trước đây popup chỉ nói được "Router mất Internet" — và nói cả khi router vẫn ra mạng
 * bình thường, vì phép thử duy nhất là gọi máy chủ FMS: máy chủ sập cũng thành "router mất
 * mạng". Mỗi nguyên nhân dưới đây cần một cách xử lý khác nhau ngoài hiện trường, và phần
 * lớn KHÔNG phải là khởi động lại router (mất 2–4 phút mạng của cả xe).
 *
 * <p>Nguồn số liệu, đều có trên cả RutOS 7.06 (RUT955) lẫn 7.25 (RUTX50):
 * <ul>
 *   <li>{@code GET /api/modems/status}: {@code data_off}, {@code mobile_stage}</li>
 *   <li>{@code GET /api/data_limit/status}: hạn mức data cài trên router</li>
 *   <li>{@code GET /api/interfaces/status}: interface 4G có IP hay chưa</li>
 *   <li>{@code POST /api/diagnostics/actions/ping} và {@code .../nslookup}: phép thử chạy
 *       ngay trên router, không phụ thuộc máy chủ FMS</li>
 * </ul>
 */
public final class RutInternetDiagnosis {

    public enum Cause {
        /** Data bị tắt bằng SMS "mobileoff". */
        DATA_OFF,
        /** SIM không sẵn sàng — chi tiết đã có ở {@link RutSimState}. */
        SIM_NOT_READY,
        /** Router tự chặn vì dùng hết hạn mức data cài trên chính nó. */
        DATA_LIMIT_REACHED,
        /** Modem chưa dựng xong phiên dữ liệu: chưa có IP, hoặc kẹt giữa các bước. */
        CONNECTION_NOT_READY,
        /** Có phiên 4G nhưng gói tin không đi: hết tiền/hết gói, hoặc phiên bị treo. */
        MOBILE_NO_TRAFFIC,
        /** Router ra được Internet bằng IP nhưng không tra được tên miền. */
        DNS_FAILURE,
        /** Router có Internet; chỉ máy chủ FMS không trả lời. Không phải lỗi router. */
        APP_SERVER_UNREACHABLE,
        UNKNOWN;

        /**
         * Khởi động lại kết nối 4G có đáng thử hay không.
         *
         * <p>Không mở cho ca máy chủ FMS: router đang chạy tốt, cắt 4G của nó chỉ làm mất
         * mạng của cả xe mà không sửa được gì. Không mở cho ca hạn mức / data bị tắt / SIM:
         * dựng lại phiên 4G bao nhiêu lần thì router vẫn chặn, vẫn thiếu SIM như cũ.
         */
        public boolean restartMayHelp() {
            return this == CONNECTION_NOT_READY || this == MOBILE_NO_TRAFFIC
                    || this == DNS_FAILURE || this == UNKNOWN;
        }
    }

    /** {@code mobile_stage} = "Mobile connection setup is complete". */
    static final int MOBILE_STAGE_SETUP_COMPLETE = 19;

    /**
     * Các {@code mobile_stage} thuộc về SIM: chờ cắm SIM (1), SIM hỏng (2), chờ PIN (5),
     * chờ PUK (6), SIM bị khoá hẳn (7). Cùng bảng mã trên 7.06 và 7.25.
     */
    private static final int[] SIM_STAGES = {1, 2, 5, 6, 7};

    /** Tên interface di động của RutOS: mob{modem}s{sim}a{apn}, có thể kèm hậu tố "_4". */
    private static final Pattern MOBILE_INTERFACE = Pattern.compile("^mob\\d+s(\\d+)a\\d+");

    private static final Pattern PACKETS_RECEIVED =
            Pattern.compile("(\\d+)\\s+(?:packets\\s+)?received");

    // Các số đo — null nghĩa là router không nói, không bao giờ được hiểu thành false.
    @Nullable public Boolean dataOff;
    @Nullable public Integer mobileStage;
    @Nullable public Boolean dataLimitReached;
    @Nullable public Boolean mobileHasIp;
    @Nullable public Boolean routerReachesIp;
    @Nullable public Boolean routerResolvesDns;

    public Cause cause = Cause.UNKNOWN;

    /**
     * Ghép các số đo thành một nguyên nhân.
     *
     * <p>Ping từ router đứng ĐẦU: router ra được Internet thì mọi chuyện về SIM, hạn mức hay
     * phiên 4G đều không phải lý do máy này mất mạng (router có thể đang đi bằng cáp WAN).
     * Nói "SIM lỗi" trong ca đó là chỉ sai việc cho người đứng cạnh xe.
     */
    public static Cause classify(@Nullable RutSimState simState,
                                 @Nullable Boolean dataConnected,
                                 @Nullable Boolean dataOff,
                                 @Nullable Integer mobileStage,
                                 @Nullable Boolean dataLimitReached,
                                 @Nullable Boolean mobileHasIp,
                                 @Nullable Boolean routerReachesIp,
                                 @Nullable Boolean routerResolvesDns) {
        if (Boolean.TRUE.equals(routerReachesIp)) {
            if (Boolean.FALSE.equals(routerResolvesDns)) return Cause.DNS_FAILURE;
            if (Boolean.TRUE.equals(routerResolvesDns)) return Cause.APP_SERVER_UNREACHABLE;
            // Ra được bằng IP mà không biết DNS ra sao: chưa đủ để đổ lỗi cho máy chủ FMS.
            return Cause.UNKNOWN;
        }

        if (Boolean.TRUE.equals(dataOff)) return Cause.DATA_OFF;
        if (isSimProblem(simState) || isSimStage(mobileStage)) return Cause.SIM_NOT_READY;
        if (Boolean.TRUE.equals(dataLimitReached)) return Cause.DATA_LIMIT_REACHED;

        boolean stageIncomplete = mobileStage != null
                && mobileStage != MOBILE_STAGE_SETUP_COMPLETE;
        if (Boolean.FALSE.equals(dataConnected) || Boolean.FALSE.equals(mobileHasIp)
                || stageIncomplete)
            return Cause.CONNECTION_NOT_READY;

        if (Boolean.FALSE.equals(routerReachesIp)) return Cause.MOBILE_NO_TRAFFIC;
        return Cause.UNKNOWN;
    }

    private static boolean isSimProblem(@Nullable RutSimState state) {
        return state == RutSimState.NO_SIM || state == RutSimState.PIN_LOCKED
                || state == RutSimState.SIM_PRESENT_NOT_REGISTERED;
    }

    private static boolean isSimStage(@Nullable Integer stage) {
        if (stage == null) return false;
        for (int simStage : SIM_STAGES) if (simStage == stage) return true;
        return false;
    }

    // ---------------------------------------------------------------- đọc số đo

    /**
     * Router ping tới được hay không, đọc từ chữ in ra của lệnh ping.
     *
     * <p>RutOS trả nguyên văn đầu ra của ping trong {@code data.response}, không có trường
     * kết quả riêng. Không nhận ra được thì trả null: một đầu ra lạ không phải là bằng chứng
     * mất mạng.
     */
    @Nullable
    static Boolean pingReached(@Nullable String output) {
        if (output == null || output.trim().isEmpty()) return null;
        String text = output.toLowerCase(Locale.US);

        Matcher received = PACKETS_RECEIVED.matcher(text);
        if (received.find()) return Integer.parseInt(received.group(1)) > 0;
        if (text.contains("bytes from")) return true;
        if (text.contains("100% packet loss") || text.contains("unreachable")
                || text.contains("bad address") || text.contains("unknown host")
                || text.contains("timed out"))
            return false;
        return null;
    }

    /**
     * nslookup có ra địa chỉ hay không.
     *
     * <p>Dòng "Address" ĐẦU TIÊN là của máy chủ DNS, không phải kết quả — chỉ dòng Address
     * nằm SAU "Name:" mới chứng minh được tên miền đã phân giải.
     */
    @Nullable
    static Boolean nslookupResolved(@Nullable String output) {
        if (output == null || output.trim().isEmpty()) return null;
        String text = output.toLowerCase(Locale.US);

        if (text.contains("can't find") || text.contains("can't resolve")
                || text.contains("nxdomain") || text.contains("servfail")
                || text.contains("no servers could be reached") || text.contains("timed out"))
            return false;
        int name = text.indexOf("name:");
        if (name >= 0 && text.indexOf("address", name) > name) return true;
        return null;
    }

    /**
     * Hạn mức data của SIM ĐANG DÙNG đã chạm trần chưa.
     *
     * <p>Chỉ tính interface của khay đang dùng: SIM 1 hết hạn mức nên router chuyển sang
     * SIM 2 là chuyện bình thường, và SIM 2 vẫn chạy tốt — đổ cho hạn mức của SIM 1 là sai.
     *
     * @return false khi router trả lời mà không có hạn mức nào đang chặn; null khi không đọc
     * được gì.
     */
    @Nullable
    static Boolean dataLimitReached(@Nullable JsonElement data, @Nullable Integer activeSimSlot) {
        if (data == null || data.isJsonNull()) return null;

        List<JsonObject> limits = RutJson.objects(data, "interface", "data_limit", "data_used");
        for (JsonObject limit : limits) {
            if (!Boolean.TRUE.equals(RutJson.bool(limit, "enabled"))) continue;
            Integer slot = simSlotOfInterface(RutJson.string(limit, "interface"));
            if (activeSimSlot != null && slot != null && !slot.equals(activeSimSlot)) continue;

            Long cap = longValue(limit, "data_limit");
            Long used = longValue(limit, "data_used");
            if (cap != null && used != null && cap > 0 && used >= cap) return true;
        }
        return false;
    }

    /**
     * Có interface di động nào đang lên và đã nhận IPv4 chưa.
     *
     * <p>Không lọc theo khay: router hai SIM có một interface cho mỗi khay và chỉ khay đang
     * dùng được dựng lên — "có ít nhất một cái có IP" là đủ.
     *
     * @return null khi router không liệt kê interface di động nào.
     */
    @Nullable
    static Boolean mobileHasIp(@Nullable JsonElement data) {
        if (data == null || data.isJsonNull()) return null;

        boolean sawMobile = false;
        for (JsonObject iface : RutJson.objects(data, "interface", "ifname", "is_up")) {
            if (!isMobileInterface(iface)) continue;
            sawMobile = true;
            Boolean up = RutJson.bool(iface, "is_up", "up");
            if (Boolean.FALSE.equals(up)) continue;
            if (hasIpv4(iface)) return true;
        }
        return sawMobile ? Boolean.FALSE : null;
    }

    private static boolean isMobileInterface(JsonObject iface) {
        if ("mobile".equalsIgnoreCase(RutJson.string(iface, "network_type"))) return true;
        return simSlotOfInterface(RutJson.string(iface, "interface", "id", "name")) != null;
    }

    private static boolean hasIpv4(JsonObject iface) {
        JsonElement addresses = iface.get("ipv4-address");
        if (addresses != null && addresses.isJsonArray() && addresses.getAsJsonArray().size() > 0)
            return true;
        JsonElement plain = iface.get("ipaddrs");
        if (plain != null && plain.isJsonArray()) {
            JsonArray array = plain.getAsJsonArray();
            for (JsonElement item : array)
                if (item != null && item.isJsonPrimitive() && item.getAsString().contains("."))
                    return true;
        }
        return false;
    }

    /** Khay SIM trong tên interface "mob1s2a1" → 2; không phải interface di động → null. */
    @Nullable
    static Integer simSlotOfInterface(@Nullable String name) {
        if (name == null) return null;
        Matcher matcher = MOBILE_INTERFACE.matcher(name.trim().toLowerCase(Locale.US));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * Số byte có thể vượt 2 GB — {@link RutJson#integer} sẽ tràn, nên đọc riêng thành long.
     * 7.06 trả {@code data_used} là số, 7.25 trả là chuỗi: nhận cả hai.
     */
    @Nullable
    private static Long longValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsLong();
        } catch (RuntimeException ex) {
            try {
                return Long.valueOf(value.getAsString().trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
