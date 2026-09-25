package com.megatech.fms.helpers;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.megatech.fms.helpers.print.PrinterProvisioner;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import java.io.File;
import java.util.Date;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZebraWorker {

    /**
     * Tên luồng in. MỌI lời gọi Bluetooth tới máy in chạy ở đây, không bao giờ trên luồng
     * giao diện.
     *
     * <p>Đo trên xe thật 09-09-2026 (bản 119, gói {@code .fhs}): {@code con.open()} đi xuống
     * {@code BluetoothSocket.connect()} — lời gọi KHÔNG có hạn chờ — và cả đường in lúc đó
     * nằm trên luồng giao diện. Ba lượt in liên tiếp khoá giao diện 12, 12 và 8 giây, đều
     * vượt ngưỡng ANR 5 giây. Lượt thứ ba (21:56:57 → 21:57:05) bị hệ thống tuyên ANR giữa
     * chừng: hộp "FMS Delivery THERMAL không phản hồi" hiện lên, dù ngay sau đó phiếu VẪN IN
     * XONG và app chạy tiếp bình thường.
     *
     * <p>Đây mới là chỗ đắt: hộp thoại đó không tự tắt. Nó nằm lại trên màn hình trong khi
     * người dùng lưu phiếu, rời chuyến, quay về danh sách — rồi gần hai phút sau (21:58:45)
     * họ bấm "Đóng ứng dụng" và tiến trình chết. Tức là KHÔNG cần máy in hỏng mới mất app:
     * một lượt in thành công cũng đủ dính. Nhật ký của app không ghi được gì về lần chết này
     * vì không có exception nào — dấu vết duy nhất nằm ở mục ANR của Crashlytics.
     *
     * <p>Một luồng duy nhất, dùng chung cho cả ứng dụng: chỉ có MỘT máy in vật lý và một
     * {@link #con}. Hai lượt in chồng lên nhau là hai luồng cùng ghi vào một socket.
     */
    private static final String PRINTER_THREAD = "FMS-Printer";

    private static final ExecutorService printExecutor =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, PRINTER_THREAD));

    /**
     * Canh hạn mở kết nối. Phải là luồng riêng, không phải luồng in: nhiệm vụ của nó là cắt
     * một lời gọi đang nằm chết trên chính luồng in.
     */
    private static final ScheduledExecutorService connectWatchdog =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> new Thread(runnable, "FMS-Printer-Watchdog"));

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Hạn chờ mở kết nối Bluetooth tới máy in.
     *
     * <p>Chỉ tính riêng bước mở kết nối. KHÔNG bao trùm cả lượt in: nạp font tiếng Việt cho
     * máy in mới có thể mất tới khoảng một phút (xem {@code PrinterProvisioner}), đặt hạn cho
     * cả lượt in là cắt ngang đúng việc đó.
     */
    private static final long CONNECT_TIMEOUT_MS = 25_000L;

    public ZebraWorker(Context ctx)
    {
        context = ctx;
        // Dò máy in cũng đọc Bluetooth của máy, nên cũng không được nằm trên luồng giao diện.
        // Luồng in là luồng đơn nên việc này chắc chắn xong trước mọi lượt in xếp sau.
        runOnPrinterThread(() -> findPrinter(null));
    }

    /** Xếp việc vào luồng in rồi trả về ngay. */
    private static void runOnPrinterThread(Runnable task) {
        if (PRINTER_THREAD.equals(Thread.currentThread().getName())) {
            task.run();
            return;
        }
        printExecutor.execute(task);
    }

    /**
     * Xếp việc vào luồng in và CHỜ nó xong.
     *
     * <p>Dành cho lối gọi đã tự chạy nền và còn việc phải làm sau khi in xong. Gọi từ luồng
     * giao diện thì lùi về kiểu không chờ — chờ ở đó chính là cái ANR mà tệp này sinh ra để
     * dập.
     */
    private static void runOnPrinterThreadAndWait(Runnable task) {
        if (PRINTER_THREAD.equals(Thread.currentThread().getName())) {
            task.run();
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Logger.appendLog("ZEBRA ERROR",
                    "Gọi in từ luồng giao diện — chuyển sang chạy nền, lối gọi này cần sửa");
            printExecutor.execute(task);
            return;
        }
        try {
            printExecutor.submit(task).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            Logger.appendLog("ZEBRA ERROR", "Lượt in kết thúc bằng lỗi: " + ex.getMessage());
        }
    }

    /**
     * Mở kết nối tới máy in, có hạn chờ.
     *
     * <p>{@code BluetoothSocket.connect()} không nhận tham số hạn chờ và không tự bỏ cuộc.
     * Đóng socket từ một luồng khác là cách duy nhất cắt được nó: {@code open()} sẽ ném lỗi
     * và đi tiếp vào đúng đường báo lỗi sẵn có, thay vì nằm im vô hạn.
     */
    private void openConnection() throws Exception {
        final Connection target = con;
        final AtomicBoolean finished = new AtomicBoolean(false);
        ScheduledFuture<?> watchdog = connectWatchdog.schedule(() -> {
            if (finished.get()) return;
            Logger.appendLog("ZEBRA ERROR", "Quá " + (CONNECT_TIMEOUT_MS / 1000)
                    + " giây chưa mở được kết nối tới máy in — cắt kết nối");
            try {
                target.close();
            } catch (Exception ignored) {
            }
        }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            target.open();
        } finally {
            finished.set(true);
            watchdog.cancel(false);
        }
    }

    private void saveAddress(String macAddress)
    {
        final SharedPreferences preferences = context.getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("ZEBRA_MAC_ADDRESS", macAddress);
        editor.apply();
    }
    private void clearAddress()
    {
        final SharedPreferences preferences = context.getSharedPreferences("FMS", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("ZEBRA_MAC_ADDRESS");
        editor.apply();
    }
    private String getAddress()
    {
        final SharedPreferences preferences = context.getSharedPreferences("FMS", MODE_PRIVATE);
        return preferences.getString("ZEBRA_MAC_ADDRESS",null);
    }

//    private void findPrinter(ReceiptModel model) {
//        try {
//            String macAddress = getAddress();
//            if (macAddress != null)
//            {
//                con = new BluetoothConnection(macAddress);
//
//                Logger.appendLog("ZEBRA MAC ADDRESS",macAddress);
//                if (model!=null)
//                    print(model);
//                return;
//            }
//            BluetoothDiscoverer.findPrinters(context, new DiscoveryHandler() {
//                @Override
//                public void foundPrinter(DiscoveredPrinter discoveredPrinter) {
//                    printer = discoveredPrinter;
//
//                    printerBluetooth = (DiscoveredPrinterBluetooth) printer;
//                    if (printerBluetooth!=null)
//                        saveAddress(printerBluetooth.address);
//
//
//
//                }
//
//                @Override
//                public void discoveryFinished() {
//                    if (printer != null) {
//                        con = printer.getConnection();
//                        if (model != null)
//                            print(model);
//                    } else if (model != null)
//                        onConnectionError();
//                }
//
//                @Override
//                public void discoveryError(String s) {
//                    if (model!=null)
//                        onConnectionError();
//                }
//            });
//        }catch (Exception ex)
//        {
//            Logger.appendLog("ZEBRA ERROR",ex.getMessage());
//            if (model!=null)
//                onConnectionError();
//        }
//    }

    private void find2503Printer(BM2503Model model) {
        try {
            String macAddress = getAddress();

            // B1: Nếu đã lưu địa chỉ MAC, kết nối luôn
            if (macAddress != null) {
                con = new BluetoothConnection(macAddress);
                Logger.appendLog("ZEBRA MAC ADDRESS", macAddress);
                if (model != null)
                    print(model);
                return;
            }

            // B2: Nếu chưa có địa chỉ MAC, tìm trong thiết bị đã ghép đôi
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        adapter == null ? "Máy không có Bluetooth"
                                : "Bluetooth đang TẮT");
                return;
            }

            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        "Chưa ghép đôi máy in nào trong Bluetooth của máy");
                return;
            }

            // Tạm chọn thiết bị đầu tiên trong danh sách đã ghép
            for (BluetoothDevice device : bondedDevices) {
                macAddress = device.getAddress();
                Logger.appendLog("ZEBRA BONDED PICKED", macAddress);

                saveAddress(macAddress);
                con = new BluetoothConnection(macAddress);
                if (model != null)
                    print(model);
                return;
            }

            onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                    "Duyệt hết danh sách ghép đôi mà không chọn được máy in");

        } catch (Exception ex) {
            if (model != null)
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        "Lỗi khi tìm máy in trong danh sách ghép đôi", ex);
        }
    }

    private void findPrinter(ReceiptModel model) {
        try {
            String macAddress = getAddress();

            // B1: Nếu đã lưu địa chỉ MAC, kết nối luôn
            if (macAddress != null) {
                con = new BluetoothConnection(macAddress);
                Logger.appendLog("ZEBRA MAC ADDRESS", macAddress);
                if (model != null)
                    print(model);
                return;
            }

            // B2: Nếu chưa có địa chỉ MAC, tìm trong thiết bị đã ghép đôi
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        adapter == null ? "Máy không có Bluetooth"
                                : "Bluetooth đang TẮT");
                return;
            }

            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        "Chưa ghép đôi máy in nào trong Bluetooth của máy");
                return;
            }

            // Tạm chọn thiết bị đầu tiên trong danh sách đã ghép
            for (BluetoothDevice device : bondedDevices) {
                macAddress = device.getAddress();
                Logger.appendLog("ZEBRA BONDED PICKED", macAddress);

                saveAddress(macAddress);
                con = new BluetoothConnection(macAddress);
                if (model != null)
                    print(model);
                return;
            }

            onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                    "Duyệt hết danh sách ghép đôi mà không chọn được máy in");

        } catch (Exception ex) {
            if (model != null)
                onConnectionError(PrintDiagnostics.Stage.DISCOVER,
                        "Lỗi khi tìm máy in trong danh sách ghép đôi", ex);
        }
    }

    Context context;
    DiscoveredPrinter printer ;

    DiscoveredPrinterBluetooth printerBluetooth;

    /**
     * In phiếu tra nạp. Trả về NGAY, việc in chạy trên luồng in.
     *
     * <p>Kết quả về qua {@link ZebraStateListener}, và callback được gọi trên luồng giao
     * diện nên lối gọi cứ đụng thẳng vào view.
     */
    public void printReceipt(ReceiptModel receiptModel)
    {
        runOnPrinterThread(() -> doPrintReceipt(receiptModel));
    }

    private void doPrintReceipt(ReceiptModel receiptModel)
    {
        currentDocument = receiptModel == null ? null : "Phiếu " + receiptModel.getNumber();
        /*if (BuildConfig.DEBUG)
            print(receiptModel);
        else {

        }*/

        if ((con == null || !con.isConnected()) && printer == null)
            findPrinter(receiptModel);
        else {
            if (con == null || !con.isConnected())
                con = printer.getConnection();
            print(receiptModel);
        }
    }
    /**
     * In phiếu BM 75.01 (yêu cầu hút nhiên liệu).
     *
     * <p>Khác {@code printReceipt}/{@code print2503}: phiếu này có hai chữ ký (SKYPEC và khách
     * hàng) nên nạp hai ảnh vào máy in. Ảnh nào chưa có thì bản in chừa chỗ ký tay.
     *
     * <p>Khác {@link #printReceipt}: hàm này CHỜ in xong mới trả về, vì lối gọi
     * ({@code B7501Activity}) đã tự chạy nền.
     * Việc in vẫn đi qua luồng in chung để không có hai luồng cùng ghi vào một máy in.
     */
    public void print7501(BM7501Model model, BM7501Printer.Options options) {
        runOnPrinterThreadAndWait(() -> doPrint7501(model, options));
    }

    private void doPrint7501(BM7501Model model, BM7501Printer.Options options) {
        currentDocument = "BM 75.01";
        if (!ensureConnection()) {
            onConnectionError(PrintDiagnostics.Stage.CONNECT,
                    "Phiếu BM 75.01: không mở được kết nối tới máy in");
            return;
        }
        try {
            openConnection();
            if (!preparePrinter()) return;

            ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(con);
            storeSignature(zebraPrinter, BM7501Printer.GRF_SKYPEC,
                    model.getSkypecSignaturePath());
            storeSignature(zebraPrinter, BM7501Printer.GRF_CUSTOMER_FINAL,
                    model.getCustomerFinalSignaturePath());

            print(BM7501Printer.createZpl(model, options));

            con.close();
            onSuccess();
        } catch (Exception ex) {
            // In lỗi thì mọi kết luận đã ghi nhớ về máy in này hết đáng tin: có thể máy
            // đã bị reset về mặc định, hoặc địa chỉ đã lưu giờ trỏ sang máy khác.
            PrinterProvisioner.forget(context, getAddress());
            onError(PrintDiagnostics.Stage.SEND, "In phiếu BM 75.01 thất bại", ex);
        }
    }

    private void storeSignature(ZebraPrinter zebraPrinter, String grfName, String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (f.exists()) {
                zebraPrinter.storeImage(grfName, f.getAbsolutePath(), 300, 200);
            }
        } catch (Exception ex) {
            // Thiếu một chữ ký không được làm hỏng cả bản in; phần đó sẽ để ký tay.
            Logger.appendLog("ZEBRA ERROR", "storeSignature " + grfName + ": " + ex.getMessage());
        }
    }

    /**
     * Bảo đảm có kết nối tới máy in: dùng địa chỉ đã lưu, nếu chưa có thì lấy thiết bị
     * Bluetooth đã ghép đôi đầu tiên. Tách riêng cho đường in BM 75.01 để không phải
     * chép lại đoạn dò máy in lần thứ tư.
     */
    private boolean ensureConnection() {
        try {
            if (con != null && con.isConnected()) return true;

            String macAddress = getAddress();
            if (macAddress == null) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) return false;

                Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
                if (bondedDevices == null || bondedDevices.isEmpty()) return false;

                for (BluetoothDevice device : bondedDevices) {
                    macAddress = device.getAddress();
                    Logger.appendLog("ZEBRA BONDED PICKED", macAddress);
                    saveAddress(macAddress);
                    break;
                }
            }
            if (macAddress == null) return false;

            con = new BluetoothConnection(macAddress);
            return true;
        } catch (Exception ex) {
            Logger.appendLog("ZEBRA ERROR", "ensureConnection: " + ex.getMessage());
            return false;
        }
    }

    /** In phiếu BM 25.03. Trả về ngay; xem {@link #printReceipt}. */
    public void print2503(BM2503Model bM2503Model)
    {
        runOnPrinterThread(() -> doPrint2503(bM2503Model));
    }

    private void doPrint2503(BM2503Model bM2503Model)
    {
        currentDocument = "BM 25.03";
        /*if (BuildConfig.DEBUG)
            print(receiptModel);
        else {

        }*/

        if ((con == null || !con.isConnected()) && printer == null)
            find2503Printer(bM2503Model);
        else {
            if (con == null || !con.isConnected())
                con = printer.getConnection();
            print(bM2503Model);
        }
    }

    /**
     * Kiểm tra máy in trước khi gửi phiếu: đúng ngôn ngữ ZPL và đã có font tiếng Việt.
     *
     * <p>Xem {@link PrinterProvisioner} — hai điều kiện này nằm trong máy in chứ không
     * trong ứng dụng, nên máy mới hoặc máy vừa reset sẽ in ra giấy trắng hoặc chữ mất dấu
     * mà không có dấu hiệu nào báo trước.
     *
     * @return false khi KHÔNG được in tiếp ở lượt này.
     */
    private boolean preparePrinter() {
        PrinterProvisioner.Result result =
                PrinterProvisioner.prepare(context, con, getAddress());

        if (result == PrinterProvisioner.Result.SWITCHED_TO_ZPL_NEEDS_RETRY) {
            // Máy in đang khởi động lại sau lệnh chuyển ngôn ngữ: kết nối hiện tại đã chết,
            // gửi phiếu vào đó là mất phiếu mà người dùng tưởng đã in.
            Logger.appendLog("ZEBRA_SETUP",
                    "Đã chuyển máy in sang ZPL, máy in đang khởi động lại — cần in lại");
            PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                    PrintDiagnostics.Stage.PREPARE, getAddress(),
                    "Máy in đang ở chế độ CPCL; đã gửi lệnh chuyển ZPL, máy đang khởi động"
                            + " lại nên lượt in này phải bỏ", null);
            try {
                con.close();
            } catch (Exception ignored) {
            }
            // Nguyên nhân đã ghi ở trên; ở đây chỉ báo cho màn hình, không ghi trùng.
            //
            // GIỮ địa chỉ MAC: vừa nói chuyện được với đúng máy in này. Xoá đi thì lượt in
            // lại sẽ tự chọn "thiết bị ghép đôi đầu tiên" — có thể là tai nghe hay máy in
            // khác. Sau Factory máy in, máy gần như chắc về CPCL nên nhánh này chạy thường
            // xuyên. Chủ dự án chốt ngày 2026-09-14.
            notifyListener(ZebraStateListener::onError);
            return false;
        }
        return true;
    }

    private  void print(BM2503Model bM2503Model)
    {
        try {

            openConnection();
            if (!preparePrinter()) return;
            String zpl =  bM2503Model.createThermalBM2503Text();

            if (bM2503Model.getSignPictureUrl() != null) {
                File f = new File(bM2503Model.getSignPictureUrl());
                if (f.exists()) {
                    ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(con);
                    zebraPrinter.storeImage("E:BUYER.GRF", f.getAbsolutePath(), 300, 200);
                }

            }


            print(zpl);

            con.close();
            onSuccess();
        }
        catch (Exception ex)
        {
            // In lỗi thì mọi kết luận đã ghi nhớ về máy in này hết đáng tin: có thể máy
            // đã bị reset về mặc định, hoặc địa chỉ đã lưu giờ trỏ sang máy khác.
            PrinterProvisioner.forget(context, getAddress());
            onError(PrintDiagnostics.Stage.SEND, "Gửi phiếu tới máy in thất bại", ex);
        }

    }
    private  void print(ReceiptModel receiptModel)
    {
        try {

            openConnection();
            if (!preparePrinter()) return;
            String zpl = receiptModel.isReturn()? receiptModel.createReturnThermalText(): receiptModel.createThermalText();

            if (receiptModel.getSignaturePath() != null) {
                File f = new File(receiptModel.getSignaturePath());
                if (f.exists()) {
                    ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(con);
                    zebraPrinter.storeImage("E:BUYER.GRF", f.getAbsolutePath(), 300, 200);
                }

            }
            if (receiptModel.getSellerSignaturePath() != null) {
                File f = new File(receiptModel.getSellerSignaturePath());
                if (f.exists()) {
                    ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(con);
                    zebraPrinter.storeImage("E:SELLER.GRF", f.getAbsolutePath(), 300, 200);

                }

            }

            print(zpl);

            con.close();
            onSuccess();
        }
        catch (Exception ex)
        {
            // In lỗi thì mọi kết luận đã ghi nhớ về máy in này hết đáng tin: có thể máy
            // đã bị reset về mặc định, hoặc địa chỉ đã lưu giờ trỏ sang máy khác.
            PrinterProvisioner.forget(context, getAddress());
            onError(PrintDiagnostics.Stage.SEND, "Gửi phiếu tới máy in thất bại", ex);
        }

    }
    /** Ghi trên luồng in, đọc cả ở nơi khác (ví dụ canh hạn mở kết nối) nên phải volatile. */
    volatile Connection con;
    public void print(String zpl) {

        if (con != null && con.isConnected()) {
            try {

                con.write(zpl.getBytes());


            } catch (Exception ex) {
                PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                        PrintDiagnostics.Stage.SEND, getAddress(),
                        "Ghi dữ liệu ZPL vào kết nối thất bại (" + zpl.length()
                                + " byte)", ex);
            }
        } else {
            onConnectionError(PrintDiagnostics.Stage.SEND,
                    "Kết nối Bluetooth đã đóng trước khi kịp gửi phiếu");
        }

    }
    /**
     * Phiếu thử: in chính TÌNH TRẠNG MÁY IN vừa đọc được lên giấy.
     *
     * <p>Người dựng máy in đứng cạnh máy, cầm tờ phiếu là biết ngay thiếu gì — không phải
     * lấy log về rồi mở máy tính đọc. Phiếu thử cũng dùng đúng bộ lệnh chất lượng in và
     * đúng cách in đậm như phiếu thật, nên nhìn tờ này là biết phiếu thật sẽ ra thế nào.
     */
    public String createTestString(PrinterProvisioner.Report report) {
        // Phiếu thử phải in được cả khi cấu hình xe chưa nạp xong — đây chính là lúc kỹ
        // thuật viên dựng máy, thiết bị có thể còn trống. Thiếu cấu hình thì lùi về lề mặc
        // định chứ không ném lỗi, vì lỗi ở đây che mất toàn bộ thông tin chẩn đoán.
        TruckModel setting = FMSApplication.getApplication().getSetting();
        TruckModel.THERMAL_PRINTER_TYPE printerType =
                setting == null ? null : setting.getThermalPrinterType();

        StringBuilder builder = new StringBuilder();
        int height = 80;
        String LEFT_INDENT = printerType == TruckModel.THERMAL_PRINTER_TYPE.ZQ520
                ? "^LH130,0\n" : "^LH000,0\n";

        builder.append("^XA");
        builder.append(ReceiptModel.printQualityHeader());
        builder.append("^CWZ,E:OPENSANS-RE.TTF^FS  \n" + LEFT_INDENT + "^CI28");

        builder.append("^CFZ,40\n"
                + "^FO0," + height + "^FB600,1,0,C,0^FDTHỬ MÁY IN^FS\n");
        height += 45;
        builder.append("^CFZ,22\n"
                + "^FO0," + height + "^FB600,1,0,C,0^FDPRINTER TEST^FS\n");
        height += 30;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FD"
                + DateUtils.formatDate(new Date(), "HH:mm dd/MM/yyyy") + "^FS\n");
        height += 30;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 20;

        builder.append("^CFZ,26\n");
        height = appendTestRow(builder, height, "Chế độ", describeLanguage(report));
        height = appendTestRow(builder, height, "Tình trạng", describeStatus(report));
        height = appendTestRow(builder, height, "Font tiếng Việt", describeFont(report));
        height = appendTestRow(builder, height, "Loại giấy", describeMedia(report));
        height = appendTestRow(builder, height, "Máy in",
                printerType == null ? "chưa có cấu hình" : printerType.toString());
        // Sau Factory máy in, hai dòng này cho biết máy đã về tên nào mà không cần mở
        // danh sách Bluetooth.
        // Giá trị do máy in trả về, có thể đã bị ai đó đặt chứa ^ hoặc ~ — hai ký tự đó là
        // tiền tố lệnh ZPL, lọt vào ^FD là gãy cả phiếu thử.
        height = appendTestRow(builder, height, "Bluetooth",
                report == null || report.bluetoothName == null ? "Không đọc được"
                        : report.bluetoothName.replace('^', ' ').replace('~', ' '));
        height = appendTestRow(builder, height, "Serial",
                report == null || report.serial == null ? "Không đọc được"
                        : report.serial.replace('^', ' ').replace('~', ' '));

        height += 10;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 20;

        // Dòng này là phép thử THẬT của font: thiếu glyph thì ra ô vuông ngay tại đây,
        // không cần chờ tới lúc in phiếu có tên khách hàng dấu nặng.
        builder.append("^CFZ,24\n"
                + "^FO0," + height + "^FB600,1,0,L,0^FDKiểm tra dấu:^FS\n");
        height += 30;
        builder.append("^FO0," + height + "^FB600,2,0,L,0^FDĂÂĐÊÔƠƯ ăâđêôơư ựữệợỹặộ 25,5°C^FS\n");
        height += 60;

        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 20;
        builder.append("^CFZ,22\n"
                + "^FO0," + height + "^FB600,2,0,C,0^FDPhiếu này dùng đúng độ đậm và cách in "
                + "đậm của phiếu thật^FS\n");
        height += 60;

        builder.append("^PQ1");
        builder.append("^LH0,0\n");
        builder.append("^XZ");

        builder.insert(3 + ReceiptModel.printQualityHeader().length(), "^LL" + (height + 100));
        return ReceiptModel.emboldenFields(builder.toString());
    }

    /** Một dòng "nhãn : giá trị" của phiếu thử. */
    private int appendTestRow(StringBuilder builder, int height, String label, String value) {
        builder.append("^FO0," + height + "^FB300,1,0,L,0^FD" + label + "^FS\n"
                + "^FO300," + height + "^FB20,1,0,C,0^FD:^FS\n"
                + "^FO330," + height + "^FB270,1,0,L,0^FD" + value + "^FS\n");
        return height + 32;
    }

    private String describeLanguage(PrinterProvisioner.Report report) {
        if (report == null || report.language == null) return "Không đọc được";
        return report.isZpl() ? "ZPL (đúng)" : report.language + " (SAI)";
    }

    /**
     * In GIÁ TRỊ THÔ máy in trả về, không dịch sang chữ của mình. Máy in mỗi đời trả một
     * tập giá trị khác nhau; tự đặt tên cho chúng là tự bịa ra thông tin chẩn đoán.
     */
    private String describeStatus(PrinterProvisioner.Report report) {
        if (report == null) return "Không đọc được";
        if (report.error != null) return report.error;
        if (report.mediaStatus == null && report.headLatch == null) return "Không đọc được";
        return "giấy " + (report.mediaStatus == null ? "?" : report.mediaStatus)
                + ", đầu in " + (report.headLatch == null ? "?" : report.headLatch);
    }

    private String describeFont(PrinterProvisioner.Report report) {
        if (report == null) return "Không đọc được";
        if (report.fontJustUploaded) return "Vừa nạp xong";
        return report.fontInstalled ? "Đã có" : "CHƯA NẠP ĐƯỢC";
    }

    /**
     * Loại giấy: phiếu là giấy cuộn trơn nên máy in PHẢI ở chế độ liên tục. Đặt ở gap/mark
     * thì máy đi tìm khe không có, nhả giấy dài rồi báo hết giấy.
     */
    private String describeMedia(PrinterProvisioner.Report report) {
        if (report == null) return "Không đọc được";
        if (report.mediaJustSetContinuous)
            return "vừa đặt lại liên tục (trước: "
                    + (report.mediaType == null ? "?" : report.mediaType) + ")";
        if (report.mediaType == null) return "Không đọc được";
        return report.mediaType + " (liên tục, đúng)";
    }
    /**
     * In thử: đọc tình trạng máy in THẬT rồi in chính tình trạng đó lên phiếu.
     *
     * <p>Cố ý không dùng cờ ghi nhớ như đường in phiếu. Người bấm In thử đang muốn biết
     * sự thật hiện tại của máy in; một cờ đã lưu từ tuần trước không trả lời được câu đó.
     */
    public void prinTest() {
        // Chạy nền, KHÔNG trên luồng giao diện. In thử giờ hỏi máy in và có thể nạp font
        // (khoảng một phút qua Bluetooth); làm việc đó trong onOptionsItemSelected sẽ treo
        // giao diện tới mức Android giết ứng dụng.
        //
        // Đi chung luồng với các lượt in phiếu, không tự mở luồng riêng: in thử và in phiếu
        // dùng chung một kết nối tới cùng một máy in, chạy song song là giẫm lên nhau.
        runOnPrinterThread(this::runPrintTest);
    }

    private void runPrintTest() {
        currentDocument = "Phiếu thử";
        try {
            if (!ensureConnection()) {
                onConnectionError(PrintDiagnostics.Stage.CONNECT,
                        "In thử: không mở được kết nối tới máy in");
                return;
            }

            // ensureConnection có thể trả về một kết nối ĐANG MỞ. Gọi open() lần nữa trên
            // kết nối đã mở là lỗi, và đó là lỗi làm nút In thử hỏng ngay từ lần bấm đầu.
            if (!con.isConnected()) openConnection();

            PrinterProvisioner.Report report = PrinterProvisioner.inspect(context, con);

            if (report.isCpclMode()) {
                // Máy đang ở CPCL: phiếu thử cũng là ZPL nên in ra sẽ là giấy trắng. Chuyển
                // ngôn ngữ rồi dừng — máy in reboot, phải bấm In thử lại.
                PrinterProvisioner.switchToZplMode(con);
                Logger.appendLog("ZEBRA_SETUP",
                        "In thử: máy in ở CPCL, đã chuyển sang ZPL — máy đang khởi động lại, bấm In thử lại");
                closeQuietly();
                // Không qua onError(): nó xoá địa chỉ MAC, trong khi vừa nói chuyện được với
                // đúng máy này. Xem preparePrinter().
                PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                        PrintDiagnostics.Stage.PREPARE, getAddress(), currentDocument,
                        "In thử: máy in ở chế độ CPCL, đã chuyển sang ZPL, máy đang khởi"
                                + " động lại", null);
                notifyListener(ZebraStateListener::onError);
                return;
            }

            print(createTestString(report));

            closeQuietly();
            onSuccess();
        } catch (Throwable ex) {
            // Ghi cả stack trace: getMessage() của phần lớn lỗi Bluetooth và lỗi SDK là
            // null, nên log cũ chỉ để lại đúng chữ "prinTest: null".
            closeQuietly();
            onError(PrintDiagnostics.Stage.SEND, "In thử thất bại",
                    ex instanceof Exception ? (Exception) ex : new Exception(ex));
        }
    }

    private void closeQuietly() {
        try {
            if (con != null && con.isConnected()) con.close();
        } catch (Exception ignored) {
        }
    }

    /** Nhận thông tin máy in đọc được, trên luồng giao diện. */
    public interface IdentityCallback {
        void onIdentity(PrinterProvisioner.Identity identity);
    }

    /**
     * Đọc tên Bluetooth, serial, ngôn ngữ của máy in — bước đầu của Factory máy in, để hộp
     * xác nhận nói được người dùng sẽ phải ghép lại với tên nào.
     *
     * <p>Chạy trên luồng in như mọi lời gọi Bluetooth khác; kết quả về luồng giao diện.
     */
    public void readPrinterIdentity(IdentityCallback callback) {
        runOnPrinterThread(() -> {
            PrinterProvisioner.Identity identity = doReadPrinterIdentity();
            if (callback != null) mainHandler.post(() -> callback.onIdentity(identity));
        });
    }

    private PrinterProvisioner.Identity doReadPrinterIdentity() {
        PrinterProvisioner.Identity identity;
        try {
            if (!ensureConnection()) {
                identity = new PrinterProvisioner.Identity();
                identity.error = "Không mở được kết nối tới máy in";
            } else {
                if (!con.isConnected()) openConnection();
                identity = PrinterProvisioner.readIdentity(con);
            }
        } catch (Throwable ex) {
            identity = new PrinterProvisioner.Identity();
            identity.error = Logger.describe(ex);
            Logger.appendLog("ZEBRA_SETUP", "Đọc thông tin máy in thất bại: " + identity.error);
        } finally {
            closeQuietly();
        }
        identity.address = getAddress();
        // Máy in không trả lời tên thì lấy tên Android đang thấy — vẫn hơn để trống.
        if (identity.bluetoothName == null) identity.bluetoothName = androidBluetoothName(identity.address);
        return identity;
    }

    /** Tên Android lưu cho thiết bị đã ghép đôi, null nếu không đọc được. */
    private String androidBluetoothName(String macAddress) {
        if (macAddress == null) return null;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return null;
            return adapter.getRemoteDevice(macAddress).getName();
        } catch (Exception ex) {
            // SecurityException khi thiếu quyền BLUETOOTH_CONNECT, hoặc MAC hỏng.
            return null;
        }
    }

    /**
     * Factory máy in: đưa về toàn bộ cài đặt gốc (in, mạng, Bluetooth), tuỳ chọn đặt tên
     * Bluetooth mới, rồi khởi động lại máy in. Trả về NGAY; kết quả về qua
     * {@link ZebraStateListener}.
     *
     * <p>Hàm gọi phải kiểm bằng {@link #readPrinterIdentity} rằng máy in đang ở ZPL — lệnh
     * factory là ZPL.
     */
    public void factoryReset(String newBluetoothName) {
        runOnPrinterThread(() -> runFactoryReset(newBluetoothName));
    }

    private void runFactoryReset(String newBluetoothName) {
        currentDocument = "Factory máy in";
        final String address = getAddress();
        try {
            if (!ensureConnection()) {
                onConnectionError(PrintDiagnostics.Stage.CONNECT,
                        "Factory: không mở được kết nối tới máy in");
                return;
            }
            if (!con.isConnected()) openConnection();

            PrinterProvisioner.factoryReset(con, newBluetoothName);
        } catch (Throwable ex) {
            closeQuietly();
            // Có thể đã gửi được một phần: máy in không còn đúng như đã nhớ nữa.
            PrinterProvisioner.forget(context, address);
            onError(PrintDiagnostics.Stage.PREPARE, "Factory máy in thất bại",
                    ex instanceof Exception ? (Exception) ex : new Exception(ex));
            return;
        }

        // Máy in đang khởi động lại, kết nối này đã chết.
        closeQuietly();
        // BẮT BUỘC: prepare() đọc cờ nhớ TRƯỚC mọi kiểm tra. Không quên thì lượt in sau
        // bỏ qua bước chuyển ZPL/nạp font/đặt giấy liên tục, gửi ZPL vào máy vừa về CPCL
        // và ra giấy trắng. MAC thì giữ nguyên: factory không đổi địa chỉ máy in.
        PrinterProvisioner.forget(context, address);
        Logger.appendLog("ZEBRA_SETUP", "Factory máy in: đã gửi xong, máy in đang khởi động lại");
        // Không đi qua onSuccess(): đó là mẫu số thống kê LƯỢT IN, factory không phải lượt in.
        notifyListener(ZebraStateListener::onSuccess);
    }

    public interface ZebraStateListener{
        void onConnectionError();
        void onError();
        void onSuccess();
    }

    /**
     * Gọi listener trên luồng giao diện.
     *
     * <p>Việc in nay chạy nền, nhưng cả ba callback đều đụng thẳng vào view — đóng hộp tiến
     * trình, hiện thông báo lỗi, {@code binding.invalidateAll()}. Đưa chúng về luồng giao
     * diện ngay tại đây để mọi lối gọi không phải tự nhớ bọc {@code runOnUiThread}.
     */
    private void notifyListener(java.util.function.Consumer<ZebraStateListener> call) {
        final ZebraStateListener listener = stateListener;
        if (listener == null) return;
        mainHandler.post(() -> call.accept(listener));
    }

    private void onConnectionError() {
        onConnectionError(PrintDiagnostics.Stage.CONNECT, "Không rõ nguyên nhân", null);
    }

    private void onConnectionError(PrintDiagnostics.Stage stage, String reason) {
        onConnectionError(stage, reason, null);
    }

    private void onConnectionError(PrintDiagnostics.Stage stage, String reason,
                                   Throwable cause) {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                stage, getAddress(), currentDocument, reason, cause);
        clearAddress();
        notifyListener(ZebraStateListener::onConnectionError);
    }
    /**
     * Báo lỗi in.
     *
     * <p>Bản không tham số vẫn còn để các lối gọi cũ biên dịch được, nhưng nó ghi nhận là
     * "không rõ nguyên nhân" — và một bản ghi như vậy chính là dấu hiệu còn sót một chỗ
     * chưa nói được vì sao mình hỏng. Mọi lối gọi mới PHẢI dùng bản có nêu bước và lý do.
     */
    private void onError() {
        onError(PrintDiagnostics.Stage.SEND, "Không rõ nguyên nhân", null);
    }

    private void onError(PrintDiagnostics.Stage stage, String reason) {
        onError(stage, reason, null);
    }

    /**
     * Phiếu đang in. Đặt ngay trước mỗi lượt in để nhật ký nói được HỎNG PHIẾU NÀO — câu hỏi
     * đầu tiên khi đối soát sau ca là phiếu đó cuối cùng có ra giấy hay không.
     */
    private String currentDocument;

    private void onError(PrintDiagnostics.Stage stage, String reason, Throwable cause) {
        PrintDiagnostics.recordFailure(PrintDiagnostics.PrinterKind.THERMAL,
                stage, getAddress(), currentDocument, reason, cause);
        clearAddress();
        notifyListener(ZebraStateListener::onError);
    }
    private void onSuccess() {
        // Ghi cả lần thành công: không có mẫu số thì "tháng này hỏng 40 lần" không nói lên
        // điều gì — 40 trên 2000 lượt in khác hẳn 40 trên 60.
        PrintDiagnostics.recordSuccess(PrintDiagnostics.PrinterKind.THERMAL, getAddress());
        notifyListener(ZebraStateListener::onSuccess);
    }
    private  ZebraStateListener stateListener;

    public ZebraStateListener getStateListener() {
        return stateListener;
    }

    public void setStateListener(ZebraStateListener stateListener) {
        this.stateListener = stateListener;
    }
}
