package com.megatech.fms.model;

public class LoginResultModel {
    private LOGIN_ERROR_TYPE errorType;

    private int userId;
    private int airportId;
    private String userName ;
    private String access_token;
    private String airport;
    private int permission;
    private String address;
    private String taxCode;
    private String invoiceName;

    public LOGIN_ERROR_TYPE getErrorType() {
        return errorType;
    }

    public void setErrorType(LOGIN_ERROR_TYPE errorType) {
        this.errorType = errorType;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getAirportId() {
        return airportId;
    }

    public void setAirportId(int airportId) {
        this.airportId = airportId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getAirport() {
        return airport;
    }

    public void setAirport(String airport) {
        this.airport = airport;
    }

    public int getPermission() {
        return permission;
    }

    public void setPermission(int permission) {
        this.permission = permission;
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

    public String getInvoiceName() {
        return invoiceName;
    }

    public void setInvoiceName(String invoiceName) {
        this.invoiceName = invoiceName;
    }

    public  enum LOGIN_ERROR_TYPE {
        CONNECTION_ERROR,
        DATA_ERROR
    }
}
