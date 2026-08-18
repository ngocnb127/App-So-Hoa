package com.megatech.fms.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * In đậm bằng cách đắp chồng trường, lệch 1 dot.
 *
 * <p>ZPL không có thuộc tính đậm cho font TrueType và máy in chỉ nạp sẵn bản nét thường,
 * nên đây là đường duy nhất làm dày nét mà KHÔNG đụng tới toạ độ hay cỡ chữ — bố cục phiếu
 * phải giữ nguyên từng dot.
 */
public class ReceiptThermalBoldTest {

    @Test
    public void everyTextFieldIsPrintedTwiceOneDotApart() {
        String out = ReceiptModel.emboldenFields("^FO250,300^FB320,1,0,R,0^FDVN-A321^FS");

        assertEquals("^FO250,300^FB320,1,0,R,0^FDVN-A321^FS"
                + "^FO251,300^FB320,1,0,R,0^FDVN-A321^FS", out);
    }

    /** Toạ độ y và mọi tham số khác phải giữ nguyên — chỉ x dịch đúng 1. */
    @Test
    public void onlyTheHorizontalOriginMoves() {
        String out = ReceiptModel.emboldenFields("^FO0,140^FB600,1,0,C,0^FDTOTAL^FS");

        assertTrue(out.contains("^FO0,140^FB600,1,0,C,0^FDTOTAL^FS"));
        assertTrue(out.contains("^FO1,140^FB600,1,0,C,0^FDTOTAL^FS"));
    }

    /**
     * Ảnh chữ ký không được đắp chồng: bitmap in lệch 1 dot chỉ nhoè thêm chứ không đậm.
     */
    @Test
    public void signatureImageIsNotOverstruck() {
        String out = ReceiptModel.emboldenFields("^FO150,900^XGE:BUYER.GRF,1,1^FS");

        assertEquals("^FO150,900^XGE:BUYER.GRF,1,1^FS", out);
        assertFalse(out.contains("^FO151"));
    }

    /** Phần ngoài trường (lệnh font, ^XA/^XZ, header chất lượng in) không bị nhân đôi. */
    @Test
    public void commandsOutsideFieldsAreLeftAlone() {
        String out = ReceiptModel.emboldenFields(
                "^XA^JMA\n^MD14\n^CFZ,30\n^FO0,10^FDA^FS^CFZ,40\n^XZ");

        assertEquals(1, countOf(out, "^XA"));
        assertEquals(1, countOf(out, "^JMA"));
        assertEquals(1, countOf(out, "^MD14"));
        assertEquals(1, countOf(out, "^XZ"));
        assertEquals(2, countOf(out, "^CFZ,"));   // vẫn đúng 2 lệnh font như đầu vào
        assertEquals(2, countOf(out, "^FD"));     // trường được đắp thêm một lần
    }

    /** Đường kẻ ^GB được đắp: dày thêm 1 dot là đúng mong muốn. */
    @Test
    public void rulesAreThickenedToo() {
        String out = ReceiptModel.emboldenFields("^FO0,500^GB700,1,3^FS");

        assertEquals(2, countOf(out, "^GB700,1,3"));
    }

    /**
     * Toạ độ hỏng thì bỏ qua trường đó, không đoán và không ném lỗi — một phiếu in thiếu
     * đậm vẫn đọc được, còn một ngoại lệ giữa lúc in thì mất cả phiếu.
     */
    @Test
    public void malformedOriginIsSkippedInsteadOfCrashing() {
        String out = ReceiptModel.emboldenFields("^FOxx,10^FDA^FS");

        assertEquals("^FOxx,10^FDA^FS", out);
    }

    @Test
    public void emptyInputIsReturnedUnchanged() {
        assertEquals("", ReceiptModel.emboldenFields(""));
        assertEquals(null, ReceiptModel.emboldenFields(null));
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) count++;
        return count;
    }
}
