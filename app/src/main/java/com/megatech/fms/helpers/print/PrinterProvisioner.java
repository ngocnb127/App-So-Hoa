package com.megatech.fms.helpers.print;

import android.content.Context;
import android.content.SharedPreferences;

import com.megatech.fms.helpers.Logger;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLinkOs;

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
 * <p>Kiểm tra tốn một lượt hỏi đáp qua Bluetooth nên KHÔNG chạy mỗi lần in: kết quả được
 * nhớ theo địa chỉ MAC của từng máy in, lần sau chỉ đọc SharedPreferences.
 */
public final class PrinterProvisioner {

    private static final String LOG_TAG = "ZEBRA_SETUP";

    /** Font nguồn nằm trong assets của ứng dụng. */
    static final String FONT_ASSET = "fonts/OpenSans-Re.ttf";

    /** Đường dẫn trên máy in, phải khớp đúng tên mà ZPL của phiếu tham chiếu. */
    static final String FONT_PRINTER_PATH = "E:OPENSANS-RE.TTF";

    /** Tên trần dùng để dò trong danh sách file của máy in. */
    static final String FONT_FILE_NAME = "OPENSANS-RE.TTF";

    private static final String PREF_FILE = "FMS_PRINTER_SETUP";

    private PrinterProvisioner() {
    }

    /** Kết quả chuẩn bị máy in. */
    public enum Result {
        /** Máy in sẵn sàng nhận phiếu. */
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
     * Kiểm tra và nạp những gì còn thiếu. Gọi sau khi {@code connection.open()}.
     *
     * @param macAddress địa chỉ máy in, dùng làm khoá ghi nhớ. Null thì không nhớ, lần in
     *                   nào cũng kiểm tra lại.
     */
    public static Result prepare(Context context, Connection connection, String macAddress) {
        if (context == null || connection == null) return Result.FAILED;
        if (isRemembered(context, macAddress)) return Result.READY;

        try {
            ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);

            if (printer.getPrinterControlLanguage() == PrinterLanguage.CPCL) {
                switchToZpl(connection);
                // Cố ý KHÔNG ghi nhớ: máy in vừa nhận lệnh khởi động lại, chưa có gì được
                // xác nhận. Lượt in sau sẽ kiểm tra lại từ đầu.
                return Result.SWITCHED_TO_ZPL_NEEDS_RETRY;
            }

            if (hasFont(printer)) {
                remember(context, macAddress);
                return Result.READY;
            }

            uploadFont(context, connection);
            remember(context, macAddress);
            return Result.READY;

        } catch (Exception ex) {
            // Không chặn việc in: máy in có thể đã đủ điều kiện từ trước, và một tờ phiếu
            // không in được vì bước chuẩn bị thất bại thì tệ hơn là cứ thử in.
            Logger.appendLog(LOG_TAG, "Chuẩn bị máy in thất bại: " + ex.getMessage());
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
        /** Ngôn ngữ máy in đang chạy: ZPL / CPCL / LINE_PRINT, hoặc null nếu không đọc được. */
        public PrinterLanguage language;
        /** Font phiếu cần đã có trên máy in chưa. */
        public boolean fontInstalled;
        /** Font vừa được nạp trong chính lượt này. */
        public boolean fontJustUploaded;
        /** Máy in báo sẵn sàng in. */
        public boolean readyToPrint;
        /** Hết giấy / mở đầu in — hai lỗi vật lý hay gặp nhất. */
        public boolean paperOut;
        public boolean headOpen;
        /** Lý do không đọc được tình trạng, null nếu đọc được hết. */
        public String error;

        public boolean isZpl() {
            return language == PrinterLanguage.ZPL;
        }
    }

    /**
     * Đọc tình trạng máy in và nạp font nếu còn thiếu — dùng cho luồng In thử.
     *
     * <p>Khác {@link #prepare}: KHÔNG đọc cờ ghi nhớ và không ghi cờ. In thử là lúc người
     * dùng muốn biết sự thật hiện tại của máy in, một cờ đã lưu từ tuần trước không trả lời
     * được câu hỏi đó.
     */
    public static Report inspect(Context context, Connection connection) {
        Report report = new Report();
        if (context == null || connection == null) {
            report.error = "Chưa có kết nối máy in";
            return report;
        }

        try {
            ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
            report.language = printer.getPrinterControlLanguage();

            // Máy đang ở CPCL thì mọi lệnh ZPL bên dưới đều vô nghĩa; dừng ở đây và để
            // hàm gọi quyết định có chuyển ngôn ngữ hay không.
            if (report.language == PrinterLanguage.CPCL) return report;

            try {
                com.zebra.sdk.printer.PrinterStatus status = printer.getCurrentStatus();
                report.readyToPrint = status.isReadyToPrint;
                report.paperOut = status.isPaperOut;
                report.headOpen = status.isHeadOpen;
            } catch (Exception ex) {
                // Không đọc được tình trạng không có nghĩa là không in được: vẫn đi tiếp
                // để kiểm tra font, phần quan trọng hơn.
                Logger.appendLog(LOG_TAG, "Không đọc được tình trạng máy in: " + ex.getMessage());
            }

            report.fontInstalled = hasFont(printer);
            if (!report.fontInstalled) {
                uploadFont(context, connection);
                report.fontInstalled = true;
                report.fontJustUploaded = true;
            }
        } catch (Exception ex) {
            report.error = ex.getMessage() == null ? "Lỗi không xác định" : ex.getMessage();
            Logger.appendLog(LOG_TAG, "Kiểm tra máy in thất bại: " + report.error);
        }
        return report;
    }

    /** Máy in đã có sẵn font thì không nạp lại — nạp mất khoảng một phút qua Bluetooth. */
    private static boolean hasFont(ZebraPrinter printer) throws Exception {
        String[] files = printer.retrieveFileNames(new String[]{"TTF"});
        if (files == null) return false;
        for (String file : files) {
            if (matchesFont(file)) {
                Logger.appendLog(LOG_TAG, "Máy in đã có font: " + file);
                return true;
            }
        }
        return false;
    }

    /**
     * Tên file máy in trả về có thể kèm ổ đĩa và khác kiểu chữ hoa thường
     * ({@code E:OPENSANS-RE.TTF}, {@code OpenSans-Re.ttf}), nên so khớp phải bỏ qua cả hai.
     */
    static boolean matchesFont(String printerFileName) {
        if (printerFileName == null) return false;
        String name = printerFileName.trim().toUpperCase(java.util.Locale.US);
        int drive = name.indexOf(':');
        if (drive >= 0) name = name.substring(drive + 1);
        return name.equals(FONT_FILE_NAME);
    }

    private static void uploadFont(Context context, Connection connection) throws Exception {
        ZebraPrinterLinkOs linkOs = ZebraPrinterFactory.getLinkOsPrinter(connection);
        try (InputStream font = context.getAssets().open(FONT_ASSET)) {
            Logger.appendLog(LOG_TAG, "Nạp font " + FONT_PRINTER_PATH + " vào máy in");
            linkOs.downloadTtfFont(font, FONT_PRINTER_PATH);
        }
        Logger.appendLog(LOG_TAG, "Nạp font xong");
    }

    /**
     * Chuyển máy in từ CPCL sang ZPL.
     *
     * <p>Ba lệnh CPCL, mỗi lệnh một dòng kết bằng CR: đặt ngôn ngữ, đặt ngôn ngữ báo cho
     * plug-and-play, rồi khởi động lại để thiết lập có hiệu lực. Sau lệnh cuối máy in tự
     * reboot nên kết nối Bluetooth đứt — đó là lý do hàm gọi phải báo người dùng in lại,
     * chứ không cố in tiếp trên một kết nối đã chết.
     */
    public static void switchToZplMode(Connection connection) throws Exception {
        switchToZpl(connection);
    }

    private static void switchToZpl(Connection connection) throws Exception {
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
