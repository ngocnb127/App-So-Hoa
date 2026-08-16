package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.helpers.ZplLayoutBuilder.Align;

import org.junit.Test;

import java.util.List;

/**
 * Kiểm tra bộ dựng ZPL — quan trọng nhất là chiều cao phải <b>chính xác</b>, không ước lượng,
 * vì đó là lý do lớp này ra đời.
 */
public class ZplLayoutBuilderTest {

    @Test
    public void chieuCaoTangDungBangSoDongThucTe() {
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(30);
        int before = b.currentY();

        String longText = "Khách hàng chịu mọi trách nhiệm nếu yêu cầu tra nạp lại lượng nhiên liệu "
                + "đã hút mà chất lượng nhiên liệu đã hút này không đạt theo quy định";
        int expectedLines = ZplLayoutBuilder.countLines(
                longText, ZplLayoutBuilder.maxCharsPerLine(30, 600));
        b.addWrappedText(longText, Align.LEFT);

        assertEquals(before + expectedLines * 30, b.currentY());
    }

    @Test
    public void moiDongLaMotTruongRieng_khongDungFBTuXuongDong() {
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(30);
        String text = "Một câu dài để chắc chắn phải xuống dòng nhiều lần trên khổ giấy 80mm "
                + "của máy in nhiệt cầm tay";
        int lines = ZplLayoutBuilder.countLines(text, ZplLayoutBuilder.maxCharsPerLine(30, 600));
        b.addWrappedText(text, Align.LEFT);

        String zpl = b.build();
        // Mỗi dòng một ^FD, và ^FB luôn khai báo 1 dòng.
        assertEquals(lines, countOccurrences(zpl, "^FD"));
        assertEquals(lines, countOccurrences(zpl, ",1,0,"));
    }

    @Test
    public void llTinhTuYCuoiCung() {
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(30);
        b.addLine("dòng 1", Align.LEFT);
        b.addLine("dòng 2", Align.LEFT);
        int y = b.currentY();

        assertTrue(b.build().contains("^LL" + (y + 200)));
    }

    @Test
    public void zq520_dayGocToaDo() {
        assertTrue(new ZplLayoutBuilder(600, true).build().contains("^LH130,0"));
        assertFalse(new ZplLayoutBuilder(600, false).build().contains("^LH130,0"));
        assertTrue(new ZplLayoutBuilder(600, false).build().contains("^LH000,0"));
    }

    @Test
    public void nhanCoFontTiengVietVaBangMaUnicode() {
        String zpl = new ZplLayoutBuilder(600, false).build();
        assertTrue(zpl.contains("^CWZ,E:OPENSANS-RE.TTF"));
        assertTrue(zpl.contains("^CI28"));
        assertTrue(zpl.startsWith("^XA"));
        assertTrue(zpl.endsWith("^XZ"));
    }

    @Test
    public void giaTriRong_inDauCham_khongInNull() {
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(25);
        b.addLabelValue("Airline", null);

        String zpl = b.build();
        assertTrue(zpl.contains("....."));
        assertFalse(zpl.contains("null"));
    }

    @Test
    public void kyTuDieuKhienZpl_duocTrungHoa() {
        assertEquals(" abc ", ZplLayoutBuilder.escape("^abc~"));
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(25);
        b.addLine("Tên ^lạ~", Align.LEFT);
        // Chỉ còn các ^ của chính lệnh ZPL, không có ^ lọt từ dữ liệu.
        assertFalse(b.build().contains("^lạ"));
    }

    @Test
    public void wrap_catTuDaiHonMotDong() {
        List<String> lines = ZplLayoutBuilder.wrap("AAAAAAAAAA", 4);
        assertEquals(3, lines.size());
        assertEquals("AAAA", lines.get(0));
        assertEquals("AA", lines.get(2));
    }

    @Test
    public void wrap_khongLamMatChu() {
        String text = "Nguyễn Văn A đại diện hãng hàng không quốc gia Việt Nam";
        StringBuilder joined = new StringBuilder();
        for (String s : ZplLayoutBuilder.wrap(text, 20)) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(s);
        }
        assertEquals(text, joined.toString());
    }

    @Test
    public void wrap_chuoiRong_van_traVeMotDong() {
        assertEquals(1, ZplLayoutBuilder.wrap(null, 10).size());
        assertEquals(1, ZplLayoutBuilder.wrap("   ", 10).size());
    }

    @Test
    public void gioiHanSoDong_catVaDanhDau() {
        ZplLayoutBuilder b = new ZplLayoutBuilder(600, false);
        b.setFont(30);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 60; i++) longText.append("chữ ");

        b.addWrappedText(longText.toString(), Align.LEFT, 2);

        assertEquals(2 * 30, b.currentY());
        assertTrue(b.build().contains("..."));
    }

    @Test
    public void num_khongInNull() {
        assertEquals(".....", ZplLayoutBuilder.num(null, 2));
        assertEquals("795.2", ZplLayoutBuilder.num(795.234d, 1));
        assertEquals("2,980", ZplLayoutBuilder.num(2980d, 0));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
