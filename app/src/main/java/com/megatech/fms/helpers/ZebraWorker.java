package com.megatech.fms.helpers;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
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

public class ZebraWorker {

    public ZebraWorker(Context ctx)
    {
        context = ctx;
        findPrinter(null);
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
                onConnectionError();
                return;
            }

            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                onConnectionError();
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

            onConnectionError();

        } catch (Exception ex) {
            Logger.appendLog("ZEBRA ERROR", ex.getMessage());
            if (model != null)
                onConnectionError();
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
                onConnectionError();
                return;
            }

            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                onConnectionError();
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

            onConnectionError();

        } catch (Exception ex) {
            Logger.appendLog("ZEBRA ERROR", ex.getMessage());
            if (model != null)
                onConnectionError();
        }
    }

    Context context;
    DiscoveredPrinter printer ;

    DiscoveredPrinterBluetooth printerBluetooth;

    public void printReceipt(ReceiptModel receiptModel)
    {
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
     * <p>Khác {@code printReceipt}/{@code print2503}: phiếu này có tới ba chữ ký nên nạp
     * ba ảnh vào máy in. Ảnh nào chưa có thì bản in chừa chỗ ký tay.
     */
    public void print7501(BM7501Model model, BM7501Printer.Options options) {
        if (!ensureConnection()) {
            onConnectionError();
            return;
        }
        try {
            con.open();
            if (!preparePrinter()) return;

            ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(con);
            storeSignature(zebraPrinter, BM7501Printer.GRF_CUSTOMER_SECTION_A,
                    model.getCustomerSectionASignaturePath());
            storeSignature(zebraPrinter, BM7501Printer.GRF_SKYPEC,
                    model.getSkypecSignaturePath());
            storeSignature(zebraPrinter, BM7501Printer.GRF_CUSTOMER_FINAL,
                    model.getCustomerFinalSignaturePath());

            print(BM7501Printer.createZpl(model, options));

            con.close();
            onSuccess();
        } catch (Exception ex) {
            Logger.appendLog("ZEBRA ERROR", "print7501: " + ex.getMessage());
            // In lỗi thì mọi kết luận đã ghi nhớ về máy in này hết đáng tin: có thể máy
            // đã bị reset về mặc định, hoặc địa chỉ đã lưu giờ trỏ sang máy khác.
            PrinterProvisioner.forget(context, getAddress());
            clearAddress();
            onError();
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

    public void print2503(BM2503Model bM2503Model)
    {
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
            try {
                con.close();
            } catch (Exception ignored) {
            }
            onError();
            return false;
        }
        return true;
    }

    private  void print(BM2503Model bM2503Model)
    {
        try {

            con.open();
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
            clearAddress();
            onError();
        }

    }
    private  void print(ReceiptModel receiptModel)
    {
        try {

            con.open();
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
            clearAddress();
            onError();
        }

    }
    Connection con;
    public void print(String zpl) {

        if (con != null && con.isConnected()) {
            try {

                con.write(zpl.getBytes());


            } catch (Exception ex) {
                Log.e("ZEBRA", ex.getMessage());
            }
        } else {
            onConnectionError();
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
        TruckModel setting = FMSApplication.getApplication().getSetting();

        StringBuilder builder = new StringBuilder();
        int height = 80;
        String LEFT_INDENT = setting.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ520
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
        height = appendTestRow(builder, height, "Máy in", setting.getThermalPrinterType().toString());

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

    private String describeStatus(PrinterProvisioner.Report report) {
        if (report == null) return "Không đọc được";
        if (report.error != null) return report.error;
        if (report.headOpen) return "Đang mở đầu in";
        if (report.paperOut) return "Hết giấy";
        return report.readyToPrint ? "Sẵn sàng" : "Chưa sẵn sàng";
    }

    private String describeFont(PrinterProvisioner.Report report) {
        if (report == null) return "Không đọc được";
        if (report.fontJustUploaded) return "Vừa nạp xong";
        return report.fontInstalled ? "Đã có" : "CHƯA NẠP ĐƯỢC";
    }
    /**
     * In thử: đọc tình trạng máy in THẬT rồi in chính tình trạng đó lên phiếu.
     *
     * <p>Cố ý không dùng cờ ghi nhớ như đường in phiếu. Người bấm In thử đang muốn biết
     * sự thật hiện tại của máy in; một cờ đã lưu từ tuần trước không trả lời được câu đó.
     */
    public void prinTest() {
        if (!ensureConnection()) {
            onConnectionError();
            return;
        }
        try {
            con.open();

            PrinterProvisioner.Report report = PrinterProvisioner.inspect(context, con);

            if (report.language == com.zebra.sdk.printer.PrinterLanguage.CPCL) {
                // Máy đang ở CPCL: phiếu thử cũng là ZPL nên in ra sẽ là giấy trắng. Chuyển
                // ngôn ngữ rồi dừng — máy in reboot, phải bấm In thử lại.
                PrinterProvisioner.switchToZplMode(con);
                Logger.appendLog("ZEBRA_SETUP",
                        "In thử: máy in ở CPCL, đã chuyển sang ZPL — máy đang khởi động lại, bấm In thử lại");
                try {
                    con.close();
                } catch (Exception ignored) {
                }
                onError();
                return;
            }

            print(createTestString(report));

            con.close();
            onSuccess();
        } catch (Exception ex) {
            Logger.appendLog("ZEBRA ERROR", "prinTest: " + ex.getMessage());
            onError();
        }
    }

    public interface ZebraStateListener{
        void onConnectionError();
        void onError();
        void onSuccess();
    }

    private void onConnectionError(){
        clearAddress();
        if (stateListener!=null)
            stateListener.onConnectionError();
    }
    private void onError() {
        clearAddress();
        if (stateListener != null)
            stateListener.onError();
    }
    private void onSuccess() {
        if (stateListener != null)
            stateListener.onSuccess();
    }
    private  ZebraStateListener stateListener;

    public ZebraStateListener getStateListener() {
        return stateListener;
    }

    public void setStateListener(ZebraStateListener stateListener) {
        this.stateListener = stateListener;
    }
}
