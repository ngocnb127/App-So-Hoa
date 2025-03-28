package com.megatech.fms.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.R;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.tcpclient.TcpClient;
import com.megatech.tcpclient.TcpEvent;

import java.nio.CharBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;

public class PrintWorker implements Observer {
    public PrintWorker()
    {}
    private  Activity activity;
    public PrintWorker(Activity context)
    {
        activity = context;
    }

    public interface PrintStateListener{
        void onConnectionError();
        void onError();
        void onSuccess();
    }

    private PrintStateListener printStateListener;

    public PrintStateListener getPrintStateListener() {
        return printStateListener;
    }

    public void setPrintStateListener(PrintStateListener printStateListener) {
        this.printStateListener = printStateListener;
    }

    private void onError()
    {
        if (printStateListener !=null)
            printStateListener.onError();
    }
    private void onConnectionError()
    {
        if (printStateListener !=null)
            printStateListener.onConnectionError();
    }
    private Queue<String> dataToPrint;

    private TcpClient mTcpClient;
    final int PRINTER_PORT = 9100;

    private void onSuccess()
    {

        printed = false;
        if (printStateListener !=null)
            printStateListener.onSuccess();
        new Runnable() {
            @Override
            public void run() {
                mTcpClient.destroy();
            }
        }.run();
    }
    //RefuelItemData itemToPrint;


    private char[] prepareText(String text)
    {
        //CharBuffer charBuffer =  CharBuffer.allocate(2048);
        String v="";
        for (char ch: text.toCharArray()) {
            if (UnicodeMap.getHashtable().containsKey(ch)) {
                UnicodeMap m = UnicodeMap.getHashtable().get(ch);
                if (m != null) {
                    v += new String(new char[]{0x1b, 0x26, 0x2, m.getReplaceChar(), m.getReplaceChar(), 9});
                    v += new String(m.getDefinedArray(),0,18);
                    v += new String(new char[]{0x1b, 0x25, 0x1});
                    v += m.getReplaceChar();
                    v += new String(new char[]{0x1B, 0x3f, m.getReplaceChar()});

                    //charBuffer.append(new char[]{0x1b, 0x26, 0x2, m.getReplaceChar(), m.getReplaceChar(), 9});
                }
            }
            else
                v+=ch;

        }
        return v.toCharArray();
    }

    private int printData() {
        try {
            //send the first data line and then remove from array
            if (dataToPrint.size() > 0) {
                String line = dataToPrint.poll();
                //dataToPrint.remove(0);
                if (line != null) {
                    line += (line.charAt(line.length() - 1) == '\n' ? "\n" : "");
                    //Logger.writePrintLog(String.format("queue %d :%s",dataToPrint.size(),line));
                    char[] charToWrite = prepareText(line);
                    //Logger.writeCharArray(line);
                    mTcpClient.sendMessage(charToWrite);


                }
            }
        }catch (Exception ex) {
            Logger.appendLog("PRINTER", ex.getMessage());
            //            return -1;
        }
        printed = dataToPrint.size() ==0;
        return  dataToPrint.size();
    }


    private void printReset() {


        mTcpClient.sendMessage(RESET_CODE);
    }


    public  boolean printBill(InvoiceModel invoiceModel) {
        return printBill(invoiceModel, false);
    }

    public boolean printBill(InvoiceModel invoiceModel, boolean old) {

        dataToPrint = old ? invoiceModel.createBillTextOld() : invoiceModel.createBillText();
        if (dataToPrint == null)
            return false;

        try {
            String printerAddress = FMSApplication.getApplication().getPrinterAddress();
            this.mTcpClient = new TcpClient(printerAddress, PRINTER_PORT);
            this.mTcpClient.addObserver(this);
            this.mTcpClient.connect();

        } catch (Exception e) {
            Log.e("ERROR", e.toString());
            return false;
        }
        return true;
    }


    public  boolean printInvoice(InvoiceModel invoiceModel)
    {
        return printInvoice(invoiceModel, false);
    }


    public boolean printInvoice(InvoiceModel invoiceModel, boolean old) {
        dataToPrint = old ? invoiceModel.createInvoiceTextOld() : invoiceModel.createInvoiceText();
        if (dataToPrint == null)
            return false;
        try {
            String printerAddress = FMSApplication.getApplication().getPrinterAddress();
            this.mTcpClient = new TcpClient(printerAddress, PRINTER_PORT);
            this.mTcpClient.addObserver(this);
            this.mTcpClient.connect();

        }
        catch (Exception e)
        {
            Log.e("ERROR", e.toString());
            return false;
        }
        return true;
    }
    String receitpData;
    boolean printReceipt;
    public boolean printReceipt(ReceiptModel receiptModel)
    {
       receitpData = receiptModel.createPrintText();
        printReceipt = true;
        try {
            String printerAddress = FMSApplication.getApplication().getPrinterAddress();
            this.mTcpClient = new TcpClient(printerAddress, PRINTER_PORT);
            this.mTcpClient.addObserver(this);
            this.mTcpClient.connect();

        }
        catch (Exception e)
        {
            Log.e("ERROR", e.toString());
            return false;
        }
        return  true;
    }
    public boolean printReturn(ReceiptModel receiptModel)
    {
        receitpData = receiptModel.createReturnText();
        printReceipt = true;
        try {
            String printerAddress = FMSApplication.getApplication().getPrinterAddress();
            this.mTcpClient = new TcpClient(printerAddress, PRINTER_PORT);
            this.mTcpClient.addObserver(this);
            this.mTcpClient.connect();

        }
        catch (Exception e)
        {
            Log.e("ERROR", e.toString());
            return false;
        }
        return  true;
    }
    private String createTestData() {


        String LS_18 = new String(new char[]{27, 51, 18});
        String LS_24 = new String(new char[]{27, 51, 24});
        String LS_DEFAULT = new String(new char[]{27, 50});
        StringBuilder builder = new StringBuilder();

        builder.append("------------------------------------------------------------------\n");
        builder.append("                    PRINTER TEST FORM                             \n");
        builder.append("                    Thử máy in                                    \n");
        builder.append("------------------------------------------------------------------\n");
        builder.append(String.format("No.: %-15s            (%s)\n", "TEST NUMBER", DateUtils.formatDate(new Date(), "dd/MM/yyyy")));
        builder.append(LS_18);
        builder.append(String.format("Buyer: %s\n", "Tên khách hàng" ));
        builder.append(LS_DEFAULT);
        builder.append("\n");
        builder.append(String.format("A/C Type         : %-16s A/C reg     : %s\n", "TEST Type", "Test Code"));
       builder.append("------------------------------------------------------------------\n");
        builder.append("| # |  Refueler No.     |   Temp.   |   USG  |  Litter |    Kg   |\n");
        builder.append(LS_18);
        builder.append("|   | Start/End Meter   | Density   |        |         |         |\n");
        builder.append(LS_DEFAULT);
        builder.append("------------------------------------------------------------------\n");
        int i = 1;

            builder.append(String.format("|%2d |%-19s|%8.2f oC|%8.0f|%9.0f|%9.0f|\n", i++, "Test printer", 0.00, 1234.56, 7890.12, 3456.78));
            builder.append(LS_18);
            builder.append(String.format("|   |%9.0f/%-9.0f|%6.4f kg/l|        |         |         |\n", 234567.89, 456789.34, 0.7898));
            builder.append(LS_DEFAULT);
            builder.append("------------------------------------------------------------------\n");

        return builder.toString();
    }
    public boolean printTest( )
    {

        receitpData = "Test Printer";
        printReceipt = true;
        try {
            String printerAddress = FMSApplication.getApplication().getPrinterAddress();
            this.mTcpClient = new TcpClient(printerAddress, PRINTER_PORT);
            this.mTcpClient.addObserver(this);
            this.mTcpClient.connect();

        }
        catch (Exception e)
        {
            Log.e("ERROR", e.toString());
            return false;
        }
        return  true;
    }
    private boolean onlineStatus = false;
    private boolean checking = false;
    @Override
    public void update(Observable o, Object arg) {
        TcpEvent event = (TcpEvent)arg;

        char[] payload = null;
        if (event.getPayload() !=null && event.getPayload() instanceof char[])
            payload = (char[])event.getPayload();
        switch (event.getTcpEventType()) {
            case CONNECTION_FAILED:

                onConnectionError();
                break;
            case MESSAGE_RECEIVED:
                if (payload!=null) {
                    Logger.appendLog("PRNT", "printer response: " + payload.toString());
                    if (checking && payload[0] == (char) 0x16) {
                        checking = false;
                        onlineStatus = true;
                        printReset();
                    } else if (checking)
                        onConnectionError();
                }
                break;
            case CONNECTION_ESTABLISHED:
                // printer connected, start sending data line to print;
                checkPrinter();
                break;

            case MESSAGE_SENT:

                // data line sent to printer, send next line, if ZERO, no more data to send
                if (payload.equals(CHECK_CODE))
                    checking = true;
                else if (payload.equals(RELEASE_CODE))
                    onSuccess();
                else if (printed){

                    releasePaper();
                }
                else {
                    if (!printReceipt)
                        printData();
                    else {
                        mTcpClient.sendMessage(prepareText(receitpData));
                        printed = true;
                    }
                }
                break;

        }
    }
    boolean printed = false;
    private void checkPrinter() {

        mTcpClient.sendMessage(CHECK_CODE);
    }
    private void releasePaper() {

        printed = false;
        mTcpClient.sendMessage(RELEASE_CODE);
    }
    private final char[] RESET_CODE =  new char[]{27, 64};
    private final char[] RELEASE_CODE = new char[]{27,101,60};
    private final char[] CHECK_CODE = new char[]{16,4,1};
    public enum PRINT_MODE
    {
        ONE_ITEM,
        ALL_ITEM
    }

    public enum PRINT_TEMPLATE
    {
        BILL,
        INVOICE
    }
}
