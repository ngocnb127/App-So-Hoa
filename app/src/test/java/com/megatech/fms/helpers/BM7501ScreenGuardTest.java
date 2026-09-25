package com.megatech.fms.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Canh màn nhập BM 75.01 bằng cách đọc thẳng layout.
 *
 * <p>Ràng buộc nghiệp vụ ở đây là "màn hình phải trùng biểu mẫu giấy": đủ ô của biểu mẫu và
 * KHÔNG có ô nào ngoài biểu mẫu. Không tách ra hàm thuần được nên canh thô trên XML — thêm ô
 * mới vào màn này thì phải đối chiếu biểu mẫu rồi sửa test cho đúng ý nghĩa mới.
 */
public class BM7501ScreenGuardTest {

    private static final String LAYOUT = "src/main/res/layout/activity_b7501.xml";

    /** Mọi ô nhập của biểu mẫu giấy, theo thứ tự A → B → C. */
    private static final String[] REQUIRED_FIELDS = {
            "f_customerRepName", "f_customerTitle", "f_customerTel", "f_customerFax",
            "f_aircraftType", "f_aircraftReg",
            "f_reason", "f_reasonOther", "f_tankDrain",
            "f_custMicroKit", "f_custMicroKitOther", "f_custMicroResult",
            "f_add_fsii", "f_add_biocide", "f_add_aquarius", "f_add_none", "f_add_undet",
            "f_prevLocation1", "f_prevGrade1", "f_prevLocation2", "f_prevGrade2",
            "f_vac", "f_cwd", "f_densityKgM3", "f_conductivityPsM",
            "f_skypecMicroKit", "f_skypecMicroResult",
            "f_defuellerTruckNo", "f_startTime", "f_endTime", "f_method",
            "f_signalThumb", "f_signalCross", "f_otherSignal",
            "f_expectedKg", "f_actualKg", "f_actualTempC", "f_actualDensityKgM3",
            "f_gallon", "f_liter",
            "f_refuellable", "f_handling", "f_storageFrom", "f_storageTo", "f_handlingNote",
            "f_skypecRepName",
    };

    /**
     * Những ô đã bỏ vì biểu mẫu giấy không có. Giữ danh sách để không ai vô tình dựng lại:
     * ô "hãng đã KT vi sinh chưa" (suy từ thiết bị/kết quả), "nghi ngờ nhiễm vi sinh",
     * "khách hàng yêu cầu KT vi sinh", "lý do phải KT vi sinh", cờ "có yêu cầu đo độ dẫn điện",
     * chữ ký mục A và nút "Ký & hoàn tất" (chốt 2026-09-23).
     */
    private static final String[] FORBIDDEN_FIELDS = {
            "f_custMicro\"", "f_contaminationSuspected", "f_customerRequestedMicrobial",
            "f_microbialReason", "f_conductivityRequired",
            // Chữ ký mục A: biểu mẫu có ô "Name & signature" nhưng thực tế hãng không ký ở đó.
            "btnSignSectionA", "lbl_sign_sectionA",
            // Phiếu không còn bị khoá sau khi in nên không có bước "Ký & hoàn tất".
            "btnSign7501",
            // Họ tên đại diện hãng chỉ khai một lần ở mục A, khối ký không hỏi lại.
            "f_customerRepFinalName",
    };

    /**
     * Ba nút của màn: Lưu, Xuất phiếu (chốt sổ, khoá nội dung), In. Mất nút Xuất là mất hẳn
     * cách chốt phiếu để hệ thống đẩy sang Omega.
     */
    @Test
    public void manNhapCoDuBaNutThaoTac() throws IOException {
        String xml = layout();
        for (String id : new String[]{"btnSave7501", "btnExport7501", "btnPrint7501"}) {
            assertTrue("Thiếu nút " + id, xml.contains("@+id/" + id + "\""));
        }
    }

    @Test
    public void manNhapCoDuOCuaBieuMau() throws IOException {
        String xml = layout();
        for (String id : REQUIRED_FIELDS) {
            assertTrue("Thiếu ô " + id + " của biểu mẫu BM 75.01",
                    xml.contains("@+id/" + id + "\""));
        }
    }

    @Test
    public void manNhapKhongCoONgoaiBieuMau() throws IOException {
        String xml = layout();
        for (String id : FORBIDDEN_FIELDS) {
            assertFalse("Ô " + id + " không có trên biểu mẫu giấy, không được đưa lại vào màn nhập",
                    xml.contains("@+id/" + id));
        }
    }

    /** Giờ hút và thời hạn lưu trữ phải chạm-để-chọn, không gõ tay giữa sân đỗ. */
    @Test
    public void oGioDungBoChonNgayGio_khongGoTay() throws IOException {
        String xml = layout();
        for (String id : new String[]{"f_startTime", "f_endTime", "f_storageFrom", "f_storageTo"}) {
            int at = xml.indexOf("@+id/" + id + "\"");
            assertTrue("Không tìm thấy ô " + id, at > 0);
            String tag = xml.substring(at, xml.indexOf("/>", at));
            assertTrue("Ô " + id + " phải dùng style giờ (chạm để chọn)",
                    tag.contains("BM7501.Input.Time"));
        }
    }

    private static String layout() throws IOException {
        return new String(Files.readAllBytes(Paths.get(LAYOUT)), StandardCharsets.UTF_8);
    }
}
