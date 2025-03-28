package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.megatech.fms.databinding.FlightReviewBinding;
import com.megatech.fms.enums.REVIEW_RATE;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.model.ReviewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ReviewActivity extends UserBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getIntent().getExtras();
        reviewId = b.getInt("REVIEW_ID", 0);
        flightId = b.getInt("FLIGHT_ID", 0);
        flightUUID = b.getString("FLIGHT_UUID", null);
        //setContentView(R.layout.flight_review);

        loadData();

    }

    private int reviewId, flightId;
    private String flightUUID;
    private boolean editable = false;
    ReviewModel model;
    FlightReviewBinding binding;
    private void loadData()
    {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                if (flightId > 0)
                    model = DataHelper.getReviewByFlight(flightId);
                else if (flightUUID != null)
                    model = DataHelper.getReviewByFlight(flightUUID);

                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {

                super.onPostExecute(unused);
                bindData();
            }
        }.execute();





    }

    private void setVisibleButton()
    {
        if (editable)
        {
            findViewById(R.id.btnSave).setVisibility(View.VISIBLE);
            findViewById(R.id.btnCapture).setVisibility(View.VISIBLE);
            findViewById(R.id.btnEdit).setVisibility(View.GONE);
        }
        else
        {
            findViewById(R.id.btnSave).setVisibility(View.GONE);
            findViewById(R.id.btnCapture).setVisibility(View.GONE);
            findViewById(R.id.btnEdit).setVisibility(View.VISIBLE);
        }
    }
    private void bindData()
    {
        if (model == null)
        {
            editable = true;

            model = new ReviewModel();
            model.setRate(REVIEW_RATE.NEUTRAL);
            model.setFlightId(flightId);
            model.setFlightUniqueId(flightUUID);
            model.setReviewDate(new Date());
            model.setDateUpdated(new Date());

            model.setUniqueId(UUID.randomUUID().toString());
        }

        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.flight_review, null, false);
        binding.setReviewItem(this.model);
        setContentView(binding.getRoot());
        setVisibleButton();
    }


    private final int REQUEST_IMAGE_CAPTURE = 1;

    private final int PICK_IMAGE = 2;

    String currentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name

        String imageFileName = "REVIEW_" + model.getUniqueId() + "_";

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
                int targetW = 1200;
                int targetH = 1200;

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
                if (bitmap == null) {
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

            model.setImagePath(currentPhotoPath);
            model.setCaptured(true);
            binding.invalidateAll();

        }
    }

    private static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }
            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
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

    @Override
    public void onClick(View v) {

        int id = v.getId();
        switch (id )
        {
            case R.id.btnSave:
                save();
                break;
            case R.id.btnEdit:
                editable = true;
                setVisibleButton();
                break;

            case R.id.btnCapture:
                if (model.isCaptured())
                    showImage(model.getImagePath());
                else
                openImagePicker();
                break;

            case R.id.btnBack:
                finish();
                break;
            case R.id.btnNeutral:
                setRate(REVIEW_RATE.NEUTRAL);
                break;
            case R.id.btnWorst:
                setRate(REVIEW_RATE.WORST);
                break;
            case R.id.btnBad:
                setRate(REVIEW_RATE.BAD);
                break;
            case R.id.btnGood:
                setRate(REVIEW_RATE.GOOD);
                break;
            case R.id.btnBest:
                setRate(REVIEW_RATE.BEST);
                break;
            case R.id.txtOtherReason:
                showInputOther();
                break;
            case R.id.chkBad1:
            case R.id.chkBad2:
            case R.id.chkBad4:
            case R.id.chkBad8:
            case R.id.chkBad16:
            case R.id.chkBad32:
                int val = Integer.parseInt( v.getTag().toString());
                if (((CheckBox)v).isChecked())
                {
                    model.setBadReason(val);
                    if (val==32 )
                        showInputOther();
                }
                else{
                    model.clearBadReason(val);
                }

                binding.invalidateAll();
                break;
        }

    }

    private void showInputOther()
    {
        showInputData(R.string.input_other_reason, ((TextView)findViewById(R.id.txtOtherReason)).getText().toString(), InputType.TYPE_CLASS_TEXT, "*", false, new OnInputCompleted() {
            @Override
            public boolean onOK(String text) {
                ((TextView)findViewById(R.id.txtOtherReason)).setText(text);
                return true;
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onCompleted() {

            }
        });
    }

    private void setRate(REVIEW_RATE rate) {
        if(editable) {
            model.setRate(rate);
            binding.invalidateAll();

            binding.notifyChange();
        }
    }

    private boolean validate()
    {
        if (model.isBad() || model.isWorst())
        {
            if (model.getBadReviewReason() <=0)
            {
                showInfoMessage(R.string.review_select_reason,null);
                return false;
            }
            if (model.isBadReason32() && model.getOtherReason() == null)
            {

                    showInfoMessage(R.string.review_input_other_reason,null);
                    return false;

            }
        }
        return true;
    }
    private void save() {

        if (validate()) {
            showConfirmMessage(R.string.create_review_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    postData();
                    return null;
                }
            });

        }


    }

    private void postData() {
        setProgressDialog();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                model.setLocalModified(true);
                DataHelper.postReview(model);
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                super.onPostExecute(unused);
                closeProgressDialog();
                showThankyou();
            }
        }.execute();
    }

    private void showThankyou() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater factory = LayoutInflater.from(this);
        final View view = factory.inflate(R.layout.review_thankyou, null);
        builder.setView(view);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dlg, int sumthin) {
                    setResult(RESULT_OK);
                    finish();
            }
        });

        Dialog dlg = builder.create();
        dlg.show();
        dlg.setCanceledOnTouchOutside(false);
    }
}