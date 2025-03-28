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
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.BM2508Model;
import com.megatech.fms.model.CheckTrucksModel;
import com.megatech.fms.model.FlightData;
import com.megatech.fms.model.InvoiceFormModel;
import com.megatech.fms.model.InvoiceModel;
import com.megatech.fms.model.LCRDataModel;
import com.megatech.fms.model.LoginResultModel;
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

    public String getContent(String url) {
        try {
            return sendGET(url).getData();
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

    public HttpResponse sendPOST(String url, String params) throws IOException {
        return sendPOST(url, params, "application/json; utf-8");
    }

    public HttpResponse sendPOST(String url, String params, String contentType) throws IOException {
        try {

            HttpURLConnection con = createConnection(url, "POST", contentType);
            if (con == null)
                return null;
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
        try {
            //Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            //Logger.appendLog("API", String.format("FlightCode: %s Amount : %.0f Start Number: %.0f End Number: %.0f",refuelData.getFlightCode(), refuelData.getRealAmount(),  refuelData.getStartNumber() , refuelData.getEndNumber()));

            String parm = gson.toJson(refuelData);
            HttpResponse response = sendPOST(url, parm);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                RefuelItemData newItem = gson.fromJson(response.getData(), RefuelItemData.class);
                if (newItem != null && refuelData.getId() == 0)
                    refuelData.setId(newItem.getId());
                return newItem;
            }
        } catch (Exception e) {


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
            con.setConnectTimeout(1000);

            con.setRequestMethod(method);
            con.setRequestProperty("Connection", "Keep-Alive");

            con.setRequestProperty("Content-Type", contentType);
            con.setRequestProperty("Accept", "*/*");
            String USER_AGENT = "Mozilla/5.0";
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("Tablet-Id", setting.getTabletSerial());
            con.setRequestProperty("App-Version", setting.getAppVersion());
            con.setRequestProperty("Truck-Id", String.valueOf(setting.getTruckId()));
            con.setRequestProperty("Truck-Code", setting.getTruckNo());
            this.token = FMSApplication.getApplication().getUser().getToken();
            if (this.token != null)
                con.setRequestProperty("Authorization", "bearer " + this.token);
            return con;
        } catch (Exception ex) {
            return null;
        }
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

    public RefuelItemData getRefuelItem(String uniqueId) {
        String url = API_BASE_URL + "api/refuels/?uniqueId=";
        try {
            url = url + uniqueId;
            HttpResponse response = sendGET(url);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String data = response.getData();
                try {


                    RefuelItemData item = gson.fromJson(data, RefuelItemData.class);
                    return item;
                } catch (Exception e) {
                    return null;

                }
            }

        } catch (Exception e) {

        }
        return null;
    }

}

