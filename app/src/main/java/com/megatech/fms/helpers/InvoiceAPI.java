package com.megatech.fms.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.ReceiptModel;

import java.io.File;
import java.net.HttpURLConnection;

public class InvoiceAPI extends  BaseAPI{

    public InvoiceAPI()
    {
        url = BASE_URL + "/api/invoices";
    }

    public InvoiceModel post(InvoiceModel model)
    {
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            Logger.appendLog("InvoiceAPI", "Post Invoice: " + model.getInvoiceNumber());

            String parm = gson.toJson(model);
            HttpResponse response = httpClient.sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), InvoiceModel.class);
        }
        catch (Exception ex)
        {
            Logger.appendLog("InvoiceAPI", ex.getMessage());
        }
        return null;
    }


}
