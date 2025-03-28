package com.megatech.fms.helpers;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

public class ScreenshotAPI extends BaseAPI {
    public ScreenshotAPI() {
        url = BASE_URL + "/api/screenshot";
    }

    public boolean postScreenshot(File file) {
        try {
            HttpResponse response = this.httpClient.sendFile(this.url, file);
            return response.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception ex) {
            return false;
        }
    }
}
