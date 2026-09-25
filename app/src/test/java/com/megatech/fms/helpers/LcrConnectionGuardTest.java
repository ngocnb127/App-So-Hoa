package com.megatech.fms.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Canh cách app kết nối và lấy dữ liệu từ đồng hồ LCR, đối chiếu với demo chính hãng của SDK
 * ({@code SDK-IQ-SampleApp}).
 *
 * <p>Chủ dự án chốt ngày 2026-09-14, sau khi đo mã SDK: mọi {@code LcrSdk} dùng chung MỘT
 * service, thiết bị {@code "LCR.iQ"} là của chung, và {@code removeAllListeners} xoá listener
 * của cả service. Mỗi lỗi dưới đây từng làm đồng hồ "mất kết nối" hoặc đứng số giữa mẻ mà
 * không để lại dấu vết nào — nên canh thẳng trên mã nguồn.
 */
public class LcrConnectionGuardTest {

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

    /** Bỏ chú thích: chính lời giải thích "đừng gọi X" không được tính là một lượt gọi X. */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static String reader() throws IOException {
        return source("src/main/java/com/megatech/fms/helpers/LCRReader.java");
    }

    private static List<File> mainJavaFiles() {
        File root = new File("src/main/java/com/megatech/fms");
        if (!root.exists()) root = new File("app/src/main/java/com/megatech/fms");
        if (!root.exists()) root = new File("../app/src/main/java/com/megatech/fms");
        assertTrue("không tìm thấy thư mục mã nguồn", root.exists());
        List<File> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    // ------------------------------------------------------------ một SDK duy nhất

    /**
     * Không màn hình nào được tự dựng SDK đồng hồ. Màn Cài đặt từng {@code new LCRReader}
     * rồi {@code destroy()} — gỡ luôn thiết bị và listener mà màn tra nạp đang dùng.
     */
    @Test
    public void khongManHinhNaoDuocTuDungSdkDongHo() throws IOException {
        for (File f : mainJavaFiles()) {
            if (f.getName().equals("LCRReader.java")) continue;
            String src = stripComments(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            assertFalse(f.getName() + " không được tự dựng LCRReader — dùng LCRReader.create/shared",
                    src.contains("new LCRReader("));
        }
    }

    /** Không màn hình nào được huỷ SDK dùng chung. */
    @Test
    public void khongManHinhNaoDuocHuySdkDungChung() throws IOException {
        for (File f : mainJavaFiles()) {
            if (f.getName().equals("LCRReader.java")) continue;
            String src = stripComments(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            assertFalse(f.getName() + " không được gọi destroy() trên đồng hồ dùng chung",
                    src.matches("(?s).*[rR]eader\\s*\\.\\s*destroy\\s*\\(.*"));
        }
    }

    /**
     * {@code create(..., renew = true)} không còn dựng SDK mới; chỉ đổi địa chỉ đồng hồ mới
     * dựng lại, và phải huỷ cái cũ TRƯỚC.
     */
    @Test
    public void createKhongDungThemSdk() throws IOException {
        String src = stripComments(reader());
        assertFalse("create() không được tự new LCRReader",
                body(src, "public static LCRReader create(").contains("new LCRReader("));

        String obtain = body(src, "private static synchronized LCRReader obtain(");
        int destroy = obtain.indexOf("destroy()");
        int create = obtain.indexOf("new LCRReader(");
        assertTrue("obtain() phải huỷ SDK cũ khi đổi địa chỉ", destroy > 0);
        assertTrue("obtain() phải huỷ SDK cũ TRƯỚC khi dựng cái mới", destroy < create);
    }

    // ------------------------------------------------------------ đăng ký trường

    /**
     * Mỗi trường đăng ký độc lập, như demo chính hãng. Hàng đợi nối tiếp cũ chờ
     * {@code onFieldDataRequestAddSuccess} mới gửi trường sau: một trường lỗi là kẹt cả hàng —
     * đúng ca SALENUMBER trống suốt mẻ.
     */
    @Test
    public void dangKyTruongKhongDuocKetVoiTruongLoi() throws IOException {
        String src = stripComments(reader());

        assertFalse("thành công của một trường không được là điều kiện để gửi trường khác",
                body(src, "public void onFieldDataRequestAddSuccess(").contains("processFieldQueue"));

        String drain = body(src, "private void drainPendingFields()");
        assertTrue("phải lấy hết các trường đang chờ trong một lượt",
                drain.contains("pendingFields.clear()"));
        assertTrue("phải gửi từng trường đang chờ", drain.contains("for ("));

        assertTrue("trường đăng ký lỗi phải được xếp lại cho lần nối sau",
                body(src, "public void onFieldDataRequestAddFailed(").contains("pendingFields.add("));
    }

    /**
     * Callback SDK chạy trên luồng giao diện; màn hình gọi {@code requestData()} từ cả luồng
     * Timer lẫn luồng nền. Mọi thay đổi tập trường đang chờ phải về luồng giao diện.
     */
    @Test
    public void dangKyTruongChayTrenLuongGiaoDien() throws IOException {
        String src = stripComments(reader());
        assertTrue("addRequestQueue phải chạy trên luồng giao diện",
                body(src, "private void addRequestQueue(").contains("onMainThread("));
        assertTrue("processFieldQueue phải chạy trên luồng giao diện",
                body(src, "private void processFieldQueue()").contains("onMainThread("));
    }

    // ------------------------------------------------------------ báo trạng thái

    /**
     * Lỗi truyền thông nhất thời và mạng rớt là việc SDK tự thử lại; chỉ ghi vết, như demo
     * chính hãng. Bật đỏ ở đây là dấu kết nối nhấp nháy trong khi thiết bị chưa rời mạng.
     */
    @Test
    public void loiNhatThoiKhongBaoMatKetNoi() throws IOException {
        String src = stripComments(reader());
        for (String signature : new String[]{
                "public void onCommunicationStatusChanged(",
                "public void deviceNetworkStateChanged(",
                "public void onNetworkError("}) {
            String b = body(src, signature);
            assertFalse(signature + " không được báo lỗi ra màn hình", b.contains("onError("));
            assertFalse(signature + " không được giả lập nối lại", b.contains("onConnected("));
        }
    }

    /**
     * Nối xong phải lấy lại trạng thái thiết bị SDK đang giữ (như demo chính hãng), nhưng
     * KHÔNG phát lại công tắc: listener công tắc tự gửi RUN, phát lại là tự mở mẻ mới.
     */
    @Test
    public void noiLaiPhaiLamMoiTrangThaiNhungKhongPhatLaiCongTac() throws IOException {
        String src = stripComments(reader());
        assertTrue("deviceOnConnect phải làm mới trạng thái thiết bị",
                body(src, "public void deviceOnConnect(").contains("refreshStatusListeners()"));

        String refresh = body(src, "private void refreshStatusListeners()");
        assertTrue("phải gắn lại deviceStatusListener", refresh.contains("addListener(deviceStatusListener)"));
        assertFalse("KHÔNG được phát lại switchStateListener", refresh.contains("switchStateListener"));
    }

    /** Chuỗi sự kiện dẫn tới lỗi truyền thông là dấu vết duy nhất nói được vì sao. */
    @Test
    public void loiTruyenThongPhaiGhiChuoiSuKien() throws IOException {
        String b = stripComments(body(reader(), "public void onCommunicationStatusError("));
        assertTrue("phải đọc chuỗi sự kiện của lỗi", b.contains("getEvents()"));
        assertTrue("phải ghi thẳng vào nhật ký", b.contains("logConnection("));
    }

    // ------------------------------------------------------------ gỡ listener

    /**
     * Màn in lấy số xong KHÔNG ngắt đồng hồ, và chỉ gỡ listener của chính nó. onDestroy của nó
     * chạy muộn — ngắt hay gán null ở đó là phá màn tra nạp kế tiếp.
     */
    @Test
    public void manInKhongNgatDongHoDungChung() throws IOException {
        String src = stripComments(source("src/main/java/com/megatech/fms/PrintReceiptActivity.java"));
        String lcrBranch = body(body(src, "private synchronized void disconnectDeviceOnce()"),
                "if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.LCR)");

        assertFalse("màn in không được ngắt đồng hồ dùng chung", lcrBranch.contains("doDisconnectDevice"));
        assertFalse("màn in không được gán null listener", lcrBranch.contains("Listener(null)"));
        assertTrue("màn in phải gỡ đúng listener của mình", lcrBranch.contains("removeListeners("));
    }

    /** Màn tra nạp cũng chỉ gỡ listener của mình. */
    @Test
    public void manTraNapChiGoListenerCuaMinh() throws IOException {
        String src = stripComments(source("src/main/java/com/megatech/fms/RefuelDetailActivity.java"));
        String clear = body(src, "private void clearListeners()");
        assertFalse("không được gán null listener", clear.contains("Listener(null)"));
        assertTrue("phải gỡ đúng listener của màn này", clear.contains("removeListeners("));
    }

    /**
     * Nút Kiểm tra ở màn Cài đặt MƯỢN listener của đồng hồ dùng chung — màn tra nạp có thể
     * đang nằm bên dưới — nên phải trả lại, kể cả khi hết hạn chờ hay rời màn hình.
     */
    @Test
    public void kiemTraIpPhaiTraLaiListener() throws IOException {
        String src = stripComments(source("src/main/java/com/megatech/fms/SettingActivity.java"));
        assertTrue("phải dùng đồng hồ dùng chung", src.contains("LCRReader.shared("));
        assertTrue("kết thúc kiểm tra phải trả listener",
                body(src, "private void finishLcrTest(").contains("restoreListeners("));
        assertTrue("rời màn hình phải trả listener",
                body(src, "protected void onDestroy()").contains("finishLcrTest("));
    }
}
