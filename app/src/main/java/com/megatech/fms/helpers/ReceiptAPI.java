package com.megatech.fms.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.ReceiptModel;

import com.megatech.fms.FMSApplication;

import java.io.File;
import java.net.HttpURLConnection;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReceiptAPI extends  BaseAPI{

    public ReceiptAPI()
    {
        url = BASE_URL + "/api/receipts";
    }

    /** Kết quả một lượt gửi ảnh — tầng trên cần biết có nên thử lại hay không. */
    public enum AttachmentOutcome {
        /** Server đã nhận. */
        SENT,
        /** Lỗi tạm (mạng, 5xx): gửi lại lượt sau có thể thành công. */
        RETRY_LATER,
        /** Gửi lại y hệt cũng hỏng (chưa có Id, hoặc server chê nội dung): dừng thử lại. */
        GIVE_UP
    }

    public BM2508Model postMultipartBM2508(BM2508Model model) {
        return send(model) == AttachmentOutcome.SENT ? model : null;
    }

    /**
     * Gửi ảnh và chữ ký của một phiếu BM2508 đã có trên server.
     *
     * <p>Bắt buộc phải có {@code Id} do server cấp: endpoint đính kèm gắn tệp vào một phiếu đã
     * tồn tại, không tạo phiếu mới. Gửi khi {@code Id = 0} thì server trả
     * {@code HTTP 400 "Missing or invalid BM2508 id"} — đúng lỗi ghi nhận trên xe sáng
     * 25-08-2026, lặp lại mỗi vòng đồng bộ vì hàng đợi chỉ xoá cờ chờ khi thành công.
     */
    public AttachmentOutcome send(BM2508Model model) {
        if (model == null) return AttachmentOutcome.GIVE_UP;

        // Chốt chặn cuối, đặt ở đây chứ không ở từng chỗ gọi: có ba đường gửi ảnh (ký hãng,
        // ký Skypec, lưu form) và cả ba đều chạy được trước khi phiếu kịp lên server.
        if (model.getId() == null || model.getId() <= 0) {
            Logger.appendLog("BM2508-1", "Chưa gửi ảnh BM2508: phiếu chưa có Id từ server"
                    + " (số phiếu " + describe(model) + "). Ảnh sẽ gửi sau khi phiếu được đồng bộ.");
            return AttachmentOutcome.GIVE_UP;
        }

        String uploadUrl = BASE_URL + "/api/bm2508/multipart";
        try {
            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("BM2508-Data", gson.toJson(model));

            addFilePart(bodyBuilder, "BM2508-Image", model.getPdfPath());
            addFilePart(bodyBuilder, "Airline-Signature", model.getAirlineSignaturePath());
            addFilePart(bodyBuilder, "UserSkypec-Signature", model.getUserSkypecSignaturePath());

            Request.Builder requestBuilder = new Request.Builder()
                    .url(uploadUrl)
                    .post(bodyBuilder.build());

            // Thiếu header này chính là lỗi cũ. Token null thì vẫn gửi (server có thể đang tắt
            // xác thực), nhưng phải ghi log để không âm thầm quay lại tình trạng 401 hàng loạt.
            String token = currentToken();
            if (token != null && !token.isEmpty())
                requestBuilder.addHeader("Authorization", "Bearer " + token);
            else
                Logger.appendLog("BM2508-1", "Không có token khi gửi ảnh BM2508");

            Response response = new OkHttpClient().newCall(requestBuilder.build()).execute();
            String body = response.body() != null ? response.body().string() : "";

            if (response.isSuccessful())
                return AttachmentOutcome.SENT;

            // Phân biệt lỗi xác thực với lỗi mạng: tầng trên còn biết có nên thử lại hay không.
            //
            // Ghi cả THÂN phản hồi. Trước đây chỉ ghi mã lỗi, và mã lỗi một mình không nói
            // được gì: đo trên máy thật 18-08, gói này trả HTTP 400 lặp lại mỗi 30 giây suốt
            // nhiều giờ mà không ai biết server chê chỗ nào — 400 nghĩa là xác thực ĐÃ QUA,
            // server từ chối nội dung, nên chỉ thân phản hồi mới chỉ ra được trường nào sai.
            Logger.appendLog("BM2508-1", (response.code() == 401 ? "SAI XÁC THỰC" : "Lỗi")
                    + " khi gửi ảnh BM2508: HTTP " + response.code()
                    + " (phiếu " + describe(model) + ")"
                    + " - " + shorten(body));

            // 4xx là server chê NỘI DUNG, không phải trục trặc đường truyền: gửi lại đúng gói
            // đó sẽ lại hỏng y hệt. Trước đây mọi lỗi đều được coi là tạm nên hàng đợi quay
            // vòng vô hạn — sáng 25-08 ghi 7 lượt 400 trong 1 giây, và lặp lại mỗi vòng đồng bộ.
            // Riêng 401 vẫn thử lại: token hết hạn rồi đăng nhập lại là gửi được.
            boolean clientError = response.code() >= 400 && response.code() < 500;
            return clientError && response.code() != 401
                    ? AttachmentOutcome.GIVE_UP
                    : AttachmentOutcome.RETRY_LATER;
        } catch (Exception ex) {
            Logger.appendLog("BM2508-1", "Lỗi mạng khi gửi ảnh BM2508 (phiếu "
                    + describe(model) + "): " + ex.getMessage());
        }
        return AttachmentOutcome.RETRY_LATER;
    }

    /** Nhãn nhận dạng phiếu cho log: có gì dùng nấy, để dòng log tự giải thích được. */
    private static String describe(BM2508Model model) {
        if (model == null) return "?";

        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(model.getId());
        if (model.getNumber() != null && !model.getNumber().isEmpty())
            sb.append(" số=").append(model.getNumber());
        if (model.getFlightCode() != null && !model.getFlightCode().isEmpty())
            sb.append(" chuyến=").append(model.getFlightCode());
        return sb.toString();
    }

    /**
     * Cắt bớt thân phản hồi trước khi ghi log.
     *
     * <p>Server .NET trả kèm nguyên stack trace vài nghìn ký tự; ghi trọn vẹn thì một lỗi
     * lặp lại mỗi 30 giây sẽ nuốt cả file log. Phần đầu chứa Message và ExceptionMessage —
     * đúng phần cần để biết trường nào sai.
     */
    private static String shorten(String body) {
        if (body == null || body.isEmpty()) return "(thân phản hồi rỗng)";
        String text = body.replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    /** Chỉ đính tệp khi đường dẫn có thật và tệp còn tồn tại trên đĩa. */
    private void addFilePart(MultipartBody.Builder builder, String partName, String path) {
        if (path == null || path.trim().isEmpty()) return;

        File file = new File(path);
        if (!file.exists()) return;

        builder.addFormDataPart(partName, file.getName(),
                RequestBody.create(file, MediaType.parse("application/octet-stream")));
    }

    private String currentToken() {
        try {
            return FMSApplication.getApplication().getUser().getToken();
        } catch (Exception ex) {
            return null;
        }
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
