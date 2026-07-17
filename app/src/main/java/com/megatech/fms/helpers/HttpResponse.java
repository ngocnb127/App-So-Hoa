package com.megatech.fms.helpers;

public class HttpResponse {
    public HttpResponse(int responseCode, String data) {
        this.responseCode = responseCode;
        this.data = data;
    }

    private int responseCode;
    private String data;

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}