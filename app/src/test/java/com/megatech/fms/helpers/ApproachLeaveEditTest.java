package com.megatech.fms.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.megatech.fms.model.RefuelItemData;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Sửa lại GIỜ TIẾP CẬN / GIỜ RỜI ĐI trên màn xem trước.
 *
 * <p>Chủ dự án yêu cầu 06-09-2026: nút "Rời đi" ghi mốc giờ ngay khi bấm và trước đó không có
 * đường nào sửa lại — bấm nhầm là hỏng mốc giờ của mẻ vĩnh viễn. Nay nhãn giờ rời đi thành ô
 * sửa được như các trường khác, mở hộp thoại sửa CẢ HAI mốc.
 */
public class ApproachLeaveEditTest {

    private static String source(String relative) throws IOException {
        File file = new File(relative);
        if (!file.exists()) file = new File("app/" + relative);
        if (!file.exists()) file = new File("../app/" + relative);
        assertTrue("không tìm thấy: " + relative, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Date at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.SEPTEMBER, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * Ràng buộc CỐT LÕI: hai mốc này KHÔNG nằm trong {@code Scope.PREVIEW}.
     *
     * <p>Nghĩa là lưu chúng qua {@code updateBinding()} sẽ bị patch theo scope BỎ IM LẶNG khi
     * gói đầy đủ bị chặn. Phải ghi bằng {@code patchRefuel} — nó đắp thẳng lên bản mới nhất,
     * không lọc theo scope. Nếu ngày nào đó hai khoá này được thêm vào scope thì test này đỏ,
     * và người sửa phải quyết định lại một cách có ý thức.
     */
    @Test
    public void hopThoaiSuaGioPhaiGhiBangPatchVìHaiTruongNamNgoaiScope() {
        RefuelItemData base = new RefuelItemData();
        base.setApproachTime(at(6, 9, 0));
        base.setLeaveTime(at(6, 9, 30));
        String baseJson = base.toJson();

        RefuelItemData edited = RefuelItemData.fromJson(baseJson);
        edited.setApproachTime(at(6, 8, 45));
        edited.setLeaveTime(at(6, 9, 50));

        List<String> dropped = RefuelFieldPatch.droppedKeysOutsideScope(
                RefuelFieldPatch.Scope.PREVIEW, baseJson, edited);

        assertTrue("ApproachTime nằm ngoài Scope.PREVIEW — phải ghi bằng patchRefuel",
                dropped.contains("ApproachTime"));
        assertTrue("LeaveTime nằm ngoài Scope.PREVIEW — phải ghi bằng patchRefuel",
                dropped.contains("LeaveTime"));
    }

    /** Nhãn giờ rời đi phải là ô SỬA ĐƯỢC, đúng quy ước của các trường khác trên màn này. */
    @Test
    public void nhanGioRoiDiPhaiLaOSuaDuoc() throws IOException {
        String layout = source("src/main/res/layout-land/activity_refuel_preview.xml");

        int at = layout.indexOf("@+id/lblLeaveTime");
        assertTrue("không tìm thấy nhãn giờ rời đi", at > 0);
        String block = layout.substring(Math.max(0, at - 400),
                Math.min(layout.length(), at + 700));

        assertTrue("phải có biểu tượng bút như các trường sửa được khác",
                block.contains("@drawable/ic_edit"));
        assertTrue("phải bấm được", block.contains("android:onClick=\"onClick\""));
    }

    /** Đường lưu phải dùng patchRefuel và phải ghi CẢ HAI mốc. */
    @Test
    public void duongLuuPhaiDapCaHaiMocBangPatch() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelPreviewActivity.java");

        int at = src.indexOf("private void saveApproachLeave(");
        assertTrue("không tìm thấy đường lưu hai mốc giờ", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 2000));

        assertTrue("phải ghi bằng patchRefuel", body.contains("DataHelper.patchRefuel("));
        assertTrue("phải đắp giờ tiếp cận", body.contains("latest.setApproachTime("));
        assertTrue("phải đắp giờ rời đi", body.contains("latest.setLeaveTime("));
        assertTrue("phải chạy ở luồng nền — patchRefuel đọc/ghi Room",
                body.contains("new Thread"));
        assertFalse("không được lưu qua updateBinding(): patch theo scope bỏ im lặng hai"
                + " trường này", body.contains("updateBinding("));
    }

    /**
     * Giờ ngược chỉ CẢNH BÁO, không chặn.
     *
     * <p>Người dùng đang đứng ngoài sân; một cặp giờ ngược vẫn tốt hơn là không sửa được gì.
     */
    @Test
    public void gioNguocChiCanhBaoKhongChan() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelPreviewActivity.java");

        int at = src.indexOf("private void confirmThenSaveApproachLeave(");
        assertTrue("không tìm thấy nhánh kiểm giờ ngược", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 1400));

        assertTrue("phải có nút vẫn lưu", body.contains("setPositiveButton"));
        assertTrue("phải ghi anomaly khi người dùng nhận sai có ý thức",
                body.contains("APPROACH_LEAVE_REVERSED_ACCEPTED"));
        assertTrue("cặp giờ hợp lệ thì lưu thẳng, không hỏi",
                body.contains("if (!reversed)"));
    }

    /** Mẻ của xe khác chỉ đọc — cùng ranh giới với nút Rời đi. */
    @Test
    public void meCuaXeKhacKhongSuaDuocMocGio() throws IOException {
        String src = source("src/main/java/com/megatech/fms/RefuelPreviewActivity.java");

        int at = src.indexOf("case R.id.lblLeaveTime:");
        assertTrue("không tìm thấy nhánh bấm vào nhãn giờ rời đi", at > 0);
        String body = src.substring(at, Math.min(src.length(), at + 500));

        assertTrue("phải chặn mẻ của xe khác", body.contains("isCurrentTruckItem(refuelData)"));
        assertTrue("phải mở hộp thoại sửa", body.contains("showApproachLeaveDialog()"));
    }
}
