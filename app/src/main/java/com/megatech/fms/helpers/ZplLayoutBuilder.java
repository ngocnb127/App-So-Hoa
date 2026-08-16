package com.megatech.fms.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dựng nhãn ZPL theo dòng, tự cộng toạ độ Y.
 *
 * <p>Ra đời để thay cách cộng {@code height} thủ công rải rác trong {@code ReceiptModel} —
 * cách đó vừa dùng {@code ^FB} cho máy in tự xuống dòng, vừa đoán số dòng theo độ dài chuỗi,
 * nên bản in dài dễ bị đè chữ.
 *
 * <p>Nguyên tắc ở đây:
 * <ul>
 *   <li><b>Wrap chủ động ở client</b>: mỗi dòng sau khi wrap là một trường {@code ^FD} riêng,
 *       {@code ^FB} luôn đặt số dòng = 1. Nhờ vậy chiều cao là <b>chính xác</b>, không phải ước lượng.</li>
 *   <li>{@code ^LL} sinh từ {@link #currentY()} cuối cùng.</li>
 *   <li>Nội dung tự do có thể giới hạn số dòng — xem {@link #addWrappedText(String, int)}.</li>
 * </ul>
 *
 * <p>Lớp thuần Java để chạy được golden test trên JVM.
 */
public final class ZplLayoutBuilder {

    /** Bề rộng vùng in mặc định (dots) — khổ giấy 80mm ở 203dpi. */
    public static final int DEFAULT_PRINT_WIDTH = 600;

    /** Bề rộng cột nhãn khi in cặp nhãn : giá trị. */
    private static final int LABEL_WIDTH = 250;

    /**
     * Tỉ lệ bề rộng ký tự trung bình so với chiều cao font, dùng để wrap.
     * Chỉ ảnh hưởng thẩm mỹ (dòng dài/ngắn), không ảnh hưởng độ chính xác chiều cao,
     * vì mỗi dòng đã wrap được in bằng một trường riêng.
     */
    private static final double CHAR_WIDTH_RATIO = 0.55d;

    public enum Align {
        LEFT("L"),
        CENTER("C"),
        RIGHT("R");

        private final String code;

        Align(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final int printWidth;
    private final String leftHome;
    private final StringBuilder body = new StringBuilder();

    private int y;
    private int fontSize = 30;

    public ZplLayoutBuilder(int printWidth, boolean zq520) {
        this.printWidth = printWidth > 0 ? printWidth : DEFAULT_PRINT_WIDTH;
        // ZQ520 in lệch sang trái so với ZQ511 nên cần đẩy gốc toạ độ.
        this.leftHome = zq520 ? "^LH130,0" : "^LH000,0";
    }

    public int currentY() {
        return y;
    }

    public int getFontSize() {
        return fontSize;
    }

    /** Đổi cỡ chữ cho các dòng tiếp theo. Chiều cao dòng bằng cỡ chữ. */
    public ZplLayoutBuilder setFont(int size) {
        this.fontSize = size;
        body.append("^CFZ,").append(size).append("\n");
        return this;
    }

    /** Chừa khoảng trắng theo chiều dọc. */
    public ZplLayoutBuilder addSpace(int dots) {
        y += dots;
        return this;
    }

    /** Một dòng đơn, không wrap — dùng cho giá trị chắc chắn ngắn. */
    public ZplLayoutBuilder addLine(String text, Align align) {
        field(0, printWidth, align, text);
        y += fontSize;
        return this;
    }

    /** Văn bản dài: wrap ở client rồi in từng dòng một. */
    public ZplLayoutBuilder addWrappedText(String text, Align align) {
        return addWrappedText(text, align, Integer.MAX_VALUE);
    }

    /**
     * Như trên nhưng chặn số dòng tối đa. Phần vượt bị cắt và đánh dấu bằng "..." —
     * tầng gọi phải kiểm tra trước bằng {@link #countLines(String, int)} và báo người dùng
     * TRƯỚC khi ký/in, chứ không để bản in âm thầm mất chữ.
     */
    public ZplLayoutBuilder addWrappedText(String text, Align align, int maxLines) {
        List<String> lines = wrap(text, maxCharsPerLine(fontSize, printWidth));
        if (lines.size() > maxLines && maxLines > 0) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, lines.get(maxLines - 1) + "...");
        }
        for (String line : lines) {
            field(0, printWidth, align, line);
            y += fontSize;
        }
        return this;
    }

    /**
     * Cặp "nhãn : giá trị" — nhãn trái, giá trị phải, giống bố cục các phiếu hiện hành.
     * Giá trị rỗng in thành dấu chấm để bản giấy không có ô trống vô nghĩa.
     */
    public ZplLayoutBuilder addLabelValue(String label, String value) {
        String shown = isBlank(value) ? "....." : value;
        int valueWidth = printWidth - LABEL_WIDTH - 20;

        List<String> valueLines = wrap(shown, maxCharsPerLine(fontSize, valueWidth));
        int startY = y;

        field(0, LABEL_WIDTH, Align.LEFT, label);
        fieldAt(LABEL_WIDTH - 10, startY, 10, Align.CENTER, ":");

        for (String line : valueLines) {
            fieldAt(LABEL_WIDTH, y, valueWidth, Align.RIGHT, line);
            y += fontSize;
        }
        return this;
    }

    /** Đường kẻ ngang. */
    public ZplLayoutBuilder addDivider() {
        body.append("^FO0,").append(y).append("^GB").append(printWidth + 100).append(",1,3^FS\n");
        y += 10;
        return this;
    }

    /**
     * In ảnh chữ ký đã nạp sẵn vào máy in bằng {@code ZebraPrinter.storeImage(...)}.
     *
     * @param grfName tên file trên máy in, ví dụ {@code E:BUYER.GRF}
     * @param height  chiều cao dành cho ảnh (dots)
     */
    public ZplLayoutBuilder addSignatureImage(String grfName, int height) {
        body.append("^FO150,").append(y).append("^XG").append(grfName).append(",1,1^FS\n");
        y += height;
        return this;
    }

    /** Sinh nhãn hoàn chỉnh; {@code ^LL} tính từ Y cuối cùng. */
    public String build() {
        StringBuilder out = new StringBuilder();
        out.append("^XA");
        out.append("^LL").append(y + 200).append("\n");
        out.append("^CWZ,E:OPENSANS-RE.TTF^FS\n");
        out.append(leftHome).append("\n");
        out.append("^CI28\n");
        out.append(body);
        out.append("^PQ1\n");
        out.append("^LH0,0\n");
        out.append("^XZ");
        return out.toString();
    }

    // -------------------------------------------------------------------- nội bộ

    private void field(int x, int width, Align align, String text) {
        fieldAt(x, y, width, align, text);
    }

    private void fieldAt(int x, int atY, int width, Align align, String text) {
        body.append("^FO").append(x).append(',').append(atY)
                .append("^FB").append(width).append(",1,0,").append(align.getCode()).append(",0")
                .append("^FD").append(escape(text)).append("^FS\n");
    }

    /**
     * ZPL dùng {@code ^} và {@code ~} làm ký tự điều khiển. Dữ liệu nghiệp vụ không có
     * hai ký tự này, nhưng nếu lọt vào thì phải trung hoà để không vỡ nhãn.
     */
    static String escape(String text) {
        if (text == null) return "";
        return text.replace("^", " ").replace("~", " ");
    }

    /** Số ký tự tối đa một dòng ở cỡ chữ và bề rộng cho trước. */
    public static int maxCharsPerLine(int fontSize, int widthDots) {
        int n = (int) Math.floor(widthDots / (fontSize * CHAR_WIDTH_RATIO));
        return Math.max(n, 1);
    }

    /** Đếm số dòng sau khi wrap — dùng để cảnh báo trước khi ký/in. */
    public static int countLines(String text, int maxChars) {
        return wrap(text, maxChars).size();
    }

    /** Wrap theo từ; từ dài hơn một dòng thì cắt cứng. */
    static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            while (word.length() > maxChars) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, maxChars));
                word = word.substring(maxChars);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Tiện ích định dạng số cho bản in, tránh in ra "null". */
    public static String num(Double value, int decimals) {
        if (value == null) return ".....";
        return String.format(Locale.US, "%,." + decimals + "f", value);
    }
}
