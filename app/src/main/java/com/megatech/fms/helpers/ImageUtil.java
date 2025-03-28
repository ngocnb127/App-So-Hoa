package com.megatech.fms.helpers;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

public class ImageUtil
{
    public static Bitmap convert(String base64Str) throws IllegalArgumentException
    {
        byte[] decodedBytes = Base64.decode(
                base64Str.substring(base64Str.indexOf(",")  + 1),
                Base64.DEFAULT
        );

        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    public static String convert(Bitmap bitmap)
    {
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);

            return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
        }
        catch (Exception ex)
        {
            Logger.appendLog("ImageUtil",ex.getMessage());
            return null;
        }
    }

    public static Bitmap resize(Bitmap bitmap, int newWidth)
    {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        double ratio = (double) newWidth/width;
        int newHeight = (int)( height * ratio);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,newWidth,newHeight,false);
        return  scaledBitmap;
    }


}