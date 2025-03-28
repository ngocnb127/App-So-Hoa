package com.megatech.fms.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.ReviewModel;

import java.io.File;
import java.net.HttpURLConnection;

public class ReviewAPI extends BaseAPI{
    public ReviewAPI() {
        url = BASE_URL + "/api/review";
    }

    public ReviewModel postReview(ReviewModel model)
    {
        try{
            getImageString(model);
            String parm = gson.toJson(model);
            model.setImageString(null);
            HttpResponse response = httpClient.sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return gson.fromJson(response.getData(), ReviewModel.class);
            }
        }
        catch (Exception ex)
        {
            Logger.appendLog("REVIEW_API",ex.getMessage());
        }
        return null;
    }
    private int MAX_WIDTH = 600;
    private void getImageString(ReviewModel model)
    {
        try {
            if (model.getImagePath() !=null && !model.getImagePath().isEmpty()) {
                File f = new File(model.getImagePath());
                if (f.exists()) {
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;

                    BitmapFactory.decodeFile(model.getImagePath(), bmOptions);
                    int height = bmOptions.outHeight;
                    int width = bmOptions.outWidth;
                    int sampleSize = width / MAX_WIDTH;
                    bmOptions.inJustDecodeBounds = false;
                    bmOptions.inSampleSize = sampleSize;//scaleFactor;

                    Bitmap src = BitmapFactory.decodeFile(model.getImagePath(), bmOptions);
                    Bitmap bitmap = src;
                    if (bitmap.getWidth() > MAX_WIDTH) {
                        height = bitmap.getHeight() * MAX_WIDTH / bitmap.getWidth();
                        bitmap = Bitmap.createScaledBitmap(src, MAX_WIDTH,height,false );
                    }
                    model.setImageString(ImageUtil.convert(bitmap));
                }
            }

        }
        catch (Exception ex)
        {
            Logger.appendLog("ReviewAPI", ex.getMessage());
        }
    }
}
