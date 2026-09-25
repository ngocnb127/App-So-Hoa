package com.megatech.fms;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Canh một HỌ LỖI, không phải một lỗi: đọc/ghi Room trên luồng giao diện.
 *
 * <p>Room chặn cứng bằng {@code assertNotMainThread()} — vi phạm là VĂNG APP, không phải
 * chậm máy. Chốt đó là của Room, nên {@code StrictMode.permitAll()} ở {@code BaseActivity}
 * KHÔNG che được. Đây là cái bẫy đã cắn dự án này ít nhất ba lần:
 *
 * <ul>
 *   <li>17-08-2026 — bảo trì sau nâng cấp: "Cannot access database on the main thread", nghĩa
 *       là backfill CHƯA TỪNG chạy trên bất kỳ xe nào (xem {@code DataHelper.runUpgradeMaintenance}).</li>
 *   <li>06-09-2026 — bấm Xác nhận ở màn xác nhận mẻ: bốn lần văng liên tiếp trên xe thật,
 *       KHÔNG AI chốt được mẻ nào (xem {@code RefuelConfirmScreenGuardTest}).</li>
 *   <li>06-09-2026 — menu "Gửi nhật ký": văng ngay khi bấm. Nặng hơn vẻ ngoài của nó, vì đây
 *       đúng là nút người dùng bấm SAU khi gặp sự cố để gửi nhật ký về.</li>
 * </ul>
 *
 * <p>Khuôn đúng: bọc trong {@code new Thread(...)} hoặc executor, rồi trả kết quả về giao
 * diện bằng {@code runOnUiThread}. Thấy một lượt gọi chạm Room nằm thẳng trong {@code onClick},
 * {@code onCreate}, {@code onResume} hay {@code onOptionsItemSelected} là sai, không cần đo.
 */
public class MainThreadRoomGuardTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy mã nguồn: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Đoạn mã sau {@code anchor} phải mở luồng nền trước khi chạm tới {@code roomCall}. */
    private static void assertRunsOffMainThread(String src, String anchor, String roomCall,
                                                String why) {
        int at = src.indexOf(anchor);
        assertTrue("không tìm thấy mốc: " + anchor, at > 0);

        int call = src.indexOf(roomCall, at);
        assertTrue("không tìm thấy lượt gọi: " + roomCall, call > at);

        String between = src.substring(at, call);
        assertTrue(why + " — phải bọc trong luồng nền, đoạn từ \"" + anchor
                        + "\" tới \"" + roomCall + "\" không mở luồng nào",
                between.contains("new Thread")
                        || between.contains("doInBackground")
                        || between.contains("Executor")
                        || between.contains("xecutor.execute"));
    }

    /** Menu "Gửi nhật ký" đọc bảng nhật ký trong Room. */
    @Test
    public void guiNhatKyPhaiChayONen() throws IOException {
        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/UserBaseActivity.java"),
                "case R.id.action_send_log:",
                "Logger.sendLog()",
                "sendLog() đọc bảng nhật ký trong Room");
    }

    /** Bảo trì sau nâng cấp chạy backfill trên Room. */
    @Test
    public void baoTriSauNangCapPhaiChayONen() throws IOException {
        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/helpers/DataHelper.java"),
                "public static void runUpgradeMaintenance(",
                "runUpgradeMaintenanceBlocking(",
                "backfill sau nâng cấp chạy trên Room");
    }

    /**
     * Chặn tiếp cận đọc toàn bộ danh sách phiếu trong Room.
     *
     * <p>Cả hai nơi hỏi đều phải ra nền: màn tra nạp và thẻ phiếu ở màn danh sách.
     */
    @Test
    public void chanTiepCanPhaiChayONen() throws IOException {
        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/RefuelDetailActivity.java"),
                "private void checkApproachAllowed(",
                "RefuelApproachGuard.findBlocking(",
                "findBlocking() đọc danh sách phiếu trong Room");

        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/view/RefuelRecyclerViewAdapter.java"),
                "binding.btnApproach.setOnClickListener(",
                "RefuelApproachGuard.findBlocking(",
                "findBlocking() đọc danh sách phiếu trong Room");
    }

    /** Ghi giờ rời đi là một lượt patch xuống Room. */
    @Test
    public void ghiGioRoiDiPhaiChayONen() throws IOException {
        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/RefuelDetailActivity.java"),
                "private void leaveBlockingAndContinue(",
                "RefuelApproachGuard.leaveNow(",
                "leaveNow() ghi patch xuống Room");

        assertRunsOffMainThread(
                source("src/main/java/com/megatech/fms/view/RefuelRecyclerViewAdapter.java"),
                "private void leaveBlockingAndApproach(",
                "RefuelApproachGuard.leaveNow(",
                "leaveNow() ghi patch xuống Room");
    }

    /** Patch metadata chứng từ lên mọi phiếu đang in — mỗi phiếu một lượt đọc/ghi Room. */
    @Test
    public void patchChungTuPhaiChayONen() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelPreviewActivity.java");
        int at = 0;
        int found = 0;
        while (true) {
            int call = src.indexOf("patchAllPrintItems(\"", at);
            if (call < 0) break;
            found++;
            // Lượt GỌI (không phải chỗ khai báo hàm) phải nằm ngay sau một new Thread.
            String before = src.substring(Math.max(0, call - 200), call);
            assertTrue("patchAllPrintItems() đọc/ghi Room cho từng phiếu nên PHẢI gọi từ"
                            + " luồng nền", before.contains("new Thread"));
            at = call + 1;
        }
        assertTrue("không tìm thấy lượt gọi patchAllPrintItems nào", found >= 2);
    }
}
