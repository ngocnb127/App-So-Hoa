package com.megatech.fms.helpers;

import android.util.Log;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.TruckModel;

import java.net.HttpURLConnection;

public class TruckAPI extends BaseAPI{
    public TruckAPI()
    {
        url = BASE_URL + "/api/trucks";
    }
    public TruckModel getTruck()
    {
        return getTruck(FMSApplication.getApplication().getTruckId());
    }
    public TruckModel getTruck(int truckId)
    {
        try {

            Logger.appendLog("TruckAPI", "getTruck(int id)");
            HttpResponse response = httpClient.sendGET(url +"/"+ truckId);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), TruckModel.class);
        }
        catch (Exception ex)
        {
            Logger.appendLog("ReceiptAPI", ex.getMessage());
            //Log.e("ReceiptAPI: post(ReceiptModel)", e.getMessage());
        }
        return null;
    }

    public TruckModel postTruck(TruckModel model)
    {
        try {

            String parm = gson.toJson(model);
            HttpResponse response = httpClient.sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {

                return gson.fromJson(response.getData(), TruckModel.class);
            }
        } catch (Exception e) {
            Log.e("postRefuel", e.getMessage());

        }
        return null;
    }
}
