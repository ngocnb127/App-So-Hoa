package com.megatech.fms.model;

import androidx.annotation.NonNull;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class TruckModel extends BaseModel {

    public TruckModel() {
    }

    public TruckModel(String code, double currentAmount) {
        this.truckNo = code;
        this.currentAmount = currentAmount;

    }
    public TruckModel(String code, int id) {
        this.truckNo = code;
        this.id = id;

    }
    private  String code;

    public String getCode() {
        return code;
    }

    private String truckNo;
    private double currentAmount;

    private String deviceSerial;


    private String deviceIP = "192.168.1.1";
    private String printerIP = "192.168.1.1";
    private Integer devicePort = 10001;
    private Integer printerPort = 9100;

    private Integer airportId;

    private String airportCode;

    private boolean allowNewRefuel;

    private boolean isFHS;

    private int receiptCount;

    private String receiptCode;

    public String getReceiptCode() {
        return receiptCode;
    }

    public void setReceiptCode(String receiptCode) {
        this.receiptCode = receiptCode;
    }

    public int getReceiptCount() {
        return receiptCount;
    }

    public void setReceiptCount(int receiptCount) {
        this.receiptCount = receiptCount;
    }

    public boolean isAllowNewRefuel() {
        return allowNewRefuel;
    }

    public void setAllowNewRefuel(boolean allowNewRefuel) {
        this.allowNewRefuel = allowNewRefuel;
    }

    public String getAirportCode() {
        return airportCode;
    }

    public void setAirportCode(String airportCode) {
        this.airportCode = airportCode;
    }

    public Integer getAirportId() {
        return airportId;
    }

    public void setAirportId(Integer airportId) {
        this.airportId = airportId;
    }

    public String getDeviceIP() {
        return deviceIP;
    }

    public void setDeviceIP(String deviceIP) {
        this.deviceIP = deviceIP;
    }

    public String getPrinterIP() {
        return printerIP;
    }

    public void setPrinterIP(String printerIP) {
        this.printerIP = printerIP;
    }

    public Integer getDevicePort() {
        return devicePort;
    }

    public void setDevicePort(Integer devicePort) {
        this.devicePort = devicePort;
    }

    public Integer getPrinterPort() {
        return printerPort;
    }

    public void setPrinterPort(Integer printerPort) {
        this.printerPort = printerPort;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public String getTruckNo() {
        return code;
    }

    public void setTruckNo(String truckNo) {
        this.code = this.truckNo = truckNo;

    }

    private int truckId;

    public int getTruckId() {
        return id;
    }

    public void setTruckId(int truckId) {
        this.id = this.truckId = truckId;

    }

    public void setCode(String code) {
        this.code = this.truckNo = code;
    }

    public double getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(double currentAmount) {
        this.currentAmount = currentAmount;
    }

    public String toJson() {

        return gson.toJson(this);
    }

    public static TruckModel fromJson(String json) {

        try {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            TruckModel model = gson.fromJson(json, TruckModel.class);
            if (model == null)
                model = new TruckModel();
            return  model;
        }catch (JsonSyntaxException ex)
        {
            return new TruckModel();
        }
    }


    public void setId(int id) {
        this.truckId = this.id = id;
    }


    private String appVersion;

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    @NonNull
    @Override
    public String toString() {
        return truckNo;
    }

    private double capacity;

    public double getCapacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public double getCapacityLitter()
    {
        return Math.round(capacity * RefuelItemData.GALLON_TO_LITTER);
    }

    public boolean isFHS() {
        return isFHS;
    }

    public void setFHS(boolean FHS) {
        isFHS = FHS;
    }

    private THERMAL_PRINTER_TYPE thermalPrinterType = THERMAL_PRINTER_TYPE.ZQ520;

    public THERMAL_PRINTER_TYPE getThermalPrinterType() {
        return thermalPrinterType;
    }

    public void setThermalPrinterType(THERMAL_PRINTER_TYPE thermalPrinterType) {
        this.thermalPrinterType = thermalPrinterType;
    }

    public enum THERMAL_PRINTER_TYPE
    {
        ZQ520,
        ZQ511
    }
}
