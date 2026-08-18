package com.megatech.fms.helpers.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
     * Máy in trả tên file theo nhiều dạng. Nhận nhầm thành "chưa có" thì lần in nào cũng
     * nạp lại font, mỗi lần mất khoảng một phút qua Bluetooth.
     */
    @Test
    public void fontIsRecognisedRegardlessOfDriveAndCase() {
        assertTrue(PrinterProvisioner.matchesFont("E:OPENSANS-RE.TTF"));
        assertTrue(PrinterProvisioner.matchesFont("OPENSANS-RE.TTF"));
        assertTrue(PrinterProvisioner.matchesFont("OpenSans-Re.ttf"));
        assertTrue(PrinterProvisioner.matchesFont("  E:OpenSans-Re.ttf  "));
    }

    /**
     * Chỉ khớp ĐÚNG tên font phiếu tham chiếu. Nhận nhầm một font khác nghĩa là bỏ qua
     * bước nạp, rồi mọi chữ có dấu in ra ô vuông.
     */
    @Test
    public void otherFontsAreNotMistakenForIt() {
        assertFalse(PrinterProvisioner.matchesFont("E:OPENSANS-BO.TTF"));
        assertFalse(PrinterProvisioner.matchesFont("E:ARIAL.TTF"));
        assertFalse(PrinterProvisioner.matchesFont("E:OPENSANS-RE.TTF.BAK"));
        assertFalse(PrinterProvisioner.matchesFont(""));
        assertFalse(PrinterProvisioner.matchesFont(null));
    }

    /** Tên trên máy in phải khớp đúng đường dẫn mà ZPL của phiếu nạp bằng ^CWZ. */
    @Test
    public void printerPathMatchesWhatTheReceiptZplReferences() {
        assertTrue(PrinterProvisioner.FONT_PRINTER_PATH.endsWith(PrinterProvisioner.FONT_FILE_NAME));
        assertTrue(PrinterProvisioner.matchesFont(PrinterProvisioner.FONT_PRINTER_PATH));
    }
}
