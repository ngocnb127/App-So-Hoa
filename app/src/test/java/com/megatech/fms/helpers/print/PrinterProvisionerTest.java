package com.megatech.fms.helpers.print;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Chuẩn bị máy in: đúng ngôn ngữ ZPL và có sẵn font tiếng Việt.
 *
 * <p>Hai thứ được kiểm ở đây là hai thứ hỏng im lặng ngoài hiện trường — sai một byte
 * trong lệnh chuyển thì máy vẫn nằm ở CPCL, so tên font sai một kiểu chữ thì lần in nào
 * cũng nạp lại font mất cả phút.
 */
public class PrinterProvisionerTest {

    /**
     * Máy in CPCL phân tách lệnh bằng CR. Dùng LF hay CRLF thì lệnh không được nhận, máy
     * vẫn ở CPCL và phiếu vẫn ra giấy trắng — không có dấu hiệu nào báo.
     */
    @Test
    public void switchCommandsEndWithCarriageReturnOnly() {
        String commands = PrinterProvisioner.switchToZplCommands();

        assertFalse("không được có LF", commands.contains("\n"));
        assertEquals("đúng ba lệnh", 3, commands.split("\r", -1).length - 1);
        assertTrue(commands.endsWith("\r"));
    }

    /** Đủ và đúng thứ tự: đặt ngôn ngữ, đặt pnp, rồi mới khởi động lại. */
    @Test
    public void switchCommandsSetBothLanguagesThenReset() {
        String[] lines = PrinterProvisioner.switchToZplCommands().split("\r");

        assertEquals("! U1 setvar \"device.languages\" \"zpl\"", lines[0]);
        assertEquals("! U1 setvar \"device.pnp_option\" \"zpl\"", lines[1]);
        assertEquals("! U1 do \"device.reset\" \"\"", lines[2]);
    }

    /**
     * Danh sách file máy in trả về khác kiểu chữ hoa thường và kèm ổ đĩa. Nhận nhầm thành
     * "chưa có" thì lần in nào cũng nạp lại font mất cả phút.
     */
    @Test
    public void fontIsFoundInAListingRegardlessOfCase() {
        assertTrue(PrinterProvisioner.listingContainsFont(
                "E:OPENSANS-RE.TTF\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertTrue(PrinterProvisioner.listingContainsFont(
                "e:opensans-re.ttf".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertTrue(PrinterProvisioner.listingContainsFont(
                "E:ARIAL.TTF\r\nE:OPENSANS-RE.TTF\r\n"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    /** Danh sách rỗng hoặc không có font thì phải báo là chưa có, không đoán. */
    @Test
    public void missingFontIsReportedAsMissing() {
        assertFalse(PrinterProvisioner.listingContainsFont(
                "E:ARIAL.TTF\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertFalse(PrinterProvisioner.listingContainsFont(new byte[0]));
        assertFalse(PrinterProvisioner.listingContainsFont(null));
    }

    /**
     * Giấy cuộn trơn không có khe: chỉ chế độ liên tục mới đúng. Mọi giá trị khác — kể cả
     * không đọc được — phải dẫn tới lệnh đặt lại, vì đặt thừa vô hại còn đặt thiếu thì máy
     * in đi tìm khe không có rồi nhả hết giấy.
     */
    @Test
    public void onlyContinuousMediaIsAccepted() {
        assertTrue(PrinterProvisioner.isContinuous("continuous"));
        assertTrue(PrinterProvisioner.isContinuous("CONTINUOUS"));
        assertFalse(PrinterProvisioner.isContinuous("gap/notch"));
        assertFalse(PrinterProvisioner.isContinuous("mark"));
        assertFalse("không đọc được thì phải đặt lại, không được coi là đã đúng",
                PrinterProvisioner.isContinuous(null));
    }

    /**
     * {@code ^MNN} đặt giấy liên tục, {@code ^JUS} lưu vĩnh viễn. Thiếu {@code ^JUS} thì mỗi
     * lần thay pin máy in lại quay về mặc định và phiếu hỏng lại từ đầu.
     */
    @Test
    public void continuousMediaCommandIsPersistedZpl() {
        String commands = PrinterProvisioner.continuousMediaCommands();

        assertTrue("phải là khối ZPL hoàn chỉnh",
                commands.startsWith("^XA") && commands.endsWith("^XZ"));
        assertTrue("^MNN = giấy liên tục", commands.contains("^MNN"));
        assertTrue("^JUS = lưu vào bộ nhớ vĩnh viễn", commands.contains("^JUS"));
        assertFalse("không được kèm lệnh khởi động lại — phải in tiếp được ngay",
                commands.contains("device.reset"));
    }

    /**
     * Phản hồi SGD nằm trong dấu nháy kép kèm xuống dòng. Không bóc sạch thì so sánh ngôn
     * ngữ luôn sai và máy ZPL bị coi là máy lạ.
     */
    @Test
    public void sgdResponseIsUnquoted() {
        assertEquals("zpl", PrinterProvisioner.cleanResponse(
                "\"zpl\"\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals("ok", PrinterProvisioner.cleanResponse(
                "  ok  ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    /**
     * Máy in trả "?" khi không có biến đó. Coi "?" là giá trị hợp lệ nghĩa là in lên phiếu
     * thử một tình trạng không có thật.
     */
    @Test
    public void unknownVariableIsNotAValue() {
        assertNull(PrinterProvisioner.cleanResponse(
                "\"?\"".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertNull(PrinterProvisioner.cleanResponse(new byte[0]));
        assertNull(PrinterProvisioner.cleanResponse(null));
    }

    /**
     * line_print cũng không hiểu ZPL, phải xử lý y như CPCL — bỏ sót thì phiếu ra giấy
     * trắng mà app báo thành công.
     */
    @Test
    public void nonZplLanguagesAreAllTreatedAsNeedingTheSwitch() {
        assertTrue(PrinterProvisioner.isCpcl("cpcl"));
        assertTrue(PrinterProvisioner.isCpcl("line_print"));
        assertFalse(PrinterProvisioner.isCpcl("zpl"));
        assertFalse(PrinterProvisioner.isCpcl(null));
    }

    /** Tên trên máy in phải khớp đúng đường dẫn mà ZPL của phiếu nạp bằng ^CWZ. */
    @Test
    public void printerPathMatchesWhatTheReceiptZplReferences() {
        assertTrue(PrinterProvisioner.FONT_PRINTER_PATH.endsWith(PrinterProvisioner.FONT_NAME + ".TTF"));
    }

    /**
     * Loại giấy đọc bằng {@code ezpl.media_type} — biến tài liệu Zebra ghi, giá trị
     * "continuous" / "gap/notch" / "mark", đúng chữ "Media Type: continuous" trên nhãn cấu
     * hình. Chủ dự án chốt ngày 2026-09-14. Mặc định sau factory là "gap/notch" nên phải bị
     * coi là CHƯA liên tục.
     */
    @Test
    public void loaiGiayDocBangEzplMediaType() {
        assertEquals("ezpl.media_type", PrinterProvisioner.MEDIA_TYPE_VAR);
        assertEquals("media.type chỉ là đường lùi",
                "media.type", PrinterProvisioner.MEDIA_TYPE_VAR_FALLBACK);

        assertTrue(PrinterProvisioner.isContinuous("continuous"));
        assertFalse("mặc định sau factory phải bị đặt lại",
                PrinterProvisioner.isContinuous("gap/notch"));
        assertFalse(PrinterProvisioner.isContinuous("mark"));
    }

    // ---------------------------------------------------------------- factory

    /**
     * Factory đủ và đúng thứ tự: cài đặt gốc → LƯU → mạng gốc → khởi động lại.
     *
     * <p>Chủ dự án chốt ngày 2026-09-13: factory đưa máy in về TOÀN BỘ cài đặt gốc, gồm cả
     * mạng và Bluetooth. Thiếu ^JUS thì lệnh khởi động lại ở cuối có thể nạp lại cài đặt
     * cũ — factory "thành công" mà máy in không đổi gì.
     */
    @Test
    public void lenhFactoryDuVaDungThuTu() {
        List<String> commands = PrinterProvisioner.factoryResetCommands(null);
        int bluetooth = PrinterProvisioner.BLUETOOTH_SETUP_COMMANDS.length;

        assertEquals("không đặt tên thì: 3 lệnh ^JU + cấu hình Bluetooth + khởi động lại",
                3 + bluetooth + 1, commands.size());
        assertEquals("^XA^JUF^XZ", commands.get(0));
        assertEquals("phải LƯU ngay sau khi nạp cài đặt gốc", "^XA^JUS^XZ", commands.get(1));
        assertEquals("^XA^JUN^XZ", commands.get(2));
        assertEquals("khởi động lại phải là lệnh CUỐI",
                "! U1 do \"device.reset\" \"\"\r\n", commands.get(commands.size() - 1));
    }

    /**
     * Đo trên máy thật 2026-09-14: factory xong máy in mất Bluetooth, không ghép lại được.
     * Cấu hình Bluetooth phải nằm SAU ^JUN (không thì bị cài đặt mạng gốc đè) và TRƯỚC khởi
     * động lại — sau khởi động lại mà Bluetooth tắt thì app không còn đường nào nói với
     * máy in.
     */
    @Test
    public void batLaiBluetoothSauJunVaTruocKhoiDongLai() {
        List<String> commands = PrinterProvisioner.factoryResetCommands(null);
        int jun = commands.indexOf("^XA^JUN^XZ");
        int reset = commands.size() - 1;

        for (String setup : PrinterProvisioner.BLUETOOTH_SETUP_COMMANDS) {
            int at = commands.indexOf(setup);
            assertTrue("thiếu lệnh Bluetooth: " + setup.trim(), at >= 0);
            assertTrue("lệnh Bluetooth phải SAU ^JUN: " + setup.trim(), at > jun);
            assertTrue("lệnh Bluetooth phải TRƯỚC khởi động lại: " + setup.trim(), at < reset);
        }
    }

    /**
     * Bộ lệnh chủ dự án chốt ngày 2026-09-14, đủ và đúng giá trị. Hai dòng quyết định có
     * ghép lại được hay không là bật sóng và cho dò thấy.
     */
    @Test
    public void cauHinhBluetoothDungBoLenhDaChot() {
        assertArrayEquals(new String[]{
                "! U1 setvar \"bluetooth.enable\" \"on\"\r\n",
                "! U1 setvar \"bluetooth.discoverable\" \"on\"\r\n",
                "! U1 setvar \"bluetooth.minimum_security_mode\" \"1\"\r\n",
                "! U1 setvar \"bluetooth.bonding\" \"on\"\r\n",
                "! U1 setvar \"bluetooth.enable_reconnect\" \"iOS_only\"\r\n",
                "! U1 setvar \"bluetooth.le.controller_mode\" \"both\"\r\n",
                "! U1 setvar \"bluetooth.le.minimum_security\" \"none\"\r\n",
        }, PrinterProvisioner.BLUETOOTH_SETUP_COMMANDS);
    }

    /**
     * App nối máy in bằng BluetoothConnection — Bluetooth Classic (SPP). Đặt controller
     * về "le" là tắt Classic: máy in vẫn hiện trong danh sách nhưng app không nối được.
     */
    @Test
    public void khongDuocTatBluetoothClassic() {
        for (String command : PrinterProvisioner.BLUETOOTH_SETUP_COMMANDS)
            if (command.contains("bluetooth.le.controller_mode"))
                assertFalse("controller_mode không được là \"le\" — app dùng Bluetooth Classic",
                        command.contains("\"le\""));
    }

    /** Tài liệu ^JU của Zebra: mỗi ^JU bọc riêng một khối, dồn chung dễ lỗi thời gian. */
    @Test
    public void moiLenhJuNamTrongMotKhoiRieng() {
        for (String command : PrinterProvisioner.factoryResetCommands(null)) {
            if (!command.contains("^JU")) continue;
            assertTrue("lệnh ^JU phải mở bằng ^XA: " + command, command.startsWith("^XA"));
            assertTrue("lệnh ^JU phải đóng bằng ^XZ: " + command, command.endsWith("^XZ"));
            assertEquals("mỗi khối chỉ một ^JU: " + command,
                    1, command.split("\\^JU", -1).length - 1);
        }
    }

    /**
     * Tên Bluetooth đặt SAU ^JUN (không thì bị chính nó ghi đè) và TRƯỚC khởi động lại
     * (tên chỉ có hiệu lực sau khi máy in khởi động lại).
     */
    @Test
    public void tenBluetoothDatSauJunVaTruocKhoiDongLai() {
        List<String> commands = PrinterProvisioner.factoryResetCommands("  XE-15 ");
        String setName = "! U1 setvar \"bluetooth.friendly_name\" \"XE-15\"\r\n";

        assertEquals("có tên thì thêm đúng một lệnh",
                PrinterProvisioner.factoryResetCommands(null).size() + 1, commands.size());
        assertTrue("tên phải được cắt khoảng trắng", commands.contains(setName));
        assertTrue("đặt tên phải SAU ^JUN",
                commands.indexOf(setName) > commands.indexOf("^XA^JUN^XZ"));
        assertEquals("đặt tên phải ngay TRƯỚC khởi động lại",
                commands.size() - 2, commands.indexOf(setName));
        assertTrue(commands.get(commands.size() - 1).contains("device.reset"));
    }

    /** Để trống ô tên nghĩa là giữ tên mặc định — không được gửi một tên rỗng. */
    @Test
    public void deTrongTenThiKhongGuiLenhDatTen() {
        for (String blank : new String[]{null, "", "   "}) {
            for (String command : PrinterProvisioner.factoryResetCommands(blank))
                assertFalse("không được đặt tên khi ô tên trống: \"" + blank + "\"",
                        command.contains("friendly_name"));
            assertNull(PrinterProvisioner.normalizeBluetoothName(blank));
            assertNull("ô tên trống là hợp lệ", PrinterProvisioner.bluetoothNameProblem(blank));
        }
    }

    /** Giới hạn 17 ký tự của SGD bluetooth.friendly_name. */
    @Test
    public void tenBluetoothToiDa17KyTu() {
        assertNull(PrinterProvisioner.bluetoothNameProblem("ABCDEFGHIJKLMNOPQ"));
        assertTrue(PrinterProvisioner.bluetoothNameProblem("ABCDEFGHIJKLMNOPQR") != null);
        assertNull("khoảng trắng hai đầu không tính",
                PrinterProvisioner.bluetoothNameProblem("  ABCDEFGHIJKLMNOPQ  "));
    }

    /**
     * Dấu nháy kép làm gãy cú pháp SGD — máy in bỏ cả lệnh mà không báo; ^ và ~ là tiền tố
     * lệnh ZPL; chữ có dấu tuỳ firmware ra ký tự rác trong danh sách Bluetooth.
     */
    @Test
    public void tenBluetoothChiNhanChuKhongDauSoVaDauNoi() {
        assertNull(PrinterProvisioner.bluetoothNameProblem("XE 15_NB-01.a"));
        for (String bad : new String[]{"XE\"15", "XE^15", "XE~15", "Xe tải", "XE/15"})
            assertTrue("phải từ chối tên: " + bad,
                    PrinterProvisioner.bluetoothNameProblem(bad) != null);
    }
}
