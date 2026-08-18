package com.megatech.fms.helpers.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
