package com.megatech.fms.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;

import androidx.test.core.app.ApplicationProvider;

import com.megatech.fms.FMSApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Nhật ký máy in phải GHI ĐƯỢC vào fms.log và ghi đủ thứ cần để lần ra nguyên nhân.
 *
 * <p>Vì sao đáng test: cả tính năng này chỉ có một mục đích — khi hiện trường báo "không in
 * được", kỹ thuật mở nhật ký ra là biết hỏng ở đâu. Một chỗ ghi hỏng im lặng (thiếu quyền,
 * sai thư mục, nuốt ngoại lệ) làm cả việc này thành vô nghĩa mà không ai phát hiện, vì thứ
 * bị mất là những dòng chưa từng được viết.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = FMSApplication.class)
public class PrintDiagnosticsTest {

    private File logFile;

    @Before
    public void clearLog() {
        Context context = ApplicationProvider.getApplicationContext();
        logFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "fms.log");
        if (logFile.exists()) assertTrue(logFile.delete());
    }

    /**
     * Các dòng của máy in trong fms.log.
     *
     * <p>Lọc theo thẻ vì nhật ký này dùng chung với mọi thành phần khác — và dùng chung
     * chính là điều mong muốn: sự cố in phải nằm cạnh những việc xảy ra ngay trước nó.
     */
    private List<String> lines() throws Exception {
        assertTrue("Phải tạo được file nhật ký", logFile.exists());
        List<String> all = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
        List<String> mine = new java.util.ArrayList<>();
        for (String line : all) if (line.contains("[PRNT]")) mine.add(line);
        return mine;
    }

    /**
     * Một dòng phải trả lời đủ bốn câu: máy in nào, hỏng ở bước nào, địa chỉ nào, vì sao.
     * Thiếu bất kỳ câu nào là lại phải đi hỏi người dùng — đúng cái vòng luẩn quẩn cần phá.
     */
    @Test
    public void records_printer_kind_stage_address_and_reason_on_one_line() throws Exception {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.CONNECT, "AC:3F:A4:11:22:33",
                "Không mở được kết nối tới máy in", null);

        List<String> lines = lines();
        assertEquals(1, lines.size());
        String line = lines.get(0);

        assertTrue("phải có nhãn sự kiện", line.contains("event=PRINT_FAILED"));
        assertTrue("phải nói loại máy in", line.contains("printer=THERMAL"));
        assertTrue("phải nói bước hỏng", line.contains("stage=CONNECT"));
        assertTrue("phải nói địa chỉ máy in", line.contains("AC:3F:A4:11:22:33"));
        assertTrue("phải nói nguyên nhân", line.contains("Không mở được kết nối"));
        assertTrue("phải có mốc thời gian", line.startsWith("["));
        assertTrue("phải mang thẻ PRNT để lọc được trong nhật ký chung",
                line.contains("[PRNT]"));
    }

    /**
     * Nhật ký phải nói HỎNG PHIẾU NÀO. Không có nó thì đối soát sau ca chỉ biết "9:14 in
     * hỏng" mà không biết phiếu đó cuối cùng có ra giấy hay không — câu hỏi đầu tiên người
     * ta hỏi khi cầm nhật ký này.
     */
    @Test
    public void records_which_document_was_being_printed() throws Exception {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.SEND, "AC:3F:A4:11:22:33", "Phiếu 0A2B3C4D",
                "Gửi phiếu tới máy in thất bại", null);

        String line = lines().get(0);
        assertTrue("phải nói phiếu nào", line.contains("document=Phiếu 0A2B3C4D"));
    }

    /**
     * Ngoại lệ gốc phải đi kèm. Phần lớn lỗi Bluetooth có {@code getMessage()} null, nên nếu
     * chỉ ghi message thì nhật ký để lại đúng chữ "null" — vô dụng đúng lúc cần nhất.
     */
    @Test
    public void keeps_the_original_exception_type_even_when_its_message_is_null()
            throws Exception {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.SEND, "AC:3F:A4:11:22:33",
                "Gửi phiếu thất bại", new java.io.IOException());

        String line = lines().get(0);
        assertTrue("phải còn tên lớp ngoại lệ", line.contains("IOException"));
    }

    /** Không có mẫu số thì con số "hỏng 40 lần" không nói lên điều gì. */
    @Test
    public void records_successful_prints_too_so_the_failure_rate_has_a_denominator()
            throws Exception {
        PrintDiagnostics.recordSuccess(PrintDiagnostics.PrinterKind.DOT_MATRIX,
                "192.168.1.50:9100");

        String line = lines().get(0);
        assertTrue(line.contains("event=PRINT_OK"));
        assertTrue(line.contains("printer=DOT_MATRIX"));
    }

    /** Mỗi lần hỏng là một dòng riêng: đếm được theo nguyên nhân mới sửa dứt điểm được. */
    @Test
    public void appends_one_line_per_failure_instead_of_overwriting() throws Exception {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.DISCOVER, null, "Bluetooth đang TẮT", null);
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.DOT_MATRIX,
                PrintDiagnostics.Stage.CHECK, "192.168.1.50:9100",
                "Máy in trả mã không sẵn sàng: 0x1b", null);

        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("Bluetooth đang TẮT"));
        assertTrue(lines.get(1).contains("0x1b"));
    }

    /**
     * Địa chỉ chưa biết vẫn phải ghi được. Ca "Bluetooth tắt" xảy ra TRƯỚC khi biết máy in
     * nào, mà đó lại là một trong những nguyên nhân hay gặp nhất — mất nó là mất đúng phần
     * đáng đếm.
     */
    @Test
    public void still_records_when_the_printer_address_is_not_known_yet() throws Exception {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.DISCOVER, null,
                "Chưa ghép đôi máy in nào trong Bluetooth của máy", null);

        String line = lines().get(0);
        assertTrue(line.contains("address=null"));
        assertFalse("không được ném ra ngoài", line.isEmpty());
    }
}
