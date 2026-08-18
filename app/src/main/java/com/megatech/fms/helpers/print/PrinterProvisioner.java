package com.megatech.fms.helpers.print;

import android.content.Context;
import android.content.SharedPreferences;

import com.megatech.fms.helpers.Logger;
import com.zebra.sdk.comm.Connection;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Đưa máy in về đúng trạng thái mà phiếu cần TRƯỚC khi gửi ZPL.
 *
 * <p>Toàn bộ phiếu của ứng dụng mở đầu bằng {@code ^CWZ,E:OPENSANS-RE.TTF} rồi in tiếng
 * Việt qua {@code ^CI28}. Hai điều kiện phải đúng thì bản in mới ra đúng:
 *
 * <ol>
 *   <li>Máy in đang ở ngôn ngữ ZPL. Máy đặt ở CPCL nhận nguyên khối ZPL như văn bản thô —
 *       ra giấy trắng hoặc một mớ ký tự lệnh.</li>
 *   <li>Font OPENSANS-RE.TTF có trên ổ E:. Thiếu font thì máy in rơi về font mặc định,
 *       vốn không có glyph tiếng Việt, nên mọi chữ có dấu ra ô vuông hoặc mất dấu.</li>
 * </ol>
 *
 * <p>Cả hai đều là trạng thái NẰM TRONG MÁY IN, không phải trong ứng dụng: máy mới, máy
 * vừa reset về mặc định hay máy đổi giữa các xe đều có thể sai mà không ai biết cho tới
 * lúc cầm tờ phiếu hỏng trên tay.
 *
 * <h3>Vì sao không dùng ZebraPrinterFactory</h3>
 *
 * <p>Đo trên máy thật 18-08 12:08: {@code ZebraPrinterFactory.getInstance(con)} ném
 * {@code NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper} từ
 * {@code com.zebra.sdk.settings.internal.JsonHelper}. Nhánh dò cấu hình của SDK cần
 * thư viện Jackson, thứ không có trong ứng dụng — và kéo Jackson vào chỉ để hỏi máy in
 * đang ở ngôn ngữ nào là đổi lấy hơn một megabyte cho cả hai bản phát hành.
 *
 * <p>Nên lớp này nói chuyện THẲNG với máy in bằng lệnh SGD và ZPL trên {@link Connection}.
 * Không phụ thuộc phần SDK nào có thể thiếu lớp, và mỗi lệnh đều tra được trong tài liệu
 * ZPL của Zebra.
 *
 * <p>Kiểm tra tốn vài lượt hỏi đáp qua Bluetooth nên KHÔNG chạy mỗi lần in: kết quả được
 * nhớ theo địa chỉ MAC của từng máy in, lần sau chỉ đọc SharedPreferences.
 */
public final class PrinterProvisioner {

    private static final String LOG_TAG = "ZEBRA_SETUP";

    /** Font nguồn nằm trong assets của ứng dụng. */
    static final String FONT_ASSET = "fonts/OpenSans-Re.ttf";

    /** Tên file trên máy in, KHÔNG kèm phần mở rộng — dạng lệnh ~DY yêu cầu. */
    static final String FONT_NAME = "OPENSANS-RE";

    /** Đường dẫn đầy đủ, phải khớp đúng cái mà ZPL của phiếu nạp bằng ^CWZ. */
    static final String FONT_PRINTER_PATH = "E:OPENSANS-RE.TTF";

    private static final String PREF_FILE = "FMS_PRINTER_SETUP";

    /** Máy in chỉ trả lời sau khi xử lý xong lệnh; qua Bluetooth cần rộng tay. */
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MORE_DATA_WAIT_MS = 500;

    private PrinterProvisioner() {
    }

    /** Kết quả chuẩn bị máy in. */
    public enum Result {
        READY,
        /**
         * Đã gửi lệnh chuyển CPCL → ZPL. Máy in đang khởi động lại nên KHÔNG in được ngay;
         * người dùng cần in lại sau khi máy in lên xong.
         */
        SWITCHED_TO_ZPL_NEEDS_RETRY,
        /** Không chuẩn bị được. Vẫn cho in — có thể phiếu vẫn ra đúng nếu máy đã sẵn sàng. */
        FAILED
    }

    /**
     * Kiểm tra và nạp những gì còn thiếu. Gọi sau khi kết nối đã mở.
     *
     * @param macAddress địa chỉ máy in, dùng làm khoá ghi nhớ. Null thì không nhớ.
     */
    public static Result prepare(Context context, Connection connection, String macAddress) {
        if (context == null || connection == null) return Result.FAILED;
        if (isRemembered(context, macAddress)) return Result.READY;

        try {
            if (isCpcl(readLanguage(connection))) {
                switchToZplMode(connection);
                // Cố ý KHÔNG ghi nhớ: máy in vừa nhận lệnh khởi động lại, chưa có gì được
                // xác nhận. Lượt in sau sẽ kiểm tra lại từ đầu.
                return Result.SWITCHED_TO_ZPL_NEEDS_RETRY;
            }

            if (!hasFont(connection)) uploadFont(context, connection);

            remember(context, macAddress);
            return Result.READY;

        } catch (Exception ex) {
            // Không chặn việc in: máy in có thể đã đủ điều kiện từ trước, và một tờ phiếu
            // không in được vì bước chuẩn bị thất bại thì tệ hơn là cứ thử in.
            Logger.appendLog(LOG_TAG, "Chuẩn bị máy in thất bại: " + Logger.describe(ex));
            return Result.FAILED;
        }
    }

    /**
     * Tình trạng máy in đọc được tại thời điểm in thử, để IN THẲNG LÊN PHIẾU THỬ.
     *
     * <p>Cố ý in ra giấy chứ không chỉ ghi log: người dựng máy in đứng cạnh máy, cầm tờ
     * phiếu là biết ngay thiếu gì, không phải lấy log về rồi mở máy tính đọc.
     */
    public static final class Report {
        /** Giá trị thô máy in trả về cho {@code device.languages}, null nếu không trả lời. */
        public String language;
        public boolean fontInstalled;
        public boolean fontJustUploaded;
        /** Giá trị thô của {@code media.status} và {@code head.latch}. */
        public String mediaStatus;
        public String headLatch;
        /** Lý do không đọc được, null nếu đọc được hết. */
        public String error;

        public boolean isZpl() {
            return language != null && language.toLowerCase(java.util.Locale.US).contains("zpl");
        }

        public boolean isCpclMode() {
            return isCpcl(language);
        }
    }

    /**
     * Đọc tình trạng máy in và nạp font nếu còn thiếu — dùng cho luồng In thử.
     *
     * <p>Khác {@link #prepare}: KHÔNG đọc cờ ghi nhớ và không ghi cờ. In thử là lúc người
     * dùng muốn biết sự thật hiện tại của máy in, một cờ đã lưu từ tuần trước không trả
     * lời được câu hỏi đó.
     */
    public static Report inspect(Context context, Connection connection) {
        Report report = new Report();
        if (context == null || connection == null) {
            report.error = "Chưa có kết nối máy in";
            return report;
        }

        try {
            report.language = readLanguage(connection);

            // Máy đang ở CPCL thì mọi lệnh ZPL bên dưới đều vô nghĩa; dừng ở đây và để
            // hàm gọi quyết định có chuyển ngôn ngữ hay không.
            if (report.isCpclMode()) return report;

            report.mediaStatus = getVar(connection, "media.status");
            report.headLatch = getVar(connection, "head.latch");

            report.fontInstalled = hasFont(connection);
            if (!report.fontInstalled) {
                uploadFont(context, connection);
                report.fontInstalled = true;
                report.fontJustUploaded = true;
            }
        } catch (Exception ex) {
            report.error = Logger.describe(ex);
            Logger.appendLog(LOG_TAG, "Kiểm tra máy in thất bại: " + report.error);
        }
        return report;
    }

    // ---------------------------------------------------------------- ngôn ngữ

    /**
     * Ngôn ngữ máy in đang chạy, đọc bằng lệnh SGD.
     *
     * <p>SGD chạy được ở CẢ hai chế độ ZPL và CPCL — đó là lý do dùng nó thay vì một lệnh
     * ZPL: một máy đang ở CPCL sẽ không trả lời lệnh ZPL, và ta lại không phân biệt được
     * "máy ở CPCL" với "máy hỏng".
     */
    static String readLanguage(Connection connection) throws Exception {
        return getVar(connection, "device.languages");
    }

    static boolean isCpcl(String language) {
        if (language == null) return false;
        String value = language.toLowerCase(java.util.Locale.US);
        // "line_print" cũng không hiểu ZPL, xử lý y như CPCL.
        return value.contains("cpcl") || value.contains("line_print");
    }

    /** Đọc một biến SGD. Trả null khi máy in không trả lời hoặc không có biến đó. */
    private static String getVar(Connection connection, String name) throws Exception {
        byte[] raw = connection.sendAndWaitForResponse(
                ("! U1 getvar \"" + name + "\"\r\n").getBytes(StandardCharsets.US_ASCII),
                READ_TIMEOUT_MS, MORE_DATA_WAIT_MS, null);
        return cleanResponse(raw);
    }

    /**
     * Bóc giá trị khỏi phản hồi SGD.
     *
     * <p>Máy in trả về giá trị trong dấu nháy kép kèm xuống dòng, ví dụ {@code "zpl"\r\n}.
     * Biến không tồn tại thì trả {@code "?"} — coi như không đọc được, chứ không phải một
     * giá trị hợp lệ.
     */
    static String cleanResponse(byte[] raw) {
        if (raw == null || raw.length == 0) return null;
        String value = new String(raw, StandardCharsets.UTF_8).trim();
        if (value.startsWith("\"")) value = value.substring(1);
        if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
        value = value.trim();
        if (value.isEmpty() || "?".equals(value)) return null;
        return value;
    }

    /**
     * Chuyển máy in từ CPCL sang ZPL.
     *
     * <p>Ba lệnh CPCL, mỗi lệnh KẾT BẰNG CR: đặt ngôn ngữ, đặt ngôn ngữ báo cho
     * plug-and-play, rồi khởi động lại để thiết lập có hiệu lực. Sau lệnh cuối máy in tự
     * reboot nên kết nối Bluetooth đứt — hàm gọi phải báo người dùng in lại, chứ không cố
     * in tiếp trên một kết nối đã chết.
     */
    public static void switchToZplMode(Connection connection) throws Exception {
        Logger.appendLog(LOG_TAG, "Máy in đang ở chế độ CPCL — gửi lệnh chuyển sang ZPL");
        connection.write(switchToZplCommands().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Ba lệnh CPCL chuyển máy in sang ZPL, mỗi lệnh KẾT BẰNG CR.
     *
     * <p>Máy in CPCL phân tách lệnh bằng CR; dùng LF hay CRLF thì lệnh không được nhận và
     * máy vẫn nằm nguyên ở CPCL — hỏng im lặng, đúng loại lỗi khó lần ra nhất.
     */
    static String switchToZplCommands() {
        return "! U1 setvar \"device.languages\" \"zpl\"\r"
                + "! U1 setvar \"device.pnp_option\" \"zpl\"\r"
                + "! U1 do \"device.reset\" \"\"\r";
    }

    // ------------------------------------------------------------------- font

    /**
     * Máy in đã có sẵn font chưa. Nạp lại mất khoảng một phút qua Bluetooth nên phải hỏi
     * trước.
     *
     * <p>{@code ^HW} liệt kê file trên một ổ; phản hồi là danh sách tên file dạng chữ.
     */
    static boolean hasFont(Connection connection) throws Exception {
        byte[] raw = connection.sendAndWaitForResponse(
                "^XA^HWE:*.TTF^XZ".getBytes(StandardCharsets.US_ASCII),
                READ_TIMEOUT_MS, MORE_DATA_WAIT_MS, null);
        boolean found = listingContainsFont(raw);
        Logger.appendLog(LOG_TAG, found
                ? "Máy in đã có font " + FONT_PRINTER_PATH
                : "Máy in chưa có font " + FONT_PRINTER_PATH);
        return found;
    }

    /**
     * Dò tên font trong danh sách file máy in trả về.
     *
     * <p>Tên có thể kèm ổ đĩa và khác kiểu chữ hoa thường, nên so khớp bỏ qua cả hai. Nhận
     * nhầm thành "chưa có" thì lần in nào cũng nạp lại font mất cả phút; nhận nhầm thành
     * "đã có" thì mọi chữ có dấu in ra ô vuông.
     */
    static boolean listingContainsFont(byte[] raw) {
        if (raw == null || raw.length == 0) return false;
        String listing = new String(raw, StandardCharsets.UTF_8).toUpperCase(java.util.Locale.US);
        return listing.contains(FONT_NAME + ".TTF");
    }

    /**
     * Nạp font lên ổ E: bằng lệnh ~DY.
     *
     * <p>{@code ~DYd:f,b,x,t,w,data} — ổ đĩa, tên KHÔNG phần mở rộng, B là dữ liệu nhị
     * phân, TTF là loại file, t là tổng số byte. Tham số w chỉ dùng cho ảnh .GRF nên để
     * trống.
     *
     * <p>Số byte trong tiêu đề phải khớp CHÍNH XÁC số byte gửi sau đó: khai thiếu thì máy
     * in cắt cụt font, khai thừa thì nó chờ mãi phần còn lại và treo cả phiên.
     */
    static void uploadFont(Context context, Connection connection) throws Exception {
        byte[] font = readAsset(context, FONT_ASSET);
        String header = "~DYE:" + FONT_NAME + ",B,TTF," + font.length + ",,";

        Logger.appendLog(LOG_TAG, "Nạp font " + FONT_PRINTER_PATH
                + " (" + font.length + " byte) vào máy in");
        connection.write(header.getBytes(StandardCharsets.US_ASCII));
        connection.write(font);
        Logger.appendLog(LOG_TAG, "Gửi xong font");
    }

    private static byte[] readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(160 * 1024);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            return out.toByteArray();
        }
    }

    // --------------------------------------------------------------- ghi nhớ

    private static boolean isRemembered(Context context, String macAddress) {
        if (macAddress == null) return false;
        return prefs(context).getBoolean(macAddress, false);
    }

    private static void remember(Context context, String macAddress) {
        if (macAddress == null) return;
        prefs(context).edit().putBoolean(macAddress, true).apply();
    }

    /**
     * Quên máy in này để lượt in sau kiểm tra lại từ đầu. Gọi khi in lỗi: máy in có thể đã
     * bị reset về mặc định hoặc đã đổi sang máy khác cùng địa chỉ đã lưu.
     */
    public static void forget(Context context, String macAddress) {
        if (context == null || macAddress == null) return;
        prefs(context).edit().remove(macAddress).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }
}
