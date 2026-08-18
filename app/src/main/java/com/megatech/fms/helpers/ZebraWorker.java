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
    public String createTestString() {
        TruckModel setting = FMSApplication.getApplication().getSetting();

        StringBuilder builder = new StringBuilder();
        int height = 80;
        String LEFT_INDENT =setting.getThermalPrinterType() == TruckModel.THERMAL_PRINTER_TYPE.ZQ520? "^LH130,0\n": "^LH000,0\n";
        builder.append("^XA");
        builder.append("^CWZ,E:OPENSANS-RE.TTF^FS  \n" +
                LEFT_INDENT +
                "^CI28");
        builder.append("^CFZ,25\n" +
                "^FO0," + height + "^FB600,2,0,C,0^FD" + "TESTING FORM^FS\n" +
                "^CFZ,40\n" +
                "^FO0," + (height + 50) + "^FB600,1,0,C,0^FDPRINTER TEST FORM^FS\n" +
                "^FO0," + (height + 90) + "^FB600,1,0,C,0^FD(THỬ MÁY IN)^FS");
        height += 140;
        builder.append("^CFZ,20\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDReceipt No. : " + "TEST0001" + "^FS\n" +
                "^FO0," + (height + 20)+ "^FB600,1,0,C,0^FD" + DateUtils.formatDate(new Date(), "dd/MM/yyyy") + "^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");
        builder.append("^CFZ,30");
        height += 50;
        builder.append("^FO0," + height + "^FB250,1,0,L,0^FDBuyer ^FS\n" +
                "^FO240," + height + "^FB10,1,0,C,0^FD:^FS\n" +
                "^FO250," + height + "^FB320," + ",0,L,0^FD TESTING ^FS");


        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDDETAIL^FS\n" +
                "^FO0," + (height + 40) + "^GB700,1,3^FS");

        height = height + 50;
        builder.append("^CFZ,30");
        int i = 1;



        builder.append("^CFZ,40\n" +
                "^FO0," + height + "^FB600,1,0,C,0^FDTOTAL^FS");
        height += 40;
        builder.append("^FO0," + height + "^GB700,1,3^FS");
        builder.append("^CFZ,30\n");
        height += 10;

        builder.append("^FO0," + height + "^GB700,1,3^FS");
        height += 10;
        builder.append("^FO0," + height + "^FB600,1,0,C,0^FDBuyer^FS");
        //print signature

        builder.append("^PQ1");
        builder.append("^LH0,0\n" );
        builder.append("^XZ");

        return builder.toString();

    }
    public void prinTest() {
        if (!ensureConnection()) {
            onConnectionError();
            return;
        }
        try {
            con.open();
            if (!preparePrinter()) return;

            print(createTestString());

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
