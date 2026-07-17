package com.megatech.fms.helpers;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.BuildConfig;

public abstract class BaseAPI {

    protected  String BASE_URL = BuildConfig.API_BASE_URL;
    protected  HttpClient httpClient = new HttpClient();
    protected  String url;
    protected Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

}
