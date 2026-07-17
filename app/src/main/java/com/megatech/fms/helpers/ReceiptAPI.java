package com.megatech.fms.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.ReceiptModel;

import java.io.File;
import java.net.HttpURLConnection;

public class ReceiptAPI extends  BaseAPI{

    public ReceiptAPI()
    {
        url = BASE_URL + "/api/receipts";
    }

    public ReceiptModel postMultipart(ReceiptModel model)
    {
        try {
            Logger.appendLog("ReceiptAPI", "Post Receipt: " + model.getNumber());
            //getPdfString(model);
            String parm = gson.toJson(model);
            FileUploader  uploader = new FileUploader(url+"/multipart","UTF-8");
            uploader.addFormField("Receipt-Data",parm);
            if (model.getPdfPath() !=null && !model.getPdfPath().isEmpty()) {
                File f = new File(model.getPdfPath());
                if (f.exists()) {
                    uploader.addFilePart("Receipt-Image", f);
                }
            }
            if (model.getSellerSignaturePath() !=null && !model.getSellerSignaturePath().isEmpty()) {
                File f = new File(model.getSellerSignaturePath());
                if (f.exists()) {
                    uploader.addFilePart("Seller-Signature", f);
                }
            }
            if (model.getSignaturePath() !=null && !model.getSignaturePath().isEmpty()) {
                File f = new File(model.getSignaturePath());
                if (f.exists()) {
                    uploader.addFilePart("Buyer-Signature", f);
                }
            }
            //model.setPdfImageString(null);
            HttpResponse response = uploader.finish();
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return gson.fromJson(response.getData(), ReceiptModel.class);
            }

        }
        catch (Exception ex)
        {
            Logger.appendLog("ReceiptAPI 1", ex.getMessage());
        }
        return null;
    }
    public BM2508Model postMultipartBM2508(BM2508Model model)
    {
        try {
            String parm = gson.toJson(model);
            FileUploader  uploader = new FileUploader(BASE_URL + "/api/bm2508" + "/multipart","UTF-8");
            uploader.addFormField("BM2508-Data",parm);
            if (model.getPdfPath() !=null && !model.getPdfPath().isEmpty()) {
                File f = new File(model.getPdfPath());
                if (f.exists()) {
                    uploader.addFilePart("BM2508-Image", f);
                }
            }
//            if (model.getAirlineSignaturePath(null) !=null && !model.getAirlineSignaturePath(null).isEmpty() ) {
//                File f = new File(model.getAirlineSignaturePath(null));
//                if (f.exists()) {
//                    uploader.addFilePart("Airline-Signature", f);
//                }
//            }

            String airlineImageUrl = model.getUrlImageAirline();
            String airlineSignaturePath = model.getAirlineSignaturePath();

            if (airlineSignaturePath == null || airlineImageUrl.equals(airlineSignaturePath)) {
                File file = new File(airlineImageUrl);
                if (file.exists()) {
                    try {
                        uploader.addFilePart("Airline-Signature", file);
                    } catch (Exception e) {
                        e.printStackTrace(); // Log or handle the error appropriately
                    }
                }
            }

            String UrlImageSkypec = model.getUrlImageSkypec();
            String UserSkypecSignaturePath = model.getUserSkypecSignaturePath();

            if (UserSkypecSignaturePath == null || UrlImageSkypec.equals(UserSkypecSignaturePath)) {
                File file = new File(UrlImageSkypec);
                if (file.exists()) {
                    try {
                        uploader.addFilePart("UserSkypec-Signature", file);
                    } catch (Exception e) {
                        e.printStackTrace(); // Log or handle the error appropriately
                    }
                }
            }

//            if (model.getUserSkypecSignaturePath() !=null && !model.getUserSkypecSignaturePath().isEmpty()) {
//                File f = new File(model.getUserSkypecSignaturePath());
//                if (f.exists()) {
//                    uploader.addFilePart("UserSkypec-Signature", f);
//                }
//            }
            //model.setPdfImageString(null);
            HttpResponse response = uploader.finish();
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return gson.fromJson(response.getData(), BM2508Model.class);
            }

        }
        catch (Exception ex)
        {
            Logger.appendLog("BM2508-1", ex.getMessage());
        }
        return null;
    }
    public ReceiptModel post(ReceiptModel model)
    {
        try {
            Logger.appendLog("ReceiptAPI3", "Post Receipt: " + model.getNumber());
            getPdfString(model);
            String parm = gson.toJson(model);
            model.setPdfImageString(null);
            HttpResponse response = httpClient.sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                Logger.appendLog("ReceiptAPI4", "Post Receipt: " + model.getNumber() + " OK");
                return gson.fromJson(response.getData(), ReceiptModel.class);
            }

        }
        catch (Exception ex)
        {
            Logger.appendLog("ReceiptAPI5", ex.getMessage());
        }
        return null;
    }
    private  int MAX_WIDTH = 800;
    private int MAX_SIGN_WIDTH = 200;

    private void getPdfString(ReceiptModel model)
    {
        try {
            if (model.getPdfPath() !=null && !model.getPdfPath().isEmpty()) {
                File f = new File(model.getPdfPath());
                if (f.exists()) {
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;

                    BitmapFactory.decodeFile(model.getPdfPath(), bmOptions);
                    int height = bmOptions.outHeight;
                    int width = bmOptions.outWidth;
                    int sampleSize = 1 / MAX_WIDTH;
                    bmOptions.inJustDecodeBounds = false;
                    bmOptions.inSampleSize = sampleSize;//scaleFactor;

                    Bitmap src = BitmapFactory.decodeFile(model.getPdfPath(), bmOptions);
                    Bitmap bitmap = src;
                    if (bitmap.getWidth() > MAX_WIDTH) {
                        height = bitmap.getHeight() * MAX_WIDTH / bitmap.getWidth();
                        bitmap = Bitmap.createScaledBitmap(src, MAX_WIDTH,height,false );
                    }
                    model.setPdfImageString(ImageUtil.convert(bitmap));
                }
            }
            if (model.getSignaturePath() !=null && !model.getSignaturePath().isEmpty()) {
                File f = new File(model.getSignaturePath());
                if (f.exists()) {
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;

                    BitmapFactory.decodeFile(model.getSignaturePath(), bmOptions);
                    int height = bmOptions.outHeight;
                    int width = bmOptions.outWidth;
                    int sampleSize = 1 / MAX_SIGN_WIDTH;
                    bmOptions.inJustDecodeBounds = false;
                    bmOptions.inSampleSize = sampleSize;//scaleFactor;

                    Bitmap bitmap = BitmapFactory.decodeFile(model.getSignaturePath(), bmOptions);
                    model.setSignImageString(ImageUtil.convert(bitmap));
                }
            }

            if (model.getSellerSignaturePath() !=null && !model.getSellerSignaturePath().isEmpty()) {
                File f = new File(model.getSellerSignaturePath());
                if (f.exists()) {
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;

                    BitmapFactory.decodeFile(model.getSellerSignaturePath(), bmOptions);
                    int height = bmOptions.outHeight;
                    int width = bmOptions.outWidth;
                    int sampleSize = 1 / MAX_SIGN_WIDTH;
                    bmOptions.inJustDecodeBounds = false;
                    bmOptions.inSampleSize = sampleSize;//scaleFactor;

                    Bitmap bitmap = BitmapFactory.decodeFile(model.getSellerSignaturePath(), bmOptions);
                    model.setSellerImageString(ImageUtil.convert(bitmap));
                }
            }
        }
        catch (Exception ex)
        {
            Logger.appendLog("ReceiptAPI6", ex.getMessage());
        }
    }
}
