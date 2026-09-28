package com.megatech.fms.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.megatech.fms.model.ReceiptModel;


import java.io.File;
import java.net.HttpURLConnection;


public class ReceiptAPI extends  BaseAPI{

    public ReceiptAPI()
    {
        url = BASE_URL + "/api/receipts";
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
    /**
     * Tải phiếu đã lưu trên server theo Id phiếu — {@code GET api/receipts/{id}}.
     *
     * <p>Endpoint này ĐÃ CÓ và chỉ đọc (đo 2026-09-16, phiếu Id 1795397 trả về đủ số phiếu,
     * ngày, chuyến, tàu bay, tổng lượng và từng mẻ). Nó thiếu {@code QualityNo} và
     * {@code IsFHS} vì phép chiếu phía server chưa gán hai trường đó.
     *
     * @return {@code null} khi mất mạng hoặc server không có phiếu.
     */
    public ReceiptModel getById(int receiptId) {
        if (receiptId <= 0) return null;
        try {
            HttpResponse response = httpClient.sendGET(url + "/" + receiptId);
            if (response != null && response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                ReceiptModel model = gson.fromJson(response.getData(), ReceiptModel.class);
                if (model != null && model.getNumber() != null && !model.getNumber().trim().isEmpty())
                    return model;
                Logger.appendLog("ReceiptAPI", "receipt " + receiptId + ": server trả phiếu rỗng");
                return null;
            }
            Logger.appendLog("ReceiptAPI", "receipt " + receiptId + ": HTTP "
                    + (response == null ? "null" : response.getResponseCode()));
        } catch (Exception ex) {
            Logger.appendLog("ReceiptAPI", "receipt " + receiptId + ": " + ex.getMessage());
        }
        return null;
    }

    /**
     * Tải phiếu đã lưu trên server theo số phiếu — để in lại phiếu của xe khác.
     *
     * <p>Cần endpoint {@code GET api/receipts/by-number?number=} phía server, chỉ đọc. Các
     * endpoint GET sẵn có của ReceiptsController đều không dùng được: {@code api/receipts/{id}}
     * cần Id server và thiếu Cert No./FHS; {@code api/receipt/image/{id}} GHI ĐÈ ảnh phiếu
     * {@code {số}.jpg} trên server mỗi lần gọi.
     *
     * @return {@code null} khi mất mạng, server chưa có endpoint, hoặc không có phiếu này.
     */
    public ReceiptModel getByNumber(String number) {
        if (number == null || number.trim().isEmpty()) return null;
        try {
            HttpResponse response = httpClient.sendGET(url + "/by-number?number="
                    + java.net.URLEncoder.encode(number.trim(), "UTF-8"));
            if (response != null && response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                ReceiptModel model = gson.fromJson(response.getData(), ReceiptModel.class);
                if (model != null && number.trim().equalsIgnoreCase(model.getNumber()))
                    return model;
                Logger.appendLog("ReceiptAPI", "by-number " + number + ": server trả phiếu khác/rỗng");
                return null;
            }
            Logger.appendLog("ReceiptAPI", "by-number " + number + ": HTTP "
                    + (response == null ? "null" : response.getResponseCode()));
        } catch (Exception ex) {
            Logger.appendLog("ReceiptAPI", "by-number " + number + ": " + ex.getMessage());
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
