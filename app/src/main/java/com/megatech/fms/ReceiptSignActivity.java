package com.megatech.fms;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.gesture.GestureOverlayView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReceiptSignActivity extends AppCompatActivity {

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

    private void save()
    {
        GestureOverlayView gesture = (GestureOverlayView)findViewById(R.id.gesture);
        Bitmap bmp = gesture.getGesture().toBitmap(300,200,8, Color.BLACK);
        Bitmap newBmp =  Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
        Canvas canvas = new Canvas(newBmp);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bmp,0,0,null);


        try {
            File dest = createSignatureFile();
            FileOutputStream out = new FileOutputStream(dest);
            newBmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            Intent data = new Intent();
            data.putExtra("signature_file", dest.getAbsolutePath());
            setResult(RESULT_OK,data);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
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