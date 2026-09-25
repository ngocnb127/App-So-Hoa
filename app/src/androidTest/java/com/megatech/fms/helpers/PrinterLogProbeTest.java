package com.megatech.fms.helpers;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * BÀI DÒ NHẬT KÝ MÁY IN — không phải test nghiệm thu.
 *
 * <p>Mục đích duy nhất: sinh ra vài bản ghi lỗi thật rồi in các dòng máy in
 * trong {@code fms.log} ra logcat, để nhìn tận mắt xem một dòng nhật ký trông thế nào và
 * nó có đủ thông tin để lần ra nguyên nhân hay không.
 *
 * <p>Vì sao cần: nguyên nhân "không in được" ngoài hiện trường hiện chưa rõ. Sửa mò khi chưa
 * biết hỏng ở đâu chỉ tạo thêm mã; việc đúng ở bước này là làm cho sự cố TỰ KHAI RA nó là gì,
 * rồi mới sửa.
 *
 * <p>Chạy: {@code adb shell am instrument -w -e class
 * com.megatech.fms.helpers.PrinterLogProbeTest com.megatech.fms.test/androidx.test.runner.AndroidJUnitRunner}
 * rồi đọc logcat theo tag {@code PRINT_LOG}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PrinterLogProbeTest {

    private static final String TAG = "PRINT_LOG";

    @Test
    public void writeSampleFailuresThenDumpTheLogFile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Log.i(TAG, "===== SINH BẢN GHI MẪU =====");

        // Đúng những ca hay gặp nhất ngoài hiện trường, mỗi ca một nguyên nhân khác nhau —
        // tất cả trước đây đều hiện lên màn hình bằng cùng một câu "Lỗi kết nối máy in".
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.DISCOVER, null, "Bluetooth đang TẮT", null);

        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.DISCOVER, null,
                "Chưa ghép đôi máy in nào trong Bluetooth của máy", null);

        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.CONNECT, "AC:3F:A4:11:22:33",
                "Không mở được kết nối tới máy in",
                new java.io.IOException("read failed, socket might closed or timeout"));

        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                PrintDiagnostics.Stage.PREPARE, "AC:3F:A4:11:22:33",
                "Máy in đang ở chế độ CPCL; đã gửi lệnh chuyển ZPL, máy đang khởi động lại"
                        + " nên lượt in này phải bỏ", null);

        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.DOT_MATRIX,
                PrintDiagnostics.Stage.CONNECT, "192.168.1.50:9100",
                "TCP không mở được tới máy in (máy tắt, sai địa chỉ, hoặc khác mạng)", null);

        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.DOT_MATRIX,
                PrintDiagnostics.Stage.CHECK, "192.168.1.50:9100",
                "Máy in trả mã không sẵn sàng: 0x1b", null);

        PrintDiagnostics.recordSuccess(PrintDiagnostics.PrinterKind.THERMAL,
                "AC:3F:A4:11:22:33");

        // Đường thật: gọi đúng lớp sản phẩm để chắc chắn nó CÓ ghi, chứ không chỉ tin vào
        // lời gọi thủ công ở trên. Máy in nhiệt không ghép đôi thì đây là một lỗi thật.
        Log.i(TAG, "===== GỌI ĐƯỜNG SẢN PHẨM (in nhiệt) =====");
        try {
            ZebraWorker worker = new ZebraWorker(context);
            worker.setStateListener(new ZebraWorker.ZebraStateListener() {
                @Override
                public void onConnectionError() {
                    Log.i(TAG, "callback: onConnectionError");
                }

                @Override
                public void onError() {
                    Log.i(TAG, "callback: onError");
                }

                @Override
                public void onSuccess() {
                    Log.i(TAG, "callback: onSuccess");
                }
            });
            worker.prinTest();
            Thread.sleep(15000);   // In thử chạy nền; chờ nó kết luận.
        } catch (Throwable ex) {
            Log.i(TAG, "gọi ZebraWorker ném: " + ex);
        }

        dumpLogFile(context);
    }

    private void dumpLogFile(Context context) throws Exception {
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "fms.log");
        Log.i(TAG, "===== NỘI DUNG " + file.getAbsolutePath() + " =====");
        if (!file.exists()) {
            Log.i(TAG, "CHƯA CÓ FILE — nghĩa là không có bản ghi nào được viết");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                // Chỉ in dòng của máy in: fms.log dùng chung nên đọc cả file sẽ ngập.
                if (!line.contains("[PRNT]")) continue;
                Log.i(TAG, line);
                count++;
            }
            Log.i(TAG, "===== TỔNG " + count + " DÒNG MÁY IN, file "
                    + file.length() + " byte =====");
        }
    }
}
