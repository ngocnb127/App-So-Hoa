package com.megatech.fms;

import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_DEVICE_CONNECTION_STATE;
import com.megatech.fms.databinding.ActivityInvoiceBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.ImageUtil;
import com.megatech.fms.helpers.LCRReader;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.sdk_tcs.sdk_tcs.IDevice;
import com.megatech.fms.sdk_tcs.sdk_tcs.model.DeviceDataView;
import com.megatech.fms.sdk_tcs.sdk_tcs.tcs.TcsDevice;
import com.megatech.fms.view.ReceiptItemAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrintReceiptActivity extends UserBaseActivity implements View.OnClickListener, UpdateSensitiveScreen {

    // Device connection fields
    private LCRReader reader = null;
    private LCRDataModel lcrModel;
    private IDevice tcsDevice;
    private String storedIP;
    private static DeviceDataView tcsData;
    /** Số ticket đọc được từ thiết bị, in ở cuối bản in nhiệt. */
    private String deviceTicketNumber = "";   // chỉ dùng để đối chiếu/cảnh báo tại màn hình này
    private TextView txtGrossValue;
    private TextView txtTotalValue;
    private boolean deviceConnected = false;
    private double lastReceivedGross = 0;
    private double lastReceivedTotal = 0;
    private boolean deviceDataChecked = false;  // ← Chỉ check một lần

    /** Thời điểm nhận được giá trị đầu tiên từ thiết bị; 0 nghĩa là chưa có gì. */
    private long firstDeviceValueAt = 0;

    /**
     * Hạn chờ giá trị còn lại sau khi đã nhận được giá trị đầu tiên.
     *
     * <p>Chu kỳ hỏi trường của {@link com.megatech.fms.helpers.LCRReader} là 1 giây, nên 4 giây
     * là dư cho một vòng đọc đủ. Vẫn phải có hạn chờ vì có trường hợp đồng hồ KHÔNG BAO GIỜ trả
     * lượng mẻ — in lại phiếu cũ sau khi thanh ghi mẻ đã reset — và khi đó màn hình không được
     * treo vô hạn.
     */
    private static final long DEVICE_READ_TIMEOUT_MS = 4000;

    private boolean deviceDisconnected = false;

    private final Handler saveLockHandler = new Handler(Looper.getMainLooper());
    private Runnable saveLockTimeoutRunnable;

    private static final long SAVE_LOCK_TIMEOUT_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        storedIP = currentApp.getDeviceIP();

        loaddata();
        printWorker = new PrintWorker();
        printWorker.setPrintStateListener(new PrintWorker.PrintStateListener() {
            @Override
            public void onConnectionError() {
                runOnUiThread(() -> {
                    showErrorMessage(R.string.printer_connection_error);
                });
            }

            @Override
            public void onError() {

            }

            @Override
            public void onSuccess() {
                model.setPrinted(true);
                binding.invalidateAll();
            }
        });

        zebra = new ZebraWorker(this);
        zebra.setStateListener(new ZebraWorker.ZebraStateListener() {
            @Override
            public void onConnectionError() {
                closeProgressDialog();
                showErrorMessage(R.string.printer_connection_error);
            }

            @Override
            public void onError() {
                closeProgressDialog();
                showErrorMessage(R.string.printer_error);
            }

            @Override
            public void onSuccess() {
                model.setPrinted(true);
                binding.invalidateAll();
                closeProgressDialog();
            }
        });

        if (BuildConfig.THERMAL_PRINTER) {
            //findViewById(R.id.btnCapture).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btnSign).setVisibility(View.GONE);
            findViewById(R.id.btnSellerSign).setVisibility(View.GONE);
        }
    }

    private void initDeviceConnection() {
        new Thread(() -> {
            try {
                TruckModel settingModel = currentApp.getSetting();

                if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.LCR) {
                    reader = LCRReader.create(PrintReceiptActivity.this, storedIP, 10001, false);
                    lcrModel = new LCRDataModel();
                    addLCRListeners();
                    if (!reader.getConnected()) {
                        reader.doConnectDevice();
                        Thread.sleep(2000);
                    }
                    if (reader.getConnected()) {
                        deviceConnected = true;
                        reader.requestData();
                        // Không disconnect ở đây - chờ dữ liệu về ở onDataChanged (xem updateGrossDisplay)
                    } else {
                        showDeviceConnectionWarning();
                        disconnectDeviceOnce(); // không kết nối được -> ngắt ngay, không giữ kết nối treo
                    }
                } else if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                    tcsDevice = new TcsDevice(storedIP, 10001,
                            () -> {
                                deviceConnected = true;
                                Logger.appendLog("PRINT", "TCS đã kết nối");
                            },
                            () -> {
                                deviceConnected = false;
                                Logger.appendLog("PRINT", "TCS đã ngắt kết nối");
                            },
                            () -> updateGrossDisplay(),
                            () -> Logger.appendLog("PRINT", "TCS đã bắt đầu"),
                            () -> Logger.appendLog("PRINT", "TCS đã dừng")
                    );
                    tcsDevice.connect();
                    tcsDevice.runTask();
                    Thread.sleep(2000);
                    if (tcsDevice.isConnect()) {
                        deviceConnected = true;
                        // Không disconnect ở đây - chờ dữ liệu về ở updateGrossDisplay (qua OnRecivedData)
                    } else {
                        showDeviceConnectionWarning();
                        disconnectDeviceOnce(); // không kết nối được -> ngắt ngay
                    }
                }
            } catch (Exception e) {
                Logger.appendLog("PRINT_DEVICE", "Lỗi kết nối thiết bị (không nghiêm trọng): " + e.getMessage());
                deviceConnected = false;
                showDeviceConnectionWarning();
                disconnectDeviceOnce();
            }
        }).start();
    }
    private synchronized void disconnectDeviceOnce() {
        if (deviceDisconnected) return;
        deviceDisconnected = true;

        try {
            TruckModel settingModel = currentApp.getSetting();
            if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.LCR) {
                if (reader != null) {
                    reader.setFieldDataListener(null);
                    reader.setConnectionListener(null);
                    reader.doDisconnectDevice();
                    Logger.appendLog("PRINT", "Đã ngắt kết nối LCR sau khi lấy số");
                }
            } else if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                if (tcsDevice != null) {
                    tcsDevice.disConnect();
                    Logger.appendLog("PRINT", "Đã ngắt kết nối TCS sau khi lấy số");
                }
            }
        } catch (Exception e) {
            Logger.appendLog("PRINT_CLEANUP", "Lỗi khi ngắt kết nối thiết bị: " + e.getMessage());
        }
    }

    private void checkDeviceDataAfterConnection() {
        try {
            // ← Check từng item, nếu có 1 item thoả mãn thì OK
            boolean found = false;

            if (model != null && model.getItems() != null) {
                for (ReceiptItemModel item : model.getItems()) {
                    // ← Item thoả mãn nếu:
                    // 1. Gallon của item == Gross từ device
                    // 2. EndNumber của item == Total từ device
                    if (Math.abs(item.getGallon() - lastReceivedGross) < 0.5 &&
                            Math.abs(item.getEndNumber() - lastReceivedTotal) < 0.5) {
                        found = true;
                        Logger.appendLog("PRINT_CHECK",
                                "Match found - Item Gallon: " + item.getGallon() +
                                        " GL, Device Gross: " + lastReceivedGross + " GL");
                        break;
                    }
                }
            }

            // Không đọc được lượng mẻ thì KHÔNG đối chiếu được — phải nói ra.
            // Trước đây nhánh cảnh báo đòi lastReceivedGross > 0, nên khi thiết bị không trả
            // được số (đúng ca đang gặp) toàn bộ việc đối chiếu im lặng bỏ qua: người in tưởng
            // đã kiểm tra xong, thực chất chưa kiểm tra gì.
            if (lastReceivedGross <= 0) {
                final String message = String.format(
                        "⚠️ Chưa đọc được lượng nạp từ đồng hồ (Meter cuối = %.0f) — chưa đối chiếu được với phiếu",
                        lastReceivedTotal);
                runOnUiThread(() -> Toast.makeText(PrintReceiptActivity.this,
                        message, Toast.LENGTH_LONG).show());
                Logger.appendLog("PRINT_CHECK", "Không đối chiếu được: thiếu Gross. Total="
                        + lastReceivedTotal);
                return;
            }

            // ← Nếu không tìm thấy item nào thoả mãn
            if (!found && lastReceivedGross > 0) {
                final String message = String.format(
                        "⚠️ Không tìm thấy mẻ nạp nào có Gallon = %.0f GL và Meter cuối = %.0f",
                        lastReceivedGross, lastReceivedTotal
                );
                runOnUiThread(() -> {
                    Toast.makeText(PrintReceiptActivity.this,
                            message,
                            Toast.LENGTH_LONG).show();
                });
                Logger.appendLog("PRINT_CHECK", "No matching item: " + message);
            } else if (found) {
                Logger.appendLog("PRINT_CHECK", "Device check passed");
            }

        } catch (Exception e) {
            Logger.appendLog("PRINT_CHECK", "Check device data error: " + e.getMessage());
        }
    }

    private void showDeviceConnectionWarning() {
        runOnUiThread(() -> {
            Toast.makeText(PrintReceiptActivity.this,
                    "⚠️ Không thể kết nối đến đồng hồ. Kiểm tra IP và kết nối mạng",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void addLCRListeners() {
        reader.setFieldDataListener(new LCRReader.LCRDataListener() {
            @Override
            public void onDataChanged(LCRDataModel dataModel, LCRReader.FIELD_CHANGE field_change) {
                lcrModel = dataModel;
                updateGrossDisplay();
            }

            @Override
            public void onErrorMessage(String errorMsg) {
                Logger.appendLog("PRINT_LCR", errorMsg);
            }

            @Override
            public void onFieldAddSucess(String field_name) {
            }
        });

        reader.setConnectionListener(new LCRReader.LCRConnectionListener() {
            @Override
            public void onConnected() {
                Logger.appendLog("PRINT", "LCR Connected");
                deviceConnected = true;
                reader.requestData();
            }

            @Override
            public void onError() {
                Logger.appendLog("PRINT", "LCR Connection Error");
                deviceConnected = false;
            }

            @Override
            public void onDeviceAdded(boolean failed) {
            }

            @Override
            public void onDisconnected() {
                Logger.appendLog("PRINT", "LCR Disconnected");
                deviceConnected = false;
            }

            @Override
            public void onCommandError(com.liquidcontrols.lcr.iq.sdk.lc.api.constants.LCR.LCR_COMMAND command) {
            }

            @Override
            public void onConnectionStateChange(LCR_DEVICE_CONNECTION_STATE state) {
            }
        });
    }

    private void updateGrossDisplay() {
        try {
            if (txtGrossValue == null || txtTotalValue == null) return;

            TruckModel settingModel = currentApp.getSetting();
            double grossValue = 0;
            double totalValue = 0;

            if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.LCR && lcrModel != null) {
                grossValue = lcrModel.getGrossQty();
                totalValue = lcrModel.getEndMeterNumber();
                // Không đọc SALENUMBER ở đây: requestData() chỉ hỏi các trường số liệu,
                // SALENUMBER phải yêu cầu riêng và đã được ghi vào phiếu từ lúc tra nạp.
                if (lcrModel.getSaleNumber() != null && !lcrModel.getSaleNumber().isEmpty())
                    deviceTicketNumber = lcrModel.getSaleNumber();
            } else if (settingModel.getDeviceType() == TruckModel.DEVICE_TYPE.TCS) {
                // Lấy thẳng từ thiết bị tại thời điểm callback. Trước đây đọc một field static
                // không bao giờ được gán, nên Gross/Total luôn bằng 0 và không thể kết luận
                // hợp lệ / không hợp lệ.
                if (tcsDevice != null) tcsData = tcsDevice.getDeviceDataView();

                if (tcsData != null) {
                    grossValue = tcsData.getGrossQtyRound();
                    totalValue = tcsData.getGrossTotalRound();
                    if (tcsData.getTicketNumber() > 0)
                        deviceTicketNumber = String.valueOf(tcsData.getTicketNumber());
                }
            }

            lastReceivedGross = grossValue;
            lastReceivedTotal = totalValue;

            final double finalGrossValue = grossValue;
            final double finalTotalValue = totalValue;

            runOnUiThread(() -> {
                txtGrossValue.setText(String.format("%.0f", finalGrossValue));
                txtTotalValue.setText(String.format("%.0f", finalTotalValue));
            });

            // Mốc nhận được giá trị ĐẦU TIÊN, để tính hạn chờ giá trị còn lại.
            if (firstDeviceValueAt == 0 && (totalValue > 0 || grossValue > 0))
                firstDeviceValueAt = System.currentTimeMillis();

            // Đợi ĐỦ CẢ HAI số rồi mới đối chiếu và ngắt kết nối.
            //
            // Trước đây cổng này chỉ xét totalValue. GROSSMETERQTY và GROSSQTY là hai trường
            // riêng, đến không cùng lúc; trường nào về trước cũng mở cổng, và ngắt kết nối
            // ngay làm trường còn lại không bao giờ về tới. Thực tế Gross = 0 ở 11/12 lần.
            boolean hasBothValues = totalValue > 0 && grossValue > 0;
            boolean waitedTooLong = firstDeviceValueAt > 0
                    && System.currentTimeMillis() - firstDeviceValueAt > DEVICE_READ_TIMEOUT_MS;

            if (!deviceDataChecked && deviceConnected && totalValue > 0
                    && (hasBothValues || waitedTooLong)) {

                if (!hasBothValues)
                    Logger.appendLog("PRINT", String.format(java.util.Locale.US,
                            "Hết hạn chờ %dms mà chưa đủ số (Gross=%.0f, Total=%.0f)"
                                    + " — vẫn đi tiếp và báo cho người dùng",
                            DEVICE_READ_TIMEOUT_MS, finalGrossValue, finalTotalValue));

                checkDeviceDataAfterConnection();
                deviceDataChecked = true;

                // Đã lấy được số cần thiết -> ngắt kết nối ngay, chỉ giữ lại giá trị vừa nhận
                disconnectDeviceOnce();
                Logger.appendLog("PRINT", String.format(java.util.Locale.US,
                        "Đã lấy được số Gross=%.0f, Total=%.0f -> ngắt kết nối thiết bị",
                        finalGrossValue, finalTotalValue));
            }

            Logger.appendLog("PRINT", String.format(java.util.Locale.US,
                    "Thiết bị - Gross: %.0f, Total: %.0f", grossValue, totalValue));
        } catch (Exception e) {
            Logger.appendLog("PRINT_DISPLAY", "Lỗi cập nhật hiển thị: " + e.getMessage());
        }
    }

    private boolean reprint = false;

    private void loaddata() {

        setProgressDialog();
        Bundle b = getIntent().getExtras();
        String data = b.getString("RECEIPT");
        if (data != null) {
            model = ReceiptModel.fromJson(data);
            bindData();
            initDeviceConnection();  // ← Khởi tạo kết nối device (background thread)
        }
        else
        {
            String uniqueId = b.getString("RECEIPT_ID");
            if (uniqueId != null)
            {
                reprint = true;
                loadReceipt(uniqueId);
            }
        }
    }

    private void loadReceipt(String uniqueId)
    {
        new AsyncTask<Void, Void, ReceiptModel>() {
            @Override
            protected ReceiptModel doInBackground(Void... voids) {
                ReceiptModel response = DataHelper.getReceipt(uniqueId);
                return response;
            }

            @Override
            protected void onPostExecute(ReceiptModel response) {
                model = response;
                bindData();
                initDeviceConnection();  // ← Khởi tạo kết nối device (background thread)
                super.onPostExecute(response);
            }
        }.execute();
    }

    ReceiptModel model = null;
    ActivityInvoiceBinding binding;

    private void bindData() {
        closeProgressDialog();
        if (model != null) {
            binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.activity_invoice, null, false);
            binding.setInvoiceItem(model);
            setContentView(binding.getRoot());

            // ← Tìm TextViews để hiển thị Gross và Total
            txtGrossValue = findViewById(R.id.txtGrossValue);
            txtTotalValue = findViewById(R.id.txtTotalValue);

            if (txtGrossValue == null || txtTotalValue == null) {
                Logger.appendLog("PRINT", "Warning: Gross/Total TextViews not found in layout");
            }

            ListView lv = findViewById(R.id.invoice_item_list);
            lv.setAdapter(new ReceiptItemAdapter(this, model.getItems()));

        }
        else {
            showMessage(R.string.title_data_error, R.string.receipt_not_found, R.drawable.ic_error, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    finish();
                    return null;
                }
            });
        }

        if (reprint)
        {
            findViewById(R.id.btnSellerSign).setVisibility(View.GONE);
            findViewById(R.id.btnSign).setVisibility(View.GONE);
            findViewById(R.id.btnCapture).setVisibility(View.GONE);
            findViewById(R.id.btnSave).setVisibility(View.GONE);
            findViewById(R.id.btnTechlog).setVisibility(View.GONE);
            findViewById(R.id.receipt_signtype_check).setVisibility(View.GONE);
        }

    }

    private void exit() {
        if (model.isCaptured() || model.isPrinted()) {
            showConfirmMessage(R.string.receipt_not_saved, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    finish();
                    return null;
                }
            });
        } else
            finish();
    }

    private final int SELLER_SIGNATURE = 445;
    private final int BUYER_SIGNATURE = 446;

    private void openSignature(boolean buyer) {
        Intent intent = new Intent(this, ReceiptSignActivity.class);
        startActivityForResult(intent, buyer ? BUYER_SIGNATURE : SELLER_SIGNATURE);
    }

    @Override
    public void onClick(View view) {
        super.onClick(view);
        int id = view.getId();
        switch (id) {
            case R.id.btnBack:
                exit();
                break;
            case R.id.btnCapture:
                if (!model.isCaptured())
                    openImagePicker();
                else
                    showImage(model.getPdfPath());
                break;
            case R.id.btnSave:
                openSave();  // ← Không check device, cho phép save bình thường
                break;
            case R.id.btnPrint:
                print();
                break;
            case R.id.btnTechlog:
                m_Title = getString(R.string.input_techlog);
                showEditDialog(R.id.receipt_techlog, InputType.TYPE_NUMBER_FLAG_DECIMAL, ".*", false);
                break;
            case R.id.receipt_number:
                m_Title = getString(R.string.input_receipt_number);
                showEditDialog(R.id.receipt_number, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, ".*", false);
                break;
            case R.id.btnSign:
            case R.id.btnSellerSign:
                openSignature(id == R.id.btnSign);
                break;

            case R.id.receipt_defueling_number:
                if (model.getReturnAmount() > 0) {
                    m_Title = getString(R.string.update_defueling_no);
                    showEditDialog(id, InputType.TYPE_CLASS_TEXT, ".*", false);
                }
                break;
            case R.id.receipt_split_check:
                if (model.isInvoiceSplit()) {
                    m_Title = getString(R.string.update_split_amount);
                    showEditDialog(id, InputType.TYPE_NUMBER_FLAG_DECIMAL, ".*", true);
                } else {
                    model.setSplitAmount(0);
                    binding.invalidateAll();
                }
                break;

            case R.id.receipt_signtype_check:
                boolean isWaitingSign = ((CheckBox) view).isChecked();
                model.setSignType(isWaitingSign ? 3 : 1);
                binding.invalidateAll();
                Logger.appendLog("RECEIPT_WINDOW", "signType = " + model.getSignType());
                break;
        }
    }

    PrintWorker printWorker = null;
    ZebraWorker zebra = null;

    private void print() {
        if (BuildConfig.THERMAL_PRINTER) {
            if (zebra == null) {
                zebra = new ZebraWorker(this);
                zebra.setStateListener(new ZebraWorker.ZebraStateListener() {
                    @Override
                    public void onConnectionError() {
                        showErrorMessage(R.string.printer_error);
                    }

                    @Override
                    public void onError() {
                    }

                    @Override
                    public void onSuccess() {
                        model.setPrinted(true);
                        binding.invalidateAll();
                    }
                });
            }
            setProgressDialog();
            zebra.printReceipt(model);
        } else {
            if (printWorker == null) {
                printWorker = new PrintWorker();
            }
            if (model.isReturn())
                printWorker.printReturn(model);
            else
                printWorker.printReceipt(model);
        }
    }

    private final int REQUEST_IMAGE_CAPTURE = 1;
    private final int PICK_IMAGE = 2;
    String currentPhotoPath;

    private File createImageFile() throws IOException {
        String imageFileName = "JPEG_" + model.getNumber() + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openImagePicker() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        }

        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");
        pickIntent.putExtra("return-data", true);
        Intent chooserIntent = Intent.createChooser(takePictureIntent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});

        startActivityForResult(chooserIntent, REQUEST_IMAGE_CAPTURE);
    }

    private void saveImage(Uri uri, String outPath) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            int targetW = 1200;
            int targetH = 1200;
            Bitmap pdfBitmap = resize(bitmap, targetW, targetH);
            try {
                pdfBitmap.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(outPath));
            } catch (Exception e) {
                FirebaseCrashlytics.getInstance().recordException(e);
            }
        } catch (Exception ex) {
            FirebaseCrashlytics.getInstance().recordException(ex);
        }
    }

    boolean exitAfterCapture = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    saveImage(uri, currentPhotoPath);
                }
            } else {
                Logger.appendLog("RECEIPT_WINDOW", "Capture completed");
                int targetW = 800;
                int targetH = 800;
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
                int photoW = bmOptions.outWidth;
                int photoH = bmOptions.outHeight;
                int scaleFactor = Math.min(photoW / targetW, photoH / targetH) - 1;
                if (scaleFactor < 1) scaleFactor = 1;
                bmOptions.inJustDecodeBounds = false;
                bmOptions.inSampleSize = scaleFactor;
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
                if (bitmap == null)
                {
                    Toast.makeText(this, getString(R.string.capture_error), Toast.LENGTH_LONG).show();
                    FirebaseCrashlytics.getInstance().log("null bitmap " + currentPhotoPath);
                    return;
                }
                Bitmap pdfBitmap = resize(bitmap, targetW, targetH);
                try {
                    pdfBitmap.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(currentPhotoPath));
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                }
            }
            model.setPdfPath(currentPhotoPath);
            model.setCaptured(true);
            binding.invalidateAll();
            if (exitAfterCapture) {
                exitAfterCapture = false;
                save();
            }
        }
        else if (requestCode == BUYER_SIGNATURE && resultCode == RESULT_OK) {
            String file = data.getExtras().getString("signature_file");
            model.setSignaturePath(file);
            binding.invalidateAll();
            ((Button) findViewById(R.id.btnSign)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_checked, 0, 0, 0);
        }
        else if (requestCode == SELLER_SIGNATURE && resultCode == RESULT_OK) {
            String file = data.getExtras().getString("signature_file");
            model.setSellerSignaturePath(file);
            binding.invalidateAll();
            ((Button) findViewById(R.id.btnSellerSign)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_checked, 0, 0, 0);
        }
    }

    private static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            if (width > maxWidth) {
                int finalWidth = maxWidth;
                int finalHeight = maxHeight;
                if (ratioMax > ratioBitmap) {
                    finalWidth = (int) ((float) maxHeight * ratioBitmap);
                } else {
                    finalHeight = (int) ((float) maxWidth / ratioBitmap);
                }
                image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            }
            return image;
        } else {
            return image;
        }
    }

    private void showImage(String imagePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        ImageView iv = new ImageView(this);
        File imgFile = new File(imagePath);
        if (imgFile.exists()) {
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            iv.setImageBitmap(myBitmap);
        }
        Button btn = new Button(this);
        btn.setText(R.string.recapture);
        layout.addView(btn);
        builder.setView(layout);
        layout.addView(iv);
        layout.setPadding(10, 10, 10, 10);
        Dialog dlg = builder.create();
        dlg.show();
        dlg.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dlg.dismiss();
                openImagePicker();
            }
        });
    }

    String m_Title, m_Text;

    private void showEditDialog(final int id, int inputType, String pattern, boolean required) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(m_Title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setTypeface(Typeface.DEFAULT);

        if (id == R.string.receipt_number) {
            input.setText(getSetting().getReceiptCode().substring(0, 3));
            input.setSelection(3);
        } else
            input.setText(((TextView) findViewById(id)).getText().toString().trim());

        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }
        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        if (!required) {
            builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        }
        final AlertDialog dialog = builder.create();
        dialog.setCancelable(!required);
        dialog.show();
        input.requestFocus();
        input.setSelection(0, input.getText().length());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doUpdateResult())
                    dialog.dismiss();
            }

            private boolean doUpdateResult() {
                Locale locale = Locale.getDefault();
                NumberFormat numberFormat = NumberFormat.getInstance(locale);

                m_Text = input.getText().toString().trim();
                if (required && m_Text.isEmpty()) {
                    showErrorMessage(R.string.empty_required_field);
                    return false;
                }
                Pattern regex = Pattern.compile(pattern);
                Matcher matcher = regex.matcher(m_Text);
                if (!matcher.find()) {
                    Toast.makeText(getBaseContext(), getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                    return false;
                }
                try {
                    switch (id) {
                        case R.id.receipt_split_check:
                            double d = numberFormat.parse(m_Text).doubleValue();
                            if (d > model.getWeight()) {
                                showErrorMessage(R.string.error_split_amount_too_large);
                                return false;
                            }
                            model.setSplitAmount(d);
                            break;

                        case R.id.receipt_defueling_number:
                            model.setDefuelingNo(m_Text);
                            break;

                        case R.id.receipt_techlog:
                            double techlog = numberFormat.parse(m_Text).doubleValue();
                            model.setTechLog(techlog);
                            break;

                        case R.id.receipt_number:
                            model.setNumber(m_Text.trim());
                            autoNumber = false;
                            break;
                    }
                    binding.invalidateAll();
                } catch (ParseException ex) {
                    Toast.makeText(getBaseContext(), R.string.invalid_number_format, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
    }

    private void openSave() {
        // ← Không check device, cho phép save bình thường dù có cảnh báo hay không
        if (!beginSaveFlow()) {
            return;
        }

        if (!model.isCaptured() && !BuildConfig.THERMAL_PRINTER) {
            showConfirmMessage(R.string.not_capture_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    exitAfterCapture = true;
                    openImagePicker();
                    return null;
                }
            });
            return;
        }

        if ((model.getSellerSignaturePath() != null && !model.getSellerSignaturePath().isEmpty()
                && model.getSignaturePath() != null && !model.getSignaturePath().isEmpty())
                || model.isCaptured()) {

            showConfirmMessage(R.string.e_invoice_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    save();
                    return null;
                }
            });

        } else {
            showErrorMessage(R.string.not_capture_sign);
            resetSaveFlow();
        }
    }

    private boolean autoNumber = true;
    private boolean isSaving = false;
    private boolean saveFlowStarted = false;

    private void save() {
        if (isSaving) {
            return;
        }

        isSaving = true;
        setSaveButtonEnabled(false);
        cancelSaveLockTimeout(); // ← đã vào save() thật, không cần safety-net 3s nữa,
        // để tránh unlock giữa lúc network đang xử lý

        Logger.appendLog("RECEIPT_WINDOW", "save receipt " + model.getNumber());
        setProgressDialog();
        sendScreenshot();

        new AsyncTask<Void, Void, Boolean>() {
            Exception error;

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    DataHelper.postReceipt(model);

                    if (autoNumber) {
                        TruckModel setting = FMSApplication.getApplication().getSetting();
                        int number = Integer.valueOf(model.getNumber().substring(4), 36);
                        setting.setReceiptCount(number);
                        FMSApplication.getApplication().saveSetting(setting);
                    }

                    return true;
                } catch (Exception ex) {
                    error = ex;
                    FirebaseCrashlytics.getInstance().recordException(ex);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                closeProgressDialog();

                if (success) {
                    postCompleted();
                } else {
                    Logger.appendLog("RECEIPT_WINDOW", "save receipt error " + model.getNumber());
                    resetSaveFlow();
                    showErrorMessage(R.string.error);
                }

                super.onPostExecute(success);
            }
        }.execute();
    }

    private void setSaveButtonEnabled(boolean enabled) {
        View btnSave = findViewById(R.id.btnSave);
        if (btnSave != null) {
            btnSave.setEnabled(enabled);
            btnSave.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private boolean beginSaveFlow() {
        if (saveFlowStarted || isSaving) {
            return false;
        }

        saveFlowStarted = true;
        setSaveButtonEnabled(false);

        // Safety net: nếu vì lý do gì đó (cancel dialog, hủy camera, exception...)
        // mà flow không tự resetSaveFlow(), tự động mở khóa lại sau 3s
        scheduleSaveLockTimeout();

        return true;
    }
    private void scheduleSaveLockTimeout() {
        cancelSaveLockTimeout(); // tránh chồng nhiều runnable
        saveLockTimeoutRunnable = () -> {
            Logger.appendLog("RECEIPT_WINDOW", "Save lock timeout - tự động mở khóa btnSave sau 3s");
            resetSaveFlow();
        };
        saveLockHandler.postDelayed(saveLockTimeoutRunnable, SAVE_LOCK_TIMEOUT_MS);
    }

    private void cancelSaveLockTimeout() {
        if (saveLockTimeoutRunnable != null) {
            saveLockHandler.removeCallbacks(saveLockTimeoutRunnable);
            saveLockTimeoutRunnable = null;
        }
    }

    private void resetSaveFlow() {
        cancelSaveLockTimeout(); // đã reset thủ công thì hủy timeout đang chờ
        saveFlowStarted = false;
        isSaving = false;
        setSaveButtonEnabled(true);
    }



    private void postCompleted() {
        closeProgressDialog();
        Logger.appendLog("RECEIPT_WINDOW", "save receipt completed " + model.getNumber());
        Intent returnIntent = new Intent();
        returnIntent.putExtra("number", model.getNumber());
        returnIntent.putExtra("uniqueId", model.getUniqueId());
        returnIntent.putExtra("techlog", model.getTechLog());
        setResult(Activity.RESULT_OK, returnIntent);

        showInfoMessage(R.string.save_receipt_completed, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                finish();
                return null;
            }
        });
    }

    private void sendScreenshot() {
        Bitmap b = takeScreenshot();
        File f = saveBitmap(b);
        Logger.appendLog("RECEIPT", "screenshot file " + f.getName());
    }

    private Bitmap takeScreenshot() {
        View rootView = findViewById(android.R.id.content).getRootView();
        rootView.setDrawingCacheEnabled(true);
        return rootView.getDrawingCache();
    }

    private File saveBitmap(Bitmap bitmap) {
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imagePath = null;
        try {
            imagePath = new File(folder, "screenshot_" + model.getNumber() + ".jpg");
            imagePath.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imagePath);
            Bitmap scaledBitmap = ImageUtil.resize(bitmap, 1200);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            bitmap.recycle();
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        } catch (Exception ex) {
        }
        return imagePath;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelSaveLockTimeout();
        disconnectDeviceOnce();
    }
}