package com.megatech.fms.helpers.print;

import android.content.Context;
import android.content.SharedPreferences;

import com.megatech.fms.helpers.Logger;
import com.zebra.sdk.comm.Connection;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            if (!isContinuous(readMediaType(connection))) setContinuousMedia(connection);

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
        /** Giá trị thô của {@code media.type}, null nếu máy in không trả lời. */
        public String mediaType;
        /** Máy in đang ở loại giấy khác và vừa được đặt lại về liên tục. */
        public boolean mediaJustSetContinuous;
        /** Giá trị thô của {@code media.status} và {@code head.latch}. */
        public String mediaStatus;
        public String headLatch;
        /** Lý do không đọc được, null nếu đọc được hết. */
        public String error;
        /**
         * Tên Bluetooth và số serial máy in đang báo. In lên phiếu thử để sau khi factory,
         * người dùng cầm tờ phiếu là biết máy đã về tên nào.
         */
        public String bluetoothName;
        public String serial;

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

            report.bluetoothName = readBluetoothName(connection);
            report.serial = readSerial(connection);
            report.mediaStatus = getVar(connection, "media.status");
            report.headLatch = getVar(connection, "head.latch");

            report.fontInstalled = hasFont(connection);
            if (!report.fontInstalled) {
                uploadFont(context, connection);
                report.fontInstalled = true;
                report.fontJustUploaded = true;
            }

            report.mediaType = readMediaType(connection);
            if (!isContinuous(report.mediaType)) {
                setContinuousMedia(connection);
                report.mediaJustSetContinuous = true;
            }
        } catch (Exception ex) {
            report.error = Logger.describe(ex);
            Logger.appendLog(LOG_TAG, "Kiểm tra máy in thất bại: " + report.error);
        }
        return report;
    }

    // ------------------------------------------------------------ loại giấy

    /**
     * Đặt máy in về GIẤY LIÊN TỤC (continuous).
     *
     * <p>Phiếu của ứng dụng là giấy cuộn trơn, không có khe (gap) hay vạch đen (mark) nào để
     * máy in dò đầu trang. Máy đặt ở "gap/notch" sẽ đi tìm khe không tồn tại: nó nhả giấy cho
     * tới hết ngưỡng dò rồi báo lỗi hết giấy — người dùng thấy máy nhả một đoạn dài rồi dừng,
     * hoặc mỗi phiếu ra kèm một khoảng trắng lớn. Đây là trạng thái NẰM TRONG MÁY IN, máy mới
     * hoặc máy vừa reset về mặc định đều có thể sai.
     *
     * <p>Chiều dài mỗi phiếu do {@code ^LL} của {@link com.megatech.fms.helpers.ZplLayoutBuilder}
     * quyết định — chỉ ở chế độ liên tục thì {@code ^LL} mới có tác dụng.
     */
    static final String MEDIA_TYPE_CONTINUOUS = "continuous";

    /**
     * Loại giấy máy in đang đặt, đọc bằng SGD. Null khi máy in không trả lời hoặc đời máy
     * không có biến này — khi đó vẫn gửi lệnh đặt, vì đặt thừa vô hại còn đặt thiếu thì
     * phiếu hỏng.
     *
     * <p>Đọc {@code ezpl.media_type} trước: đây là biến SGD tài liệu Zebra ghi cho loại giấy
     * ZPL, giá trị {@code "continuous" / "gap/notch" / "mark"} — đúng chữ "Media Type:
     * continuous" trên nhãn cấu hình, mặc định sau factory là "gap/notch". Chủ dự án chốt
     * ngày 2026-09-14. {@code media.type} (biến cũ app từng đọc, không có trong tài liệu
     * ZPL/SGD) chỉ còn là đường lùi cho đời máy không trả lời biến kia.
     */
    static String readMediaType(Connection connection) throws Exception {
        String value = getVar(connection, MEDIA_TYPE_VAR);
        return value != null ? value : getVar(connection, MEDIA_TYPE_VAR_FALLBACK);
    }

    static final String MEDIA_TYPE_VAR = "ezpl.media_type";
    static final String MEDIA_TYPE_VAR_FALLBACK = "media.type";

    static boolean isContinuous(String mediaType) {
        if (mediaType == null) return false;
        return mediaType.toLowerCase(java.util.Locale.US).contains(MEDIA_TYPE_CONTINUOUS);
    }

    /**
     * Chuyển sang giấy liên tục và LƯU LẠI trong máy in.
     *
     * <p>Dùng ZPL chứ không dùng SGD: {@code ^MNN} là lệnh chuẩn có ở mọi đời máy ZPL, trong
     * khi tên biến SGD của loại giấy khác nhau giữa các dòng máy. {@code ^JUS} ghi thiết lập
     * vào bộ nhớ vĩnh viễn nên máy in tắt mở lại vẫn đúng — không có nó thì mỗi lần thay pin
     * là hỏng lại. Lệnh này KHÔNG làm máy in khởi động lại, nên in tiếp được ngay.
     */
    static void setContinuousMedia(Connection connection) throws Exception {
        Logger.appendLog(LOG_TAG, "Đặt máy in về giấy liên tục (^MNN) và lưu thiết lập");
        connection.write(continuousMediaCommands().getBytes(StandardCharsets.US_ASCII));
    }

    /** {@code ^MNN} = giấy liên tục, không dò đầu trang; {@code ^JUS} = lưu vĩnh viễn. */
    static String continuousMediaCommands() {
        return "^XA^MNN^JUS^XZ";
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

    // ---------------------------------------------------------------- factory

    /**
     * Tên Bluetooth dài nhất máy in nhận — giới hạn của SGD {@code bluetooth.friendly_name}
     * trong ZPL/SGD Programming Guide của Zebra.
     */
    public static final int BLUETOOTH_NAME_MAX = 17;

    /** Nghỉ giữa các lệnh factory: tài liệu ^JU của Zebra cảnh báo lỗi thời gian khi dồn lệnh. */
    private static final long FACTORY_STEP_PAUSE_MS = 500;

    /** Máy in nào đang cầm trên tay — đọc TRƯỚC khi factory để người dùng ghi lại. */
    public static final class Identity {
        /** Địa chỉ MAC app đang dùng để nối. Factory không đổi MAC. */
        public String address;
        /** Tên Bluetooth hiện tại, null nếu không đọc được. */
        public String bluetoothName;
        /**
         * Số serial. Theo tài liệu Zebra, {@code bluetooth.friendly_name} chưa đặt thì mặc
         * định là số serial — tức gần như chắc là tên máy in hiện ra sau factory.
         */
        public String serial;
        /** Giá trị thô của {@code device.languages}. */
        public String language;
        /** Lý do không đọc được, null nếu đọc được. */
        public String error;

        public boolean isCpclMode() {
            return isCpcl(language);
        }
    }

    /**
     * Đọc tên Bluetooth, serial và ngôn ngữ. Toàn bằng SGD nên chạy được cả khi máy đang ở
     * CPCL — hàm gọi cần biết điều đó để CHẶN factory, vì lệnh factory là ZPL.
     */
    public static Identity readIdentity(Connection connection) {
        Identity identity = new Identity();
        if (connection == null) {
            identity.error = "Chưa có kết nối máy in";
            return identity;
        }
        try {
            identity.language = readLanguage(connection);
            identity.bluetoothName = readBluetoothName(connection);
            identity.serial = readSerial(connection);
        } catch (Exception ex) {
            identity.error = Logger.describe(ex);
            Logger.appendLog(LOG_TAG, "Đọc thông tin máy in thất bại: " + identity.error);
        }
        return identity;
    }

    static String readBluetoothName(Connection connection) throws Exception {
        return getVar(connection, "bluetooth.friendly_name");
    }

    static String readSerial(Connection connection) throws Exception {
        return getVar(connection, "device.unique_id");
    }

    /** Tên người dùng nhập, đã cắt khoảng trắng. Null khi để trống — tức giữ tên mặc định. */
    public static String normalizeBluetoothName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Lý do tên Bluetooth không dùng được, null nếu dùng được (kể cả để trống).
     *
     * <p>Chỉ nhận chữ không dấu, số, khoảng trắng và {@code - _ .}. Dấu nháy kép làm gãy
     * cú pháp lệnh SGD — máy in bỏ qua cả lệnh mà không báo gì; chữ có dấu thì tuỳ đời
     * firmware, có máy hiện ra ký tự rác trong danh sách Bluetooth.
     */
    public static String bluetoothNameProblem(String name) {
        String value = normalizeBluetoothName(name);
        if (value == null) return null;
        if (value.length() > BLUETOOTH_NAME_MAX)
            return "Tên Bluetooth tối đa " + BLUETOOTH_NAME_MAX + " ký tự (đang có "
                    + value.length() + ")";
        if (!value.matches("[A-Za-z0-9 ._-]+"))
            return "Tên Bluetooth chỉ gồm chữ không dấu, số, khoảng trắng và - _ .";
        return null;
    }

    /**
     * Cấu hình Bluetooth đặt lại ngay sau khi nạp cài đặt mạng gốc — bộ lệnh chủ dự án chốt
     * ngày 2026-09-14, giữ nguyên thứ tự.
     *
     * <p>Đo trên máy thật 2026-09-14: factory xong máy in KHÔNG còn Bluetooth, máy tính bảng
     * không tìm thấy để ghép lại. Khớp với tài liệu Zebra: {@code bluetooth.discoverable} mặc
     * định "off" từ Link-OS 6, và máy ZQ500 bản hai sóng (Wi-Fi + Bluetooth) xuất xưởng với
     * Bluetooth TẮT. Mọi giá trị dưới đây đã đối chiếu SGD Programming Guide.
     *
     * <p>PHẢI gửi trong cùng phiên, TRƯỚC {@code device.reset}: khởi động lại xong mà Bluetooth
     * tắt thì app không còn đường nào để nói với máy in nữa.
     */
    static final String[] BLUETOOTH_SETUP_COMMANDS = {
            // Bật sóng Bluetooth.
            "! U1 setvar \"bluetooth.enable\" \"on\"\r\n",
            // Cho máy tính bảng dò thấy để ghép đôi.
            "! U1 setvar \"bluetooth.discoverable\" \"on\"\r\n",
            // Mức 1: ghép đôi không cần mã PIN, không tự tắt discoverable như mức 3/4.
            "! U1 setvar \"bluetooth.minimum_security_mode\" \"1\"\r\n",
            // Máy in nhớ thiết bị đã ghép, không phải ghép lại sau mỗi lần tắt mở.
            "! U1 setvar \"bluetooth.bonding\" \"on\"\r\n",
            // Chỉ tác dụng với iOS; Android không bị ảnh hưởng.
            "! U1 setvar \"bluetooth.enable_reconnect\" \"iOS_only\"\r\n",
            // "both" giữ Bluetooth Classic — app nối bằng BluetoothConnection (SPP) là Classic.
            "! U1 setvar \"bluetooth.le.controller_mode\" \"both\"\r\n",
            // Chỉ áp cho kết nối Bluetooth LE; máy không có sóng LE thì bỏ qua.
            "! U1 setvar \"bluetooth.le.minimum_security\" \"none\"\r\n",
    };

    /**
     * Bộ lệnh đưa máy in về toàn bộ cài đặt gốc, theo đúng thứ tự gửi.
     *
     * <ol>
     *   <li>{@code ^JUF} — nạp lại cài đặt gốc của máy in.</li>
     *   <li>{@code ^JUS} — lưu lại. Tài liệu ^JU không nói {@code ^JUF} tự lưu; không lưu
     *       thì lệnh khởi động lại ở cuối có thể nạp lại đúng cài đặt cũ, factory thành
     *       công cốc mà không ai biết.</li>
     *   <li>{@code ^JUN} — nạp lại cài đặt mạng gốc.</li>
     *   <li>{@link #BLUETOOTH_SETUP_COMMANDS} — bật lại Bluetooth, SAU {@code ^JUN} để không
     *       bị chính nó ghi đè.</li>
     *   <li>(tuỳ chọn) đặt tên Bluetooth — cũng sau {@code ^JUN}.</li>
     *   <li>{@code device.reset} — khởi động lại. Cài đặt Bluetooth chỉ có hiệu lực sau bước này.</li>
     * </ol>
     *
     * <p>Mỗi {@code ^JU} bọc riêng một cặp {@code ^XA…^XZ} như tài liệu ^JU của Zebra
     * khuyên, tránh lỗi thời gian khi dồn nhiều lệnh ^JU vào một khối.
     */
    static List<String> factoryResetCommands(String newBluetoothName) {
        List<String> commands = new ArrayList<>();
        commands.add("^XA^JUF^XZ");
        commands.add("^XA^JUS^XZ");
        commands.add("^XA^JUN^XZ");
        commands.addAll(java.util.Arrays.asList(BLUETOOTH_SETUP_COMMANDS));
        String name = normalizeBluetoothName(newBluetoothName);
        if (name != null)
            commands.add("! U1 setvar \"bluetooth.friendly_name\" \"" + name + "\"\r\n");
        commands.add("! U1 do \"device.reset\" \"\"\r\n");
        return commands;
    }

    /**
     * Factory máy in. Máy in đang ở ZPL — hàm gọi phải kiểm bằng {@link #readIdentity}
     * trước: máy ở CPCL/line_print không hiểu ^JU và sẽ in nguyên chuỗi lệnh ra giấy.
     *
     * <p>Sau lệnh cuối máy in khởi động lại, kết nối Bluetooth đứt. Hàm gọi phải đóng kết
     * nối và {@link #forget} máy in: máy vừa factory thì mọi kết luận đã nhớ (ZPL, font,
     * giấy liên tục) đều hết đúng.
     *
     * @param newBluetoothName tên mới, null/trống thì để máy in về tên mặc định.
     * @throws IllegalArgumentException tên không hợp lệ — chưa gửi lệnh nào.
     */
    public static void factoryReset(Connection connection, String newBluetoothName)
            throws Exception {
        String problem = bluetoothNameProblem(newBluetoothName);
        if (problem != null) throw new IllegalArgumentException(problem);

        List<String> commands = factoryResetCommands(newBluetoothName);
        Logger.appendLog(LOG_TAG, "Factory máy in: gửi " + commands.size() + " lệnh"
                + (normalizeBluetoothName(newBluetoothName) == null ? ", giữ tên Bluetooth mặc định"
                : ", đặt tên Bluetooth \"" + normalizeBluetoothName(newBluetoothName) + "\""));
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            // Ngay trước khi khởi động lại: lần cuối còn hỏi được máy in trong phiên này.
            if (i == commands.size() - 1) logBluetoothReadback(connection);
            connection.write(command.getBytes(StandardCharsets.US_ASCII));
            Logger.appendLog(LOG_TAG, "Factory bước " + (i + 1) + "/" + commands.size()
                    + ": " + command.trim());
            if (i < commands.size() - 1) Thread.sleep(FACTORY_STEP_PAUSE_MS);
        }
    }

    /**
     * Ghi vào nhật ký giá trị Bluetooth máy in đang giữ, trước khi khởi động lại.
     *
     * <p>Chỉ để chẩn đoán, KHÔNG chặn gì: factory xong mà mất Bluetooth thì dòng này cho biết
     * lệnh bật đã tới máy in chưa, hay tới rồi mà bị cài đặt gốc đè lúc khởi động lại.
     */
    private static void logBluetoothReadback(Connection connection) {
        try {
            Logger.appendLog(LOG_TAG, "Factory: trước khi khởi động lại, bluetooth.enable="
                    + getVar(connection, "bluetooth.enable")
                    + ", bluetooth.discoverable=" + getVar(connection, "bluetooth.discoverable"));
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Factory: không đọc lại được cài đặt Bluetooth: "
                    + Logger.describe(ex));
        }
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
