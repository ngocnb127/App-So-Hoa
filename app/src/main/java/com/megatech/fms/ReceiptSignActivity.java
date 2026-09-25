package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import com.megatech.fms.helpers.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReceiptSignActivity extends AppCompatActivity implements UpdateSensitiveScreen {

    private static final String LOG_TAG = "SIGN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_sign);

    }

    private File createSignatureFile() throws IOException {
        // Create an image file name

        String imageFileName = "JPEG_Signature";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents

        return image;
    }

    /**
     * Lưu chữ ký vừa vẽ.
     *
     * <p>Hai lỗi cũ được sửa ở đây:
     * <ul>
     *   <li>{@code gesture.getGesture()} trả null khi người dùng chưa vẽ nét nào, mà dòng
     *       {@code toBitmap} lại NẰM NGOÀI try ⇒ bấm LƯU trên màn hình trắng là CRASH. Nay
     *       kiểm tra null trước và chỉ báo "chưa có chữ ký".</li>
     *   <li>{@code finish()} nằm TRONG try và {@code catch} chỉ in stack trace ⇒ ghi file
     *       hỏng (hết dung lượng, không có quyền) thì màn hình ký TREO IM LẶNG: người dùng
     *       bấm LƯU mà không có gì xảy ra, cũng không có thông báo nào. Nay lỗi được báo ra
     *       và màn hình đóng lại với {@code RESULT_CANCELED} để bên gọi biết.</li>
     * </ul>
     */
    private void save()
    {
        GestureOverlayView gesture = (GestureOverlayView)findViewById(R.id.gesture);
        Bitmap newBmp;
        try {
            Gesture drawn = gesture == null ? null : gesture.getGesture();
            if (drawn == null) {
                Toast.makeText(this, R.string.sign_empty, Toast.LENGTH_LONG).show();
                return;
            }
            Bitmap bmp = drawn.toBitmap(300, 200, 8, Color.BLACK);
            newBmp = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
            Canvas canvas = new Canvas(newBmp);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bmp, 0, 0, null);
        } catch (Exception ex) {
            Logger.appendLog(LOG_TAG, "Không dựng được ảnh chữ ký: " + ex.getMessage());
            Toast.makeText(this, R.string.sign_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        File dest = null;
        try {
            dest = createSignatureFile();
            FileOutputStream out = new FileOutputStream(dest);
            newBmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            Intent data = new Intent();
            data.putExtra("signature_file", dest.getAbsolutePath());
            setResult(RESULT_OK,data);
            // Đếm tần suất: trước bản vá không có log nào ở đây nên không đo được thật.
            Logger.appendLog(LOG_TAG, "SIGN_SAVED path=" + dest.getAbsolutePath());
            finish();
        } catch (Exception e) {
            Logger.appendLog(LOG_TAG, "SIGN_SAVE_FAILED path="
                    + (dest == null ? "null" : dest.getAbsolutePath())
                    + " lỗi=" + e.getMessage());
            Toast.makeText(this, R.string.sign_save_failed, Toast.LENGTH_LONG).show();
            // finish() TRƯỚC ĐÂY nằm trong try nên không bao giờ chạy khi ghi file hỏng.
            setResult(RESULT_CANCELED);
            finish();
        }

    }
    public void onClick(View view) {

        int id = view.getId();
        switch (id)
        {
            case R.id.btnSave:
                save();
                break;
            case R.id.btnClear:
                ((GestureOverlayView)findViewById(R.id.gesture)).clear(false);
                break;
            case R.id.btnBack:
                finish();
                break;
        }
    }
}