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
import android.provider.MediaStore;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.megatech.fms.databinding.ActivityInvoiceBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.ImageUtil;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.PrintWorker;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
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

public class PrintReceiptActivity extends UserBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);


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
       //     findViewById(R.id.btnCapture).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btnSign).setVisibility(View.GONE);
            findViewById(R.id.btnSellerSign).setVisibility(View.GONE);
        }

    }
    private  boolean reprint = false;
    private void loaddata() {

        setProgressDialog();
        Bundle b = getIntent().getExtras();
        String data = b.getString("RECEIPT");
        if (data !=null) {
            model = ReceiptModel.fromJson(data);
            bindData();
        }
        else
        {
            String uniqueId = b.getString("RECEIPT_ID");
            if (uniqueId!=null)
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

            ListView lv = findViewById(R.id.invoice_item_list);
            lv.setAdapter(new ReceiptItemAdapter(this, model.getItems()));

        }
        else {
            showMessage(R.string.title_data_error, R.string.receipt_not_found,R.drawable.ic_error, new Callable<Void>() {

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
        }

    }

    private void exit() {
        //setResult(Activity.RESULT_CANCELED);
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
                    //openCapture();
                    openImagePicker();
                else
                    showImage(model.getPdfPath());
                break;
            case R.id.btnSave:
                openSave();
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
        // Create an image file name

        String imageFileName = "JPEG_" + model.getNumber() + "_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        //File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openImagePicker() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File

            }
            // Continue only if the File was successfully created
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
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
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

                // Get the dimensions of the bitmap
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                bmOptions.inJustDecodeBounds = true;

                BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

                int photoW = bmOptions.outWidth;
                int photoH = bmOptions.outHeight;

                // Determine how much to scale down the image
                int scaleFactor = Math.min(photoW / targetW, photoH / targetH) - 1;
                if (scaleFactor < 1) scaleFactor = 1;

                // Decode the image file into a Bitmap sized to fill the View
                bmOptions.inJustDecodeBounds = false;
                bmOptions.inSampleSize = scaleFactor;//scaleFactor;

                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
                if (bitmap == null)
                {
                    Toast.makeText(this, getString(R.string.capture_error),Toast.LENGTH_LONG).show();
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
            if (exitAfterCapture)
                save();
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

            if (width> maxWidth) {
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

        //iv.getLayoutParams().width = 800;

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
        final AlertDialog dialog = builder.create();// builder.show();
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
        //LocalDate today = LocalDate.now();

        if (!model.isCaptured() && !BuildConfig.THERMAL_PRINTER) {
            showConfirmMessage(R.string.not_capture_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    exitAfterCapture = true;
                    openImagePicker();
                    return null;
                }
            }/*, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    save();
                    return null;
                }
            }*/);
        } else
            showConfirmMessage(R.string.e_invoice_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    save();
                    return null;
                }
            });

    }

    private boolean autoNumber = true;

    private void save() {
        Logger.appendLog("RECEIPT_WINDOW", "save receipt " + model.getNumber());
        setProgressDialog();
        sendScreenshot();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.postReceipt(model);
                if (autoNumber) {
                    TruckModel setting = FMSApplication.getApplication().getSetting();

                    int number = Integer.valueOf( model.getNumber().substring(4),36);


                    setting.setReceiptCount(number);
                    FMSApplication.getApplication().saveSetting(setting);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void response) {
                postCompleted();

                super.onPostExecute(response);
            }
        }.execute();


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

    private  void sendScreenshot()
    {
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
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ;
        File imagePath = null;
        try {
            imagePath = new File(folder,"screenshot_"+ model.getNumber()+".jpg");
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

        } catch (Exception ex)
        {

        }
        return imagePath;
    }
}
