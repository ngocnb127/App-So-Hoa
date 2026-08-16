package com.megatech.fms;

import static com.megatech.fms.BuildConfig.API_BASE_URL;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.telecom.Call;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;

import com.google.common.net.MediaType;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.megatech.fms.databinding.ActivityInvoiceBinding;
import com.megatech.fms.databinding.B2508FormBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.helpers.HttpResponse;
import com.megatech.fms.helpers.ImageUtil;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.ReceiptAPI;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.view.BM2505ArrayAdapter;
import com.megatech.fms.view.BM2508ArrayAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

public class B2508Activity extends DateBaseActivity implements View.OnClickListener, UpdateSensitiveScreen {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_b2508);
        loaddata();
    }

    @Override
    public void onClick(View view) {
        int id=view.getId();
        switch (id)
        {
            case R.id.btnBack:
                finish();
                break;
            case R.id.btnNew:
                openNew();
                break;
            case R.id.btnDelete:
                openDelete();
                break;
            case R.id.b2508_date:
                showDateDialog();
            default:
                break;
        }
    }
    public BM2508Model modelb2508 = null;
    private B2508FormItemFragement b2508FormItemFragement;
    private void openEdit( BM2508Model model) {

        FragmentManager fm = getSupportFragmentManager();
        B2508NewItemFragement newItemFragement = new B2508NewItemFragement(model);
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }

    private void openNew() {

        FragmentManager fm = getSupportFragmentManager();
        B2508NewItemFragement newItemFragement = new B2508NewItemFragement();
        newItemFragement.show(fm, "fragment_edit_name");
        loaddata();
    }
    public void openFormDetail(BM2508Model model) {

        FragmentManager fm = getSupportFragmentManager();
        B2508FormItemFragement detailItemFragement = new B2508FormItemFragement(model);
        b2508FormItemFragement = detailItemFragement;
        detailItemFragement.show(fm, "fragment_detail_name");
        loaddata();
        modelb2508 = model;
    }
    private  void sendScreenshot()
    {
        Bitmap b = takeScreenshot();
        File f = saveBitmap(b);
        Logger.appendLog("RECEIPT", "screenshot file " + f.getName());

    }
    private File saveBitmap(Bitmap bitmap) {
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ;
        File imagePath = null;
        try {
            imagePath = new File(folder,"screenshot_"+ modelb2508.getNumber()+".jpg");
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
    private Bitmap takeScreenshot() {
        View rootView = findViewById(android.R.id.content).getRootView();
        rootView.setDrawingCacheEnabled(true);
        return rootView.getDrawingCache();
    }
    private boolean autoNumber = true;
    private boolean savingBM2508;
    private void save() {
        if (savingBM2508) return;
        savingBM2508 = true;
        Logger.appendLog("RECEIPT_WINDOW", "save receipt " + modelb2508.getNumber());
        setProgressDialog();
        sendScreenshot();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                DataHelper.postBM2508(modelb2508);
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
        savingBM2508 = false;
        closeProgressDialog();
        Logger.appendLog("RECEIPT_WINDOW", "save receipt completed " + modelb2508.getNumber());
        Intent returnIntent = new Intent();
        returnIntent.putExtra("number", modelb2508.getNumber());
        returnIntent.putExtra("uniqueId", modelb2508.getUniqueId());
        setResult(Activity.RESULT_OK, returnIntent);
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
    private final int Airline = 445;
    private final int UserSkypec = 446;
    String currentPhotoPath;

    B2508FormBinding binding;
    boolean exitAfterCapture = false;
    private final int REQUEST_IMAGE_CAPTURE = 1;
    public void openSignature(boolean sign) {
        Intent intent = new Intent(this, ReceiptSignActivity.class);
        startActivityForResult(intent, sign ? Airline : UserSkypec);

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
            modelb2508.setPdfPath(currentPhotoPath);
            modelb2508.setCaptured(true);
            binding.invalidateAll();
            if (exitAfterCapture)
                save();
        }


        else if (requestCode == Airline && resultCode == RESULT_OK) {
            String file = data.getExtras().getString("signature_file");
            modelb2508.setAirlineSignaturePath(file);
            modelb2508.setUrlImageAirline(file);
            ReceiptAPI client = new ReceiptAPI();
            // save();
            ImageView signatureImageView = b2508FormItemFragement.getView().findViewById(R.id.ImageAirlineSign);

            if (signatureImageView != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(file);

                if (bitmap != null) {
                    signatureImageView.setImageBitmap(bitmap);
                    signatureImageView.setVisibility(View.VISIBLE);
                    //saveImageToGallery(b2508FormItemFragement.getContext(), bitmap, "airline_image.jpg");
                }
            }
            b2508FormItemFragement.model.setAirlineSignaturePath(file);
            modelb2508.setTextAirlineSignature("Chạm để ký");
            binding.invalidateAll();

            // Gửi file ảnh lên API
            client.postMultipartBM2508(modelb2508);
        }
        else if (requestCode == UserSkypec && resultCode == RESULT_OK) {
            String file = data.getExtras().getString("signature_file");
            modelb2508.setUserSkypecSignaturePath(file);
            modelb2508.setUrlImageSkypec(file);
            // save();
            ImageView signatureImageView = b2508FormItemFragement.getView().findViewById(R.id.ImageUserSkypecSign);

            if (signatureImageView != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(file);

                if (bitmap != null) {
                    signatureImageView.setImageBitmap(bitmap);
                    signatureImageView.setVisibility(View.VISIBLE);
                    //saveImageToGallery(b2508FormItemFragement.getContext(), bitmap, "userSkypec_image.jpg");
                }
            }
            b2508FormItemFragement.model.setUserSkypecSignaturePath(file);
            modelb2508.setTextUserSkypecSignature("Chạm để ký");
            binding.invalidateAll();
            ReceiptAPI client = new ReceiptAPI();
            // Gửi file ảnh lên API
            client.postMultipartBM2508(modelb2508);
        }
    }

    public void saveImageToGallery(Context context, Bitmap bitmap, String fileName,boolean isAirlineSignature) {
        OutputStream fos;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

            // Thêm ảnh vào MediaStore (thư viện ảnh)
            android.net.Uri imageUri = context.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri != null) {
                fos = context.getContentResolver().openOutputStream(imageUri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
                Log.d("BM2508Model", "Airline Signature Gallery Path: " + modelb2508.getAirlineSignatureGalleryPath());
                // Lưu đường dẫn ảnh vào modelb2508
                if (isAirlineSignature) {
                    modelb2508.setAirlineSignatureGalleryPath(imageUri.toString());
                } else {
                    modelb2508.setUserSkypecSignatureGalleryPath(imageUri.toString());
                }
                Log.d("BM2508Model", modelb2508.toJson());
                Toast.makeText(context, "Ảnh đã lưu vào thư viện!", Toast.LENGTH_SHORT).show();


            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Lưu ảnh thất bại!", Toast.LENGTH_SHORT).show();
        }
    }
    private void openDelete() {
        ListView lv = (ListView)findViewById(R.id.b2508_list);
        List<Integer> list = ((BM2508ArrayAdapter)lv.getAdapter()).getCheckedItems();
        if(list.size()>0) {
            int[] ids = new int[list.size()];
            for (int i=0;i<list.size(); i++)
                ids[i] = list.get(i);

            showConfirmMessage(R.string.delete_confirm, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    setProgressDialog();
                    //String mData = b.getString("REFUEL", "");
                    new AsyncTask<Void, Void, List<BM2508Model>>() {
                        @Override
                        protected List<BM2508Model> doInBackground(Void... voids) {
                            DataHelper.deleteBM2508(ids);
                            List<BM2508Model> lst = DataHelper.getBM2508List(selectedDate);
                            return lst;
                        }

                        @Override
                        protected void onPostExecute(List<BM2508Model> models) {
                            dataList = models;
                            bindData();
                        }
                    }.execute();
                    return  null;
                }
            });
        }
    }

    private List<BM2508Model> dataList;
    public List<FlightModel> flightList = null;
    public List<AirportsModel> airportslist = null;
    public List<TruckModel> Trucklist = null;
    @Override
    public void loaddata() {
        setProgressDialog();
        //String mData = b.getString("REFUEL", "");
        new AsyncTask<Void, Void, List<BM2508Model>>() {
            @Override
            protected List<BM2508Model> doInBackground(Void... voids) {
                userList = DataHelper.getUsers();
                flightList =  DataHelper.getFlights();
                airportslist =  DataHelper.getAirports();
                Trucklist =  DataHelper.getTrucks();
                List<BM2508Model> lst = DataHelper.getBM2508List(selectedDate);
                return lst;
            }

            @Override
            protected void onPostExecute(List<BM2508Model> models) {
                dataList = models;
                bindData();
            }
        }.execute();
    }

    @Override
    public void bindData() {
        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.b2508_form, null, false);
        BM2508ArrayAdapter adapter = new BM2508ArrayAdapter(this, dataList);
        ((ListView)findViewById(R.id.b2508_list)).setAdapter(adapter);

        ((ListView)findViewById(R.id.b2508_list)).setOnItemClickListener((adapterView, view, i, l) -> {
            BM2508Model model = (BM2508Model)adapterView.getItemAtPosition(i);
            openEdit(model);
        });
        ((TextView)findViewById(R.id.b2508_truck_no)).setText(currentApp.getTruckNo());
        ((TextView)findViewById(R.id.b2508_date)).setText(DateUtils.formatDate(selectedDate,"dd/MM/yyyy"));

        closeProgressDialog();
    }
}
