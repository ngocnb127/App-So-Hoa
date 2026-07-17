package com.megatech.fms.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class UserInfo {
    public UserInfo() {
    }
    public UserInfo(int userId, String userName, String token, int permission, String airport, int airportId) {
        this(userId,userName, token, permission,airport, "","","",airportId);
    }

    public UserInfo(int userId, String userName, String token, int permission, String airport,String address, String taxcode, String invoiceName, int airportId) {
        this.userId = userId;
        this.userName = userName;
        this.token = token;
        this.permission = permission;
        this.lastLogin = new Date();
        this.airport = airport;
        this.address = address;
        this.taxCode = taxcode;
        this.invoiceName = invoiceName;
        this.airportId = airportId;

    }

    private Date lastLogin;
    private int userId = 0;
    private int airportId = 0;
    private String userName = "";

    private int permission;

    public int getPermission() {
        return permission;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    private String token ="";

    public  String getUserName(){
        return  userName;

    }

    public int getUserId(){
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;

    }
    public int getAirportId(){
        return airportId;
    }

    public void setAirportId(int airportId) {
        this.airportId = airportId;

    }

    private String airport;
    private String address;
    private String taxCode;

    public String getAirport() {
        return airport;
    }

    public void setAirport(String airport) {
        this.airport = airport;
    }

    public Date getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }
    private String invoiceName;

    public String getInvoiceName() {
        return invoiceName;
    }

    public void setInvoiceName(String invoiceName) {
        this.invoiceName = invoiceName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void addToSharePreferences(Context ctx)
    {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("FMS",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USER_NAME", this.userName);
        editor.putInt("USER_ID", this.userId);
        editor.putString("TOKEN", this.token);
        editor.putString("AIRPORT", this.airport);
        editor.putString("ADDRESS", this.address);
        editor.putString("TAX_CODE", this.taxCode);
        editor.putString("INVOICE_NAME", this.invoiceName);
        editor.putLong("LOGIN_TIME", (new Date()).getTime());
        editor.putInt("PERMISSION", this.permission);
        editor.putInt("AIRPORT_ID", this.airportId);
        editor.putInt("BUILD", 12);

        editor.commit();
    }
    public void readFromSharedPreferences(Context ctx)
    {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("FMS",Context.MODE_PRIVATE);
        this.userId = sharedPreferences.getInt("USER_ID", 0);
        this.airportId = sharedPreferences.getInt("AIRPORT_ID", 0);
        this.userName = sharedPreferences.getString("USER_NAME", "");
        this.token = sharedPreferences.getString("TOKEN", "");
        this.permission = sharedPreferences.getInt("PERMISSION", 0);

    }

    public static UserInfo fromSharedPreferences(Context ctx){
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("FMS",Context.MODE_PRIVATE);
        UserInfo user = new UserInfo();
        user.userId = sharedPreferences.getInt("USER_ID", 0);
        user.userName = sharedPreferences.getString("USER_NAME", "");
        user.token = sharedPreferences.getString("TOKEN", "");
        user.permission = sharedPreferences.getInt("PERMISSION", 0);
        user.airport = sharedPreferences.getString("AIRPORT","");
        user.address = sharedPreferences.getString("ADDRESS","");
        user.taxCode =sharedPreferences.getString("TAX_CODE","");
        user.invoiceName =sharedPreferences.getString("INVOICE_NAME","");
        user.airportId = sharedPreferences.getInt("AIRPORT_ID", 0);
        return user;
    }
    public static void logout(Context ctx){
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("FMS",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("USER_NAME");
        editor.remove("USER_ID");
        editor.remove("AIRPORT_ID");
        editor.remove("TOKEN");
        editor.remove("LOGIN_TIME");
        editor.remove("SHIFT");
        editor.commit();
    }

    public enum USER_PERMISSION {
        @SerializedName("0") NONE(0),
        @SerializedName("1") CREATE_REFUEL(1),
        @SerializedName("2") CREATE_EXTRACT(2),
        @SerializedName("4") CREATE_CUSTOMER(4);
        private final int value;

        USER_PERMISSION(int i) {
            value = i;
        }

        public int getValue() {
            return value;
        }
    }
}
