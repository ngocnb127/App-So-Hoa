package com.megatech.fms.helpers;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.LogEntryModel;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.List;

public class LogEntryAPI extends BaseAPI{

    public boolean postLogs(List<LogEntryModel> models)
    {
        String url = BuildConfig.API_BASE_URL + "api/log/list";
        String params = gson.toJson(models);
        try {
            HttpResponse response =  httpClient.sendPOST(url, params);
            return  response.getResponseCode() == HttpURLConnection.HTTP_OK;
        }
        catch (Exception ex)
        {

        }
        return  false;
    }

    public boolean postLogFile(String filePath) throws IOException {

        String url = BuildConfig.API_BASE_URL + "api/log";

        String twoHyphens = "--";
        String truckNo = FMSApplication.getApplication().getTruckNo();
        String boundary = "*****" + System.currentTimeMillis() + "*****";
        String CRLF = "\r\n";
        String charset = "UTF-8";
        File textFile = new File(filePath);

        HttpURLConnection con = httpClient.createConnection(url, "POST", "multipart/form-data; boundary=" + boundary);
        try (
                OutputStream output = con.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true)
        ) {
            // Send text file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"textFile\"; filename=\"" + truckNo + ".fms.log\"").append(CRLF);
            writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF); // Text file itself must be saved in this charset!
            writer.append(CRLF).flush();
            Files.copy(textFile.toPath(), output);
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
            writer.append("--" + boundary + "--").append(CRLF).flush();
        } catch (Exception ex) {
            return false;
        }

        int responseCode = con.getResponseCode();
        con.disconnect();
        return responseCode == 200;
    }
}
