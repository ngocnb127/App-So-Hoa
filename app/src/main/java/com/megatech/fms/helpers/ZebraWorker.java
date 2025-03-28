package com.megatech.fms.helpers;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import java.io.File;
import java.util.Date;

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

    private void findPrinter(ReceiptModel model) {
        try {
            String macAddress = getAddress();
            if (macAddress != null)
            {
                con = new BluetoothConnection(macAddress);

                Logger.appendLog("ZEBRA MAC ADDRESS",macAddress);
                if (model!=null)
                    print(model);
                return;
            }
            BluetoothDiscoverer.findPrinters(context, new DiscoveryHandler() {
                @Override
                public void foundPrinter(DiscoveredPrinter discoveredPrinter) {
                    printer = discoveredPrinter;

                    printerBluetooth = (DiscoveredPrinterBluetooth) printer;
                    if (printerBluetooth!=null)
                        saveAddress(printerBluetooth.address);



                }

                @Override
                public void discoveryFinished() {
                    if (printer != null) {
                        con = printer.getConnection();
                        if (model != null)
                            print(model);
                    } else if (model != null)
                        onConnectionError();
                }

                @Override
                public void discoveryError(String s) {
                    if (model!=null)
                        onConnectionError();
                }
            });
        }catch (Exception ex)
        {
            Logger.appendLog("ZEBRA ERROR",ex.getMessage());
            if (model!=null)
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
    private  void print(ReceiptModel receiptModel)
    {
        try {

            con.open();
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

        print(createTestString());
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
