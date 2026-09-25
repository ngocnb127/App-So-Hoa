package com.megatech.fms.helpers;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.data.entity.ParkingLot;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2503Model;
import com.megatech.fms.model.BM2504Model;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2506Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.BM2509Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightData;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.LoginResultModel;
import com.megatech.fms.model.ProductModel;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.ShiftModel;
import com.megatech.fms.model.TruckFuelModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.MultipartBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;



public class HttpClient {
    private final String API_BASE_URL = BuildConfig.API_BASE_URL;
    private String token;
    private TruckModel setting;
    private final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    public HttpClient() {
        this.token = FMSApplication.getApplication().getUser().getToken();
        this.setting = FMSApplication.getApplication().getSetting();
    }

    public HttpClient(String token) {
        this.token = token;
    }


    public LoginResultModel login(String username, String password) {
        String loginUrl = API_BASE_URL + "/token";
        String contentType = "application/x-www-form-urlencoded";
        LoginResultModel resultModel = new LoginResultModel();
        resultModel.setErrorType(LoginResultModel.LOGIN_ERROR_TYPE.CONNECTION_ERROR);
        try {
            String param = "grant_type=password&username=" + username + "&password=" + password;
            HttpResponse response = sendPOST(loginUrl, param, contentType);// executeUrl(loginUrl,contentType,"POST", "grant_type=password&username="+username+"&password="+password);

            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try {
                    JSONObject loginData = new JSONObject(response.getData());
                    resultModel.setUserId(loginData.getInt("userId"));
                    resultModel.setAirportId(loginData.getInt("airportId"));
                    resultModel.setUserName(loginData.getString("userName"));
                    resultModel.setAccess_token(loginData.getString("access_token"));
                    resultModel.setPermission(loginData.getInt("permission"));
                    resultModel.setAirport(loginData.getString("airport"));
                    resultModel.setAddress(loginData.getString("address"));
                    resultModel.setTaxCode(loginData.getString("taxcode"));
                    resultModel.setInvoiceName(loginData.getString("invoiceName"));
                    resultModel.setErrorType(null);

                } catch (Exception e) {

                    Log.e("LOGIN", e.getMessage());

                }
            } else if (response.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST)
                resultModel.setErrorType(LoginResultModel.LOGIN_ERROR_TYPE.CONNECTION_ERROR.DATA_ERROR);

        } catch (IOException e) {
            Log.e("ERROR", e.getMessage());
        }
        return resultModel;
    }

    public boolean putRefuelData(LCRDataModel dataModel) {
        return true;
    }

    public FlightData getFlightData(Integer id) {
        String url = API_BASE_URL + "api/flights/";
        String contentType = "application/json";
        try {
            url = url + id.toString();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                try {
                    JSONObject json = new JSONObject(data);
                    if (json.has("AircraftCode")) {
                        FlightData flight = new FlightData();
                        flight.setAircraftCode(json.getString("AircraftCode"));
                        flight.setCode(json.getString("Code"));
                        flight.setRouteName(json.getString("RouteName"));
                        flight.setParkingLot(json.getString("ParkingLot"));
                        return flight;
                    }
                } catch (JSONException e) {
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    public RefuelItemData getRefuelItem(Integer id) {
        String url = API_BASE_URL + "api/refuels/";
        try {
            url = url + id.toString();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                try {
                    //JSONObject json = new JSONObject(data);

                    RefuelItemData item = gson.fromJson(data, RefuelItemData.class);
                    item.setRawJson(data);
                    return item;
                } catch (Exception e) {
                    return null;

                }
            }

        } catch (Exception e) {

        }
        return null;
    }

    public List<UserModel> getUsers() {
        String url = API_BASE_URL + "api/users";

        List<UserModel> lst = new ArrayList<UserModel>();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                JSONArray arr = new JSONArray(data);
                Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
                lst = Arrays.asList(gson.fromJson(data, UserModel[].class));
            }
        } catch (Exception ex) {

        }
        return lst;
    }

    public List<TruckModel> getTrucks() {
        String url = API_BASE_URL + "api/trucks";

        List<TruckModel> lst = new ArrayList<TruckModel>();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                //JSONArray arr = new JSONArray(data);
                //Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
                lst = Arrays.asList(gson.fromJson(data, TruckModel[].class));
            }
        } catch (Exception ex) {

        }
        return lst;
    }

    /**
     * Nội dung của URL, hoặc null nếu không lấy được.
     *
     * sendGET() trả về chuỗi giữ chỗ ("Not Authorized", "GET request not worked") cho các
     * mã lỗi HTTP, nên nếu trả thẳng ra thì phía gọi không phân biệt được "server trả 404"
     * với "server trả đúng nội dung này". Ở đây chỉ trả nội dung khi thực sự HTTP 200.
     */
    public String getContent(String url) {
        try {
            HttpResponse response = sendGET(url);
            if (response == null) return null;
            if (response.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            return response.getData();
        } catch (IOException ex) {
            return null;
        }
    }

    public HttpResponse sendGET(String url) throws IOException {
        try {

            HttpURLConnection con = createConnection(url, "GET", "*/*");
            if (con == null)
                return null;

            int responseCode = con.getResponseCode();
            String responseData;
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                con.disconnect();
                // print result

                responseData = response.toString();
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                responseData = "Not Authorized";
            } else {

                responseData = "GET request not worked";
            }
            return new HttpResponse(responseCode, responseData);
        } catch (SocketTimeoutException ex) {
            return new HttpResponse(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, "socket timeout");
        } catch (IOException ex) {
            return new HttpResponse(HttpURLConnection.HTTP_BAD_GATEWAY, "IO Error");
        }
    }

    /** Reads a binary response entirely in memory; no application file is created. */
    public byte[] sendGETBytes(String url, String acceptType) throws IOException {
        HttpURLConnection con = createConnection(url, "GET", acceptType);
        if (con == null) return null;
        con.setRequestProperty("Accept", acceptType);
        try {
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream input = new BufferedInputStream(con.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return output.toByteArray();
            }
        } finally {
            con.disconnect();
        }
    }

    public HttpResponse sendPOST(String url, String params) throws IOException {
        return sendPOST(url, params, "application/json; utf-8");
    }

    public HttpResponse sendPOST(String url, String params, String contentType) throws IOException {
        try {

            HttpURLConnection con = createConnection(url, "POST", contentType);
            if (con == null)
                return null;

            //con.setReadTimeout(5000);
            // For POST only - START
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(params.getBytes());
            os.flush();
            os.close();

            // For POST only - END

            int responseCode = con.getResponseCode();
            //Logger.appendLog("HTTP", "sendPost: " + "POST Response Code :: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { //success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                con.disconnect();
                // print result
                return new HttpResponse(responseCode, response.toString());
            } else {
                return new HttpResponse(responseCode, null);
            }
        } catch (SocketTimeoutException ex) {
            return new HttpResponse(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, null);
        }
    }


    String crlf = "\r\n";
    String twoHyphens = "--";
    String boundary =  "*****";
    public HttpResponse sendFile(String url, File file) throws IOException {
        try {
            String crlf = "\r\n";
            String twoHyphens = "--";
            String boundary =  "*****";
            HttpURLConnection con = createConnection(url, "POST",  "multipart/form-data;boundary=" + boundary);
            if (con == null)
                return null;
            // For POST only - START
            con.setDoOutput(true);
            DataOutputStream os = new DataOutputStream(con.getOutputStream());
            os.writeBytes(twoHyphens + boundary + crlf);
            os.writeBytes("Content-Disposition: form-data; name=\"" +
                    file.getName() + "\";filename=\"" +
                    file.getName() + "\"" + crlf);
            os.writeBytes( crlf);
            int size = (int) file.length();
            byte[] bytes = new byte[size];
            try {
                BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
                buf.read(bytes, 0, bytes.length);
                buf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            os.write(bytes);
            os.writeBytes(crlf);
            os.writeBytes(twoHyphens + boundary +
                    twoHyphens + crlf);
            os.flush();
            os.close();

            // For POST only - END

            int responseCode = con.getResponseCode();
            //Logger.appendLog("HTTP", "sendPost: " + "POST Response Code :: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { //success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                con.disconnect();
                // print result
                return new HttpResponse(responseCode, response.toString());
            } else {
                return new HttpResponse(responseCode, null);
            }
        } catch (SocketTimeoutException ex) {
            return new HttpResponse(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, null);
        }
    }
    public String executeUrl(String targetURL, String contentType, String method, String urlParameters) {
        int timeout = 5000;
        URL url;
        HttpURLConnection connection = null;
        try {
            // Create connection
            url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type", contentType);

            connection.setRequestProperty("Content-Length",
                    "" + urlParameters.getBytes().length);
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            // Send request
            DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Get Response
                InputStream is = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                return response.toString();
            } else
                return connection.getResponseMessage();

        } catch (SocketTimeoutException ex) {
            ex.printStackTrace();

        } catch (MalformedURLException ex) {
            //Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException ex) {

            //Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    public ShiftModel getShift() {
        String url = API_BASE_URL + "api/shift";
        try {
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                ShiftModel model = gson.fromJson(data, ShiftModel.class);
                model.setSelected(true);

                return model;
            }
        } catch (Exception e) {
            Log.e("getShift", e.getMessage());
        }
        return null;
    }

    public List<RefuelItemData> getRefuelList() {
        return getRefuelList(false, 0);
    }

    public List<RefuelItemData> getRefuelList(boolean others) {
        return getRefuelList(others, 0);
    }

    public List<RefuelItemData> getRefuelList(boolean others, Integer t) {
        return getRefuelList(others, t, false);
    }

    public List<RefuelItemData> getRefuelList(boolean others, Integer t, boolean d) {

        String url = API_BASE_URL + "api/refuels?truckNo="
                + FMSApplication.getApplication().getTruckNo()
                + "&truckId="
                + FMSApplication.getApplication().getTruckId()
                + "&o=" + (others ? "1" : "0") + "&type=" + t.toString() + "&d=" + d;

        List<RefuelItemData> lst = new ArrayList<RefuelItemData>();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
                if (arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        RefuelItemData item;

                        item = gson.fromJson(o.toString(), RefuelItemData.class);
                        lst.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("getRefuelList", e.getMessage());
            return null;
        }
        return lst;

    }

    public List<RefuelItemData> getModifiedRefuels(Integer t, Date lastModified) {

        String url = API_BASE_URL + "api/refuels/modified?type=" + t.toString();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSS");
        if (lastModified != null)
            url += "&lastModified=" + dateFormat.format(lastModified);

        List<RefuelItemData> lst = new ArrayList<RefuelItemData>();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                //Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
                if (arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        RefuelItemData item;

                        item = gson.fromJson(o.toString(), RefuelItemData.class);
                        // Giữ chuỗi gốc: bước trộn theo quyền sở hữu trường cần biết khoá nào
                        // server THỰC SỰ gửi, phân biệt với khoá vắng mặt (model điền mặc định).
                        item.setRawJson(o.toString());
                        lst.add(item);
                    }
                }
            }
        } catch (Exception e) {
            //Logger.appendLog("HTTP", "GetModified:" + e.getLocalizedMessage());
            return null;
        }
        return lst;

    }

    public List<RefuelItemData> getExtractList() {

        return getRefuelList(true, 1);

    }

    public RefuelItemData postRefuel(RefuelItemData refuelData) {
        String url = API_BASE_URL + "api/refuels";
        long start = System.currentTimeMillis();

        Logger.appendLog("HTTP_REFUEL", ">> START POST App-Version:"+BuildConfig.VERSION_CODE+"-"+BuildConfig.PATCH_NUMBER+" uid=" + refuelData.getUniqueId()
                + " seq=" + refuelData.getClientSeq()
                + " localModified=" + refuelData.isLocalModified()
                + " thread=" + Thread.currentThread().getName());

        try {
            // Chốt chặn cuối trước khi serialize: Volume phải khớp Gallon. Đặt ở đây vì đây là
            // đường DUY NHẤT mọi gói tin phiếu đi qua — cả DIRECT_POST lẫn BACKGROUND_SYNC.
            // Còn dòng log này nghĩa là còn một đường ghi đổi Gallon mà quên số lít.
            String volumeFix = refuelData.reconcileVolume();
            if (volumeFix != null)
                Logger.appendLog("VOLUME_MISMATCH", String.format(java.util.Locale.US,
                        "uid=%s seq=%d status=%s %s -> dùng volume_calc",
                        refuelData.getUniqueId(), refuelData.getClientSeq(),
                        refuelData.getStatus(), volumeFix));

            String parm = gson.toJson(refuelData);
            HttpResponse response = sendPOST(url, parm);
            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                Logger.appendLog("HTTP_REFUEL", "!! NULL RESPONSE uid=" + refuelData.getUniqueId()
                        + " sau " + elapsed + "ms (con=null, có thể do createConnection lỗi)");
                return null;
            }

            if (response.getResponseCode() == HttpURLConnection.HTTP_GATEWAY_TIMEOUT) {
                Logger.appendLog("HTTP_REFUEL", "!! TIMEOUT (HUỶ) uid=" + refuelData.getUniqueId()
                        + " sau " + elapsed + "ms — readTimeout đã kích hoạt, request bị huỷ");
                return null;
            }

            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                RefuelItemData newItem = gson.fromJson(response.getData(), RefuelItemData.class);
                if (newItem != null)
                    newItem.setRawJson(response.getData());
                Logger.appendLog("HTTP_REFUEL", "<< SUCCESS uid=" + refuelData.getUniqueId()
                        + " sau " + elapsed + "ms — serverId=" + (newItem != null ? newItem.getId() : "null"));
                if (newItem != null && refuelData.getId() == 0)
                    refuelData.setId(newItem.getId());
                return newItem;
            } else {
                Logger.appendLog("HTTP_REFUEL", "!! HTTP " + response.getResponseCode()
                        + " uid=" + refuelData.getUniqueId() + " sau " + elapsed + "ms");
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Logger.appendLog("HTTP_REFUEL", "!! EXCEPTION uid=" + refuelData.getUniqueId()
                    + " sau " + elapsed + "ms: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return null;
    }

    public void updateTruckAmount(String truckNo, double currentAmount) {

        String url = API_BASE_URL + "api/trucks/amount";
        try {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(new TruckModel(truckNo, currentAmount));
            HttpResponse resp = sendPOST(url, parm);

        } catch (Exception e) {
            Log.e("updateTruckAmount", e.getMessage());
        }
    }

    public TruckFuelModel postTruckFuel(TruckFuelModel model) {

        String url = API_BASE_URL + "api/trucks/fuel";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), TruckFuelModel.class);
        } catch (Exception e) {
            Log.e("postTruckFuel", e.getMessage());
        }
        return null;
    }


    public InvoiceModel postInvoice(InvoiceModel model) {

        String url = API_BASE_URL + "api/invoices";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), InvoiceModel.class);
        } catch (Exception e) {
            Log.e("postTruckFuel", e.getMessage());
        }
        return null;
    }

    public TruckModel postTruck(TruckModel model) {
        String url = API_BASE_URL + "api/trucks";
        try {
            //Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {

                return gson.fromJson(response.getData(), TruckModel.class);
            }
        } catch (Exception e) {
            Log.e("postRefuel", e.getMessage());

        }
        return null;

    }

    public AirlineModel postAirline(AirlineModel model) {
        String url = API_BASE_URL + "api/airlines";
        //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
        String parm = gson.toJson(model);
        try {
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), AirlineModel.class);
        } catch (Exception ex) {

        }
        return null;
    }


    public List<AirlineModel> getAirlines() {
        String url = API_BASE_URL + "api/airlines";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<AirlineModel> lst = new ArrayList<AirlineModel>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        AirlineModel item = gson.fromJson(o.toString(), AirlineModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("updateTruckAmount", e.getMessage());
        }
        return null;
    }


    public List<AirportsModel> getAirports() {
        String url = API_BASE_URL + "api/airports";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<AirportsModel> lst = new ArrayList<AirportsModel>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        AirportsModel item = gson.fromJson(o.toString(), AirportsModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("updateTruckAmount", e.getMessage());
        }
        return null;
    }
    public List<ShiftModel> getShifts() {
        String url = API_BASE_URL + "api/shift";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONObject arr = new JSONObject(data);
                List<ShiftModel> lst = new ArrayList<ShiftModel>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr;

                        ShiftModel item = gson.fromJson(o.toString(), ShiftModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("updateTruckAmount", e.getMessage());
        }
        return null;
    }
    public List<ParkingLot> getParking() {
        String url = API_BASE_URL + "api/parking";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<ParkingLot> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        ParkingLot item = gson.fromJson(o.toString(), ParkingLot.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("updateTruckAmount", e.getMessage());
        }
        return null;
    }

    public boolean checkTruck(int truckId, String truckNo) {

        String url = API_BASE_URL + "api/trucks/check";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(new TruckModel(truckNo, truckId));
            String data = sendPOST(url, parm).getData();
            boolean val = gson.fromJson(data, boolean.class);
            return val;
        } catch (Exception e) {
            Log.e("Truck Check API ", e.getMessage());
            return true;
        }

    }


    public boolean sendLog(String url, String filePath) throws IOException {

        String twoHyphens = "--";
        String truckNo = FMSApplication.getApplication().getTruckNo();
        String boundary = "*****" + System.currentTimeMillis() + "*****";
        String CRLF = "\r\n";
        String charset = "UTF-8";
        File textFile = new File(filePath);

        HttpURLConnection con = createConnection(url, "POST", "multipart/form-data; boundary=" + boundary);
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

    public HttpURLConnection createConnection(String url, String method, String contentType) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(30000);

            con.setRequestMethod(method);
            con.setRequestProperty("Connection", "Keep-Alive");

            con.setRequestProperty("Content-Type", contentType);
            con.setRequestProperty("Accept", "*/*");
            String USER_AGENT = "Mozilla/5.0";
            con.setRequestProperty("User-Agent", USER_AGENT);

            TruckModel current = currentSetting();
            con.setRequestProperty("Tablet-Id", header(current.getTabletSerial()));
            con.setRequestProperty("App-Version", header(current.getAppVersion() != null
                    ? current.getAppVersion() : BuildConfig.VERSION_NAME));
            con.setRequestProperty("Truck-Id", String.valueOf(current.getTruckId()));
            con.setRequestProperty("Truck-Code", header(current.getTruckNo()));
            this.token = currentToken();
            if (this.token != null)
                con.setRequestProperty("Authorization", "bearer " + this.token);
            return con;
        } catch (Exception ex) {
            Log.e("HTTP", "createConnection failed: " + url, ex);
            return null;
        }
    }

    /**
     * Setting ĐANG có của app, không phải bản chụp lúc dựng HttpClient.
     *
     * <p>DataHelper giữ một HttpClient static dùng lại suốt vòng đời process, nên bản chụp
     * lúc khởi tạo có thể là setting rỗng của lần cài mới (chưa chọn xe). Giữ nguyên bản
     * chụp đó thì mọi request sau khi chọn xe vẫn gửi Truck-Id = 0.
     */
    private TruckModel currentSetting() {
        TruckModel current = FMSApplication.getApplication().getSetting();
        if (current != null)
            this.setting = current;
        if (this.setting == null)
            this.setting = new TruckModel();
        return this.setting;
    }

    private String currentToken() {
        try {
            return FMSApplication.getApplication().getUser().getToken();
        } catch (Exception ex) {
            return this.token;
        }
    }

    /**
     * Giá trị header không được null: HttpURLConnection.setRequestProperty ném lỗi và
     * createConnection trả về null ⇒ toàn bộ request hỏng. Lần cài mới có tabletSerial,
     * appVersion và truckNo đều null, nên đây là đường đi bình thường chứ không phải biên.
     */
    private static String header(String value) {
        return value == null ? "" : value;
    }


    public InvoiceFormModel[] getInvoiceForms() {
        String url = API_BASE_URL + "api/invoiceform";
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                return gson.fromJson(data, InvoiceFormModel[].class);
            }
        } catch (Exception ex) {

        }
        return null;
    }

    public List<TruckFuelModel> getTruckFuels() {

        String url = API_BASE_URL + "api/trucks/fuels?truckId=" + setting.getTruckId();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<TruckFuelModel> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        TruckFuelModel item = gson.fromJson(o.toString(), TruckFuelModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("truckfuel list", e.getMessage());
        } finally {

        }
        return null;
    }

    public BM2505Model postBM2505(BM2505Model model) {
        String url = API_BASE_URL + "api/bm2505";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), BM2505Model.class);
        } catch (Exception e) {
            Log.e("postTruckFuel", e.getMessage());
        }
        return null;
    }

    public BM2508Model postBM2508(BM2508Model model) {
        String url = API_BASE_URL + "api/bm2508";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), BM2508Model.class);
        } catch (Exception e) {
            Log.e("postBM2508", e.getMessage());
        }
        return null;
    }
    //2508 mới
    public BM2508Model postBM2508Post2(BM2508Model model) {

        String url = API_BASE_URL + "api/bm2508/post2";

        try {
            String json = gson.toJson(model);
            Log.d("BM2508_POST2_JSON", json);

            RequestBody jsonBody = RequestBody.create(
                    json,
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Builder bodyBuilder =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("model", null, jsonBody);

            if (model.getUserSkypecSignaturePath() != null &&
                    !model.getUserSkypecSignaturePath().trim().isEmpty()) {

                File skypecFile = new File(model.getUserSkypecSignaturePath());
                Log.d("BM2508_POST2_SKYPEC_FILE", skypecFile.getAbsolutePath());

                if (skypecFile.exists()) {
                    RequestBody fileBody = RequestBody.create(
                            skypecFile,
                            MediaType.parse(getImageMimeType(skypecFile.getName()))
                    );

                    bodyBuilder.addFormDataPart(
                            "signSkypec",
                            skypecFile.getName(),
                            fileBody
                    );
                }
            }

            if (model.getAirlineSignaturePath() != null &&
                    !model.getAirlineSignaturePath().trim().isEmpty()) {

                File airlineFile = new File(model.getAirlineSignaturePath());
                Log.d("BM2508_POST2_AIRLINE_FILE", airlineFile.getAbsolutePath());

                if (airlineFile.exists()) {
                    RequestBody fileBody = RequestBody.create(
                            airlineFile,
                            MediaType.parse(getImageMimeType(airlineFile.getName()))
                    );

                    bodyBuilder.addFormDataPart(
                            "signAirline",
                            airlineFile.getName(),
                            fileBody
                    );
                }
            }

            RequestBody requestBody = bodyBuilder.build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + currentToken())
                    .post(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(request).execute();

            Log.d("BM2508_POST2_CODE", String.valueOf(response.code()));
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d("BM2508_POST2_RESPONSE", responseBody);

            if (response.isSuccessful()) {
                return gson.fromJson(responseBody, BM2508Model.class);
            }

        } catch (Exception e) {
            Log.e("postBM2508Post2", Log.getStackTraceString(e));
        }

        return null;
    }

    public List<BM2508Model> getBM2508List2() {

        String url = API_BASE_URL + "api/bm2508/get2/" + setting.getTruckId();

        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<BM2508Model> lst = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    BM2508Model item = gson.fromJson(o.toString(), BM2508Model.class);
                    lst.add(item);
                }

                return lst;
            }
        } catch (Exception e) {
            Log.e("getBM2508List2", Log.getStackTraceString(e));
        }

        return null;
    }

    public String getBM2508SkypecSignatureUrl(int id) {
        return API_BASE_URL + "api/bm2508/" + id + "/signature/skypec";
    }

    public String getBM2508AirlineSignatureUrl(int id) {
        return API_BASE_URL + "api/bm2508/" + id + "/signature/airline";
    }

    private String getImageMimeType(String fileName) {
        if (fileName == null) return "image/jpeg";

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
    //Kết thúc 2508 mới
    public CheckTrucksModel postCheckTrucks(CheckTrucksModel model) {
        String url = API_BASE_URL + "api/checktrucks";
        //String url = "http://localhost:61013/api/checktrucks";
        try {
            //Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            String parm = gson.toJson(model);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), CheckTrucksModel.class);
        } catch (Exception e) {
            Log.e("postTruckFuel", e.getMessage());
        }
        return null;
    }
    public List<BM2505Model> getBM2505List() {

        String url = API_BASE_URL + "api/bm2505/" + setting.getTruckId();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<BM2505Model> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        BM2505Model item = gson.fromJson(o.toString(), BM2505Model.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("truckfuel list", e.getMessage());
        }
        return null;
    }
    public List<BM2508Model> getBM2508List() {

        String url = API_BASE_URL + "api/bm2508/" + setting.getTruckId();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<BM2508Model> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        BM2508Model item = gson.fromJson(o.toString(), BM2508Model.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("BM2508 list", e.getMessage());
        }
        return null;
    }
    public List<CheckTrucksModel> getCheckTrucksList() {

        String url = API_BASE_URL + "api/checktrucks/" + setting.getTruckId();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<CheckTrucksModel> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        CheckTrucksModel item = gson.fromJson(o.toString(), CheckTrucksModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("truckfuel list", e.getMessage());
        }
        return null;
    }
    public List<BM2505ContainerModel> getBM2505ContainerList() {

        String url = API_BASE_URL + "api/bm2505/containers";
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<BM2505ContainerModel> lst = new ArrayList<>();
                if (arr.length() > 0) {

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);

                        BM2505ContainerModel item = gson.fromJson(o.toString(), BM2505ContainerModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("truckfuel list", e.getMessage());
        }
        return null;
    }
    public List<ProductModel> getProductList() {
        String url = API_BASE_URL + "api/Products/list"; // sửa URL đúng endpoint của API product

        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();

                JSONArray arr = new JSONArray(data);
                List<ProductModel> lst = new ArrayList<>();
                if (arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        ProductModel item = gson.fromJson(o.toString(), ProductModel.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("product list", e.getMessage());
        }

        return null;
    }
    public RefuelItemData getRefuelItem(String uniqueId) {
        String url = API_BASE_URL + "api/refuels/?uniqueId=";
        try {
            url = url + uniqueId;
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                try {


                    RefuelItemData item = gson.fromJson(data, RefuelItemData.class);
                    item.setRawJson(data);
                    return item;
                } catch (Exception e) {
                    return null;

                }
            }

        } catch (Exception e) {

        }
        return null;
    }

    // ===== BM2506 / BM2509 =====
    public BM2506Model postBM2506(BM2506Model model) {
        return postForm("api/bm2506", model, BM2506Model.class);
    }

    public BM2509Model postBM2509(BM2509Model model) {
        return postForm("api/bm2509", model, BM2509Model.class);
    }

    public List<BM2506Model> getBM2506List() {
        return getFormList("api/bm2506/", BM2506Model.class);
    }

    public List<BM2509Model> getBM2509List() {
        return getFormList("api/bm2509/", BM2509Model.class);
    }

    private <T> T postForm(String path, Object model, Class<T> cls) {
        try {
            HttpResponse response = sendPOST(API_BASE_URL + path, gson.toJson(model));
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                return gson.fromJson(response.getData(), cls);
            Log.e("post " + path, "HTTP " + response.getResponseCode() + " " + response.getData());
        } catch (Exception e) {
            Log.e("post " + path, Log.getStackTraceString(e));
        }
        return null;
    }

    private <T> List<T> getFormList(String path, Class<T> cls) {
        try {
            HttpResponse response = sendGET(API_BASE_URL + path + setting.getTruckId());
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                JSONArray arr = new JSONArray(response.getData());
                List<T> lst = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++)
                    lst.add(gson.fromJson(arr.getJSONObject(i).toString(), cls));
                return lst;
            }
            Log.e("get " + path, "HTTP " + response.getResponseCode());
        } catch (Exception e) {
            Log.e("get " + path, Log.getStackTraceString(e));
        }
        return null;
    }

    //2503
    public BM2503Model postBM2503(BM2503Model model) {

        String url = API_BASE_URL + "api/bm2503";

        try {


            // ===== JSON PART =====
            String json = gson.toJson(model);
            Log.d("BM2503_POST_JSON", json);

            RequestBody jsonBody = RequestBody.create(
                    json,
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Builder bodyBuilder =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("model", null, jsonBody);

            // ===== FILE PART =====
            if (model.getSignPictureUrl() != null) {
                File file = new File(model.getSignPictureUrl());
                Log.d("BM2503_POST_FILE", file.getAbsolutePath());

                if (file.exists()) {
                    RequestBody fileBody =
                            RequestBody.create(file, MediaType.parse("image/jpeg"));

                    bodyBuilder.addFormDataPart(
                            "signImage",
                            file.getName(),
                            fileBody
                    );
                }
            }

            // 🔥 TẠO requestBody Ở ĐÂY
            RequestBody requestBody = bodyBuilder.build();

            // ===== REQUEST =====
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + currentToken()) // 🔥 BẮT BUỘC
                    .post(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();

            Response response = client.newCall(request).execute();

            Log.d("BM2503_POST_CODE", String.valueOf(response.code()));
            String responseBody = response.body().string();
            Log.d("BM2503_POST_RESPONSE", responseBody);

            if (response.isSuccessful()) {
                return gson.fromJson(responseBody, BM2503Model.class);
            }

        } catch (Exception e) {
            Log.e("postBM2503", Log.getStackTraceString(e));
        }

        return null;
    }



    public List<BM2503Model> getBM2503List() {

        String url = API_BASE_URL + "api/bm2503/" + setting.getTruckId();
        try {
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {

                String data = response.getData();
                JSONArray arr = new JSONArray(data);

                List<BM2503Model> lst = new ArrayList<>();
                if (arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        BM2503Model item = gson.fromJson(o.toString(), BM2503Model.class);
                        lst.add(item);
                    }
                }
                return lst;
            }
        } catch (Exception e) {
            Log.e("getBM2503List", e.getMessage());
        }
        return null;
    }
    // =====================
// POST BM2504
// =====================
    public BM2504Model postBM2504(BM2504Model model) {

        String url = API_BASE_URL + "api/bm2504";

        try {

            // ===== JSON PART =====
            String json = gson.toJson(model);
            Log.d("BM2504_POST_JSON", json);

            RequestBody jsonBody = RequestBody.create(
                    json,
                    MediaType.parse("application/json; charset=utf-8")
            );

            MultipartBody.Builder bodyBuilder =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("model", null, jsonBody);

            // ===== FILE PART (SIGNATURE) =====
            if (model.getSignPictureUrl() != null) {
                File file = new File(model.getSignPictureUrl());
                Log.d("BM2504_POST_FILE", file.getAbsolutePath());

                if (file.exists()) {
                    RequestBody fileBody =
                            RequestBody.create(file, MediaType.parse("image/jpeg"));

                    bodyBuilder.addFormDataPart(
                            "signImage",
                            file.getName(),
                            fileBody
                    );
                }
            }

            // ===== BUILD BODY =====
            RequestBody requestBody = bodyBuilder.build();

            // ===== REQUEST =====
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + currentToken()) // 🔥 BẮT BUỘC
                    .post(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();

            Response response = client.newCall(request).execute();

            Log.d("BM2504_POST_CODE", String.valueOf(response.code()));
            String responseBody = response.body().string();
            Log.d("BM2504_POST_RESPONSE", responseBody);

            if (response.isSuccessful()) {
                return gson.fromJson(responseBody, BM2504Model.class);
            }

        } catch (Exception e) {
            Log.e("postBM2504", Log.getStackTraceString(e));
        }

        return null;
    }
    // =====================
// GET BM2504 LIST
// =====================
    public List<BM2504Model> getBM2504List() {

        String url = API_BASE_URL + "api/bm2504/" + setting.getTruckId();

        try {
            HttpResponse response = sendGET(url);

            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {

                String data = response.getData();
                JSONArray arr = new JSONArray(data);

                List<BM2504Model> lst = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    BM2504Model item =
                            gson.fromJson(o.toString(), BM2504Model.class);
                    lst.add(item);
                }

                return lst;
            }

        } catch (Exception e) {
            Log.e("getBM2504List", Log.getStackTraceString(e));
        }

        return null;
    }





}
