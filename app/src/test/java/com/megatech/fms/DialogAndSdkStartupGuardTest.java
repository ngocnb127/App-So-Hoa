package com.megatech.fms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canh hai họ lỗi khởi động/kết thúc màn hình, cả hai đều là VĂNG APP chứ không phải chậm máy.
 *
 * <ol>
 *   <li><b>Hộp thoại sống lâu hơn màn hình.</b> Crashlytics bản 118, 13 thiết bị:
 *       {@code IllegalArgumentException: View=DecorView[RefuelDetailActivity] not attached to
 *       window manager}. Bọc {@code try/catch} quanh {@code dismiss()} KHÔNG cứu được — sau
 *       mỗi lần bấm nút, {@code AlertController} còn tự gửi lệnh dismiss của riêng nó và lệnh
 *       đó nằm trong khung hệ thống. Cách duy nhất là đóng hộp thoại khi màn hình kết thúc.</li>
 *   <li><b>Khởi động service của SDK khi tiến trình đang ở background.</b> Crashlytics bản
 *       119: {@code BackgroundServiceStartNotAllowedException} bay ra từ
 *       {@code lcrSdk.init()} trong {@code MainActivity.onCreate}. Từ Android 12, việc này bị
 *       hệ thống từ chối, và {@code onCreate} KHÔNG bảo đảm đang ở foreground.</li>
 * </ol>
 */
public class DialogAndSdkStartupGuardTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        assertTrue("không tìm thấy: " + signature, at > 0);
        int open = src.indexOf('{', at);
        assertTrue("không tìm thấy thân: " + signature, open > at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("thân không đóng: " + signature);
    }

    /**
     * Bỏ chú thích trước khi soi mã. Không bỏ thì chính dòng ghi chú "initReader() ĐÃ CHUYỂN
     * sang onResume" cũng bị đếm là một lượt gọi — test tự vấp vào lời giải thích của mình.
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static String refuelDetail() throws IOException {
        return source("src/main/java/com/megatech/fms/RefuelDetailActivity.java");
    }

    // ---------------------------------------------------------------- hộp thoại

    /**
     * MỌI hộp thoại mở ra trong màn tra nạp phải được ghi lại. Sót một cái là sót đúng cái có
     * thể làm văng app — và cái gây sự cố trên máy thật chỉ là một nút Huỷ tầm thường.
     */
    @Test
    public void moiHopThoaiPhaiDuocGhiLai() throws IOException {
        String src = stripComments(refuelDetail());

        // Đếm, không parse. Chuỗi gọi AlertDialog.Builder trải nhiều dòng và bên trong còn
        // chứa lambda có cả ';' lẫn '{}', nên mọi cách cắt câu lệnh đều sai ở đâu đó. Bất
        // biến đếm thì không có chỗ để sai: mỗi lượt mở một Dialog phải có đúng một track().
        int shows = 0;
        Matcher m = Pattern.compile("\\.show\\(\\)\\s*\\)?\\s*;").matcher(src);
        while (m.find()) {
            int lineStart = src.lastIndexOf('\n', m.start()) + 1;
            String line = src.substring(lineStart, m.end());
            // Toast và PopupMenu không phải Dialog, không giữ cửa sổ của Activity.
            if (line.contains("Toast") || line.contains("popup")) continue;
            shows++;
        }

        // Trừ 1 cho chính khai báo hàm track().
        int tracks = -1;
        Matcher t = Pattern.compile("\\btrack\\(").matcher(src);
        while (t.find()) tracks++;

        assertTrue("có " + shows + " lượt mở Dialog nhưng chỉ " + tracks
                        + " lượt track() — sót một cái là sót đúng cái có thể làm văng app",
                tracks >= shows);
    }

    /**
     * Đóng khi màn hình KẾT THÚC, không phải mọi lần tạm dừng. Đóng ở mọi lần tạm dừng là làm
     * mất số đồng hồ người dùng vừa gõ tay khi màn hình tắt — đổi một lỗi văng lấy một lỗi
     * mất dữ liệu thì không phải là sửa.
     */
    @Test
    public void ketThucManHinhPhaiDongHopThoai() throws IOException {
        String src = refuelDetail();

        String onPause = stripComments(body(src, "protected void onPause()"));
        assertTrue("onPause phải đóng hộp thoại khi màn hình đang kết thúc",
                onPause.contains("dismissOpenDialogs()"));
        assertTrue("chỉ đóng khi isFinishing() — tạm dừng thường phải giữ nguyên hộp nhập tay",
                onPause.contains("isFinishing()"));

        String onDestroy = stripComments(body(src, "protected void onDestroy()"));
        assertTrue("onDestroy phải đóng hộp thoại làm lưới cuối",
                onDestroy.contains("dismissOpenDialogs()"));
    }

    // ---------------------------------------------------------------- SDK đồng hồ

    /**
     * {@code lcrSdk.init()} khởi động service NGAY và ĐỒNG BỘ, dù chữ ký của nó trông như bất
     * đồng bộ. Không bọc thì exception bay thẳng ra {@code onCreate} và giết tiến trình.
     */
    @Test
    public void khoiTaoSdkDongHoPhaiDuocBoc() throws IOException {
        String src = source("src/main/java/com/megatech/fms/helpers/LCRReader.java");
        String init = stripComments(body(src, "private void init()"));

        assertTrue("init() phải bắt lỗi khởi tạo SDK, không để nó bay ra ngoài",
                init.contains("catch"));
        assertFalse("init() không được gọi thẳng lcrSdk.init() ngoài khối try",
                init.replaceAll("(?s)try\\s*\\{.*", "").contains("lcrSdk.init("));
    }

    /**
     * Dựng kết nối đồng hồ ở {@code onResume}, không phải {@code onCreate}: chỉ onResume mới
     * bảo đảm tiến trình đang ở foreground, tức là được phép khởi động service.
     */
    @Test
    public void manHinhChinhDungSdkODungChoForeground() throws IOException {
        String src = source("src/main/java/com/megatech/fms/MainActivity.java");

        String onCreate = stripComments(body(src, "protected void onCreate(Bundle savedInstanceState)"));
        assertFalse("onCreate KHÔNG được gọi initReader() — onCreate không bảo đảm foreground",
                onCreate.contains("initReader()"));

        String onResume = stripComments(body(src, "protected void onResume()"));
        assertTrue("onResume phải là nơi dựng kết nối đồng hồ",
                onResume.contains("initReader()"));
        assertTrue("chỉ dựng một lần cho mỗi lần sống của màn hình",
                onResume.contains("readerInitialized"));
    }
}
